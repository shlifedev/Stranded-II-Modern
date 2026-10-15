import {useEffect,useState} from 'react';

export type RecentProject={path:string;name:string;map:string};
export function ProjectHome({defaultPath,defaultBase,recent,busy,error,onOpen,onCreate,onConnect}:{
  defaultPath:string;defaultBase:string;recent:RecentProject[];busy:boolean;error:string;
  onOpen:(path:string)=>Promise<void>;onCreate:(path:string,base:string,name:string)=>Promise<void>;
  onConnect:(path:string,base:string)=>Promise<void>;
}){
  const [path,setPath]=useState(defaultPath),[base,setBase]=useState(defaultBase),[name,setName]=useState('My Extended World'),[folderError,setFolderError]=useState('');
  useEffect(()=>{setPath(defaultPath);},[defaultPath]);useEffect(()=>{setBase(defaultBase);},[defaultBase]);
  return <div className="welcome"><div className="welcome-mark">S<span>II</span></div><h1>Stranded II Studio</h1>
    <p className="welcome-copy">프로젝트 폴더에서 섬과 콘텐츠를 만들고 외부 Git 도구로 관리하세요.</p>
    <section className="welcome-form"><fieldset disabled={busy}>
      <label>프로젝트 이름<input value={name} onChange={e=>setName(e.target.value)}/></label>
      <label>프로젝트 폴더<div className="input-row"><input value={path} onChange={e=>{setPath(e.target.value);setFolderError('');}}/><button onClick={async()=>{const p=await window.studio.choose({directory:true,title:'프로젝트 폴더'});if(p){setPath(p);setFolderError('');}}}>찾기</button><button disabled={!path} onClick={()=>void window.studio.openFolder(path).catch(e=>setFolderError(String(e)))}>폴더 열기</button></div></label>
      <label>이 PC의 기본 리소스<div className="input-row"><input value={base} onChange={e=>setBase(e.target.value)}/><button onClick={async()=>{const p=await window.studio.choose({directory:true,title:'준비된 기본 리소스'});if(p)setBase(p);}}>찾기</button></div></label>
      <div className="actions"><button disabled={!path} onClick={()=>void onOpen(path)}>프로젝트 열기</button><button className="primary" disabled={!path||!base} onClick={()=>void onCreate(path,base,name)}>프로젝트 만들기</button></div>
      <button className="wide" disabled={!path||!base} onClick={()=>void onConnect(path,base)}>기존 프로젝트의 리소스 다시 연결하고 열기</button>
    </fieldset>
    <p className="muted">새 프로젝트는 맵마다 현재 상태를 JSON 하나에 저장합니다. Git 저장소나 문서가 있는 폴더에도 만들 수 있습니다. 공용 리소스는 프로젝트와 같은 버전을 연결하세요. 기존 프로젝트는 자동 변환하지 않습니다.</p>
    <p className="muted">3D 미리보기는 Studio에 포함된 기본 리소스를 사용합니다: {defaultBase}</p>
    {(error||folderError)&&<pre className="error">{error||folderError}</pre>}</section>
    {recent.length>0&&<section className="recent-projects"><h2>최근 프로젝트</h2>{recent.map(p=><button key={p.path} disabled={busy} onClick={()=>{setPath(p.path);void onOpen(p.path);}}><strong>{p.name}</strong><small>{p.path}{p.map?' · '+p.map:''}</small></button>)}</section>}
  </div>;
}
