#include <bb/blitz/blitz.h>
#include <bb/filesystem/filesystem.h>
#include "pixmap.h"

#include <string>
#include <cstring>
#include <cstdint>

#define STB_IMAGE_IMPLEMENTATION
#define STBI_ONLY_JPEG
#define STBI_ONLY_PNG
#define STBI_ONLY_BMP
#define STBI_ONLY_TGA
#define STBI_NO_STDIO
#include "../graphics.webgpu/stb_image.h"

static bool pngHasAlpha( const std::string &d ){
	static const unsigned char sig[8]={ 137,80,78,71,13,10,26,10 };
	if( d.size()<33 || memcmp( d.data(),sig,8 )!=0 ) return false;

	unsigned char color_type=(unsigned char)d[25];
	if( color_type==4 || color_type==6 ) return true;

	size_t pos=8;
	while( pos+8<=d.size() ){
		uint32_t len=((uint32_t)(unsigned char)d[pos]<<24)|((uint32_t)(unsigned char)d[pos+1]<<16)|
		             ((uint32_t)(unsigned char)d[pos+2]<<8)|(uint32_t)(unsigned char)d[pos+3];
		const char *tag=d.data()+pos+4;
		if( memcmp( tag,"tRNS",4 )==0 ) return true;
		if( memcmp( tag,"IDAT",4 )==0 ) return false;
		pos+=12+(size_t)len;
	}
	return false;
}

BBPixmap *bbLoadPixmapWithFreeImage( const std::string &path ){
	std::streambuf *sb=gx_filesys->openFile( path,std::ios_base::in );
	if( !sb ) return 0;
	std::string data; char tmp[8192]; std::streamsize n;
	while( (n=sb->sgetn(tmp,sizeof tmp))>0 ) data.append( tmp,(size_t)n );
	delete sb;
	if( data.empty() ) return 0;

	int w=0,h=0,ch=0;
	stbi_uc *pix=stbi_load_from_memory( (const stbi_uc*)data.data(),(int)data.size(),&w,&h,&ch,4 );
	if( !pix ) return 0;

	BBPixmap *pm=new BBPixmap();
	pm->format=PF_RGBA;
	pm->width=w; pm->height=h; pm->depth=32; pm->pitch=w*4; pm->bpp=4;
	pm->trans=(ch==4) || pngHasAlpha( data );
	pm->bits=new unsigned char[(size_t)w*4*h];
	for( int y=0;y<h;y++ ){
		uint32_t *dst=(uint32_t*)(pm->bits+(size_t)(h-1-y)*w*4);
		const uint32_t *src=(const uint32_t*)(pix+(size_t)y*w*4);
		for( int x=0;x<w;x++ ){
			uint32_t v=src[x];
			dst[x]=(v&0xFF00FF00u)|((v&0x00FF0000u)>>16)|((v&0x000000FFu)<<16);
		}
	}
	stbi_image_free( pix );
	return pm;
}

BBPixmap *bbLoadPixmapRGBA( const std::string &path ){
	std::streambuf *sb=gx_filesys->openFile( path,std::ios_base::in );
	if( !sb ) return 0;
	std::string data; char tmp[65536]; std::streamsize n;
	while( (n=sb->sgetn(tmp,sizeof tmp))>0 ) data.append( tmp,(size_t)n );
	delete sb;
	if( data.empty() ) return 0;

	int w=0,h=0,ch=0;
	stbi_uc *pix=stbi_load_from_memory( (const stbi_uc*)data.data(),(int)data.size(),&w,&h,&ch,4 );
	if( !pix ) return 0;

	BBPixmap *pm=new BBPixmap();
	pm->format=PF_RGBA;
	pm->width=w; pm->height=h; pm->depth=32; pm->pitch=w*4; pm->bpp=4;
	pm->trans=(ch==4) || pngHasAlpha( data );
	pm->bits=new unsigned char[(size_t)w*4*h];
	memcpy( pm->bits,pix,(size_t)w*4*h );
	stbi_image_free( pix );
	return pm;
}
