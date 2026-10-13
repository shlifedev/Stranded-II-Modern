import {useEffect,useRef,useState} from 'react';
import {RecordInfo,request,Thumbnail} from './api';
import {createThumbnailLoader,thumbnailKey} from './thumbnailCache';

const loader=createThumbnailLoader(input=>request<Thumbnail>(input));
const symbols={building:'⌂',recipe:'⇌',object:'◇',unit:'♟',item:'◈',info:'⚑'};
const reasons:Record<string,string>={
  'no-icon':'등록된 아이콘이 없습니다.', 'missing-file':'아이콘 파일을 찾을 수 없습니다.',
  'missing-reference':'결과 콘텐츠를 찾을 수 없습니다.', 'invalid-frame':'아이콘 프레임이 올바르지 않습니다.',
  'image-too-large':'미리보기 크기 제한을 넘는 이미지입니다.', 'unreadable-image':'아이콘 파일을 읽을 수 없습니다.',
  'request-failed':'미리보기를 불러오지 못했습니다.'
};

export function ContentThumbnail({project,version,record,large=false}:{project:string;version:string;record:RecordInfo;large?:boolean}){
  const container=useRef<HTMLSpanElement>(null),[visible,setVisible]=useState(large);
  const [loaded,setLoaded]=useState<{key:string;value:Thumbnail}|null>(null);
  const input={command:'thumbnail' as const,project,kind:record.kind,contentId:record.id},key=thumbnailKey(input,version);
  const value=loaded?.key===key?loaded.value:null;
  const additional=record.kind==='recipe'?Math.max(0,(record.properties.gen?.length||0)-1):0;
  useEffect(()=>{
    if(large){setVisible(true);return;}
    const element=container.current;if(!element)return;
    const observer=new IntersectionObserver(entries=>setVisible(entries.some(entry=>entry.isIntersecting)),
      {root:element.closest('.asset-grid'),rootMargin:'64px'});
    observer.observe(element);
    return ()=>observer.disconnect();
  },[large]);
  useEffect(()=>{
    if(!visible)return;
    let current=true;
    const unsubscribe=loader.subscribe(input,version,result=>{
      if(current)setLoaded({key,value:result.version===version?result:
        {version,status:'unavailable',reason:'changed',additionalResults:additional}});
    },large);
    return ()=>{current=false;unsubscribe();};
  },[key,visible,large]);
  const ready=value?.status==='ready';
  const message=!value?'미리보기 불러오는 중…':ready?`${record.name} · 원본 ${value.width} × ${value.height}`:
    value.reason==='changed'?'저장된 콘텐츠 갱신을 기다리는 중…':reasons[value.reason]||'미리보기 없음';
  return <span ref={container} className={large?'content-preview':'content-thumbnail'} title={message}>
    <span className="thumbnail-image" aria-busy={!value}>
      {ready?<img src={value.dataUrl} alt={`${record.name} 아이콘`} draggable={false}
        onError={()=>setLoaded({key,value:{version,status:'unavailable',reason:'unreadable-image',additionalResults:additional}})}/>:
        <span className={'asset-icon '+record.kind} aria-label={message}>{symbols[record.kind]}</span>}
      {additional>0&&<span className="thumbnail-count" title={`결과 ${additional+1}종 · 첫 번째 결과 이미지`}>+{additional}</span>}
    </span>
    {large&&<span className="thumbnail-caption">{ready?`원본 ${value.width} × ${value.height} · 아이콘 확대`:message}
      {additional>0&&<span>첫 번째 결과 · 다른 결과 {additional}종</span>}</span>}
  </span>;
}
