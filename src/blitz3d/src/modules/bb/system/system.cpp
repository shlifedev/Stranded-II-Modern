
#include "../stdutil/stdutil.h"
#include <bb/blitz/blitz.h>
#include <bb/runtime/runtime.h>
#include "system.h"

#include <string.h>
#include <time.h>
#ifdef BB_WINDOWS
#include <windows.h>
#include <shellapi.h>
#include "../../../stdutil/windows_utf8.h"
#else
#include <unistd.h>
#include <sys/time.h>
#endif
#ifdef BB_MACOS
extern "C" int bbMacOpenURL(const char *);
#endif
#ifdef BB_EMSCRIPTEN
#include <emscripten.h>
#endif

#ifdef BB_EMSCRIPTEN
EM_ASYNC_JS( char*,bbClipboardReadJS,(void),{
	try{
		var text=await navigator.clipboard.readText();
		var len=lengthBytesUTF8( text )+1;
		var ptr=_malloc( len );
		stringToUTF8( text,ptr,len );
		return ptr;
	}catch(e){
		return 0;
	}
});
#endif

static int base_time=0;
std::map<std::string,std::string> bbSystemProperties;
BBSystemDriver *bbSystemDriver=0;

void BBCALL bbRuntimeError( BBStr *str ){
	std::string t=*str;delete str;
	if( t.size()>255 ) t[255]=0;
	static char err[256];
	strcpy( err,t.c_str() );
	RTEX( err );
}

static std::vector<std::string> bbErrorMsgPool;

void BBCALL bbInitErrorMsgs( bb_int_t number,bb_int_t hasMacro ){
	bbErrorMsgPool.assign( number,std::string() );
}

void BBCALL bbSetErrorMsg( bb_int_t pos,BBStr *str ){
	if( pos>=0 && pos<(bb_int_t)bbErrorMsgPool.size() ) bbErrorMsgPool[pos]=*str;
	delete str;
}

void BBCALL bbMemoryAccessViolation(){
	std::string msg;
	for( size_t k=0;k<bbErrorMsgPool.size();++k ){
		if( bbErrorMsgPool[k].empty() ) continue;
		if( !msg.empty() ) msg+="\n";
		msg+=bbErrorMsgPool[k];
	}
	if( msg.empty() ) msg="MemoryAccessViolation";
	if( msg.size()>1023 ) msg.resize(1023);
	static char err[1024];
	strcpy( err,msg.c_str() );
	RTEX( err );
}

bb_int_t BBCALL bbExecFile( BBStr *f ){
	std::string t=*f;delete f;
	if( !t.size() ) return false;
	if( !bbRuntimeIdle() ) RTEX( 0 );
	if( t.compare( 0,7,"http://" )==0||t.compare( 0,8,"https://" )==0 ){
#ifdef BB_EMSCRIPTEN
		EM_ASM( { window.open( UTF8ToString( $0 ),'_blank','noopener' ); },t.c_str() );
		return 1;
#elif defined(BB_MACOS)
        return bbMacOpenURL(t.c_str());
#elif defined(BB_WINDOWS)
        return reinterpret_cast<intptr_t>(ShellExecuteW(nullptr,L"open",bbWide(t).c_str(),nullptr,nullptr,SW_SHOWNORMAL))>32;
#endif
	}
	return 0;
}

void BBCALL bbDelay( bb_int_t ms ){
#ifdef BB_EMSCRIPTEN
	if( !bbRuntimeIdle() ) RTEX( 0 );
	emscripten_sleep( ms>0 ? ms : 0 );
#else
	int t=bbMilliSecs()+ms;
	for(;;){
		if( !bbRuntimeIdle() ) RTEX( 0 );
		int d=t-bbMilliSecs();
		if( d<=0 ) return;
		if( d>100 ) d=100;
#ifdef BB_WINDOWS
        Sleep(d);
#else
		usleep( d * 1000 );
#endif
	}
#endif
}

bb_int_t BBCALL bbMilliSecs(){
#ifdef BB_EMSCRIPTEN
	return (bb_int_t)(long long)emscripten_get_now()-base_time;
#elif defined(BB_WINDOWS)
    return bb_int_t(GetTickCount())-base_time;
#else
	int t;
	struct timeval tv;
	gettimeofday(&tv,0);
	t=tv.tv_sec*1000;
	t+=tv.tv_usec/1000;
	return t-base_time;
#endif
}

BBStr * BBCALL bbSystemProperty( BBStr *p ){
#ifdef S2_NATIVE_GAME
	extern std::string strandedMacProperty(const std::string &);
	if(p->rfind("StrandedMac:",0)==0){
		std::string request=p->substr(12);delete p;
		return d_new BBStr(strandedMacProperty(request));
	}
	if(tolower(*p)=="apphwnd"){delete p;return d_new BBStr("1");}
	if(tolower(*p)=="appdir"){
		extern std::string strandedMacAppDirectory(const std::string &);
		delete p;return d_new BBStr(strandedMacAppDirectory(bbApp().executable_path));
	}
#endif
#ifdef BB_EMSCRIPTEN
	extern std::string strandedWebProperty( const std::string & );
	if( p->rfind("StrandedWeb:",0)==0 ){
		std::string request=p->substr(12);delete p;
		return d_new BBStr( strandedWebProperty(request) );
	}
	if( tolower(*p)=="apphwnd" ){ delete p;return d_new BBStr("1"); }
	if( tolower(*p)=="appdir" ){ delete p;return d_new BBStr("/game/"); }
#endif
#ifdef BB_WINDOWS
    if(tolower(*p)=="apphwnd"){
        extern bb_int_t s2WindowsWindowHandle();delete p;
        return d_new BBStr(std::to_string(s2WindowsWindowHandle()));
    }
#endif
	std::string t=tolower(*p);
	delete p;return d_new BBStr( bbSystemProperties[t] );
}

BBStr * BBCALL bbGetEnv( BBStr *env_var ){
	char *p=getenv( env_var->c_str() );
	BBStr *val=d_new BBStr( p ? p : "" );
	delete env_var;
	return val;
}

void BBCALL bbSetEnv( BBStr *env_var,BBStr *val ){
#ifdef BB_WINDOWS
    _putenv_s(env_var->c_str(),val->c_str());
#else
	setenv( (*env_var).c_str(),(*val).c_str(),1 );
#endif
	delete env_var;
	delete val;
}

BBStr * BBCALL bbGetClipboardContents(){
#ifdef BB_EMSCRIPTEN
	char *p=bbClipboardReadJS();
	BBStr *r=d_new BBStr( p?p:"" );
	if( p ) free( p );
	return r;
#else
	return d_new BBStr( "" );
#endif
}

void BBCALL bbSetClipboardContents( BBStr *contents ){
#ifdef BB_EMSCRIPTEN
	EM_ASM({
		try{ navigator.clipboard.writeText( UTF8ToString( $0 ) ); }catch(e){}
	},contents->c_str() );
#endif
	delete contents;
}

bb_int_t BBCALL bbScreenWidth( bb_int_t i ){
#ifdef BB_EMSCRIPTEN
	return EM_ASM_INT( return (typeof window!=='undefined'&&window.innerWidth)?window.innerWidth:0; );
#else
	return 0;
#endif
}

bb_int_t BBCALL bbScreenHeight( bb_int_t i ){
#ifdef BB_EMSCRIPTEN
	return EM_ASM_INT( return (typeof window!=='undefined'&&window.innerHeight)?window.innerHeight:0; );
#else
	return 0;
#endif
}

bb_int_t BBCALL bbDesktopWidth(){
	return bbScreenWidth( -1 );
}

bb_int_t BBCALL bbDesktopHeight(){
	return bbScreenHeight( -1 );
}

bb_int_t BBCALL bbTotalPhys(){
	return 0;
}

bb_int_t BBCALL bbAvailPhys(){
	return 0;
}

bb_float_t BBCALL bbDPIScaleX(){
	return 1.0;
}

bb_float_t BBCALL bbDPIScaleY(){
	return 1.0;
}

BBStr * BBCALL bbCurrentDate(){
	time_t t;
	time( &t );
	char buff[256];
	strftime( buff,256,"%d %b %Y",localtime( &t ) );
	return d_new BBStr( buff );
}

BBStr * BBCALL bbCurrentTime(){
	time_t t;
	time( &t );
	char buff[256];
	strftime( buff,256,"%H:%M:%S",localtime( &t ) );
	return d_new BBStr( buff );
}

BBMODULE_CREATE( system ){
	bbSystemDriver=0;
#ifdef BB_WINDOWS
	wchar_t executable[32768];
	DWORD length=GetModuleFileNameW(nullptr,executable,32768);
	std::string path=bbUtf8(std::wstring(executable,length));
	bbSystemProperties["appdir"]=path.substr(0,path.find_last_of("/\\")+1);
#else
	bbSystemProperties["appdir"]=bbApp().executable_path;
#endif
	base_time=0;
	base_time=bbMilliSecs();
	return true;
}

BBMODULE_DESTROY( system ){
	if( bbSystemDriver ){
		delete bbSystemDriver;
		bbSystemDriver=0;
	}
	return true;
}

#ifdef S2_NATIVE_GAME
void strandedMacError(const char *message){RTEX(message);}
#endif
