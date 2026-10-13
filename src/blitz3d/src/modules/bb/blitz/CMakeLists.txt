bb_start_module(blitz)
set(SOURCES app.cpp app.h basic.cpp basic.h debug.cpp debug.h env.cpp env.h ex.cpp ex.h module.h commands.h)
if(BB_LEGACY32)
  list(APPEND SOURCES legacy32.cpp legacy32.h)
endif()
set(LIBS stdutil)

if(BB_NDK)
  set(LIBS ${LIBS} log)
endif()

bb_end_module()
