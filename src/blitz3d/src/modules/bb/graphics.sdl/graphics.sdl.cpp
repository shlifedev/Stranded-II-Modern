#include "../../../stdutil/stdutil.h"
#include "graphics.sdl.h"

void SDLGraphics::onAppChange( void *data,void *context ){
	SDLGraphics *graphics=(SDLGraphics*)context;
	SDL_SetWindowTitle( graphics->wnd,bbApp().title.c_str() );
}

SDLGraphics::SDLGraphics( SDL_Window *wnd,SDL_GLContext ctx ):wnd(wnd),context(ctx){
	for( int k=0;k<256;++k ) gamma_red[k]=gamma_green[k]=gamma_blue[k]=k;

	bbAppOnChange.add( onAppChange,this );

	resize();
}

SDLGraphics::~SDLGraphics(){
	bbAppOnChange.remove( onAppChange,this );

	SDL_GL_DeleteContext( context );
	SDL_DestroyWindow( wnd );wnd=0;
}

void SDLGraphics::resize(){
	SDL_GetWindowSize( wnd,&window_width,&window_height );
	SDL_GL_GetDrawableSize( wnd,&drawable_width,&drawable_height );

	float sx=(float)drawable_width/window_width;
	float sy=(float)drawable_height/window_height;
	((GLCanvas*)front_canvas)->setScale( sx,sy );
	((GLCanvas*)back_canvas)->setScale( sx,sy );

	((GLCanvas*)front_canvas)->resize( drawable_width,drawable_height,getDensity() );
	((GLCanvas*)back_canvas)->resize( drawable_width,drawable_height,getDensity() );
}

void SDLGraphics::backup(){
}

bool SDLGraphics::restore(){
	return true;
}

void SDLGraphics::vwait(){}

void SDLGraphics::copy( BBCanvas *dest,int dx,int dy,int dw,int dh,BBCanvas *src,int sx,int sy,int sw,int sh ){
	dest->blit( dx,dy,src,sx,sy,sw,sh,false );
}

void SDLGraphics::setGamma( int r,int g,int b,float dr,float dg,float db ){
	gamma_red[r&255]=dr*257.0f;
	gamma_green[g&255]=dg*257.0f;
	gamma_blue[b&255]=db*257.0f;
}

void SDLGraphics::getGamma( int r,int g,int b,float *dr,float *dg,float *db ){
	*dr=gamma_red[r&255];*dg=gamma_green[g&255];*db=gamma_blue[b&255];
}

void SDLGraphics::updateGamma( bool calibrate ){
	SDL_SetWindowGammaRamp( wnd,gamma_red,gamma_green,gamma_blue );
}

int SDLGraphics::getWidth()const{ return drawable_width; }
int SDLGraphics::getHeight()const{ return drawable_height; }
int SDLGraphics::getLogicalWidth()const{ return window_width; }
int SDLGraphics::getLogicalHeight()const{ return window_height; }
int SDLGraphics::getDepth()const{ return 0; }
float SDLGraphics::getDensity()const{
	if( window_width<=0 || drawable_width<=0 ) return 1.0f;
	return (float)drawable_width/window_width;
}
int SDLGraphics::getScanLine()const{ return 0; }
int SDLGraphics::getAvailVidmem()const{ return 0; }
int SDLGraphics::getTotalVidmem()const{ return 0; }

void SDLGraphics::moveMouse( int x,int y ){
	SDL_WarpMouseInWindow( wnd,x,y );
}


SDLContextDriver::SDLContextDriver(){
	bbSceneDriver=this;
}

static const int kWebModes[][2]={
	{1280,720},{1920,1080},{1600,900},{1366,768},{1024,768},{800,600},{640,480}
};

int SDLContextDriver::numGraphicsDrivers(){
	return 1;
}

void SDLContextDriver::graphicsDriverInfo( int driver,std::string *name,int *c ){
	*name="WebGL";
	*c=GFXMODECAPS_3D;
}

int SDLContextDriver::numGraphicsModes( int driver ){
	return (int)(sizeof(kWebModes)/sizeof(kWebModes[0]));
}

void SDLContextDriver::graphicsModeInfo( int driver,int mode,int *w,int *h,int *d,int *c ){
	int n=(int)(sizeof(kWebModes)/sizeof(kWebModes[0]));
	if( mode<0 ) mode=0;
	if( mode>=n ) mode=n-1;
	*w=kWebModes[mode][0];*h=kWebModes[mode][1];*d=32;*c=GFXMODECAPS_3D;
}

void SDLContextDriver::windowedModeInfo( int *c ){
	*c=GFXMODECAPS_3D;
}

BBGraphics *SDLContextDriver::openGraphics( int w,int h,int d,int driver,int flags ){
	LOGD( "[openGraphics] requested %dx%d d=%d flags=%d existing=%d",w,h,d,flags,graphics?1:0 );
	if( graphics ) return 0;

	if( SDL_Init(SDL_INIT_VIDEO)<0 ){
		LOGD( "%s","failed to init sdl" );
		return 0;
	}

	SDL_GL_SetAttribute( SDL_GL_CONTEXT_MAJOR_VERSION,3 );
	SDL_GL_SetAttribute( SDL_GL_CONTEXT_MINOR_VERSION,0 );

	SDL_Window* wnd=SDL_CreateWindow( bbApp().title.c_str(),SDL_WINDOWPOS_UNDEFINED,SDL_WINDOWPOS_UNDEFINED,1,1,SDL_WINDOW_OPENGL|SDL_WINDOW_ALLOW_HIGHDPI );
	if( wnd==NULL ){
		LOGD( "%s","failed to create window" );
		return 0;
	}

	SDL_SetWindowSize( wnd,w,h );
	SDL_ShowWindow( wnd );

	SDL_GLContext context;
	if( !(context=SDL_GL_CreateContext( wnd )) ){
		LOGD( "%s",SDL_GetError() );
		return 0;
	}

	if( SDL_GL_MakeCurrent( wnd,context ) ){
		LOGD( "%s",SDL_GetError() );
		return 0;
	}

	int screen_w,screen_h;
	int drawableW,drawableH;

	SDL_GetWindowSize( wnd,&screen_w,&screen_h );
	SDL_GL_GetDrawableSize( wnd,&drawableW,&drawableH );

	LOGD( "GL Version:  %s",glGetString( GL_VERSION ) );
	LOGD( "GL Vendor:   %s",glGetString( GL_VENDOR ) );
	LOGD( "GL window:   %ix%i",screen_w,screen_h );
	LOGD( "GL drawable: %ix%i",drawableW,drawableH );

	GL( glViewport( 0,0,drawableW,drawableH ) );
	GL( glScissor( 0,0,drawableW,drawableH ) );
	GL( glClearColor( 0.0,0.0,0.0,1.0 ) );
	GL( glClear( GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT ) );

	SDL_SetWindowFullscreen( wnd,0 );

	graphics=d_new SDLGraphics( wnd,context );
	if( graphics->init() ){
		SDL_RaiseWindow( wnd );
		return graphics;
	}
	delete graphics;
	return 0;
}

void SDLContextDriver::closeGraphics( BBGraphics *g ){
	if( graphics!=g || !g ) return;
	delete graphics;graphics=0;
}

bool SDLContextDriver::graphicsLost(){
	return false;
}

#ifdef BB_EMSCRIPTEN
#include <emscripten.h>
EM_ASYNC_JS( void, bb_wait_raf, (), {
	await new Promise( function( resolve ){
		var done=false;
		var raf=0;
		var tid=0;
		var fin=function(){
			if( done ) return;
			done=true;
			if( raf ) cancelAnimationFrame( raf );
			if( tid ) clearTimeout( tid );
			resolve();
		};
		if( typeof requestAnimationFrame==='function' ) raf=requestAnimationFrame( fin );
		tid=setTimeout( fin,250 );
	} );
} );
#endif

void SDLContextDriver::flip( bool vwait ){
#ifdef BB_EMSCRIPTEN
	static bool hinted;
	if( !hinted ){ SDL_SetHint( SDL_HINT_EMSCRIPTEN_ASYNCIFY,"0" );hinted=true; }
	SDL_GL_SwapWindow( ((SDLGraphics*)graphics)->wnd );
	if( vwait ){
		bb_wait_raf();
	}else{
		emscripten_sleep( 0 );
	}
#endif
}

static BBContextDriver *createSDLContext( const std::string &name ){
	if( name.find("webgpu")!=std::string::npos ){
		return 0;
	}
	if( name.find("gl")!=std::string::npos&&name.find("sdl")!=std::string::npos ){
		return 0;
	}

	return d_new SDLContextDriver();
}

BBMODULE_CREATE( graphics_sdl ){
	bbContextDrivers.insert( bbContextDrivers.begin(),createSDLContext );

	return true;
}

BBMODULE_DESTROY( graphics_sdl ){
	return true;
}
