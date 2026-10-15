import {useEffect,useState} from 'react';
import {draftEvent,readDraft,writeDraft,removeDraft} from './drafts';
import type {AuthoringTarget} from './api';

// Compare edited fields with their original values; unrelated changes may merge.
export function useDraft<T extends Record<string,unknown>>(key:string,values:T,revision:number,target?:AuthoringTarget){
  type Draft={changes:Partial<T>;revision:number;base?:T};
  function read():Draft{
    const stored=readDraft(key);
    if(stored?.changes)return stored;
    if(stored&&'text' in stored)return {changes:{text:stored.text} as unknown as Partial<T>,revision:stored.revision};
    return {changes:{},revision};
  }
  const [state,setState]=useState({key,draft:read()});
  const draft=state.key===key?state.draft:read();
  useEffect(()=>{
    const refresh=()=>setState({key,draft:read()});refresh();
    window.addEventListener(draftEvent,refresh);return()=>window.removeEventListener(draftEvent,refresh);
  },[key]);
  const dirty=Object.keys(draft.changes).length>0;
  const update=(name:keyof T,value:T[keyof T])=>{
    const base=dirty?draft.base:structuredClone(values),changes={...draft.changes,[name]:value};
    if(base&&JSON.stringify(base[name])===JSON.stringify(value))delete changes[name];
    const next={changes,revision:dirty?draft.revision:revision,...(base?{base}:{}),...(target?{target}:{})};
    if(Object.keys(changes).length)writeDraft(key,next);else removeDraft(key);
  };
  const reset=()=>removeDraft(key);
  return {values:{...values,...draft.changes},changes:draft.changes,revision:dirty?draft.revision:revision,dirty,update,reset};
}
