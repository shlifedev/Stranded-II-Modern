"""Check diagnostic isolation and measurement math; these do not measure FPS."""
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest import TestCase
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
from profile_web import instrument, replace_once, validate_report
from verify_timing import function


class WebProfile(TestCase):
    def test_instrumentation_retains_clock_and_pacing(self):
        original = (ROOT / 'src/engine/includes/modern_runtime.bb').read_text('utf8')
        with TemporaryDirectory() as directory:
            source = Path(directory) / 'src'
            shutil.copytree(ROOT / 'src/engine', source)
            instrument(source)
            changed = (source / 'includes/modern_runtime.bb').read_text('utf8')
            for name in ('modern_clock', 'modern_frame', 'modern_counter_elapsed', 'modern_game_time', 'modern_focus'):
                self.assertEqual(function(original, name), function(changed, name))
            self.assertIn('modern_sleep(1)', changed)
            self.assertIn('Flip False', changed)
            self.assertEqual(changed.count('profile("start:flip")'), 1)
            loop = (source / 'includes/engine_loop.bb').read_text('utf8')
            self.assertIn('modern_present()', loop)
            self.assertNotIn('compat_clock', loop)
            self.assertEqual((ROOT / 'src/engine/includes/modern_runtime.bb').read_text('utf8'), original)

    def test_changed_or_ambiguous_source_fails_instead_of_silent_mismeasurement(self):
        for text in ('absent', 'anchor anchor'):
            with self.assertRaises(ValueError):
                replace_once(text, 'anchor', 'mark')

    def test_browser_report_math(self):
        nodes = list((ROOT / 'deps/emsdk/node').glob('*/node.exe'))
        node = nodes[0] if nodes else shutil.which('node')
        if not node:
            self.skipTest('Node.js is not installed')
        subprocess.run([str(node), str(ROOT / 'tests/web_profile_metrics.cjs')], check=True, timeout=10)

    def test_incomplete_or_invalid_measurements_cannot_be_marked_complete(self):
        trials = [dict(repeat=repeat, water=water, fps_limit=fps, valid=True, frames=100)
                  for repeat in (1, 2, 3) for water, fps in ((6, 144), (3, 144), (0, 144), (0, 60))]
        validate_report(dict(status='complete', trials=trials))
        for invalid in (trials[:-1], trials + [trials[0]],
                        trials[:-1] + [dict(trials[-1], valid=False)]):
            with self.assertRaises(ValueError):
                validate_report(dict(status='complete', trials=invalid))
        for invalid in ([], {}, dict(status='complete', trials=['invalid'])):
            with self.assertRaises(ValueError):
                validate_report(invalid)
