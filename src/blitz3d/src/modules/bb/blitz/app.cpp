
#include "../stdutil/stdutil.h"
#include <bb/runtime/runtime.h>
#include "app.h"
#include "ex.h"
#include "debug.h"

static BBApp app;
BBHook bbAppOnChange;

void BBCALL bbStop(){
	_bbDebugStop();
	if( !bbRuntimeIdle() ) RTEX( 0 );
}

void BBCALL bbEnd(){
	RTEX( 0 );
}

void BBCALL bbAppTitle( BBStr *ti,BBStr *cp ){
	app.title=*ti;
	app.close=*cp;

	BBApp tmp=app;
	bbAppOnChange.run( &tmp );

	delete ti;delete cp;
}

BBStr * BBCALL bbCommandLine(){
	return d_new BBStr( app.cmd_line );
}

void BBCALL bbStartup( const char *executable_path,const char *params ){
	if (executable_path) app.executable_path=executable_path;
	if (params) app.cmd_line=params;
}

BBApp BBCALL bbApp(){
	return app;
}

// The optional overlay observes logs without replacing stdout/stderr.
#include <cstdarg>
#include <atomic>
#include <vector>
static std::atomic<void(*)(int,const char*)> s2LogHandler{nullptr};
void bbSetLogHandler(void(*handler)(int,const char*)){s2LogHandler.store(handler);}
void bbRuntimeLog(int level,const char *format,...){
  va_list args,copy;va_start(args,format);va_copy(copy,args);
  int count=vsnprintf(nullptr,0,format,copy);va_end(copy);
  if(count>=0){std::vector<char> text(size_t(count)+1);vsnprintf(text.data(),text.size(),format,args);
    fprintf(level==0?stdout:stderr,"%s\n",text.data());
    if(auto handler=s2LogHandler.load())handler(level,text.data());}
  va_end(args);
}
