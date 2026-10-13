import {classes,Kind,RecordInfo} from './api';

export function placementTarget(content:{kind:Kind;id:number|string}|null,catalog:RecordInfo[]){
  if(!content)return null;
  let {kind,id}=content;
  if(kind==='building'){
    const props=catalog.find(r=>r.kind===kind&&String(r.id)===String(id))?.properties;
    if(props?.objectid){kind='object';id=props.objectid[0];}
    else if(props?.unitid){kind='unit';id=props.unitid[0];}
    else return null;
  }
  const entityClass=classes[kind],type=Number(id);
  return entityClass&&Number.isInteger(type)&&type>0?{kind,id:type,class:entityClass}:null;
}
