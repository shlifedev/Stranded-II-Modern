#include <bb/stub/stub.h>
#include <bb/runtime/runtime.h>
#include "../../../stdutil/windows_utf8.h"

#include <string>

static std::string commandTail(){
	const wchar_t *p=GetCommandLineW();
	bool quoted=false;
	for( ;*p;++p ){
		if( *p==L'"' ) quoted=!quoted;
		else if( !quoted && (*p==L' ' || *p==L'\t') ) break;
	}
	while( *p==L' ' || *p==L'\t' ) ++p;
	std::wstring tail=p;
	while( !tail.empty() && (tail.back()==L' ' || tail.back()==L'\t') ) tail.pop_back();
	return bbUtf8(tail);
}

extern "C" int BBCALL bbStart( int argc,char *argv[], BBMAIN bbMain ) {
	std::string cmd_line=commandTail();
	bbStartup( argv[0],cmd_line.c_str() );
	return bbruntime_run( bbMain,true )?0:1;
};

void bbruntime_panic( const char *err ){
	RTEX( err );
}
