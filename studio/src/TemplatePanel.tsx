import {useState} from 'react';
import {request,Snapshot} from './api';
import {ResourceInput,contentChoices} from './ResourceInput';

export function TemplatePanel({project,snapshot,onClose,onCreated}:{project:string;snapshot:Snapshot;onClose:()=>void;onCreated:(map?:string)=>Promise<void>}){
  const [kind,setKind]=useState('scenario'),[slug,setSlug]=useState('island_story'),[name,setName]=useState('섬의 이야기');
  const items=snapshot.catalog.filter(r=>r.kind==='item'),units=snapshot.catalog.filter(r=>r.kind==='unit'),objects=snapshot.catalog.filter(r=>r.kind==='object');
  const [source,setSource]=useState(String(items[0]?.id||'')),[npc,setNpc]=useState(String(units.find(r=>r.id===40)?.id||units[0]?.id||'')),[result,setResult]=useState(String(items[0]?.id||''));
  const [ingredients,setIngredients]=useState(items.slice(0,2).map(r=>({id:String(r.id),count:1}))),[review,setReview]=useState<{key:string;text:string}|null>(null),[busy,setBusy]=useState(false),[error,setError]=useState('');
  const operation={op:'template.create',template:kind,slug,name,source:Number(kind==='npc'?npc:source),npcSource:Number(npc),result:Number(result),ingredients:ingredients.map(r=>[Number(r.id),r.count])};
  const key=JSON.stringify({operation,projectHash:snapshot.projectHash});
  async function submit(dryRun:boolean){
    setBusy(true);setError('');
    try{
      const value=await request<{results:{map?:string}[];diagnostics:unknown[]}>({command:'apply',project,expectRevision:snapshot.revision,expectProjectHash:snapshot.projectHash,operations:[operation],dryRun});
      if(dryRun)setReview({key,text:JSON.stringify(value,null,2)});
      else {await onCreated(value.results[0]?.map);onClose();}
    }catch(e){setError(String(e));}finally{setBusy(false);}
  }

  return <div className="modal-backdrop"><section className="template-dialog" role="dialog" aria-modal="true" aria-label="콘텐츠 템플릿" onKeyDown={e=>{e.stopPropagation();if(e.key==='Escape'&&!busy)onClose();}}>
    <h2>콘텐츠 템플릿</h2><p>기존 모델을 사용해 정의·스크립트·번역을 함께 만듭니다. 생성 전에 ID와 변경 내용을 확인하세요.</p>
    <fieldset disabled={busy}><label>종류<select value={kind} onChange={e=>{setKind(e.target.value);setResult(String((e.target.value==='building'?objects:items)[0]?.id||''));}}>{[['scenario','치료제 전달 시나리오'],['item','회복 아이템'],['recipe','제작법'],['building','건축'],['npc','NPC 상호작용']].map(([v,n])=><option key={v} value={v}>{n}</option>)}</select></label>
      <label>식별자<input value={slug} onChange={e=>setSlug(e.target.value)} placeholder="영문으로 시작 · 영문, 숫자, _"/></label><label>표시 이름<input value={name} onChange={e=>setName(e.target.value)}/></label>
      {['scenario','item'].includes(kind)&&<label>아이템 원본<ResourceInput label="아이템 원본" value={source} onChange={setSource} choices={contentChoices(items)}/></label>}
      {['scenario','npc'].includes(kind)&&<label>NPC 원본<ResourceInput label="NPC 원본" value={npc} onChange={setNpc} choices={contentChoices(units)}/></label>}
      {['recipe','building'].includes(kind)&&<label>생성 결과<ResourceInput label="생성 결과" value={result} onChange={setResult} choices={contentChoices(kind==='building'?objects:items)}/></label>}
      {['scenario','recipe','building'].includes(kind)&&<div><h3>재료</h3>{ingredients.map((r,i)=><div className="input-row" key={i}><ResourceInput label="재료" value={r.id} onChange={id=>setIngredients(old=>old.map((v,j)=>i===j?{...v,id}:v))} choices={contentChoices(items)}/><input aria-label="재료 수량" type="number" min="1" value={r.count} onChange={e=>setIngredients(old=>old.map((v,j)=>i===j?{...v,count:Number(e.target.value)}:v))}/><button onClick={()=>setIngredients(old=>old.filter((_,j)=>i!==j))}>삭제</button></div>)}<button onClick={()=>setIngredients(old=>[...old,{id:String(items[0]?.id||''),count:1}])}>재료 추가</button></div>}
    </fieldset>
    {error&&<p className="error">{error}</p>}{review?.key===key&&<pre className="template-review">{review.text}</pre>}
    <div className="actions"><button disabled={busy} onClick={onClose}>닫기</button><button disabled={busy} onClick={()=>void submit(true)}>변경 미리보기</button><button className="primary" disabled={busy||review?.key!==key} onClick={()=>void submit(false)}>생성</button></div>
  </section></div>;
}
