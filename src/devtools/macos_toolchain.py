"""Private ARM64 Blitz compiler and compatibility runtime; no player data."""
from pathlib import Path
import hashlib
import json
import os
import platform
import subprocess
import sys
import tarfile
import tempfile
import urllib.request

from blitz3d_toolchain import run
import blitz3d_toolchain as engine

ROOT = Path(__file__).resolve().parents[2]
SOURCE = engine.ENGINE
ENGINE = engine.ENGINE
BUILD = ROOT / 'build/macos'
TOOLS = BUILD / 'toolchain'


def config():
    return json.loads((ROOT / 'config/macos-toolchain.json').read_text('utf8'))


def require_host():
    if platform.system() != 'Darwin' or platform.machine() != 'arm64':
        raise ValueError('Native Mac builds currently require an Apple Silicon Mac.')


def environment_report():
    """Record the actual build tools, without machine-specific paths."""
    commands = {
        'macos': ['sw_vers', '-productVersion'], 'sdk': ['xcrun', '--sdk', 'macosx', '--show-sdk-version'],
        'clang': ['xcrun', 'clang', '--version'], 'cmake': ['cmake', '--version'],
        'ninja': ['ninja', '--version'], 'ffmpeg': ['ffmpeg', '-version'],
    }
    result = {'architecture': platform.machine(), 'python': platform.python_version()}
    for name, command in commands.items():
        result[name] = subprocess.check_output(command, text=True).splitlines()[0]
    return result


def dawn():
    return engine.dawn()


def prepare_dawn():
    engine.fetch(engine.config()['dawn']['macos'], 'dawn-macos.tar.gz')


def prepare():
    require_host()
    engine.prepare()


def build(graphics=False):
    require_host()
    compiler = engine.build_compiler()
    output = engine.build_native(graphics)
    import shutil
    target = output / 'bin/blitzcc'
    shutil.copy2(compiler, target)
    return target


if __name__ == '__main__':
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=('prepare', 'build'))
    parser.add_argument('--graphics', action='store_true', help='Build the Metal runtime')
    args = parser.parse_args()
    try:
        build(args.graphics) if args.command == 'build' else prepare()
    except (ValueError, OSError, subprocess.CalledProcessError) as exc:
        parser.exit(1, f'Error: {exc}\n')
