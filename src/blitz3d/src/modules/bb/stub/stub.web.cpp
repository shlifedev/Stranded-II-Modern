#include <bb/stub/stub.h>
#include <bb/runtime/runtime.h>
#include <bb/blitz/blitz.h>

#include <iostream>
#include <cstdlib>
#include <cstring>

#include <emscripten.h>

class StdioDebugger : public Debugger{
private:
	bool trace;
	std::string file;
	int row,col;
public:
	StdioDebugger( bool t ):trace(t){
	}

	void debugRun(){
	}
	void debugStop(){
	}
	void debugStmt( int srcpos,const char *f ){
		file=std::string(f);
		row=(srcpos>>16)&0xffff;
		col=srcpos&0xffff;
		if( trace ) std::cout<<file<<":"<<"["<<row+1<<":"<<col<<"]"<<std::endl;
	}
	void debugEnter( void *frame,void *env,const char *func ){
	}
	void debugLeave(){
	}
	void debugLog( const char *msg ){
		if( msg && msg[0]=='@' && msg[1]=='@' ) std::cout<<msg<<std::endl;
	}
	void debugMsg( const char *msg,bool serious ){
		std::cout<<(serious?"[RuntimeError] ":"[msg] ")<<(msg?msg:"")<<std::endl;
	}
	void debugSys( void *msg ){
	}
};

static bool webDebugEnabled(){
	if( getenv( "SCPCB_RT_DEBUG" ) ) return true;
	return EM_ASM_INT({
		try{
			if( typeof Module!=='undefined' && Module['SCPCB_DEBUG'] ) return 1;
			if( typeof location!=='undefined' && /[?&]debug=1/.test( location.search ) ) return 1;
		}catch(e){}
		return 0;
	})!=0;
}

extern "C"
int BBCALL bbStart( int argc,char *argv[], BBMAIN bbMain ) {
	try {
		bbStartup( argc>0 ? argv[0] : "", "" );

		bool trace=false;
		static StdioDebugger debugger( trace );
		bbAttachDebugger( &debugger );

#ifdef BB_DEBUG
		bool debug=true;
#else
		bool debug=webDebugEnabled();
#endif
		bool ok=bbruntime_run( bbMain,debug );
		if( ok ) std::cout<<"[game ended]"<<std::endl;
		return ok?0:1;
	} catch( bbEx &x ) {
		std::cout<<"[bbEx uncaught] "<<x.err<<std::endl;
		return 1;
	} catch( std::exception &e ) {
		std::cout<<"[std::exception] "<<e.what()<<std::endl;
		return 1;
	} catch( ... ) {
		std::cout<<"[unknown exception]"<<std::endl;
		return 1;
	}
}
