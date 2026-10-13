import {useRef,useState} from 'react';

export function useTextDialog(){
  const [dialog,setDialog]=useState<{title:string;value:string}|null>(null);
  const resolve=useRef<((value:string|null)=>void)|null>(null);
  const ask=(title:string,value='')=>new Promise<string|null>(done=>{resolve.current=done;setDialog({title,value});});
  const close=(value:string|null)=>{resolve.current?.(value);resolve.current=null;setDialog(null);};
  const modal=dialog&&<div className="modal-backdrop"><form role="dialog" aria-modal="true" aria-label={dialog.title} className="text-dialog" onSubmit={e=>{e.preventDefault();close(dialog.value.trim());}} onKeyDown={e=>{e.stopPropagation();if(e.key==='Escape')close(null);}}>
    <label>{dialog.title}<input autoFocus value={dialog.value} onFocus={e=>e.target.select()} onChange={e=>setDialog({...dialog,value:e.target.value})}/></label>
    <div className="actions"><button type="button" onClick={()=>close(null)}>취소</button><button className="primary" disabled={!dialog.value.trim()}>확인</button></div>
  </form></div>;
  return {ask,modal};
}
