#ifdef BB_LEGACY32
#include <bb/blitz/legacy32.h>
#endif
#include "canvas.h"

BBCanvas::~BBCanvas(){
#ifdef BB_LEGACY32
	s2ForgetPointer(this);
#endif
}

void BBCanvas::getLogicalViewport( int *x,int *y,int *w,int *h )const{
	float sx,sy;
	getScale( &sx,&sy );
	getViewport( x,y,w,h );
	*x/=sx;*y/=sy;*w/=sx;*h/=sy;
}
