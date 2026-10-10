"""Verify edition boundaries using changes, not just configuration snapshots."""
from pathlib import Path
import json
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
from editions import Edition, override_files, source_hashes
from locales import catalog_text, validate
from versioning import make_info, select_version, validate_info


class EditionTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        for edition in ('classic', 'extended'):
            self.put('src/engine/' + Edition(edition).entry,
                     f'Include "includes\\common.bb"\nInclude "editions\\{edition}\\rules.bb"\n')
            self.put(f'src/engine/editions/{edition}/rules.bb', '; original rules\n')
        self.put('src/engine/includes/common.bb', '; shared\n')
        for code in ('en', 'ko'):
            self.put(f'locales/{code}.json', json.dumps({'name': code}))
            self.put(f'locales/{code}.tsv', 'item.axe\tOriginal axe\n')

    def put(self, relative, text):
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding='utf8')
        return path

    def test_extended_source_changes_do_not_change_classic_inputs(self):
        classic = source_hashes(self.root)
        extended = source_hashes(self.root, 'extended')
        self.put('src/engine/editions/extended/rules.bb', '; new balance\n')
        self.assertEqual(source_hashes(self.root), classic)
        self.assertNotEqual(source_hashes(self.root, 'extended'), extended)
        self.put('src/engine/includes/common.bb', '; shared fix\n')
        self.assertNotEqual(source_hashes(self.root), classic)

    def test_cross_edition_includes_and_cycles_fail(self):
        rules = 'src/engine/editions/classic/rules.bb'
        self.put(rules, 'Include "editions\\extended\\rules.bb"\n')
        with self.assertRaisesRegex(ValueError, 'another edition'):
            source_hashes(self.root)
        self.put(rules, 'Include "editions\\classic\\..\\extended\\rules.bb"\n')
        with self.assertRaisesRegex(ValueError, 'another edition'):
            source_hashes(self.root)
        self.put(rules, 'Include "StrandedII.bb"\n')
        with self.assertRaisesRegex(ValueError, 'Cyclic'):
            source_hashes(self.root)

    def test_content_overrides_are_ordered_and_isolated(self):
        relative = 'mods/Stranded II/sys/items.inf'
        shared = self.put('resources/overrides/' + relative, 'damage=10\n')
        extended = self.put('resources/editions/extended/overrides/' + relative, 'damage=20\n')
        self.assertEqual(override_files(self.root)[relative], shared)
        self.assertEqual(override_files(self.root, 'extended')[relative], extended)
        extended.write_text('damage=30\n', encoding='utf8')
        self.assertEqual(override_files(self.root)[relative].read_text(), 'damage=10\n')
        self.put('resources/editions/extended/overrides/mods/Stranded II/saves/private.sav', 'private')
        self.assertEqual(override_files(self.root)[relative], shared)
        with self.assertRaisesRegex(ValueError, 'player settings'):
            override_files(self.root, 'extended')

    def test_extended_text_and_references_do_not_leak_to_classic(self):
        before = catalog_text(self.root, 'en')
        self.put('locales/editions/extended/en.tsv', 'item.axe\tNew axe\nqol.sort\tSort\n')
        self.put('src/engine/editions/extended/rules.bb', 'Global title$="[[loc:qol.sort]]"\n')
        self.assertEqual(catalog_text(self.root, 'en'), before)
        self.assertNotIn('qol.sort', validate(self.root)['en'])
        self.assertEqual(validate(self.root, 'extended')['en']['item.axe'], 'New axe')
        self.assertNotIn('qol.sort', validate(self.root, 'extended')['ko'])  # English fallback
        self.put('locales/editions/extended/en.tsv', 'qol.sort\tSort\nqol.sort\tDuplicate\n')
        validate(self.root)
        with self.assertRaisesRegex(ValueError, 'duplicate'):
            validate(self.root, 'extended')

    def test_runtime_and_defaults_are_separate(self):
        classic, extended = Edition('classic'), Edition('extended')
        self.assertEqual(classic.runtime(self.root), self.root / 'build/runtime')
        self.assertEqual(extended.runtime(self.root), self.root / 'build/extended/runtime')
        self.assertNotEqual(classic.defaults, extended.defaults)
        self.assertNotEqual(classic.archive_root, extended.archive_root)

    def test_independent_versions_even_with_same_commit(self):
        commit, old = 'a' * 40, 'b' * 40
        tags = {'v0.1.8': commit, 'extended-v0.1.1': old, 'extended-v0.1.2-dev.4': old}
        self.assertEqual(select_version('0.1.0', 'main', commit, tags, set(tags)), '0.1.8')
        self.assertEqual(select_version('0.1.0', 'main', commit, tags, set(tags), 'extended'), '0.1.2')
        self.assertEqual(select_version('0.1.0', 'dev', commit, tags, set(tags), 'extended'), '0.1.2-dev.5')
        info = make_info('0.1.2', 'main', commit, edition='extended')
        self.assertEqual(info['tag'], 'extended-v0.1.2')
        self.assertEqual(info['display_version'], 'extended-0.1.2-main')
        with self.assertRaises(ValueError):
            validate_info(dict(info, edition='classic'))
        legacy = make_info('1.1.0', 'main', commit)
        del legacy['edition']
        self.assertEqual(validate_info(legacy)['edition'], 'classic')


if __name__ == '__main__':
    unittest.main()
