import {RuntimeEntry,RuntimeRecord} from './runtimeSource';
export function RuntimeDebug({entries,variables,onJump}:{entries:RuntimeEntry[];variables:RuntimeRecord|null;onJump:(path:string,line:number)=>void}){
  return <div className="runtime-debug"><div><h3>실행 이벤트·오류 · 최근 300개</h3><div className="runtime-events">{entries.map((r,i)=><button className={r.kind==='error'?'runtime-error':''} key={i} disabled={!r.path} onClick={()=>r.path&&onJump(r.path,r.line||1)}>
    {r.kind==='error'?'오류':'이벤트'} · {r.class}/{r.id} · on:{r.event} {r.info} {r.message}
    {r.path?<small> ↗ {r.path}:{r.line}</small>:r.kind==='error'?<small> · 파서 행 {r.row}, 열 {r.column} · 파일 위치 미확정</small>:null}
    {r.code&&<pre>{r.code}</pre>}
  </button>)}</div></div><div><h3>변수 · 읽기 전용 · 선택 {variables?.class||0}/{variables?.id||0}</h3><div className="runtime-variables"><table><tbody>{variables?.values?.map(([scope,key,value],i)=><tr key={i}><td>{scope==='global'?'전역':'선택'}</td><td>${key}</td><td>{value}</td></tr>)}</tbody></table><small>최대 256개 · 긴 값은 1,024자까지 표시</small></div></div></div>;
}
