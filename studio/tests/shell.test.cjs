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
  const handlers={},events={},appEvents={},windowEvents={};let browser,loaded,closed=false;
  const ready=new Promise(resolve=>loaded=resolve);
  class Window {
    constructor(){browser=this;this.webContents={setWindowOpenHandler(){},on(){},session:{setPermissionRequestHandler(){}}};}
    setMenuBarVisibility(){}
    on(name,fn){windowEvents[name]=fn;}
    close(){closed=true;}
    async loadURL(url){this.url=url;loaded();}
  }
  const opened=[];
  const electron={BrowserWindow:Window,ipcMain:{handle:(name,fn)=>handlers[name]=fn,on:(name,fn)=>events[name]=fn},dialog:{},shell:{openPath:async name=>{opened.push(name);return '';}},
    app:{whenReady:()=>Promise.resolve(),getPath:()=>temp,on:(name,fn)=>appEvents[name]=fn,quit(){throw new Error('Shell startup failed');}}};
  const filename=path.join(__dirname,'../electron/main.cjs'),realRequire=createRequire(filename);
  const context=vm.createContext({require:name=>name==='electron'?electron:realRequire(name),__dirname:path.dirname(filename),process,console,URL,Buffer});
  vm.runInContext(fs.readFileSync(filename,'utf8'),context,{filename});
  t.after(()=>{appEvents['will-quit']?.();fs.rmSync(temp,{recursive:true,force:true});});
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
  const saved=JSON.parse(fs.readFileSync(path.join(temp,'authoring-drafts.json'),'utf8'));
  assert.equal(saved['studio.draft'],'unsaved script');
  events['studio:storage'](event,'get','studio.draft');
  assert.equal(event.returnValue.value,'unsaved script');
  events['studio:storage'](event,'keys','studio.');
  assert.deepEqual([...event.returnValue.value],['studio.draft']);
  assert.equal((await handlers['studio:environment'](event)).projects,path.join(temp,'Stranded II Studio','Projects'));
  await handlers['studio:open-folder'](event,project);assert.deepEqual(opened,[project]);
  await assert.rejects(()=>handlers['studio:open-folder'](event,'relative'),/existing project/);
  const revision=await handlers['studio:request'](event,{command:'revision',project});
  assert.match(revision.result.projectHash,/^[a-f0-9]{64}$/);
  const connected=await handlers['studio:request'](event,{command:'connect',project,base});
  assert.equal(connected.ok,true);
  assert.equal(JSON.parse(fs.readFileSync(path.join(project,'project.json'))).format,'stranded-studio/3');
  const legacy=path.join(temp,'legacy');
  const call=async request=>{
    const response=await handlers['studio:request'](event,{project:legacy,...request});
    assert.equal(response.ok,true,JSON.stringify(response));return response.result;
  };
  await call({command:'init',base,formatVersion:1});
  const original=fs.readFileSync(path.join(legacy,'project.json'),'utf8');
  const review=await call({command:'migrate'});
  assert.equal(review.dryRun,true);
  assert.equal(fs.readFileSync(path.join(legacy,'project.json'),'utf8'),original);
  const converted=await call({command:'migrate',dryRun:false,expectProjectHash:review.projectHash});
  assert.equal(JSON.parse(fs.readFileSync(path.join(legacy,'project.json'))).format,'stranded-studio/3');
  assert.equal((await call({command:'migrations'})).backups[0].id,converted.backup);
  assert.equal((await call({command:'restore-migration',backup:converted.backup})).dryRun,true);
  await call({command:'restore-migration',backup:converted.backup,dryRun:false});
  assert.equal(fs.readFileSync(path.join(legacy,'project.json'),'utf8'),original);
  await call({command:'apply',project,operations:{op:'map.create',name:'island',size:16}});
  const edit=await call({command:'apply',project,map:'island',operations:{op:'entity.add',map:'island',kind:'item',type:1}});
  assert.equal(edit.pendingSave,true);assert.equal(edit.snapshot.map.entities.length,2);
  let prevented=false;windowEvents.close({preventDefault(){prevented=true;}});
  assert.equal(prevented,true);assert.equal(closed,false);
  for(let n=0;n<100&&!closed;n++)await new Promise(resolve=>setTimeout(resolve,5));
  assert.equal(closed,true);
  assert.equal(JSON.parse(fs.readFileSync(path.join(project,'maps/island/map.json'))).entities.length,2);
});
