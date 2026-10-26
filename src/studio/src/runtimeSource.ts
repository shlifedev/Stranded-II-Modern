import type {ScriptSources} from './api';
export type RuntimeRecord={kind:'event'|'error'|'variables';event?:string;info?:string;message?:string;code?:string;script?:string;class:number;id:number;row?:number;column?:number;values?:[string,string,string][]};
export type RuntimeEntry=RuntimeRecord&{path?:string;line?:number};

// The parser's row counts omit blank lines. Only expose a file position when
// the saved source provides an unambiguous match; dynamic scripts keep context.
export function locateRuntime(record:RuntimeRecord,sources?:ScriptSources):{path:string;line:number}|undefined{
  if(!sources||record.kind!=='error'||!record.script||!record.row)return;
  const parsed=record.script.split('\x1f').map(s=>s.trim());
  const hits:{path:string;line:number}[]=[];
  for(const f of sources.files){
    const lines=f.lines.map((text,i)=>({text:text.trim(),line:i+1})).filter(r=>r.text);
    if(!lines.length)continue;
    for(let start=0;start+lines.length<=parsed.length;start++){
      const at=record.row-1-start;
      if(at>=0&&at<lines.length&&lines.every((r,i)=>r.text===parsed[start+i]))hits.push({path:f.path,line:lines[at].line});
    }
  }
  if(hits.length===1)return hits[0];
}
