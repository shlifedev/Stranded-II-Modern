from bisect import bisect_right
from functools import cache
from pathlib import Path
import os
import re
import subprocess
from textwrap import dedent

from tree_sitter_language_pack import get_parser


ROOT = Path(__file__).resolve().parents[2]
LIMIT = 30

VENDORED = (
    'licenses/', 'src/native/macos/licenses/',
    'src/blitz3d/deps/dxsdk/include/', 'src/blitz3d/deps/fmod/include/',
    'src/blitz3d/deps/glew/include/', 'src/blitz3d/src/help/views/tutorials/',
)
VENDOR_FILES = {
    'src/blitz3d/src/ios.toolchain.cmake', 'src/blitz3d/src/stdutil/ecvt.c',
    'src/blitz3d/src/stdutil/gcvt.c', 'src/blitz3d/src/tools/bblaunch/checkdx.cpp',
    'src/blitz3d/src/modules/bb/graphics.webgpu/stb_image.h',
}
LANGUAGES = {
    '.py': 'python', '.rb': 'ruby', '.sh': 'bash', '.ps1': 'powershell',
    '.ts': 'typescript', '.tsx': 'tsx', '.js': 'javascript', '.jsx': 'javascript', '.cjs': 'javascript',
    '.mjs': 'javascript', '.css': 'css', '.scss': 'scss', '.html': 'html',
    '.erb': 'embeddedtemplate', '.xml': 'xml', '.manifest': 'xml',
    '.yaml': 'yaml', '.yml': 'yaml', '.cmake': 'cmake', '.wgsl': 'wgsl',
    '.java': 'java', '.lua': 'lua', '.md': 'markdown', '.toml': 'toml',
}
NAMES = {'CMakeLists.txt': 'cmake', 'Gemfile': 'ruby', 'requirements.txt': 'requirements'}
C_EXTENSIONS = {'.c', '.cc', '.cpp', '.cxx', '.h', '.hpp', '.h_old', '.old', '.m', '.mm', '.rc', '.glsl'}

C_TOKENS = re.compile(
    rb"(?:\b[0-9]|\.[0-9])(?:[a-zA-Z0-9_.]|'[a-zA-Z0-9_])*"
    rb'|R"(?P<delim>[^()\\\s]{0,16})\([\s\S]*?\)(?P=delim)"'
    rb'|"(?:\\[\s\S]|[^"\\\n])*"?'
    rb"|'(?:\\[\s\S]|[^'\\\n])*'?"
    rb'|(?P<comment>//(?:\\\n|[^\n])*|/\*[\s\S]*?(?:\*/|\Z))'
)
BLITZ_TOKENS = re.compile(rb'"[^"\n]*"?|(?P<comment>;[^\n]*)')
INF_TOKENS = re.compile(rb'"[^"\n]*"?|(?P<comment>^[ \t]*\#[^\n]*|//[^\n]*|/\*[\s\S]*?(?:\*/|\Z))', re.M)
COMMENT_NODES = {'comment', 'line_comment', 'block_comment', 'bracket_comment', 'js_comment', 'comment_directive'}

parser_for = cache(get_parser)


def yaml_value(node, *keys):
    for key in keys:
        if node.type in ('block_node', 'flow_node'):
            node = node.named_child(0)
        if node is None or not node.type.endswith('mapping'):
            return None
        value = None
        for pair in node.named_children:
            name = pair.child_by_field_name('key')
            if name and name.text == key:
                value = pair.child_by_field_name('value')
                break
        if value is None:
            return None
        node = value
    return node.text


def workflow_script(node):
    pair = node.parent.parent
    key = pair.child_by_field_name('key')
    if key is None or key.text != b'run':
        return
    scope = pair.parent
    shell = yaml_value(scope, b'shell')
    runner = None
    while scope and not shell:
        shell = yaml_value(scope, b'defaults', b'run', b'shell')
        runner = runner or yaml_value(scope, b'runs-on')
        scope = scope.parent
    shell = shell or (b'pwsh' if b'windows' in (runner or b'') else b'bash')
    interpreter = shell.strip(b'"\'').split(maxsplit=1)[0]
    extensions = {b'pwsh': '.ps1', b'powershell': '.ps1', b'python': '.py', b'cmd': '.cmd'}
    header, _, body = node.text.partition(b'\n')
    script = dedent(body.decode('utf8')).encode('utf8')
    original = node.start_byte + len(header) + 1
    starts = [original, *(original + m.end() for m in re.finditer(b'\n', body))]
    for row in comment_lines(Path('step' + extensions.get(interpreter, '.sh')), script):
        yield starts[row - 1], starts[row - 1] + 1


def parsed_comments(language, data, offset=0):
    root = parser_for('yaml' if language == 'workflow' else language).parse(data).root_node
    stack = [root]
    while stack:
        node = stack.pop()
        start, end = node.start_byte, node.end_byte
        kind = node.type.lower()
        nested = None
        if kind in COMMENT_NODES:
            yield offset + start, offset + end
        elif language == 'markdown' and kind == 'inline':
            nested = 'markdown_inline'
        elif language in ('markdown', 'markdown_inline') and kind in ('html_block', 'html_tag'):
            nested = 'html'
        elif language == 'embeddedtemplate' and kind in ('content', 'code'):
            nested = 'html' if kind == 'content' else 'ruby'
        elif language == 'html' and kind == 'raw_text':
            nested = 'javascript' if node.parent.type == 'script_element' else 'css'
        else:
            if language == 'workflow' and kind == 'block_scalar':
                yield from workflow_script(node)
            stack.extend(reversed(node.named_children))
        if nested:
            yield from parsed_comments(nested, data[start:end], offset + start)


def spans(path, data):
    suffix = path.suffix.lower()
    if path.as_posix().startswith(VENDORED) or path.as_posix() in VENDOR_FILES:
        return
    if suffix in C_EXTENSIONS:
        tokens = C_TOKENS
    elif suffix in ('.bb', '.decls'):
        tokens = BLITZ_TOKENS
    elif suffix in ('.inf', '.s2s'):
        tokens = INF_TOKENS
    else:
        tokens = None
    if tokens:
        for match in tokens.finditer(data):
            if match.lastgroup == 'comment':
                yield match.span()
    elif suffix in ('.tsv', '.ini', '.iss', '.def', '.cmd', '.bat') or path.name in (
            '.gitignore', '.gitattributes', 'exclusions.txt'):
        if suffix in ('.cmd', '.bat'):
            pattern = rb'^[ \t]*@?(?i:rem\b|::)[^\n]*'
        elif suffix in ('.iss', '.def'):
            pattern = rb'^[ \t]*;[^\n]*'
        elif suffix == '.ini':
            pattern = rb'^[ \t]*[;#][^\n]*'
        else:
            pattern = rb'^#[^\n]*'
        for match in re.finditer(pattern, data, re.M):
            yield match.span()
    else:
        language = NAMES.get(path.name) or LANGUAGES.get(suffix)
        if language == 'yaml' and path.parent == Path('.github/workflows'):
            language = 'workflow'
        if language:
            yield from parsed_comments(language, data)


def comment_lines(path, data):
    starts = [0, *(m.end() for m in re.finditer(b'\n', data))]
    lines = set()
    for start, end in spans(path, data):
        token = data[start:end].rstrip(b'\n')
        if start == 0 and path.suffix.lower() in ('.sh', '.py', '.rb', '.js', '.mjs') and token.startswith(b'#!'):
            continue
        if start == 0 and path.suffix.lower() == '.md' and re.fullmatch(rb'<!-- dev-docs-ai-translation: \{[^\n]*\} -->', token):
            continue
        if path.name == 'resource.h' and token == b'//{{NO_DEPENDENCIES}}':
            continue
        last = start + max(len(token) - 1, 0)
        lines.update(range(bisect_right(starts, start), bisect_right(starts, last) + 1))
    return sorted(lines)


def files():
    entries = subprocess.check_output(['git', 'ls-files', '-z', '--cached', '--others', '--exclude-standard'], cwd=ROOT)
    return (Path(os.fsdecode(entry)) for entry in entries.split(b'\0') if entry)


def main():
    total = 0
    for path in files():
        source = ROOT / path
        if not source.is_file() or source.is_symlink():
            continue
        data = source.read_bytes().replace(b'\r\n', b'\n').replace(b'\r', b'\n')
        lines = comment_lines(path, data)
        if lines:
            print(f'{path.as_posix()}: {", ".join(map(str, lines))}')
            total += len(lines)
    print(f'{total}/{LIMIT} project comment lines')
    return int(total > LIMIT)


if __name__ == '__main__':
    raise SystemExit(main())
