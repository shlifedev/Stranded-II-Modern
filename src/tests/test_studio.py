"""Authoring transactions and native resource preservation; no GUI automation."""
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest import TestCase, mock
import base64
import hashlib
import json
import io
import wave
import struct
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'src/devtools'))
from studio_core import content
from studio_core.maps import EditableMap, new_map, string
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
        self.assertAlmostEqual(model.world_height(0,0),156.862793,places=3)
        self.assertAlmostEqual(model.entities[0]['y'],model.world_height(0,0),places=3)

    def test_ground_placement_matches_native_rounded_samples_and_wrapped_edges(self):
        model = EditableMap(self.raw)
        data = bytearray(self.raw)
        for gx, gz, height in [(0, 0, .01), (16, 16, .12), (0, 7, .11),
                               (16, 7, .91), (7, 0, .02), (7, 16, .88)]:
            struct.pack_into('<f', data, model.heights_start + (gx * 17 + gz) * 4, height)
        model = EditableMap(data)
        # Values read from TerrainY with the game's load order, scale and floor.
        for x, z, expected in [(-512, -512, -1223.529419), (-512, -64, 1311.372559),
                               (-64, -512, 1210.980469), (512, -64, 1311.372559),
                               (-768, -768, -1223.529419)]:
            placed = model.apply({'op':'entity.add', 'kind':'unit', 'type':1, 'values':{'x':x,'z':z}})
            self.assertAlmostEqual(EditableMap(placed).entity('unit',100)[2]['y'], expected, places=3)

    def test_summary_uses_first_script_for_each_entity_class_and_id(self):
        data = EditableMap(self.raw).apply({'op':'entity.add','kind':'item','type':1,'id':1})
        model = EditableMap(data)
        def extension(parent, mode, source):
            return struct.pack('<BBii', 0, *parent, mode) + string(b'') + string(source) + string(b'')
        records = [extension((4,1), 1, b'other mode'),
                   extension((3,1), 0, b'item'),
                   extension((4,1), 0, 'first\x1f한글'.encode()),
                   extension((4,1), 0, b'later'),
                   extension((3,99), 0, b'\xff')]
        raw = (data[:model.extension_start] + struct.pack('<i', len(records)) +
               b''.join(records) + data[model.extension_end:])
        summary = EditableMap(raw).summary()
        self.assertEqual([(e['kind'], e['id'], e['script']) for e in summary['entities']],
                         [('item', 1, 'item'), ('info', 1, 'first\n한글')])

    def test_transform_only_changes_addressed_float_bytes(self):
        raw = EditableMap(self.raw).apply({'op':'entity.add','kind':'object','type':1})
        model = EditableMap(raw)
        pos,_ = model.entity('object',1)[3]['x']
        changed = model.apply({'op':'entity.set','kind':'object','id':1,'values':{'x':64.5}})
        self.assertEqual(changed[:pos],raw[:pos])
        self.assertEqual(changed[pos+4:],raw[pos+4:])
        self.assertEqual(EditableMap(changed).entity('object',1)[2]['x'],64.5)

    def test_group_move_matches_individual_edits_byte_for_byte(self):
        raw = self.raw
        selection = [{'kind': 'info', 'id': 1}]
        for kind, identifier in [('object', 1), ('unit', 100), ('item', 1)]:
            raw = EditableMap(raw).apply({'op': 'entity.add', 'type': 1, 'kind': kind, 'id': identifier})
            selection.append({'kind': kind, 'id': identifier})
        model = EditableMap(raw)
        expected = raw
        for selected in selection:
            entity = model.entity(selected['kind'], selected['id'])[2]
            values = {'x': entity['x'] + 320, 'z': entity['z'] - 64}
            if selected['kind'] != 'object':
                values['y'] = entity['y'] + (model.world_height(values['x'], values['z'])
                                             - model.world_height(entity['x'], entity['z']))
            expected = EditableMap(expected).apply({'op': 'entity.set', **selected, 'values': values})
        moved = model.apply({'op': 'entities.move', 'selection': selection,
                             'delta': {'x': 320, 'z': -64}, 'terrain': True})
        self.assertEqual(moved, expected)

    def test_terrain_brush_preserves_every_other_section(self):
        model = EditableMap(self.raw)
        changed = model.apply({'op':'terrain.brush','x':8,'z':8,'radius':2,'strength':.02})
        end = model.heights_start+(model.size+1)**2*4
        self.assertEqual(changed[:model.heights_start],self.raw[:model.heights_start])
        self.assertEqual(changed[end:],self.raw[end:])
        self.assertAlmostEqual(EditableMap(changed).height(8,8)-model.height(8,8),.02)
        self.assertEqual(EditableMap(changed).height(0,0),model.height(0,0))

    def test_edge_brush_persists_the_cell_rendered_by_the_game(self):
        from studio_core.preview import encode_patch
        raw = bytearray(self.raw)
        base = EditableMap(raw)
        struct.pack_into('<f', raw, base.heights_start + (16*17+16)*4, .75)
        before = EditableMap(bytes(raw))
        self.assertEqual(before.apply({'op':'terrain.brush', 'x':0, 'z':0,
                                       'radius':1, 'strength':0}), bytes(raw))
        after = EditableMap(before.apply({'op':'terrain.brush','mode':'lower',
                                          'x':0,'z':0,'radius':1,'strength':.25}))
        for gx in (0, 16):
            for gz in (0, 16):
                self.assertEqual(after.height(gx, gz), .5)
        for previous, following, height in ((before, after, .5), (after, before, .75)):
            packet, counts = encode_patch(previous, following)
            self.assertEqual(counts['heights'], 1)
            self.assertEqual(struct.unpack_from('<iif', packet, 12), (16, 16, height))
        # The shared vertex receives the nearest weight once, even for a large brush.
        wide = EditableMap(after.apply({'op':'terrain.brush','x':4,'z':0,'radius':16,'strength':.25}))
        self.assertEqual(wide.height(0,0), .5 + .25*.75)
        self.assertEqual(wide.height(16,16), wide.height(0,0))

    def test_terrain_patch_handles_shifted_sections_and_numeric_equality(self):
        from studio_core.preview import encode_patch
        before = EditableMap(self.raw)
        shifted = EditableMap(before.apply({'op':'map.script', 'text':'on:start { msg "island"; }'}))
        self.assertNotEqual(before.heights_start, shifted.heights_start)
        def samples(model, updates):
            raw = bytearray(model.data)
            for x, z, height in updates:
                struct.pack_into('<f', raw, model.heights_start + (x*17+z)*4, height)
            return EditableMap(bytes(raw))
        old = samples(before, [(3,4,0.0), (7,1,0.0)])
        new = samples(shifted, [(3,4,-0.0), (7,1,-0.0), (7,8,.75), (13,4,.25)])
        patch, counts = encode_patch(old, new)
        self.assertEqual(counts['heights'], 2)
        self.assertEqual(list(struct.iter_unpack('<iif',patch[12:-4])), [(7,8,.75),(13,4,.25)])

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

    def test_map_edits_splice_only_selected_script_and_attachment_spans(self):
        def attachment(name, body):
            return (bytes(255 - b for b in name) + b'\r\n' + struct.pack('<i', len(body))
                    + bytes(255 - b for b in body))
        first = attachment(b'first.dat', bytes(range(256)))
        last = attachment(b'last.dat', b'keep')
        raw = self.raw + first + last
        model = Map(raw)
        script = next(field for field in model.fields if field.key == 'global_script')
        text = '새 섬'.encode()
        edited = model.write({'global_script': text, 'attachment/first.dat': b''})
        expected = (self.raw[:script.start] + struct.pack('<i', len(text)) + text + self.raw[script.end:]
                    + attachment(b'first.dat', b'') + last)
        self.assertEqual(edited, expected)
        self.assertEqual(Map(edited).write({'global_script': script.data,
                                           'attachment/first.dat': bytes(range(256))}), raw)
        self.assertEqual(model.write(), raw)
        with self.assertRaises(AssertionError):
            Map(raw[:-1])

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

    def test_recipe_spans_exclude_gaps_and_keep_script_delimiters(self):
        path = 'combinations_custom.inf'
        first = 'combi=start\r\nscript=start\r\ncombi=end\r\nscript=end\r\nreq=24,1\r\ncombi=end\r\n'
        second = 'combi=start\r\nreq=25,1\r\ncombi=end'
        gap = '# comment outside both recipes\r\ngen=999,1\r\n'
        text = 'author=custom\r\n' + first + gap + second
        records = content.records(path, text)
        self.assertEqual([r.identifier for r in records], [path+'#0', path+'#1'])
        self.assertEqual([text[r.start:r.end] for r in records], [first, second])
        self.assertEqual([r.text for r in records], [first, second])
        self.assertEqual(records[0].properties, {'script':['combi=end'], 'req':['24,1']})
        self.assertEqual(records[1].properties, {'req':['25,1']})
        with self.assertRaisesRegex(ValueError, 'unterminated combination'):
            content.records(path, 'combi=start\n' + second)

    def test_recipe_repeats_remain_before_end_marker(self):
        text='combi=start\nreq=1,1\nreq=2,1\ncombi=end\n'
        changed=content.edit(text,{'req':['1,3','2,2'],'gen':['3,1'],'script':'on:use { msg "x"; }'})
        self.assertTrue(changed.endswith('combi=end\n'))
        record=content.records('combinations.inf',changed)[0]
        self.assertEqual(record.properties['req'],['1,3','2,2'])
        self.assertEqual(record.properties['gen'],['3,1'])


class NativeDefinitionFiles(TestCase):
    def test_mixed_case_extensions_survive_edits_and_export(self):
        with TemporaryDirectory() as temporary:
            root = Path(temporary)
            base = root / 'base'
            directory = base / MOD / 'sys'
            directory.mkdir(parents=True)
            inputs = {'infos_scene.InF':'id=2\nname=Marker\n',
                      'items_studio.INF':'id=1\nname=Log\n',
                      'units_addon.iNf':'id=1\nname=Bird\n',
                      'objects_addon.Inf':'id=1\nname=Tree\n',
                      'buildings_addon.inF':'id=1\nobjectid=1\n',
                      'combinations_addon.INF':'combi=start\nreq=1,1\nreq=1,1\ngen=1,1\ncombi=end\n',
                      'Items_ignored.INF':'id=9\nname=Ignored\n'}
            for name, data in inputs.items():
                (directory/name).write_text(data, encoding='utf8')
            (directory/'items_archive.INF').mkdir()
            project = Project.create(root/'project', base)
            self.assertEqual({record.kind for record in project.catalog()}, set(content.PREFIXES))
            self.assertEqual(len(project.catalog()), 6)
            recipe = MOD+'sys/combinations_addon.INF#0'
            self.assertEqual(project.find('recipe', recipe).properties['gen'], ['1,1'])
            project.apply([{'op':'content.set','kind':'info','id':2,'properties':{'name':'Changed'}},
                           {'op':'content.clone','kind':'item','source':1}])
            project.export(root/'output')
            exported = root/'output/overrides'/MOD/'sys'
            self.assertEqual({p.name for p in exported.iterdir()}, {'infos_scene.InF','items_studio.INF'})
            self.assertEqual((exported/'infos_scene.InF').read_text('utf8'), 'id=2\nname=Changed\n')
            items = content.records('items_studio.INF', (exported/'items_studio.INF').read_text('utf8'))
            self.assertEqual([record.identifier for record in items], [1, 2])
            self.assertEqual((directory/'infos_scene.InF').read_text('utf8'), inputs['infos_scene.InF'])

            # An older project may contain a second spelling in its overlay.
            project.put_text(MOD+'sys/items_studio.inf', 'id=3\nname=Other\n')
            with self.assertRaisesRegex(ValueError, 'Multiple Studio definition files'):
                project.perform({'op':'content.clone','kind':'item','source':1})


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
        self.project=Project.create(self.root/'project',self.base)

    def tearDown(self):
        self.assertEqual(self.original,{str(p.relative_to(self.base)):p.read_bytes() for p in self.base.rglob('*') if p.is_file()})

    def test_clone_allocates_new_id_without_duplicate_const(self):
        result=self.project.apply([{'op':'content.clone','kind':'item','source':1,'properties':{'name':'New log'}}])
        identifier=result['results'][0]['id']
        self.assertEqual(identifier,3)
        self.assertNotIn('const',self.project.find('item',identifier).properties)
        self.assertEqual(self.project.find('item',1).properties['name'],['Log'])

    def test_interrupted_manifest_edit_preserves_previous_undo_history(self):
        from studio_core.project import atomic
        self.project.apply([{'op':'map.create','name':'first','size':16}])
        self.project.apply([{'op':'map.create','name':'second','size':16}])

        def interrupt(path, data):
            if path == self.project.path:
                raise OSError('interrupted before replacing project.json')
            return atomic(path, data)

        with mock.patch('studio_core.project.atomic', side_effect=interrupt), self.assertRaises(OSError):
            self.project.apply([{'op':'map.create','name':'third','size':16}])
        reopened = Project(self.project.root)
        self.assertEqual(list(reopened.data['maps']), ['first','second'])
        reopened.undo(expected=2)
        self.assertEqual(list(reopened.data['maps']), ['first'])
        reopened.undo(expected=3)
        self.assertEqual(reopened.data['maps'], {})

    def test_interrupted_undo_promotes_its_matching_redo_history(self):
        self.project.apply([{'op':'map.create','name':'island','size':16}])
        other = Project(self.project.root)
        pending = self.project.root/'.studio/history.pending.json'
        replace = Path.replace

        def interrupt(path, destination):
            if path == pending:
                raise OSError('interrupted after replacing project.json')
            return replace(path, destination)

        with mock.patch.object(Path, 'replace', interrupt), self.assertRaises(OSError):
            self.project.undo(expected=1)
        other.undo(redo=True, expected=2)
        self.assertEqual(list(other.data['maps']), ['island'])
        self.assertFalse(pending.exists())

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

    def test_case_only_map_name_rolls_back_whole_batch(self):
        with self.assertRaisesRegex(ValueError, 'same output path'):
            self.project.apply([{'op':'map.create','name':'Island','size':16},
                                {'op':'map.create','name':'island','size':32}])
        self.assertEqual(self.project.data['maps'], {})
        self.assertFalse((self.project.root/'sources').exists())

    def test_assets_reject_equivalent_names_but_allow_exact_replacements(self):
        name = MOD+'gfx/Écorce.b3d'
        source = self.root/'source.b3d'
        source.write_bytes(b'one')
        operation = {'op':'asset.import','destination':name,'path':str(source)}
        self.project.apply([operation])
        for alternate in (MOD+'gfx/écorce.B3D', MOD+'gfx/E\u0301corce.b3d'):
            with self.subTest(name=alternate), self.assertRaisesRegex(ValueError, 'same output path'):
                self.project.apply([dict(operation, destination=alternate)])
        source.write_bytes(b'two')
        self.project.apply([operation])
        self.project.export(self.root/'out')
        self.assertEqual((self.root/'out/overrides'/name).read_bytes(), b'two')

    def test_older_ambiguous_project_reports_both_names_before_export(self):
        self.project.apply([{'op':'map.create','name':'Island'}])
        self.project.data['maps']['island'] = self.project.data['maps']['Island']
        self.project.path.write_text(json.dumps(self.project.data), encoding='utf8')
        project = Project(self.project.root)
        error = next(i for i in project.validate() if i['code']=='resource-name-collision')
        self.assertIn("'Island' and 'island'", error['message'])
        with self.assertRaisesRegex(ValueError, 'same output path'):
            project.export(self.root/'out')
        self.assertFalse((self.root/'out').exists())

    def test_revision_rejects_concurrent_agent_and_gui(self):
        second=Project(self.project.root)
        self.project.apply([{'op':'map.create','name':'island'}],expected=0)
        with self.assertRaisesRegex(ValueError,'Revision conflict'):
            second.apply([{'op':'map.create','name':'other'}],expected=0)
        self.assertEqual(list(Project(self.project.root).data['maps']),['island'])

    def test_preview_converts_only_unsupported_imported_wavs_and_preserves_export(self):
        def pcm(width):
            data = io.BytesIO()
            with wave.open(data, 'wb') as output:
                output.setparams((1, width, 16000, 0, 'NONE', 'not compressed'))
                output.writeframes(bytes(width * 64))
            return data.getvalue()

        original, supported = pcm(3), pcm(2)
        for name, data in [('new', original), ('ready', supported)]:
            asset = self.root/(name + '.bin')
            asset.write_bytes(data)
            self.project.apply([{'op': 'asset.import', 'path': str(asset),
                                 'destination': MOD + 'sfx/' + name + '.WAV'}])
        self.project.apply([{'op': 'map.create', 'name': 'island'}])

        def convert(inputs, output):
            (source, target), = inputs
            self.assertEqual(source.name, 'new.WAV')
            self.assertEqual(source.read_bytes(), original)
            output.mkdir()
            result = output/'converted.wav'
            result.write_bytes(supported)
            return {target: result}

        with mock.patch.object(web_audio, 'prepare', side_effect=convert) as prepare:
            full = self.project.preview('island')
            for name in ('new', 'ready'):
                self.assertEqual(base64.b64decode(full['files'][MOD + 'sfx/' + name + '.WAV']), supported)
            self.project.preview('island', since=full['token'])
            self.project.export(self.root/'out')
            self.assertEqual(prepare.call_count, 1)
        self.assertEqual((self.root/'out/overrides'/MOD/'sfx/new.WAV').read_bytes(), original)

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

    def test_schema_and_locale_edits_use_the_game_registry(self):
        from locales import languages
        packs = list(languages(ROOT))
        self.assertEqual(studio.dispatch({'command':'schema'})['languages'], packs)
        for code in packs:
            self.project.apply([{'op':'locale.set','language':code,'key':'item.name','text':'Log'}])
        with self.assertRaisesRegex(ValueError, 'registered language'):
            self.project.apply([{'op':'locale.set','language':'legacy-index','key':'item.name','text':'Log'}])

    def test_existing_unregistered_project_translation_blocks_export(self):
        self.project.data['locales']['legacy-index'] = {'item.name':'Log'}
        self.project.path.write_text(json.dumps(self.project.data), encoding='utf8')
        project = Project(self.project.root)
        self.assertIn('unknown-language', [issue['code'] for issue in project.validate()])
        with self.assertRaisesRegex(ValueError, 'legacy-index'):
            project.export(self.root/'out')
        self.assertFalse((self.root/'out').exists())

    def test_project_translation_requires_a_base_or_project_english_key(self):
        result = self.project.apply([{'op':'locale.set','language':'ja','key':'studio.log','text':'丸太'}])
        self.assertIn('missing-translation', [issue['code'] for issue in result['diagnostics']])
        with self.assertRaisesRegex(ValueError, 'studio.log'):
            self.project.export(self.root/'out')
        result = self.project.apply([{'op':'locale.set','language':'en','key':'studio.log','text':'Log'}])
        self.assertEqual(result['diagnostics'], [])
        self.project.export(self.root/'out')
        self.assertEqual(catalog(self.root/'out/locales/ja.tsv'), {'studio.log':'丸太'})

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
        result=subprocess.run([sys.executable,ROOT/'src/devtools/studio.py','serve'],input=''.join(json.dumps(r)+'\n' for r in requests),text=True,capture_output=True,check=True)
        lines=[json.loads(line) for line in result.stdout.splitlines()]
        self.assertEqual([r['ok'] for r in lines],[True,False,False,True])
        self.assertEqual(lines[0]['id'],1);self.assertEqual(lines[-1]['id'],4)
        self.assertEqual(result.stderr,'')

    def test_batch_replays_each_appended_map_edit_once(self):
        self.project.apply([{'op':'map.create','name':'island','size':16}])
        before = self.project.map_bytes('island')
        operations = [{'op':'entity.set','map':'island','kind':'info','id':1,'values':{'yaw':yaw}}
                      for yaw in range(12)]
        with mock.patch.object(EditableMap, 'apply', autospec=True, side_effect=EditableMap.apply) as replay:
            self.project.apply(operations)
        # Each edit is checked when issued, then replayed into the unbound map.
        self.assertLessEqual(replay.call_count, 2 * len(operations))
        expected = before
        for operation in operations:
            expected = EditableMap(expected).apply(operation)
        self.assertEqual(self.project.map_bytes('island'), expected)
        self.project.undo()
        self.assertEqual(self.project.map_bytes('island'), before)
        self.project.undo(redo=True)
        self.assertEqual(self.project.map_bytes('island'), expected)

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

    def test_preview_cache_retains_requested_parent_within_limit(self):
        self.project.apply([{'op':'map.create', 'name':'island', 'size':16}])
        with mock.patch('studio_core.preview.MAX_BASELINES', 2):
            first = self.project.preview('island')['token']
            def add_object():
                self.project.apply([{'op':'entity.add', 'map':'island', 'kind':'object', 'type':1}])
            add_object()
            second = self.project.preview('island', since=first)['token']
            add_object()
            with mock.patch.object(self.project, 'preview_resources', side_effect=ValueError('resource error')):
                with self.assertRaisesRegex(ValueError, 'resource error'):
                    self.project.preview('island')
            cache = self.project.root / '.studio/previews'
            self.assertEqual({p.stem for p in cache.glob('*.json')}, {first, second})
            third = self.project.preview('island', since=first)['token']
            self.assertEqual({p.stem for p in cache.glob('*.json')}, {first, third})
            self.assertEqual(self.project.preview('island', since=third)['changes']['upserted'], 0)

    def test_inline_map_and_entity_text_marks_new_font_characters_pending(self):
        from studio_core import fonts
        from fonts import repertoire
        known = set(repertoire({}, {})) | {ord('섬')}
        self.project.apply([{'op':'map.create','name':'island','size':16}])
        with mock.patch.object(fonts, 'base_glyphs', return_value=known), \
             mock.patch.object(fonts, 'project_fonts', return_value={}):
            full = self.project.preview('island')
            def write(text, entity=False):
                op = {'op':'entity.script','kind':'info','id':1} if entity else {'op':'map.script'}
                self.project.apply([{**op,'map':'island','text':text}])

            write('on:start { msg "섬"; }')
            self.assertFalse(self.project.preview('island', since=full['token'])['resourcesPending'])
            write('on:use { msg "힣"; }', entity=True)
            delta = self.project.preview('island', since=full['token'])
            self.assertTrue(delta['resourcesPending'])
            refreshed = self.project.preview('island')
            self.assertEqual(delta['resourcesVersion'], refreshed['resourcesVersion'])
            self.assertNotEqual(full['resourcesVersion'], refreshed['resourcesVersion'])
            write('on:start { msg "힣힣"; }')
            self.assertFalse(self.project.preview('island', since=refreshed['token'])['resourcesPending'])

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
