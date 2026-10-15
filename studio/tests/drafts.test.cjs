const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const source=(async()=>{
  const {transformWithOxc}=await import('vite');
  const filename=path.join(__dirname,'../src/drafts.ts');
  const {code}=await transformWithOxc(fs.readFileSync(filename,'utf8'),filename);
  return import('data:text/javascript;base64,'+Buffer.from(code).toString('base64'));
})();
function storage(){
  const values=new Map(),events=[];
  global.window={studio:{storage:{keys:prefix=>[...values.keys()].filter(k=>k.startsWith(prefix)),getItem:key=>values.get(key)||null,setItem:(key,value)=>values.set(key,value),removeItem:key=>values.delete(key)}},dispatchEvent:event=>events.push(event.type)};
  return {values,events};
}
test('all persisted drafts, including closed tabs and prior sessions, belong to exactly one project',async()=>{
  const {listDrafts,writeDraft}=await source;storage();
  const project='C:/Projects/섬';
  writeDraft(`studio.content:${project}:recipe:mods/Stranded II/sys/combinations.inf#2`,{base:{req:['1,1']},changes:{req:['1,2']}});
  writeDraft(`studio.entity:${project}:island:unit:100`,{base:{x:0},changes:{x:64}});
  writeDraft(`studio.file:${project}:scripts/closed.s2s`,{text:'closed draft',hash:'hash'});
  writeDraft(`studio.script:${project}:item/unit/100`,{target:{scope:'entity',map:'item',kind:'unit',id:100},base:{text:''},changes:{text:'on:use {}'}});
  writeDraft('studio.file:C:/Projects/other:scripts/closed.s2s',{text:'other'});
  const drafts=listDrafts(project);
  assert.equal(drafts.length,4);
  assert.equal(drafts.find(d=>d.section==='properties').target.id,'mods/Stranded II/sys/combinations.inf#2');
  assert.deepEqual(drafts.find(d=>d.section==='script').target,{scope:'entity',map:'item',kind:'unit',id:100});
  assert.equal(drafts.find(d=>d.section==='script').operation.base,'');
});
test('save acknowledgments clear only the exact saved draft and preserve subsequent typing',async()=>{
  const {listDrafts,writeDraft,acknowledgeDraft,readDraft}=await source;storage();
  const key='studio.file:/project:scripts/a.s2s';
  writeDraft(key,{text:'first',hash:'old',base:'original'});let entry=listDrafts('/project')[0];
  writeDraft(key,{text:'second',hash:'old',base:'original'});
  acknowledgeDraft(entry,{hash:'saved'});
  assert.deepEqual(readDraft(key),{text:'second',hash:'saved',base:'first'});
  entry=listDrafts('/project')[0];acknowledgeDraft(entry,{hash:'second'});
  assert.equal(listDrafts('/project').length,0);
});
test('property acknowledgments update the merge base without dropping newer fields',async()=>{
  const {listDrafts,writeDraft,acknowledgeDraft,readDraft}=await source;storage();
  const key='studio.content:/project:item:1';
  writeDraft(key,{base:{name:['old'],health:['10']},changes:{name:['first']}});const entry=listDrafts('/project')[0];
  writeDraft(key,{base:{name:['old'],health:['10']},changes:{name:['second'],health:['20']}});
  acknowledgeDraft(entry,{});
  assert.deepEqual(readDraft(key).base,{name:['first'],health:['10']});
  assert.deepEqual(readDraft(key).changes,{name:['second'],health:['20']});
});
test('legacy and corrupt stored drafts remain visible instead of being silently discarded',async()=>{
  const {listDrafts,writeDraft,readDraft}=await source;const {values}=storage();
  writeDraft('studio.script:/project:island',{text:'old version',revision:2});
  values.set('studio.content:/project:item:1','broken json');
  const drafts=listDrafts('/project');assert.equal(drafts.length,2);
  assert.equal('base' in drafts.find(d=>d.section==='script').operation,false);
  assert.equal(readDraft('studio.content:/project:item:1').invalid,'broken json');
});
test('existing inspector storage keys without a kind/id separator retain their identity',async()=>{
  const {listDrafts,writeDraft}=await source;storage();
  writeDraft('studio.content:/project:object150',{base:{health:['100']},changes:{health:['123']}});
  writeDraft('studio.entity:/project:island:unit100',{base:{x:0},changes:{x:16}});
  writeDraft('studio.content:/project:recipemods/Stranded II/sys/combinations.inf#3',{base:{req:[]},changes:{req:['1,2']}});
  const entries=listDrafts('/project');
  assert.deepEqual(entries.find(d=>d.key.endsWith('object150')).target,{scope:'definition',kind:'object',id:'150'});
  assert.deepEqual(entries.find(d=>d.section==='values').target,{scope:'entity',map:'island',kind:'unit',id:100});
  assert.equal(entries.find(d=>d.target.kind==='recipe').target.id,'mods/Stranded II/sys/combinations.inf#3');
});
