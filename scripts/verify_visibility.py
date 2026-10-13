"""Compile grass placement and object/shadow culling with entity test doubles.

This checks eligibility across camera turns without a graphics window. Actual
per-view culling and shadow frusta are covered by verify_engine.py --render.
"""
from pathlib import Path
import argparse
import json
import re
import subprocess

import blitz3d_toolchain as engine
from verify_timing import function


FIXTURE = r'''
Const Cworld_size=40,Cworld_height=1000
Global cam=1,ter=2,ter_tex_color=3,gfx_grasspreadtex=4
Global set_grass=1,grass_c=2,grass_dist=20,grass_mapsize=16,grass_f#=10,ter_size=4
Global grass_x=-2147483648,grass_y=-2147483648,grass_yaw=-2147483648,grass_wind#,f#=1
Global in_t100go,test_camx#,test_camz#,test_yaw#,test_height#=50,test_in_view
Global test_positions,test_visibility_queries,test_failures
Dim grass_h(2,2),grass_rgb(16,16,3),test_shown(9),test_px#(9)
Global report=WriteFile(SystemProperty("APPDIR")+"visibility.txt")
Function check(ok,label$)
 WriteLine report,"CHECK "+label$
 If Not ok Then test_failures=test_failures+1:WriteLine report,"FAIL "+label$
End Function
Function shown()
 Local count=0
 For i=1 To 9:count=count+test_shown(i):Next
 Return count
End Function
Function test_x#(h)
 Return test_camx
End Function
Function test_z#(h)
 Return test_camz
End Function
Function test_yaw#(h)
 Return test_yaw
End Function
Function test_view(h,camera)
 test_visibility_queries=test_visibility_queries+1
 Return test_in_view
End Function
Function test_position(h,x#,y#,z#)
 test_positions=test_positions+1:test_px(h)=x
End Function
Function test_show(h)
 test_shown(h)=1
End Function
Function test_hide(h)
 test_shown(h)=0
End Function
Function test_color(h,r,g,b)
End Function
Function test_rotate(h,angle#)
End Function
Function test_terrain#(h,x#,y#,z#)
 Return test_height
End Function
Function test_size(h)
 Return 16
End Function
For ix=0 To 2:For iz=0 To 2:grass_h(ix,iz)=ix*3+iz+1:Next:Next
For ix=0 To 15:For iz=0 To 15
 grass_rgb(ix,iz,0)=20:grass_rgb(ix,iz,1)=120:grass_rgb(ix,iz,2)=30
 grass_rgb(ix,iz,3)=1
Next:Next
grass_rgb(9,9,3)=0
; All patches start outside the main view, but remain eligible for the next
; camera orientation and other render views. Native culling selects the pixels.
test_in_view=0:in_t100go=0:grass_spread()
check(shown()=8,"terrain-eligible patches remain available outside the current camera view")
check(test_shown(5)=0,"terrain-disabled patch stays hidden")
check(test_positions=9 And test_px(1)=-10,"density and staggered grid positions are retained")
test_yaw=90:test_in_view=1:grass_spread()
check(shown()=8,"turning into grass needs no 100ms visibility update")
test_yaw=180:test_in_view=0:in_t100go=1:grass_spread()
test_yaw=-179:test_in_view=1:in_t100go=0:grass_spread()
check(shown()=8,"rapid reversal across the yaw wrap does not retain hidden patches")
check(test_positions=9,"turning the camera does not rebuild the grass grid")
; A pitch-only change can alter visibility without changing the grid or yaw.
test_in_view=0:in_t100go=1:grass_spread()
test_in_view=1:in_t100go=0:grass_spread()
check(shown()=8,"pitch-only turns use the current render view")
test_camx=20:grass_spread()
check(test_positions=18 And test_px(1)=10,"crossing a grid cell updates placement immediately")
check(shown()=8 And test_shown(2)=0 And test_shown(5)=1,"movement reapplies the terrain eligibility mask")
test_height=0:grass_heightmap():grass_spread()
check(shown()=0,"height mask changes refresh visibility while the camera is stationary")
test_height=50:grass_heightmap():grass_spread()
check(shown()=9,"restoring land refreshes all eligible patches without a camera turn")
test_camx=-2000:grass_spread()
check(shown()=0,"positions outside the terrain remain hidden")
Local previous=test_positions
set_grass=0:test_camx=2000:grass_spread()
check(test_positions=previous,"disabled grass does not populate a new grid")
WriteLine report,"FAILURES "+test_failures:CloseFile report
End
'''


SHADOW_FIXTURE = r'''
Const Cremaster_shadows=2
Global cam=20,env_light=21,map_hour=12,f#=1,set_windsway,set_debug_rt
Global TCobject.Tobject,test_failures,test_captures,test_distance#,test_captured
Dim remaster_enabled(3),Dobject_afc(1),Dobject_col(1),Dobject_active(1)
Dim Dobject_swayspeed#(1),Dobject_swaypower#(1)
Dim test_visible(20),test_enabled(20),test_alpha#(20),test_inview(20),test_dist#(20)
Global report=WriteFile(SystemProperty("APPDIR")+"shadow-visibility.txt")
Function check(ok,label$)
 WriteLine report,"CHECK "+label$
 If Not ok Then test_failures=test_failures+1:WriteLine report,"FAIL "+label$
End Function
Function test_show(h)
 test_visible(h)=1:test_enabled(h)=1
End Function
Function test_hide(h)
 test_visible(h)=0:test_enabled(h)=0
End Function
Function test_setalpha(h,a#)
 test_alpha(h)=a
End Function
Function test_brush(h)
 Return h
End Function
Function test_getalpha#(b)
 Return test_alpha(b)
End Function
Function test_freebrush(b)
End Function
Function test_distance#(h,camera)
 Return test_dist(h)
End Function
Function test_view(h,camera)
 Return test_inview(h)
End Function
Function test_angle#(h)
 Return 0
End Function
Function test_rotate(h,x#,y#,z#)
End Function
Function update_object_behaviour()
End Function
Function test_shadows(camera,light,distance#)
 test_captures=test_captures+1:test_distance=distance:test_captured=0
 If distance>0 Then
  For h=1 To 8
   If test_visible(h) And test_alpha(h)>0.99 Then test_captured=test_captured Or (1 Shl h)
  Next
 EndIf
 ; Collision-only child meshes must never be included by the shadow wrapper.
 check(test_alpha(10)=0 And test_alpha(11)=0,"collision meshes retain zero alpha during capture")
End Function
Dobject_afc(1)=1200:Dobject_col(1)=1:remaster_enabled(2)=1
For i=1 To 8
 Local obj.Tobject=New Tobject
 obj\id=i:obj\typ=1:obj\h=i:obj\alpha=1
 test_visible(i)=1:test_enabled(i)=1:test_alpha(i)=1:test_dist(i)=200
 If i=2 Then obj\ch=10
 If i=4 Then obj\ch=11
 If i=3 Or i=4 Then test_dist(i)=700
 If i=5 Then test_dist(i)=1500
 If i=6 Then obj\alpha=0:test_alpha(i)=0
 If i=7 Then obj\alpha=0.35:test_alpha(i)=0.35:test_dist(i)=700
Next
; Start in view, then turn just far enough to cull each caster.
For i=1 To 8:test_inview(i)=1:Next
test_cull_objects():remaster_shadow_frame()
Local expected=test_captured
For i=1 To 7:test_inview(i)=0:Next
test_cull_objects()
check(test_visible(1)=1 And test_alpha(1)=0 And test_enabled(1)=1,"near collider remains enabled at zero alpha")
check(test_visible(2)=0 And test_enabled(2)=0 And test_enabled(10)=1,"swaying model hides but its near collider remains enabled")
check(test_visible(3)=0 And test_visible(4)=0 And test_enabled(11)=0,"far off-screen objects retain legacy collision culling")
remaster_shadow_frame()
check(test_captured=expected,"turning past the view edge retains every eligible shadow caster")
check((test_captured And (1 Shl 5))=0,"view-distance-culled objects do not reappear in shadows")
check((test_captured And (1 Shl 6))=0,"script-invisible objects do not reappear in shadows")
check((test_captured And (1 Shl 7))=0,"translucent objects retain their script alpha during capture")
check(test_visible(1)=1 And test_alpha(1)=0 And test_enabled(1)=1,"capture restores the alpha-only collider state")
check(test_visible(2)=0 And test_alpha(2)=1 And test_enabled(2)=0 And test_enabled(10)=1,"capture restores the hidden swaying model and collider state")
check(test_visible(3)=0 And test_alpha(3)=1 And test_enabled(3)=0 And test_enabled(11)=0,"capture restores far hidden objects")
check(test_visible(7)=0 And test_alpha(7)=0.35,"capture restores fractional material alpha exactly")
For i=1 To 8:test_inview(i)=1:Next
test_cull_objects():remaster_shadow_frame()
check(test_captured=expected And test_alpha(1)=1,"turning back restores normal visibility without stale shadow state")
remaster_enabled(2)=0:remaster_shadow_frame()
check(test_distance=0,"disabling shadows clears the capture without changing visibility")
remaster_enabled(2)=1:map_hour=22:remaster_shadow_frame()
check(test_distance=0 And test_alpha(1)=1,"night capture remains disabled")
WriteLine report,"FAILURES "+test_failures:CloseFile report
End
'''


def check_shadows(output):
    includes = engine.ROOT / 'src/engine/includes'
    types = (includes / 'load_vars.bb').read_text('utf8')
    object_type = re.search(r'(?ms)^Type Tobject\n.*?^End Type', types)[0]
    cull = function((includes / 'cull.bb').read_text('utf8'), 'cull')
    # Execute the actual object loop, without unrelated AI, timers or scripts.
    cull = 'Function test_cull_objects()\n' + cull[cull.index('\t;Object'):cull.index('\t;Unit')] + 'End Function'
    shadow = function((includes / 'remaster.bb').read_text('utf8'), 'remaster_shadow_frame')
    body = object_type + '\n' + SHADOW_FIXTURE + '\n' + cull + '\n' + shadow
    replacements = {'ShowEntity': 'test_show', 'HideEntity': 'test_hide',
                    'EntityAlpha': 'test_setalpha', 'GetEntityBrush': 'test_brush',
                    'GetBrushAlpha': 'test_getalpha', 'FreeBrush': 'test_freebrush',
                    'EntityDistance': 'test_distance', 'EntityInView': 'test_view',
                    'EntityPitch': 'test_angle', 'EntityYaw': 'test_angle',
                    'RotateEntity': 'test_rotate', 'RenderWorldShadows': 'test_shadows'}
    for name, replacement in replacements.items():
        body = re.sub(r'\b' + name + r'\b', replacement, body)
    path = output / 'shadow-visibility.bb'
    path.write_text(body, encoding='utf8')
    exe = output / 'shadow-visibility.exe'
    engine.compile_test(path, exe, graphics=False, cwd=output)
    subprocess.run([str(exe)], cwd=output, check=True, timeout=30,
                   creationflags=subprocess.CREATE_NO_WINDOW)
    result = (output / 'shadow-visibility.txt').read_text('utf8')
    print(result)
    assert 'FAILURES 0' in result and '\nFAIL ' not in '\n' + result, result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    source = (engine.ROOT / 'src/engine/includes/grasspread.bb').read_text('utf8')
    body = FIXTURE + '\n' + '\n'.join(function(source, name) for name in ('grass_spread', 'grass_heightmap'))
    replacements = {'EntityX': 'test_x', 'EntityZ': 'test_z', 'EntityYaw': 'test_yaw',
                    'EntityInView': 'test_view', 'PositionEntity': 'test_position',
                    'ShowEntity': 'test_show', 'HideEntity': 'test_hide', 'EntityColor': 'test_color',
                    'RotateTexture': 'test_rotate', 'TerrainY': 'test_terrain', 'TextureHeight': 'test_size'}
    for name, replacement in replacements.items():
        body = re.sub(r'\b' + name + r'\b', replacement, body)
    path = output / 'visibility.bb'
    path.write_text(body, encoding='utf8')
    exe = output / 'visibility.exe'
    engine.compile_test(path, exe, graphics=False, cwd=output)
    subprocess.run([str(exe)], cwd=output, check=True, timeout=30,
                   creationflags=subprocess.CREATE_NO_WINDOW)
    result = (output / 'visibility.txt').read_text('utf8')
    print(result)
    assert 'FAILURES 0' in result and '\nFAIL ' not in '\n' + result, result
    check_shadows(output)
    (output / 'report.json').write_text(json.dumps({
        'grass_placement_and_eligibility': 'pass',
        'offscreen_shadow_casters_and_collision_restoration': 'pass',
        'rendering': 'not tested; explicit entity/terrain doubles',
    }, indent=2) + '\n', encoding='utf8')
    print('Grass visibility logic checks passed:', output)


if __name__ == '__main__':
    main()
