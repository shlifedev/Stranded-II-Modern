"""Extract translatable spans without rewriting script identifiers or map geometry."""
from pathlib import Path
from collections import defaultdict
import re,json,hashlib
from s2map import Map
SPEECH={'msg':{0},'add':{0},'seqmsg':{1},'msgbox':{0},'process':{0},'setindicatorinfo':{1},'name':{2},'text3d':{0},'setname':{2},'text':{1},'button':{1},'msgwin':{0},'inputwin':{0},'seqimagetext':{1},'cracklock':{0},'incskill':{2},'msg_replace':{1}}
ALIASES={'diary':{0},'modifyentry':{0},'freeentry':{0},'extendentry':{0},'modifyentryline':{0},'showentry':{0}}
LEX=re.compile(r'//[^\n¦]*|/\*[\s\S]*?\*/|"[^"\r\n¦]*"|[A-Za-z_][A-Za-z_0-9]*|[{};,()\n]')

def source_text(path):
    return path.read_bytes().decode('cp1252').replace('\r\n','\n')

def spans(text, location, mode='mixed'):
    """Yield (start,end,kind). Offsets are in the original decoded resource."""
    seen=[]
    def add(a,b,kind):
        # Keep leading/trailing spacing and font/color/key markup intact.
        while a<b and text[a].isspace():a+=1
        while b>a and text[b-1].isspace():b-=1
        if a==b or not re.search('[A-Za-zÀ-ÿ]',text[a:b]):return
        if text[a:b].startswith('$img='):return
        if any(a<y and b>x for x,y,_ in seen):return
        seen.append((a,b,kind))
    if mode=='plain':
        for m in re.finditer(r'[^\n¦]+',text):
            a=m.start()
            if re.match('![0-6]',m.group()):a+=2
            add(a,m.end(),'prose')
        return seen
    if mode=='groups':
        for m in re.finditer(r'^(?:object|unit|item|info|building)=[^,\n]+,(.+)',text,re.M):add(m.start(1),m.end(1),'label')
        return seen
    if mode=='strings':
        for m in re.finditer(r'^[me0]\d+=(.*)',text,re.M):add(m.start(1),m.end(1),'ui')
        return seen
    # Text sections, dialogues, descriptions and definition fields.
    block=False; comment=False; depth=0; offset=0
    for line in text.splitlines(keepends=True):
        raw=line.rstrip('\r\n'); t=raw.strip(); start=offset+len(raw)-len(raw.lstrip())
        if t.startswith('/*'):comment=True
        if comment:
            if '*/' in t:comment=False
            offset+=len(line);continue
        if t.startswith(('//','#')):offset+=len(line);continue
        if t in ('text=start','info=start','description=start'):block=True
        elif t in ('text=end','info=end','description=end'):block=False
        elif block:
            add(start+(2 if re.match('![0-6]',t) else 0),offset+len(raw),'prose')
        elif depth==0:
            match=re.match(r'(name|info|description|title|skillname)=(.*)',t)
            if match and match[2] not in ('start','end'):
                add(start+match.start(2),start+match.end(2),'label')
            elif t.startswith('button=') and ',' in t:
                add(start+t.index(',')+1,start+len(t),'button')
            elif t.startswith('ibutton=') and t.count(',')>=2:
                a=t.index(',',t.index(',')+1)+1;add(start+a,start+len(t),'button')
            elif t and not re.match(r'[\w_]+\s*=',t) and not re.search(r'[{};]',t) and not t.startswith(('$','on:', 'elseif', 'else')):
                add(start+(2 if re.match('![0-6]',t) else 0),start+len(t),'prose')
        # Braces in quoted strings/comments do not affect script depth.
        clean=re.sub(r'"[^"\n]*"|//.*','',t)
        depth=max(0,depth+clean.count('{')-clean.count('}'))
        offset+=len(line)
    # Script display literals, including concatenated fragments. IDs stay aliases.
    command=None; arg=0; nest=0
    for m in LEX.finditer(text):
        tok=m.group()
        if tok.startswith(('/',)):continue
        if tok in ('{','}',';','\n'):command=None;arg=0;nest=0
        elif tok in SPEECH or tok in ALIASES:
            if command is None:command=tok;arg=0;nest=0
        elif tok=='(':nest+=1
        elif tok==')':nest-=1
        elif tok==',' and nest<=1:arg+=1
        elif tok.startswith('"') and command:
            if arg in SPEECH.get(command,set()):add(m.start()+1,m.end()-1,'script')
            elif arg in ALIASES.get(command,set()):add(m.start()+1,m.end()-1,'alias')
    return sorted(seen)

def resources(BASE):
    for p in sorted(BASE.rglob('*')):
        if p.suffix.lower() not in ('.inf','.s2s','.txt'):continue
        rel=p.relative_to(BASE).as_posix()
        if rel.startswith(('sfx/','saves/')) or p.name=='info.txt':continue
        mode='mixed'
        if rel=='sys/strings.inf':mode='strings'
        if rel=='sys/groups.inf':mode='groups'
        if rel.startswith(('tutorial/','sys/skillinfos/')):mode='plain'
        if rel=='sys/credits.inf':continue # dedicated credit heading aliases
        yield rel,source_text(p),mode
    for p in sorted((BASE/'maps').rglob('*.s2')):
        rel=p.relative_to(BASE).as_posix()
        model=Map(p.read_bytes())
        for f in model.fields:
            if not f.data:continue
            t=f.data.decode('cp1252')
            if f.key=='global_script' or ('/mode0/' in f.key and f.key.endswith('/value')):
                yield rel+'::'+f.key,t.replace('¦','\n'),'mixed'
            elif re.search(r'info/\d+/type36/vars',f.key):
                parts=t.split('¦')
                if len(parts)>6 and parts[6]:yield rel+'::'+f.key+'::indicator',parts[6],'plain'
