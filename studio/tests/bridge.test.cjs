'use strict';
const {test}=require('node:test');
const assert=require('node:assert/strict');
const vm=require('node:vm');
const fs=require('node:fs');
const path=require('node:path');

function viewport(){
  const listeners={},canvasListeners={},overlayListeners={},messages=[],writes=[];
  const origin='http://127.0.0.1:12345';
  const overlay={innerHTML:'',addEventListener:(name,fn)=>overlayListeners[name]=fn,setPointerCapture(){},hasPointerCapture:()=>false};
  const canvas={focus(){},setPointerCapture(){},width:1280,height:800,style:{},addEventListener:(name,fn)=>canvasListeners[name]=fn,
    getBoundingClientRect:()=>({left:20,top:30,width:640,height:400})};
  const document={hidden:false,activeElement:null,pointerLockElement:null,
    exitPointerLock(){this.pointerLockElement=null;},getElementById:id=>id==='canvas'?canvas:id==='gizmo'?overlay:{style:{}}};
  const parent={postMessage:value=>messages.push(value)};
  const context=vm.createContext({document,parent,location:{origin,reload(){throw new Error('Unexpected automatic reload');}},window:{addEventListener:(name,fn)=>listeners[name]=fn},
    console,performance,Uint8Array,atob,innerWidth:640,innerHeight:400,navigator:{},WebAssembly});
  vm.runInContext(fs.readFileSync(path.join(__dirname,'../engine/gizmo.js'),'utf8'),context);
  vm.runInContext(fs.readFileSync(path.join(__dirname,'../engine/studio.js'),'utf8'),context);
  context.Module.FS={mkdirTree(){},writeFile:(name,data)=>writes.push({name,data}),cwd:()=>'/game'};
  const send=(value,sender=parent,from=origin)=>listeners.message({source:sender,origin:from,data:{source:'studio-ui',...value}});
  return {context,document,canvas,overlay,messages,writes,send,canvasListeners,overlayListeners,listeners,bridge:context.Module.s2Bridge};
}

test('runtime records preserve Unicode, source separators and read-only variables',async()=>{
  const h=viewport();
  const record={kind:'error',message:'한글 "error"',script:'on:start {\x1fbad;\x1f}',row:2};
  await h.bridge('studio-event\x1fdebug\x1f'+JSON.stringify(record));
  assert.equal(JSON.stringify(h.messages.at(-1).record),JSON.stringify(record));
  await h.bridge('studio-event\x1fdebug\x1f'+JSON.stringify({kind:'variables',values:[['global','test','1']]}));
  assert.equal(h.messages.at(-1).record.values[0][1],'test');
  assert.equal(h.writes.length,0);
});

test('play reuses the loaded engine, fetches the latest saved map and starts only once',async()=>{
  const h=viewport();ready(h);const engine=h.context.Module;
  h.send({type:'play',id:1});h.send({type:'play',id:1});
  assert.equal(await h.bridge('studio-poll'),'');
  const request=h.messages.find(m=>m.type==='request-preview');
  assert.equal(request.since,'a');assert.equal(request.playId,1);
  assert.equal(h.messages.filter(m=>m.type==='request-preview').length,1);
  assert.equal(h.writes.length,0);
  h.send({type:'project',playId:1,preview:delta()});
  assert.equal(await h.bridge('studio-poll'),'play');
  assert.equal(Buffer.from(h.writes[0].data).toString(),'native-map');
  assert.equal(h.writes.length,1);assert.equal(h.context.Module,engine);
  assert.equal(await h.bridge('studio-poll'),'');
  await h.bridge('studio-event\x1floaded\x1f1\x1f32');
  assert.equal(await h.bridge('focus'),1); // first frame before pointer capture
  await h.bridge('studio-event\x1fplaying');
  assert.equal(h.messages.at(-1).state,'playing');assert.ok(h.messages.at(-1).startMs>=0);
  assert.equal(await h.bridge('focus'),0); // Esc/unlocked pauses normally
  h.document.pointerLockElement=h.canvas;
  assert.equal(await h.bridge('focus'),1);
});

test('play waits for an outstanding scene ACK and uses its new baseline',async()=>{
  const h=viewport();ready(h);h.send({type:'refresh'});h.send({type:'play',id:1});
  assert.equal(await h.bridge('studio-poll'),'');
  h.send({type:'project',preview:delta()});
  assert.equal(await h.bridge('studio-poll'),'patch\x1fb');
  assert.equal(await h.bridge('studio-poll'),'');
  assert.equal(h.messages.filter(m=>m.playId===1).length,0);
  await h.bridge('studio-event\x1fapplied\x1fb');
  await h.bridge('studio-poll');
  const request=h.messages.find(m=>m.playId===1);
  assert.equal(request.since,'b');
  h.send({type:'project',playId:1,preview:delta('c','b')});
  assert.equal(await h.bridge('studio-poll'),'play');
});

test('canceled starts ignore late snapshots and allow a fresh request',async()=>{
  const h=viewport();ready(h);h.send({type:'play',id:1});await h.bridge('studio-poll');
  h.send({type:'stop',id:2});
  h.send({type:'project',playId:1,preview:delta()});
  assert.equal(await h.bridge('studio-poll'),'');assert.equal(h.writes.length,0);
  assert.equal(h.messages.at(-1).state,'idle');
  h.send({type:'play',id:3});await h.bridge('studio-poll');
  h.send({type:'project',playId:1,preview:delta()});
  assert.equal(await h.bridge('studio-poll'),'');
  h.send({type:'project',playId:3,preview:delta()});
  assert.equal(await h.bridge('studio-poll'),'play');
});

test('changed resources and stale play baselines leave the editor intact',async()=>{
  for(const next of [{...delta(),resourcesPending:true},{...delta(),resourcesRequired:true},{...delta(),baseToken:'old'}]){
    const h=viewport();ready(h);h.send({type:'play',id:1});await h.bridge('studio-poll');
    h.send({type:'project',playId:1,preview:next});
    assert.equal(h.messages.at(-1).state,'idle');assert.ok(h.messages.at(-1).message);
    assert.equal(await h.bridge('studio-poll'),'');assert.equal(h.writes.length,0);
    assert.equal(vm.runInContext('syncFailed',h.context),false);
  }
});

test('failed play validation can be retried without restarting the engine',async()=>{
  const h=viewport();ready(h);h.send({type:'play',id:1});await h.bridge('studio-poll');
  h.send({type:'play-error',id:1,message:'Invalid saved map'});
  assert.equal(h.messages.at(-1).state,'idle');assert.equal(h.messages.at(-1).message,'Invalid saved map');
  h.send({type:'play',id:2});await h.bridge('studio-poll');
  h.send({type:'play-error',id:1,message:'Old failure'});
  h.send({type:'project',playId:2,preview:delta()});
  assert.equal(await h.bridge('studio-poll'),'play');
});

test('stop releases paused focus and restores the authoring map, camera and selection',async()=>{
  const h=viewport();ready(h);select(h);await h.bridge('studio-poll');
  h.send({type:'play',id:1});await h.bridge('studio-poll');
  h.send({type:'project',playId:1,preview:delta()});assert.equal(await h.bridge('studio-poll'),'play');
  await h.bridge('studio-event\x1fcamera\x1f25\x1f45\x1f1\x1f2\x1f3');
  await h.bridge('studio-event\x1floaded\x1f1\x1f32');await h.bridge('studio-event\x1fplaying');
  h.context.Module.FS.writeFile('/game/mods/Stranded II/maps/studio.s2','gameplay-changes');
  assert.equal(await h.bridge('focus'),0);
  h.send({type:'stop',id:2});assert.equal(await h.bridge('focus'),1);
  assert.equal(h.writes.at(-1).data,'gameplay-changes'); // restore only at frame boundary
  assert.equal(await h.bridge('studio-poll'),'stop');
  assert.equal(Buffer.from(h.writes.at(-1).data).toString(),'native-map');
  await h.bridge('studio-event\x1floaded\x1f0\x1f32');
  assert.equal(await h.bridge('studio-poll'),'restore-pose\x1f1\x1f2\x1f3\x1f25\x1f45');
  assert.equal(vm.runInContext('selection.id',h.context),100);
  assert.equal(h.messages.findLast(m=>m.type==='play-state').state,'idle');
  await h.bridge('studio-poll');h.send({type:'play',id:3});await h.bridge('studio-poll');
  assert.equal(h.messages.at(-1).since,'b');
});

test('stop during native loading waits for the editor and ignores the stale first frame',async()=>{
  const h=viewport();ready(h);h.send({type:'play',id:1});await h.bridge('studio-poll');
  h.send({type:'project',playId:1,preview:delta()});assert.equal(await h.bridge('studio-poll'),'play');
  h.send({type:'stop',id:2});
  await h.bridge('studio-event\x1floaded\x1f1\x1f32');await h.bridge('studio-event\x1fplaying');
  assert.equal(h.messages.filter(m=>m.state==='playing').length,0);
  assert.equal(await h.bridge('focus'),1);assert.equal(await h.bridge('studio-poll'),'stop');
  await h.bridge('studio-event\x1floaded\x1f0\x1f32');
  assert.equal(h.messages.findLast(m=>m.type==='play-state').state,'idle');
});

test('canvas picks account for scale and drop retains content identity',async()=>{
  const host=viewport();ready(host);
  vm.runInContext("initial.available.push('object/22')",host.context);
  host.canvasListeners.drop({clientX:340,clientY:230,shiftKey:false,preventDefault(){},
    dataTransfer:{getData:()=>JSON.stringify({kind:'object',id:22})}});
  assert.equal(await host.bridge('studio-poll\x1f'),'pick\x1f640\x1f400\x1f1\x1f1\x1f1\x1f22\x1f0');
  await host.bridge('studio-event\x1fpick\x1f1\x1f0\x1f0\x1f12\x1f160\x1f24\x1f1');
  const result=host.messages.at(-1);
  assert.equal(result.content.id,22);assert.equal(result.purpose,'place');assert.equal(result.hit,true);
  assert.equal(result.placementSample,true);
});

test('placement hover samples the latest scaled pointer without flooding the native queue',async()=>{
  const host=viewport();ready(host);
  host.send({type:'tool',tool:'place',placement:{kind:'object',id:1},snap:16,entities:[]});
  assert.equal(await host.bridge('studio-poll'),'select\x1f0\x1f0');
  for(let i=0;i<100;i++)host.canvasListeners.pointermove({clientX:241+i,clientY:230});
  assert.equal(await host.bridge('studio-hover'),'1\x1f1\x1f640\x1f400\x1f16');
  assert.equal(await host.bridge('studio-poll'),'','hover does not queue pick commands');
  assert.equal(await host.bridge('studio-hover'),'1\x1f1\x1f640\x1f400\x1f16','stationary pointer follows camera every frame');
  host.send({type:'tool',tool:'place',placement:{kind:'unit',id:1},snap:32,entities:[]});
  assert.equal(await host.bridge('studio-hover'),'2\x1f1\x1f640\x1f400\x1f32');
});

test('placement click captures its model and snap before the selected content changes',async()=>{
  const host=viewport();ready(host);
  host.send({type:'tool',tool:'place',placement:{kind:'unit',id:1},snap:16,entities:[]});
  await host.bridge('studio-poll');
  host.canvasListeners.pointerdown(pointer());
  host.canvasListeners.pointerup({...pointer(),shiftKey:false});
  host.send({type:'tool',tool:'place',placement:{kind:'object',id:1},snap:32,entities:[]});
  assert.equal(await host.bridge('studio-poll'),'pick\x1f640\x1f400\x1f1\x1f1\x1f2\x1f1\x1f16');
  await host.bridge('studio-event\x1fpick\x1f1\x1f0\x1f0\x1f16\x1f170\x1f32\x1f1');
  const result=host.messages.at(-1);
  assert.equal(result.content.kind,'unit');assert.equal(result.placementSample,true);
  assert.equal(result.x,16);assert.equal(result.y,170);assert.equal(result.z,32);
});

test('placement preview clears on leaving, camera rotation, Escape, blur and unavailable content',async()=>{
  const host=viewport();ready(host);
  const choose=()=>host.send({type:'tool',tool:'place',placement:{kind:'object',id:1},snap:0,entities:[]});
  const move=()=>host.canvasListeners.pointermove(pointer());
  choose();move();assert.notEqual(await host.bridge('studio-hover'),'');
  host.canvasListeners.pointerleave();assert.equal(await host.bridge('studio-hover'),'');
  move();host.listeners.blur();assert.equal(await host.bridge('studio-hover'),'');
  move();host.canvasListeners.pointerdown({...pointer(),button:2});assert.equal(await host.bridge('studio-hover'),'');
  host.canvasListeners.pointerup({...pointer(),button:2});assert.notEqual(await host.bridge('studio-hover'),'');
  host.listeners.keydown({code:'Escape',preventDefault(){}});
  assert.equal(await host.bridge('studio-hover'),'');assert.equal(host.messages.at(-1).type,'cancel-placement');
  choose();move();host.document.hidden=true;assert.equal(await host.bridge('studio-hover'),'');host.document.hidden=false;
  host.send({type:'tool',tool:'place',placement:{kind:'object',id:999},entities:[]});
  assert.equal(await host.bridge('studio-hover'),'');
  choose();host.send({type:'play',id:1});assert.equal(await host.bridge('studio-hover'),'');
});

test('building drop uses its resolved model and rejects unapplied definitions',async()=>{
  const host=viewport();ready(host);
  const drop=placement=>host.canvasListeners.drop({...pointer(),dataTransfer:{getData:()=>JSON.stringify({kind:'building',id:99,placement})}});
  drop({kind:'unit',id:1});
  assert.equal(await host.bridge('studio-poll'),'pick\x1f640\x1f400\x1f1\x1f1\x1f2\x1f1\x1f0');
  drop({kind:'unit',id:999});assert.equal(await host.bridge('studio-poll'),'');
});

test('messages from another window or origin do not reach Blitz',async()=>{
  const host=viewport();host.send({type:'play'},{},'https://outside.test');
  assert.equal(await host.bridge('studio-poll\x1f'),'');
});

function ready(host){
  vm.runInContext("initial={files:{},token:'a',available:['info/1','unit/1','object/1']};starting=true;loaded=true;mapSize=32;acknowledged='a';",host.context);
}
function delta(token='b',baseToken='a'){
  const patch=Buffer.alloc(16);patch.writeInt32LE(1);
  return {mode:'delta',token,baseToken,revision:2,available:['info/1','unit/1','object/1'],resourcesPending:false,resourcesRequired:false,
    files:{'mods/Stranded II/maps/studio.s2':btoa('native-map')},patch:patch.toString('base64')};
}
function select(host,kind='unit'){
  host.send({type:'tool',tool:'move',selection:{kind,id:100,class:kind==='object'?1:2},
    entities:[{kind,id:100,class:kind==='object'?1:2,x:0,y:160,z:0}],revision:5,snap:16});
}
function pointer(axis='x',x=340,y=230){return {target:{dataset:{axis}},pointerId:1,button:0,clientX:x,clientY:y,preventDefault(){}};}
async function projectGizmo(host){await host.bridge('studio-event\x1fgizmo\x1f640\x1f400\x1f100\x1f768\x1f400\x1f100\x1f640\x1f272\x1f100\x1f704\x1f464\x1f164');}

test('scene delta stages at a frame boundary and advances baseline only after ACK',async()=>{
  const host=viewport();ready(host);
  host.send({type:'refresh'});
  assert.equal(host.messages.at(-1).since,'a');
  host.send({type:'project',preview:delta()});
  assert.equal(host.writes.length,0);
  assert.equal(await host.bridge('studio-poll\x1f'),'patch\x1fb');
  assert.equal(host.writes.length,2);
  assert.equal(Buffer.from(host.writes[0].data).toString(),'native-map');
  host.send({type:'refresh'});
  assert.equal(host.messages.filter(m=>m.type==='request-preview').length,1);
  await host.bridge('studio-event\x1fapplied\x1fb');
  assert.equal(host.messages.at(-1).since,'b');
  assert.equal(host.messages.filter(m=>m.type==='request-preview').length,2);
});

test('invalid packets and stale baselines never touch the live filesystem or reload',async()=>{
  for(const bad of [{...delta(),baseToken:'stale'},{...delta(),patch:btoa('truncated')}]){
    const host=viewport();ready(host);host.send({type:'project',preview:bad});
    assert.equal(await host.bridge('studio-poll\x1f'),'');
    assert.equal(host.writes.length,0);assert.equal(host.messages.at(-1).type,'error');
  }
});

test('resource edits stay pending and unsupported new definitions wait for explicit apply',async()=>{
  const host=viewport();ready(host);
  host.send({type:'project',preview:{...delta(),resourcesPending:true}});
  assert.equal(host.messages.at(-1).pending,true);
  assert.equal(await host.bridge('studio-poll\x1f'),'patch\x1fb');
  await host.bridge('studio-event\x1fapplied\x1fb');
  host.send({type:'project',preview:{...delta('c','b'),resourcesPending:true,resourcesRequired:true}});
  assert.equal(await host.bridge('studio-poll\x1f'),'');
  assert.equal(host.messages.at(-1).required,true);
  assert.equal(host.writes.length,2);
});

test('axis drag previews one group delta, commits once, queues external edits and keeps start revision',async()=>{
  const host=viewport();ready(host);select(host);await projectGizmo(host);
  assert.equal(await host.bridge('studio-poll\x1f'),'select\x1f2\x1f100');
  host.overlayListeners.pointerdown(pointer());
  host.overlayListeners.pointermove(pointer('x',360));
  host.overlayListeners.pointermove(pointer('x',375));
  host.send({type:'refresh'});
  // An inspect response from a CLI writer must not cancel the current gesture.
  host.send({type:'tool',tool:'move',selection:{kind:'unit',id:100,class:2},entities:[{kind:'unit',id:100,class:2,x:900,y:160,z:0}],revision:6,snap:16});
  assert.equal(host.messages.filter(m=>m.type==='request-preview').length,0);
  assert.equal(await host.bridge('studio-poll\x1f'),'drag-begin\x1f1\x1f2\x1f100');
  assert.equal(await host.bridge('studio-poll\x1f'),'select\x1f2\x1f100');
  assert.equal(await host.bridge('studio-poll\x1f'),'drag-move\x1f32\x1f0\x1f0\x1f0');
  host.overlayListeners.pointerup(pointer('x',375));
  host.overlayListeners.pointerup(pointer('x',375));
  const commits=host.messages.filter(m=>m.type==='drag-commit');
  assert.equal(commits.length,1);assert.equal(commits[0].revision,5);assert.equal(commits[0].delta.x,32);
  host.send({type:'drag-result',id:commits[0].id,ok:false});
  assert.equal(await host.bridge('studio-poll\x1f'),'drag-cancel');
  assert.equal(host.messages.at(-1).since,'a');
});

test('Escape and lost capture restore transient movement without a commit',async()=>{
  for(const cancel of [host=>host.listeners.keydown({code:'Escape',preventDefault(){}}),host=>host.overlayListeners.lostpointercapture()]){
    const host=viewport();ready(host);select(host);await projectGizmo(host);await host.bridge('studio-poll\x1f');
    host.overlayListeners.pointerdown(pointer());host.overlayListeners.pointermove(pointer('x',375));
    cancel(host);
    assert.equal(host.messages.filter(m=>m.type==='drag-commit').length,0);
    assert.equal(await host.bridge('studio-poll\x1f'),'drag-begin\x1f1\x1f2\x1f100');
    assert.equal(await host.bridge('studio-poll\x1f'),'drag-cancel');
    assert.equal(await host.bridge('studio-poll\x1f'),'');
  }
});

test('ground handle waits for final terrain pick, snaps and emits one transaction',async()=>{
  const host=viewport();ready(host);select(host);await projectGizmo(host);await host.bridge('studio-poll\x1f');
  host.overlayListeners.pointerdown(pointer('ground'));
  assert.equal(await host.bridge('studio-poll\x1f'),'drag-begin\x1f1\x1f2\x1f100');
  assert.match(await host.bridge('studio-poll\x1f'),/^pick/);
  await host.bridge('studio-event\x1fpick\x1f1\x1f0\x1f0\x1f0\x1f160\x1f0\x1f1');
  host.overlayListeners.pointerup(pointer('ground',380));
  assert.equal(host.messages.filter(m=>m.type==='drag-commit').length,0);
  assert.match(await host.bridge('studio-poll\x1f'),/^pick/);
  await host.bridge('studio-event\x1fpick\x1f2\x1f0\x1f0\x1f37\x1f140\x1f20\x1f1');
  const commit=host.messages.find(m=>m.type==='drag-commit');
  assert.equal(commit.terrain,true);assert.equal(commit.delta.x,32);assert.equal(commit.delta.z,16);assert.equal(commit.delta.y,0);
});

test('world handles have enlarged hit areas and objects hide the Y axis',async()=>{
  const host=viewport();ready(host);select(host,'object');await projectGizmo(host);
  assert.match(host.overlay.innerHTML,/stroke-width="14"/);assert.match(host.overlay.innerHTML,/width="18"/);
  assert.doesNotMatch(host.overlay.innerHTML,/data-axis="y"/);
});

test('ground drag released outside the canvas finishes on a missed final terrain pick',async()=>{
  const host=viewport();ready(host);select(host);await projectGizmo(host);await host.bridge('studio-poll');
  host.overlayListeners.pointerdown(pointer('ground'));await host.bridge('studio-poll');await host.bridge('studio-poll');
  await host.bridge('studio-event\x1fpick\x1f1\x1f0\x1f0\x1f0\x1f160\x1f0\x1f1');
  host.overlayListeners.pointermove(pointer('ground',380));await host.bridge('studio-poll');
  await host.bridge('studio-event\x1fpick\x1f2\x1f0\x1f0\x1f37\x1f140\x1f20\x1f1');
  host.overlayListeners.pointerup(pointer('ground',900));
  assert.equal(await host.bridge('studio-poll'),'pick\x1f1760\x1f400\x1f3\x1f1');
  await host.bridge('studio-event\x1fpick\x1f3\x1f0\x1f0\x1f0\x1f0\x1f0\x1f0');
  const commits=host.messages.filter(m=>m.type==='drag-commit');
  assert.equal(commits.length,1);assert.equal(commits[0].delta.x,32);assert.equal(commits[0].delta.z,16);
});

test('camera survives explicit resource initialization and play-stop',async()=>{
  const host=viewport();
  vm.runInContext("initial={files:{},token:'a',available:[],restorePose:[1,2,3,25,45]};starting=true;",host.context);
  await host.bridge('studio-event\x1floaded\x1f1\x1f32');
  assert.equal(await host.bridge('studio-poll\x1f'),'');
  host.send({type:'stop',id:1});
  assert.equal(await host.bridge('studio-poll\x1f'),'stop');
  await host.bridge('studio-event\x1floaded\x1f0\x1f32');
  assert.equal(await host.bridge('studio-poll\x1f'),'restore-pose\x1f1\x1f2\x1f3\x1f25\x1f45');
});

test('Python-produced delta for every native entity class passes viewport packet validation',async()=>{
  const {spawnSync}=require('node:child_process');
  const root=path.resolve(__dirname,'../..');
  const python=process.platform==='win32'?path.join(root,'.venv/Scripts/python.exe'):path.join(root,'.venv/bin/python');
  const code=`
import sys,base64
sys.path.insert(0,'scripts')
from studio_core.maps import EditableMap,new_map
from studio_core.preview import encode_patch
before=EditableMap(new_map(16))
raw=before.data
for kind,identifier in [('object',1),('unit',100),('item',1),('info',2)]:
    raw=EditableMap(raw).apply({'op':'entity.add','kind':kind,'id':identifier,'type':1})
raw=EditableMap(raw).apply({'op':'entity.set','kind':'info','id':2,'values':{'vars':'한글,설정'}})
print(base64.b64encode(encode_patch(before,EditableMap(raw))[0]).decode())
`;
  const output=spawnSync(fs.existsSync(python)?python:'python3',['-c',code],{cwd:root,encoding:'utf8'});
  assert.equal(output.status,0,output.stderr);
  const host=viewport();ready(host);
  vm.runInContext("initial.available.push('item/1')",host.context);
  host.send({type:'project',preview:{...delta(),patch:output.stdout.trim()}});
  assert.equal(await host.bridge('studio-poll\x1f'),'patch\x1fb');
  assert.equal(Buffer.from(host.writes[1].data).toString('base64'),output.stdout.trim());
});

test('successful drag keeps its transient pose until the authoritative patch arrives',async()=>{
  const host=viewport();ready(host);select(host);await projectGizmo(host);await host.bridge('studio-poll\x1f');
  host.overlayListeners.pointerdown(pointer());host.overlayListeners.pointerup(pointer('x',375));
  const commit=host.messages.find(m=>m.type==='drag-commit');
  host.send({type:'drag-result',id:commit.id,ok:true});
  assert.equal(await host.bridge('studio-poll\x1f'),'drag-begin\x1f1\x1f2\x1f100');
  assert.equal(await host.bridge('studio-poll\x1f'),'drag-move\x1f32\x1f0\x1f0\x1f0');
  host.send({type:'project',preview:delta()});
  assert.equal(await host.bridge('studio-poll\x1f'),'patch\x1fb');
});
