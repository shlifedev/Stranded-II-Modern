"""Transactional authoring templates, shared by Studio and the JSON CLI."""
import re
from . import scripts


def templates():
    return [{'id':k,'name':v} for k,v in [('item','회복 아이템'),('recipe','제작법'),('building','건축'),('npc','NPC 상호작용'),('scenario','치료제 전달 시나리오')]]


def create(project, op):
    kind=op['template'];slug=op['slug'];name=op.get('name',slug)
    if not re.fullmatch(r'[A-Za-z][A-Za-z0-9_]{0,47}',slug): raise ValueError('Template slug: 1–48 ASCII letters, numbers or _; start with a letter')
    if not isinstance(name,str) or not name.strip(): raise ValueError('Template needs a name')
    key='studio.'+slug
    def text(suffix,en,ko=None):
        for lang,value in [('en',en),('ko',ko or en)]:
            project.perform({'op':'locale.set','language':lang,'key':key+'.'+suffix,'text':value})
        return '[[loc:'+key+'.'+suffix+']]'
    def first(kind):
        if kind=='unit' and any(r.kind=='unit' and r.identifier==40 for r in project.catalog()): return 40
        return next(r.identifier for r in project.catalog() if r.kind==kind)
    def file(suffix,body,target=None):
        path='scripts/'+slug+'/'+suffix+'.s2s'
        scripts.write(project,path,body,None)
        result['files'].append(path)
        if target: scripts.perform(project,{'op':'script.bind','path':path,'target':target})
        return scripts.native_path(path)
    def clone(kind,source,title):
        return project.perform({'op':'content.clone','kind':kind,'source':source,'properties':{'name':title,'script':''}})['id']
    ingredients=op.get('ingredients')
    if ingredients is None:
        ingredients=[[r.identifier,1] for r in project.catalog() if r.kind=='item'][:2]
    def requirements(minimum=2):
        if not isinstance(ingredients,list) or len(ingredients)<minimum: raise ValueError(f'Provide at least {minimum} ingredient entries')
        values=[]
        seen=set()
        for identifier,count in ingredients:
            project.find('item',identifier)
            if identifier in seen: raise ValueError('Use one entry per ingredient type')
            seen.add(identifier)
            if isinstance(count,bool) or not isinstance(count,int) or count<1: raise ValueError('Ingredient quantities must be positive integers')
            values.append(f'{identifier},{count}')
        return values
    result={'template':kind,'files':[]}
    if kind in ('item','scenario'):
        item=clone('item',op.get('source',first('item')),text('item',name if kind=='item' else name+' Tonic',name if kind=='item' else name+' 치료제'))
        used=text('used','You feel better.','기운을 되찾았습니다.')
        file('item',f'on:use {{\n heal "unit",1,20;\n freestored "unit",1,{item},1;\n msg "{used}";\n}}\n',{'scope':'definition','kind':'item','id':item})
        result['item']=item
        if kind=='item': return result
    if kind in ('recipe','scenario'):
        output=item if kind=='scenario' else int(op['result']);project.find('item',output)
        result['recipe']=project.perform({'op':'content.create','kind':'recipe','properties':{'req':requirements(),'gen':f'{output},1','genname':text('recipe',name)}})['id']
        if kind=='recipe': return result
    if kind=='building':
        output=int(op['result']);project.find('object',output)
        result['building']=project.perform({'op':'content.create','kind':'building','properties':{'objectid':output,'req':requirements(1)}})['id']
        return result
    if kind in ('npc','scenario'):
        npc=clone('unit',op.get('npcSource',op.get('source',first('unit'))) if kind=='npc' else op.get('npcSource',first('unit')),text('npc',name if kind=='npc' else 'Island visitor','섬의 방문자' if kind=='scenario' else name))
        result['npc']=npc
        greeting=text('greeting','Hello, traveler.','반갑습니다, 여행자님.')
        if kind=='npc':
            file('npc',f'on:use {{\n msg "{greeting}";\n}}\n',{'scope':'definition','kind':'unit','id':npc})
            return result
        map_name=op.get('map',slug)
        project.perform({'op':'map.create','name':map_name,'size':32})
        reward_type=ingredients[0][0]
        completed=text('completed','Delivery complete. The island story is finished.','치료제를 전달했습니다. 섬의 이야기를 완료했습니다.')
        reward=file('rewards',f'on:{slug}_reward {{\n $reward=create("item",{reward_type});\n store $reward,"unit",1;\n msg "{completed}";\n}}\n')
        intro=text('intro','Craft the tonic from the nearby supplies and deliver it to the visitor.','근처 재료로 치료제를 제작한 뒤 방문자에게 전달하세요.')
        file('start',f'on:preload {{\n extendscript 0,0,"{reward}";\n}}\non:start {{\n ${slug}_done=0;\n msg "{intro}";\n}}\n',{'scope':'map','map':map_name})
        request=text('request','Please bring me one tonic.','치료제 하나를 가져다 주세요.')
        done=text('thanks','Thank you again.','도와주셔서 감사합니다.')
        file('npc',f'on:use {{\n if (${slug}_done==1) {{\n  msg "{done}";\n }} else {{\n  if (count_stored("unit",1,{item})>=1) {{\n   freestored "unit",1,{item},1;\n   ${slug}_done=1;\n   event "{slug}_reward","global";\n  }} else {{\n   msg "{request}";\n  }}\n }}\n}}\n',{'scope':'definition','kind':'unit','id':npc})
        project.perform({'op':'entity.add','map':map_name,'kind':'unit','type':npc,'values':{'x':64,'z':64}})
        for i,(identifier,count) in enumerate(ingredients):
            project.perform({'op':'entity.add','map':map_name,'kind':'item','type':identifier,'values':{'x':32+i*24,'z':-32,'count':count*2}})
        result['map']=map_name
        return result
    raise ValueError('Unknown template: '+kind)
