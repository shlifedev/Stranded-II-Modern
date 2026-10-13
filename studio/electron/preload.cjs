'use strict';
const {contextBridge, ipcRenderer} = require('electron');
function storage(action,key,value){
  const result=ipcRenderer.sendSync('studio:storage',action,key,value);
  if(!result.ok)throw new Error(result.error);
  return result.value;
}
contextBridge.exposeInMainWorld('studio', {
  storage: {getItem:key=>storage('get',key),setItem:(key,value)=>storage('set',key,value),removeItem:key=>storage('remove',key)},
  request: request => ipcRenderer.invoke('studio:request', request),
  choose: options => ipcRenderer.invoke('studio:choose', options),
  environment: () => ipcRenderer.invoke('studio:environment')
});
