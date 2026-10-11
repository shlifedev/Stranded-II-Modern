"""Build the shared x86 Unicode input bridge with installed MSVC/Windows SDK."""
from pathlib import Path
import hashlib
import json
import os
import shutil
import struct
import subprocess
import winreg
from editions import Edition


def build(root: Path, edition='classic') -> Path:
    vswhere = Path(os.environ.get('ProgramFiles(x86)', r'C:\Program Files (x86)')) / 'Microsoft Visual Studio/Installer/vswhere.exe'
    if not vswhere.is_file():
        raise RuntimeError('Building Stranded II requires Visual Studio C++ build tools and a Windows SDK.')
    installation = subprocess.check_output([
        str(vswhere), '-latest', '-products', '*', '-requires',
        'Microsoft.VisualStudio.Component.VC.Tools.x86.x64', '-property', 'installationPath',
    ], text=True).strip()
    if not installation:
        raise RuntimeError('Install the MSVC x86/x64 C++ build tools for Stranded II.')
    versions = Path(installation) / 'VC/Tools/MSVC'
    vc = max((p for p in versions.iterdir() if p.is_dir()), key=lambda p: tuple(map(int, p.name.split('.'))))
    with winreg.OpenKey(winreg.HKEY_LOCAL_MACHINE, r'SOFTWARE\Microsoft\Windows Kits\Installed Roots',
                        access=winreg.KEY_READ | winreg.KEY_WOW64_32KEY) as key:
        sdk = Path(winreg.QueryValueEx(key, 'KitsRoot10')[0])
    sdk_version = max((p.name for p in (sdk / 'Include').iterdir()
                       if (p / 'um/Windows.h').is_file()), key=lambda name: tuple(map(int, name.split('.'))))
    toolbin = vc / 'bin/Hostx64/x86'
    output = Edition(edition).build_dir(root) / 'native'
    output.mkdir(parents=True, exist_ok=True)
    source = root / 'src/native/qol_input.c'
    dll = output / 'QolInput.dll'
    command = [str(toolbin / 'cl.exe'), '/nologo', '/LD', '/O2', '/GS-', '/W4', '/WX',
               '/Fo' + str(output / 'qol_input.obj'), '/Fe' + str(dll), str(source)]
    for include in (vc / 'include', sdk / 'Include' / sdk_version / 'shared',
                    sdk / 'Include' / sdk_version / 'um', sdk / 'Include' / sdk_version / 'ucrt'):
        command.append('/I' + str(include))
    command += ['/link', '/NOENTRY', '/NODEFAULTLIB', '/MACHINE:X86', '/Brepro',
                '/EXPORT:QolInputAttach=_QolInputAttach@8', '/EXPORT:QolInputDetach=_QolInputDetach@0',
                '/LIBPATH:' + str(sdk / 'Lib' / sdk_version / 'um/x86'),
                'user32.lib', 'kernel32.lib', 'imm32.lib']
    env = dict(os.environ, PATH=str(toolbin) + os.pathsep + os.environ.get('PATH', ''))
    result = subprocess.run(command, cwd=output, env=env, capture_output=True)
    if result.returncode:
        raise RuntimeError(result.stdout.decode(errors='replace') + result.stderr.decode(errors='replace'))
    data = dll.read_bytes()
    offset = struct.unpack_from('<I', data, 0x3c)[0]
    assert data[offset:offset+4] == b'PE\0\0' and struct.unpack_from('<H', data, offset+4)[0] == 0x14c
    (output / 'report.json').write_text(json.dumps({
        'edition': edition, 'compiler': str(toolbin / 'cl.exe'), 'sdk': sdk_version,
        'source_sha256': hashlib.sha256(source.read_bytes()).hexdigest(),
        'dll_sha256': hashlib.sha256(data).hexdigest(),
    }, indent=2) + '\n', encoding='utf8')
    return dll


def install(source: Path, runtime: Path):
    target = runtime / source.name
    if target.is_file() and target.read_bytes() == source.read_bytes():
        return
    pending = runtime / (source.name + '.next')
    shutil.copy2(source, pending)
    try:
        os.replace(pending, target)
    except PermissionError as exc:
        raise RuntimeError(f'Compiled {source}; close the development game before replacing {target}') from exc


if __name__ == '__main__':
    print(build(Path(__file__).resolve().parents[1]))
