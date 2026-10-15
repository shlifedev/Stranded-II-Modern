"""v2 filesystem authoring, with the same editing commands and native outputs."""
from copy import deepcopy
from pathlib import Path
import uuid

from . import content, scripts, file_store as store, definition_files as definitions, map_files
from .maps import EditableMap, new_map, number
from .project import Project, MOD, ROOT, KEY, atomic, encode, digest, safe, locked, resource_path, catalog_output


class FileProject(Project):
    def __init__(self, directory):
        self.root = Path(directory).resolve()
        self.path = self.root/'project.json'
        with locked(self.root):
            store.recover(self.root)
            self.load()

    @classmethod
    def create(cls, directory, base, name='Extended project', version=3):
        from .workspace import ignore_file
        root, base = Path(directory).resolve(), Path(base).resolve()
        reserved = {'project.json', 'sources', '.studio', *store.DIRECTORIES}
        if root.exists() and any(p.name.casefold() in reserved for p in root.iterdir()):
            raise ValueError('Studio paths already exist; open the project or choose another folder')
        if root == base or root.is_relative_to(base) or base.is_relative_to(root):
            raise ValueError('The authoring project and base runtime must be separate directories')
        if not (base/MOD/'sys').is_dir():
            raise ValueError('Base must be a prepared runtime containing mods/Stranded II/sys')
        ignore = ignore_file(root)
        manifest = {'format':f'stranded-studio/{version}','edition':'extended','name':name,'base':{'contentHash':store.base_hash(base)}}
        root.mkdir(parents=True, exist_ok=True)
        with locked(root):
            before = store.scan(root)
            after = {**before,'project.json':encode(manifest),'.gitignore':ignore}
            after['.gitattributes'] = store.git_attributes(before.get('.gitattributes',b''))
            atomic(safe(root,'.studio/local.json'), encode({'base':str(base)}))
            store.commit(root,before,after,{'revision':-1,'undo':[],'redo':[]},remember=False)
        return cls(root)

    def load(self):
        self.files = store.scan(self.root)
        self._original_files = dict(self.files)
        self._manifest_bytes = self.files['project.json']
        self.manifest = store.read_json(self._manifest_bytes, 'project.json')
        if self.manifest.get('format') not in store.FORMATS or self.manifest.get('edition') != 'extended':
            raise ValueError('Not a supported file-based Studio project')
        pin = self.manifest.get('base',{})
        pin = pin.get('contentHash') if isinstance(pin,dict) else None
        if not isinstance(pin,str) or len(pin)!=64 or any(c not in '0123456789abcdef' for c in pin):
            raise ValueError('project.json must pin the base contentHash')
        local = safe(self.root, '.studio/local.json')
        settings = store.read_json(local.read_bytes(), str(local)) if local.exists() else {}
        if 'base' in settings and (not isinstance(settings['base'],str) or not settings['base']):
            raise ValueError('Invalid local resource connection; reconnect the base in Studio')
        self.base = Path(settings.get('base', ROOT/'build/studio/runtime')).resolve()
        if not (self.base/MOD/'sys').is_dir():
            raise ValueError('Base runtime is missing. Reconnect the base in Studio or use connect --base PATH.')
        self.base_actual_hash = store.base_hash(self.base)
        self.base_texts = {p.relative_to(self.base).as_posix():p.read_text('utf8') for p in sorted((self.base/MOD/'sys').glob('*.inf'))}
        self._texts = self._catalog = None
        self._state = store.state(self.root, self.files)
        self.script_files = scripts.scan(self)
        self._script_originals = deepcopy(self.script_files)
        self._script_writes = set()
        self._blobs = {}
        self._rebuild()

    def _rebuild(self):
        previous = getattr(self,'data',{})
        self.raw_definitions = {MOD+p[len('resources/'):]:raw.decode('utf8') for p,raw in self.files.items()
                                if p.startswith('resources/sys/') and p.endswith('.inf')}
        files, self.aliases = definitions.compile_files(self)
        from locales import catalog
        locales = {Path(p).stem:catalog(Path(p), raw.decode('utf8')) for p,raw in self.files.items()
                   if p.startswith('locales/') and p.endswith('.tsv')}
        maps = {}
        bindings = {}
        self._missing_scripts = []
        for path, meta, target in self.targets():
            if target['scope'] == 'map':
                maps[target['map']] = {'edits':[]}
            ref = meta.get('script')
            if ref:
                scripts.path_name(ref['path'])
                if ref['path'] not in self.script_files:
                    self._missing_scripts.append({'severity':'error','code':'script-binding','message':'Missing script: '+ref['path'],
                                                  'location':ref['path'],'target':target,'field':'script'})
                if ref.get('shared'): bindings[scripts.target_key(target)] = ref['path']
        assets = {MOD+p[len('resources/'):]:p for p in self.files if p.startswith('resources/') and MOD+p[len('resources/'):] not in files}
        for path in assets: resource_path(path)
        self.data = {**self.manifest,'revision':self._state['revision'],'files':files,'maps':maps,
                     'assets':assets,'locales':locales,'scriptBindings':bindings}
        if previous.get('files') != files:
            self._texts = self._catalog = None
        elif not hasattr(self,'_texts'):
            self._texts = self._catalog = None
        self._map_cache = {}

    def targets(self):
        for path, meta, _ in definitions.entries(self):
            yield path, meta, {'scope':'definition','kind':meta['kind'],'id':meta['id']}
        yield from map_files.targets(self)

    def _meta(self, target):
        key = scripts.target_key(target)
        return next(((path,meta) for path,meta,t in self.targets() if scripts.target_key(t) == key), (None,None))

    def script_ref(self, target, text, old=None):
        if old and old.get('shared'):
            return dict(old)
        if old:
            path = old['path']
        else:
            if target['scope'] == 'definition':
                name = str(target['id']) if target['kind'] != 'recipe' else digest(str(target['id']).encode())[:24]
                prefix = f'scripts/owned/{target["kind"]}/{name}'
            else:
                prefix = f'scripts/owned/maps/{target["map"]}/'+('global' if target['scope']=='map' else f'{target["kind"]}-{target["id"]}')
            path = prefix+'.s2s';index = 1
            while path in self.script_files:
                path = prefix+f'-{index}.s2s';index += 1
        current = self.script_files.get(path)
        scripts.write(self,path,text,digest(current.encode()) if current is not None else None)
        return {**(old or {}),'path':path,'shared':False}

    def _set_ref(self, target, ref):
        if target['scope'] == 'definition': self._ensure_definition(target['kind'], target['id'])
        path, meta = self._meta(target)
        if path is None: raise ValueError('Missing script target')
        old = meta.get('script')
        meta['script'] = ref
        if target['scope'] == 'definition':
            body_path = path[:-len('.meta.json')]+'.inf'
            body = self.files[body_path].decode('utf8')
            if not any(f[0]=='script' for f in content.fields(body)):
                body = content.edit(body, {'script':''})
            self.files[body_path] = body.encode()
        if target['scope'] == 'entity' and self.manifest['format'] == store.FORMAT:
            map_files.set_entity(self,target['map'],meta)
        else:
            self.files[path] = encode(meta)
        if old and not old.get('shared') and old['path'] != ref['path']:
            previous = old['path']
            referenced = any(m.get('script',{}).get('path') == previous for _,m,_ in self.targets())
            referenced = referenced or any(r['path'] == previous for text in self.script_files.values() for r in scripts.analyze(text,'')['references'])
            if not referenced:
                self.script_files.pop(previous,None);self._script_writes.add(previous)

    def text_files(self):
        if self._texts is None: self._texts = {**self.base_texts, **self.data['files']}
        return self._texts

    def catalog(self):
        if self._catalog is None:
            self._catalog = []
            for path,text in self.text_files().items():
                for r in content.records(path,text):
                    if r.kind == 'recipe': r.identifier = self.aliases.get((path,r.identifier),r.identifier)
                    self._catalog.append(r)
        return self._catalog

    def _ensure_definition(self, kind, identifier):
        target = {'scope':'definition','kind':kind,'id':identifier}
        path, meta = self._meta(target)
        if path: return meta
        record = self.find(kind,identifier)
        _, all_blocks = definitions.blocks(record.path,self.text_files()[record.path])
        index = next(i for i,(r,_) in enumerate(all_blocks) if r.start == record.start)
        meta = {'kind':kind,'id':record.identifier,'source':record.path,'order':index*1024,'slot':index}
        definitions.write_block(self,meta,all_blocks[index][1])
        return meta

    def map_bytes(self, name):
        if name not in self.data['maps']: raise ValueError('Unknown map: '+name)
        if name not in self._map_cache:
            self._map_cache[name] = scripts.overlay_map(self,name,map_files.assemble(self,name))
        return self._map_cache[name]

    def _sync_scripts(self):
        for path in self._script_writes:
            if path in self.script_files:
                old = self.files.get(path)
                if old is None or scripts.clean(old.decode('utf8')) != self.script_files[path]:
                    self.files[path] = self.script_files[path].encode('utf8')
            else: self.files.pop(path,None)

    def perform(self, operation):
        owned = {m['script']['path'] for _,m,_ in self.targets() if m.get('script') and not m['script'].get('shared')}
        result = self._perform(operation)
        referenced = {m['script']['path'] for _,m,_ in self.targets() if m.get('script')}
        for path in owned-referenced:
            if not any(r['path']==path for text in self.script_files.values() for r in scripts.analyze(text,'')['references']):
                self.script_files.pop(path,None);self._script_writes.add(path)
        self._sync_scripts()
        self._rebuild()
        return result

    def _perform(self, operation):
        if not isinstance(operation,dict) or not isinstance(operation.get('op'),str):
            raise ValueError('Each operation must be an object with a string op')
        op = operation['op']
        if op == 'script.write': return scripts.perform(self,operation)
        if op in ('script.bind','script.extract','script.unbind'):
            target = operation['target'];text = scripts.target_text(self,target)
            _, meta = self._meta(target);old = (meta or {}).get('script',{})
            if op == 'script.unbind':
                ref = self.script_ref(target,text)
            else:
                path = scripts.path_name(operation['path'])
                if op == 'script.extract': scripts.write(self,path,text,None)
                if path not in self.script_files: raise ValueError('Script file missing: '+path)
                ref = {**old,'path':path,'shared':True}
            # Ensure a native extension exists for newly bound entity scripts.
            if target['scope'] != 'definition' and not old:
                values = {k:v for k,v in target.items() if k != 'scope'}
                raw = EditableMap(self.map_bytes(target['map'])).apply({'op':target['scope']+'.script',**values,'text':text or ' '})
                map_files.split(self,target['map'],raw)
            self._set_ref(target,ref)
            return {'target':target,'path':None if op=='script.unbind' else ref['path']}
        if op in ('draft.apply','template.create'):
            return super().perform(operation)
        routed = scripts.route_write(self,operation)
        if routed is not None: return routed
        if op in ('content.set','content.create','content.clone'):
            kind = operation['kind']
            if kind not in content.PREFIXES: raise ValueError('Unknown content kind: '+kind)
            updates = operation.get('properties',{})
            if 'id' in updates or 'combi' in updates: raise ValueError('Structural markers are not editable properties')
            if op == 'content.set':
                record = self.find(kind,operation['id']);meta = self._ensure_definition(kind,record.identifier)
                _, records = definitions.blocks(record.path,self.text_files()[record.path])
                body = next(body for r,body in records if r.start == record.start)
            else:
                identifier = 'recipe-'+uuid.uuid4().hex if kind=='recipe' else number(operation.get('id',self.next_id(kind)),'id',1,self.limits()[kind],True)
                if any(r.kind==kind and str(r.identifier)==str(identifier) for r in self.catalog()): raise ValueError('Duplicate definition ID')
                source = MOD+'sys/'+content.PREFIXES[kind]+'_studio.inf'
                orders = [m['order'] for _,m,_ in definitions.entries(self) if m['source']==source]
                meta = {'kind':kind,'id':identifier,'source':source,'order':max(orders+[-1024])+1024}
                if op == 'content.clone':
                    body = self.find(kind,operation['source']).text
                    body = content.edit(body,{'const':None, **({'id':identifier} if kind!='recipe' else {})})
                else: body = content.new_record(kind,identifier,{})
            definitions.write_block(self,meta,content.edit(body,updates))
            return {'kind':kind,'id':meta['id']}
        if op in ('map.create','map.import'):
            name = operation['name']
            if not isinstance(name,str) or not KEY.fullmatch(name): raise ValueError('Invalid map name')
            if name in self.data['maps']: raise ValueError('Map already exists: '+name)
            raw = new_map(operation.get('size',32)) if op=='map.create' else Path(operation['path']).read_bytes()
            map_files.split(self,name,raw)
            return {'map':name}
        if op.startswith('entity.') or op in ('entities.move','terrain.brush','map.script'):
            name = operation['map'];model = EditableMap(self.map_bytes(name));operation = deepcopy(operation)
            if op == 'entity.remove' and scripts.binding(self,{'scope':'entity','map':name,'kind':operation['kind'],'id':operation['id']}):
                raise ValueError('Unbind the script before deleting this entity')
            if op == 'entity.add':
                record = self.find(operation['kind'],operation['type'])
                values = operation.setdefault('values',{})
                for field in ('health','health_max') if record.kind in ('object','unit') else ('health',) if record.kind=='item' else ():
                    values.setdefault(field,float(record.properties.get('health',['100'])[0]))
                operation.setdefault('id',max([e['id'] for e in model.entities if e['kind']==operation['kind']]+[99 if operation['kind']=='unit' else 0])+1)
            raw = model.apply(operation);map_files.split(self,name,raw)
            return {'map':name,'id':operation.get('id')}
        if op == 'asset.import':
            destination = resource_path(operation['destination'])
            if not any(destination.startswith(MOD+p+'/') for p in ('gfx','models','sfx','music')): raise ValueError('Import assets under gfx, models, sfx or music')
            self.files['resources/'+destination[len(MOD):]] = Path(operation['path']).read_bytes()
            return {'asset':destination}
        if op == 'locale.set':
            result = super().perform(operation)
            code = operation['language'];self.files['locales/'+code+'.tsv'] = catalog_output(self.data['locales'][code]).encode('utf8')
            return result
        raise ValueError('Unknown operation: '+op)

    def project_hash(self):
        return digest(encode({'sources':store.version(self.files),'base':self.base_actual_hash}))

    def history(self):
        return self._state

    def apply(self, operations, expected=None, dry_run=False, expected_hash=None):
        if not isinstance(operations,list) or not 1<=len(operations)<=1000: raise ValueError('Provide 1–1000 operations')
        with locked(self.root):
            store.recover(self.root);self.load();self.check_hash(expected_hash)
            if expected is not None and expected != self.data['revision']: raise ValueError('Revision conflict; reload before editing')
            if self.manifest['base']['contentHash'] != self.base_actual_hash:
                raise ValueError('Base resource version mismatch; reconnect matching resources before editing')
            if self._missing_scripts: raise ValueError(self._missing_scripts[0]['message']+'; restore the source file before editing')
            try:
                results = [self.perform(op) for op in operations]
                issues = self.validate()
                if store.base_hash(self.base) != self.base_actual_hash:
                    raise ValueError('Base resources changed during editing; reload before saving')
                changed = self.files != self._original_files
                if not dry_run: store.commit(self.root,self._original_files,self.files,self._state)
                return {'revision':self.data['revision']+int(changed and not dry_run),'results':results,'diagnostics':issues,'dryRun':dry_run}
            finally:
                if not (self.root/store.JOURNAL).exists(): self.load()

    def undo(self, redo=False, expected=None, expected_hash=None):
        with locked(self.root):
            store.recover(self.root);self.load();self.check_hash(expected_hash)
            if expected is not None and expected != self.data['revision']: raise ValueError('Revision conflict; reload before undo/redo')
            store.history_step(self.root,self.files,self._state,redo);self.load()
            return {'revision':self.data['revision']}

    def validate(self):
        issues = super().validate()+self._missing_scripts
        if self.manifest['base']['contentHash'] != self.base_actual_hash:
            issues.append({'severity':'error','code':'base-version','location':'project.json','message':'공용 리소스 버전이 다릅니다. 프로젝트와 같은 버전의 리소스를 연결하세요.'})
        return issues

    def snapshot(self, map_name=None):
        return {**super().snapshot(map_name),'baseHash':self.manifest['base']['contentHash']}

    def thumbnail_version(self):
        return digest(encode({'base':self.base_actual_hash,'files':self.data['files'],
                             'assets':{p:digest(self.files[p]) for p in self.data['assets'].values()}}))

    def asset_versions(self):
        return {native:digest(self.files[path]) for native,path in self.data['assets'].items()}

    def preview(self, *args, **kwargs):
        result = super().preview(*args,**kwargs)
        if store.scan(self.root) != self._original_files or store.base_hash(self.base) != self.base_actual_hash:
            raise ValueError('Project content changed during preview')
        return result

    def export(self, destination):
        result = super().export(destination)
        if store.scan(self.root) != self._original_files or store.base_hash(self.base) != self.base_actual_hash:
            raise ValueError('Project content changed during export; retry to a new empty directory')
        return result
