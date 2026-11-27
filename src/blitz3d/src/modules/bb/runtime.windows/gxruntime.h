
#ifndef GXRUNTIME_H
#define GXRUNTIME_H

#include <bb/runtime/runtime.h>
#include <bb/frame/frame.h>
#include <bb/graphics.d3d7/graphics.d3d7.h>
#include <string>
#include <vector>

class gxRuntime : public BBRuntime, public D3D7ContextDriver, public Frame{
public:
	BBEnv env;

	HINSTANCE hinst;

	void moveMouse( int x,int y );

	LRESULT windowProc( HWND hwnd,UINT msg,WPARAM w,LPARAM l );

private:
	gxRuntime( HINSTANCE hinst,HWND hwnd );
	~gxRuntime();

	void afterCreate();

	void refreshSystemProperties();

	void suspend();
	void forceSuspend();
	void resume();
	void forceResume();

	void backupGraphics();
	void restoreGraphics();

public:
	static gxRuntime *openRuntime( HINSTANCE hinst );

	void asyncStop();
	void asyncRun();
	void asyncEnd();

public:
	void *window();

	bool idle();

	void setPointerVisible( bool vis );


	static void _onDebugStop( void *data,void *context );
	void onDebugStop();
	static void _onDebugError( void *data,void *context );
	void onDebugError( const char *t );
	static void _onDebugInfo( void *data,void *context );
	void onDebugInfo( const char *t );

	BBGraphics *openGraphics( int w,int h,int d,int driver,int flags );
	void closeGraphics( BBGraphics *graphics );
	bool graphicsLost();
};

#endif
