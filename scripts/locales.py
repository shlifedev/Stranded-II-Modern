"""Strict catalog format, reference validation and language-neutral resources."""
from pathlib import Path
import json, re, struct
from resource_spans import resources, spans, source_text
from s2map import Map

SEP=str.maketrans({'¦':'\x1f','¶':'\x1e','¿':'\x1d'})
REFERENCE=re.compile(r'\[\[loc:([A-Za-z0-9_.-]+)(?:\|[^\]]*)?\]\]')
PLACEHOLDER=re.compile(r'(?<!\\)\$(?:[0-9]+|[A-Za-z_][A-Za-z0-9_]*)')

def catalog(path):
    result={}
    for number,line in enumerate(path.read_text('utf8').splitlines(),1):
        if not line or line.startswith('#'):continue
        if '\t' not in line:raise ValueError(f'{path}:{number}: expected key<TAB>text')
        key,value=line.split('\t',1)
        if not re.fullmatch(r'[A-Za-z0-9_.-]+',key):raise ValueError(f'{path}:{number}: invalid key {key!r}')
        if key in result:raise ValueError(f'{path}:{number}: duplicate {key}')
        def unescape(m):
            if m[1] not in '\\nrt':raise ValueError(f'{path}:{number}: invalid escape {m[0]}')
            return {'n':'\n','r':'\r','t':'\t','\\':'\\'}[m[1]]
        # A trailing backslash is invalid rather than silently discarded.
        if len(value)-len(value.rstrip('\\')) & 1:raise ValueError(f'{path}:{number}: trailing escape')
        result[key]=re.sub(r'\\(.)',unescape,value).translate(SEP)
    return result

def languages(root):
    """Discover paired UTF-8 catalogs/metadata in a stable menu order."""
    folder=root/'locales'
    codes={p.stem for p in folder.glob('*.tsv')}
    # legacy-index.json is a key migration table, not a language manifest.
    codes.update(p.stem for p in folder.glob('*.json') if p.name!='legacy-index.json')
    if not {'en','ko'}<=codes:raise ValueError('English and Korean language packs are required')
    result={}
    for code in sorted(codes,key=lambda c:(0 if c=='ko' else 1 if c=='en' else 2,c)):
        if not re.fullmatch(r'[a-z0-9]+(?:-[a-z0-9]+)*',code):
            raise ValueError(f'Invalid language code: {code!r}; use lowercase ASCII letters/digits and hyphens')
        path=folder/f'{code}.json'
        if not path.is_file() or not (folder/f'{code}.tsv').is_file():
            raise ValueError(f'{code}: both locales/{code}.json and locales/{code}.tsv are required')
        info=json.loads(path.read_text('utf8'))
        if not isinstance(info,dict) or set(info)-{'name','font'}:
            raise ValueError(f'{path}: expected name and optional font fields')
        name=info.get('name')
        if not isinstance(name,str) or not name.strip() or any(ord(c)<32 or ord(c)>65535 for c in name):
            raise ValueError(f'{path}: name must be a nonempty single-line BMP string')
        if 'font' in info and (not isinstance(info['font'],str) or not info['font'].strip()):
            raise ValueError(f'{path}: font must be a nonempty file path, or omit it for the default')
        result[code]=info
    return result


def validate(root):
    catalogs={lang:catalog(root/'locales'/f'{lang}.tsv') for lang in languages(root)}
    en=catalogs['en']
    if not en:raise ValueError('English catalog is empty')
    for lang,values in catalogs.items():
        for key,value in values.items():
            if key not in en:raise ValueError(f'{lang}: key absent from English: {key}')
            if set(PLACEHOLDER.findall(value))!=set(PLACEHOLDER.findall(en[key])):
                raise ValueError(f'{lang}: placeholders differ: {key}')
            if any(ord(c)>65535 for c in value):raise ValueError(f'{lang}: BMP font cannot represent {key}')
            for ref in REFERENCE.findall(value):
                if ref not in en:raise ValueError(f'{lang}: missing reference {ref}')
    # Cycles would otherwise reach the runtime resolver depth limit.
    def visit(key,values,stack):
        if key in stack:raise ValueError('Cyclic localization reference: '+key)
        if len(stack)>=8:raise ValueError('Localization nesting exceeds runtime limit: '+key)
        for child in REFERENCE.findall(values.get(key,en[key])):visit(child,values,stack+[key])
    for values in catalogs.values():
        for key in values:visit(key,values,[])
    for folder in ('src/engine','resources/overrides'):
        for p in (root/folder).rglob('*'):
            if p.is_file() and p.suffix in ('.bb','.inf','.txt','.s2s'):
                text=p.read_text('utf8')
                if p.suffix=='.bb':text='\n'.join(line for line in text.splitlines() if not line.lstrip().startswith(';'))
                for ref in REFERENCE.findall(text):
                    if ref not in en:raise ValueError(f'{p}: missing reference {ref}')
    return catalogs

def encoded(text):return text.translate(SEP).encode('utf8')
def bstring(text):
    value=encoded(text)
    return struct.pack('<i',len(value))+value

def prepare(root,game,runtime):
    catalogs=validate(root)
    keys=json.loads((root/'resources/source-keys.json').read_text('utf8'))
    for key in keys.values():
        if key not in catalogs['en']:raise ValueError('Missing source mapping: '+key)
    base=game/'mods/Stranded II';out=runtime/'mods/Stranded II'
    entries=list(resources(base));aliases=set();changed={};references=set()
    def ref(text):
        if text not in keys:return text
        references.add(keys[text]);return '[[loc:'+keys[text]+']]'
    for location,text,mode in entries:
        for a,b,kind in reversed(spans(text,location,mode)):
            if kind=='alias':aliases.add(text[a:b])
            else:text=text[:a]+ref(text[a:b])+text[b:]
        changed[location]=text
    aliases.update(['Color Game','Shooting Range','Tower Defense','Survival Guide','NoName'])
    for p in base.rglob('*'):
        if p.suffix.lower() not in ('.inf','.s2s','.txt'):continue
        relative=p.relative_to(base).as_posix()
        if relative.startswith('saves/'):continue
        text=changed.get(relative,source_text(p))
        if relative=='sys/keys.inf':
            text=re.sub(r'(?m)^(\w+=)([^\n]+)',lambda m:m[1]+ref(m[2]),text)
        if relative=='sys/credits.inf':
            text=re.sub(r'(?m)^([!0-6 ]*)(.+)$',lambda m:m[1]+ref(m[2]),text)
            text+='\n\n!1 - Community modernization -\nUTF-8 / localization: Noto Sans KR (SIL OFL 1.1)\nOriginal game: Unreal Software\n'
        dest=out/relative;dest.parent.mkdir(parents=True,exist_ok=True)
        dest.write_bytes(encoded(text).replace(b'\n',b'\r\n'))
    map_count=0
    for p in (base/'maps').rglob('*.s2'):
        relative=p.relative_to(base).as_posix();model=Map(p.read_bytes());fields={}
        assert model.write({})==p.read_bytes(),relative
        for field in model.fields:
            if field.kind=='attachment':continue
            location=relative+'::'+field.key;text=field.data.decode('cp1252')
            if location in changed:text=changed[location].replace('\n','¦')
            elif location+'::indicator' in changed:
                parts=text.split('¦');parts[6]=changed[location+'::indicator'];text='¦'.join(parts)
            fields[field.key]=encoded(text)
        dest=out/relative;dest.parent.mkdir(parents=True,exist_ok=True)
        dest.write_bytes(model.write(fields))
        rebuilt=Map(dest.read_bytes())
        # Replacing all converted fields with originals must restore every byte,
        # including geometry, numeric IDs, extensions and embedded attachments.
        assert rebuilt.write({f.key:f.data for f in model.fields if f.kind!='attachment'})==model.data,relative
        map_count+=1
    pairs={a:ref(a) for a in aliases if a in keys}
    (out/'sys/ko_aliases.dat').write_bytes(struct.pack('<i',len(pairs))+b''.join(bstring(a)+bstring(b) for a,b in sorted(pairs.items())))
    terms=json.loads((root/'resources/help-terms.json').read_text('utf8'));lines=[]
    for line in source_text(game/'core/scriptcommands.inf').splitlines():
        parts=line.split('¦')
        for i in range(1,len(parts)):
            parts[i]=re.sub(r'[^\W\d_]+',lambda m:'[[loc:'+terms[m[0]]+']]' if m[0] in terms else m[0],parts[i])
        lines.append('¦'.join(parts))
    (runtime/'core/scriptcommands.inf').write_bytes(encoded('\n'.join(lines)+'\n').replace(b'\n',b'\r\n'))
    return catalogs,{'maps_roundtrip':map_count,'resource_keys':len(references),'display_aliases':len(pairs)}
