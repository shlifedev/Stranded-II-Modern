"""Content image decoding and read-only GUI/CLI contract."""
import base64
from io import BytesIO
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest import TestCase, mock
import json
import subprocess
import sys

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
import studio
from studio_core.project import Project, MOD
from studio_core.thumbnails import thumbnail


class Thumbnails(TestCase):
    def setUp(self):
        temp = TemporaryDirectory(); self.addCleanup(temp.cleanup)
        self.root = Path(temp.name); self.base = self.root / 'base'
        sysdir = self.base / MOD / 'sys'; sysdir.mkdir(parents=True)
        self.items = MOD + 'sys/items.inf'
        (self.base / self.items).write_text('id=1\nname=First\nicon=gfx\\first.bmp\nid=2\nname=Second\nicon=gfx/second.png\nid=3\nname=No icon\n', encoding='utf8')
        (sysdir / 'objects.inf').write_text('id=1\nicon=gfx/first.bmp\n', encoding='utf8')
        (sysdir / 'units.inf').write_text('id=1\nicon=gfx/second.png\n', encoding='utf8')
        (sysdir / 'buildings.inf').write_text('id=1\nobjectid=1\nid=2\nunitid=1\n', encoding='utf8')
        (sysdir / 'combinations.inf').write_text('combi=start\ngen=1,2\ngen=2\ncombi=end\n', encoding='utf8')
        (sysdir / 'infos.inf').write_text('id=1\nframe=18\n', encoding='utf8')
        first = Image.new('RGB', (40, 41), 'black'); first.putpixel((1, 1), (255, 0, 0))
        self.save_image('gfx/first.bmp', first)
        self.save_image('gfx/second.png', Image.new('RGBA', (12, 20), (0, 255, 0, 123)))
        sheet = Image.new('RGB', (320, 320), (255, 0, 255)); sheet.paste((1, 2, 3), (256, 32, 288, 64)); sheet.putpixel((256, 32), (255, 0, 255))
        self.save_image('sys/gfx/icons.bmp', sheet)
        self.project = Project.create(self.root / 'project', self.base)

    def save_image(self, name, image):
        path = self.base / MOD / name; path.parent.mkdir(parents=True, exist_ok=True); image.save(path)

    def decoded(self, result):
        self.assertEqual(result['status'], 'ready', result)
        return Image.open(BytesIO(base64.b64decode(result['dataUrl'].split(',', 1)[1]))).convert('RGBA')

    def query(self, kind='item', identifier=1):
        return thumbnail(self.project, kind, identifier)

    def test_native_icons_and_alpha_without_writing_any_files(self):
        before = {str(p.relative_to(self.root)): p.read_bytes() for p in self.root.rglob('*') if p.is_file()}
        result = self.query(); image = self.decoded(result)
        self.assertEqual((result['width'], result['height']), (40, 41))
        self.assertEqual(image.getpixel((0, 0)), (0, 0, 0, 0))
        self.assertEqual(image.getpixel((1, 1)), (255, 0, 0, 255))
        self.assertEqual(self.decoded(self.query(identifier=2)).getpixel((0, 0)), (0, 255, 0, 255))
        self.assertEqual(result['revision'], 0)
        self.assertEqual(before, {str(p.relative_to(self.root)): p.read_bytes() for p in self.root.rglob('*') if p.is_file()})

    def test_derived_building_recipe_and_info_images(self):
        for kind, identifier, expected in [('object', 1, 1), ('unit', 1, 2), ('building', 1, 1), ('building', 2, 2)]:
            with self.subTest(kind=kind, identifier=identifier):
                self.assertEqual(self.query(kind, identifier)['dataUrl'], self.query(identifier=expected)['dataUrl'])
        recipe = self.query('recipe', MOD + 'sys/combinations.inf#0')
        self.assertEqual(recipe['dataUrl'], self.query()['dataUrl']); self.assertEqual(recipe['additionalResults'], 1)
        info = self.decoded(self.query('info', 1))
        self.assertEqual(info.size, (32, 32)); self.assertEqual(info.getpixel((1, 1)), (1, 2, 3, 255))
        self.assertEqual(info.getpixel((0, 0))[3], 0)

    def test_import_override_definition_changes_and_undo_refresh(self):
        initial = self.query(); before = initial['dataUrl']
        imported = self.root / 'new.png'; Image.new('RGB', (40, 40), 'blue').save(imported)
        self.project.apply([{'op': 'asset.import', 'path': str(imported), 'destination': MOD + 'gfx/first.bmp'}])
        after = self.query(); self.assertNotEqual(before, after['dataUrl']); self.assertEqual(after['revision'], 1)
        self.assertNotEqual(initial['version'], after['version'])
        self.assertEqual(self.decoded(after).getpixel((1, 1)), (0, 0, 255, 255))
        self.project.undo(); self.assertEqual(self.query()['dataUrl'], before)
        self.assertEqual(self.query()['version'], initial['version'])
        self.project.apply([{'op': 'content.set', 'kind': 'item', 'id': 1, 'properties': {'icon': 'gfx/second.png'}}])
        self.assertEqual(self.query()['dataUrl'], self.query(identifier=2)['dataUrl'])
        self.assertNotEqual(self.query()['version'], initial['version'])

    def test_map_edits_and_undo_keep_thumbnail_version_and_pixels(self):
        initial = self.query()
        self.project.apply([{'op': 'map.create', 'name': 'island', 'size': 16}])
        for x in (0, 64, 128):
            self.project.apply([{'op': 'entity.add', 'map': 'island', 'kind': 'item', 'type': 1, 'values': {'x': x, 'z': 0, 'y': 100}}])
            result = self.query()
            self.assertEqual(result['version'], initial['version'])
            self.assertEqual(self.project.snapshot('island')['thumbnailVersion'], initial['version'])
            self.assertEqual(result['dataUrl'], initial['dataUrl'])
            self.assertGreater(result['revision'], initial['revision'])
        self.project.undo(); self.project.undo(redo=True)
        self.assertEqual(self.query()['version'], initial['version'])

    def test_missing_corrupt_and_invalid_references_are_local_failures(self):
        self.assertEqual(self.query(identifier=3)['reason'], 'no-icon')
        cases = [('gfx/absent.bmp', 'missing-file'), ('gfx/corrupt.bmp', 'unreadable-image')]
        (self.base / MOD / 'gfx/corrupt.bmp').write_bytes(b'not an image')
        for icon, reason in cases:
            self.project.put_text(self.items, 'id=1\nicon=' + icon + '\n')
            self.assertEqual(self.query()['reason'], reason)
        self.project.put_text(MOD + 'sys/buildings.inf', 'id=1\nobjectid=99\n')
        self.assertEqual(self.query('building')['reason'], 'missing-reference')
        self.project.put_text(MOD + 'sys/infos.inf', 'id=1\nframe=100\n')
        self.assertEqual(self.query('info')['reason'], 'invalid-frame')

    def test_large_images_are_bounded_and_keep_original_dimensions(self):
        self.save_image('gfx/large.png', Image.new('RGB', (800, 400), 'red'))
        self.project.put_text(self.items, 'id=1\nicon=gfx/large.png\n')
        result = self.query()
        self.assertEqual((result['width'], result['height']), (800, 400))
        self.assertEqual(self.decoded(result).size, (160, 80))
        with mock.patch('studio_core.thumbnails.MAX_PIXELS', 100):
            self.save_image('gfx/large.png', Image.new('RGB', (30, 30), 'green'))
            self.assertEqual(self.query()['reason'], 'image-too-large')

    def test_paths_and_symlinks_cannot_escape_resource_roots(self):
        secret = self.root / 'secret.png'; Image.new('RGB', (4, 4), 'red').save(secret)
        for path in ('../../secret.png', str(secret), 'gfx/../../../../secret.png', 'https://example.invalid/a.png'):
            self.project.put_text(self.items, 'id=1\nicon=' + path + '\n')
            self.assertEqual(self.query()['status'], 'unavailable')
        linked = self.base / MOD / 'gfx/link.png'
        try:
            linked.symlink_to(secret)
        except OSError:
            return  # Windows may not grant symlink creation to this account.
        self.project.put_text(self.items, 'id=1\nicon=gfx/link.png\n')
        self.assertEqual(self.query()['status'], 'unavailable')

    def test_resource_link_and_changed_source_do_not_reuse_stale_pixels(self):
        (self.base / MOD / 'gfx/alias.inf').write_text('first.bmp\n', encoding='utf8')
        self.project.put_text(self.items, 'id=1\nicon=gfx/alias.bmp\n')
        before = self.query(); self.assertEqual(before['source'], MOD + 'gfx/first.bmp')
        self.save_image('gfx/first.bmp', Image.new('RGB', (40, 41), 'blue'))
        self.assertNotEqual(self.query()['dataUrl'], before['dataUrl'])
        (self.base / MOD / 'gfx/alias.inf').write_text('../../../../secret.png\n', encoding='utf8')
        self.assertEqual(self.query()['status'], 'unavailable')

    def test_cli_and_json_service_use_same_contract_without_a_map(self):
        expected = studio.response({'command': 'thumbnail', 'project': str(self.project.root), 'kind': 'item', 'contentId': 1})
        output = subprocess.check_output([sys.executable, str(ROOT / 'scripts/studio.py'), 'thumbnail', str(self.project.root), '--kind', 'item', '--id', '1'], text=True, encoding='utf8')
        self.assertEqual(json.loads(output), expected); self.assertEqual(expected['result']['status'], 'ready')
        bad = studio.response({'command': 'thumbnail', 'project': str(self.project.root), 'kind': 'item', 'contentId': 999})
        self.assertFalse(bad['ok'])
