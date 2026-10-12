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

test('stopping a paused play session releases focus before the engine polls',async()=>{
  const host=viewport();
  await host.bridge('studio-event\x1floaded\x1f1\x1f32');
  assert.equal(await host.bridge('focus\x1fplay'),0);
  host.send({type:'stop'});
  assert.equal(await host.bridge('focus\x1fplay'),1);
  assert.equal(await host.bridge('studio-poll\x1f'),'pose');
  assert.equal(await host.bridge('studio-poll\x1f'),'stop');
});

test('canvas picks account for scale and drop retains content identity',async()=>{
  const host=viewport();
  host.canvasListeners.drop({clientX:340,clientY:230,shiftKey:false,preventDefault(){},
    dataTransfer:{getData:()=>JSON.stringify({kind:'object',id:22})}});
  assert.equal(await host.bridge('studio-poll\x1f'),'pick\x1f640\x1f400\x1f1\x1f1');
  await host.bridge('studio-event\x1fpick\x1f1\x1f0\x1f0\x1f12\x1f160\x1f24\x1f1');
  const result=host.messages.at(-1);
  assert.equal(result.content.id,22);assert.equal(result.purpose,'place');assert.equal(result.hit,true);
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

test('camera survives explicit resource initialization and play-stop',async()=>{
  const host=viewport();
  vm.runInContext("initial={files:{},token:'a',available:[],restorePose:[1,2,3,25,45]};starting=true;",host.context);
  await host.bridge('studio-event\x1floaded\x1f1\x1f32');
  assert.equal(await host.bridge('studio-poll\x1f'),'pose');
  host.send({type:'stop'});
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
