import {useEffect,useRef} from 'react';
import * as monaco from 'monaco-editor/editor/editor.api.js';
import Worker from 'monaco-editor/editor/editor.worker.js?worker';
import type {Diagnostic} from './api';

(globalThis as unknown as {MonacoEnvironment:unknown}).MonacoEnvironment={getWorker:()=>new Worker()};
monaco.languages.register({id:'stranded'});
monaco.languages.setMonarchTokensProvider('stranded',{
  tokenizer:{root:[[/\/\/.*$/,'comment'],[/#[^\n]*/,'comment'],[/"(?:[^"\\]|\\.)*"/,'string'],[/\$[\w]+/,'variable'],[/\[\[loc:[^\]]+\]\]/,'string'],[/\bon:[\w]+/,'keyword'],[/\b(if|elseif|else|loop|while|return|break)\b/,'keyword'],[/\b\d+(?:\.\d+)?\b/,'number'],[/[{}()]/,'delimiter.bracket']]}
});
monaco.languages.setLanguageConfiguration('stranded',{comments:{lineComment:'//'},brackets:[['{','}'],['(',')']],autoClosingPairs:[{open:'{',close:'}'},{open:'(',close:')'},{open:'"',close:'"'}]});
monaco.editor.defineTheme('stranded-dark',{base:'vs-dark',inherit:true,rules:[],colors:{'editor.background':'#1a2027','editorLineNumber.foreground':'#536273','editorCursor.foreground':'#80c5ad'}});

export function ScriptEditor({value,onChange,commands=[],fileKey='inline',diagnostics=[],location,readOnly=false}:{value:string;onChange:(text:string)=>void;commands?:{name:string;parameters:string}[];fileKey?:string;diagnostics?:Diagnostic[];location?:{line:number;serial:number};readOnly?:boolean}){
  const element=useRef<HTMLDivElement>(null),editor=useRef<monaco.editor.IStandaloneCodeEditor|null>(null),change=useRef(onChange),updating=useRef(false);
  const models=useRef(new Map<string,monaco.editor.ITextModel>());
  const echoes=useRef(new Set<string>());
  change.current=onChange;
  useEffect(()=>{
    if(!element.current)return;
    const instance=monaco.editor.create(element.current,{value,language:'stranded',theme:'stranded-dark',fontSize:13,minimap:{enabled:false},automaticLayout:true,scrollBeyondLastLine:false,tabSize:2,wordWrap:'on'});
    editor.current=instance;
    const listener=instance.onDidChangeModelContent(()=>{if(!updating.current){const text=instance.getValue();echoes.current.add(text);change.current(text);}});
    return()=>{listener.dispose();instance.getModel()?.dispose();for(const model of models.current.values())if(!model.isDisposed())model.dispose();models.current.clear();instance.dispose();editor.current=null;};
  },[]);
  useEffect(()=>{
    if(!editor.current)return;
    echoes.current.clear();
    let model=models.current.get(fileKey);
    if(!model){model=monaco.editor.createModel(value,'stranded');models.current.set(fileKey,model);}
    updating.current=true;const old=editor.current.getModel();editor.current.setModel(model);
    if(old&&![...models.current.values()].includes(old))old.dispose();updating.current=false;
  },[fileKey]);
  useEffect(()=>{
    if(!editor.current)return;
    // React may acknowledge an earlier keystroke after Monaco has accepted the
    // next one. Do not replace the model/cursor with that stale local echo.
    if(echoes.current.has(value)){
      if(editor.current.getValue()===value)echoes.current.clear();else echoes.current.delete(value);
      return;
    }
    if(editor.current.getValue()!==value){echoes.current.clear();updating.current=true;editor.current.setValue(value);updating.current=false;}
  },[value,fileKey]);
  useEffect(()=>{editor.current?.updateOptions({readOnly});},[readOnly]);
  useEffect(()=>{const model=editor.current?.getModel();if(model)monaco.editor.setModelMarkers(model,'studio',diagnostics.map(d=>({severity:d.severity==='error'?monaco.MarkerSeverity.Error:monaco.MarkerSeverity.Warning,message:d.message,startLineNumber:d.line||1,startColumn:d.column||1,endLineNumber:d.line||1,endColumn:(d.column||1)+1})));},[diagnostics,fileKey]);
  useEffect(()=>{if(location&&editor.current){editor.current.revealLineInCenter(location.line);editor.current.setPosition({lineNumber:location.line,column:1});}},[location,fileKey]);
  useEffect(()=>{
    const registration=monaco.languages.registerCompletionItemProvider('stranded',{provideCompletionItems(model,position){
      const word=model.getWordUntilPosition(position),range={startLineNumber:position.lineNumber,endLineNumber:position.lineNumber,startColumn:word.startColumn,endColumn:word.endColumn};
      return {suggestions:[...commands.map(command=>({label:command.name,kind:monaco.languages.CompletionItemKind.Function,insertText:command.name+' ',detail:command.parameters,documentation:command.parameters,range})),...['start','preload','load','use','collect','kill','hit','timer'].map(name=>({label:'on:'+name,kind:monaco.languages.CompletionItemKind.Snippet,insertText:'on:'+name+' {\n  ${1}\n}',insertTextRules:monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,range}))]};
    }});
    const hover=monaco.languages.registerHoverProvider('stranded',{provideHover(model,position){
      const word=model.getWordAtPosition(position),command=commands.find(c=>c.name===word?.word);
      return command?{contents:[{value:'**'+command.name+'**'},{value:command.parameters}]}:null;
    }});
    return()=>{registration.dispose();hover.dispose();};
  },[commands]);
  return <div ref={element} className="script-editor"/>;
}
