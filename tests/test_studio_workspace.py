"""Folder portability, Git checkouts and atomic field-aware draft saves."""
import json
import shutil
import subprocess
import sys
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest import TestCase

sys.path.insert(0, str(Path(__file__).resolve().parents[1]/'scripts'))
import studio
from studio_core.project import Project, MOD, digest
from studio_core.workspace import connect, references, assets, DraftConflict
from studio_core.maps import EditableMap


class AuthoringWorkspace(TestCase):
    def setUp(self):
        temp=TemporaryDirectory();self.addCleanup(temp.cleanup);self.root=Path(temp.name).resolve()
        self.base=self.root/'base';directory=self.base/MOD/'sys';directory.mkdir(parents=True)
        for name,text in {
            'items.inf':'id=1\nname=나무\nhealth=10\ncustom=keep\nid=2\nname=Stone\n',
            'objects.inf':'id=1\nname=Tree\n', 'units.inf':'id=1\nname=Visitor\n',
            'infos.inf':'id=1\nname=Start\n',
        }.items(): (directory/name).write_text(text,encoding='utf8')
        self.p=Project.create(self.root/'project',self.base,version=1)
        self.target={'scope':'definition','kind':'item','id':1}

    def draft(self, changes=None, **kwargs):
        return {'op':'draft.apply','key':'draft-one','target':self.target,'section':'properties',
                'base':self.p.find('item',1).properties,'changes':changes or {'name':['new']}, **kwargs}

    def test_create_in_existing_repository_preserves_files_and_ignores_local_data(self):
        folder=self.root/'Git 프로젝트';folder.mkdir()
        subprocess.run(['git','init','-q',str(folder)],check=True)
        (folder/'README.md').write_bytes(b'User README\n')
        (folder/'.gitignore').write_bytes(b'# user rules\r\n*.log\r\n')
        p=Project.create(folder,self.base,version=1)
        self.assertEqual((folder/'README.md').read_bytes(),b'User README\n')
        self.assertTrue((folder/'.gitignore').read_bytes().startswith(b'# user rules\r\n*.log\r\n'))
        p.apply([{'op':'map.create','name':'island','size':16}])
        connect(folder,self.base)
        status=subprocess.check_output(['git','-C',str(folder),'status','--porcelain','--untracked-files=all'],text=True)
        self.assertIn('project.json',status);self.assertIn('sources/',status);self.assertNotIn('.studio/',status)
        ignore=(folder/'.gitignore').read_bytes();connect(folder,self.base)
        self.assertEqual(ignore,(folder/'.gitignore').read_bytes())

    def test_reserved_paths_and_symlink_ignore_are_not_overwritten(self):
        for name in ('project.json','Scripts','.studio','sources'):
            folder=self.root/name.replace('.','_');folder.mkdir();(folder/name).write_bytes(b'keep')
            with self.assertRaises(ValueError):Project.create(folder,self.base,version=1)
            self.assertEqual((folder/name).read_bytes(),b'keep')
        folder=self.root/'links';folder.mkdir();(folder/'original').write_text('keep')
        try:(folder/'.gitignore').symlink_to(folder/'original')
        except OSError:self.skipTest('Symlinks unavailable')
        with self.assertRaises(ValueError):Project.create(folder,self.base,version=1)
        self.assertFalse((folder/'project.json').exists())

    def test_clone_reconnect_changes_no_manifest_and_preview_stays_strict(self):
        self.p.apply([{'op':'map.create','name':'island','size':16}])
        folder=self.root/'different'/'clone';shutil.copytree(self.p.root,folder,ignore=shutil.ignore_patterns('.studio'))
        before=(folder/'project.json').read_bytes()
        with self.assertRaisesRegex(ValueError,'Base runtime is missing'):Project(folder)
        connect(folder,self.base);p=Project(folder)
        self.assertEqual(p.base,self.base);self.assertEqual((folder/'project.json').read_bytes(),before)
        with self.assertRaisesRegex(ValueError,'matching its bundled assets'):p.preview('island',self.root/'other-runtime')
        self.assertEqual(p.preview('island',self.base)['map'],'maps/studio.s2')
        local=(folder/'.studio/local.json').read_bytes()
        with self.assertRaises(ValueError):connect(folder,self.root/'missing')
        self.assertEqual((folder/'.studio/local.json').read_bytes(),local)

    def test_same_revision_checkout_invalidates_apply_undo_and_preview(self):
        self.p.apply([{'op':'map.create','name':'island','size':16}]);old=self.p.project_hash()
        data=json.loads(self.p.path.read_text());data['name']='Other branch';self.p.path.write_text(json.dumps(data))
        current=studio.dispatch({'command':'revision','project':str(self.p.root)})
        self.assertEqual(current['revision'],self.p.data['revision']);self.assertNotEqual(old,current['projectHash'])
        for action in (lambda:self.p.apply([{'op':'content.set','kind':'item','id':1,'properties':{'name':'lost'}}],expected_hash=old),
                       lambda:self.p.undo(expected_hash=old),lambda:self.p.preview('island',expected_hash=old)):
            with self.assertRaisesRegex(ValueError,'content changed'):action()

    def test_field_merge_preserves_unrelated_changes_and_undo_is_one_transaction(self):
        draft=self.draft({'health':['20']})
        self.p.apply([{'op':'content.set','kind':'item','id':1,'properties':{'name':'Renamed','unknown':'keep too'}}])
        self.p.apply([draft,{'op':'script.write','path':'scripts/test.s2s','text':'on:start {}','expectedHash':None}])
        props=self.p.find('item',1).properties
        self.assertEqual(props['health'],['20']);self.assertEqual(props['name'],['Renamed']);self.assertEqual(props['unknown'],['keep too']);self.assertEqual(props['custom'],['keep'])
        self.p.undo();self.assertEqual(self.p.find('item',1).properties['health'],['10']);self.assertFalse((self.p.root/'scripts/test.s2s').exists())
        self.p.undo(redo=True);self.assertTrue((self.p.root/'scripts/test.s2s').exists())

    def test_conflicting_batch_preserves_every_draft_target_and_reports_values(self):
        draft=self.draft();self.p.apply([{'op':'content.set','kind':'item','id':1,'properties':{'name':'external'}}]);before=self.p.path.read_bytes()
        result=studio.response({'command':'apply','project':str(self.p.root),'operations':[
            {'op':'script.write','path':'scripts/new.s2s','text':'on:start {}','expectedHash':None},draft]})
        self.assertFalse(result['ok']);self.assertEqual(result['error']['details']['conflicts'][0]['current'],['external'])
        self.assertEqual(result['error']['details']['key'],'draft-one');self.assertEqual(self.p.path.read_bytes(),before)
        self.assertFalse((self.p.root/'scripts/new.s2s').exists())

    def test_inline_file_entity_drafts_and_binding_conflicts(self):
        self.p.apply([{'op':'map.create','name':'island','size':16},{'op':'entity.add','map':'island','kind':'object','type':1}])
        entity={'scope':'entity','map':'island','kind':'object','id':1}
        self.p.apply([{'op':'draft.apply','target':entity,'section':'values','base':{'x':0},'changes':{'x':64}},
                      {'op':'draft.apply','target':entity,'section':'script','base':'','text':'on:use {}'},
                      {'op':'script.write','path':'scripts/one.s2s','text':'on:start {}','expectedHash':None}])
        summary=EditableMap(self.p.map_bytes('island')).summary()
        self.assertEqual(summary['entities'][0]['x'],64)
        self.assertIn('on:use',summary['entities'][0]['script'])
        draft={'op':'draft.apply','target':{'scope':'file','path':'scripts/one.s2s'},'section':'file','hash':digest(b'on:start {}'),'text':'on:start { msg "new"; }'}
        self.p.apply([draft]);self.assertIn('new',(self.p.root/'scripts/one.s2s').read_text())
        with self.assertRaises(DraftConflict):self.p.apply([{**draft,'text':'stale'}])
        self.p.apply([{'op':'script.bind','target':entity,'path':'scripts/one.s2s'}])
        with self.assertRaises(DraftConflict):self.p.apply([{'op':'draft.apply','target':entity,'section':'script','base':'on:use {}','text':'lost'}])

    def test_removed_target_and_legacy_draft_are_not_silently_rebased(self):
        draft=self.draft();del draft['base']
        with self.assertRaises(DraftConflict):self.p.apply([draft])
        with self.assertRaises(DraftConflict):self.p.apply([self.draft(target={**self.target,'id':999})])

    def test_saved_syntax_errors_are_navigable_and_block_preview(self):
        self.p.apply([{'op':'map.create','name':'island','size':16},{'op':'map.script','map':'island','text':'on:start {'},
                      {'op':'entity.add','map':'island','kind':'object','type':1},
                      {'op':'entity.script','map':'island','kind':'object','id':1,'text':'on:use {'}])
        issues=[d for d in self.p.validate() if d['code']=='script-syntax']
        self.assertEqual({d['target']['scope'] for d in issues},{'map','entity'})
        self.assertTrue(all(d['field']=='script' and 'path' not in d for d in issues))
        with self.assertRaisesRegex(ValueError,'validation'):self.p.preview('island')

    def test_reverse_references_assets_and_diagnostic_targets(self):
        self.p.apply([{'op':'map.create','name':'island','size':16},{'op':'entity.add','map':'island','kind':'item','type':1},
                      {'op':'content.create','kind':'recipe','properties':{'req':['1,2','2,1'],'gen':['1,1']}},
                      {'op':'script.write','path':'scripts/one.s2s','text':'on:start {}','expectedHash':None},
                      {'op':'script.write','path':'scripts/two.s2s','text':'on:start { extendscript 0,0,"sys/scripts/studio/one.s2s"; }','expectedHash':None},
                      {'op':'script.bind','target':self.target,'path':'scripts/one.s2s'}])
        rows=references(self.p,self.target)['references']
        self.assertEqual({r['target']['scope'] for r in rows},{'entity','definition','file'})
        rows=references(self.p,{'scope':'file','path':'scripts/one.s2s'})['references']
        self.assertEqual({r['target']['scope'] for r in rows},{'file','definition'})
        self.p.apply([{'op':'content.set','kind':'item','id':1,'properties':{'icon':'gfx/missing.bmp'}}])
        issue=next(d for d in self.p.validate() if d['code']=='missing-asset')
        self.assertEqual(issue['target'],self.target);self.assertEqual(issue['field'],'icon')
        source=self.root/'image.png';source.write_bytes(b'image')
        self.p.apply([{'op':'asset.import','path':str(source),'destination':MOD+'gfx/한국어.png'}])
        self.assertIn('gfx/한국어.png',assets(self.p)['paths'])
