"""Create and inspect read-only Mac distribution disk images without opening Finder."""
from contextlib import contextmanager
from pathlib import Path
import hashlib
import os
import stat
import subprocess
import sys
import tempfile

from macos_validation import tree_records

MEMBERS = {'Stranded II.app', 'Applications', 'README-en.txt', 'README-ko.txt',
           'verification-summary.json'}
# HFS+ and macOS may add these independently of the distribution payload.
VOLUME_METADATA = {'.DS_Store', '.Trashes', '.fseventsd', '.Spotlight-V100',
                   '.TemporaryItems', '.DocumentRevisions-V100'}


def payload_records(root):
    """Only the installer shortcut may be a link; app members remain strict."""
    if root.is_symlink() or not root.is_dir():
        raise ValueError('Expected a Mac distribution directory.')
    if {p.name for p in root.iterdir()} - VOLUME_METADATA != MEMBERS:
        raise ValueError('Missing or unexpected Mac DMG members.')
    shortcut = root / 'Applications'
    if not shortcut.is_symlink() or os.readlink(shortcut) != '/Applications':
        raise ValueError('Mac DMG Applications shortcut must target /Applications.')
    records = {'Applications': {'type': 'symlink', 'target': '/Applications'}}
    for name in sorted(MEMBERS - {'Applications'}):
        path = root / name
        mode = path.lstat().st_mode
        directory = name == 'Stranded II.app'
        if not (stat.S_ISDIR(mode) if directory else stat.S_ISREG(mode)):
            raise ValueError(f'Unsupported Mac DMG member: {name}')
        record = {'type': 'directory' if directory else 'file', 'mode': stat.S_IMODE(mode)}
        if directory:
            records.update({name + '/' + key: value for key, value in tree_records(path).items()})
        else:
            record['sha256'] = hashlib.sha256(path.read_bytes()).hexdigest()
        records[name] = record
    return records


def write_image(path, stage):
    records = payload_records(stage)
    subprocess.run(['hdiutil', 'create', '-srcfolder', str(stage), '-volname', 'Stranded II',
                    '-fs', 'HFS+', '-format', 'UDZO', '-nospotlight', str(path)], check=True)
    return records


@contextmanager
def mounted_image(path):
    if sys.platform != 'darwin':
        raise ValueError('Mac DMG verification requires macOS.')
    subprocess.run(['hdiutil', 'verify', str(path)], check=True)
    with tempfile.TemporaryDirectory(prefix='stranded-dmg-') as directory:
        mount = Path(directory) / '마운트 Test'
        mount.mkdir()
        attached = False
        try:
            subprocess.run(['hdiutil', 'attach', '-readonly', '-nobrowse', '-noautoopen',
                            '-owners', 'on', '-mountpoint', str(mount), str(path)], check=True)
            attached = True
            yield mount
        finally:
            if attached or os.path.ismount(mount):
                subprocess.run(['hdiutil', 'detach', str(mount)], check=True)
