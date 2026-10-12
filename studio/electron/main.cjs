'use strict';
const {app, BrowserWindow, ipcMain, dialog} = require('electron');
const {spawn} = require('node:child_process');
const readline = require('node:readline');
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const {root, python} = require('./python.cjs');
let window, backend, server, sequence = 0, origin;
let saved = {}, storagePath;
const pending = new Map();
const allowed = new Set(['schema','commands','revision','init','inspect','catalog','apply','validate','export','undo','redo','preview']);

function startBackend() {
  backend = spawn(python(), ['-X','utf8',path.join(root,'scripts/studio.py'),'serve'], {cwd:root, stdio:['pipe','pipe','pipe']});
  backend.stderr.on('data', data => process.stderr.write(data));
  const fail = error => { for (const {reject} of pending.values()) reject(error); pending.clear(); };
  backend.on('error', fail);
  backend.on('exit', code => fail(new Error(`Studio core exited (${code}). Check Python 3.10+ and restart Studio.`)));
  readline.createInterface({input:backend.stdout}).on('line', line => {
    try {
      const result = JSON.parse(line), item = pending.get(result.id);
      if (item) { pending.delete(result.id); item.resolve(result); }
    } catch (error) { fail(error); }
  });
}
function request(value) {
  if (!value || !allowed.has(value.command)) throw new Error('Unsupported Studio command');
  return new Promise((resolve,reject) => {
    if (!backend || backend.exitCode !== null) return reject(new Error('Studio core is unavailable'));
    const id = ++sequence; pending.set(id,{resolve,reject});
    backend.stdin.write(JSON.stringify({...value,id})+'\n', error => {if (error) {pending.delete(id);reject(error);}});
  });
}
function trusted(event) {
  if (!window || event.sender !== window.webContents || event.senderFrame?.url.split('/').slice(0,3).join('/') !== origin)
    throw new Error('Unknown Studio caller');
}
function startServer() {
  const ui = path.join(root,'studio/dist'), engine = path.join(root,'build/studio/engine');
  const mime = {'.html':'text/html; charset=utf-8','.js':'text/javascript; charset=utf-8','.css':'text/css; charset=utf-8','.wasm':'application/wasm','.json':'application/json','.svg':'image/svg+xml'};
  server = http.createServer((req,res) => {
    try {
      if (req.method !== 'GET' && req.method !== 'HEAD') {res.writeHead(405);res.end();return;}
      const url = new URL(req.url, 'http://localhost');
      let name = decodeURIComponent(url.pathname), base = ui;
      if (name.startsWith('/engine/')) {base=engine;name=name.slice('/engine'.length);}
      if (name === '/') name = '/index.html';
      const file = path.resolve(base,'.'+name);
      const relative = path.relative(base,file);
      if (relative.startsWith('..') || path.isAbsolute(relative)) throw new Error('Invalid path');
      const real = fs.realpathSync(file), check = path.relative(base,real);
      if (check.startsWith('..') || path.isAbsolute(check) || !fs.statSync(real).isFile()) throw new Error('Invalid file');
      res.writeHead(200, {'Content-Type':mime[path.extname(file)]||'application/octet-stream',
        'Cache-Control':'no-store','X-Content-Type-Options':'nosniff',
        'Cross-Origin-Opener-Policy':'same-origin','Cross-Origin-Embedder-Policy':'require-corp',
        'Content-Security-Policy':"default-src 'self'; script-src 'self' 'wasm-unsafe-eval'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; worker-src 'self' blob:; connect-src 'self'; media-src 'self' blob:; object-src 'none'"});
      if (req.method === 'HEAD') res.end(); else fs.createReadStream(real).pipe(res);
    } catch {res.writeHead(404,{'Content-Type':'text/plain; charset=utf-8'});res.end('Not built. Run: python scripts/studio.py engine build');}
  });
  return new Promise(resolve => server.listen(0,'127.0.0.1',() => {origin=`http://127.0.0.1:${server.address().port}`;resolve();}));
}
app.whenReady().then(async () => {
  storagePath = path.join(app.getPath('userData'),'authoring-drafts.json');
  if(fs.existsSync(storagePath)) saved=JSON.parse(fs.readFileSync(storagePath,'utf8'));
  // The HTTP port changes between sessions; Electron owns persistent drafts so
  // their lifetime does not depend on the renderer's temporary origin.
  ipcMain.on('studio:storage',(event,action,key,value) => {
    try{
      trusted(event);
      if(typeof key!=='string'||!key.startsWith('studio.'))throw new Error('Invalid storage key');
      if(action==='get'){event.returnValue={ok:true,value:saved[key]??null};return;}
      if(action==='remove')delete saved[key];
      else if(action==='set'&&typeof value==='string')saved[key]=value;
      else throw new Error('Invalid storage action');
      const temporary=storagePath+'.tmp';
      fs.writeFileSync(temporary,JSON.stringify(saved));fs.renameSync(temporary,storagePath);
      event.returnValue={ok:true};
    }catch(error){event.returnValue={ok:false,error:error.message};}
  });
  startBackend(); await startServer();
  ipcMain.handle('studio:request',(event,value) => {trusted(event);return request(value);});
  ipcMain.handle('studio:environment',event => {
    trusted(event);
    return {root, base:path.join(root,'build/studio/runtime'), engineReady:fs.existsSync(path.join(root,'build/studio/engine/game.wasm'))};
  });
  ipcMain.handle('studio:choose',async (event,options={}) => {
    trusted(event);
    const result = await dialog.showOpenDialog(window, {title:String(options.title||'선택'),
      properties:options.directory ? ['openDirectory','createDirectory'] : ['openFile'],
      filters: options.map ? [{name:'Stranded map',extensions:['s2']}] : undefined});
    return result.canceled ? null : result.filePaths[0];
  });
  window = new BrowserWindow({width:1500,height:960,minWidth:1050,minHeight:680,backgroundColor:'#171c22',title:'Stranded II Studio',
    webPreferences:{preload:path.join(__dirname,'preload.cjs'),contextIsolation:true,nodeIntegration:false,sandbox:true}});
  window.setMenuBarVisibility(false);
  window.webContents.setWindowOpenHandler(() => ({action:'deny'}));
  window.webContents.on('will-navigate',(event,url) => {if (!url.startsWith(origin+'/')) event.preventDefault();});
  window.webContents.session.setPermissionRequestHandler((contents,permission,callback,details) => {
    callback(contents===window.webContents && details.requestingUrl.startsWith(origin+'/') && ['pointerLock','fullscreen'].includes(permission));
  });
  await window.loadURL(origin);
}).catch(error => {console.error(error);app.quit();});
app.on('window-all-closed',() => app.quit());
app.on('before-quit',() => {backend?.stdin.end();server?.close();});
