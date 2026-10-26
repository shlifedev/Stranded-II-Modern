"""Explicit build boundaries for the base game and Extended; no engine code generation."""
from dataclasses import dataclass
from pathlib import Path
import hashlib
import re

NAMES = ('classic', 'extended')
INCLUDE = re.compile(r'^\s*Include\s+"([^"]+)"', re.I | re.M)


@dataclass(frozen=True)
class Edition:
    name: str

    def __post_init__(self):
        if self.name not in NAMES:
            raise ValueError(f'Unknown edition: {self.name}')

    @property
    def entry(self):
        return 'StrandedII.bb' if self.name == 'classic' else 'StrandedIIExtended.bb'

    @property
    def executable(self):
        return 'StrandedII.exe' if self.name == 'classic' else 'StrandedII-Extended.exe'

    @property
    def title(self):
        return 'Stranded II' if self.name == 'classic' else 'Stranded II - Extended'

    @property
    def archive_root(self):
        return 'StrandedII' if self.name == 'classic' else 'StrandedII-Extended'

    @property
    def version_config(self):
        return 'config/version.json' if self.name == 'classic' else 'config/editions/extended/version.json'

    @property
    def defaults(self):
        return 'config/modern.ini' if self.name == 'classic' else 'config/editions/extended/modern.ini'

    def build_dir(self, root):
        return root / 'build' if self.name == 'classic' else root / 'build/extended'

    def runtime(self, root):
        return self.build_dir(root) / 'runtime'

    def overrides(self, root):
        return (root / 'resources/overrides', root / 'resources/editions' / self.name / 'overrides')


def source_files(root, edition='classic', platform='windows'):
    """Follow the actual static Include graph, using Blitz's source-root paths."""
    profile = Edition(edition)
    root = root.resolve()
    base = root / 'src/engine'
    result, visiting = {}, set()

    def visit(relative):
        path = (base / relative).resolve()
        if not path.is_relative_to(base.resolve()):
            raise ValueError(f'Engine Include escapes source root: {relative}')
        parts = tuple(p.lower() for p in path.relative_to(base.resolve()).parts)
        if 'editions' in parts:
            index = parts.index('editions')
            if len(parts) <= index + 1 or parts[index + 1] != edition:
                raise ValueError(f'{edition} includes another edition: {relative}')
        key = path.relative_to(root).as_posix()
        if key in visiting:
            raise ValueError(f'Cyclic engine Include: {relative}')
        if key in result:
            return
        visiting.add(key)
        text = path.read_text('utf8')
        result[key] = path
        for child in INCLUDE.findall(text):
            visit(child.replace('\\', '/'))
        visiting.remove(key)

    if platform not in ('windows','web','macos') or (platform!='windows' and edition!='classic'):
        raise ValueError(f'Unsupported source target: {edition}/{platform}')
    entry = {'web': 'StrandedIIWeb.bb', 'macos': 'StrandedIIMac.bb'}.get(platform, profile.entry)
    visit(entry)
    return result


def source_hashes(root, edition='classic', platform='windows'):
    return {name: hashlib.sha256(path.read_bytes()).hexdigest()
            for name, path in source_files(root, edition, platform).items()}


def override_files(root, edition='classic'):
    """Shared resources first, then only the selected edition's UTF-8 overrides."""
    result = {}
    for folder in Edition(edition).overrides(root):
        for path in sorted(folder.rglob('*')):
            if not path.is_file() or path.name == '.gitkeep':
                continue
            rel = path.relative_to(folder)
            parts = tuple(p.lower() for p in rel.parts)
            if (not path.resolve().is_relative_to(folder.resolve()) or
                    any(p in ('saves', 'preserved') for p in parts) or
                    parts[0] != 'mods' or len(parts) < 3 or parts[1] != 'stranded ii' or
                    path.suffix.lower() in ('.cfg', '.sav', '.bak', '.tmp', '.log', '.exe', '.dll') or
                    path.name.lower() == 'settings.inf' or path.name.lower().startswith('screen_')):
                raise ValueError(f'Overrides may only replace game content, not player settings: {path}')
            result[rel.as_posix()] = path
    return result
