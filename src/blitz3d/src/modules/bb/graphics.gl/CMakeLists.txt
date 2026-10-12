bin2h(SOURCE_FILE default.glsl HEADER_FILE default.glsl.h VARIABLE_NAME DEFAULT_GLSL)

bb_start_module(graphics.gl)
set(DEPENDS_ON bb.graphics)
set(SOURCES graphics.gl.cpp graphics.gl.h canvas.cpp canvas.h default.glsl.h)

IF(BB_MACOS)
  set(SYSTEM_LIBS "-framework OpenGL")
ENDIF()

IF(BB_DESKTOP)
  set(LIBS ${LIBS} glew)
ENDIF()

bb_end_module()

if(BB_EMSCRIPTEN)
  set(BB_GL_NOCHECK_DEFAULT ON)
else()
  set(BB_GL_NOCHECK_DEFAULT OFF)
endif()
option(BB_GL_NOCHECK "Compile out GL() per-call glGetError checks" ${BB_GL_NOCHECK_DEFAULT})
if(BB_GL_NOCHECK)
  target_compile_definitions(bb.graphics.gl PUBLIC BB_GL_NOCHECK)
endif()

if(BB_MACOS)
  target_compile_definitions(bb.graphics.gl PUBLIC -DGL_SILENCE_DEPRECATION)
endif()
