import type {AuthoringTarget,Kind,Operation} from './api';

export const draftEvent='studio-drafts';
export function writeDraft(key:string,value:unknown){window.studio.storage.setItem(key,JSON.stringify(value));window.dispatchEvent(new Event(draftEvent));}
export function removeDraft(key:string){window.studio.storage.removeItem(key);window.dispatchEvent(new Event(draftEvent));}
export function readDraft(key:string){
  const raw=window.studio.storage.getItem(key);if(!raw)return null;
  try{const value=JSON.parse(raw);return value&&typeof value==='object'&&!Array.isArray(value)?value:{invalid:raw};}catch{return {invalid:raw};}
}
export type DraftEntry={key:string;raw:string;target:AuthoringTarget;section:string;operation:Operation;label:string};
export function listDrafts(project:string):DraftEntry[]{
  if(!project)return [];
  const entries:DraftEntry[]=[];
  for(const type of ['entity','content','script','file']){
    const prefix=`studio.${type}:${project}:`;
    for(const key of window.studio.storage.keys(prefix)){
      const value=readDraft(key);if(!value)continue;
      const name=key.slice(prefix.length);let target:AuthoringTarget,section:string;
      if(type==='file'){target={scope:'file',path:name};section='file';}
      else if(type==='entity'){
        const at=name.indexOf(':'),map=name.slice(0,at),match=/^(object|unit|item|info):?(\d+)$/.exec(name.slice(at+1));
        target={scope:'entity',map,kind:(match?.[1]||'') as Kind,id:Number(match?.[2])};section='values';
      }else if(type==='content'){
        const match=/^(object|unit|item|info|building|recipe):?(.*)$/.exec(name);
        target={scope:'definition',kind:(match?.[1]||'') as Kind,id:match?.[2]||''};section='properties';
      }else{
        const parts=name.split('/'),kinds=['object','unit','item','info','building','recipe'];
        if(parts.length>1&&kinds.includes(parts[0]))target={scope:'definition',kind:parts[0] as Kind,id:parts.slice(1).join('/')};
        else if(parts.length===3)target={scope:'entity',map:parts[0],kind:parts[1] as Kind,id:Number(parts[2])};
        else target={scope:'map',map:name};
        section='script';
      }
      target=value.target||target;
      const operation:Operation={op:'draft.apply',key,target,section};
      if(section==='file')Object.assign(operation,{text:value.text,hash:value.hash,...('base' in value?{base:value.base}:{})});
      else if(section==='script'){
        operation.text=value.changes?.text??value.text;
        if('base' in value)operation.base=typeof value.base==='string'?value.base:value.base.text;
      }else Object.assign(operation,{changes:value.changes,...('base' in value?{base:value.base}:{})});
      const label=target.scope==='file'?target.path:target.scope==='map'?target.map:`${target.scope==='entity'?target.map+' / ':''}${target.kind} #${target.id}`;
      entries.push({key,raw:window.studio.storage.getItem(key)!,target,section,operation,label});
    }
  }
  return entries;
}

// A keystroke arriving during an async save remains a draft against the version just saved.
export function acknowledgeDraft(entry:DraftEntry,result:{hash?:string}){
  if(window.studio.storage.getItem(entry.key)===entry.raw){removeDraft(entry.key);return;}
  const current=readDraft(entry.key);if(!current)return;
  if(entry.section==='file')writeDraft(entry.key,{...current,hash:result.hash,base:entry.operation.text});
  else if(entry.section==='script')writeDraft(entry.key,{...current,base:{text:entry.operation.text}});
  else writeDraft(entry.key,{...current,base:{...(current.base||{}),...(entry.operation.changes as object)}});
}
