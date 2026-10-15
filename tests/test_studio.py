"""Authoring transactions and native resource preservation; no GUI automation."""
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest import TestCase, mock
import base64
import hashlib
import json
import struct
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'scripts'))
from studio_core import content
from studio_core.maps import EditableMap, new_map
from studio_core.project import Project, MOD, safe, resource_path
from s2map import Map
from locales import catalog
import studio
import web_build
import web_audio


class NativeMaps(TestCase):
    def setUp(self):
        self.raw = new_map(16)

    def test_new_map_has_native_player_start_and_roundtrips(self):
        model = EditableMap(self.raw)
        self.assertEqual(Map(self.raw).write(),self.raw)
        self.assertEqual(model.size,16)
        self.assertEqual(model.entities[0]['type'],1)
        self.assertAlmostEqual(model.world_height(0,0),160,places=3)

    def test_transform_only_changes_addressed_float_bytes(self):
        raw = EditableMap(self.raw).apply({'op':'entity.add','kind':'object','type':1})
        model = EditableMap(raw)
        pos,_ = model.entity('object',1)[3]['x']
        changed = model.apply({'op':'entity.set','kind':'object','id':1,'values':{'x':64.5}})
        self.assertEqual(changed[:pos],raw[:pos])
        self.assertEqual(changed[pos+4:],raw[pos+4:])
        self.assertEqual(EditableMap(changed).entity('object',1)[2]['x'],64.5)

    def test_terrain_brush_preserves_every_other_section(self):
        model = EditableMap(self.raw)
        changed = model.apply({'op':'terrain.brush','x':8,'z':8,'radius':2,'strength':.02})
        end = model.heights_start+(model.size+1)**2*4
        self.assertEqual(changed[:model.heights_start],self.raw[:model.heights_start])
        self.assertEqual(changed[end:],self.raw[end:])
        self.assertAlmostEqual(EditableMap(changed).height(8,8)-model.height(8,8),.02)
        self.assertEqual(EditableMap(changed).height(0,0),model.height(0,0))

    def test_global_script_preserves_attachments_and_numeric_bytes(self):
        name = bytes(255-b for b in b'custom.inf')+b'\r\n'
        attachment = name+struct.pack('<i',5)+bytes(255-b for b in b'hello')
        raw = self.raw+attachment
        changed = EditableMap(raw).apply({'op':'map.script','text':'on:start {\n msg "섬";\n}'})
        model = EditableMap(changed)
        self.assertIn('섬',model.summary()['script'])
        self.assertEqual(changed[-len(attachment):],attachment)
        self.assertEqual(model.source.fields[-1].data,b'hello')
        self.assertEqual(model.apply({'op':'map.script','text':''}),raw)

    def test_entity_script_clear_restores_original_and_allows_delete(self):
        op = {'op':'entity.script','kind':'info','id':1,'text':'on:use {\n msg "hello";\n}'}
        changed = EditableMap(self.raw).apply(op)
        with self.assertRaisesRegex(ValueError,'attached'):
            EditableMap(changed).apply({'op':'entity.remove','kind':'info','id':1})
        self.assertEqual(EditableMap(changed).summary()['entities'][0]['script'],op['text'])
        restored = EditableMap(changed).apply(dict(op,text=''))
        self.assertEqual(restored,self.raw)
        self.assertEqual(EditableMap(EditableMap(restored).apply({'op':'entity.remove','kind':'info','id':1})).entities,[])

    def test_info_string_length_can_change_without_losing_trailer(self):
        changed = EditableMap(self.raw).apply({'op':'entity.set','kind':'info','id':1,'values':{'vars':'1,2,새 설정','yaw':90}})
        entity = EditableMap(changed).entities[0]
        self.assertEqual(entity['vars'],'1,2,새 설정')
        self.assertEqual(entity['yaw'],90)
        self.assertEqual(Map(changed).write(),changed)

    def test_rejects_unstored_object_height_and_invalid_numbers(self):
        raw = EditableMap(self.raw).apply({'op':'entity.add','kind':'object','type':1})
        for values in ({'y':32},{'x':float('nan')},{'x':float('inf')},{'daytimer':1.5},{'health':-1}):
            with self.subTest(values=values),self.assertRaises(ValueError):
                EditableMap(raw).apply({'op':'entity.set','kind':'object','id':1,'values':values})

    def test_prepared_original_maps_roundtrip(self):
        maps = list((ROOT/'build/studio/runtime'/MOD/'maps').rglob('*.s2'))
        if not maps:self.skipTest('Prepared official resources are optional')
        for path in maps:
            with self.subTest(map=path.name):
                raw=path.read_bytes();model=EditableMap(raw)
                self.assertEqual(model.source.write(),raw)
                # Resetting a numeric field must not alter any map byte.
                if model.entities:
                    e=model.entities[0]
                    self.assertEqual(model.apply({'op':'entity.set','kind':e['kind'],'id':e['id'],'values':{'x':e['x']}}),raw)


class Definitions(TestCase):
    def test_script_and_description_do_not_create_phantom_records(self):
        text='id=1\nname=Original\nscript=start\nid=99\nname=script\nscript=end\ndescription=start\nid=55\ndescription=end\n# retained\nunknown=xyz\nid=2\nname=Next\n'
        records=content.records('items_test.inf',text)
        self.assertEqual([r.identifier for r in records],[1,2])
        changed=content.edit(records[0].text,{'name':'섬 아이템'})
        self.assertEqual(changed.replace('name=섬 아이템','name=Original'),records[0].text)

    def test_recipe_repeats_remain_before_end_marker(self):
        text='combi=start\nreq=1,1\nreq=2,1\ncombi=end\n'
        changed=content.edit(text,{'req':['1,3','2,2'],'gen':['3,1'],'script':'on:use { msg "x"; }'})
        self.assertTrue(changed.endswith('combi=end\n'))
        record=content.records('combinations.inf',changed)[0]
        self.assertEqual(record.properties['req'],['1,3','2,2'])
        self.assertEqual(record.properties['gen'],['3,1'])


class Projects(TestCase):
    def setUp(self):
        directory=TemporaryDirectory();self.addCleanup(directory.cleanup)
        self.root=Path(directory.name);self.base=self.root/'base'
        files={
            MOD+'sys/items.inf':'id=1\nname=Log\nmodel=gfx/test.b3d\nconst=wood\nid=2\nname=Stone\n',
            MOD+'sys/objects.inf':'id=1\nname=Tree\nmodel=gfx/test.b3d\nhealth=250\n',
            MOD+'sys/units.inf':'id=1\nname=Creature\n',
            MOD+'sys/infos.inf':'id=1\nname=Player start\n',
            MOD+'sys/game.inf':'limit_items=10\nlimit_objects=10\nlimit_units=10\n',
            MOD+'gfx/test.b3d':'test model',
            'core/locales/en.tsv':'item.name\tLog\n',
            'core/locales/ko.tsv':'item.name\t나무\n',
            MOD+'saves/private.sav':'DO NOT COPY',
            MOD+'sys/settings.cfg':'DO NOT COPY',
        }
        for name,value in files.items():
            path=self.base/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(value,encoding='utf8')
        self.original={str(p.relative_to(self.base)):p.read_bytes() for p in self.base.rglob('*') if p.is_file()}
        self.project=Project.create(self.root/'project',self.base,version=1)

    def tearDown(self):
        self.assertEqual(self.original,{str(p.relative_to(self.base)):p.read_bytes() for p in self.base.rglob('*') if p.is_file()})

    def test_clone_allocates_new_id_without_duplicate_const(self):
        result=self.project.apply([{'op':'content.clone','kind':'item','source':1,'properties':{'name':'New log'}}])
        identifier=result['results'][0]['id']
        self.assertEqual(identifier,3)
        self.assertNotIn('const',self.project.find('item',identifier).properties)
        self.assertEqual(self.project.find('item',1).properties['name'],['Log'])

    def test_failed_batch_rolls_back_all_files_and_blobs(self):
        before=self.project.path.read_bytes()
        with self.assertRaises(ValueError):
            self.project.apply([{'op':'map.create','name':'island'},{'op':'content.clone','kind':'item','source':99}])
        self.assertEqual(self.project.path.read_bytes(),before)
        self.assertFalse((self.project.root/'sources').exists())

    def test_dry_run_has_no_project_or_blob_writes(self):
        before=self.project.path.read_bytes()
        result=self.project.apply([{'op':'map.create','name':'island'}],dry_run=True)
        self.assertTrue(result['dryRun'])
        self.assertEqual(self.project.path.read_bytes(),before)
        self.assertFalse((self.project.root/'sources').exists())

    def test_revision_rejects_concurrent_agent_and_gui(self):
        second=Project(self.project.root)
        self.project.apply([{'op':'map.create','name':'island'}],expected=0)
        with self.assertRaisesRegex(ValueError,'Revision conflict'):
            second.apply([{'op':'map.create','name':'other'}],expected=0)
        self.assertEqual(list(Project(self.project.root).data['maps']),['island'])

    def test_undo_redo_restores_exact_map_and_increments_revision(self):
        self.project.apply([{'op':'map.create','name':'island'}])
        original=self.project.map_bytes('island')
        self.project.apply([{'op':'terrain.brush','map':'island','x':16,'z':16}])
        edited=self.project.map_bytes('island');self.assertNotEqual(original,edited)
        self.project.undo();self.assertEqual(self.project.map_bytes('island'),original)
        self.assertEqual(self.project.data['revision'],3)
        self.project.undo(redo=True);self.assertEqual(self.project.map_bytes('island'),edited)
        self.assertEqual(self.project.data['revision'],4)

    def test_invalid_reference_blocks_export_without_creating_directory(self):
        result=self.project.apply([{'op':'content.create','kind':'recipe','properties':{'req':['1,2','900,1'],'gen':['2,1']}}])
        self.assertTrue(result['diagnostics'])
        with self.assertRaisesRegex(ValueError,'Export blocked'):
            self.project.export(self.root/'out')
        self.assertFalse((self.root/'out').exists())

    def test_native_export_and_preview_exclude_personal_data(self):
        self.project.apply([{'op':'map.create','name':'island'},{'op':'content.clone','kind':'item','source':1}])
        self.project.export(self.root/'out')
        output=self.root/'out/overrides'/MOD/'maps/island.s2'
        self.assertEqual(output.read_bytes(),self.project.map_bytes('island'))
        files=self.project.preview('island')['files']
        self.assertEqual(base64.b64decode(files[MOD+'maps/studio.s2']),output.read_bytes())
        self.assertFalse(any('private' in p or 'settings' in p for p in files))
        self.assertEqual(len(list((self.root/'out/overrides').rglob('*.inf'))),1)

    def test_localization_roundtrips_escapes_and_fallback(self):
        text='새 이름\n줄\t탭\\끝'
        self.project.apply([{'op':'map.create','name':'island'},
                            {'op':'locale.set','language':'en','key':'studio.name','text':'New'},
                            {'op':'locale.set','language':'ko','key':'studio.name','text':text}])
        self.project.export(self.root/'out')
        self.assertEqual(catalog(self.root/'out/locales/ko.tsv')['studio.name'],text)
        files=self.project.preview('island')['files']
        self.assertEqual(catalog(Path('unused'),base64.b64decode(files['core/locales/ko.tsv']).decode())['studio.name'],text)
        self.assertEqual(self.project.localizer()('[[loc:studio.name]]'),text)

    def test_protected_paths_and_existing_export_are_rejected(self):
        for name in ('../outside','/outside','C:/outside','mods/Stranded II/saves/a.sav','mods/Stranded II/sys/settings.inf'):
            with self.subTest(path=name),self.assertRaises(ValueError):resource_path(name)
        external=self.root/'external';external.mkdir()
        (self.project.root/'linked').symlink_to(external,target_is_directory=True)
        with self.assertRaises(ValueError):safe(self.project.root,'linked/file')
        with self.assertRaises(ValueError):self.project.export(self.base)
        with self.assertRaises(ValueError):Project.create(self.base/'new',self.base)

    def test_serve_recovers_after_malformed_requests(self):
        requests=[{'id':1,'command':'revision','project':str(self.project.root)},[],
                  {'id':3,'command':'apply','project':str(self.project.root),'operations':[None]},
                  {'id':4,'command':'schema'}]
        result=subprocess.run([sys.executable,ROOT/'scripts/studio.py','serve'],input=''.join(json.dumps(r)+'\n' for r in requests),text=True,capture_output=True,check=True)
        lines=[json.loads(line) for line in result.stdout.splitlines()]
        self.assertEqual([r['ok'] for r in lines],[True,False,False,True])
        self.assertEqual(lines[0]['id'],1);self.assertEqual(lines[-1]['id'],4)
        self.assertEqual(result.stderr,'')

    def test_snapshot_after_undo_of_active_map_is_empty(self):
        self.project.apply([{'op':'map.create','name':'island'}]);self.project.undo()
        value=self.project.snapshot('island')
        self.assertIsNone(value['map']);self.assertIsNone(value['activeMap'])

    def test_preview_delta_tracks_final_state_across_processes_undo_and_redo(self):
        self.project.apply([{'op':'map.create','name':'island','size':16}])
        full = self.project.preview('island')
        self.project.apply([{'op':'entity.add','map':'island','kind':'object','type':1,'values':{'x':64}}])
        # A fresh Project is the same path used by a separately launched CLI.
        other = Project(self.project.root)
        added = other.preview('island', since=full['token'])
        self.assertEqual(added['mode'],'delta')
        self.assertEqual(added['changes'], {'removed':0,'upserted':1,'heights':0})
        self.assertEqual(list(added['files']),[MOD+'maps/studio.s2'])
        packet = base64.b64decode(added['patch'])
        self.assertEqual(struct.unpack_from('<iiiiBii',packet), (1,0,0,1,1,1,1))
        self.assertEqual(struct.unpack_from('<f',packet,25)[0],64)
        other.undo()
        removed = other.preview('island', since=added['token'])
        self.assertEqual(removed['changes'], {'removed':1,'upserted':0,'heights':0})
        other.undo(redo=True)
        # Skipped intermediate changes must not manufacture delete/add operations.
        final = other.preview('island', since=added['token'])
        self.assertEqual(final['changes'], {'removed':0,'upserted':0,'heights':0})
        self.assertEqual(final['files'], added['files'])

    def test_terrain_patch_contains_only_changed_samples_and_script_still_reaches_play(self):
        self.project.apply([{'op':'map.create','name':'island','size':16}])
        full = self.project.preview('island')
        self.project.apply([{'op':'terrain.brush','map':'island','x':8,'z':8,'radius':1,'strength':.01},
                            {'op':'map.script','map':'island','text':'on:start { msg "latest"; }'}])
        delta = self.project.preview('island', since=full['token'])
        self.assertEqual(delta['changes'], {'removed':0,'upserted':0,'heights':1})
        packet = base64.b64decode(delta['patch'])
        self.assertEqual(struct.unpack_from('<iiiii',packet), (1,0,1,8,8))
        self.assertAlmostEqual(struct.unpack_from('<f',packet,20)[0], .56)
        raw = base64.b64decode(delta['files'][MOD+'maps/studio.s2'])
        self.assertEqual(raw,self.project.map_bytes('island'))
        self.assertIn('latest',EditableMap(raw).summary()['script'])

    def test_resources_wait_for_explicit_full_apply_and_new_definition_is_blocked(self):
        self.project.apply([{'op':'map.create','name':'island','size':16}])
        full = self.project.preview('island')
        self.project.apply([{'op':'content.clone','kind':'object','source':1}])
        pending = self.project.preview('island', since=full['token'])
        self.assertTrue(pending['resourcesPending'])
        self.assertFalse(pending['resourcesRequired'])
        self.assertNotIn('object/2',pending['available'])
        self.assertEqual(list(pending['files']),[MOD+'maps/studio.s2'])
        self.project.apply([{'op':'entity.add','map':'island','kind':'object','type':2}])
        blocked = self.project.preview('island', since=pending['token'])
        self.assertTrue(blocked['resourcesRequired'])
        applied = self.project.preview('island')
        self.assertFalse(applied['resourcesPending'])
        self.assertIn('object/2',applied['available'])
        self.assertTrue(any('objects_studio.inf' in name for name in applied['files']))
        self.project.undo()  # undo placement
        self.project.undo()  # undo definition: original resources again
        restored = self.project.preview('island', since=full['token'])
        self.assertFalse(restored['resourcesPending'])

    def test_preview_rejects_wrong_or_missing_baseline_without_modifying_project(self):
        self.project.apply([{'op':'map.create','name':'one','size':16},{'op':'map.create','name':'two','size':16}])
        full = self.project.preview('one')
        saved = self.project.path.read_bytes()
        for name,token in [('two',full['token']),('one','0'*64),('one','../outside')]:
            with self.subTest(name=name,token=token),self.assertRaises(ValueError):
                self.project.preview(name,since=token)
        self.assertEqual(saved,self.project.path.read_bytes())

    def test_group_move_preserves_relative_height_offsets_and_is_one_undo(self):
        self.project.apply([{'op':'map.create','name':'island','size':16},
                            {'op':'entity.add','map':'island','kind':'unit','type':1,'id':100,'values':{'x':0,'y':180,'z':0}},
                            {'op':'entity.add','map':'island','kind':'item','type':1,'id':1,'values':{'x':32,'y':230,'z':0}}])
        before = self.project.map_bytes('island');model = EditableMap(before)
        selection = [{'kind':'unit','id':100},{'kind':'item','id':1}]
        self.project.apply([{'op':'entities.move','map':'island','selection':selection,'delta':{'x':320,'z':0},'terrain':True}],expected=1)
        moved = EditableMap(self.project.map_bytes('island'))
        for identity in selection:
            a=model.entity(**{'kind':identity['kind'],'identifier':identity['id']})[2]
            b=moved.entity(identity['kind'],identity['id'])[2]
            self.assertEqual(b['x']-a['x'],320)
            self.assertAlmostEqual(a['y']-model.world_height(a['x'],a['z']),b['y']-moved.world_height(b['x'],b['z']),places=3)
        self.project.undo(expected=2)
        self.assertEqual(self.project.map_bytes('island'),before)
        # A drag using the pre-undo revision must not overwrite another writer.
        with self.assertRaisesRegex(ValueError,'Revision conflict'):
            self.project.apply([{'op':'entities.move','map':'island','selection':selection,'delta':{'y':16}}],expected=1)
        self.assertEqual(self.project.map_bytes('island'),before)

    def test_mixed_object_group_rejects_y_and_axis_move_keeps_unit_height(self):
        self.project.apply([{'op':'map.create','name':'island','size':16},
                            {'op':'entity.add','map':'island','kind':'object','type':1},
                            {'op':'entity.add','map':'island','kind':'unit','type':1,'id':100,'values':{'y':200}}])
        selection=[{'kind':'object','id':1},{'kind':'unit','id':100}]
        with self.assertRaisesRegex(ValueError,'do not store Y'):
            self.project.apply([{'op':'entities.move','map':'island','selection':selection,'delta':{'y':32}}])
        self.project.apply([{'op':'entities.move','map':'island','selection':selection,'delta':{'x':256}}])
        self.assertEqual(EditableMap(self.project.map_bytes('island')).entity('unit',100)[2]['y'],200)


class BuildBoundaries(TestCase):
    def test_preload_escapes_at_in_repository_path(self):
        with TemporaryDirectory(prefix='studio@checkout-') as directory:
            build=Path(directory)
            with mock.patch.multiple(web_build,BUILD=build,SITE=build/'site'), \
                 mock.patch.object(web_build,'asset_files',return_value=[]), \
                 mock.patch('web_audio.prepare',return_value={}), \
                 mock.patch('web_toolchain.run') as run:
                # pack_assets appends a runtime hook after the generated loader.
                (build/'site').mkdir();(build/'site/assets.js').write_text('')
                web_build.pack_assets({})
            argument=run.call_args.args[0][-1]
            split=argument.replace('@@','__').index('@')
            self.assertEqual(argument[:split].replace('@@','@'),str(build/'package'))
            self.assertEqual(argument[split+1:],'/game')

    def test_studio_source_graph_uses_only_extended_profile(self):
        from studio_core.engine import source_hashes
        files=source_hashes()
        self.assertIn('src/engine/editions/extended/profile.bb',files)
        self.assertFalse(any('/editions/classic/' in path for path in files))
        self.assertNotIn('src/engine/includes/engine_loop.bb',files)

    def test_native_audio_tool_identity_is_actual_binary_hash(self):
        with TemporaryDirectory() as directory:
            tool=Path(directory)/'ffmpeg';tool.write_bytes(b'native-tool')
            with mock.patch.object(web_audio.os,'name','posix'),mock.patch.object(web_audio,'tools',return_value=(tool,)):
                self.assertEqual(web_audio.tool_identity(),{'native':{'ffmpeg':hashlib.sha256(b'native-tool').hexdigest()}})
