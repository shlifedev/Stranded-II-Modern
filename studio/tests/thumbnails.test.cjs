'use strict';
const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
// Use the app's transformer on Node 22.12, before native TS loading is default.
const cacheModule=(async()=>{
  const {transformWithOxc}=await import('vite');
  const filename=path.join(__dirname,'../src/thumbnailCache.ts');
  const {code}=await transformWithOxc(fs.readFileSync(filename,'utf8'),filename);
  return import('data:text/javascript;base64,'+Buffer.from(code).toString('base64'));
})();
const tick=()=>new Promise(resolve=>setImmediate(resolve));
const input=(project='one',contentId=1)=>({command:'thumbnail',project,kind:'item',contentId});
const result=version=>({version,revision:1,status:'ready',dataUrl:'data:image/png;base64,test',source:'icon.bmp',width:40,height:40,additionalResults:0});

test('thumbnail requests are shared, bounded and cancelled when no longer visible',async()=>{
  const {createThumbnailLoader}=await cacheModule;
  const calls=[],finish=[];
  const loader=createThumbnailLoader(value=>{calls.push(value);return new Promise(resolve=>finish.push(resolve));});
  let first=0,second=0;
  loader.subscribe(input(),'resources-1',()=>first++);
  loader.subscribe(input(),'resources-1',()=>second++);
  for(let id=2;id<=4;id++)loader.subscribe(input('one',id),'resources-1',()=>{});
  const cancel=loader.subscribe(input('one',5),'resources-1',()=>assert.fail('cancelled thumbnail delivered'));
  loader.subscribe(input('one',6),'resources-1',()=>{});
  loader.subscribe(input('one',7),'resources-1',()=>{},true);
  cancel();await tick();assert.equal(calls.length,4);
  finish[0](result('resources-1'));await tick();assert.equal(first,1);assert.equal(second,1);assert.equal(calls[4].contentId,7);
  finish[1](result('resources-1'));await tick();assert.equal(calls[5].contentId,6);
  finish.slice(2).forEach(resolve=>resolve(result('resources-1')));await tick();
  assert.equal(calls.some(value=>value.contentId===5),false);
  loader.subscribe(input(),'resources-1',()=>first++);assert.equal(first,2);assert.equal(calls.length,6);
});

test('project/resource-version isolation and unsubscribe prevent stale delivery',async()=>{
  const {createThumbnailLoader}=await cacheModule;
  const finish=[];let delivered=0;
  const loader=createThumbnailLoader(()=>new Promise(resolve=>finish.push(resolve)));
  const cancel=loader.subscribe(input(),'resources-1',()=>assert.fail('old selection updated'));
  loader.subscribe(input('two'),'resources-1',()=>delivered++);
  loader.subscribe(input(),'resources-2',()=>delivered++);
  cancel();await tick();assert.equal(finish.length,3);
  finish[0](result('resources-1'));finish[1](result('resources-1'));finish[2](result('resources-3'));await tick();assert.equal(delivered,2);
  loader.subscribe(input(),'resources-2',()=>{});await tick();assert.equal(finish.length,4,'mismatched response must not be cached');
  finish[3](result('resources-2'));await tick();
});

test('failed image requests allow queued work and subsequent retries',async()=>{
  const {createThumbnailLoader}=await cacheModule;
  let calls=0,value;
  const loader=createThumbnailLoader(async()=>{calls++;if(calls===1)throw Error('offline');return result('resources-1');});
  loader.subscribe(input(),'resources-1',v=>value=v);await tick();assert.equal(value.reason,'request-failed');
  loader.subscribe(input(),'resources-1',v=>value=v);await tick();assert.equal(value.status,'ready');assert.equal(calls,2);
});

test('map revision changes reuse the resource cache including an in-flight request',async()=>{
  const {createThumbnailLoader}=await cacheModule;
  let calls=0,finish,latest;
  const loader=createThumbnailLoader(()=>{calls++;return new Promise(resolve=>finish=resolve);});
  const cancel=loader.subscribe(input(),'same-icons',()=>assert.fail('unmounted tile updated'));
  await tick();cancel();
  loader.subscribe(input(),'same-icons',value=>latest=value);
  finish({...result('same-icons'),revision:19});await tick();
  assert.equal(latest.revision,19);assert.equal(calls,1);
  loader.subscribe(input(),'same-icons',value=>latest=value);
  await tick();assert.equal(calls,1,'placing another entity must not reload icons');
  loader.subscribe(input(),'changed-icons',value=>latest=value);
  await tick();assert.equal(calls,2,'saved content edits must refresh icons');
  finish({...result('changed-icons'),revision:20});await tick();
  assert.equal(latest.version,'changed-icons');
});
