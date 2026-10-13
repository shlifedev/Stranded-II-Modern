"""Convert only unsupported web audio in a build cache; keep source assets intact."""
from pathlib import Path
import hashlib
import json
import os
import shutil
import struct
import subprocess
import urllib.request
import wave
import zipfile

import web_toolchain


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def tools():
    if os.name != 'nt':
        paths = tuple(shutil.which(name) for name in ('ffmpeg', 'ffprobe'))
        if not all(paths):
            raise ValueError('Native web/Studio audio preparation needs ffmpeg and ffprobe on PATH.')
        return tuple(Path(path) for path in paths)
    cfg = web_toolchain.config()['ffmpeg']
    root = web_toolchain.ROOT / 'deps/ffmpeg-web'
    root.mkdir(parents=True, exist_ok=True)
    archive = root / (cfg['directory'] + '.zip')
    if not archive.exists():
        print('Downloading pinned web audio preparation tools:', cfg['version'], flush=True)
        temporary = archive.with_suffix('.zip.part')
        with urllib.request.urlopen(cfg['url'], timeout=60) as source, temporary.open('wb') as output:
            while block := source.read(1024 * 1024):
                output.write(block)
        if digest(temporary) != cfg['sha256']:
            raise ValueError('FFmpeg archive SHA-256 mismatch.')
        temporary.replace(archive)
    if digest(archive) != cfg['sha256']:
        raise ValueError(f'FFmpeg archive SHA-256 mismatch: {archive}')
    # Only extract explicit, hash-verified entries. These tools are never shipped.
    with zipfile.ZipFile(archive) as source:
        for relative in ('bin/ffmpeg.exe', 'bin/ffprobe.exe', 'LICENSE', 'README.txt'):
            data = source.read(cfg['directory'] + '/' + relative)
            target = root / relative
            if not target.exists() or digest(target) != hashlib.sha256(data).hexdigest():
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(data)
    return root / 'bin/ffmpeg.exe', root / 'bin/ffprobe.exe'


def tool_identity():
    if os.name == 'nt':
        return web_toolchain.config()['ffmpeg']
    return {'native': {path.name: digest(path) for path in tools()}}


def supported_wav(path):
    """The runtime requires a PCM fmt chunk immediately after the RIFF header."""
    with path.open('rb') as stream:
        head = stream.read(36)
    return (len(head) == 36 and head[:4] == b'RIFF' and head[8:16] == b'WAVEfmt '
            and struct.unpack_from('<H', head, 20)[0] == 1
            and struct.unpack_from('<H', head, 22)[0] in (1, 2)
            and struct.unpack_from('<H', head, 34)[0] in (8, 16))


def prepare(files, output):
    candidates = [(path, target) for path, target in files
                  if path.suffix.lower() == '.wav' and not supported_wav(path)]
    ffmpeg, ffprobe = tools()
    output.mkdir(parents=True, exist_ok=True)
    report_path = output / 'report.json'
    previous = json.loads(report_path.read_text('utf8')) if report_path.exists() else {}
    cfg = tool_identity()
    old_files = previous.get('files', {}) if previous.get('tool') == cfg else {}
    replacements, records = {}, {}
    for source, target in candidates:
        name = target.removeprefix('/game/')
        destination = output / name
        source_hash = digest(source)
        old = old_files.get(name, {})
        if (old.get('input_sha256') == source_hash and destination.exists()
                and old.get('output_sha256') == digest(destination) and supported_wav(destination)):
            records[name] = old
        else:
            info = json.loads(subprocess.check_output([str(ffprobe), '-v', 'error', '-select_streams', 'a:0',
                '-show_entries', 'stream=codec_name,sample_rate,channels', '-of', 'json', str(source)], text=True))['streams'][0]
            destination.parent.mkdir(parents=True, exist_ok=True)
            temporary = destination.with_suffix('.wav.tmp')
            subprocess.run([str(ffmpeg), '-nostdin', '-hide_banner', '-loglevel', 'error', '-xerror', '-y',
                '-i', str(source), '-map', '0:a:0', '-map_metadata', '-1', '-fflags', '+bitexact',
                '-flags:a', '+bitexact', '-c:a', 'pcm_s16le', '-f', 'wav', str(temporary)], check=True)
            with wave.open(str(temporary), 'rb') as decoded:
                rate, channels, frames = decoded.getframerate(), decoded.getnchannels(), decoded.getnframes()
                if (rate != int(info['sample_rate']) or channels != info['channels']
                        or decoded.getsampwidth() != 2 or frames <= 0):
                    raise ValueError(f'Web audio conversion changed format or produced no samples: {name}')
            if not supported_wav(temporary):
                raise ValueError(f'Web audio conversion is still unsupported: {name}')
            os.replace(temporary, destination)
            records[name] = {'input_sha256': source_hash, 'output_sha256': digest(destination),
                'input_codec': info['codec_name'], 'sample_rate': rate, 'channels': channels, 'frames': frames}
        replacements[target] = destination
    report_path.write_text(json.dumps({'tool': cfg, 'files': records}, indent=2) + '\n', encoding='utf8')
    print(f'Prepared {len(records)} web-only PCM audio files (source resources unchanged).', flush=True)
    return replacements


def verify(files, package, output):
    report = json.loads((output / 'report.json').read_text('utf8'))
    if report['tool'] != tool_identity():
        raise ValueError('Web audio was prepared with a different toolchain.')
    expected = {target.removeprefix('/game/'): source for source, target in files
                if source.suffix.lower() == '.wav' and not supported_wav(source)}
    if set(report['files']) != set(expected):
        raise ValueError('Web audio conversion list is stale.')
    for name, source in expected.items():
        entry, destination = report['files'][name], package / name
        if digest(source) != entry['input_sha256'] or digest(destination) != entry['output_sha256']:
            raise ValueError(f'Web audio input/output changed: {name}')
        if not supported_wav(destination):
            raise ValueError(f'Unsupported packaged web audio: {name}')
        with wave.open(str(destination), 'rb') as audio:
            actual = (audio.getframerate(), audio.getnchannels(), audio.getnframes(), audio.getsampwidth())
            if actual != (entry['sample_rate'], entry['channels'], entry['frames'], 2):
                raise ValueError(f'Web audio metadata mismatch: {name}')
            if len(audio.readframes(audio.getnframes())) != entry['frames'] * entry['channels'] * 2:
                raise ValueError(f'Truncated packaged web audio: {name}')
    return len(expected)
