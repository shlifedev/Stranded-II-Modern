/* DOM bridge regression test; no window or OS input. */
const assert=require('node:assert/strict'),vm=require('node:vm'),fs=require('node:fs'),path=require('node:path');
const handlers=new Map(),commits=[],opens=[];
const input={style:{},value:'',setAttribute(){},addEventListener(t,f){handlers.set(t,[...(handlers.get(t)||[]),f]);},
 focus(){document.activeElement=this;}};
const canvas={width:1280,height:720,getBoundingClientRect:()=>({left:10,top:20,width:640,height:360}),focus(){document.activeElement=this;}};
const document={body:{append(){}},createElement:()=>input,activeElement:canvas,pointerLockElement:canvas,
 exitPointerLock(){this.pointerLockElement=null;}};
const context=vm.createContext({document});
vm.runInContext(fs.readFileSync(path.join(__dirname,'../src/web/debugui.js'),'utf8'),context);
const moduleBridge={ccall(name,result,types,values){assert.equal(name,'s2DebugUiCommit');commits.push(values[0]);}};
context.s2InstallDebugUi(moduleBridge,canvas,open=>opens.push(open));
function emit(type,data={}){let stopped=false;const event={stopImmediatePropagation(){stopped=true;},...data};for(const f of handlers.get(type)||[]){f(event);if(stopped)break;}return stopped;}
moduleBridge.s2DebugUiSetOpen(true);assert.equal(document.pointerLockElement,null);
moduleBridge.s2DebugUiIme(true,100,80,20);assert.equal(document.activeElement,input);
assert.equal(input.style.left,'60px');assert.equal(input.style.top,'60px');assert.equal(input.style.height,'10px');
emit('compositionstart');input.value='?';emit('input',{isComposing:true,data:'?'});assert.deepEqual(commits,[]);
emit('compositionend',{data:'??'});input.value='??';emit('input',{data:'??',inputType:'insertFromComposition'});
assert.deepEqual(commits,['??']);
input.value=' ????';emit('input',{inputType:'insertFromPaste'});assert.deepEqual(commits,['??',' ????']);
assert.equal(emit('keydown',{ctrlKey:true,code:'KeyV'}),true,'paste has one owner');
assert.equal(emit('keydown',{code:'ArrowLeft'}),false,'navigation reaches SDL');
assert.equal(emit('keypress'),true,'keypress does not duplicate textarea text');
moduleBridge.s2DebugUiSetOpen(false);assert.equal(input.style.display,'none');assert.equal(document.activeElement,canvas);
input.value='ignored';emit('input');assert.equal(commits.length,2);
assert.deepEqual(opens,[true,false]);console.log('PASS: browser overlay focus, Korean composition, paste, navigation and close');
