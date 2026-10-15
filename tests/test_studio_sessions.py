"""Window-free authoring: map storage, live edits, persistence and recovery."""
from pathlib import Path
from unittest import TestCase, mock
import json
import sys

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'scripts'))
import test_studio_files
from studio import dispatch
from studio_core import file_store as store, map_files, migration
from studio_core.file_project import FileProject
from studio_core.project import Project, encode, digest, MOD
from studio_core.maps import EditableMap
from studio_core.session import Sessions


class LiveProjects(TestCase):
    def setUp(self):
        test_studio_files.FileProjects.setUp(self)
        base=self.root/'build/studio/runtime';base.parent.mkdir(parents=True)
        self.base.rename(base);self.base=base
        self.enterContext(mock.patch('studio.ROOT',self.root))
        self.p = Project.create(self.root/'current',self.base)
        self.p.apply([{'op':'map.create','name':'island','size':16}])
        self.sessions = Sessions(delay=60)
        self.addCleanup(self.stop_timers)
        self.call('inspect',map='island')

    def stop_timers(self):
        for session in self.sessions.items.values():
            if session.timer: session.timer.cancel()

    def call(self,command,**values):
        return dispatch({'command':command,'project':str(self.p.root),**values},self.sessions)

    def place(self,**values):
        return self.call('apply',map='island',operations=[{'op':'entity.add','map':'island','kind':'object','type':1,'values':values}])

    def test_default_format_stores_current_entities_in_one_map_json(self):
        self.assertEqual(self.p.data['format'],store.FORMAT)
        self.p.apply([{'op':'map.create','name':'second','size':16}])
        before=store.scan(self.p.root)
        self.place(x=64);self.call('flush')
        after=store.scan(self.p.root)
        self.assertEqual([p for p in after if before[p]!=after[p]],['maps/island/map.json'])
        self.assertFalse(any('/entities/' in p for p in after))
        self.assertEqual(json.loads(after['maps/island/map.json'])['entities'][0]['x'],64)

    def test_resource_pin_keeps_legacy_path_ordering(self):
        folder=self.base/MOD/'gfx';(folder/'a').mkdir(parents=True)
        (folder/'a/image.bmp').write_bytes(b'nested');(folder/'a.bmp').write_bytes(b'sibling')
        paths=[p for name in (MOD+'sys',MOD+'gfx') for p in (self.base/name).rglob('*') if p.is_file()]
        expected=digest(encode({p.relative_to(self.base).as_posix():digest(p.read_bytes()) for p in sorted(paths)}))
        self.assertEqual(store.base_hash(self.base),expected)

    def test_many_clicks_are_live_before_source_save_and_keep_individual_undo(self):
        before=store.scan(self.p.root);writes=[];atomic=store.atomic
        def record(path,data):
            if str(path).endswith('maps/island/map.json'):writes.append(data)
            return atomic(path,data)
        with mock.patch.object(store,'atomic',side_effect=record):
            for i in range(10):
                result=self.place(x=i*16)
                self.assertTrue(result['pendingSave'])
                self.assertEqual(len(result['snapshot']['map']['entities']),i+2)
            self.assertEqual(store.scan(self.p.root),before)
            self.assertEqual(writes,[])
            self.call('flush')
            self.assertEqual(len(writes),1)
        self.assertFalse(self.call('revision')['pendingSave'])
        self.call('undo')
        self.assertEqual(len(self.call('inspect',map='island')['map']['entities']),10)
        self.call('redo')
        self.assertEqual(len(self.call('inspect',map='island')['map']['entities']),11)

    def test_placement_and_delta_do_not_reload_or_rescan_shared_resources(self):
        preview=self.call('preview',map='island')
        self.call('inspect',map='island')
        with (mock.patch.object(store,'base_hash',side_effect=AssertionError('full resource scan')),
              mock.patch.object(FileProject,'load',side_effect=AssertionError('reload')),
              mock.patch.object(FileProject,'validate',side_effect=AssertionError('full validation'))):
            result=self.place(x=32)
            self.assertEqual(result['snapshot']['map']['entities'][0]['x'],32)
            delta=self.call('preview',map='island',since=preview['token'])
            self.assertEqual(delta['changes']['upserted'],1)
            self.call('flush')
            self.place(x=64)

    def test_acknowledged_pending_edit_recovers_after_process_loss(self):
        before=store.scan(self.p.root)
        self.place(x=64);self.place(x=128)
        self.stop_timers()
        self.assertEqual(store.scan(self.p.root),before)
        recovered=Project(self.p.root)
        self.assertEqual(len(EditableMap(recovered.map_bytes('island')).entities),3)
        recovered.undo()
        self.assertEqual(len(EditableMap(recovered.map_bytes('island')).entities),2)

    def test_failed_batch_does_not_acknowledge_or_change_sources(self):
        before=store.scan(self.p.root);version=self.call('revision')['projectHash']
        with self.assertRaises(ValueError):
            self.call('apply',map='island',operations=[
                {'op':'entity.add','map':'island','kind':'object','type':1},
                {'op':'entity.add','map':'island','kind':'object','type':999999}])
        self.assertEqual(self.call('revision')['projectHash'],version)
        self.assertEqual(store.scan(self.p.root),before)
        self.assertFalse((self.p.root/store.JOURNAL).exists())

    def test_save_failure_is_visible_and_retry_keeps_the_edit(self):
        self.place(x=64)
        with mock.patch.object(store,'recover',side_effect=OSError('disk unavailable')):
            with self.assertRaisesRegex(OSError,'disk unavailable'):self.call('flush')
        status=self.call('revision')
        self.assertTrue(status['pendingSave']);self.assertIn('disk unavailable',status['saveError'])
        self.call('flush')
        self.assertEqual(EditableMap(Project(self.p.root).map_bytes('island')).entities[0]['x'],64)

    def test_external_edits_conflict_without_overwriting_either_source(self):
        self.place(x=64)
        path=self.p.root/'maps/island/map.json';raw=path.read_bytes()+b'\n';path.write_bytes(raw)
        with self.assertRaisesRegex(ValueError,'conflict'):self.place(x=128)
        with self.assertRaisesRegex(ValueError,'Recovery conflict'):self.call('flush')
        self.assertEqual(path.read_bytes(),raw)
        self.assertTrue((self.p.root/store.JOURNAL).exists())

    def test_cli_edit_after_recovery_is_detected_and_stale_input_is_rejected(self):
        saved=self.place(x=64)['snapshot']
        Project(self.p.root).apply([{'op':'entity.set','map':'island','kind':'object','id':1,'values':{'yaw':90}}])
        next=self.call('inspect',map='island')
        self.assertEqual(next['map']['entities'][0]['yaw'],90)
        with self.assertRaisesRegex(ValueError,'content changed'):
            self.call('apply',expectProjectHash=saved['projectHash'],operations={'op':'entity.set','map':'island','kind':'object','id':1,'values':{'x':128}})

    def test_test_start_flushes_and_rechecks_the_resource_pin(self):
        self.place(x=64)
        version=self.call('revision')
        self.call('preview',map='island',expectProjectHash=version['projectHash'])
        self.assertFalse(self.call('revision')['pendingSave'])
        (self.base/'mods/Stranded II/sys/objects.inf').write_text('id=1\nname=Different\n')
        with self.assertRaises(ValueError):self.call('preview',map='island',expectProjectHash=version['projectHash'])

    def test_v2_conversion_and_restore_preserve_maps_scripts_and_files(self):
        old=Project.create(self.root/'old',self.base,version=2)
        old.apply([{'op':'map.create','name':'island','size':16},
                   {'op':'entity.add','map':'island','kind':'object','type':1},
                   {'op':'entity.script','map':'island','kind':'object','id':1,'text':'on:use { msg "keep"; }'}])
        before=store.scan(old.root);native=old.map_bytes('island')
        review=migration.migrate(old.root)
        self.assertEqual(store.scan(old.root),before)
        result=migration.migrate(old.root,False,review['projectHash'])
        new=Project(old.root)
        self.assertEqual(new.map_bytes('island'),native)
        self.assertFalse(any('/entities/' in p for p in new.files))
        self.assertEqual(new.script_files,old.script_files)
        migration.restore(old.root,result['backup'],False)
        self.assertEqual(store.scan(old.root),before)

    def test_prepared_maps_preserve_every_native_byte_in_the_new_format(self):
        paths=list((Path(__file__).resolve().parents[1]/'build/studio/runtime/mods/Stranded II/maps').rglob('*.s2'))
        if not paths:self.skipTest('Prepared resources are optional')
        for i,path in enumerate(paths):
            with self.subTest(map=path.name):
                raw=path.read_bytes();name=f'original-{i}'
                map_files.split(self.p,name,raw);self.p._sync_scripts();self.p._rebuild()
                self.assertEqual(self.p.map_bytes(name),raw)
