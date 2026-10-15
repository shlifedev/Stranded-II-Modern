"""Stranded II Studio CLI. JSON stdout, diagnostics on stderr; no GUI required."""
from pathlib import Path
import argparse
import json
import struct
import subprocess
import sys

from studio_core.project import Project, ROOT


SCHEMA = {
    'protocol': 'stranded-studio/1',
    'projectFormats': ['stranded-studio/1', 'stranded-studio/2', 'stranded-studio/3'],
    'commands': ['init','connect','migrate','migrations','restore-migration','inspect','revision','catalog','thumbnail','commands','scripts','templates','references','assets','apply','flush','validate','export','undo','redo','preview','schema','serve','engine'],
    'thumbnail': {'kind': 'item|object|unit|building|recipe|info', 'contentId': 'definition ID or recipe identifier (CLI: --id)',
                  'result': 'revision, version (content resources only), status (ready|unavailable), additionalResults; ready: PNG dataUrl, source, width, height; unavailable: reason'},
    'operations': {
        'draft.apply': {'key':'caller draft identity', 'target':'map|entity|definition target, or {scope:file,path:scripts/name.s2s}',
                        'section':'properties|values|script|file', 'base':'original field values or inline script text',
                        'changes':'edited fields for properties/values', 'text':'script/file draft text', 'hash':'original file hash for file drafts'},
        'script.write': {'path':'scripts/name.s2s','text':'native script','expectedHash':'hash from scripts command; null for new file'},
        'script.bind': {'path':'scripts/name.s2s','target':{'scope':'map|entity|definition','map':'for map/entity','kind':'for entity/definition','id':'for entity/definition'}},
        'script.extract': {'path':'scripts/new.s2s','target':'same shape as script.bind; preserve existing text'},
        'script.unbind': {'target':'same shape as script.bind; keep current script inline'},
        'template.create': {'template':'item|recipe|building|npc|scenario','name':'display name','slug':'ASCII name for files','source':'definition ID for cloning','npcSource':'unit definition ID for NPC/scenario','map':'map for scenario','ingredients':'recipe/building/scenario: [[item ID, count], ...]','result':'recipe: output item ID; building: output object ID'},
        'content.clone': {'kind': 'item|object|unit|building|recipe|info', 'source': 'existing ID', 'id': 'optional new numeric ID', 'properties': {'name': 'New name'}},
        'content.create': {'kind': 'item|object|unit|building|recipe|info', 'id': 'optional numeric ID', 'properties': {'req': ['24,2','23,1'], 'gen': ['30,1']}},
        'content.set': {'kind': 'content kind', 'id': 'existing ID', 'properties': {'health': '200', 'script': 'on:use {\n msg "Hello";\n}' }},
        'map.create': {'name': 'map-name', 'size': '16|32|64|128|256 (default 32)'},
        'map.import': {'name': 'map-name', 'path': 'path to prepared UTF-8 .s2 map'},
        'entity.add': {'map': 'map-name', 'kind': 'object|unit|item|info', 'type': 'definition ID', 'values': {'x': 0, 'z': 0, 'yaw': 0}},
        'entity.set': {'map': 'map-name', 'kind': 'entity kind', 'id': 'instance ID', 'values': {'x': 64, 'yaw': 90}},
        'entities.move': {'map':'map-name', 'selection':[{'kind':'unit','id':100}], 'delta':{'x':64,'y':0,'z':0}, 'terrain':False},
        'entity.remove': {'map': 'map-name', 'kind': 'entity kind', 'id': 'instance ID'},
        'entity.script': {'map': 'map-name', 'kind': 'entity kind', 'id': 'instance ID', 'text': 'on:use { msg "Hello"; }'},
        'terrain.brush': {'map': 'map-name', 'x': 'terrain sample X', 'z': 'terrain sample Z', 'radius': 2, 'strength': .005, 'mode': 'raise|lower|flatten|smooth'},
        'map.script': {'map': 'map-name', 'text': 'on:start {\n msg "Hello";\n}'},
        'asset.import': {'path': 'local file', 'destination': 'mods/Stranded II/gfx/custom.b3d'},
        'locale.set': {'language': 'en|ko|de', 'key': 'studio.house.name', 'text': 'House'},
    },
    'notes': ['apply accepts one operation or an array; a batch is atomic',
              'Use expectRevision to reject concurrent GUI/agent changes',
              'inspect/revision include projectHash; optional expectProjectHash guards apply, undo, redo and preview even after same-revision Git checkouts',
              'connect writes only local base settings and missing gitignore rules; references takes target; assets lists resource paths',
              'New projects use v3 per-map current-state JSON; existing projects are never automatically converted',
              'serve retains file-based projects in memory; map edits return a snapshot and pendingSave, with durable recovery before acknowledgement; flush waits for authoring files',
              'migrate and restore-migration default to dryRun=true; pass dryRun=false to execute after review; restore-migration requires backup ID',
              'v2 revision is local; projectHash covers authoring files and the connected base content. Base version mismatches block editing/preview/export',
              'Definition IDs and map instance IDs are different',
              'Map object transforms store x, z, yaw; units/items also store y',
              'Terrain x/z are sample coordinates; world = (sample-size/2)*64; normalized height 0.5 is sea level',
              'Properties accept a string/number, a list for repeated properties, or null to remove',
              'Validation diagnostics can be fixed in subsequent edits; export requires no errors',
              'preview --since TOKEN returns a delta from the last acknowledged token; omit since for explicit full/resource load',
              'Preview runs real Blitz code; use no GUI for CLI validation'],
}


def dispatch(request, sessions=None, project=None):
    if not isinstance(request, dict):
        raise ValueError('Request must be a JSON object')
    command = request['command']
    if command == 'flush':
        if sessions: return sessions.flush(request.get('project'))
        if request.get('project'): Project(request['project'])
        return {'pendingSave':False,'saveError':None}
    if sessions and command in ('connect','migrate','restore-migration'):
        sessions.flush(request['project'],forget=True)
    if command == 'schema':
        return SCHEMA
    if command == 'commands':
        from locales import catalog
        base = ROOT/'build/studio/runtime'
        source = base/'core/scriptcommands.inf'
        entries = catalog(base/'core/locales/ko.tsv') if source.is_file() else {}
        from studio_core.project import LOC
        return {'commands': [{'name': parts[0], 'parameters': LOC.sub(lambda m: entries.get(m[1],m[0]), parts[2])}
                for line in source.read_text('utf8').split('\n') if len(parts := line.strip().split('\x1f')) >= 3]} if source.is_file() else {'commands': []}
    if command == 'init':
        project = Project.create(request['project'], request['base'], request.get('name','Extended project'), request.get('formatVersion',3))
        return project.snapshot()
    if command == 'connect':
        from studio_core.workspace import connect
        return connect(request['project'], request['base'])
    if command in ('migrate','migrations','restore-migration'):
        from studio_core import migration
        if command == 'migrations': return migration.backups(request['project'])
        if command == 'migrate': return migration.migrate(request['project'],request.get('dryRun',True),request.get('expectProjectHash'))
        return migration.restore(request['project'],request['backup'],request.get('dryRun',True),request.get('expectProjectHash'))
    if sessions:
        return sessions.run(request,lambda cached:dispatch(request,project=cached),ROOT/'build/studio/runtime')
    project = project if project is not None else Project(request['project'])
    if command in ('references','assets'):
        from studio_core import workspace
        return workspace.references(project, request['target']) if command == 'references' else workspace.assets(project)
    if command == 'templates':
        from studio_core.templates import templates
        return {'templates': templates()}
    if command == 'scripts':
        from studio_core.scripts import workspace, path_name
        result = workspace(project, request.get('query',''))
        if request.get('path'):
            path = path_name(request['path'])
            if path not in project.script_files: raise ValueError('Script file missing: '+path)
            result['file'] = {**next(f for f in result['files'] if f['path']==path),'text':project.script_files[path]}
        return result
    if command == 'revision':
        from studio_core.scripts import version
        return {'revision': project.data['revision'], 'scriptsVersion': version(project.script_files), 'projectHash': project.project_hash()}
    if command == 'inspect':
        return project.snapshot(request.get('map'))
    if command == 'catalog':
        query = request.get('query', '').casefold()
        values = [r for r in project.public_catalog(request.get('language','ko')) if not request.get('kind') or r['kind'] == request['kind']]
        return {'revision': project.data['revision'], 'records': [r for r in values if not query or query in (str(r['id'])+' '+r['name']).casefold()]}
    if command == 'thumbnail':
        from studio_core.thumbnails import thumbnail
        return thumbnail(project, request['kind'], request['contentId'])
    if command == 'apply':
        operations = request['operations']
        return project.apply(operations if isinstance(operations,list) else [operations],
                             request.get('expectRevision'), request.get('dryRun',False), request.get('expectProjectHash'))
    if command == 'validate':
        issues = project.validate()
        return {'valid': not any(i['severity']=='error' for i in issues), 'diagnostics': issues, 'revision': project.data['revision']}
    if command == 'export':
        return project.export(request['out'])
    if command in ('undo','redo'):
        return project.undo(command=='redo', request.get('expectRevision'), request.get('expectProjectHash'))
    if command == 'preview':
        return project.preview(request['map'], ROOT/'build/studio/runtime', request.get('since'),
                               request.get('expectRevision'), request.get('expectScriptsVersion'), request.get('expectProjectHash'))
    raise ValueError(f'Unknown command: {command}')


def response(request, sessions=None):
    try:
        return {'ok': True, 'result': dispatch(request,sessions)}
    except (ValueError, OSError, KeyError, TypeError, OverflowError, struct.error) as exc:
        return {'ok': False, 'error': {'type': type(exc).__name__, 'message': str(exc),
                                     **({'details':exc.details} if hasattr(exc,'details') else {})}}


def serve():
    # One newline-delimited JSON request / response, including caller-provided ID.
    from studio_core.session import Sessions
    sessions = Sessions()
    try:
        for line in sys.stdin:
            request = {}
            try:
                request = json.loads(line)
                result = response(request,sessions)
            except (ValueError, TypeError) as exc:
                result = {'ok': False, 'error': {'type':'ProtocolError', 'message':str(exc)}}
            result['id'] = request.get('id') if isinstance(request,dict) else None
            print(json.dumps(result,ensure_ascii=False,allow_nan=False),flush=True)
    finally:
        sessions.flush()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='command',required=True)
    sub.add_parser('schema',help='Print the machine-readable editing command schema')
    sub.add_parser('commands',help='List scripting completions from the prepared game')
    sub.add_parser('serve',help='Serve newline-delimited JSON on stdin/stdout (used by Electron)')
    flush = sub.add_parser('flush',help='Finish recoverable pending source saves')
    flush.add_argument('project',type=Path)
    init = sub.add_parser('init',help='Create a project outside the prepared base runtime')
    init.add_argument('project',type=Path)
    init.add_argument('--base',required=True,type=Path)
    init.add_argument('--name',default='Extended project')
    init.add_argument('--format-version',type=int,choices=(1,2,3),default=3)
    for name in ('connect','migrate','migrations','restore-migration','inspect','revision','catalog','thumbnail','scripts','templates','references','assets','apply','validate','export','undo','redo','preview'):
        p = sub.add_parser(name)
        p.add_argument('project',type=Path)
        if name in ('migrate','restore-migration'):
            group=p.add_mutually_exclusive_group()
            group.add_argument('--apply',action='store_true',dest='execute',help='Execute the reviewed migration or restoration')
            group.add_argument('--dry-run',action='store_false',dest='execute',help='Preview without changing files (default)')
            p.set_defaults(execute=False)
            if name == 'restore-migration':p.add_argument('--backup',required=True)
        if name == 'connect':
            p.add_argument('--base',required=True,type=Path)
        if name == 'references':
            p.add_argument('--target',required=True,type=json.loads,help='JSON target: scope and map/kind/id or file path')
        if name == 'scripts':
            p.add_argument('--path')
            p.add_argument('--query', default='')
        if name == 'preview':
            p.add_argument('--since', help='Last token acknowledged by the viewport; omit for explicit full load')
        if name in ('inspect','preview'):
            p.add_argument('--map',required=name=='preview')
        if name == 'catalog':
            p.add_argument('--kind',choices=('item','object','unit','building','recipe','info'))
            p.add_argument('--query',default='')
            p.add_argument('--language',default='ko')
        if name == 'thumbnail':
            p.add_argument('--kind',required=True,choices=('item','object','unit','building','recipe','info'))
            p.add_argument('--id',dest='contentId',required=True)
        if name == 'apply':
            p.add_argument('--file',default='-',help='JSON operation(s); - reads stdin')
            p.add_argument('--dry-run',action='store_true')
        if name in ('apply','undo','redo'):
            p.add_argument('--expect-revision',type=int)
        if name in ('apply','undo','redo','preview','migrate','restore-migration'):
            p.add_argument('--expect-project-hash')
        if name == 'export':
            p.add_argument('--out',required=True,type=Path)
    engine = sub.add_parser('engine',help='Prepare/build the isolated Blitz Studio renderer')
    engine.add_argument('action',choices=('setup','build','check'))
    engine.add_argument('--game',type=Path)
    engine.add_argument('--font',type=Path)
    args = parser.parse_args()
    if args.command == 'serve':
        serve(); return
    if args.command == 'engine':
        from studio_core import engine
        try:
            getattr(engine,args.action)(args)
        except (ValueError,OSError,subprocess.CalledProcessError) as exc:
            parser.exit(1,f'Error: {exc}\n')
        return
    request = {key:str(value) if isinstance(value,Path) else value for key,value in vars(args).items()}
    if 'format_version' in request: request['formatVersion'] = request.pop('format_version')
    if 'execute' in request: request['dryRun'] = not request.pop('execute')
    if 'expect_revision' in request:
        request['expectRevision'] = request.pop('expect_revision')
    if 'expect_project_hash' in request:
        request['expectProjectHash'] = request.pop('expect_project_hash')
    if args.command == 'apply':
        try:
            request['operations'] = json.loads(sys.stdin.read() if args.file=='-' else Path(args.file).read_text('utf8'))
        except (ValueError,OSError) as exc:
            parser.exit(1,f'Invalid operation JSON: {exc}\n')
        request['dryRun'] = request.pop('dry_run')
    result = response(request)
    print(json.dumps(result,ensure_ascii=False,indent=2,allow_nan=False))
    if not result['ok'] or (args.command=='validate' and not result['result']['valid']):
        raise SystemExit(1)


if __name__ == '__main__':
    main()
