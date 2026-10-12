
#include <bb/blitz/blitz.h>
#include "pixmap.h"
#include "../../../stdutil/stdutil.h"

#include <string.h>
#include <stdint.h>

BBPixmap::BBPixmap():format(PF_UNKNOWN),width(0),height(0),depth(0),pitch(0),bpp(0),trans(false),bits(0){
}

BBPixmap::~BBPixmap(){
	delete[] bits;
}

int BBPixmap::getWidth(){
	return width;
}

int BBPixmap::getHeight(){
	return height;
}

int BBPixmap::read( int x,int y ){
	return bits[((size_t)y*width+x)*bpp];
}

BBPixmap *bbLoadPixmapWithFreeImage( const std::string &file );

BBPixmap *bbLoadPixmap( const std::string &file ){
	return bbLoadPixmapWithFreeImage( canonicalpath( file ) );
}

void BBPixmap::mask( int r,int g,int b ){
	size_t n=(size_t)width*height;
	for( size_t i=0;i<n;i++ ){
		unsigned char *p=&bits[bpp*i];
		if( p[0]==r && p[1]==g && p[2]==b ) p[3]=0;
	}
}

void BBPixmap::buildAlpha( bool whiten ){
	size_t n=(size_t)width*height;
	for( size_t i=0;i<n;i++ ){
		unsigned char *p=&bits[bpp*i];
		p[3]=(p[0]+p[1]+p[2])/3;
		if( whiten ) p[0]=p[1]=p[2]=255;
	}
}

void BBPixmap::fill( int r,int g,int b,float a ){
	size_t n=(size_t)width*height;
	for( size_t i=0;i<n;i++ ){
		unsigned char *p=&bits[bpp*i];
		p[0]=r;
		p[1]=g;
		p[2]=b;
		p[3]=a*255;
	}
}

void BBPixmap::flipVertically(){
	if( !bits || height<2 ) return;
	size_t scanline=(size_t)width*bpp;
	unsigned char *row=new unsigned char[scanline];
	unsigned char *top=bits,*bot=bits+(size_t)(height-1)*scanline;
	for( int y=0;y<height/2;y++ ){
		memcpy( row,top,scanline );
		memcpy( top,bot,scanline );
		memcpy( bot,row,scanline );
		top+=scanline;bot-=scanline;
	}
	delete[] row;
}

void BBPixmap::swapBytes0and2(){
	size_t n=(size_t)width*height;
	if( bpp==4 ){
		uint32_t *p=(uint32_t*)bits;
		for( size_t i=0;i<n;i++ ){
			uint32_t v=p[i];
			p[i]=(v&0xFF00FF00u)|((v&0x00FF0000u)>>16)|((v&0x000000FFu)<<16);
		}
		return;
	}
	for( size_t i=0;i<n;i++ ){
		unsigned char *p=&bits[bpp*i],tmp;
		tmp=p[0];
		p[0]=p[2];
		p[2]=tmp;
	}
}

BBMODULE_CREATE( pixmap ){
  return true;
}

BBMODULE_DESTROY( pixmap ){
  return true;
}
