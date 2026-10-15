import {useEffect,useRef,useState} from 'react';
import {classes,Entity,Kind,labels,Operation,Preview,RecordInfo,request,Snapshot,ScriptTarget,ScriptSources,AuthoringTarget,Conflict,Diagnostic,StudioError} from './api';
import {ScriptEditor} from './ScriptEditor';
import {useDraft} from './useDraft';
import {useTextDialog} from './useTextDialog';
import {ContentThumbnail} from './ContentThumbnail';
import {placementTarget} from './placement';
import {ScriptWorkspace,ScriptLocation} from './ScriptWorkspace';
import {TemplatePanel} from './TemplatePanel';
import {RuntimeDebug} from './RuntimeDebug';
import {locateRuntime,RuntimeRecord,RuntimeEntry} from './runtimeSource';
import {acknowledgeDraft,draftEvent,listDrafts,readDraft,removeDraft} from './drafts';
import {ProjectHome,RecentProject} from './ProjectHome';
import {References} from './AuthoringNavigation';
import {ResourceInput,contentChoices} from './ResourceInput';
import {MigrationPanel} from './MigrationPanel';
import {EditQueue} from './editQueue';

type Selection={kind:Kind;id:number};
type Tool='select'|'move'|'place'|'terrain';
const fields:Record<string,string>={name:'이름',genname:'조합 결과 이름',model:'모델',icon:'아이콘',health:'체력',health_max:'최대 체력',weight:'무게',damage:'피해량',scale:'크기',group:'그룹',behaviour:'행동',objectid:'결과 오브젝트',unitid:'결과 유닛',buildspace:'건축 위치',count:'수량',x:'X',y:'Y',z:'Z',yaw:'방향',pitch:'기울기',vars:'설정값',description:'설명'};

export function App(){
  const {ask,modal}=useTextDialog();
  const [snapshot,setSnapshot]=useState<Snapshot|null>(null),[project,setProject]=useState(''),[activeMap,setActiveMap]=useState('');
  const [base,setBase]=useState(''),[projectPath,setProjectPath]=useState(''),[recent,setRecent]=useState<RecentProject[]>([]);
  const [draftVersion,setDraftVersion]=useState(0),[showDrafts,setShowDrafts]=useState(false),[conflict,setConflict]=useState<Conflict|null>(null);
  const [migrationOpen,setMigrationOpen]=useState(false);
  const editQueue=useRef(new EditQueue());
  const [worldQuery,setWorldQuery]=useState(''),[worldKind,setWorldKind]=useState(''),[assetPaths,setAssetPaths]=useState<string[]>([]);
  const [fieldLocation,setFieldLocation]=useState<{field:string;serial:number}>(),[inlineLocation,setInlineLocation]=useState<{line:number;serial:number}>();
  useEffect(()=>{const changed=()=>setDraftVersion(v=>v+1);window.addEventListener(draftEvent,changed);return()=>window.removeEventListener(draftEvent,changed);},[]);
  const drafts=listDrafts(project);
  const [engineReady,setEngineReady]=useState(false),[engineStatus,setEngineStatus]=useState('대기'),[busy,setBusy]=useState(false),[error,setError]=useState('');
  const [tool,setTool]=useState<Tool>('select'),[selection,setSelection]=useState<Selection[]>([]),[contentSelection,setContentSelection]=useState<{kind:Kind;id:number|string}|null>(null);
  const [filter,setFilter]=useState<Kind>('object'),[query,setQuery]=useState(''),[tab,setTab]=useState<'assets'|'console'|'script'>('assets');
  const [logs,setLogs]=useState<string[]>([]),[playing,setPlaying]=useState(false),[radius,setRadius]=useState(2),[strength,setStrength]=useState(.005),[brush,setBrush]=useState('raise');
  const [playState,setPlayState]=useState<'idle'|'starting'|'playing'|'stopping'>('idle');
  const playId=useRef(0);
  const autoPlay=useRef<number|null>(null),preparingPlay=useRef(false),playVersion=useRef<{revision:number;scriptsVersion?:string;projectHash:string}|null>(null);
  const [fileDirty,setFileDirty]=useState(false),[scriptLocation,setScriptLocation]=useState<ScriptLocation>(),[templatesOpen,setTemplatesOpen]=useState(false);
  const runtimeSources=useRef<ScriptSources|undefined>(undefined);
  const [runtimeEntries,setRuntimeEntries]=useState<RuntimeEntry[]>([]),[runtimeVariables,setRuntimeVariables]=useState<RuntimeRecord|null>(null);
  function jumpScript(path:string,line=1){setScriptLocation({path,line,serial:Date.now()});setTab('script');}
  async function jumpRuntime(path:string,line=1){
    try{
      const value=await request<{file:{hash:string}}>({command:'scripts',project:current.current.project,path});
      if(value.file.hash!==runtimeSources.current?.files.find(f=>f.path===path)?.hash){setError('테스트 이후 파일이 바뀌었습니다. 다시 테스트해 오류 위치를 갱신하세요.');return;}
      jumpScript(path,line);
    }catch(e){setError(String(e));}
  }
  const [snap,setSnap]=useState(0),[bottomHeight,setBottomHeight]=useState(285),[preview,setPreview]=useState<Preview|null>(null);
  const [frameVersion,setFrameVersion]=useState(0),[resourcesPending,setResourcesPending]=useState(false),[available,setAvailable]=useState<string[]>([]),[dragActive,setDragActive]=useState(false);
  const sceneIdentity=useRef(''),cameraPose=useRef<number[]|undefined>(undefined),session=useRef(0),dragLock=useRef(false),pendingExternal=useRef(false);
  const pendingFocus=useRef<Selection|null>(null);
  const [commands,setCommands]=useState<{name:string;parameters:string}[]>([]);
  const frame=useRef<HTMLIFrameElement>(null),current=useRef({snapshot,project,activeMap,tool,selection,contentSelection,radius,strength,brush,snap,busy,preview,playing});
  current.current={snapshot,project,activeMap,tool,selection,contentSelection,radius,strength,brush,snap,busy,preview,playing};
  const primary=selection.at(-1);
  const selectedEntity=snapshot?.map?.entities.find(e=>primary?.kind===e.kind&&primary.id===e.id);
  const selectedContent=snapshot?.catalog.find(r=>contentSelection?.kind===r.kind&&String(contentSelection.id)===String(r.id));
  const scriptTarget=selectedEntity ? `${activeMap}/${selectedEntity.kind}/${selectedEntity.id}` : selectedContent ? `${selectedContent.kind}/${selectedContent.id}` : activeMap;
  const scriptSource=selectedEntity?selectedEntity.script:selectedContent?(selectedContent.properties.script?.[0]||''):(snapshot?.map?.script||'');
  const fileTarget:ScriptTarget|null=selectedEntity?{scope:'entity',map:activeMap,kind:selectedEntity.kind,id:selectedEntity.id}:selectedContent?{scope:'definition',kind:selectedContent.kind,id:selectedContent.id}:activeMap?{scope:'map',map:activeMap}:null;
  const scriptKey='studio.script:'+project+':'+scriptTarget;
  const inlineDraft=useDraft(scriptKey,{text:scriptSource},snapshot?.revision||0,fileTarget||undefined);
  const script=inlineDraft.values.text,scriptDirty=inlineDraft.dirty;
  const changeScript=(text:string)=>inlineDraft.update('text',text),resetScript=inlineDraft.reset;
  const post=(type:string,value:Record<string,unknown>={})=>frame.current?.contentWindow?.postMessage({source:'studio-ui',type,...value},location.origin);
  function log(message:string){setLogs(previous=>[...previous,message].slice(-300));}
  async function run(work:()=>Promise<void>){
    if(current.current.busy)return false;
    current.current.busy=true;setBusy(true);setError('');
    try{await work();return true;}catch(reason){const message=String(reason instanceof Error?reason.message:reason);setError(message);log(message);if(reason instanceof StudioError&&reason.details){setConflict(reason.details);setShowDrafts(true);}return false;}
    finally{current.current.busy=false;setBusy(false);}
  }
  function sendTool(){
    const state=current.current,primary=state.selection.at(-1);
    const entities=state.selection.map(s=>state.snapshot?.map?.entities.find(e=>e.kind===s.kind&&e.id===s.id)).filter((e):e is Entity=>!!e).map(e=>({...e,class:classes[e.kind]}));
    const target=placementTarget(state.contentSelection,state.snapshot?.catalog||[]);
    const placement=target&&state.preview?.available.includes(target.kind+'/'+target.id)?target:null;
    post('tool',{tool:state.tool,selection:primary?{...primary,class:classes[primary.kind]}:null,entities,revision:state.snapshot?.revision,snap:state.snap,placement});
  }
  async function fullPreview(dir:string,map:string){
    const value=await request<Preview>({command:'preview',project:dir,map});
    const identity=dir+'\n'+map;
    if(sceneIdentity.current===identity)value.restorePose=cameraPose.current;
    else cameraPose.current=undefined;
    sceneIdentity.current=identity;session.current++;
    setResourcesPending(false);setAvailable(value.available);setPreview(value);current.current.preview=value;
    setEngineStatus('Blitz 연결 중');setFrameVersion(v=>v+1);
  }
  async function refresh(dir=project,map=activeMap){
    const next=await request<Snapshot>({command:'inspect',project:dir,map:map||undefined});
    pendingExternal.current=false;
    dir=next.project;map=next.activeMap||'';setSnapshot(next);setProject(dir);setActiveMap(map);
    current.current.snapshot=next;current.current.project=dir;current.current.activeMap=map;
    setSelection(previous=>previous.filter(s=>next.map?.entities.some(e=>e.kind===s.kind&&e.id===s.id)));window.studio.storage.setItem('studio.project',dir);
    window.studio.storage.setItem('studio.last-map:'+dir,map);
    setRecent(previous=>{const values=[{path:dir,name:next.name,map},...previous.filter(p=>p.path!==dir)].slice(0,10);window.studio.storage.setItem('studio.recent',JSON.stringify(values));return values;});
    if(map&&engineReady&&next.base===base&&!next.diagnostics.some(d=>d.severity==='error')){
      if(sceneIdentity.current===dir+'\n'+map&&current.current.preview)post('refresh');
      else await fullPreview(dir,map);
    }else if(!map){setPreview(null);current.current.preview=null;sceneIdentity.current='';}
    else if(next.base!==base){setPreview(null);current.current.preview=null;sceneIdentity.current='';setEngineStatus('미리보기 리소스 재연결 필요');}
    else {setResourcesPending(true);setEngineStatus('검증 오류 · 마지막 정상 화면');}
  }
  async function applyResources(){
    if(dragLock.current)return;
    await run(async()=>{
      const state=current.current;
      // Refresh the authoritative saved state even if the CLI edited it just now.
      await refresh(state.project,state.activeMap);
      await fullPreview(state.project,current.current.activeMap);
    });
  }
  async function mutate(operations:Operation[],expected?:number){
    const state=current.current;
    if(state.playing)return false;
    if(operations.every(op=>['entity.add','entity.set','entity.remove','entities.move','terrain.brush'].includes(op.op))){
      if(state.busy&&!editQueue.current.active)return false;
      const context=state.project+'\n'+state.activeMap;
      return editQueue.current.enqueue(async()=>{
        const latest=current.current;
        if(latest.playing||context!==latest.project+'\n'+latest.activeMap)return false;
        return run(async()=>{
          const result=await request<{results:unknown[];snapshot?:Snapshot}>({command:'apply',project:latest.project,map:latest.activeMap,operations,expectRevision:expected??latest.snapshot?.revision,expectProjectHash:latest.snapshot?.projectHash});
          if(result.snapshot){
            setSnapshot(result.snapshot);current.current.snapshot=result.snapshot;
            setSelection(old=>old.filter(s=>result.snapshot!.map?.entities.some(e=>e.kind===s.kind&&e.id===s.id)));
            post('refresh');
          }else await refresh(latest.project,latest.activeMap);
        });
      });
    }
    return run(async()=>{const result=await request<{results:unknown[]}>({command:'apply',project:state.project,operations,expectRevision:expected??state.snapshot?.revision,expectProjectHash:state.snapshot?.projectHash});log(JSON.stringify(result.results));await refresh(state.project,state.activeMap);});
  }
  async function flushSaved(){
    await run(async()=>{await request({command:'flush',project:current.current.project});setSnapshot(old=>old?{...old,pendingSave:false,saveError:null}:old);});
  }
  async function saveAll(){
    if(listDrafts(current.current.project).length&&!await saveDrafts())return;
    await flushSaved();
  }
  async function history(redo=false){
    const state=current.current;if(state.playing||dragLock.current)return;
    await run(async()=>{await request({command:redo?'redo':'undo',project:state.project,expectRevision:state.snapshot?.revision,expectProjectHash:state.snapshot?.projectHash});await refresh(state.project,state.activeMap);});
  }
  async function saveDrafts(keys?:string[]){
    if(current.current.playing||dragLock.current)return false;
    return run(async()=>{
      const state=current.current,entries=listDrafts(state.project).filter(d=>!keys||keys.includes(d.key));
      if(!entries.length)return;
      const version=await request<{revision:number;projectHash:string}>({command:'revision',project:state.project});
      const result=await request<{results:{hash?:string}[]}>({command:'apply',project:state.project,operations:entries.map(d=>d.operation),expectRevision:version.revision,expectProjectHash:version.projectHash});
      entries.forEach((entry,i)=>acknowledgeDraft(entry,result.results[i]));setConflict(null);
      await refresh(state.project,state.activeMap);
    });
  }
  async function jumpTarget(target:AuthoringTarget,field?:string,line=1){
    if(target.scope==='file'){jumpScript(target.path,line);return;}
    if(current.current.playing||dragLock.current){setError('테스트 또는 드래그를 끝낸 뒤 대상으로 이동하세요.');return;}
    if('map' in target&&target.map!==current.current.activeMap){
      const ok=await run(()=>refresh(current.current.project,target.map));if(!ok)return;
    }
    setWorldQuery('');setWorldKind('');
    if(target.scope==='definition'){setSelection([]);setContentSelection({kind:target.kind,id:target.id});setFilter(target.kind);}
    else if(target.scope==='entity'){
      const selected={kind:target.kind,id:target.id};setSelection([selected]);setContentSelection(null);pendingFocus.current=selected;
      post('focus',{selection:{...selected,class:classes[target.kind]}});
    }else{setSelection([]);setContentSelection(null);}
    if(field==='script'){setTab('script');setInlineLocation({line,serial:Date.now()});}
    else if(field)setFieldLocation({field,serial:Date.now()});
  }
  function jumpDiagnostic(d:Diagnostic){if(d.path)jumpScript(d.path,d.line);else if(d.target)void jumpTarget(d.target,d.field,d.line);}
  useEffect(()=>{
    if(!project)return;let alive=true;
    void request<{paths:string[]}>({command:'assets',project}).then(r=>{if(alive)setAssetPaths(r.paths);}).catch(e=>{if(alive)setError(String(e));});
    return()=>{alive=false;};
  },[project,snapshot?.thumbnailVersion]);
  useEffect(()=>{
    if(!fieldLocation)return;
    const timer=setTimeout(()=>{const input=document.querySelector<HTMLElement>(`[data-field="${CSS.escape(fieldLocation.field)}"] input,[data-field="${CSS.escape(fieldLocation.field)}"] textarea`);input?.scrollIntoView({block:'center'});input?.focus();},0);
    return()=>clearTimeout(timer);
  },[fieldLocation]);
  useEffect(()=>{
    window.studio.environment().then(env=>{setBase(env.base);setEngineReady(env.engineReady);setProjectPath(env.projects+'/my-extended-project');});
    try{const values=JSON.parse(window.studio.storage.getItem('studio.recent')||'[]');const last=window.studio.storage.getItem('studio.project');setRecent(values.length?values.slice(0,10):last?[{path:last,name:last.split(/[\\/]/).pop()||last,map:''}]:[]);}catch{}
    request<{commands:{name:string;parameters:string}[]}>({command:'commands'}).then(value=>setCommands(value.commands)).catch(()=>{});
  },[]);
  const validPlay=!!snapshot&&!!activeMap&&engineReady&&snapshot.base===base;
  async function togglePlay(savedOnly=false){
    if(current.current.playing){
      const pending=preparingPlay.current||autoPlay.current!==null;autoPlay.current=null;preparingPlay.current=false;
      const id=++playId.current;
      if(pending){current.current.playing=false;setPlaying(false);setPlayState('idle');}
      else {setPlayState('stopping');post('stop',{id});}return;
    }
    if(!activeMap||!engineReady||current.current.busy||dragLock.current)return;
    if(!savedOnly){if(!await saveDrafts())return;if(listDrafts(current.current.project).length){setError('저장 중 초안이 바뀌었습니다. 다시 저장 후 테스트하세요.');return;}}
    const state=current.current;
    const id=++playId.current;
    preparingPlay.current=true;current.current.playing=true;setPlaying(true);setPlayState('starting');setError('');
    try{
      const next=await request<Snapshot>({command:'inspect',project:state.project,map:state.activeMap});
      if(next.diagnostics.some(d=>d.severity==='error')){setSnapshot(next);current.current.snapshot=next;setTab('console');throw new Error('저장된 스크립트·콘텐츠의 진단 오류를 수정하세요.');}
      const prepared=await request<Preview>({command:'preview',project:state.project,map:state.activeMap,expectRevision:next.revision,expectScriptsVersion:next.scripts.version,expectProjectHash:next.projectHash});
      if(id!==playId.current)return;
      preparingPlay.current=false;setSnapshot(next);current.current.snapshot=next;playVersion.current={revision:next.revision,scriptsVersion:next.scripts.version,projectHash:next.projectHash};
      runtimeSources.current=prepared.sources;setRuntimeEntries([]);setRuntimeVariables(null);
      if(listDrafts(state.project).length)log('미저장 초안은 제외하고 디스크에 저장된 버전으로 테스트합니다.');
      if(!state.preview||prepared.resourcesVersion!==state.preview.resourcesVersion||engineStatus!=='Blitz 연결됨'){
        prepared.restorePose=cameraPose.current;autoPlay.current=id;
        setPreview(prepared);current.current.preview=prepared;setAvailable(prepared.available);setResourcesPending(false);session.current++;
        sceneIdentity.current=state.project+'\n'+state.activeMap;setEngineStatus('저장된 리소스 적용 중');setFrameVersion(v=>v+1);
      }else post('play',{id});
    }catch(reason){if(id===playId.current){preparingPlay.current=false;current.current.playing=false;setPlaying(false);setPlayState('idle');setError(String(reason));}}
  }
  useEffect(()=>{
    sendTool();
  },[tool,selection,contentSelection,snapshot,snap]);
  useEffect(()=>{
    if(!project)return;
    const timer=setInterval(async()=>{
      if(current.current.busy||playing)return;
      try{const next=await request<{revision:number;scriptsVersion:string;projectHash:string;pendingSave?:boolean;saveError?:string|null}>({command:'revision',project});
        if(current.current.project!==project||current.current.busy||current.current.playing)return;
        if(next.projectHash!==current.current.snapshot?.projectHash){
          if(dragLock.current){pendingExternal.current=true;return;}
          log('외부 변경을 불러왔습니다.');await refresh(project,current.current.activeMap);
        }else setSnapshot(old=>old?{...old,pendingSave:next.pendingSave,saveError:next.saveError}:old);
      }catch(reason){setError(String(reason));}
    },1800);return()=>clearInterval(timer);
  },[project,engineReady,playing]);
  useEffect(()=>{
    const listener=(event:MessageEvent)=>{
      if(event.origin!==location.origin||event.source!==frame.current?.contentWindow||event.data?.source!=='blitz-studio')return;
      const data=event.data,state=current.current;
      if(data.type==='runtime'){
        const record=data.record as RuntimeRecord;
        if(record.kind==='variables')setRuntimeVariables(record);
        else setRuntimeEntries(old=>[...old,{...record,...locateRuntime(record,runtimeSources.current)}].slice(-300));
        return;
      }
      if(data.type==='ready-for-project'&&state.preview){post('project',{preview:state.preview});return;}
      if(data.type==='loaded'){setEngineStatus('Blitz 연결됨');if(!data.playing){sendTool();if(pendingFocus.current){post('focus',{selection:{...pendingFocus.current,class:classes[pendingFocus.current.kind]}});pendingFocus.current=null;}if(autoPlay.current!==null){const id=autoPlay.current;autoPlay.current=null;post('play',{id});}}return;}
      if(data.type==='play-state'){
        if(data.id!==playId.current)return;
        const active=data.state!=='idle';current.current.playing=active;setPlaying(active);setPlayState(data.state);
        if(data.message){setError(data.message);log(data.message);}
        if(data.state==='playing')log(`플레이 시작: 시작 요청부터 첫 게임 프레임까지 ${data.startMs}ms`);
        return;
      }
      if(data.type==='pose'){cameraPose.current=data.pose;return;}
      if(data.type==='cancel-placement'){setTool('select');return;}
      if(data.type==='resources'){setResourcesPending(data.pending);setAvailable(data.available||[]);if(data.required)setError('새 콘텐츠를 장면에 표시하려면 리소스 적용이 필요합니다.');return;}
      if(data.type==='applied'){setEngineStatus('Blitz 연결됨');return;}
      if(data.type==='request-preview'){
        const identity=session.current;
        void request<Preview>({command:'preview',project:state.project,map:state.activeMap,since:data.since,...(data.playId!==undefined?{expectRevision:playVersion.current?.revision,expectScriptsVersion:playVersion.current?.scriptsVersion,expectProjectHash:playVersion.current?.projectHash}:{})}).then(value=>{
          if(identity===session.current)post('project',{preview:value,playId:data.playId});
        }).catch(reason=>{if(identity===session.current)post(data.playId===undefined?'sync-error':'play-error',{id:data.playId,message:String(reason)});});return;
      }
      if(data.type==='drag-state'){dragLock.current=data.active;setDragActive(data.active);if(!data.active&&pendingExternal.current)void run(()=>refresh(state.project,state.activeMap));return;}
      if(data.type==='drag-commit'){
        void (async()=>{
          const ok=await mutate([{op:'entities.move',map:state.activeMap,selection:data.selection,delta:data.delta,terrain:data.terrain}],data.revision);
          // On a revision conflict restore the transient pose, then reconcile the
          // latest saved scene. No losing edit is silently replayed.
          if(!ok)try{await refresh(state.project,state.activeMap);}catch(reason){setError(String(reason));}
          post('drag-result',{id:data.id,ok});dragLock.current=false;setDragActive(false);
        })();return;
      }
      if(data.type==='error'){
        if(preparingPlay.current||autoPlay.current!==null){++playId.current;preparingPlay.current=false;autoPlay.current=null;current.current.playing=false;setPlaying(false);setPlayState('idle');}
        setEngineStatus('엔진 오류');setError(data.message);log(data.message);return;
      }
      if(data.type==='log'){log(data.message);return;}
      if(data.type==='shortcut'){if(data.key==='undo')void history();else if(data.key==='redo')void history(true);else if(data.key==='delete')void removeSelected();else if(data.key==='duplicate')void duplicateSelected();else if(data.key==='save')void saveAll();return;}
      if(data.type!=='pick'||!data.hit||state.playing||(state.busy&&!(editQueue.current.active&&['place','terrain'].includes(data.purpose))))return;
      const round=(value:number)=>state.snap?Math.round(value/state.snap)*state.snap:value;
      if(data.purpose==='select'){
        if(state.tool==='move'&&!data.shift&&state.selection.some(s=>s.kind===data.kind&&s.id===data.id))return;
        if(data.kind){setSelection(previous=>data.shift?[...previous.filter(s=>s.kind!==data.kind||s.id!==data.id),{kind:data.kind,id:data.id}]:[{kind:data.kind,id:data.id}]);setContentSelection(null);}
        else if(!data.shift)setSelection([]);
      }else if(data.purpose==='place'){
        const record=placementTarget(data.content||state.contentSelection,state.snapshot?.catalog||[]);
        if(record&&classes[record.kind as Kind]&&!state.preview?.available.includes(record.kind+'/'+record.id)){setError('새 콘텐츠는 리소스 적용 후 배치할 수 있습니다.');return;}
        if(record&&classes[record.kind as Kind])void mutate([{op:'entity.add',map:state.activeMap,kind:record.kind,type:Number(record.id),values:{x:data.placementSample?data.x:round(data.x),z:data.placementSample?data.z:round(data.z),...(record.kind==='object'?{}:{y:data.y})}}]);
      }else if(data.purpose==='terrain'&&state.snapshot?.map){
        const size=state.snapshot.map.size;void mutate([{op:'terrain.brush',map:state.activeMap,x:data.x/64+size/2,z:data.z/64+size/2,radius:state.radius,strength:state.strength,mode:state.brush}]);
      }
    };
    window.addEventListener('message',listener);return()=>window.removeEventListener('message',listener);
  },[engineReady,playing]);
  async function removeSelected(){const state=current.current;if(state.playing||dragLock.current)return;if(state.selection.length)await mutate(state.selection.map(s=>({op:'entity.remove',map:state.activeMap,...s})));setSelection([]);}
  async function duplicateSelected(){
    const state=current.current;if(state.playing||dragLock.current||!state.snapshot?.map)return;
    const entities=state.snapshot.map.entities;
    const nextIds:Partial<Record<Kind,number>>={},operations:Operation[]=[];
    for(const s of state.selection){
      const e=entities.find(e=>e.kind===s.kind&&e.id===s.id);if(!e)continue;
      const id=nextIds[e.kind]??Math.max(e.kind==='unit'?99:0,...entities.filter(v=>v.kind===e.kind).map(v=>v.id))+1;
      nextIds[e.kind]=id+1;
      const values=Object.fromEntries(Object.entries(e).filter(([key])=>!['kind','id','type','script'].includes(key)));
      operations.push({op:'entity.add',map:state.activeMap,kind:e.kind,id,type:e.type,values:{...values,x:e.x+32,z:e.z+32}});
      if(e.script)operations.push({op:'entity.script',map:state.activeMap,kind:e.kind,id,text:e.script});
    }
    if(operations.length)await mutate(operations);
  }
  useEffect(()=>{
    const handler=(event:KeyboardEvent)=>{
      if((event.target as HTMLElement).closest('input,textarea,.monaco-editor'))return;
      if((event.ctrlKey||event.metaKey)&&event.key.toLowerCase()==='s'){event.preventDefault();void saveAll();}
      if((event.ctrlKey||event.metaKey)&&event.key.toLowerCase()==='z'){event.preventDefault();void history(event.shiftKey);}
      if((event.ctrlKey||event.metaKey)&&event.key.toLowerCase()==='d'){event.preventDefault();void duplicateSelected();}
      if(event.key==='Delete')void removeSelected();
    };window.addEventListener('keydown',handler);return()=>window.removeEventListener('keydown',handler);
  },[snapshot,project,activeMap]);
  async function open(dir:string){
    await run(async()=>{const result=await request<Snapshot>({command:'inspect',project:dir});const last=window.studio.storage.getItem('studio.last-map:'+result.project);const map=last&&result.maps.includes(last)?last:result.maps[0]||'';setSelection([]);setContentSelection(null);setConflict(null);setShowDrafts(false);await refresh(result.project,map);});
  }
  async function newMap(){
    const name=await ask('새 맵 이름 (영문, 숫자, -, _)','island');if(!name)return;
    await run(async()=>{await request({command:'apply',project,expectRevision:snapshot?.revision,expectProjectHash:snapshot?.projectHash,operations:{op:'map.create',name,size:32}});await refresh(project,name);});
  }
  async function importMap(){
    const path=await window.studio.choose({map:true,title:'준비된 UTF-8 맵 가져오기'});if(!path)return;
    const name=await ask('프로젝트에서 사용할 맵 이름','imported-island');if(!name)return;
    await run(async()=>{await request({command:'apply',project,expectRevision:snapshot?.revision,expectProjectHash:snapshot?.projectHash,operations:{op:'map.import',name,path}});await refresh(project,name);});
  }
  async function cloneContent(){
    if(!selectedContent)return;const name=selectedContent.kind==='building'?selectedContent.name:await ask('새 콘텐츠 이름',selectedContent.name+' 복사');if(!name)return;
    await run(async()=>{
      const result=await request<{results:{kind:Kind;id:number|string}[]}>({command:'apply',project,expectRevision:snapshot?.revision,expectProjectHash:snapshot?.projectHash,operations:{op:'content.clone',kind:selectedContent.kind,source:selectedContent.id,properties:selectedContent.kind==='building'?{}:selectedContent.kind==='recipe'?{genname:name}:{name}}});
      await refresh();setContentSelection(result.results[0]);setSelection([]);
    });
  }
  async function saveScript(){
    await saveDrafts([scriptKey]);
  }
  const selectContent=(record:RecordInfo)=>{if(dragLock.current||current.current.playing)return;setContentSelection({kind:record.kind,id:record.id});setSelection([]);};
  const entityLabel=(entity:Entity)=>snapshot?.catalog.find(r=>r.kind===entity.kind&&r.id===entity.type)?.name||`${entity.kind} ${entity.type}`;
  const filtered=snapshot?.catalog.filter(r=>r.kind===filter&&(!query||(r.name+' '+r.id).toLowerCase().includes(query.toLowerCase())))||[];
  const worldEntities=snapshot?.map?.entities.filter(e=>(!worldKind||e.kind===worldKind)&&(!worldQuery||`${entityLabel(e)} ${e.id} ${e.type}`.toLocaleLowerCase().includes(worldQuery.toLocaleLowerCase())))||[];

  if(!snapshot)return <ProjectHome defaultPath={projectPath} defaultBase={base} recent={recent} busy={busy} error={error} onOpen={open}
    onCreate={async (dir,resource,name)=>{await run(async()=>{await request({command:'init',project:dir,base:resource,name});await refresh(dir,'');});}}
    onConnect={async (dir,resource)=>{const ok=await run(async()=>{await request({command:'connect',project:dir,base:resource});});if(ok)await open(dir);}}/>;

  return <div className="app" style={{'--bottom-height':bottomHeight+'px'} as React.CSSProperties}>
    {modal}
    {migrationOpen&&<MigrationPanel snapshot={snapshot} draftCount={drafts.length} onClose={()=>setMigrationOpen(false)} onChanged={()=>refresh()}/>}
    {showDrafts&&<div className="modal-backdrop"><section className="draft-dialog" role="dialog" aria-modal="true" aria-label="미저장 초안" onKeyDown={e=>{e.stopPropagation();if(e.key==='Escape')setShowDrafts(false);}}>
      <h2>미저장 초안 {drafts.length}개</h2><p>프로젝트 전체의 초안입니다. 테스트에는 저장을 마친 내용이 사용됩니다.</p>
      {conflict&&<div className="notice"><strong>다른 변경과 충돌했습니다. 초안은 보존했습니다.</strong><p>{conflict.conflicts.find(c=>c.message)?.message}</p>
        <div className="draft-comparison">{conflict.conflicts.filter(c=>!c.message).map(c=><section key={c.field}><h3>{c.field}</h3><div><label>편집 시작 값<pre>{JSON.stringify(c.base,null,2)}</pre></label><label>현재 저장 값<pre>{JSON.stringify(c.current,null,2)}</pre></label><label>내 초안<pre>{JSON.stringify(c.draft,null,2)}</pre></label></div></section>)}</div>
        <button onClick={()=>{setShowDrafts(false);void jumpTarget(conflict.target,conflict.conflicts[0]?.field==='text'?'script':conflict.conflicts[0]?.field);}}>충돌 대상으로 이동</button></div>}
      <div className="draft-list">{drafts.map(d=><div key={d.key}><button onClick={()=>{setShowDrafts(false);void jumpTarget(d.target,d.section==='script'?'script':undefined);}}>{d.label} · {d.section}</button><button disabled={busy||playing} onClick={()=>{removeDraft(d.key);if(conflict?.key===d.key)setConflict(null);}}>초안 취소</button><details><summary>내용 보기·복사</summary><pre>{JSON.stringify(readDraft(d.key),null,2)}</pre></details></div>)}</div>
      <div className="actions"><button onClick={()=>setShowDrafts(false)}>닫기</button><button className="primary" disabled={busy||playing||dragActive||!drafts.length} onClick={()=>void saveDrafts()}>모두 저장</button></div>
    </section></div>}
    {templatesOpen&&<TemplatePanel project={project} snapshot={snapshot} onClose={()=>setTemplatesOpen(false)} onCreated={async map=>{setSelection([]);setContentSelection(null);await refresh(project,map||activeMap);setTab('script');}}/>}
    <header className="topbar"><div className="brand">S<span>II</span><strong>STUDIO</strong><small>EXTENDED</small></div><div className="project-name">{snapshot.name}<span> / {activeMap||'맵을 선택하세요'}</span></div><span className="revision" title={snapshot.saveError||undefined}>r{snapshot.revision} · {snapshot.saveError?'저장 실패':snapshot.pendingSave?'저장 중…':'저장됨'} · 초안 {drafts.length}개</span><button disabled={busy} onClick={()=>void run(()=>window.studio.openFolder(project))}>폴더 열기</button><button disabled={busy||playing||dragActive} onClick={()=>setMigrationOpen(true)}>{snapshot.format!=='stranded-studio/3'?'맵 저장 형식 전환':'형식·백업'}</button><button onClick={()=>setShowDrafts(true)}>초안 {drafts.length}</button><button disabled={busy||playing||dragActive||(!drafts.length&&!snapshot.pendingSave&&!snapshot.saveError)} onClick={()=>void saveAll()}>모두 저장</button><button disabled={playing||dragActive||busy} onClick={()=>void run(async()=>{await request({command:'flush',project});setProjectPath(project);setSnapshot(null);setProject('');setActiveMap('');current.current.project='';current.current.snapshot=null;current.current.activeMap='';setPreview(null);current.current.preview=null;sceneIdentity.current='';session.current++;})}>프로젝트</button><button disabled={busy} onClick={()=>void run(async()=>{const out=await window.studio.choose({directory:true,title:'비어 있는 내보내기 폴더'});if(out){await request({command:'export',project,out});log('내보내기 완료: '+out);}})}>내보내기 ↗</button></header>
    <div className="toolbar"><div className="tool-group">{([['select','↖','선택'],['move','✥','이동'],['place','＋','배치'],['terrain','▱','지형']] as const).map(([key,icon,label])=><button key={key} className={tool===key?'active':''} disabled={playing||dragActive} onClick={()=>setTool(key)} title={label}><span>{icon}</span>{label}</button>)}</div><div className="toolbar-options">{tool==='terrain'?<><select value={brush} onChange={e=>setBrush(e.target.value)}><option value="raise">높이기</option><option value="lower">낮추기</option><option value="flatten">평탄화</option><option value="smooth">부드럽게</option></select><label>반경 <input type="number" min="1" max="32" value={radius} onChange={e=>setRadius(Number(e.target.value))}/></label><label>강도 <input type="number" step="0.001" min="0.001" max="0.1" value={strength} onChange={e=>setStrength(Number(e.target.value))}/></label></>:<label>스냅 <select value={snap} onChange={e=>setSnap(Number(e.target.value))}><option value="0">자유</option><option value="16">16</option><option value="32">32</option><option value="64">64</option></select></label>}</div>
      <div className="play-controls"><button className={resourcesPending?'resource-pending':''} disabled={busy||playing||dragActive||!activeMap} onClick={()=>void applyResources()} title="정의·모델·언어·폰트 변경을 모아서 적용합니다">{resourcesPending?'● 리소스 적용 필요':'리소스 적용'}</button><button className={playing?'playing':'primary'} disabled={playState==='stopping'||(!playing&&(!validPlay||busy||dragActive))} onClick={()=>void togglePlay()}>{playState==='stopping'?'■ 복원 중':playing?(playState==='playing'?'■ 정지':'■ 시작 취소'):'▶ 저장 후 테스트'}</button><button disabled={!validPlay||busy||playing||dragActive} onClick={()=>void togglePlay(true)} title="초안을 제외하고 저장된 내용만 실행">저장본 테스트</button></div>
      <button disabled={!snapshot.canUndo||busy||playing||dragActive} onClick={()=>void history()} title="Ctrl/Cmd+Z">↶</button><button disabled={!snapshot.canRedo||busy||playing||dragActive} onClick={()=>void history(true)} title="Ctrl/Cmd+Shift+Z">↷</button><button disabled={busy||playing||dragActive} onClick={()=>void run(()=>refresh())}>새로고침</button></div>
    <aside className="hierarchy panel"><div className="panel-title">월드<span>HIERARCHY</span></div><div className="map-controls"><select disabled={busy||playing||dragActive} value={activeMap} onChange={e=>{setSelection([]);void run(()=>refresh(project,e.target.value));}}><option value="">맵 선택</option>{snapshot.maps.map(name=><option key={name}>{name}</option>)}</select><button onClick={()=>void newMap()} disabled={busy||playing||dragActive} title="새 맵">＋</button><button onClick={()=>void importMap()} disabled={busy||playing||dragActive} title="맵 가져오기">↥</button></div>
      <div className="world-search"><input aria-label="월드 개체 검색" placeholder="이름·개체 ID·정의 ID" value={worldQuery} onChange={e=>setWorldQuery(e.target.value)}/><select aria-label="개체 종류" value={worldKind} onChange={e=>setWorldKind(e.target.value)}><option value="">모든 종류</option>{(['object','unit','item','info'] as Kind[]).map(k=><option key={k} value={k}>{labels[k]}</option>)}</select></div><button className="map-script" disabled={!activeMap||dragActive||playing} onClick={()=>{setSelection([]);setContentSelection(null);setTab('script');}}>맵 전체 스크립트</button><div className="hierarchy-list">{worldEntities.map(entity=><button key={entity.kind+entity.id} className={selection.some(s=>s.kind===entity.kind&&s.id===entity.id)?'entity selected':'entity'} disabled={dragActive||playing} onClick={e=>{setSelection(previous=>e.shiftKey?[...previous.filter(s=>s.kind!==entity.kind||s.id!==entity.id),{kind:entity.kind,id:entity.id}]:[{kind:entity.kind,id:entity.id}]);setContentSelection(null);}} onDoubleClick={()=>post('focus',{selection:{...entity,class:classes[entity.kind]}})}><span className={'kind-icon '+entity.kind}>{entity.kind==='object'?'◇':entity.kind==='unit'?'♟':entity.kind==='item'?'◈':'⚑'}</span><span>{entityLabel(entity)}</span><small>{entity.id}</small></button>)}</div>
      <div className="panel-footer"><span>{worldEntities.length} / {snapshot.map?.entities.length||0} 개체</span><button disabled={!selection.length||busy||playing||dragActive} onClick={()=>void duplicateSelected()}>복제</button><button disabled={!selection.length||busy||playing||dragActive} onClick={()=>void removeSelected()}>삭제</button></div></aside>
    <main className="scene panel"><div className="panel-title">장면<span>BLITZ · {engineStatus}</span><span className="play-status" role="status">{playState==='playing'?'플레이 중':playState==='starting'?'플레이로 전환 중…':playState==='stopping'?'편집 화면 복원 중…':''}</span><span className="scene-size">{snapshot.map?`${snapshot.map.size} × ${snapshot.map.size}`:''}</span></div>
      <div className="scene-viewport">
      {activeMap&&engineReady&&preview?<iframe key={frameVersion} ref={frame} src="/engine/index.html" title="Blitz 3D 월드" allow="autoplay; fullscreen"/>:<div className="scene-empty"><div className="island-symbol">⌁</div><h2>{snapshot.base!==base?'미리보기 리소스를 다시 연결하세요':activeMap?'장면을 준비할 수 없습니다':'첫 번째 섬을 만들어보세요'}</h2><p>{snapshot.base!==base?'프로젝트 화면에서 Studio의 기본 리소스를 연결하세요.':activeMap?(engineReady?'진단 탭에서 오류를 확인하세요.':'python scripts/studio.py engine build'):'왼쪽 + 버튼으로 새 맵을 만들거나 기존 .s2 맵을 가져올 수 있습니다.'}</p></div>}
      </div>
      <div className="scene-hint">{playing?'화면을 클릭하면 플레이 · Esc로 마우스 해제':tool==='place'?`${selectedContent?.name||'아래에서 콘텐츠를 선택하세요'} · 초록색 모델 미리보기 · 클릭: 배치 · Esc: 취소`:tool==='terrain'?'지형을 클릭하면 브러시 적용':tool==='move'?'축: 방향 이동 · 가운데: 지형 위 이동 · Esc: 취소 · Shift 클릭: 다중 선택':'우클릭 드래그: 시점 · WASD/QE: 이동 · F: 선택 개체 보기 · 휠: 줌'}</div>
    </main>
    <aside className="inspector panel"><div className="panel-title">속성<span>INSPECTOR</span></div>
      {selectedEntity?<><div className="selection-title"><span className="kind-badge">{labels[selectedEntity.kind]} #{selectedEntity.id}</span><h2>{entityLabel(selectedEntity)}</h2></div><fieldset disabled={busy||playing||dragActive}><PropertyForm key={project+activeMap+selectedEntity.kind+selectedEntity.id} draftKey={'studio.entity:'+project+':'+activeMap+':'+selectedEntity.kind+selectedEntity.id} revision={snapshot.revision} values={Object.fromEntries(Object.entries(selectedEntity).filter(([key])=>['x','y','z','yaw','pitch','health','health_max','count','vars'].includes(key)))} onSave={key=>saveDrafts([key])}/></fieldset><button className="wide" disabled={playing||dragActive} onClick={()=>{setContentSelection({kind:selectedEntity.kind,id:selectedEntity.type});setSelection([]);}}>콘텐츠 정의 열기 →</button><button className="wide" onClick={()=>setTab('script')}>개체 이벤트 스크립트</button></>:
      selectedContent?<><div className="selection-title"><span className="kind-badge">{labels[selectedContent.kind]} · {selectedContent.id}</span><h2>{selectedContent.name}</h2><p>{selectedContent.path.split('/').pop()}</p></div><ContentThumbnail project={project} version={snapshot.thumbnailVersion} record={selectedContent} large/><fieldset disabled={busy||playing||dragActive}><ContentForm key={project+selectedContent.kind+selectedContent.id} draftKey={'studio.content:'+project+':'+selectedContent.kind+selectedContent.id} revision={snapshot.revision} record={selectedContent} catalog={snapshot.catalog} assetPaths={assetPaths} fieldLocation={fieldLocation} onSave={key=>saveDrafts([key])}/></fieldset><button className="wide" disabled={busy||playing||dragActive} onClick={()=>void cloneContent()}>새 콘텐츠로 복제</button><button className="wide" onClick={()=>setTab('script')}>정의 스크립트 편집</button></>:
      <div className="inspector-empty"><h3>선택한 콘텐츠의 속성</h3><p>장면의 개체 또는 아래 콘텐츠 목록에서 항목을 선택하세요.</p>{activeMap&&<button onClick={()=>setTab('script')}>맵 전체 스크립트</button>}</div>}
      {fileTarget&&<References project={project} projectHash={snapshot.projectHash} target={fileTarget} onJump={(target,field,line)=>void jumpTarget(target,field,line)}/>}
    </aside>
    <section className="bottom panel"><div className="resize-handle" onPointerDown={e=>{const start=e.clientY,old=bottomHeight;e.currentTarget.setPointerCapture(e.pointerId);const move=(event:PointerEvent)=>setBottomHeight(Math.max(180,Math.min(600,old+start-event.clientY)));const up=()=>{window.removeEventListener('pointermove',move);window.removeEventListener('pointerup',up);};window.addEventListener('pointermove',move);window.addEventListener('pointerup',up);}}/>
      <div className="tabs"><button className={tab==='assets'?'active':''} onClick={()=>setTab('assets')}>콘텐츠 <small>PROJECT</small></button><button className={tab==='script'?'active':''} onClick={()=>setTab('script')}>스크립트 {(scriptDirty||fileDirty)?'●':''}</button><button className={tab==='console'?'active':''} onClick={()=>setTab('console')}>진단 <span className={snapshot.diagnostics.length?'error-count':'count'}>{snapshot.diagnostics.length}</span></button></div>
      {tab==='assets'?<div className="asset-browser"><nav>{(Object.keys(labels) as Kind[]).map(kind=><button key={kind} className={filter===kind?'active':''} onClick={()=>setFilter(kind)}>{labels[kind]}<small>{snapshot.catalog.filter(r=>r.kind===kind).length}</small></button>)}</nav><div className="asset-main"><div className="asset-toolbar"><button disabled={busy||playing||dragActive} onClick={()=>setTemplatesOpen(true)}>템플릿</button><input placeholder="이름 또는 ID 검색…" value={query} onChange={e=>setQuery(e.target.value)}/><button disabled={busy||playing||!selectedContent} onClick={()=>void cloneContent()}>선택 항목 복제</button><button disabled={busy||playing||dragActive} onClick={()=>void run(async()=>{const path=await window.studio.choose({title:'모델·이미지·오디오 가져오기'});if(!path)return;const filename=path.replaceAll('\\','/').split('/').pop();const destination=await ask('게임 리소스 경로','mods/Stranded II/gfx/'+filename);if(!destination)return;await request({command:'apply',project,expectRevision:snapshot.revision,expectProjectHash:snapshot.projectHash,operations:{op:'asset.import',path,destination}});await refresh();})}>에셋 가져오기</button></div>
        <div className="asset-grid">{filtered.map(record=><button key={record.kind+record.id} className={contentSelection?.kind===record.kind&&contentSelection.id===record.id?'asset selected':'asset'} disabled={playing||dragActive} draggable={!playing&&!dragActive&&(!classes[record.kind]||available.includes(record.kind+'/'+record.id))} title={classes[record.kind]&&!available.includes(record.kind+'/'+record.id)?'리소스 적용 후 배치 가능':undefined} onDragStart={event=>event.dataTransfer.setData('application/s2-content',JSON.stringify({kind:record.kind,id:record.id,placement:placementTarget(record,snapshot.catalog)}))} onClick={()=>selectContent(record)} onDoubleClick={()=>{selectContent(record);if(classes[record.kind]||record.kind==='building')setTool('place');}}><ContentThumbnail project={project} version={snapshot.thumbnailVersion} record={record}/><strong>{record.name}</strong><small>{classes[record.kind]&&!available.includes(record.kind+'/'+record.id)?'적용 대기':record.kind==='recipe'?'조합':`#${record.id}`}</small></button>)}</div></div></div>:
      <div className="console" style={{display:tab==='console'?undefined:'none'}}><RuntimeDebug entries={runtimeEntries} variables={runtimeVariables} onJump={(path,line)=>void jumpRuntime(path,line)}/>{snapshot.diagnostics.map((d,i)=><div key={i} className="diagnostic"><b>{d.code}</b><span>{d.message}</span><small>{d.path||d.target?<button onClick={()=>jumpDiagnostic(d)}>{d.path||d.location}{d.line?':'+d.line:''}</button>:d.location}</small></div>)}{!snapshot.diagnostics.length&&<p className="valid">✓ 콘텐츠 참조와 맵 구조 검사를 통과했습니다.</p>}{logs.map((line,i)=><pre key={i}>{line}</pre>)}</div>}
      <div style={{display:tab==='script'?'contents':'none'}}><ScriptWorkspace project={project} snapshot={snapshot} target={fileTarget} commands={commands} location={scriptLocation} onDirty={setFileDirty} onChanged={()=>refresh()} locked={busy||dragActive||playing} onSave={key=>saveDrafts([key])} onJump={(target,field,line)=>void jumpTarget(target,field,line)} draftVersion={draftVersion} inline={<div className="script-panel"><div className="script-toolbar"><span>{scriptTarget||'맵이나 콘텐츠를 선택하세요'} {scriptDirty&&inlineDraft.revision!==snapshot.revision?'· 이전 버전의 초안':''}</span><button disabled={!scriptDirty||busy} onClick={resetScript}>초안 취소</button><button className="primary" disabled={!scriptTarget||busy||playing||!scriptDirty} onClick={()=>void saveScript()}>스크립트 적용</button></div><ScriptEditor readOnly={busy||playing} value={script} onChange={changeScript} commands={commands} fileKey={scriptKey} location={inlineLocation} diagnostics={snapshot.diagnostics.filter(d=>d.field==='script'&&JSON.stringify(d.target)===JSON.stringify(fileTarget))}/></div>}/></div>
    </section>
    <footer className="statusbar"><span className={error?'status-error':''}>{busy?'작업 중…':error||(drafts.length?`미저장 초안 ${drafts.length}개 · 저장 후 테스트로 한 번에 반영하세요.`:'프로젝트가 저장되었습니다.')}</span><span>{selection.length?`${selection.length}개 선택 · `:''}Extended · GUI / CLI 공통 프로젝트</span></footer>
  </div>;
}

type DraftProps={draftKey:string;revision:number;onSave:(key:string)=>Promise<boolean>};
function PropertyForm({values,onSave,draftKey,revision}:DraftProps&{values:Record<string,unknown>}){
  const draft=useDraft(draftKey,values,revision);
  return <form className="property-form" onSubmit={async event=>{event.preventDefault();await onSave(draftKey);}}><h3>배치와 상태</h3>{Object.entries(draft.values).map(([key,value])=><label key={key} data-field={key}><span>{fields[key]||key}</span><input type={typeof value==='number'?'number':'text'} step="any" value={String(value)} onChange={e=>draft.update(key,typeof value==='number'?Number(e.target.value):e.target.value)}/></label>)}
    {draft.dirty&&<button type="button" onClick={draft.reset}>초안 취소{draft.revision!==revision?' · 외부 변경 있음':''}</button>}<button className="primary wide" type="submit" disabled={!draft.dirty}>속성 적용</button></form>;
}

function ContentForm({record,catalog,assetPaths,fieldLocation,onSave,draftKey,revision}:DraftProps&{record:RecordInfo;catalog:RecordInfo[];assetPaths:string[];fieldLocation?:{field:string;serial:number}}){
  const edit=useDraft(draftKey,record.properties,revision),draft=edit.values;
  const [advanced,setAdvanced]=useState(false),[newKey,setNewKey]=useState('');
  useEffect(()=>{if(fieldLocation)setAdvanced(true);},[fieldLocation]);
  const simple=['name','genname','description','model','icon','health','weight','damage','scale','group','behaviour','objectid','unitid','buildspace','buildingsite','atobject'];
  const keys=[...new Set([...Object.keys(draft),...(fieldLocation&&fieldLocation.field!=='script'?[fieldLocation.field]:[])])];
  const visible=keys.filter(key=>key!=='script'&&(advanced||simple.includes(key)||key==='req'||key==='gen'));
  const update=(key:string,values:string[])=>edit.update(key,values);
  const items=catalog.filter(r=>r.kind==='item');
  const referenceKinds:Record<string,Kind>={objectid:'object',unitid:'unit',buildingsite:'object',atobject:'object'};
  return <form className="property-form" onSubmit={async event=>{event.preventDefault();await onSave(draftKey);}}>
    <h3>콘텐츠 정의</h3>{visible.map(key=>{
      const values=draft[key]||[];
      if(key==='req'||key==='gen')return <div className="requirements" key={key} data-field={key}><h4>{key==='req'?'필요 재료':'생성 결과'}</h4>
        {values.map((value,index)=>{
          const [id,count='1',...rest]=value.split(',');
          const replace=(nextId:string,nextCount:string)=>update(key,values.map((v,i)=>i===index?[nextId,nextCount,...rest].join(','):v));
          return <div key={index} className="requirement"><ResourceInput label={key==='req'?'재료':'결과'} value={id.trim()} choices={contentChoices(items)} onChange={v=>replace(v,count)}/><input aria-label="수량" type="number" min="1" step="1" value={count} onChange={e=>replace(id,e.target.value)}/><button type="button" title="행 삭제" onClick={()=>update(key,values.filter((_,i)=>i!==index))}>×</button></div>;
        })}<button type="button" className="wide" onClick={()=>update(key,[...values,`${items[0]?.id||1},1`])}>＋ {key==='req'?'재료':'결과'} 추가</button></div>;
      let input;
      if(key==='description')input=<textarea rows={4} value={values[0]||''} onChange={e=>update(key,[e.target.value,...values.slice(1)])}/>;
      else if((key==='model'||key==='icon'||referenceKinds[key])&&values.length<=1){
        const choices=referenceKinds[key]?contentChoices(catalog.filter(r=>r.kind===referenceKinds[key])):assetPaths.filter(p=>key==='icon'?/\.(bmp|png|jpg|jpeg|tga)$/i.test(p):/\.(b3d|3ds|x|md2)$/i.test(p)).map(p=>({value:p,label:p}));
        input=<ResourceInput label={fields[key]||key} value={values[0]||''} choices={choices} onChange={v=>update(key,[v])}/>;
      }else input=<input value={values.join(' | ')} onChange={e=>update(key,e.target.value.split(' | '))}/>;
      return <label key={key} data-field={key}><span>{fields[key]||key}</span>{input}</label>;
    })}
    {(record.kind==='building'||record.kind==='recipe')&&!draft.req&&<button type="button" onClick={()=>update('req',[`${items[0]?.id||1},1`])}>재료 추가</button>}
    <button type="button" className="text-button" onClick={()=>setAdvanced(!advanced)}>{advanced?'기본 속성만 보기':'모든 속성 보기'}</button>
    {advanced&&<div className="input-row"><input placeholder="추가할 속성 키" value={newKey} onChange={e=>setNewKey(e.target.value)}/><button type="button" onClick={()=>{if(newKey&&newKey!=='script'){update(newKey,['']);setNewKey('');}}}>＋</button></div>}
    <button type="button" disabled={!edit.dirty} onClick={edit.reset}>초안 취소</button><button className="primary wide" type="submit" disabled={!edit.dirty}>정의 적용</button>
  </form>;
}
