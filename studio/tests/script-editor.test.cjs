// Drive the real editor effects with delayed React prop acknowledgments.
const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');

test('rapid typing ignores stale local echoes, while external values and target changes still apply',async()=>{
  const {transformWithOxc}=await import('vite');
  const filename=path.join(__dirname,'../src/ScriptEditor.tsx');
  const {code}=await transformWithOxc(fs.readFileSync(filename,'utf8'),filename);
  const hooks=[],pending=[],emitted=[],replacements=[];let cursor=0,listener,model,editor;
  const disposable=()=>({dispose(){}});
  function createModel(text){return {text,dispose(){},isDisposed:()=>false};}
  const monaco={
    languages:{register(){},setMonarchTokensProvider(){},setLanguageConfiguration(){},registerCompletionItemProvider:disposable,registerHoverProvider:disposable},
    editor:{defineTheme(){},createModel,setModelMarkers(){},create(_element,options){
      model=createModel(options.value);
      editor={getModel:()=>model,setModel:value=>{model=value;listener?.();},getValue:()=>model.text,
        setValue:value=>{replacements.push(value);model.text=value;listener?.();},
        onDidChangeModelContent:fn=>{listener=fn;return disposable();},updateOptions(){},dispose(){}};
      return editor;
    }},MarkerSeverity:{Error:8,Warning:4}
  };
  const context=vm.createContext({monaco,Worker:function(){},
    useRef:value=>{const i=cursor++;return hooks[i]??=( {current:value} );},
    useEffect:(effect,deps)=>{
      const i=cursor++,old=hooks[i];
      if(!old||deps.some((v,j)=>v!==old.deps[j]))pending.push(()=>{old?.cleanup?.();hooks[i]={deps,cleanup:effect()};});
    },
    _jsx:(_tag,props)=>{if(props.ref)props.ref.current={};return {};}
  });
  vm.runInContext(code.replace(/^import .*;\n/gm,'').replace('export function ScriptEditor','function ScriptEditor'),context);
  function render(value,fileKey='one'){
    cursor=0;context.ScriptEditor({value,fileKey,onChange:text=>emitted.push(text)});
    while(pending.length)pending.shift()();
  }
  function type(text){model.text=text;listener();}
  render('');type('o');type('on');render('o');
  assert.equal(editor.getValue(),'on');assert.deepEqual(replacements,[]);
  type('on:start {}');render('on');assert.equal(editor.getValue(),'on:start {}');
  render('on:start {}');assert.deepEqual(emitted,['o','on','on:start {}']);
  render('external');assert.equal(editor.getValue(),'external');assert.deepEqual(replacements,['external']);
  render('second target','two');assert.equal(editor.getValue(),'second target');
  assert.deepEqual(emitted,['o','on','on:start {}'],'programmatic changes must not create drafts');
});
