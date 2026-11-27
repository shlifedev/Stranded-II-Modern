#include "context.egl.h"

#include "egl.h"

#ifdef BB_NX
#include <switch.h>
#endif

class EGLGraphics : public GLGraphics{
protected:
	int window_width,window_height,drawable_width,drawable_height;

	unsigned short gamma_red[256], gamma_green[256], gamma_blue[256];

	static void onAppChange( void *data,void *context );
public:
	EGLGraphics( int w,int h );
	~EGLGraphics();

	void backup();
	bool restore();

	void vwait();

	void copy( BBCanvas *dest,int dx,int dy,int dw,int dh,BBCanvas *src,int sx,int sy,int sw,int sh );

	void setGamma( int r,int g,int b,float dr,float dg,float db );
	void getGamma( int r,int g,int b,float *dr,float *dg,float *db );
	void updateGamma( bool calibrate );

	int getWidth()const;
	int getHeight()const;
	int getLogicalWidth()const;
	int getLogicalHeight()const;
	int getDepth()const;
	float getDensity()const;
	int getScanLine()const;
	int getAvailVidmem()const;
	int getTotalVidmem()const;

	void moveMouse( int x,int y );
};

void EGLGraphics::onAppChange( void *data,void *context ){
}

EGLGraphics::EGLGraphics( int w,int h ){
	for( int k=0;k<256;++k ) gamma_red[k]=gamma_green[k]=gamma_blue[k]=k;

	window_width=drawable_width=w;
	window_height=drawable_height=h;
	((GLCanvas*)front_canvas)->resize( w,h,1.0 );
	((GLCanvas*)back_canvas)->resize( w,h,1.0 );

	bbAppOnChange.add( onAppChange,this );
}

EGLGraphics::~EGLGraphics(){
	bbAppOnChange.remove( onAppChange,this );

}

void EGLGraphics::backup(){
}

bool EGLGraphics::restore(){
	return true;
}

void EGLGraphics::vwait(){}

void EGLGraphics::copy( BBCanvas *dest,int dx,int dy,int dw,int dh,BBCanvas *src,int sx,int sy,int sw,int sh ){
	dest->blit( dx,dy,src,sx,sy,sw,sh,false );
}

void EGLGraphics::setGamma( int r,int g,int b,float dr,float dg,float db ){
	gamma_red[r&255]=dr*257.0f;
	gamma_red[g&255]=dg*257.0f;
	gamma_red[b&255]=db*257.0f;
}

void EGLGraphics::getGamma( int r,int g,int b,float *dr,float *dg,float *db ){
	*dr=gamma_red[r&255];*dg=gamma_green[g&255];*db=gamma_blue[b&255];
}

void EGLGraphics::updateGamma( bool calibrate ){
}

int EGLGraphics::getWidth()const{ return drawable_width; }
int EGLGraphics::getHeight()const{ return drawable_height; }
int EGLGraphics::getLogicalWidth()const{ return window_width; };
int EGLGraphics::getLogicalHeight()const{ return window_height; };
int EGLGraphics::getDepth()const{ return 0; }
float EGLGraphics::getDensity()const{ return (float)drawable_width/window_width; };
int EGLGraphics::getScanLine()const{ return 0; }
int EGLGraphics::getAvailVidmem()const{ return 0; }
int EGLGraphics::getTotalVidmem()const{ return 0; }

void EGLGraphics::moveMouse( int x,int y ){
}

EGLContextDriver::EGLContextDriver(){
	bbSceneDriver=this;

#ifdef BB_NX
	egl.create( nwindowGetDefault(),0 );
#endif
}

EGLContextDriver::~EGLContextDriver(){
	egl.destroy();
}

int EGLContextDriver::numGraphicsDrivers(){
	return 1;
}

void EGLContextDriver::graphicsDriverInfo( int driver,std::string *name,int *c ){
}

int EGLContextDriver::numGraphicsModes( int driver ){
	return 1;
}

void EGLContextDriver::graphicsModeInfo( int driver,int mode,int *w,int *h,int *d,int *c ){

}

void EGLContextDriver::windowedModeInfo( int *c ){
	*c=GFXMODECAPS_3D;
}

BBGraphics *EGLContextDriver::openGraphics( int w,int h,int d,int driver,int flags ){
	if( graphics ) return 0;

	graphics=d_new EGLGraphics( w,h );
		return graphics;
}

void EGLContextDriver::closeGraphics( BBGraphics *g ){
	if( graphics!=g || !g ) return;
	delete graphics;graphics=0;
}

bool EGLContextDriver::graphicsLost(){
	return false;
}

void EGLContextDriver::flip( bool vwait ){
	egl.swap();
}

static BBContextDriver *createEGLContext( const std::string &name ){
	if( name.find("gl")!=std::string::npos ){
		return 0;
	}

	return d_new EGLContextDriver();
}

BBMODULE_CREATE( context_egl ){
	bbContextDrivers.push_back( createEGLContext );

	return true;
}

BBMODULE_DESTROY( context_egl ){
	return true;
}
