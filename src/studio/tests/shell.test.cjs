'use strict';
// Exercise the real server/backend/IPC with a window stub. No Electron or GUI starts.
const {test}=require('node:test');
const assert=require('node:assert/strict');
const vm=require('node:vm');
const fs=require('node:fs');
const os=require('node:os');
const path=require('node:path');
const {createRequire}=require('node:module');

test('desktop shell serves isolated assets, persists drafts and uses the JSON core',async t=>{
  const temp=fs.mkdtempSync(path.join(os.tmpdir(),'studio-shell-'));
  const handlers={},events={},appEvents={};let browser,loaded;
  const ready=new Promise(resolve=>loaded=resolve);
  class Window {
    constructor(){browser=this;this.webContents={setWindowOpenHandler(){},on(){},session:{setPermissionRequestHandler(){}}};}
    setMenuBarVisibility(){}
    async loadURL(url){this.url=url;loaded();}
  }
  const electron={BrowserWindow:Window,ipcMain:{handle:(name,fn)=>handlers[name]=fn,on:(name,fn)=>events[name]=fn},dialog:{},
    app:{whenReady:()=>Promise.resolve(),getPath:()=>temp,on:(name,fn)=>appEvents[name]=fn,quit(){throw new Error('Shell startup failed');}}};
  const filename=path.join(__dirname,'../electron/main.cjs'),realRequire=createRequire(filename);
  const context=vm.createContext({require:name=>name==='electron'?electron:realRequire(name),__dirname:path.dirname(filename),process,console,URL,Buffer});
  vm.runInContext(fs.readFileSync(filename,'utf8'),context,{filename});
  t.after(()=>{appEvents['before-quit']?.();fs.rmSync(temp,{recursive:true,force:true});});
  await ready;
  const event={sender:browser.webContents,senderFrame:{url:browser.url}};
  const response=await fetch(browser.url);
  assert.equal(response.status,200);
  assert.equal(response.headers.get('cross-origin-opener-policy'),'same-origin');
  assert.equal(response.headers.get('cross-origin-embedder-policy'),'require-corp');
  assert.match(await response.text(),/<div id="root">/);
  assert.equal((await fetch(browser.url+'/project.json')).status,404);
  const schema=await handlers['studio:request'](event,{command:'schema'});
  assert.equal(schema.ok,true);assert.equal(schema.result.protocol,'stranded-studio/1');
  const base=path.join(temp,'base'),project=path.join(temp,'project');
  fs.mkdirSync(path.join(base,'mods/Stranded II/sys'),{recursive:true});
  fs.mkdirSync(path.join(base,'mods/Stranded II/gfx'),{recursive:true});
  fs.writeFileSync(path.join(base,'mods/Stranded II/sys/items.inf'),'id=1\nname=Icon\nicon=gfx/icon.bmp\n');
  const bitmap=Buffer.alloc(58);bitmap.write('BM');bitmap.writeUInt32LE(58,2);bitmap.writeUInt32LE(54,10);
  bitmap.writeUInt32LE(40,14);bitmap.writeInt32LE(1,18);bitmap.writeInt32LE(1,22);bitmap.writeUInt16LE(1,26);bitmap.writeUInt16LE(24,28);bitmap[56]=255;
  fs.writeFileSync(path.join(base,'mods/Stranded II/gfx/icon.bmp'),bitmap);
  assert.equal((await handlers['studio:request'](event,{command:'init',project,base})).ok,true);
  const thumbnail=await handlers['studio:request'](event,{command:'thumbnail',project,kind:'item',contentId:1});
  assert.equal(thumbnail.ok,true,JSON.stringify(thumbnail));assert.equal(thumbnail.result.status,'ready');
  assert.match(thumbnail.result.dataUrl,/^data:image\/png;base64,/);assert.equal(thumbnail.result.revision,0);
  assert.throws(()=>handlers['studio:request']({...event,senderFrame:{url:'https://outside.test'}},{command:'schema'}),/Unknown/);
  events['studio:storage'](event,'set','studio.draft','unsaved script');
  assert.equal(event.returnValue.ok,true);
  const {openDraftStore}=require('../electron/drafts.cjs');
  const other=openDraftStore(temp);
  assert.equal(other('get','studio.draft'),'unsaved script');
  other('set','studio.other','다른 작업');
  events['studio:storage'](event,'remove','studio.draft');
  assert.equal(other('get','studio.draft'),null);
  events['studio:storage'](event,'get','studio.other');
  assert.equal(event.returnValue.value,'다른 작업');
  events['studio:storage'](event,'get','studio.draft');
  assert.equal(event.returnValue.value,null);
});

test('updated and discarded legacy drafts survive restart without rewriting the old profile',t=>{
  const {openDraftStore}=require('../electron/drafts.cjs');
  const profile=fs.mkdtempSync(path.join(os.tmpdir(),'studio-drafts-'));
  t.after(()=>fs.rmSync(profile,{recursive:true,force:true}));
  const original=JSON.stringify({'studio.old':'original','studio.keep':'다른 초안'});
  const file=path.join(profile,'authoring-drafts.json');
  fs.writeFileSync(file,original);
  const first=openDraftStore(profile),second=openDraftStore(profile);
  assert.equal(first('get','studio.keep'),'다른 초안');
  second('set','studio.old','');
  assert.equal(first('get','studio.old'),'');
  first('remove','studio.old');
  assert.equal(openDraftStore(profile)('get','studio.old'),null);
  second('set','studio.old','restored');
  assert.equal(first('get','studio.old'),'restored');
  assert.equal(fs.readFileSync(file,'utf8'),original);
  assert.equal(first('get','studio.keep'),'다른 초안');
  const rename=t.mock.method(fs,'renameSync',()=>{throw new Error('draft rename failed');});
  assert.throws(()=>first('set','studio.old','incomplete'),/draft rename failed/);
  rename.mock.restore();
  assert.equal(second('get','studio.old'),'restored');
  assert.deepEqual(fs.readdirSync(path.join(profile,'authoring-drafts')).filter(name=>name.endsWith('.tmp')),[]);
});
