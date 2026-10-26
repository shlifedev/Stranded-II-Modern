import {useState} from 'react';

// Drafts survive selecting another record, external CLI edits and app restarts.
// Their original revision is kept, so applying one cannot overwrite newer work.
export function useDraft<T extends Record<string,unknown>>(key:string,values:T,revision:number){
  type Draft={changes:Partial<T>;revision:number};
  const [draft,setDraft]=useState<Draft>(()=>{
    try{return JSON.parse(window.studio.storage.getItem(key)||'null')||{changes:{},revision};}
    catch{return {changes:{},revision};}
  });
  const dirty=Object.keys(draft.changes).length>0;
  const update=(name:keyof T,value:T[keyof T])=>{
    const next={changes:{...draft.changes,[name]:value},revision:dirty?draft.revision:revision};
    window.studio.storage.setItem(key,JSON.stringify(next));setDraft(next);
  };
  const reset=()=>{window.studio.storage.removeItem(key);setDraft({changes:{},revision});};
  return {values:{...values,...draft.changes},changes:draft.changes,revision:dirty?draft.revision:revision,dirty,update,reset};
}
