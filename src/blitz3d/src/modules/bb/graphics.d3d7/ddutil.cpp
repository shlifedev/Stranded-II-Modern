
#include "ddutil.h"
#include "asmcoder.h"
#include "gxgraphics.h"

#if !defined(FOURCC_DXT1) && !defined(FOURCC_DXT1) && !defined(FOURCC_DXT1)
#define FOURCC_DXT1 0x31545844
#define FOURCC_DXT3 0x33545844
#define FOURCC_DXT5 0x35545844
#endif

static AsmCoder asm_coder;

static void calcShifts( unsigned mask,unsigned char *shr,unsigned char *shl ){
	if( mask ){
		for( *shl=0;!(mask&1);++*shl,mask>>=1 ){}
		for( *shr=8;mask&1;--*shr,mask>>=1 ){}
	}else *shr=*shl=0;
}

PixelFormat::~PixelFormat(){
	if( plot_code ){
		VirtualFree( plot_code,0,MEM_RELEASE );
	}
}

void PixelFormat::setFormat( const DDPIXELFORMAT &pf ){
	if( plot_code ){
		VirtualFree( plot_code,0,MEM_RELEASE );
	}

	if( !(pf.dwFlags & DDPF_RGB) ){
		memset( this,0,sizeof(*this) );
		return;
	}

	plot_code=(char*)VirtualAlloc( 0,128,MEM_COMMIT|MEM_RESERVE,PAGE_EXECUTE_READWRITE );
	point_code=plot_code+64;

	depth=pf.dwRGBBitCount;
	amask=pf.dwRGBAlphaBitMask;
	rmask=pf.dwRBitMask;
	gmask=pf.dwGBitMask;
	bmask=pf.dwBBitMask;
	pitch=depth/8;argbfill=0;
	if( !amask ) argbfill|=0xff000000;
	if( !rmask ) argbfill|=0x00ff0000;
	if( !gmask ) argbfill|=0x0000ff00;
	if( !bmask ) argbfill|=0x000000ff;
	calcShifts( amask,&ashr,&ashl );ashr+=24;
	calcShifts( rmask,&rshr,&rshl );rshr+=16;
	calcShifts( gmask,&gshr,&gshl );gshr+=8;
	calcShifts( bmask,&bshr,&bshl );
	plot=(Plot)(void*)plot_code;
	point=(Point)(void*)point_code;
	asm_coder.CodePlot( plot_code,depth,amask,rmask,gmask,bmask );
	asm_coder.CodePoint( point_code,depth,amask,rmask,gmask,bmask );
}

static void adjustTexSize( int *width,int *height,IDirect3DDevice7 *dir3dDev ){
    D3DDEVICEDESC7 ddDesc={0};
	if( dir3dDev->GetCaps( &ddDesc )<0 ){
		*width=*height=256;
		return;
	}
	int w=*width,h=*height,min,max;
		for( w=1;w<*width;w<<=1 ){}
		for( h=1;h<*height;h<<=1 ){}
    if( ddDesc.dpcTriCaps.dwTextureCaps & D3DPTEXTURECAPS_SQUAREONLY ){
		if( w>h ) h=w;
		else w=h;
	}
	if( max=ddDesc.dwMaxTextureAspectRatio ){
		int asp=w>h ? w/h : h/w;
		if( asp>max ){
			if( w>h ) h=w/max;
			else w=h/max;
		}
	}
	if( (min=ddDesc.dwMinTextureWidth) && w<min ) w=min;
	if( (min=ddDesc.dwMinTextureHeight) && h<min ) h=min;
	if( (max=ddDesc.dwMaxTextureWidth) && w>max ) w=max;
	if( (max=ddDesc.dwMaxTextureHeight) && h>max ) h=max;

	*width=w;*height=h;
}

static ddSurf *createSurface( int width,int height,int pitch,void *bits,IDirectDraw7 *dirDraw ){
	DDSURFACEDESC2 desc={sizeof(desc)};
	desc.dwFlags=DDSD_WIDTH|DDSD_HEIGHT|DDSD_LPSURFACE|DDSD_PITCH|DDSD_PIXELFORMAT|DDSD_CAPS;
	desc.ddsCaps.dwCaps=DDSCAPS_OFFSCREENPLAIN|DDSCAPS_SYSTEMMEMORY;
	desc.dwWidth=width;desc.dwHeight=height;
	desc.lPitch=pitch;desc.lpSurface=bits;
	desc.ddpfPixelFormat.dwSize=sizeof(DDPIXELFORMAT);
	desc.ddpfPixelFormat.dwFlags=DDPF_RGB|DDPF_ALPHAPIXELS;
	desc.ddpfPixelFormat.dwRGBBitCount=32;
	desc.ddpfPixelFormat.dwRBitMask=0xff0000;
	desc.ddpfPixelFormat.dwGBitMask=0x00ff00;
	desc.ddpfPixelFormat.dwBBitMask=0x0000ff;
	desc.ddpfPixelFormat.dwRGBAlphaBitMask=0xff000000;
	ddSurf *surf;
	if( dirDraw->CreateSurface( &desc,&surf,0 )>=0 ) return surf;
	return 0;
}

static void buildMask( ddSurf *surf ){
	DDSURFACEDESC2 desc={sizeof(desc)};
	surf->Lock( 0,&desc,DDLOCK_WAIT,0 );
	unsigned char *surf_p=(unsigned char*)desc.lpSurface;
	PixelFormat fmt( desc.ddpfPixelFormat );

	for( int y=0;y<desc.dwHeight;++y ){
		unsigned char *p=surf_p;
		for( int x=0;x<desc.dwWidth;++x ){
			unsigned argb=fmt.getPixel( p );
			unsigned rgb=argb&0xffffff;
			unsigned a=rgb ? 0xff000000 : 0;
			fmt.setPixel( p,a|rgb );
			p+=fmt.getPitch();
		}
		surf_p+=desc.lPitch;
	}
	surf->Unlock( 0 );
}

static void buildAlpha( ddSurf *surf,bool whiten ){

	DDSURFACEDESC2 desc={sizeof(desc)};
	surf->Lock( 0,&desc,DDLOCK_WAIT,0 );
	unsigned char *surf_p=(unsigned char*)desc.lpSurface;
	PixelFormat fmt( desc.ddpfPixelFormat );

	for( int y=0;y<desc.dwHeight;++y ){
		unsigned char *p=surf_p;
		for( int x=0;x<desc.dwWidth;++x ){
			unsigned argb=fmt.getPixel( p );
			unsigned alpha=(((argb>>16)&0xff)+((argb>>8)&0xff)+(argb&0xff))/3;
			argb=(alpha<<24) | (argb & 0xffffff);
			if( whiten ) argb|=0xffffff;
			fmt.setPixel( p,argb );
			p+=fmt.getPitch();
		}
		surf_p+=desc.lPitch;
	}
	surf->Unlock( 0 );
}

void ddUtil::buildMipMaps( ddSurf *surf ){

	DDSURFACEDESC2 desc={sizeof(desc)};
	surf->GetSurfaceDesc( &desc );
	if( !(desc.ddsCaps.dwCaps & DDSCAPS_TEXTURE) ) return;
	if( !(desc.ddpfPixelFormat.dwFlags & DDPF_RGB) ) return;

	DDSCAPS2 caps={0};
	caps.dwCaps=DDSCAPS_TEXTURE;
	caps.dwCaps2=DDSCAPS2_MIPMAPSUBLEVEL;

	IDirectDrawSurface7 *src=surf,*dest;

	while( src->GetAttachedSurface( &caps,&dest )>=0 ){

		DDSURFACEDESC2 src_desc={sizeof(src_desc)};
		if( src->Lock( 0,&src_desc,DDLOCK_WAIT,0 )<0 ) abort();
		unsigned char *src_p=(unsigned char*)src_desc.lpSurface;
		PixelFormat src_fmt( src_desc.ddpfPixelFormat );

		DDSURFACEDESC2 dest_desc={sizeof(dest_desc)};
		if( dest->Lock( 0,&dest_desc,DDLOCK_WAIT,0 )<0 ) abort();
		unsigned char *dest_p=(unsigned char *)dest_desc.lpSurface;
		PixelFormat dest_fmt( dest_desc.ddpfPixelFormat );

		if( src_desc.dwWidth==1 ){
			for( int y=0;y<dest_desc.dwHeight;++y ){
				unsigned p1=src_fmt.getPixel( src_p );
				unsigned p2=src_fmt.getPixel( src_p+src_desc.lPitch );
				unsigned argb=
					((p1&0xfefefefe)>>1)+((p2&0xfefefefe)>>1);
				argb+=( (
					(p1&0x01010101)+(p2&0x01010101) )>>1 ) & 0x01010101;
				dest_fmt.setPixel( dest_p,argb );
				src_p+=src_desc.lPitch*2;
				dest_p+=dest_desc.lPitch;
			}
		}else if( src_desc.dwHeight==1 ){
			for( int x=0;x<dest_desc.dwWidth;++x ){
				unsigned p1=src_fmt.getPixel( src_p );
				unsigned p2=src_fmt.getPixel( src_p+src_fmt.getPitch() );
				unsigned argb=
					((p1&0xfefefefe)>>1)+((p2&0xfefefefe)>>1);
				argb+=( (
					(p1&0x01010101)+(p2&0x01010101) )>>1 ) & 0x01010101;
				dest_fmt.setPixel( dest_p,argb );
				src_p+=src_fmt.getPitch()*2;
				dest_p+=dest_fmt.getPitch();
			}
		}else{
			for( int y=0;y<dest_desc.dwHeight;++y ){
				unsigned char *src_t=src_p;
				unsigned char *dest_t=dest_p;
				for( int x=0;x<dest_desc.dwWidth;++x ){

					unsigned p1=src_fmt.getPixel( src_t );
					unsigned p2=src_fmt.getPixel( src_t+src_fmt.getPitch() );
					unsigned p3=src_fmt.getPixel( src_t+src_desc.lPitch+src_fmt.getPitch() );
					unsigned p4=src_fmt.getPixel( src_t+src_desc.lPitch );

					unsigned argb=
						((p1&0xfcfcfcfc)>>2)+((p2&0xfcfcfcfc)>>2)+
						((p3&0xfcfcfcfc)>>2)+((p4&0xfcfcfcfc)>>2);
					argb+=( (
						(p1&0x03030303)+(p2&0x03030303)+
						(p3&0x03030303)+(p4&0x03030303) )>>2 ) & 0x03030303;

					dest_fmt.setPixel( dest_t,argb );
					src_t+=src_fmt.getPitch()*2;
					dest_t+=dest_fmt.getPitch();
				}
				src_p+=src_desc.lPitch*2;
				dest_p+=dest_desc.lPitch;
			}
		}
		src->Unlock( 0 );
		dest->Unlock( 0 );
		dest->Release();
		src=dest;
	}
}

void ddUtil::copy( ddSurf *dest,int dx,int dy,int dw,int dh,ddSurf *src,int sx,int sy,int sw,int sh ){

	DDSURFACEDESC2 src_desc={sizeof(src_desc)};
	src->Lock( 0,&src_desc,DDLOCK_WAIT,0 );
	PixelFormat src_fmt( src_desc.ddpfPixelFormat );
	unsigned char *src_p=(unsigned char*)src_desc.lpSurface;
	src_p+=src_desc.lPitch*sy+src_fmt.getPitch()*sx;

	DDSURFACEDESC2 dest_desc={sizeof(dest_desc)};
	dest->Lock( 0,&dest_desc,DDLOCK_WAIT,0 );
	PixelFormat dest_fmt( dest_desc.ddpfPixelFormat );
	unsigned char *dest_p=(unsigned char *)dest_desc.lpSurface;
	dest_p+=dest_desc.lPitch*dy+dest_fmt.getPitch()*dx;

	for( int y=0;y<dh;++y ){
		unsigned char *dest=dest_p;
		unsigned char *src=src_p+src_desc.lPitch*(y*sh/dh);
		for( int x=0;x<dw;++x ){
			dest_fmt.setPixel( dest,src_fmt.getPixel( src+src_fmt.getPitch()*(x*sw/dw) ) );
			dest+=dest_fmt.getPitch();
		}
		dest_p+=dest_desc.lPitch;
	}

	src->Unlock( 0 );
	dest->Unlock( 0 );
}

ddSurf *ddUtil::createSurface( int w,int h,int flags,gxGraphics *gfx ){

	DDSURFACEDESC2 desc={sizeof(desc)};

	desc.dwFlags=DDSD_CAPS;

	int hi=flags & gxCanvas::CANVAS_TEX_HICOLOR?1:0;

	if( w ){ desc.dwWidth=w;desc.dwFlags|=DDSD_WIDTH; }
	if( h ){ desc.dwHeight=h;desc.dwFlags|=DDSD_HEIGHT; }

	if( flags & gxCanvas::CANVAS_TEX_MASK ){
		desc.dwFlags|=DDSD_PIXELFORMAT;
		desc.ddpfPixelFormat=gfx->texRGBMaskFmt[hi];
	}else if( flags & gxCanvas::CANVAS_TEX_RGB ){
		desc.dwFlags|=DDSD_PIXELFORMAT;
		desc.ddpfPixelFormat=(flags&gxCanvas::CANVAS_TEX_ALPHA)?gfx->texRGBAlphaFmt[hi]:gfx->texRGBFmt[hi];
	}else if( flags & gxCanvas::CANVAS_TEX_ALPHA ){
		desc.dwFlags|=DDSD_PIXELFORMAT;
		desc.ddpfPixelFormat=gfx->texAlphaFmt[hi];
	}else if( flags & gxCanvas::CANVAS_TEXTURE ){
		desc.dwFlags|=DDSD_PIXELFORMAT;
		desc.ddpfPixelFormat=gfx->primFmt;
	}
	if( flags & gxCanvas::CANVAS_TEXTURE ){
		desc.ddsCaps.dwCaps|=DDSCAPS_TEXTURE;
		if( !(flags & gxCanvas::CANVAS_TEX_VIDMEM) ){
			desc.ddsCaps.dwCaps2|=DDSCAPS2_TEXTUREMANAGE;
			if( flags & gxCanvas::CANVAS_TEX_MIPMAP ){
				desc.ddsCaps.dwCaps|=DDSCAPS_MIPMAP|DDSCAPS_COMPLEX;
			}
		}
		if( flags & (gxCanvas::CANVAS_TEX_CUBE) ){
			desc.ddsCaps.dwCaps|=DDSCAPS_COMPLEX;
			desc.ddsCaps.dwCaps2|=DDSCAPS2_CUBEMAP|DDSCAPS2_CUBEMAP_ALLFACES;
		}
		adjustTexSize( (int*)&desc.dwWidth,(int*)&desc.dwHeight,gfx->dir3dDev );
	}else{
		desc.ddsCaps.dwCaps=DDSCAPS_OFFSCREENPLAIN;
		if( flags & gxCanvas::CANVAS_HIGHCOLOR ){
			desc.dwFlags|=DDSD_PIXELFORMAT;
			desc.ddsCaps.dwCaps|=DDSCAPS_SYSTEMMEMORY;
			desc.ddpfPixelFormat.dwSize=sizeof(DDPIXELFORMAT);
			desc.ddpfPixelFormat.dwFlags=DDPF_RGB|DDPF_ALPHAPIXELS;
			desc.ddpfPixelFormat.dwRGBBitCount=32;
			desc.ddpfPixelFormat.dwRBitMask=0xff0000;
			desc.ddpfPixelFormat.dwGBitMask=0x00ff00;
			desc.ddpfPixelFormat.dwBBitMask=0x0000ff;
			desc.ddpfPixelFormat.dwRGBAlphaBitMask=0xff000000;
		}else if( flags & gxCanvas::CANVAS_NONDISPLAY ){
			desc.ddsCaps.dwCaps|=DDSCAPS_SYSTEMMEMORY;
		}
	}
	ddSurf *surf;
	if( gfx->dirDraw->CreateSurface( &desc,&surf,0 )>=0 ) return surf;
	if( desc.ddsCaps.dwCaps & DDSCAPS_OFFSCREENPLAIN ){
		if( !(desc.ddsCaps.dwCaps & DDSCAPS_SYSTEMMEMORY) ){
			desc.ddsCaps.dwCaps|=DDSCAPS_SYSTEMMEMORY;
			if( gfx->dirDraw->CreateSurface( &desc,&surf,0 )>=0 ) return surf;
		}
	}
	return 0;
}

IDirectDrawSurface7 *loadDXTC(const char* filename,gxGraphics *gfx)
{
	HRESULT hr;
	DDSURFACEDESC2 ddsd;
	DDSURFACEDESC2 fileddsd;
	char magicID[4];
	FILE *fp;

	fp = fopen(filename, "rb");
	if(!fp) return NULL;

	fread(magicID, 1, 4, fp);
	if (strncmp(magicID, "DDS ", 4) != 0)
	{
		fclose(fp);
		return NULL;
	}

	fread(&fileddsd, sizeof(DDSURFACEDESC2), 1, fp);

	if (fileddsd.dwSize != sizeof(DDSURFACEDESC2))
	{
		fclose(fp);
		return NULL;
	}

	memcpy(&ddsd, &fileddsd,sizeof(DDSURFACEDESC2));

	int blockSize = 0;
	int chunkSize = 0;

	if(ddsd.ddpfPixelFormat.dwFourCC == FOURCC_DXT1)
		blockSize = 8;
	if(ddsd.ddpfPixelFormat.dwFourCC == FOURCC_DXT3)
		blockSize = 16;
	if(ddsd.ddpfPixelFormat.dwFourCC == FOURCC_DXT5)
		blockSize = 16;

	if (blockSize == 0)
	{
		fclose(fp);
		return NULL;
	}

	ddsd.ddsCaps.dwCaps2|=DDSCAPS2_TEXTUREMANAGE;

	IDirectDrawSurface7 * newSurf = NULL;
	hr = gfx->dirDraw->CreateSurface(&ddsd, &newSurf, NULL);
	if(FAILED(hr))
	{
		fclose(fp);
		return NULL;
	}

	DDSCAPS2 mipmapddsd;
	ZeroMemory(&mipmapddsd,sizeof(DDSCAPS2));
	mipmapddsd.dwCaps = DDSCAPS_TEXTURE|DDSCAPS_MIPMAP|DDSCAPS_COMPLEX;

	IDirectDrawSurface7 *topDDS = NULL;
	IDirectDrawSurface7 *nextDDS = NULL;

	topDDS = newSurf;
	topDDS->AddRef();

	while(TRUE)
	{
		hr = topDDS->Lock(NULL,&ddsd,DDLOCK_WAIT,NULL);
		if(FAILED(hr))
		{
			fclose(fp);
			topDDS->Release();
			newSurf->Release();
			nextDDS->Release();
			return NULL;
		}

		chunkSize = ((ddsd.dwWidth+3)/4) * ((ddsd.dwHeight+3)/4) * blockSize;

		if(!fread(ddsd.lpSurface, chunkSize, 1, fp))
		{
			fclose(fp);
			topDDS->Release();
			newSurf->Release();
			nextDDS->Release();
			return NULL;
		}
		topDDS->Unlock(NULL);


		hr = topDDS->GetAttachedSurface(&mipmapddsd,&nextDDS);
		if(FAILED(hr))
		{
			fclose(fp);
			topDDS->Release();
			break;
		}

		topDDS->Release();
		topDDS = nextDDS;
		nextDDS->Release();
	}

	return newSurf;
}

ddSurf *ddUtil::loadSurface( const std::string &f,int flags,gxGraphics *gfx ){

	int i=f.find( ".dds" );
	if( i!=std::string::npos && i+4==f.size() ){
		ddSurf *surf=loadDXTC( f.c_str(),gfx );
		return surf;
	}

	BBPixmap *pm=bbLoadPixmap( f );
	if( !pm ){
		return 0;
	}

	int width=pm->width;
	int height=pm->height;
	int pitch=pm->pitch;
	bool trans=pm->trans;
	void *bits=pm->bits;

	ddSurf *src=::createSurface( width,height,pitch,bits,gfx->dirDraw );
	if( !src ){
		delete pm;
		return 0;
	}

	if( flags & gxCanvas::CANVAS_TEX_ALPHA ){
		if( flags & gxCanvas::CANVAS_TEX_MASK ){
			buildMask( src );
		}else if( !trans ){
			buildAlpha( src,(flags & gxCanvas::CANVAS_TEX_RGB)?false:true );
		}
	}else{
		unsigned char *p=(unsigned char *)bits;
		for( int k=0;k<height;++k ){
			unsigned char *t=p+3;
			for( int j=0;j<width;++j ){
				*t=0xff;t+=4;
			}
			p+=pitch;
		}
	}

	ddSurf *dest=createSurface( width,height,flags,gfx );
	if( !dest ){
		src->Release();
		delete pm;
		return 0;
	}

	int t_w=width,t_h=height;
	if( flags & gxCanvas::CANVAS_TEXTURE ) adjustTexSize( &t_w,&t_h,gfx->dir3dDev );
	copy( dest,0,0,t_w,t_h,src,0,height-1,width,-height );

	src->Release();
	delete pm;
	return dest;
}
