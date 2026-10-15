import {useEffect,useState} from 'react';
import {AuthoringTarget,Reference,request} from './api';

export function References({project,projectHash,target,onJump}:{project:string;projectHash:string;target:AuthoringTarget;onJump:(target:AuthoringTarget,field?:string,line?:number)=>void}){
  const [rows,setRows]=useState<Reference[]>([]),[error,setError]=useState('');
  useEffect(()=>{
    let alive=true;setRows([]);setError('');
    void request<{references:Reference[]}>({command:'references',project,target}).then(v=>{if(alive)setRows(v.references);}).catch(e=>{if(alive)setError(String(e));});
    return()=>{alive=false;};
  },[project,projectHash,JSON.stringify(target)]);
  return <section className="references"><h3>사용처·스크립트 연결</h3>{error?<p>{error}</p>:rows.length?rows.map((r,i)=><button key={i} onClick={()=>onJump(r.target,r.field,r.line)}>{r.label}</button>):<p>확인된 참조 없음</p>}<small>동적으로 계산되는 참조는 스크립트 검색으로 확인하세요.</small></section>;
}
