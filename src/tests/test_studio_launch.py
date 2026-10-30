"""Launcher input selection and failure handling, without opening a GUI."""
from contextlib import redirect_stderr
from io import StringIO
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest import TestCase, mock
import json
import os
import subprocess
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / 'src/devtools'))
import studio_launch


class StudioLauncher(TestCase):
    def setUp(self):
        directory = TemporaryDirectory(prefix='studio launcher ')
        self.addCleanup(directory.cleanup)
        # resource_inputs() resolves paths; macOS temp folders sit behind /var -> /private/var.
        self.root = Path(directory.name).resolve()
        patch = mock.patch.object(studio_launch, 'ROOT', self.root)
        patch.start()
        self.addCleanup(patch.stop)

    def inputs(self, folder):
        game = self.root / folder / 'game'
        game.mkdir(parents=True)
        font = game.parent / 'font.ttf'
        font.write_bytes(b'font')
        return {'game': str(game), 'font': str(font)}

    def test_saved_external_inputs_are_preserved_and_invalid_override_fails(self):
        inputs = self.inputs('external resources')
        settings = self.root / 'build/studio/dependencies.local.json'
        settings.parent.mkdir(parents=True)
        settings.write_text(json.dumps(inputs), encoding='utf8')
        self.assertEqual(studio_launch.resource_inputs(), {k: Path(v) for k, v in inputs.items()})
        before = settings.read_bytes()
        with self.assertRaisesRegex(ValueError, 'Missing game input'):
            studio_launch.resource_inputs(game=self.root / 'missing')
        self.assertEqual(settings.read_bytes(), before)

    def test_setup_moved_from_another_machine_uses_local_dependencies(self):
        game = self.root / 'deps/game'
        game.mkdir(parents=True)
        font = self.root / 'deps/fonts/NotoSansKR[wght].ttf'
        font.parent.mkdir()
        font.write_bytes(b'font')
        settings = self.root / 'build/studio/dependencies.local.json'
        settings.parent.mkdir(parents=True)
        settings.write_text(json.dumps({'game': 'missing/game', 'font': 'missing/font.ttf'}), encoding='utf8')
        self.assertEqual(studio_launch.resource_inputs(), {'game': game, 'font': font})

    def test_first_launch_reenters_local_venv_before_installing_packages(self):
        with mock.patch.dict(os.environ, {}, clear=True), mock.patch.object(studio_launch, 'run') as run:
            run.return_value.returncode = 0
            self.assertEqual(studio_launch.python_environment(['--prepare-only']), 0)
        self.assertEqual(run.call_args_list[0].args[0][1:], ['-m', 'venv', self.root / '.venv'])
        child = run.call_args_list[1]
        self.assertEqual(child.kwargs['env']['S2_STUDIO_PYTHON'], str(child.args[0][0]))
        self.assertEqual(child.args[0][-1], '--prepare-only')

    def test_failed_engine_build_never_launches_the_app(self):
        def execute(arguments, **kwargs):
            if arguments[-1] == 'build':
                raise subprocess.CalledProcessError(37, arguments)
        with mock.patch.dict(os.environ), \
             mock.patch.object(studio_launch, 'python_environment', return_value=None), \
             mock.patch.object(studio_launch, 'python_dependencies'), \
             mock.patch.object(studio_launch, 'node_dependencies_ready', return_value=True), \
             mock.patch.object(studio_launch, 'resource_inputs', return_value=self.inputs('base')), \
             mock.patch.object(studio_launch.shutil, 'which', side_effect=lambda name: name), \
             mock.patch.object(studio_launch.subprocess, 'check_output', return_value='v24.0.0'), \
             mock.patch.object(studio_launch, 'run', side_effect=execute) as run, \
             redirect_stderr(StringIO()):
            self.assertEqual(studio_launch.main([]), 37)
        self.assertFalse(any('start' in call.args[0] or 'check' in call.args[0] for call in run.call_args_list))
