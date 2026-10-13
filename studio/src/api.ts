export type Kind = 'object'|'unit'|'item'|'info'|'building'|'recipe';
export type RecordInfo = {kind:Kind;id:number|string;name:string;path:string;properties:Record<string,string[]>};
export type Thumbnail = {version:string;additionalResults:number;revision?:number} & (
  {status:'ready';dataUrl:string;source:string;width:number;height:number} |
  {status:'unavailable';reason:string}
);
export type Entity = {kind:Kind;id:number;type:number;x:number;z:number;y?:number;yaw:number;pitch?:number;health?:number;health_max?:number;count?:number;vars?:string;script:string};
export type Diagnostic = {severity:string;code:string;message:string;location:string};
export type Snapshot = {name:string;project:string;base:string;revision:number;thumbnailVersion:string;maps:string[];activeMap:string|null;catalog:RecordInfo[];baseMaps:string[];assets:string[];canUndo:boolean;canRedo:boolean;
  map:{size:number;worldSize:number;entities:Entity[];script:string;attachments:string[]}|null;diagnostics:Diagnostic[]};
export type Operation = Record<string,unknown> & {op:string};
export type Preview = {files:Record<string,string>;map:string;revision:number;mode:'full'|'delta';token:string;baseToken?:string;available:string[];resourcesPending:boolean;resourcesRequired:boolean;patch?:string;restorePose?:number[]};
declare global {
  interface Window {studio:{
    storage:{getItem:(key:string)=>string|null;setItem:(key:string,value:string)=>void;removeItem:(key:string)=>void};
    request:(value:Record<string,unknown>)=>Promise<{ok:boolean;result:unknown;error?:{message:string}}>;
    choose:(options:{directory?:boolean;map?:boolean;title?:string})=>Promise<string|null>;
    environment:()=>Promise<{root:string;base:string;engineReady:boolean}>;
  }}
}
export async function request<T>(value:Record<string,unknown>):Promise<T>{
  const response=await window.studio.request(value);
  if(!response.ok)throw new Error(response.error?.message||'Studio request failed');
  return response.result as T;
}
export const labels:Record<Kind,string>={object:'오브젝트',unit:'유닛',item:'아이템',info:'맵 정보',building:'건축',recipe:'레시피'};
export const classes:Partial<Record<Kind,number>>={object:1,unit:2,item:3,info:4};
