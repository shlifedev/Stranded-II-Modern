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
    'commands': ['init','inspect','revision','catalog','commands','apply','validate','export','undo','redo','preview','schema','serve','engine'],
    'operations': {
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
              'Definition IDs and map instance IDs are different',
              'Map object transforms store x, z, yaw; units/items also store y',
              'Terrain x/z are sample coordinates; world = (sample-size/2)*64; normalized height 0.5 is sea level',
              'Properties accept a string/number, a list for repeated properties, or null to remove',
              'Validation diagnostics can be fixed in subsequent edits; export requires no errors',
              'preview --since TOKEN returns a delta from the last acknowledged token; omit since for explicit full/resource load',
              'Preview runs real Blitz code; use no GUI for CLI validation'],
}


def dispatch(request):
    if not isinstance(request, dict):
        raise ValueError('Request must be a JSON object')
    command = request['command']
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
        project = Project.create(request['project'], request['base'], request.get('name','Extended project'))
        return project.snapshot()
    project = Project(request['project'])
    if command == 'revision':
        return {'revision': project.data['revision']}
    if command == 'inspect':
        return project.snapshot(request.get('map'))
    if command == 'catalog':
        query = request.get('query', '').casefold()
        values = [r for r in project.public_catalog(request.get('language','ko')) if not request.get('kind') or r['kind'] == request['kind']]
        return {'revision': project.data['revision'], 'records': [r for r in values if not query or query in (str(r['id'])+' '+r['name']).casefold()]}
    if command == 'apply':
        operations = request['operations']
        return project.apply(operations if isinstance(operations,list) else [operations],
                             request.get('expectRevision'), request.get('dryRun',False))
    if command == 'validate':
        issues = project.validate()
        return {'valid': not any(i['severity']=='error' for i in issues), 'diagnostics': issues, 'revision': project.data['revision']}
    if command == 'export':
        return project.export(request['out'])
    if command in ('undo','redo'):
        return project.undo(command=='redo', request.get('expectRevision'))
    if command == 'preview':
        return project.preview(request['map'], ROOT/'build/studio/runtime', request.get('since'))
    raise ValueError(f'Unknown command: {command}')


def response(request):
    try:
        return {'ok': True, 'result': dispatch(request)}
    except (ValueError, OSError, KeyError, TypeError, OverflowError, struct.error) as exc:
        return {'ok': False, 'error': {'type': type(exc).__name__, 'message': str(exc)}}


def serve():
    # One newline-delimited JSON request / response, including caller-provided ID.
    for line in sys.stdin:
        request = {}
        try:
            request = json.loads(line)
            result = response(request)
        except (ValueError, TypeError) as exc:
            result = {'ok': False, 'error': {'type':'ProtocolError', 'message':str(exc)}}
        result['id'] = request.get('id') if isinstance(request,dict) else None
        print(json.dumps(result,ensure_ascii=False,allow_nan=False),flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='command',required=True)
    sub.add_parser('schema',help='Print the machine-readable editing command schema')
    sub.add_parser('commands',help='List scripting completions from the prepared game')
    sub.add_parser('serve',help='Serve newline-delimited JSON on stdin/stdout (used by Electron)')
    init = sub.add_parser('init',help='Create a project outside the prepared base runtime')
    init.add_argument('project',type=Path)
    init.add_argument('--base',required=True,type=Path)
    init.add_argument('--name',default='Extended project')
    for name in ('inspect','revision','catalog','apply','validate','export','undo','redo','preview'):
        p = sub.add_parser(name)
        p.add_argument('project',type=Path)
        if name == 'preview':
            p.add_argument('--since', help='Last token acknowledged by the viewport; omit for explicit full load')
        if name in ('inspect','preview'):
            p.add_argument('--map',required=name=='preview')
        if name == 'catalog':
            p.add_argument('--kind',choices=('item','object','unit','building','recipe','info'))
            p.add_argument('--query',default='')
            p.add_argument('--language',default='ko')
        if name == 'apply':
            p.add_argument('--file',default='-',help='JSON operation(s); - reads stdin')
            p.add_argument('--dry-run',action='store_true')
        if name in ('apply','undo','redo'):
            p.add_argument('--expect-revision',type=int)
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
    if 'expect_revision' in request:
        request['expectRevision'] = request.pop('expect_revision')
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
