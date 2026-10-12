/* The viewport hosts the actual Extended Blitz engine. No substitute renderer. */
'use strict';
const canvas=document.getElementById('canvas'),statusText=document.getElementById('status'),search=document.getElementById('search');
const overlay=document.getElementById('gizmo');
const SEP=String.fromCharCode(31),queue=[],pendingPicks=new Map(),keys=new Set();
let initial,loaded=false,starting=false,playing=false,tool='select',selection=null,dragging=false,rotating=false;
let pitch=25,yaw=0,pickSerial=0,lastX=0,lastY=0,snapshotPending=null;
let acknowledged=null,inFlight=null,requested=false,wantSync=false,syncFailed=false,resourceBlocked=false,mapSize=0;
let selected=[],revision=0,snap=0,projection=null,drag=null,dragSerial=0,transient=null,editorPose=null;

const send=(type,value={})=>parent.postMessage({source:'blitz-studio',type,...value},location.origin);
const command=(...parts)=>queue.push(parts.join(SEP));
const words=value=>{const n=BigInt(Math.floor(value));return Number(BigInt.asIntN(32,n))+SEP+Number(BigInt.asIntN(32,n>>32n));};
function fail(error){statusText.textContent=String(error?.message||error);send('error',{message:statusText.textContent});}
function normalize(path){
  const full=path.startsWith('/')?path:Module.FS.cwd()+'/'+path;
  const parts=[];for(const part of full.replaceAll('\\','/').split('/')){if(part==='..')parts.pop();else if(part&&part!=='.')parts.push(part);}
  return '/'+parts.join('/');
}
function applyFiles(files){
  for(const [relative,encoded] of Object.entries(files)){
    if(relative.includes('..')||relative.startsWith('/')||relative.includes('\\'))throw new Error('Invalid project resource path');
    const name='/game/'+relative;Module.FS.mkdirTree(name.slice(0,name.lastIndexOf('/')));
    Module.FS.writeFile(name,Uint8Array.from(atob(encoded),c=>c.charCodeAt(0)));
  }
}
function validatePatch(encoded){
  const bytes=Uint8Array.from(atob(encoded),c=>c.charCodeAt(0)),view=new DataView(bytes.buffer);
  let offset=0;
  const read=(type='int')=>{const size=type==='byte'?1:4;if(offset+size>bytes.length)throw new Error('잘린 장면 패치');
    const value=type==='byte'?view.getUint8(offset):type==='float'?view.getFloat32(offset,true):view.getInt32(offset,true);offset+=size;
    if(!Number.isFinite(value))throw new Error('유효하지 않은 장면 좌표');return value;};
  const count=()=>{const n=read();if(n<0||n>bytes.length)throw new Error('유효하지 않은 장면 개수');return n;};
  const identity=()=>{const c=read('byte'),id=read();if(c<1||c>4||id<1)throw new Error('유효하지 않은 개체');return c;};
  if(read()!==1)throw new Error('지원하지 않는 장면 패치');
  for(let n=count();n;n--)identity();
  for(let n=count();n;n--){const x=read(),z=read();if(x<0||z<0||x>mapSize||z>mapSize)throw new Error('지형 범위 오류');read('float');}
  const fields={1:'fffffi',2:'fffffffffff',3:'fffffibbi',4:'fffff'};
  for(let n=count();n;n--){const c=identity(),type=read();
    if(!initial.available.includes(['','object','unit','item','info'][c]+'/'+type))throw new Error('리소스 적용이 필요한 개체');
    for(const f of fields[c])read(f==='f'?'float':f==='b'?'byte':'int');
    if(c===4){const length=count();offset+=length;if(offset>bytes.length)throw new Error('잘린 맵 정보 문자열');}
  }
  if(offset!==bytes.length)throw new Error('장면 패치 길이 오류');
  return bytes;
}
function pick(event,purpose,terrain=false,content=null,extra={}){
  const rect=canvas.getBoundingClientRect();
  const x=(event.clientX-rect.left)*canvas.width/rect.width,y=(event.clientY-rect.top)*canvas.height/rect.height;
  const token=String(++pickSerial);pendingPicks.set(token,{purpose,shift:event.shiftKey,selection,content,...extra});
  command('pick',x,y,token,terrain?1:0);
}
async function bridge(request){
  const [op,...args]=request.split(SEP),FS=Module.FS;
  switch(op){
    case 'counter':return words(performance.now()*1000);
    case 'walltime':return words(performance.now()*10000);
    case 'width':return 1280;
    case 'height':return 800;
    case 'canvas-width':return canvas.width;
    case 'canvas-height':return canvas.height;
    case 'focus':return !document.hidden&&(!playing||document.pointerLockElement===canvas||document.activeElement===search)?1:0;
    case 'sync':return 1; // Deliberately ephemeral; project persistence belongs to the shared core.
    case 'copy':try{const dst=normalize(args[1]);if(Number(args[2])&&FS.analyzePath(dst).exists)return 0;FS.writeFile(dst,FS.readFile(normalize(args[0])));return 1;}catch{return 0;}
    case 'move':try{FS.rename(normalize(args[0]),normalize(args[1]));return 1;}catch{return 0;}
    case 'stamp':try{return words(FS.stat(normalize(args[0])).mtime.getTime()*10000);}catch{return '';}
    case 'search-open':search.style.display='block';search.value=args[0];return 1;
    case 'search-close':search.style.display='none';return 1;
    case 'search-text':return search.value;
    case 'search-set':search.value=args[0];return 1;
    case 'search-position':{const rect=canvas.getBoundingClientRect();Object.assign(search.style,{left:(rect.left+Number(args[0])*rect.width/canvas.width)+'px',top:(rect.top+Number(args[1])*rect.height/canvas.height)+'px',width:(Number(args[2])*rect.width/canvas.width)+'px',height:(Number(args[3])*rect.height/canvas.height)+'px'});return 1;}
    case 'search-focus':Number(args[0])===2?search.focus():canvas.focus();return 1;
    case 'control-focus':return document.activeElement===search?2:1;
    case 'studio-poll':
      // Commands such as drag rollback run before a pending authoritative patch.
      if(!queue.length&&snapshotPending&&!drag&&!playing&&!inFlight){
        const next=snapshotPending;snapshotPending=null;
        if(next.mode!=='delta'||next.baseToken!==acknowledged){syncFailed=true;fail('미리보기 기준이 달라졌습니다. 리소스 적용으로 다시 연결하세요.');return '';}
        resourceBlocked=next.resourcesRequired;
        if(next.resourcesRequired){send('resources',{pending:true,required:true,available:next.available});return '';}
        let patch;
        try{patch=validatePatch(next.patch);applyFiles(next.files);}catch(error){syncFailed=true;fail(error);return '';}
        FS.writeFile('/game/mods/Stranded II/maps/studio.patch',patch);
        transient=null;inFlight=next;
        return ['patch',next.token].join(SEP);
      }
      if(!queue.length&&transient){const value=transient;transient=null;return value;}
      maybeSync();
      if(queue.length)return queue.shift();
      if(!playing&&loaded&&!drag&&(rotating||keys.size)){
        const speed=keys.has('ShiftLeft')?15:5;
        return ['camera',pitch,yaw,((keys.has('KeyD')?1:0)-(keys.has('KeyA')?1:0))*speed,
          ((keys.has('KeyE')?1:0)-(keys.has('KeyQ')?1:0))*speed,((keys.has('KeyW')?1:0)-(keys.has('KeyS')?1:0))*speed].join(SEP);
      }
      return '';
    case 'studio-event':
      if(args[0]==='ready'){command(initial?.startPlaying?'play':'load',0);return 1;}
      if(args[0]==='loaded'){
        loaded=true;mapSize=Number(args[2]);playing=Number(args[1])===1;keys.clear();statusText.textContent='';fitCanvas();
        if(!acknowledged){acknowledged=initial?.token;editorPose=initial?.restorePose||null;}
        if(!playing&&editorPose)command('restore-pose',...editorPose);
        command('pose');send('loaded',{playing,size:Number(args[2])});
        send('resources',{pending:false,available:initial?.available||[]});maybeSync();return 1;
      }
      if(args[0]==='applied'){
        if(!inFlight||args[1]!==inFlight.token){syncFailed=true;fail('미리보기 적용 응답이 일치하지 않습니다.');return 1;}
        acknowledged=inFlight.token;send('applied',{token:acknowledged,revision:inFlight.revision});inFlight=null;maybeSync();return 1;
      }
      if(args[0]==='gizmo'){projection=args.slice(1).map(Number);drawGizmo();return 1;}
      if(args[0]==='camera'){if(!rotating){pitch=Number(args[1]);yaw=Number(args[2]);}if(!playing){editorPose=[Number(args[3]),Number(args[4]),Number(args[5]),Number(args[1]),Number(args[2])];send('pose',{pose:editorPose});}return 1;}
      if(args[0]==='pick'){
        const context=pendingPicks.get(args[1]);pendingPicks.delete(args[1]);
        if(context){
          const hit={kind:['','object','unit','item','info'][Number(args[2])],id:Number(args[3]),x:Number(args[4]),y:Number(args[5]),z:Number(args[6]),hit:Number(args[7])!==0};
          if(context.purpose==='drag-start'||context.purpose==='drag-ground'){
            if(drag&&context.dragId===drag.id&&hit.hit){
              if(context.purpose==='drag-start')drag.ground=hit;
              else if(drag.ground){
                drag.delta={x:StudioGizmo.snapDelta(hit.x-drag.ground.x,drag.anchor.x,drag.snap),y:0,z:StudioGizmo.snapDelta(hit.z-drag.ground.z,drag.anchor.z,drag.snap)};
                previewDrag();
              }
            }
            if(context.final&&drag?.id===context.dragId)finishDrag();
          }else send('pick',{...hit,...context});
        }return 1;
      }
      if(args[0]==='error'){syncFailed=true;inFlight=null;fail(args[1]);return 1;}
      return 1;
    default:throw new Error('Unknown engine service: '+op);
  }
}
var Module={canvas,noInitialRun:true,s2Bridge:bridge,locateFile:name=>'/engine/'+name,
  print:message=>send('log',{message:String(message)}),printErr:message=>send('log',{message:String(message)}),onAbort:fail,
  setStatus:text=>{if(!loaded)statusText.textContent=text;},
  onRuntimeInitialized:async()=>{
    try{
      applyFiles(initial.files);
      Module.FS.mkdirTree('/game/mods/Stranded II/saves');
      Module.FS.writeFile('/game/core/modern.ini','language=ko\nfps=60\ndisplay=windowed\nrender_width=1280\nrender_height=800\nresolution=custom\n');
      Module.FS.chdir('/game');
      await Module.callMain([]);
    }catch(error){fail(error);}
  }};
async function boot(){
  if(starting)return;starting=true;
  if(!navigator.gpu||typeof WebAssembly.Suspending!=='function')throw new Error('WebGPU 및 JSPI를 지원하는 Electron이 필요합니다.');
  if(!await navigator.gpu.requestAdapter())throw new Error('WebGPU 장치를 사용할 수 없습니다.');
  for(const name of ['assets.js','game.js'])await new Promise((resolve,reject)=>{const script=document.createElement('script');script.src='/engine/'+name;script.onload=resolve;script.onerror=()=>reject(new Error('Blitz 엔진 파일을 읽지 못했습니다: '+name));document.body.append(script);});
}
window.addEventListener('message',event=>{
  if(event.source!==parent||event.origin!==location.origin||event.data?.source!=='studio-ui')return;
  const message=event.data;
  if(message.type==='project'){
    if(!initial){initial=message.preview;boot().catch(fail);}
    else{
      requested=false;snapshotPending=message.preview;
      send('resources',{pending:message.preview.resourcesPending,required:message.preview.resourcesRequired,available:message.preview.available});
    }
  }else if(message.type==='refresh'){wantSync=true;maybeSync();}
  else if(message.type==='sync-error'){requested=false;syncFailed=true;fail(message.message);}
  else if(message.type==='tool'){
    const identities=entities=>entities.map(e=>e.kind+'/'+e.id).join(',');
    if(drag&&!drag.finishing&&(tool!==message.tool||identities(selected)!==identities(message.entities||[])))cancelDrag();
    tool=message.tool;selection=message.selection||null;selected=message.entities||[];revision=message.revision;snap=message.snap||0;
    command('select',selection?.class||0,selection?.id||0);drawGizmo();
  }else if(message.type==='focus'&&message.selection&&!drag){command('focus',message.selection.class,message.selection.id);command('pose');}
  else if(message.type==='drag-result'){
    if(drag?.id===message.id){
      // A successful gesture remains visible until its authoritative patch;
      // studio_patch restores the baseline and applies the edit in one frame.
      if(!message.ok){transient=null;command('drag-cancel');}
      drag=null;send('drag-state',{active:false});wantSync=true;maybeSync();drawGizmo();
    }
  }else if(message.type==='play'&&!drag&&!inFlight&&!snapshotPending&&!requested){command('play');}
  else if(message.type==='stop'){playing=false;document.exitPointerLock();keys.clear();command('stop');}
});
function maybeSync(){
  if(wantSync&&loaded&&!playing&&!drag&&!inFlight&&!snapshotPending&&!requested&&!syncFailed){
    requested=true;wantSync=false;send('request-preview',{since:acknowledged});
  }
}
function drawGizmo(){
  if(!overlay)return;
  if(!loaded||playing||tool!=='move'||!selection||!projection||projection[2]<=0){overlay.innerHTML='';return;}
  const rect=canvas.getBoundingClientRect(),sx=rect.width/canvas.width,sy=rect.height/canvas.height;
  const origin=projection.slice(0,3),x=rect.left+origin[0]*sx,y=rect.top+origin[1]*sy;
  const axes=['x','y','z'],colors=['#ff6871','#72df8d','#67aeff'];
  let html='';
  axes.forEach((axis,index)=>{
    if(axis==='y'&&selected.some(e=>e.kind==='object'))return;
    const vector=StudioGizmo.axis(origin,projection.slice(3+index*3,6+index*3),sx,sy);if(!vector)return;
    const ex=x+vector.x*80,ey=y+vector.y*80,color=drag?.axis===axis?'#ffe08b':colors[index];
    html+=`<line x1="${x}" y1="${y}" x2="${ex}" y2="${ey}" stroke="${color}" stroke-width="4"/><line data-axis="${axis}" x1="${x}" y1="${y}" x2="${ex}" y2="${ey}" stroke="transparent" stroke-width="14"/><circle cx="${ex}" cy="${ey}" r="5" fill="${color}"/><text x="${ex+7}" y="${ey-7}" fill="${color}">${axis.toUpperCase()}</text>`;
  });
  overlay.innerHTML=html+`<rect data-axis="ground" class="center" x="${x-9}" y="${y-9}" width="18" height="18" rx="3"/>`;
}
function beginDrag(event){
  const axis=event.target.dataset?.axis;
  if(!axis||event.button!==0||drag||playing||inFlight||requested||snapshotPending||syncFailed||resourceBlocked||!projection||!selected.length)return;
  event.preventDefault();canvas.focus();keys.clear();rotating=false;
  const rect=canvas.getBoundingClientRect(),index=['x','y','z'].indexOf(axis);
  const vector=index<0?null:StudioGizmo.axis(projection.slice(0,3),projection.slice(3+index*3,6+index*3),rect.width/canvas.width,rect.height/canvas.height);
  if(index>=0&&!vector)return;
  if(axis==='y'&&selected.some(e=>e.kind==='object'))return;
  drag={id:++dragSerial,axis,vector,startX:event.clientX,startY:event.clientY,pointerId:event.pointerId,
    anchor:{...selected.at(-1)},selection:selected.map(e=>({kind:e.kind,id:e.id})),revision,snap,delta:{x:0,y:0,z:0},finishing:false};
  overlay.setPointerCapture(event.pointerId);
  command('drag-begin',selected.length,...selected.flatMap(e=>[e.class,e.id]));
  send('drag-state',{active:true});
  if(axis==='ground')pick(event,'drag-start',true,null,{dragId:drag.id});
}
function previewDrag(){
  if(drag)transient=['drag-move',drag.delta.x,drag.delta.y,drag.delta.z,drag.axis==='ground'?1:0].join(SEP);
}
function moveDrag(event,final=false){
  if(!drag||drag.finishing||event.pointerId!==drag.pointerId)return;
  if(drag.axis==='ground'){
    // Keep only the latest terrain sample while the engine is busy.
    for(const [token,pending] of pendingPicks)if(pending.purpose==='drag-ground'){
      pendingPicks.delete(token);const index=queue.findIndex(c=>c.split(SEP)[3]===token&&c.startsWith('pick'+SEP));if(index>=0)queue.splice(index,1);
    }
    pick(event,'drag-ground',true,null,{dragId:drag.id,final});
    if(final)drag.finishing=true;
  }else{
    const distance=StudioGizmo.distance(drag.vector,event.clientX-drag.startX,event.clientY-drag.startY);
    if(distance!==null){drag.delta=StudioGizmo.delta(drag.axis,distance,drag.anchor,drag.snap);previewDrag();}
    if(final)finishDrag();
  }
}
function finishDrag(){
  if(!drag)return;
  drag.finishing=true;
  if(!Object.values(drag.delta).some(v=>Math.abs(v)>.00001)){cancelDrag();return;}
  send('drag-commit',{id:drag.id,revision:drag.revision,selection:drag.selection,delta:drag.delta,terrain:drag.axis==='ground'});
}
function cancelDrag(){
  if(!drag)return;
  const id=drag.pointerId;drag=null;transient=null;command('drag-cancel');
  if(overlay.hasPointerCapture?.(id))overlay.releasePointerCapture(id);
  send('drag-state',{active:false});maybeSync();drawGizmo();
}
overlay?.addEventListener('pointerdown',beginDrag);
overlay?.addEventListener('pointermove',event=>moveDrag(event));
overlay?.addEventListener('pointerup',event=>{if(event.button===0)moveDrag(event,true);});
overlay?.addEventListener('pointercancel',()=>{if(!drag?.finishing)cancelDrag();});
overlay?.addEventListener('lostpointercapture',()=>{if(!drag?.finishing)cancelDrag();});
canvas.addEventListener('pointerdown',event=>{
  canvas.focus();Module.SDL2?.audioContext?.resume();
  if(playing){canvas.requestPointerLock()?.catch(fail);return;}
  if(drag)return;
  canvas.setPointerCapture(event.pointerId);
  if(event.button===2){rotating=true;lastX=event.clientX;lastY=event.clientY;}
  if(event.button===0){dragging=true;if(tool==='select'||tool==='move')pick(event,'select');}
});
canvas.addEventListener('pointermove',event=>{
  if(playing)return;
  if(rotating){yaw+=(event.clientX-lastX)*.25;pitch=Math.max(-85,Math.min(85,pitch+(event.clientY-lastY)*.25));lastX=event.clientX;lastY=event.clientY;}
});
canvas.addEventListener('pointerup',event=>{
  if(playing)return;
  if(event.button===2)rotating=false;
  if(event.button===0&&dragging){
    dragging=false;
    if(tool==='place')pick(event,'place',true);
    else if(tool==='terrain')pick(event,'terrain',true);
  }
});
canvas.addEventListener('wheel',event=>{if(!playing&&!drag){event.preventDefault();command('camera',pitch,yaw,0,0,-Math.sign(event.deltaY)*25);}},{passive:false});
window.addEventListener('keydown',event=>{
  if(playing)return;
  if(event.code==='Escape'&&drag&&!drag.finishing){event.preventDefault();cancelDrag();return;}
  if(drag)return;
  let shortcut;
  if(event.ctrlKey||event.metaKey){if(event.code==='KeyZ')shortcut=event.shiftKey?'redo':'undo';if(event.code==='KeyD')shortcut='duplicate';}
  if(event.code==='Delete')shortcut='delete';
  if(shortcut){event.preventDefault();send('shortcut',{key:shortcut});return;}
  keys.add(event.code);if(['KeyW','KeyA','KeyS','KeyD','KeyQ','KeyE','Space'].includes(event.code))event.preventDefault();
  if(event.code==='KeyF'&&selection){command('focus',selection.class,selection.id);command('pose');}
});
window.addEventListener('keyup',event=>keys.delete(event.code));
window.addEventListener('blur',()=>{keys.clear();rotating=dragging=false;if(!drag?.finishing)cancelDrag();});
canvas.addEventListener('contextmenu',event=>event.preventDefault());
canvas.addEventListener('dragover',event=>{if(!playing&&event.dataTransfer.types.includes('application/s2-content')){event.preventDefault();event.dataTransfer.dropEffect='copy';}});
canvas.addEventListener('drop',event=>{
  if(playing||drag)return;event.preventDefault();
  try{const content=JSON.parse(event.dataTransfer.getData('application/s2-content'));if(content&&['object','unit','item','info','building'].includes(content.kind))pick(event,'place',true,content);}catch(error){fail(error);}
});
function fitCanvas(){
  const ratio=Math.min(innerWidth/(canvas.width||1280),innerHeight/(canvas.height||800));
  canvas.style.width=((canvas.width||1280)*ratio)+'px';canvas.style.height=((canvas.height||800)*ratio)+'px';
}
window.addEventListener('resize',()=>{fitCanvas();drawGizmo();});
window.addEventListener('error',event=>fail(event.error||event.message));
window.addEventListener('unhandledrejection',event=>fail(event.reason));
send('ready-for-project');
