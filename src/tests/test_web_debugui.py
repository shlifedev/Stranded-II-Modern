"""Run the shared browser debug overlay's input and focus fixture."""
from pathlib import Path
from unittest import TestCase
import os
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[2]


class WebDebugOverlay(TestCase):
    def test_shared_debug_overlay_focus_and_korean_composition(self):
        pattern = '*/node.exe' if os.name == 'nt' else '*/bin/node'
        node = next((ROOT / 'deps/emsdk/node').glob(pattern), None) or shutil.which('node')
        if not node:
            self.skipTest('Node.js is not installed')
        subprocess.run([str(node), ROOT / 'src/tests/web_debugui.cjs'], check=True, timeout=10)
