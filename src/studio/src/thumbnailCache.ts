import type {Kind,Thumbnail} from './api';

// id is reserved for the JSON Lines request/response envelope.
export type ThumbnailRequest={command:'thumbnail';project:string;kind:Kind;contentId:number|string};
type Listener=(value:Thumbnail)=>void;
type Task={key:string;input:ThumbnailRequest;version:string;listeners:Set<Listener>;started:boolean};

export function thumbnailKey(input:ThumbnailRequest,version:string){
  return JSON.stringify([input.project,version,input.kind,String(input.contentId)]);
}

// Keep IPC traffic bounded so scrolling cannot fill the editing command queue.
export function createThumbnailLoader(request:(input:ThumbnailRequest)=>Promise<Thumbnail>){
  const cache=new Map<string,Thumbnail>(),pending=new Map<string,Task>(),queue:Task[]=[];
  let active=0;
  function pump(){
    while(active<4&&queue.length){
      const task=queue.shift()!;
      if(!task.listeners.size){pending.delete(task.key);continue;}
      task.started=true;active++;
      void Promise.resolve().then(()=>request(task.input)).then(value=>{
        if(value.version===task.version){
          cache.set(task.key,value);
          if(cache.size>256)cache.delete(cache.keys().next().value!);
        }
        for(const listener of task.listeners)listener(value);
      },()=>{
        for(const listener of task.listeners)listener({version:task.version,status:'unavailable',reason:'request-failed',additionalResults:0});
      }).finally(()=>{pending.delete(task.key);active--;pump();});
    }
  }
  return {
    subscribe(input:ThumbnailRequest,version:string,listener:Listener,priority=false){
      const key=thumbnailKey(input,version),cached=cache.get(key);
      if(cached){cache.delete(key);cache.set(key,cached);listener(cached);return ()=>{};}
      let task=pending.get(key);
      if(!task){task={key,input,version,listeners:new Set(),started:false};pending.set(key,task);queue.push(task);}
      task.listeners.add(listener);
      if(priority&&!task.started){queue.splice(queue.indexOf(task),1);queue.unshift(task);}
      pump();
      return ()=>{task.listeners.delete(listener);};
    }
  };
}
