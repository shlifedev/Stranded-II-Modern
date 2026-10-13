#include "module.h"
#include "ex.h"
#include "env.h"
#include <cstring>

#ifdef BB_EMSCRIPTEN
#include <emscripten.h>
#endif

bbEx::bbEx( const char *e ){
	err=e?std::string( e ):"";
#ifdef BB_EMSCRIPTEN
	if( bb_env.debug ){
		EM_ASM({ console.log("[bbEx] "+UTF8ToString($0)+"\n"+(new Error().stack)); }, err.c_str());
	}
#endif
}

bbEx::~bbEx(){
}
