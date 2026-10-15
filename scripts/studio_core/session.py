"""Desktop-only live projects with durable recovery and coalesced source saves.

CLI commands stay synchronous. Acknowledged edits already have a recovery
journal; only publishing the authoring files waits for the autosave timer.
"""
from pathlib import Path
from threading import RLock, Timer

from . import file_store as store
from .file_project import FileProject
from .maps import EditableMap
from .project import Project, locked

FAST_OPS = {'entity.add','entity.set','entity.remove','entities.move','terrain.brush'}


class Session:
    def __init__(self, project, delay=.35):
        self.project = project
        self.root = project.root
        self.delay = delay
        self.lock = RLock()
        self.timer = None
        self.pending = False
        self.error = None
        self.baseline = dict(project.files)
        self.local = self.local_bytes()
        self.cached = None
        self.summaries = {}

    def local_bytes(self):
        path = self.root/'.studio/local.json'
        return path.read_bytes() if path.exists() else None

    def status(self):
        return {'pendingSave':self.pending,'saveError':self.error}

    def refresh(self, check_base=False):
        if not self.pending:
            store.recover(self.root)
        current = store.scan(self.root)
        local = self.local_bytes()
        if current != self.baseline or local != self.local:
            if self.pending and current != self.project.files and (self.root/store.JOURNAL).exists():
                raise ValueError('External edit conflicts with pending save; recovery journal preserved')
            if current != self.project.files or local != self.local:
                self.project.load()
                self.cached = None
                self.summaries.clear()
            self.baseline = dict(self.project.files)
            self.local = local
            self.pending = False
        if check_base:
            actual = store.base_hash(self.project.base)
            if actual != self.project.base_actual_hash:
                self.project.base_actual_hash = actual
                self.cached = None

    def flush(self):
        with self.lock:
            if self.timer:
                self.timer.cancel();self.timer = None
            if not self.pending and not (self.root/store.JOURNAL).exists():
                return self.status()
            try:
                with locked(self.root):
                    store.recover(self.root)
                    self.refresh()
                    self.baseline = dict(self.project.files)
                    self.pending = False
                    self.error = None
            except (ValueError,OSError) as exc:
                self.error = str(exc)
                raise
            return self.status()

    def autosave(self):
        try: self.flush()
        except (ValueError,OSError): pass  # Retained in status; next open can recover.

    def schedule(self):
        if self.timer is None:
            self.timer = Timer(self.delay,self.autosave)
            self.timer.daemon = True
            self.timer.start()

    def snapshot(self, map_name=None):
        p = self.project
        if self.cached is None:
            self.cached = p.snapshot()
        if map_name and map_name not in p.data['maps']:
            map_name = next(iter(p.data['maps']),None)
        if map_name and map_name not in self.summaries:
            self.summaries[map_name] = EditableMap(p.map_bytes(map_name)).summary()
        return {**self.cached,'revision':p.data['revision'],'projectHash':p.project_hash(),
                'canUndo':bool(p._state['undo']),'canRedo':bool(p._state['redo']),
                'activeMap':map_name,'map':self.summaries.get(map_name),**self.status()}

    def apply(self, request, operations):
        p = self.project
        p.check_hash(request.get('expectProjectHash'))
        if request.get('expectRevision',p.data['revision']) != p.data['revision']:
            raise ValueError('Revision conflict; reload before editing')
        if p.manifest['base']['contentHash'] != p.base_actual_hash:
            raise ValueError('Base resource version mismatch; reconnect matching resources before editing')
        if p._missing_scripts:
            raise ValueError('Restore missing script sources before editing')
        self.snapshot()  # Cache unchanged catalog, assets and resource diagnostics once.
        before = dict(p.files)
        old_scripts, old_writes = dict(p.script_files),set(p._script_writes)
        try:
            results = [p.perform(op) for op in operations]
            changed = p.files != before
            if changed:
                state = store.prepare(self.root,before,p.files,p._state,baseline=self.baseline)
                p._state = state
                p.data['revision'] = state['revision']
                p._original_files = dict(p.files)
                self.pending = True
                self.error = None
                self.schedule()
        except Exception:
            p.files = before;p.script_files = old_scripts;p._script_writes = old_writes;p._rebuild()
            raise
        # Local map constraints are checked by EditableMap.apply. Resource-wide
        # validation is retained for open, explicit validate, play and export.
        for name in {op['map'] for op in operations}:
            summary = EditableMap(p.map_bytes(name)).summary()
            self.summaries[name] = summary
            issues = [d for d in self.cached['diagnostics'] if not (d['location']==name and d['code']=='player-start')]
            if not any(e['kind']=='info' and e['type']==1 for e in summary['entities']):
                issues.append({'severity':'error','code':'player-start','message':'A playable map needs a player start (info type 1)',
                               'location':name,'target':{'scope':'map','map':name}})
            self.cached = {**self.cached,'diagnostics':issues}
        return {'revision':p.data['revision'],'results':results,'diagnostics':self.cached['diagnostics'],
                'dryRun':False,'snapshot':self.snapshot(request.get('map')),**self.status()}

    def run(self, request, normal, engine_base=None):
        command = request['command']
        with self.lock:
            if command == 'flush': return self.flush()
            operations = request.get('operations',[])
            if isinstance(operations,dict): operations = [operations]
            fast = (command=='apply' and isinstance(operations,list) and 1<=len(operations)<=1000
                    and not request.get('dryRun') and all(isinstance(op,dict) and op.get('op') in FAST_OPS for op in operations))
            strict = command in ('validate','export') or (command=='preview' and (not request.get('since') or any(request.get(k) is not None for k in ('expectProjectHash','expectRevision','expectScriptsVersion'))))
            mutation = command in ('apply','undo','redo') and not fast
            if strict or mutation:
                self.flush()
                self.project.load()
                self.cached = None;self.summaries.clear()
            with locked(self.root):
                self.refresh(check_base=command=='revision' or strict)
                if fast: return self.apply(request,operations)
                if command == 'inspect': return self.snapshot(request.get('map'))
                if command == 'preview' and not strict:
                    from .preview import preview
                    if engine_base is not None and self.project.base != engine_base.resolve():
                        raise ValueError('Blitz preview requires the project base to match the bundled Studio resources')
                    if any(d['severity']=='error' for d in self.snapshot()['diagnostics']):
                        raise ValueError('Preview blocked by validation errors')
                    return preview(self.project,request['map'],request.get('since'))
            # Normal mutators acquire their own project lock.
            result = normal(self.project)
            if mutation:
                self.baseline = dict(self.project.files)
                self.cached = None;self.summaries.clear()
            if command == 'revision': result.update(self.status())
            return result


class Sessions:
    def __init__(self, delay=.35):
        self.items = {}
        self.delay = delay

    def run(self, request, normal, engine_base=None):
        root = str(Path(request['project']).resolve())
        session = self.items.get(root)
        if session and store.read_json((Path(root)/'project.json').read_bytes(),'project.json').get('format') not in store.FORMATS:
            with session.lock:
                if session.timer: session.timer.cancel()
                del self.items[root]
                session = None
        if session is None:
            project = Project(root)
            if not isinstance(project,FileProject): return normal(project)
            session = self.items[root] = Session(project,self.delay)
        return session.run(request,normal,engine_base)

    def flush(self, directory=None, forget=False):
        roots = [str(Path(directory).resolve())] if directory else list(self.items)
        for root in roots:
            if root in self.items:
                self.items[root].flush()
                if forget: del self.items[root]
        return {'pendingSave':False,'saveError':None}
