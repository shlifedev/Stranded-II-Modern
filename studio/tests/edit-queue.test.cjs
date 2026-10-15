const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const source=(async()=>{
  const {transformWithOxc}=await import('vite');
  const filename=path.join(__dirname,'../src/editQueue.ts');
  const {code}=await transformWithOxc(fs.readFileSync(filename,'utf8'),filename);
  return import('data:text/javascript;base64,'+Buffer.from(code).toString('base64'));
})();
test('rapid placements keep click order and use the latest acknowledged revision',async()=>{
  const {EditQueue}=await source,queue=new EditQueue(),seen=[];let revision=0,release;
  const first=queue.enqueue(async()=>{await new Promise(resolve=>release=resolve);seen.push(revision++);return true;});
  const rest=Array.from({length:19},()=>queue.enqueue(async()=>{seen.push(revision++);return true;}));
  assert.equal(queue.active,true);assert.deepEqual(seen,[]);release();
  assert.ok((await Promise.all([first,...rest])).every(Boolean));
  assert.deepEqual(seen,Array.from({length:20},(_,i)=>i));assert.equal(queue.active,false);
});
test('a conflict cancels dependent clicks and permits a fresh edit after reconciliation',async()=>{
  const {EditQueue}=await source,queue=new EditQueue();let release,called=false;
  const failed=queue.enqueue(async()=>{await new Promise(resolve=>release=resolve);throw new Error('conflict');});
  const dependent=queue.enqueue(async()=>{called=true;return true;});release();
  assert.deepEqual(await Promise.all([failed,dependent]),[false,false]);assert.equal(called,false);
  assert.equal(await queue.enqueue(async()=>true),true);
});
