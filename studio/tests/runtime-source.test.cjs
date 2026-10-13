'use strict';
const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const source=(async()=>{
  const {transformWithOxc}=await import('vite');
  const filename=path.join(__dirname,'../src/runtimeSource.ts');
  const {code}=await transformWithOxc(fs.readFileSync(filename,'utf8'),filename);
  return import('data:text/javascript;base64,'+Buffer.from(code).toString('base64'));
})();
test('runtime errors map parser rows back through omitted blank lines and appended scripts',async()=>{
  const {locateRuntime}=await source;
  const lines=['on:use {','',' badcommand;','}'];
  const record={kind:'error',row:3,script:'// earlier\x1fon:use {\x1fbadcommand;\x1f}'};
  assert.deepEqual(locateRuntime(record,{files:[{path:'scripts/npc.s2s',lines}]}),{path:'scripts/npc.s2s',line:3});
});
test('ambiguous, truncated and dynamic code never claim an exact file position',async()=>{
  const {locateRuntime}=await source;
  const files=['a','b'].map(name=>({path:`scripts/${name}.s2s`,lines:['on:use {','badcommand;','}']}));
  const record={kind:'error',row:2,script:'on:use {\x1fbadcommand;\x1f}'};
  assert.equal(locateRuntime(record,{files}),undefined);
  assert.equal(locateRuntime({...record,script:''},{files}),undefined);
  assert.equal(locateRuntime({...record,script:'dynamic {\x1fbadcommand;\x1f}'},{files}),undefined);
});
