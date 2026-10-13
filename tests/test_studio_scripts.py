"""File source ownership, native export and GUI/CLI concurrency contracts."""
import base64
import json
from pathlib import Path
from tempfile import TemporaryDirectory
import sys
from unittest import TestCase, mock

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'scripts'))
from studio_core.project import Project, MOD, digest
from studio_core import scripts
from studio_core.maps import EditableMap
import studio


class ScriptWorkspace(TestCase):
    def setUp(self):
        temp=TemporaryDirectory();self.addCleanup(temp.cleanup);self.root=Path(temp.name)
        self.base=self.root/'base';sysdir=self.base/MOD/'sys';sysdir.mkdir(parents=True)
        for name,text in {'items.inf':'id=1\nname=Wood\nid=2\nname=Stone\n','units.inf':'id=1\nname=Visitor\n',
                          'objects.inf':'id=1\nname=Hut\n','infos.inf':'id=1\nname=Start\n'}.items():
            (sysdir/name).write_text(text,encoding='utf8')
        self.p=Project.create(self.root/'project',self.base)
        self.p.apply([{'op':'map.create','name':'island','size':16}])
        self.path='scripts/maps/island.s2s';self.target={'scope':'map','map':'island'}

    def write(self,text,path=None,expected=None):
        return self.p.apply([{'op':'script.write','path':path or self.path,'text':text,'expectedHash':expected}])

    def bind(self,target=None):
        self.p.apply([{'op':'script.bind','path':self.path,'target':target or self.target}])

    def test_extract_preserves_native_map_and_external_edits_are_authoritative(self):
        body='on:start {\n msg "섬";\n}'
        self.p.apply([{'op':'map.script','map':'island','text':body}]);before=self.p.map_bytes('island')
        self.p.apply([{'op':'script.extract','target':self.target,'path':self.path}])
        self.assertEqual(self.p.map_bytes('island'),before)
        self.assertEqual((self.p.root/self.path).read_text('utf8'),body)
        (self.p.root/self.path).write_text(body.replace('섬','새 섬'),encoding='utf8')
        other=Project(self.p.root)
        self.assertIn('새 섬',EditableMap(other.map_bytes('island')).summary()['script'])
        self.assertEqual(other.data['revision'],self.p.data['revision'])
        self.assertNotEqual(scripts.version(other.script_files),scripts.version(self.p.script_files))

    def test_file_hash_prevents_stale_gui_and_legacy_setter_overwrites(self):
        self.write('on:start {}');self.bind()
        before=digest(b'on:start {}')
        (self.p.root/self.path).write_text('on:start { msg "external"; }',encoding='utf8')
        for op in [{'op':'script.write','path':self.path,'text':'lost','expectedHash':before},
                   {'op':'map.script','map':'island','text':'lost'}]:
            with self.assertRaisesRegex(ValueError,'conflict|expectedHash'): self.p.apply([op])
        self.assertIn('external',(self.p.root/self.path).read_text('utf8'))

    def test_legacy_setter_routes_to_bound_file(self):
        self.write('on:start {}');self.bind()
        self.p.apply([{'op':'map.script','map':'island','text':'on:start { msg "new"; }','expectedHash':digest(b'on:start {}')}])
        self.assertIn('new',(self.p.root/self.path).read_text('utf8'))

    def test_numeric_binding_ids_have_one_identity(self):
        self.write('on:use {}')
        self.bind({'scope':'definition','kind':'item','id':'1'})
        self.assertEqual(scripts.binding(self.p,{'scope':'definition','kind':'item','id':1}),self.path)

    def test_binding_and_write_batch_undo_redo_and_external_conflict(self):
        self.p.apply([{'op':'script.write','path':self.path,'text':'on:start {}','expectedHash':None},
                      {'op':'script.bind','path':self.path,'target':self.target}])
        self.p.undo();self.assertFalse((self.p.root/self.path).exists());self.p.undo(redo=True)
        self.assertTrue((self.p.root/self.path).exists())
        (self.p.root/self.path).write_text('external',encoding='utf8')
        with self.assertRaisesRegex(ValueError,'external'): self.p.undo()
        self.assertEqual((self.p.root/self.path).read_text('utf8'),'external')

    def test_dry_run_and_failed_batch_leave_no_sources(self):
        ops=[{'op':'script.write','path':self.path,'text':'on:start {}','expectedHash':None}]
        self.p.apply(ops,dry_run=True);self.assertFalse((self.p.root/'scripts').exists())
        with self.assertRaises(ValueError):self.p.apply(ops+[{'op':'script.bind','path':self.path,'target':{'scope':'map','map':'missing'}}])
        self.assertFalse((self.p.root/'scripts').exists())

    def test_definition_and_entity_bindings_export_native_contents(self):
        body='on:use { msg "hello"; }';self.write(body)
        self.bind({'scope':'definition','kind':'item','id':1})
        self.bind({'scope':'entity','kind':'info','id':1,'map':'island'})
        self.p.export(self.root/'out')
        out=self.root/'out/overrides'/MOD
        self.assertIn(body,(out/'sys/items.inf').read_text('utf8'))
        self.assertEqual((out/scripts.native_path(self.path)).read_text('utf8'),body)
        self.assertEqual(EditableMap((out/'maps/island.s2').read_bytes()).summary()['entities'][0]['script'],body)

    def test_unbind_retains_current_source_and_keeps_file(self):
        self.write('on:start { msg "hello"; }');self.bind()
        before=self.p.map_bytes('island')
        self.p.apply([{'op':'script.unbind','target':self.target}])
        self.assertEqual(self.p.map_bytes('island'),before);self.assertTrue((self.p.root/self.path).exists())

    def test_lexical_errors_and_missing_reference_have_source_locations(self):
        info=scripts.analyze('// {\non:start {\n msg "}";\n',self.path)
        self.assertEqual(info['diagnostics'][0]['line'],2)
        self.write('on:start { extendscript 0,0,"sys/scripts/studio/missing.s2s"; }')
        issue=next(d for d in self.p.validate() if d['code']=='missing-script')
        self.assertEqual(issue['path'],self.path);self.assertEqual(issue['line'],1)

    def test_missing_bound_file_does_not_destroy_inline_script(self):
        self.write('on:start {}');self.bind();(self.p.root/self.path).unlink()
        self.p.load();self.assertTrue(any(d['code']=='script-binding' for d in self.p.validate()))

    def test_paths_and_case_collisions(self):
        for path in ('../a.s2s','scripts/../a.s2s','scripts/a.txt','scripts/x/../../a.s2s'):
            with self.assertRaises(ValueError):self.write('',path)
        self.write('', 'scripts/Test.s2s')
        with self.assertRaises(ValueError):self.write('', 'scripts/test.s2s')

    def test_preview_freezes_sources_and_rejects_a_mid_preparation_edit(self):
        self.write('on:start {}');self.bind()
        preview=self.p.preview('island')
        self.assertEqual(base64.b64decode(preview['files'][MOD+scripts.native_path(self.path)]),b'on:start {}')
        from studio_core.preview import preview as real
        def racing(*args):
            value=real(*args);(self.p.root/self.path).write_text('on:start { msg "race"; }',encoding='utf8');return value
        with mock.patch('studio_core.preview.preview',side_effect=racing),self.assertRaisesRegex(ValueError,'changed'):
            self.p.preview('island')

    def test_preview_rechecks_expected_versions_after_loading_under_lock(self):
        self.write('on:start {}');self.bind()
        revision=self.p.data['revision'];version=scripts.version(self.p.script_files)
        (self.p.root/self.path).write_text('on:start { msg "external"; }',encoding='utf8')
        with self.assertRaisesRegex(ValueError,'Scripts changed'):
            self.p.preview('island',expected_revision=revision,expected_scripts=version)
        self.p.apply([{'op':'locale.set','language':'en','key':'test','text':'new'}])
        with self.assertRaisesRegex(ValueError,'Project changed'):
            self.p.preview('island',expected_revision=revision)

    def test_recovery_finishes_an_interrupted_multi_file_commit(self):
        from studio_core.project import atomic
        self.write('on:start {}')
        def interrupt(path,data):
            if path==self.p.path:raise OSError('power loss')
            return atomic(path,data)
        with mock.patch('studio_core.project.atomic',side_effect=interrupt),self.assertRaises(OSError):
            self.write('on:start { msg "new"; }',expected=digest(b'on:start {}'))
        recovered=Project(self.p.root)
        self.assertEqual(recovered.script_files[self.path],'on:start { msg "new"; }')
        recovered.undo();self.assertEqual(recovered.script_files[self.path],'on:start {}')

    def test_template_sample_is_atomic_exportable_and_searchable(self):
        op={'op':'template.create','template':'scenario','name':'Rescue','slug':'rescue','map':'rescue','source':1,'npcSource':1}
        dry=self.p.apply([op],dry_run=True);self.assertEqual(dry['diagnostics'],[])
        self.assertFalse((self.p.root/'scripts').exists())
        result=self.p.apply([op]);self.assertEqual(result['diagnostics'],[])
        self.assertEqual(len(self.p.script_files),4)
        self.assertTrue(scripts.workspace(self.p,'reward')['matches'])
        self.assertTrue(any(f['references'] for f in scripts.workspace(self.p)['files']))
        self.p.export(self.root/'out')
        self.p.undo();self.assertEqual(self.p.script_files,{})

    def test_cli_scripts_contract(self):
        self.write('on:start {}')
        response=studio.response({'command':'scripts','project':str(self.p.root),'path':self.path})
        self.assertTrue(response['ok']);self.assertEqual(response['result']['file']['hash'],digest(b'on:start {}'))

    def test_each_template_validates_and_rolls_back_in_dry_run(self):
        for kind in ('item','recipe','building','npc'):
            with self.subTest(kind=kind):
                value=self.p.apply([{'op':'template.create','template':kind,'slug':kind+'_sample','result':1}],dry_run=True)
                self.assertFalse(value['diagnostics']);self.assertFalse(self.p.script_files)

    def test_backslash_reference_and_workspace_limit(self):
        self.write('on:start { extendscript 0,0,"sys\\scripts\\studio\\missing.s2s"; }')
        self.assertTrue(any(d['code']=='missing-script' for d in self.p.validate()))
        with mock.patch.object(scripts,'MAX_TOTAL',5),self.assertRaisesRegex(ValueError,'workspace exceeds'):
            self.write('on:use {}','scripts/new.s2s')
        self.assertFalse((self.p.root/'scripts/new.s2s').exists())

    def test_recovery_preserves_new_external_changes(self):
        from studio_core.project import atomic
        self.write('on:start {}')
        def interrupt(path,data):
            if path==self.p.path:raise OSError('power loss')
            return atomic(path,data)
        with mock.patch('studio_core.project.atomic',side_effect=interrupt),self.assertRaises(OSError):
            self.write('on:start { msg "saved"; }',expected=digest(b'on:start {}'))
        (self.p.root/self.path).write_text('external work',encoding='utf8')
        with self.assertRaisesRegex(ValueError,'Recovery conflict'):Project(self.p.root)
        self.assertEqual((self.p.root/self.path).read_text('utf8'),'external work')
