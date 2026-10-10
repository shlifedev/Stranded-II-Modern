"""macOS archive, input integrity, and native runtime update checks."""
from pathlib import Path
from unittest.mock import patch
import hashlib
import io
import json
import os
import plistlib
import stat
import subprocess
import sys
import tarfile
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
import package_macos as macos
import release
from versioning import make_info


def mac_package(directory, info, windows):
    """Small structurally valid package, with the exact Windows payload."""
    with zipfile.ZipFile(windows) as archive:
        game = {name.removeprefix('StrandedII/'): archive.read(name) for name in archive.namelist()}
    files = {name: hashlib.sha256(data).hexdigest() for name, data in game.items()}
    identifier = hashlib.sha256(json.dumps(files, sort_keys=True).encode()).hexdigest()
    prefix = 'Stranded II.app/Contents/'
    resources = prefix + 'Resources/'
    members = {resources + 'game/' + name: data for name, data in game.items()}
    members[resources + 'payload.json'] = json.dumps(dict(id=identifier, files=files)).encode()
    members[resources + 'macos-build.json'] = json.dumps(dict(info, platform=macos.PLATFORM, payload_id=identifier,
        windows_zip_sha256=hashlib.sha256(windows.read_bytes()).hexdigest())).encode()
    members[prefix + 'Info.plist'] = plistlib.dumps(dict(CFBundleExecutable='StrandedII', LSArchitecturePriority=['arm64']))
    output = directory / release.archive_name(info, macos.PLATFORM)
    with zipfile.ZipFile(output, 'w') as archive:
        for name, data in members.items():
            archive.writestr(name, data)
        for name in (prefix + 'MacOS/StrandedII', resources + 'wine/bin/wine', resources + 'wine/bin/wineserver'):
            entry = zipfile.ZipInfo(name)
            entry.external_attr = (stat.S_IFREG | 0o755) << 16
            archive.writestr(entry, b'test executable')
    output.with_suffix('.zip.sha256').write_text(f'{macos.digest(output)}  {output.name}\n', encoding='utf8')
    return output


class MacPackagingTests(unittest.TestCase):
    def test_download_rechecks_cache_before_using_it(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / 'engine.tar.xz'
            path.write_bytes(b'wrong')
            with self.assertRaisesRegex(ValueError, 'SHA-256 mismatch'), patch('urllib.request.urlopen') as download:
                macos.checked_download({'sha256': 'a' * 64}, path)
            download.assert_not_called()

    @unittest.skipIf(os.name == 'nt', 'Creating Unix symlinks requires macOS/Linux')
    def test_zip_preserves_modes_links_and_unicode(self):
        with tempfile.TemporaryDirectory() as temporary:
            base = Path(temporary)
            app = base / '한글 App.app'
            app.mkdir()
            binary = app / 'executable'
            binary.write_bytes(b'binary')
            binary.chmod(0o755)
            (app / 'alias').symlink_to('executable')
            output = base / 'app.zip'
            macos.write_app_zip(app, output)
            with zipfile.ZipFile(output) as archive:
                self.assertEqual(archive.getinfo('한글 App.app/executable').external_attr >> 16 & 0o777, 0o755)
                self.assertTrue(stat.S_ISLNK(archive.getinfo('한글 App.app/alias').external_attr >> 16))
                self.assertEqual(archive.read('한글 App.app/alias'), b'executable')

    def test_wine_archive_rejects_escape_and_special_files(self):
        for name, link, special in [('wswine.bundle/../../escape', '', False), ('wswine.bundle/link', '/tmp/escape', False), ('wswine.bundle/device', '', True)]:
            with self.subTest(name=name), tempfile.TemporaryDirectory() as temporary:
                base = Path(temporary)
                archive = base / 'wine.tar'
                with tarfile.open(archive, 'w') as tar:
                    member = tarfile.TarInfo(name)
                    if link: member.type = tarfile.SYMTYPE; member.linkname = link
                    if special: member.type = tarfile.CHRTYPE
                    tar.addfile(member, io.BytesIO())
                with self.assertRaises(ValueError):
                    macos.extract_wine(archive, base / 'extract')

    @unittest.skipUnless(sys.platform == 'darwin', 'Native macOS launcher')
    def test_native_update_and_recovery(self):
        with tempfile.TemporaryDirectory() as temporary:
            binary = str(Path(temporary) / 'runtime-tests')
            subprocess.run(['xcrun', 'swiftc', str(ROOT / 'src/native/macos/Runtime.swift'),
                            str(ROOT / 'tests/MacRuntimeTests.swift'), '-o', binary], check=True)
            subprocess.run([binary], check=True, timeout=30)


if __name__ == '__main__':
    unittest.main()
