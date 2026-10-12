import {useEffect,useRef} from 'react';
import * as monaco from 'monaco-editor/editor/editor.api.js';
import Worker from 'monaco-editor/editor/editor.worker.js?worker';

(globalThis as unknown as {MonacoEnvironment:unknown}).MonacoEnvironment={getWorker:()=>new Worker()};
monaco.languages.register({id:'stranded'});
monaco.languages.setMonarchTokensProvider('stranded',{
  tokenizer:{root:[[/\/\/.*$/,'comment'],[/#[^\n]*/,'comment'],[/"(?:[^"\\]|\\.)*"/,'string'],[/\$[\w]+/,'variable'],[/\[\[loc:[^\]]+\]\]/,'string'],[/\bon:[\w]+/,'keyword'],[/\b(if|elseif|else|loop|while|return|break)\b/,'keyword'],[/\b\d+(?:\.\d+)?\b/,'number'],[/[{}()]/,'delimiter.bracket']]}
});
monaco.languages.setLanguageConfiguration('stranded',{comments:{lineComment:'//'},brackets:[['{','}'],['(',')']],autoClosingPairs:[{open:'{',close:'}'},{open:'(',close:')'},{open:'"',close:'"'}]});
monaco.editor.defineTheme('stranded-dark',{base:'vs-dark',inherit:true,rules:[],colors:{'editor.background':'#1a2027','editorLineNumber.foreground':'#536273','editorCursor.foreground':'#80c5ad'}});

export function ScriptEditor({value,onChange,commands=[]}:{value:string;onChange:(text:string)=>void;commands?:{name:string;parameters:string}[]}){
  const element=useRef<HTMLDivElement>(null),editor=useRef<monaco.editor.IStandaloneCodeEditor|null>(null),change=useRef(onChange),updating=useRef(false);
  change.current=onChange;
  useEffect(()=>{
    if(!element.current)return;
    const instance=monaco.editor.create(element.current,{value,language:'stranded',theme:'stranded-dark',fontSize:13,minimap:{enabled:false},automaticLayout:true,scrollBeyondLastLine:false,tabSize:2,wordWrap:'on'});
    editor.current=instance;
    const listener=instance.onDidChangeModelContent(()=>{if(!updating.current)change.current(instance.getValue());});
    return()=>{listener.dispose();instance.getModel()?.dispose();instance.dispose();editor.current=null;};
  },[]);
  useEffect(()=>{if(editor.current&&editor.current.getValue()!==value){updating.current=true;editor.current.setValue(value);updating.current=false;}},[value]);
  useEffect(()=>{
    const registration=monaco.languages.registerCompletionItemProvider('stranded',{provideCompletionItems(model,position){
      const word=model.getWordUntilPosition(position),range={startLineNumber:position.lineNumber,endLineNumber:position.lineNumber,startColumn:word.startColumn,endColumn:word.endColumn};
      return {suggestions:commands.map(command=>({label:command.name,kind:monaco.languages.CompletionItemKind.Function,insertText:command.name+' ',detail:command.parameters,range}))};
    }});
    return()=>registration.dispose();
  },[commands]);
  return <div ref={element} className="script-editor"/>;
}
