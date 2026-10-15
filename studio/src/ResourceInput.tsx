import {useId,useState} from 'react';
import type {RecordInfo} from './api';

// Searchable suggestions also accept raw values, including unknown legacy values.
export function ResourceInput({value,onChange,choices,label}:{value:string;onChange:(value:string)=>void;choices:{value:string;label:string}[];label:string}){
  const id=useId(),[query,setQuery]=useState(''),[open,setOpen]=useState(false);
  const matches=choices.filter(c=>(c.label+' '+c.value).toLocaleLowerCase().includes(query.toLocaleLowerCase()));
  return <div className="resource-input"><div className="input-row"><input aria-label={label} value={value} onChange={e=>onChange(e.target.value)}/><button type="button" aria-expanded={open} aria-controls={id} onClick={()=>setOpen(!open)}>찾기</button></div>
    {open&&<div className="resource-options" id={id}><input aria-label={label+' 검색'} placeholder="이름·ID·경로 검색" value={query} onChange={e=>setQuery(e.target.value)}/><div>{matches.slice(0,100).map(c=><button type="button" key={c.value} title={c.value} onClick={()=>{onChange(c.value);setOpen(false);setQuery('');}}>{c.label}</button>)}{matches.length>100&&<small>상위 100개 · 검색어를 좁혀주세요.</small>}{!matches.length&&<small>검색 결과 없음 · 직접 입력할 수 있습니다.</small>}</div></div>}
  </div>;
}
export const contentChoices=(records:RecordInfo[])=>records.map(r=>({value:String(r.id),label:`${r.name} #${r.id}`}));
