"""Prevent the Unicode loss that made developer controls unreadable."""
from pathlib import Path
import sys
import unittest

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'src/devtools'))
from locales import catalog


class DeveloperCatalogTests(unittest.TestCase):
    def test_translated_controls_survive_utf8(self):
        packs={lang:catalog(ROOT/'resources/locales'/f'{lang}.tsv') for lang in ('en','ko','de')}
        keys={key for key in packs['en'] if key.startswith('dev.')}
        for lang,pack in packs.items():
            self.assertEqual(keys,{key for key in pack if key.startswith('dev.')})
            for key in keys:
                self.assertNotIn('?',pack[key],(lang,key))
                if lang=='ko':
                    self.assertTrue(any('\uac00'<=char<='\ud7a3' for char in pack[key]),key)
        self.assertEqual(packs['ko']['dev.items'],'아이템 지급')
        self.assertEqual(packs['de']['dev.exhaustion'],'Erschöpfung')
