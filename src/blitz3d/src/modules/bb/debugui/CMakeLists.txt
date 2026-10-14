if((S2_NATIVE_GAME OR BB_WEBGPU) AND TARGET SDL2)
  bb_start_module(debugui)
  set(DEPENDS_ON bb.blitz bb.graphics)
  set(SOURCES debugui.cpp debugui.h profiler.cpp profiler.h profiler_model.h benchmark.cpp benchmark.h benchmark_model.h
    ${S2_DEP_IMGUI}/imgui.cpp ${S2_DEP_IMGUI}/imgui_draw.cpp
    ${S2_DEP_IMGUI}/imgui_tables.cpp ${S2_DEP_IMGUI}/imgui_widgets.cpp
    ${S2_DEP_IMGUI}/backends/imgui_impl_sdl2.cpp
    ${S2_DEP_IMGUI}/backends/imgui_impl_wgpu.cpp
    ${S2_DEP_IMGUI}/misc/cpp/imgui_stdlib.cpp
    ${S2_DEP_IMGUI}/misc/freetype/imgui_freetype.cpp)
  if(APPLE)
    # The WebGPU surface helper uses Cocoa and CAMetalLayer on macOS.
    set_source_files_properties(${S2_DEP_IMGUI}/backends/imgui_impl_wgpu.cpp PROPERTIES LANGUAGE OBJCXX)
  endif()
  set(LIBS SDL2 freetype)
  bb_end_module()
  set_target_properties(bb.debugui PROPERTIES UNITY_BUILD OFF)
  target_include_directories(bb.debugui PRIVATE ${S2_DEP_IMGUI} ${S2_DEP_IMGUI}/backends ${S2_DEP_IMGUI}/misc/cpp)
  target_compile_definitions(bb.debugui PUBLIC IMGUI_USE_WCHAR32)
  target_compile_definitions(bb.debugui PRIVATE IMGUI_IMPL_WEBGPU_BACKEND_DAWN IMGUI_ENABLE_FREETYPE)
  if(BB_EMSCRIPTEN)
    target_compile_options(bb.debugui PUBLIC "--use-port=emdawnwebgpu")
  else()
    target_link_libraries(bb.debugui dawn::webgpu_dawn)
  endif()
endif()
