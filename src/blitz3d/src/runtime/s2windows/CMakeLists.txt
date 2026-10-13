bb_start_runtime(s2windows "Stranded II Direct3D 12" bbCreateOpenGLRuntime)
foreach(module stub blitz hook event math string stdio stream system system.windows
    filesystem.windows timer bank input debugui audio audio.openal graphics.webgpu
    blitz3d.webgpu runtime.sdl platform.windows sockets)
  bb_addmodule(${module})
endforeach()
bb_end_runtime()
