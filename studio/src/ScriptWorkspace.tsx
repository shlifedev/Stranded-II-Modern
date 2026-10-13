import {ReactNode,useEffect,useRef,useState} from 'react';
import {Diagnostic,request,ScriptTarget,ScriptWorkspaceInfo,Snapshot} from './api';
import {ScriptEditor} from './ScriptEditor';
import {useTextDialog} from './useTextDialog';

type OpenFile={text:string;hash:string;remoteHash:string;dirty:boolean};
export type ScriptLocation={path:string;line:number;serial:number};
const sameTarget=(a:ScriptTarget,b:ScriptTarget|null)=>!!b&&a.scope===b.scope&&('map' in a?a.map:null)===('map' in b?b.map:null)&&('kind' in a?a.kind:null)===('kind' in b?b.kind:null)&&String('id' in a?a.id:'')===String('id' in b?b.id:'');

export function ScriptWorkspace({project,snapshot,target,inline,onChanged,commands,location,onDirty}:{project:string;snapshot:Snapshot;target:ScriptTarget|null;inline:ReactNode;onChanged:()=>Promise<void>;commands:{name:string;parameters:string}[];location?:ScriptLocation;onDirty:(dirty:boolean)=>void}){
  const {ask,modal}=useTextDialog();
  const [files,setFiles]=useState<Record<string,OpenFile>>({}),[active,setActive]=useState(''),[error,setError]=useState(''),[busy,setBusy]=useState(false),[query,setQuery]=useState(''),[matches,setMatches]=useState<ScriptWorkspaceInfo['matches']>([]),[line,setLine]=useState<{line:number;serial:number}>();
  const current=useRef(files);current.current=files;
  const binding=snapshot.scripts.bindings.find(b=>sameTarget(b.target,target));
  const key=(path:string)=>'studio.file:'+project+':'+path;
  const generation=useRef(0);
  useEffect(()=>{generation.current++;setFiles({});setActive('');setError('');},[project]);
  useEffect(()=>{onDirty(Object.values(files).some(f=>f.dirty));},[files]);
  async function read(path:string){
    const value=await request<ScriptWorkspaceInfo&{file:{text:string;hash:string}}>({command:'scripts',project,path});return value.file;
  }
  async function open(path:string,at=1){
    const gen=generation.current;
    try{
      if(!current.current[path]){
        const value=await read(path);if(gen!==generation.current)return;
        let draft:{text:string;hash:string}|null=null;
        try{draft=JSON.parse(window.studio.storage.getItem(key(path))||'null');}catch{}
        setFiles(old=>({...old,[path]:{text:draft?.text??value.text,hash:draft?.hash??value.hash,remoteHash:value.hash,dirty:!!draft}}));
      }
      setActive(path);setLine({line:at,serial:Date.now()});setError('');
    }catch(e){setError(String(e));}
  }
  useEffect(()=>{if(location)void open(location.path,location.line);},[location?.serial]);
  useEffect(()=>{if(binding)void open(binding.path);else setActive('');},[JSON.stringify(target),binding?.path]);
  useEffect(()=>{
    let alive=true;
    for(const path of Object.keys(current.current)){
      const info=snapshot.scripts.files.find(f=>f.path===path);
      if(!info){setFiles(old=>old[path]?{...old,[path]:{...old[path],remoteHash:''}}:old);continue;}
      if(info.hash===current.current[path].remoteHash)continue;
      void read(path).then(value=>{if(alive)setFiles(old=>!old[path]?old:{...old,[path]:old[path].dirty?{...old[path],remoteHash:value.hash}:{text:value.text,hash:value.hash,remoteHash:value.hash,dirty:false}});}).catch(e=>{if(alive)setError(String(e));});
    }
    return()=>{alive=false;};
  },[snapshot.scripts.version]);
  useEffect(()=>{
    let alive=true;
    const timer=setTimeout(()=>{void request<ScriptWorkspaceInfo>({command:'scripts',project,query}).then(v=>{if(alive)setMatches(v.matches);}).catch(e=>{if(alive)setError(String(e));});},250);
    return()=>{alive=false;clearTimeout(timer);};
  },[query,project,snapshot.scripts.version]);
  async function apply(operations:Record<string,unknown>[]){
    if(busy)return false;setBusy(true);setError('');
    try{const result=await request<{results:{hash?:string}[]}>({command:'apply',project,expectRevision:snapshot.revision,operations});await onChanged();return result;}
    catch(e){setError(String(e));return false;}finally{setBusy(false);}
  }
  function change(text:string){
    const f=current.current[active];if(!f)return;
    window.studio.storage.setItem(key(active),JSON.stringify({text,hash:f.hash}));
    setFiles(old=>({...old,[active]:{...f,text,dirty:true}}));
  }
  async function save(){
    const path=active,f=current.current[path];if(!f)return;
    if(!f.dirty||f.hash!==f.remoteHash)return;
    const result=await apply([{op:'script.write',path,text:f.text,expectedHash:f.hash}]);
    if(result){
      const value=await read(path),latest=current.current[path],hash=result.results[0].hash!;
      const draft=latest&&latest.text!==f.text;
      if(draft)window.studio.storage.setItem(key(path),JSON.stringify({text:latest.text,hash}));
      else window.studio.storage.removeItem(key(path));
      setFiles(old=>({...old,[path]:{text:draft?latest.text:value.text,hash:draft?hash:value.hash,remoteHash:value.hash,dirty:!!draft}}));
    }
  }
  useEffect(()=>{
    const handler=(e:KeyboardEvent)=>{if((e.ctrlKey||e.metaKey)&&e.key.toLowerCase()==='s'&&active&&(e.target as HTMLElement).closest('.script-workspace')){e.preventDefault();void save();}};
    window.addEventListener('keydown',handler);return()=>window.removeEventListener('keydown',handler);
  },[active,files,busy,snapshot.revision]);
  async function newFile(copy=false){
    const path=await ask(copy?'초안을 새 파일에 보관':'새 스크립트 경로','scripts/new_script.s2s');if(!path)return;
    if(await apply([{op:'script.write',path,text:copy?current.current[active]?.text||'':'on:start {\n}\n',expectedHash:null}]))await open(path);
  }
  async function extract(){
    if(!target)return;const path=await ask('기존 스크립트를 분리할 파일','scripts/'+('map' in target?target.map:target.kind+'_'+target.id)+'.s2s');if(!path)return;
    if(await apply([{op:'script.extract',path,target}]))await open(path);
  }
  async function reload(){
    const path=active;try{const value=await read(path);window.studio.storage.removeItem(key(path));setFiles(old=>({...old,[path]:{text:value.text,hash:value.hash,remoteHash:value.hash,dirty:false}}));}catch(e){setError(String(e));}
  }
  const file=files[active],info=snapshot.scripts.files.find(f=>f.path===active),conflict=file?.dirty&&file.hash!==file.remoteHash;
  return <div className="script-workspace">
    <aside className="script-explorer"><div className="script-actions"><button disabled={busy} onClick={()=>void newFile()}>＋ 파일</button><button disabled={busy||!target||!!binding} onClick={()=>void extract()}>파일로 분리</button></div>
      <input placeholder="파일 내용 검색…" value={query} onChange={e=>setQuery(e.target.value)}/>
      {query?matches.map((m,i)=><button key={i} className="script-tree-file" onClick={()=>void open(m.path,m.line)}><b>{m.path}:{m.line}</b><small>{m.text}</small></button>):snapshot.scripts.files.map(f=><div key={f.path}>
        <button className={'script-tree-file '+(active===f.path?'selected':'')} onClick={()=>void open(f.path)}>{f.path.replace(/^scripts\//,'')} {files[f.path]?.dirty?'●':''}</button>
        {active===f.path&&f.events.map((e,i)=><button className="script-event" key={i} onClick={()=>void open(f.path,e.line)}>on:{e.name} · {e.line}</button>)}
      </div>)}
    </aside>
    <div className="script-workbench"><div className="script-file-tabs"><button className={!active?'active':''} onClick={()=>setActive('')}>선택 대상</button>{Object.keys(files).map(path=><button key={path} className={active===path?'active':''} onClick={()=>setActive(path)}>{path.split('/').pop()} {files[path].dirty?'●':''}<span title="탭 닫기" onClick={e=>{e.stopPropagation();if(files[path].dirty){setError('초안을 저장하거나 버린 뒤 탭을 닫으세요.');return;}setFiles(old=>{const next={...old};delete next[path];return next;});if(active===path)setActive('');}}> ×</span></button>)}</div>
      {error&&<div className="notice">{error}</div>}
      {active&&file?<><div className="script-toolbar"><span title={info?.runtimePath}>{active} {file.dirty?'· 미저장 초안':''}</span><button disabled={busy||!file.dirty||conflict} onClick={()=>void save()}>파일 저장</button><button disabled={busy||!file.dirty} onClick={()=>void reload()}>초안 버리기</button><button disabled={busy||!target} onClick={()=>void apply([{op:'script.bind',path:active,target}])}>선택 대상에 연결</button><button disabled={busy||!file.dirty} onClick={()=>void newFile(true)}>다른 파일로 저장</button></div>
        {conflict&&<div className="notice">외부 파일이 바뀌었습니다. 초안을 다른 파일에 보관하거나 버리고 최신 파일을 여세요. <button onClick={()=>void reload()}>초안 버리고 다시 읽기</button></div>}
        {!file.remoteHash&&<div className="notice">파일이 삭제되었습니다. 열린 내용은 보존했습니다.</div>}
        <ScriptEditor value={file.text} onChange={change} commands={commands} fileKey={project+'/'+active} diagnostics={snapshot.scripts.diagnostics.filter(d=>d.path===active)} location={line}/>
        <div className="script-references">{info?.references.map((r,i)=><button key={i} onClick={()=>void open(r.path)}>↗ {r.path}</button>)}</div>
      </>:binding?<div className="script-bound"><p>연결된 원본: {binding.path}</p><button onClick={()=>void open(binding.path)}>파일 열기</button><button disabled={busy} onClick={()=>void apply([{op:'script.unbind',target}])}>연결 해제 · 현재 내용을 대상에 보관</button></div>:inline}
      {snapshot.scripts.diagnostics.length>0&&<div className="script-problems">{snapshot.scripts.diagnostics.map((d:Diagnostic,i)=><button key={i} onClick={()=>d.path&&void open(d.path,d.line)}>{d.path}:{d.line} · {d.message}</button>)}</div>}
    </div>{modal}
  </div>;
}
