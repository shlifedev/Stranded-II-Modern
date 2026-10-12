
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
