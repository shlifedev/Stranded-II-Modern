import {useEffect,useState} from 'react';
import {request,Snapshot} from './api';

type Review={changedFiles:string[];projectHash?:string;verified?:Record<string,number>;backup?:string;alreadyConverted?:boolean};
export function MigrationPanel({snapshot,draftCount,onClose,onChanged}:{snapshot:Snapshot;draftCount:number;onClose:()=>void;onChanged:()=>Promise<void>}){
  const legacy=snapshot.format!=='stranded-studio/3';
  const [restore,setRestore]=useState(!legacy);
  const [backups,setBackups]=useState<{id:string;created:string}[]>([]),[backup,setBackup]=useState('');
  const [review,setReview]=useState<Review|null>(null),[error,setError]=useState(''),[busy,setBusy]=useState(false),[complete,setComplete]=useState(false);
  useEffect(()=>{void request<{backups:{id:string;created:string}[]}>({command:'migrations',project:snapshot.project}).then(r=>{setBackups(r.backups);setBackup(r.backups[0]?.id||'');}).catch(e=>setError(String(e)));},[]);
  useEffect(()=>{if(!complete)setReview(null);},[backup,restore,snapshot.projectHash]);
  async function perform(dryRun:boolean){
    setBusy(true);setError('');
    try{
      if(draftCount)throw new Error('미저장 초안을 먼저 저장하거나 취소하세요. 초안은 자동으로 삭제하지 않습니다.');
      const result=await request<Review>({command:restore?'restore-migration':'migrate',project:snapshot.project,backup,dryRun,expectProjectHash:dryRun?snapshot.projectHash:review?.projectHash||snapshot.projectHash});
      setReview(result);
      if(!dryRun){setComplete(true);await onChanged();}
    }catch(e){setError(String(e));}finally{setBusy(false);}
  }
  return <div className="modal-backdrop"><section className="template-dialog" role="dialog" aria-modal="true" aria-label="프로젝트 형식 전환" onKeyDown={e=>{e.stopPropagation();if(e.key==='Escape'&&!busy)onClose();}}>
    <h2>{restore?'프로젝트 변환 백업':'맵별 상태 저장으로 전환'}</h2>
    <p>{restore?'변환 이후 변경이 없는 경우에만 백업으로 이전 형식을 복원할 수 있습니다.':'맵마다 개체·배치의 현재 상태를 JSON 하나에 저장합니다. 지형·첨부·스크립트는 보존하며, 변환 전후 실행용 데이터를 비교하고 원본을 백업합니다.'}</p>
    {legacy&&!complete&&<button disabled={busy} onClick={()=>setRestore(!restore)}>{restore?'새 형식으로 전환하기':'기존 변환 백업 보기'}</button>}
    {draftCount>0&&<p className="notice">미저장 초안 {draftCount}개가 있습니다. 모두 저장하거나 필요한 내용을 보관한 뒤 초안을 취소하세요.</p>}
    {restore&&<label>변환 백업<select disabled={busy||complete} value={backup} onChange={e=>setBackup(e.target.value)}>{backups.length?backups.map(b=><option key={b.id} value={b.id}>{new Date(b.created).toLocaleString()} · {b.id.slice(0,8)}</option>):<option value="">변환 백업 없음</option>}</select></label>}
    {error&&<pre className="error">{error}</pre>}
    {review&&<><p>{complete?'완료되었습니다.':`${review.changedFiles.length}개 파일 변경 예정`}{review.backup&&` · 백업 ${review.backup}`}</p>{review.verified&&<p>변환 전후 데이터 비교 통과 · 맵 {review.verified.maps}개 · 정의 {review.verified.definitions}개 · 스크립트 {review.verified.scripts}개</p>}<pre className="template-review">{review.changedFiles.join('\n')}</pre></>}
    <div className="actions"><button disabled={busy} onClick={onClose}>닫기</button>{!complete&&<><button disabled={busy||draftCount>0||(restore&&!backup)} onClick={()=>void perform(true)}>사전검사</button><button className="primary" disabled={busy||draftCount>0||!review} onClick={()=>void perform(false)}>{restore?'백업으로 복원':'백업하고 전환'}</button></>}</div>
  </section></div>;
}
