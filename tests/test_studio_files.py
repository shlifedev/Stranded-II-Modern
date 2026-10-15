"""v2 file granularity, native equivalence, transactions and reversible migration."""
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest import TestCase, mock
import json
import shutil
import struct
import sys

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'scripts'))
from studio_core.project import Project, MOD, encode, digest
from studio_core.file_project import FileProject
from studio_core import file_store as store, migration, scripts, map_files
from studio_core.maps import EditableMap, new_map
from studio_core.workspace import connect


class FileProjects(TestCase):
    def setUp(self):
        temp=TemporaryDirectory();self.addCleanup(temp.cleanup)
        self.root=Path(temp.name).resolve();self.base=self.root/'base'
        directory=self.base/MOD/'sys';directory.mkdir(parents=True)
        texts={'items.inf':'# header\nid=1\nname=Wood\nunknown=keep\nid=2\nname=Stone\n',
               'objects.inf':'id=1\nname=Tree\nhealth=200\n','units.inf':'id=1\nname=Visitor\n',
               'infos.inf':'id=1\nname=Start\n','game.inf':'limit_items=10\nlimit_objects=10\nlimit_units=10\n',
               'combinations.inf':'# recipes\ncombi=start\nreq=1,1\nreq=2,1\ngen=1,1\ncombi=end\n# gap\n\n'}
        for path,text in texts.items():(directory/path).write_text(text,encoding='utf8')
        self.p=Project.create(self.root/'project',self.base,version=2)

    def apply(self,*operations): return self.p.apply(list(operations))

    def island(self):
        self.apply({'op':'map.create','name':'island','size':16},
                   {'op':'entity.add','map':'island','kind':'object','type':1},
                   {'op':'entity.add','map':'island','kind':'object','type':1})

    def test_new_project_has_small_stable_manifest_and_local_base(self):
        self.assertIsInstance(self.p,FileProject)
        before=self.p.path.read_bytes();manifest=json.loads(before)
        self.assertEqual(set(manifest),{'name','format','edition','base'})
        self.assertNotIn(str(self.base),before.decode())
        self.island();self.assertEqual(self.p.path.read_bytes(),before)
        self.assertEqual(Project(self.p.root).snapshot()['format'],'stranded-studio/2')

    def test_one_entity_edit_changes_only_its_json_and_retains_order(self):
        self.island();before=store.scan(self.p.root);raw=self.p.map_bytes('island')
        self.apply({'op':'entity.set','map':'island','kind':'object','id':1,'values':{'x':64}})
        after=store.scan(self.p.root)
        self.assertEqual([p for p in after if after[p]!=before[p]],['maps/island/entities/object-1.json'])
        self.assertEqual(self.p.map_bytes('island'),EditableMap(raw).apply({'op':'entity.set','kind':'object','id':1,'values':{'x':64}}))
        self.p.undo();self.assertEqual(store.scan(self.p.root),before)
        self.p.undo(redo=True);self.assertEqual(store.scan(self.p.root),after)
        self.apply({'op':'entity.set','map':'island','kind':'object','id':1,'values':{'x':64}})
        self.assertEqual(store.scan(self.p.root),after)

    def test_definition_edit_is_one_inf_and_preserves_comments_and_unknown_fields(self):
        self.apply({'op':'content.set','kind':'item','id':1,'properties':{'name':'First'}})
        before=store.scan(self.p.root)
        self.apply({'op':'content.set','kind':'item','id':1,'properties':{'name':'Second'}})
        after=store.scan(self.p.root)
        self.assertEqual([p for p in after if after[p]!=before[p]],['definitions/item/1.inf'])
        self.assertIn('# header',self.p.text_files()[MOD+'sys/items.inf'])
        self.assertEqual(self.p.find('item',1).properties['unknown'],['keep'])
        self.assertEqual(len([r for r in self.p.catalog() if r.kind=='item' and r.identifier==1]),1)

    def test_owned_and_shared_scripts_are_single_sources(self):
        self.island();target={'scope':'entity','map':'island','kind':'object','id':1}
        self.apply({'op':'entity.script','map':'island','kind':'object','id':1,'text':'on:use { msg "own"; }'})
        before=store.scan(self.p.root)
        self.apply({'op':'entity.script','map':'island','kind':'object','id':1,'text':'on:use { msg "new"; }'})
        after=store.scan(self.p.root)
        self.assertEqual([p for p in after if after[p]!=before[p]],['scripts/owned/maps/island/object-1.s2s'])
        self.apply({'op':'script.write','path':'scripts/shared.s2s','text':'on:use { msg "shared"; }','expectedHash':None},
                   {'op':'script.bind','path':'scripts/shared.s2s','target':target})
        self.assertIn('shared',scripts.target_text(self.p,target))
        self.apply({'op':'script.unbind','target':target})
        self.assertIsNone(scripts.binding(self.p,target));self.assertIn('shared',scripts.target_text(self.p,target))
        self.assertEqual(self.p.script_files['scripts/shared.s2s'],'on:use { msg "shared"; }')

    def test_definition_scripts_and_recipe_identity_survive_reordering(self):
        self.apply({'op':'content.set','kind':'item','id':1,'properties':{'script':'on:use { msg "item"; }'}})
        self.assertNotIn('msg', (self.p.root/'definitions/item/1.inf').read_text())
        self.assertIn('msg', self.p.find('item',1).properties['script'][0])
        created=self.apply(*[{'op':'content.create','kind':'recipe','properties':{'req':['1,1','2,1'],'gen':'1,1'}} for _ in range(2)])
        ids=[r['id'] for r in created['results']]
        metadata=list((self.p.root/'definitions/recipe').glob('*.meta.json'))
        for path in metadata:
            value=json.loads(path.read_text());value['order']=10000-value['order'];path.write_bytes(encode(value))
        self.p.load()
        self.assertTrue(all(self.p.find('recipe',identifier) for identifier in ids))
        self.apply({'op':'script.write','path':'scripts/recipe.s2s','text':'on:use {}','expectedHash':None},
                   {'op':'script.bind','path':'scripts/recipe.s2s','target':{'scope':'definition','kind':'recipe','id':ids[0]}})
        self.assertEqual(self.p.find('recipe',ids[0]).properties['script'],['on:use {}'])

    def test_clearing_an_owned_script_removes_only_its_private_file_and_undo_restores(self):
        self.island()
        self.apply({'op':'entity.script','map':'island','kind':'object','id':1,'text':'on:use {}'})
        before=store.scan(self.p.root)
        self.apply({'op':'entity.script','map':'island','kind':'object','id':1,'text':''})
        self.assertNotIn('scripts/owned/maps/island/object-1.s2s',self.p.script_files)
        self.p.undo();self.assertEqual(store.scan(self.p.root),before)

    def test_terrain_edits_only_touch_terrain_and_preserve_attachment(self):
        payload=b'attachment';attachment=bytes(255-b for b in b'unknown.bin')+b'\r\n'+struct.pack('<i',len(payload))+bytes(255-b for b in payload)
        raw=new_map(16)+attachment;path=self.root/'input.s2';path.write_bytes(raw)
        self.apply({'op':'map.import','name':'island','path':str(path)})
        self.assertEqual(self.p.map_bytes('island'),raw)
        before=store.scan(self.p.root)
        self.apply({'op':'terrain.brush','map':'island','x':8,'z':8})
        after=store.scan(self.p.root)
        self.assertEqual([p for p in after if after[p]!=before[p]],['maps/island/terrain.bin'])
        self.assertTrue(self.p.map_bytes('island').endswith(attachment))

    def test_atomic_drafts_and_external_edits(self):
        self.island();before=store.scan(self.p.root)
        draft={'op':'draft.apply','section':'values','target':{'scope':'entity','map':'island','kind':'object','id':1},
               'base':{'x':0},'changes':{'x':64}}
        path=self.p.root/'maps/island/entities/object-1.json'
        value=json.loads(path.read_bytes());value['yaw']=90;path.write_bytes(encode(value))
        old_hash=self.p.project_hash()
        with self.assertRaisesRegex(ValueError,'content changed'):self.p.apply([draft],expected_hash=old_hash)
        self.apply(draft)
        e=EditableMap(self.p.map_bytes('island')).entity('object',1)[2]
        self.assertEqual((e['x'],e['yaw']),(64,90))
        latest=store.scan(self.p.root)
        with self.assertRaises(ValueError):self.apply({'op':'content.set','kind':'item','id':1,'properties':{'name':'lost'}},dict(draft,changes={'x':128}))
        self.assertEqual(store.scan(self.p.root),latest)

    def test_external_formatting_is_not_rewritten_by_another_entity_edit(self):
        self.island()
        path=self.p.root/'maps/island/entities/object-2.json'
        value=json.loads(path.read_bytes());path.write_text(json.dumps(value,separators=(',',':')))
        raw=path.read_bytes()
        self.apply({'op':'entity.set','map':'island','kind':'object','id':1,'values':{'yaw':10}})
        self.assertEqual(path.read_bytes(),raw)

    def test_external_asset_replacement_invalidates_resource_preview(self):
        self.island();asset=self.root/'image.bmp';asset.write_bytes(b'first image')
        self.apply({'op':'asset.import','path':str(asset),'destination':MOD+'gfx/custom.bmp'})
        before=self.p.preview('island',self.base)
        (self.p.root/'resources/gfx/custom.bmp').write_bytes(b'second image')
        after=self.p.preview('island',self.base,before['token'])
        self.assertTrue(after['resourcesPending'])

    def test_missing_owned_script_is_reported_without_recreating_empty_content(self):
        self.island();self.apply({'op':'map.script','map':'island','text':'on:start {}'})
        path=self.p.root/'scripts/owned/maps/island/global.s2s';path.unlink()
        self.p.load();self.assertTrue(any(d['code']=='script-binding' for d in self.p.validate()))
        with self.assertRaisesRegex(ValueError,'restore the source'):
            self.apply({'op':'entity.set','map':'island','kind':'object','id':1,'values':{'yaw':10}})
        self.assertFalse(path.exists())

    def test_prepared_maps_reassemble_byte_for_byte(self):
        paths=list((Path(__file__).resolve().parents[1]/'build/studio/runtime'/MOD/'maps').rglob('*.s2'))
        if not paths:self.skipTest('Prepared resources are optional')
        for i,path in enumerate(paths):
            with self.subTest(map=path.name):
                raw=path.read_bytes();name=f'original-{i}'
                map_files.split(self.p,name,raw);self.p._sync_scripts();self.p._rebuild()
                self.assertEqual(self.p.map_bytes(name),raw)

    def test_base_pin_clone_reconnect_and_mismatch_block(self):
        self.island();clone=self.root/'clone'
        shutil.copytree(self.p.root,clone,ignore=shutil.ignore_patterns('.studio'))
        with self.assertRaises(ValueError):connect(clone,self.root)
        connect(clone,self.base);other=Project(clone)
        self.assertEqual(other.project_hash(),self.p.project_hash())
        (self.base/MOD/'sys/objects.inf').write_text('id=1\nname=Changed base\n')
        self.p.load();self.assertTrue(any(i['code']=='base-version' for i in self.p.validate()))
        with self.assertRaisesRegex(ValueError,'Validation|validation'):self.p.preview('island')
        with self.assertRaisesRegex(ValueError,'version mismatch'):connect(clone,self.base)
        before=store.scan(self.p.root)
        with self.assertRaisesRegex(ValueError,'version mismatch'):
            self.apply({'op':'content.set','kind':'item','id':1,'properties':{'name':'Wrong base'}})
        self.assertEqual(store.scan(self.p.root),before)

    def test_invalid_external_sources_fail_without_overwriting_them(self):
        self.island()
        path=self.p.root/'maps/island/native/header.bin';path.write_bytes(b'incomplete')
        before=store.scan(self.p.root)
        with self.assertRaisesRegex(ValueError,'Incomplete native map header'):
            self.apply({'op':'entity.set','map':'island','kind':'object','id':1,'values':{'x':64}})
        self.assertEqual(store.scan(self.p.root),before)
        self.p.path.write_text('[]')
        with self.assertRaisesRegex(ValueError,'JSON object'):Project(self.p.root)

    def test_transaction_recovery_and_external_conflict(self):
        self.island();original=store.atomic
        def fail(path,data):
            if str(path).endswith('object-2.json'):raise OSError('power loss')
            return original(path,data)
        with mock.patch.object(store,'atomic',side_effect=fail),self.assertRaises(OSError):
            self.apply(*[{'op':'entity.set','map':'island','kind':'object','id':i,'values':{'x':64}} for i in (1,2)])
        self.assertTrue((self.p.root/store.JOURNAL).exists())
        recovered=Project(self.p.root)
        self.assertEqual([e['x'] for e in EditableMap(recovered.map_bytes('island')).entities if e['kind']=='object'],[64,64])
        recovered.undo();self.assertEqual([e['x'] for e in EditableMap(recovered.map_bytes('island')).entities if e['kind']=='object'],[0,0])
        with mock.patch.object(store,'atomic',side_effect=fail),self.assertRaises(OSError):
            recovered.apply([{'op':'entity.set','map':'island','kind':'object','id':2,'values':{'x':32}}])
        path=self.p.root/'maps/island/entities/object-2.json';path.write_text('external work')
        with self.assertRaisesRegex(ValueError,'Recovery conflict'):Project(self.p.root)
        self.assertEqual(path.read_text(),'external work')

    def test_template_preview_export_and_one_undo(self):
        before=store.scan(self.p.root)
        op={'op':'template.create','template':'scenario','slug':'rescue','name':'Rescue','source':1,'npcSource':1}
        result=self.p.apply([op],dry_run=True);self.assertFalse(result['diagnostics']);self.assertEqual(store.scan(self.p.root),before)
        result=self.apply(op);self.assertFalse(result['diagnostics'])
        self.assertIn('rescue',self.p.snapshot()['maps'])
        self.assertEqual(len(self.p.script_files),4)
        self.assertIn('$rescue_done',EditableMap(self.p.map_bytes('rescue')).summary()['script'])
        self.assertIn('heal',self.p.find('item',result['results'][0]['item']).properties['script'][0])
        self.p.preview('rescue',self.base)
        self.p.export(self.root/'export')
        self.p.undo();self.assertEqual(store.scan(self.p.root),before)

    def test_migration_is_explicit_byte_equivalent_and_restorable(self):
        old=Project.create(self.root/'legacy',self.base,version=1)
        old.apply([{'op':'map.create','name':'island','size':16},
                   {'op':'entity.add','map':'island','kind':'object','type':1},
                   {'op':'entity.script','map':'island','kind':'object','id':1,'text':'on:use {\n msg "Hi";\n}'},
                   {'op':'content.set','kind':'item','id':1,'properties':{'script':'on:use { msg "Wood"; }'}},
                   {'op':'locale.set','language':'en','key':'example','text':'Hello'},
                   {'op':'script.write','path':'scripts/map.s2s','text':'on:start {}','expectedHash':None},
                   {'op':'script.bind','path':'scripts/map.s2s','target':{'scope':'map','map':'island'}}])
        before=store.scan(old.root);raw=old.map_bytes('island');definitions=old.text_files()
        self.assertTrue(migration.migrate(old.root)['dryRun']);self.assertEqual(store.scan(old.root),before)
        report=migration.migrate(old.root,False);new=Project(old.root)
        self.assertIsInstance(new,FileProject);self.assertEqual(new.map_bytes('island'),raw);self.assertEqual(new.text_files(),definitions)
        self.assertTrue(migration.migrate(old.root,False)['alreadyConverted'])
        migration.restore(old.root,report['backup'],False)
        self.assertEqual(store.scan(old.root),before);self.assertNotIsInstance(Project(old.root),FileProject)
        self.assertTrue(migration.restore(old.root,report['backup'],False)['alreadyRestored'])

    def test_restore_refuses_later_work_and_conversion_refuses_collisions(self):
        old=Project.create(self.root/'legacy',self.base,version=1)
        (old.root/'maps').mkdir();(old.root/'maps/keep.txt').write_text('User file')
        with self.assertRaisesRegex(ValueError,'already contain'):migration.migrate(old.root,False)
        (old.root/'maps/keep.txt').unlink()
        report=migration.migrate(old.root,False);new=Project(old.root)
        new.apply([{'op':'content.set','kind':'item','id':1,'properties':{'name':'Later'}}])
        with self.assertRaisesRegex(ValueError,'changed after'):migration.restore(old.root,report['backup'],False)
        self.assertEqual(Project(old.root).find('item',1).properties['name'],['Later'])

    def test_interrupted_migration_recovers_and_preserves_the_original_backup(self):
        old=Project.create(self.root/'legacy',self.base,version=1)
        old.apply([{'op':'map.create','name':'island','size':16},
                   {'op':'content.set','kind':'item','id':1,'properties':{'name':'Custom'}}])
        before=store.scan(old.root);native=old.map_bytes('island');original=store.atomic
        def fail(path,data):
            if path.name=='terrain.bin':raise OSError('power loss')
            return original(path,data)
        with mock.patch.object(store,'atomic',side_effect=fail),self.assertRaises(OSError):
            migration.migrate(old.root,False)
        self.assertEqual(json.loads(old.path.read_bytes())['format'],'stranded-studio/1')
        recovered=Project(old.root)
        self.assertIsInstance(recovered,FileProject)
        self.assertEqual(recovered.map_bytes('island'),native)
        backup=migration.backups(old.root)['backups'][0]['id']
        migration.restore(old.root,backup,False)
        self.assertEqual(store.scan(old.root),before)

    def test_repeated_restore_finishes_interrupted_local_connection_cleanup(self):
        old=Project.create(self.root/'legacy',self.base,version=1)
        local=old.root/'.studio/local.json';local.parent.mkdir(exist_ok=True)
        original=json.dumps({'base':str(self.base),'note':'preserve'}).encode()
        local.write_bytes(original)
        report=migration.migrate(old.root,False)
        with mock.patch.object(migration,'atomic',side_effect=OSError('power loss')),self.assertRaises(OSError):
            migration.restore(old.root,report['backup'],False)
        self.assertNotIsInstance(Project(old.root),FileProject)
        self.assertNotEqual(local.read_bytes(),original)
        self.assertTrue(migration.restore(old.root,report['backup'],False)['alreadyRestored'])
        self.assertEqual(local.read_bytes(),original)

    def test_conversion_rechecks_original_sources_before_publishing(self):
        old=Project.create(self.root/'legacy',self.base,version=1)
        old.apply([{'op':'map.create','name':'island','size':16}])
        source=old.root/old.data['maps']['island']['source'];before=old.path.read_bytes()
        convert=migration.converted
        def changed(project):
            new=convert(project);source.write_bytes(b'external replacement');return new
        with mock.patch.object(migration,'converted',side_effect=changed),self.assertRaisesRegex(ValueError,'source changed'):
            migration.migrate(old.root,False)
        self.assertEqual(old.path.read_bytes(),before)
        self.assertEqual(source.read_bytes(),b'external replacement')
