#include <bb/debugui/profiler.h>
#include "../../../stdutil/stdutil.h"
#include <bb/graphics/font.h>
#include "canvas.webgpu.h"
#include <bb/debugui/debugui.h>
#include <utf8.h>

#include <cmath>
#include <cstring>
#include <algorithm>
#include <vector>


WebGPUCanvas::WebGPUCanvas( WebGPUContextResources *res,int w,int h,int f ):
	res(res),width(w),height(h),pixels(0),lock_count(0),font(0),
	scale_x(1.0f),scale_y(1.0f),origin_x(0),origin_y(0),handle_x(0),handle_y(0),
	mask(0),pixmap(0),dirty(false),pixmap_stale(false),clear_pending(false),
	gpu_written(false),is_surface(false),wide_format(false),use_mips(false),mip_levels(1),level0_view(0),mips_dirty(false),hit_valid(false),font_pass(false),cube_face(0),cube_mode(0),
	texture(0),twidth(0),theight(0),texture_view(0){
	flags=f;

	color[0]=color[1]=color[2]=1.0f;
	cls_color[0]=cls_color[1]=cls_color[2]=0.0f;
	cls_argb=0;

	vx=vy=0;
	vw=w;vh=h;
}

WebGPUCanvas::WebGPUCanvas( WebGPUContextResources *res,int f ):WebGPUCanvas( res,0,0,f ){
}

WebGPUCanvas::~WebGPUCanvas(){
	s2DebugUiForgetCanvas(this);
	if( res->pass_target==this ) res->endPass();
	releaseBlitSnapshot();
	res->notifyCanvasDestroyed( this );
	if( level0_view ){
		wgpuTextureViewRelease( level0_view );
		level0_view=0;
	}
	if( texture_view ){
		res->invalidateBindGroupsFor( texture_view );
		wgpuTextureViewRelease( texture_view );
		texture_view=0;
	}
	if( texture ){
		wgpuTextureRelease( texture );
		texture=0;
	}
	if( pixels ){
		delete[] pixels;pixels=0;
	}
	if( pixmap ){
		delete pixmap;pixmap=0;
	}
}

void WebGPUCanvas::resize( int w,int h,float d ){
	blit_snapshot_valid=false;
	width=w;height=h;
	vx=vy=0;vw=w;vh=h;
}


WGPUTextureFormat WebGPUCanvas::format()const{
	if( is_surface ) return res->surface_format;
	return wide_format?WGPUTextureFormat_RGB10A2Unorm:WGPUTextureFormat_RGBA8Unorm;
}

void WebGPUCanvas::ensureTexture(){
	if( is_surface ) return;

	int w=width>0?width:1,h=height>0?height:1;
	if( texture && twidth==w && theight==h ) return;
	blit_snapshot_valid=false;

	if( level0_view ){
		wgpuTextureViewRelease( level0_view );
		level0_view=0;
	}
	if( texture_view ){
		res->invalidateBindGroupsFor( texture_view );
		wgpuTextureViewRelease( texture_view );
		texture_view=0;
	}
	if( texture ){
		wgpuTextureRelease( texture );
		texture=0;
	}

	WGPUTextureDescriptor desc={};
	desc.label=bbStrView( "bb.canvas" );
	desc.usage=WGPUTextureUsage_TextureBinding|WGPUTextureUsage_RenderAttachment|
	           WGPUTextureUsage_CopySrc|WGPUTextureUsage_CopyDst;
	desc.dimension=WGPUTextureDimension_2D;
	desc.size={ (uint32_t)w,(uint32_t)h,1 };
	desc.format=format();
	mip_levels=1;
	if( use_mips && !wide_format ){
		int longest=w>h?w:h;
		while( longest>1 ){ longest>>=1;++mip_levels; }
	}
	desc.mipLevelCount=(uint32_t)mip_levels;
	desc.sampleCount=1;
	texture=wgpuDeviceCreateTexture( res->device,&desc );
	texture_view=wgpuTextureCreateView( texture,0 );
	twidth=w;theight=h;
}

WGPUTextureView WebGPUCanvas::targetView(){
	if( is_surface ) return res->acquireSurfaceView();
	if( dirty ) uploadData();
	ensureTexture();
	if( mip_levels>1 ){
		if( !level0_view ){
			WGPUTextureViewDescriptor vd={};
			vd.format=format();
			vd.dimension=WGPUTextureViewDimension_2D;
			vd.baseMipLevel=0;
			vd.mipLevelCount=1;
			vd.baseArrayLayer=0;
			vd.arrayLayerCount=1;
			vd.aspect=WGPUTextureAspect_All;
			level0_view=wgpuTextureCreateView( texture,&vd );
		}
		mips_dirty=true;
		return level0_view;
	}
	return texture_view;
}

WGPUTextureView WebGPUCanvas::sampleView(){
	if( is_surface ){
		res->surface_sampled=true;
		return res->acquireSurfaceView();
	}
	if( dirty ) uploadData();
	ensureTexture();
	if( mips_dirty ){
		mips_dirty=false;
		res->flush();
		res->generateMips( texture,mip_levels );
	}
	return texture_view;
}

bool WebGPUCanvas::consumeClearPending(){
	bool c=clear_pending;
	clear_pending=false;
	return c;
}

void WebGPUCanvas::getClsColorf( float col[4] )const{
	col[0]=cls_color[0];col[1]=cls_color[1];col[2]=cls_color[2];col[3]=1.0f;
}


void WebGPUCanvas::uploadData(){
	blit_snapshot_valid=false;
	if( !res->device ) return;
	if( wide_format ){
		dirty=false;
		return;
	}

	BBPixmap *pm=0;
	const void *data=0;

	if( pixels ){
		data=pixels;
	}else if( pixmap && pixmap->bits ){
		if( (flags&CANVAS_TEXTURE) && (flags&CANVAS_TEX_MASK) ){
			pm=d_new BBPixmap;
			pm->format=pixmap->format;
			pm->width=pixmap->width;
			pm->height=pixmap->height;
			pm->depth=pixmap->depth;
			pm->pitch=pixmap->pitch;
			pm->bpp=pixmap->bpp;
			pm->trans=pixmap->trans;

			size_t size=(size_t)pm->width*pm->bpp*pm->height;
			pm->bits=new unsigned char[size];
			memcpy( pm->bits,pixmap->bits,size );
			pm->mask( (mask>>16)&255,(mask>>8)&255,mask&255 );

			data=pm->bits;
		}else{
			data=pixmap->bits;
		}
	}

	dirty=false;
	if( !data ){
		ensureTexture();
		delete pm;
		return;
	}

	res->flush();

	WGPUTexture tex;
	WGPUTextureFormat fmt=format();
	if( is_surface ){
		if( !res->acquireSurfaceView() ){ delete pm;return; }
		tex=res->surface_texture;
	}else{
		ensureTexture();
		tex=texture;
	}

	const unsigned char *src=(const unsigned char*)data;
	std::vector<unsigned char> swapped;
	if( fmt==WGPUTextureFormat_BGRA8Unorm ){
		swapped.resize( (size_t)width*height*4 );
		for( size_t i=0;i<(size_t)width*height;i++ ){
			swapped[i*4+0]=src[i*4+2];
			swapped[i*4+1]=src[i*4+1];
			swapped[i*4+2]=src[i*4+0];
			swapped[i*4+3]=src[i*4+3];
		}
		src=swapped.data();
	}

	WGPUTexelCopyTextureInfo dst={};
	dst.texture=tex;
	dst.aspect=WGPUTextureAspect_All;
	WGPUTexelCopyBufferLayout layout={};
	layout.offset=0;
	layout.bytesPerRow=(uint32_t)width*4;
	layout.rowsPerImage=(uint32_t)height;
	WGPUExtent3D extent={ (uint32_t)width,(uint32_t)height,1 };
	wgpuQueueWriteTexture( res->queue,&dst,src,(size_t)width*height*4,&layout,&extent );
	if( mip_levels>1 && fmt==WGPUTextureFormat_RGBA8Unorm ) res->generateMips( tex,mip_levels );

	// LockBuffer writes must also invalidate/update the retained CPU copy.
	if( pixels && pixmap && pixmap->bits && pixmap->bpp==4 &&
	    pixmap->width==width && pixmap->height==height ){
		memcpy( pixmap->bits,pixels,(size_t)width*height*4 );
	}
	pixmap_stale=false;
	delete pm;

	if( !is_surface && !pixels && pixmap && pixmap->bits && texture ){
		delete[] pixmap->bits;
		pixmap->bits=0;
	}
}

void WebGPUCanvas::ensureCpuCopy(){
	if( !pixmap ) return;
	if( pixmap->bits ){
		syncPixmapFromTexture();
		return;
	}
	if( pixmap->bpp!=4 || width<=0 || height<=0 || pixmap->width!=width || pixmap->height!=height ) return;

	pixmap->bits=new unsigned char[(size_t)width*height*4];
	if( readbackInto( pixmap->bits ) ){
		pixmap_stale=false;
	}else{
		delete[] pixmap->bits;
		pixmap->bits=0;
	}
}

struct BBMapRequest{
	volatile bool done=false;
	bool ok=false;
};

static void bbOnMapped( WGPUMapAsyncStatus status,WGPUStringView message,void *userdata1,void *userdata2 ){
	BBMapRequest *req=(BBMapRequest*)userdata1;
	req->ok=( status==WGPUMapAsyncStatus_Success );
	if( !req->ok ){
		LOGD( "[webgpu] mapAsync failed: %.*s",(int)message.length,message.data?message.data:"" );
	}
	req->done=true;
}

bool WebGPUCanvas::readbackInto( unsigned char *dst ){
	if( !dst || !res->device || width<=0 || height<=0 ) return false;

	WGPUTexture tex;
	WGPUTextureFormat fmt=format();
	if( is_surface ){
		if( !res->acquireSurfaceView() ) return false;
		tex=res->surface_texture;
	}else{
		if( dirty ) uploadData();
		ensureTexture();
		tex=texture;
	}

	res->flush();

	size_t bpr=((size_t)width*4+255)&~(size_t)255;
	size_t size=bpr*height;

	WGPUBufferDescriptor bdesc={};
	bdesc.label=bbStrView( "bb.readback" );
	bdesc.usage=WGPUBufferUsage_MapRead|WGPUBufferUsage_CopyDst;
	bdesc.size=size;
	WGPUBuffer buf=wgpuDeviceCreateBuffer( res->device,&bdesc );
	if( !buf ) return false;

	WGPUCommandEncoderDescriptor edesc={};
	edesc.label=bbStrView( "bb.readback.encoder" );
	WGPUCommandEncoder enc=wgpuDeviceCreateCommandEncoder( res->device,&edesc );

	WGPUTexelCopyTextureInfo srci={};
	srci.texture=tex;
	srci.aspect=WGPUTextureAspect_All;
	WGPUTexelCopyBufferInfo dsti={};
	dsti.layout.offset=0;
	dsti.layout.bytesPerRow=(uint32_t)bpr;
	dsti.layout.rowsPerImage=(uint32_t)height;
	dsti.buffer=buf;
	WGPUExtent3D extent={ (uint32_t)width,(uint32_t)height,1 };
	wgpuCommandEncoderCopyTextureToBuffer( enc,&srci,&dsti,&extent );

	WGPUCommandBuffer commands=wgpuCommandEncoderFinish( enc,0 );
	wgpuCommandEncoderRelease( enc );
	wgpuQueueSubmit( res->queue,1,&commands );
	wgpuCommandBufferRelease( commands );

	BBMapRequest req;
	WGPUBufferMapCallbackInfo cb={};
	cb.mode=BB_WGPU_CALLBACK_MODE;
	cb.callback=bbOnMapped;
	cb.userdata1=&req;
	wgpuBufferMapAsync( buf,WGPUMapMode_Read,0,size,cb );
	bbWebGPUWait( res,&req.done );

	if( !req.ok ){
		wgpuBufferRelease( buf );
		return false;
	}

	const unsigned char *mapped=(const unsigned char*)wgpuBufferGetConstMappedRange( buf,0,size );
	if( !mapped ){
		wgpuBufferUnmap( buf );
		wgpuBufferRelease( buf );
		return false;
	}

	bool bgra=( fmt==WGPUTextureFormat_BGRA8Unorm );
	for( int y=0;y<height;y++ ){
		const unsigned char *in=mapped+(size_t)y*bpr;
		unsigned char *out=dst+(size_t)y*width*4;
		if(fmt==WGPUTextureFormat_RGB10A2Unorm){
			for(int x=0;x<width;++x){
				uint32_t value;memcpy(&value,in+x*4,4);
				out[x*4+0]=((value&1023)*255+511)/1023;
				out[x*4+1]=(((value>>10)&1023)*255+511)/1023;
				out[x*4+2]=(((value>>20)&1023)*255+511)/1023;
				out[x*4+3]=(value>>30)*85;
			}
		}else if( bgra ){
			for( int x=0;x<width;x++ ){
				out[x*4+0]=in[x*4+2];
				out[x*4+1]=in[x*4+1];
				out[x*4+2]=in[x*4+0];
				out[x*4+3]=in[x*4+3];
			}
		}else{
			memcpy( out,in,(size_t)width*4 );
		}
	}

	wgpuBufferUnmap( buf );
	wgpuBufferRelease( buf );
	return true;
}

void WebGPUCanvas::syncPixmapFromTexture(){
	if( !pixmap || !pixmap->bits || !pixmap_stale ) return;
	if( pixmap->bpp==4 && pixmap->width==width && pixmap->height==height ){
		if( readbackInto( pixmap->bits ) ) pixmap_stale=false;
	}
}

void WebGPUCanvas::downloadData(){
	if( pixels && pixmap && pixmap->bits && !pixmap_stale &&
	    pixmap->bpp==4 && pixmap->width==width && pixmap->height==height ){
		memcpy( pixels,pixmap->bits,(size_t)width*height*4 );
		return;
	}

	unsigned char *bits=pixels?pixels:(pixmap?pixmap->bits:0);
	if( bits ){
		readbackInto( bits );
		if( pixmap && bits==pixmap->bits ) pixmap_stale=false;
	}
}

void WebGPUCanvas::uploadRGBA( const unsigned char *rgba,int w,int h ){
	if( !res->device || is_surface || wide_format || w!=width || h!=height ) return;

	ensureTexture();
	res->flush();

	WGPUTexelCopyTextureInfo dst={};
	dst.texture=texture;
	dst.aspect=WGPUTextureAspect_All;
	WGPUTexelCopyBufferLayout layout={};
	layout.bytesPerRow=(uint32_t)w*4;
	layout.rowsPerImage=(uint32_t)h;
	WGPUExtent3D extent={ (uint32_t)w,(uint32_t)h,1 };
	wgpuQueueWriteTexture( res->queue,&dst,rgba,(size_t)w*h*4,&layout,&extent );
	if( mip_levels>1 ) res->generateMips( texture,mip_levels );

	dirty=false;
	pixmap_stale=false;
	hit_valid=false;
}

void WebGPUCanvas::setPixmap( BBPixmap *pm ){
	if( pixmap==pm ) return;

	dirty=true;

	if( !pm ){
		if( pixmap ){
			delete pixmap;
		}
		pixmap=0;
		return;
	}

	if( pixmap ) delete pixmap;
	pixmap=pm;
	pixmap_stale=false;
	hit_valid=false;
	use_mips=( flags&CANVAS_TEX_MIPMAP )!=0 && ( flags&CANVAS_TEXTURE )!=0;

	width=pm->width;
	height=pm->height;
	vx=vy=0;vw=width;vh=height;
	uploadData();
}


void WebGPUCanvas::draw2d( WGPUPrimitiveTopology topology,bool blend,WGPUTextureView tex,
                           WGPUSampler sampler,const float xywh[4],const float col[3],
                           const float scale[2],const BBWebGPUVertex *verts,int nverts,
                           unsigned image_mode,unsigned image_mask ){
	if( !res->device || width<=0 || height<=0 || nverts<=0 ) return;

	int cx=vx,cy=vy,cw=vw,ch=vh;
	if( cw<=0 || ch<=0 ){ cx=0;cy=0;cw=width;ch=height; }
	if( cx<0 ){ cw+=cx;cx=0; }
	if( cy<0 ){ ch+=cy;cy=0; }
	if( cx+cw>width ) cw=width-cx;
	if( cy+ch>height ) ch=height-cy;
	if( cw<=0 || ch<=0 ) return;
	blit_snapshot_valid=false;

	bool texenabled=( tex!=0 );
	if( !tex ) tex=res->white_view;
	if( !sampler ) sampler=res->sampler_linear;

	WGPUTextureView view=targetView();
	if( !view ) return;

	size_t vbytes=(size_t)nverts*sizeof( BBWebGPUVertex );
	size_t voff_est=(res->vertex_used+15)&~(size_t)15;
	size_t uoff_est=(res->uniform_used+255)&~(size_t)255;
	if( voff_est+vbytes>res->vertex_capacity || uoff_est+256>res->uniform_capacity ){
		res->flush();
	}

	uint32_t voffset=res->pushVertices( verts,nverts );
	BBWebGPURenderState state={};
	state.xywh[0]=xywh[0];state.xywh[1]=xywh[1];state.xywh[2]=xywh[2];state.xywh[3]=xywh[3];
	state.res[0]=(float)width;state.res[1]=(float)height;
	state.texscale[0]=1.0f;state.texscale[1]=1.0f;
	state.color[0]=col[0];state.color[1]=col[1];state.color[2]=col[2];
	state.texenabled=texenabled?( font_pass?4:1 ):0;
	state.scale[0]=scale[0];state.scale[1]=scale[1];
	state.image_mode=image_mode;state.image_mask=image_mask;
	uint32_t uoffset=res->pushUniformsCached( state );

	WGPURenderPassEncoder pass=res->ensurePass( this );
	if( !pass ) return;

	WGPURenderPipeline pipeline=res->getPipeline( topology,blend,format() );
	WGPUBindGroup group=res->getBindGroup( tex,sampler );

	bool scissor_changed=!res->bound_scissor_valid||res->bound_scissor[0]!=cx||res->bound_scissor[1]!=cy||
	                     res->bound_scissor[2]!=cw||res->bound_scissor[3]!=ch;
	bool pipeline_changed=pipeline!=res->bound_pipeline;
	bool group_changed=group!=res->bound_group||!res->bound_group_offset_valid||res->bound_group_offset!=uoffset;
	bool buffer_changed=res->bound_vertex_buffer!=res->vertex_buffer;
	bool changed=scissor_changed||pipeline_changed||group_changed||buffer_changed;

	if( changed ) res->flushPendingDraw();

	if( scissor_changed ){
		wgpuRenderPassEncoderSetScissorRect( pass,(uint32_t)cx,(uint32_t)cy,(uint32_t)cw,(uint32_t)ch );
		res->bound_scissor[0]=cx;res->bound_scissor[1]=cy;res->bound_scissor[2]=cw;res->bound_scissor[3]=ch;
		res->bound_scissor_valid=true;
	}
	if( pipeline_changed ){
		wgpuRenderPassEncoderSetPipeline( pass,pipeline );
		res->bound_pipeline=pipeline;
	}
	if( group_changed ){
		wgpuRenderPassEncoderSetBindGroup( pass,0,group,1,&uoffset );
		res->bound_group=group;
		res->bound_group_offset=uoffset;
		res->bound_group_offset_valid=true;
	}
	if( buffer_changed ){
		wgpuRenderPassEncoderSetVertexBuffer( pass,0,res->vertex_buffer,0,WGPU_WHOLE_SIZE );
		res->bound_vertex_buffer=res->vertex_buffer;
	}

	uint32_t first=(uint32_t)( voffset/sizeof( BBWebGPUVertex ) );
	bool strip=( topology==WGPUPrimitiveTopology_LineStrip||topology==WGPUPrimitiveTopology_TriangleStrip );
	if( !changed&&!strip&&res->pending_draw&&res->pending_first+res->pending_count==first ){
		res->pending_count+=(uint32_t)nverts;
		if(topology==WGPUPrimitiveTopology_TriangleList)res->pending_triangles+=(uint32_t)nverts/3;
	}else{
		res->flushPendingDraw();
		res->pending_first=first;
		res->pending_count=(uint32_t)nverts;
		res->pending_triangles=topology==WGPUPrimitiveTopology_TriangleList?(uint32_t)nverts/3:0;
		res->pending_draw=!strip;
		if( strip ){
			wgpuRenderPassEncoderDraw( pass,(uint32_t)nverts,1,first,0 );
			s2prof::draw(s2prof::TwoD,topology==WGPUPrimitiveTopology_TriangleStrip?std::max(0,nverts-2):0);
		}
	}

	pixmap_stale=true;
	gpu_written=true;
	hit_valid=false;
}

void WebGPUCanvas::quad( int x,int y,int w,int h,bool solid,bool blend,
                         WGPUTextureView tex,float col[3] ){
	// The rectangle lives in the vertices, as in blitSelf, so consecutive rects
	// of one colour share a uniform slot and merge. The shader's position*1+0
	// reproduces the former unit*wh+xy exactly.
	float xywh[4]={ 0.f,0.f,1.f,1.f };
	float scale[2]={ scale_x,scale_y };
	float l=(float)x,t=(float)y;
	float r=l+(float)w,b=t+(float)h;

	if( solid ){
		const BBWebGPUVertex verts[6]={
			{ l,t,0.f,0.f },
			{ l,b,0.f,1.f },
			{ r,t,1.f,0.f },
			{ r,t,1.f,0.f },
			{ l,b,0.f,1.f },
			{ r,b,1.f,1.f }
		};
		draw2d( WGPUPrimitiveTopology_TriangleList,blend,tex,res->sampler_linear,xywh,col,scale,verts,6 );
	}else{
		const BBWebGPUVertex verts[8]={
			{ l,t,0.f,0.f },{ l,b,0.f,1.f },
			{ l,b,0.f,1.f },{ r,b,1.f,1.f },
			{ r,b,1.f,1.f },{ r,t,1.f,0.f },
			{ r,t,1.f,0.f },{ l,t,0.f,0.f }
		};
		draw2d( WGPUPrimitiveTopology_LineList,blend,tex,res->sampler_linear,xywh,col,scale,verts,8 );
	}
}

void WebGPUCanvas::cls(){
	blit_snapshot_valid=false;
	bool full=( vx<=0 && vy<=0 && vx+vw>=width && vy+vh>=height );
	if( full ){
		if( res->pass_target==this ) res->endPass();
		clear_pending=true;
		res->ensurePass( this );
		pixmap_stale=true;
		gpu_written=true;
		hit_valid=false;
	}else{
		float col[3]={ cls_color[0],cls_color[1],cls_color[2] };
		float xywh[4]={ (float)vx,(float)vy,(float)vw,(float)vh };
		float scale[2]={ 1.0f,1.0f };
		static const BBWebGPUVertex verts[6]={
			{ 0.f,0.f,0.f,0.f },
			{ 0.f,1.f,0.f,1.f },
			{ 1.f,0.f,1.f,0.f },
			{ 1.f,0.f,1.f,0.f },
			{ 0.f,1.f,0.f,1.f },
			{ 1.f,1.f,1.f,1.f }
		};
		draw2d( WGPUPrimitiveTopology_TriangleList,false,0,0,xywh,col,scale,verts,6 );
	}
}

void WebGPUCanvas::plot( int x,int y ){
	quad( x,y,1,1,true,false,0,color );
}

void WebGPUCanvas::line( int x,int y,int x2,int y2 ){
	const BBWebGPUVertex verts[2]={
		{ (float)x,(float)y,0.f,0.f },
		{ (float)x2,(float)y2,0.f,0.f }
	};
	float xywh[4]={ 0.f,0.f,1.f,1.f };
	float scale[2]={ scale_x,scale_y };
	draw2d( WGPUPrimitiveTopology_LineList,false,0,0,xywh,color,scale,verts,2 );
}

void WebGPUCanvas::rect( int x,int y,int w,int h,bool solid ){
	quad( x,y,w,h,solid,false,0,color );
}

void WebGPUCanvas::oval( int x,int y,int w,int h,bool solid ){
	int rx=w/2,ry=h/2;
	int segs=abs( rx )+abs( ry );
	if( segs<3 ) segs=3;

	float theta=2*3.1415926f/float( segs );
	float c=cosf( theta ),s=sinf( theta ),t;

	x+=rx;y+=ry;

	float ux=1.0f,uy=0.0f;

	std::vector<BBWebGPUVertex> ring;
	for( int i=0;i<segs;i++ ){
		BBWebGPUVertex v={ ux*rx+x,uy*ry+y,0.f,0.f };
		ring.push_back( v );

		t=ux;
		ux=c*ux-s*uy;
		uy=s*t+c*uy;
	}

	std::vector<BBWebGPUVertex> verts;
	if( solid ){
		for( int i=1;i+1<(int)ring.size();i++ ){
			verts.push_back( ring[0] );
			verts.push_back( ring[i] );
			verts.push_back( ring[i+1] );
		}
	}else{
		for( int i=0;i<(int)ring.size();i++ ){
			verts.push_back( ring[i] );
			verts.push_back( ring[(i+1)%ring.size()] );
		}
	}

	float xywh[4]={ 0.f,0.f,1.f,1.f };
	float scale[2]={ scale_x,scale_y };
	draw2d( solid?WGPUPrimitiveTopology_TriangleList:WGPUPrimitiveTopology_LineList,
	        false,0,0,xywh,color,scale,verts.data(),(int)verts.size() );
}

void WebGPUCanvas::text( int x,int y,const std::string &t ){
	if( !font || t.size()==0 || !res->device ) return;

	bool changed=font->loadChars( t );
	if( changed ) font->rebuildAtlas();
	if( !font->atlas ) return;

	WebGPUContextResources::FontTexture &ft=res->font_textures[font];

	if( changed || !ft.tex || ft.width!=font->atlas->width || ft.height!=font->atlas->height ){
		if( ft.tex && ft.width==font->atlas->width && ft.height==font->atlas->height ) res->flush();

		if( ft.tex && (ft.width!=font->atlas->width || ft.height!=font->atlas->height) ){
			if( ft.view ){
				res->invalidateBindGroupsFor( ft.view );
				wgpuTextureViewRelease( ft.view );
				ft.view=0;
			}
			wgpuTextureRelease( ft.tex );
			ft.tex=0;
		}

		if( !ft.tex ){
			WGPUTextureDescriptor desc={};
			desc.label=bbStrView( "bb.font.atlas" );
			desc.usage=WGPUTextureUsage_TextureBinding|WGPUTextureUsage_CopyDst;
			desc.dimension=WGPUTextureDimension_2D;
			desc.size={ (uint32_t)font->atlas->width,(uint32_t)font->atlas->height,1 };
			desc.format=WGPUTextureFormat_R8Unorm;
			desc.mipLevelCount=1;
			desc.sampleCount=1;
			ft.tex=wgpuDeviceCreateTexture( res->device,&desc );
			ft.view=wgpuTextureCreateView( ft.tex,0 );
			ft.width=font->atlas->width;
			ft.height=font->atlas->height;
		}

		WGPUTexelCopyTextureInfo dst={};
		dst.texture=ft.tex;
		dst.aspect=WGPUTextureAspect_All;
		WGPUTexelCopyBufferLayout layout={};
		layout.offset=0;
		layout.bytesPerRow=(uint32_t)font->atlas->width;
		layout.rowsPerImage=(uint32_t)font->atlas->height;
		WGPUExtent3D extent={ (uint32_t)font->atlas->width,(uint32_t)font->atlas->height,1 };
		wgpuQueueWriteTexture( res->queue,&dst,font->atlas->bits,(size_t)font->atlas->width*font->atlas->height,&layout,&extent );
	}

	static std::vector<BBWebGPUVertex> verts;
	verts.clear();
	verts.reserve( t.size()*6 );

	float fy=(float)y+font->baseline*font->density;
	float fx=(float)x;

	float awidth=(float)font->atlas->width,aheight=(float)font->atlas->height;

	const char *cstr=t.c_str();
	utf8_int32_t curr=0,prev=0;
	while( *cstr ){
		prev=curr;
		cstr=utf8codepoint( cstr,&curr );
		BBImageFont::Char ch=font->getChar( curr );

		float cx=fx+ch.bearing_x*font->density,cy=fy-ch.bearing_y*font->density;

		if( prev>0 ){
			cx+=font->getKerning( prev,curr );
		}

		float l=ch.x/awidth;
		float r=(ch.x+ch.width)/awidth;
		float tt=ch.y/aheight;
		float b=(ch.y+ch.height)/aheight;

		BBWebGPUVertex lb={ cx,cy+ch.height*font->density,l,b },
		               rb={ cx+ch.width*font->density,cy+ch.height*font->density,r,b },
		               rt={ cx+ch.width*font->density,cy,r,tt },
		               lt={ cx,cy,l,tt };

		verts.push_back( lb );verts.push_back( rb );verts.push_back( rt );
		verts.push_back( lb );verts.push_back( rt );verts.push_back( lt );

		fx+=ch.advance*font->density;
	}

	if( verts.empty() ) return;

	float xywh[4]={ 0.f,0.f,1.f,1.f };
	float scale[2]={ scale_x,scale_y };
	font_pass=true;
	draw2d( WGPUPrimitiveTopology_TriangleList,true,ft.view,res->sampler_linear,
	        xywh,color,scale,verts.data(),(int)verts.size() );
	font_pass=false;
}

void WebGPUCanvas::releaseBlitSnapshot(){
	blit_snapshot_valid=false;
	if( blit_view ){
		res->invalidateBindGroupsFor( blit_view );
		wgpuTextureViewRelease( blit_view );
		blit_view=0;
	}
	if( blit_texture ){
		wgpuTextureRelease( blit_texture );
		blit_texture=0;
	}
}

void WebGPUCanvas::applyAntialias(){
	if( !res->device || width<=0 || height<=0 ) return;
	// Read a snapshot, never the colour attachment being written. Reuse the
	// CopyRect allocation but invalidate its source-region cache after this draw.
	res->endPass();
	WGPUTextureView dst=targetView();
	if( !dst ) return;
	consumeClearPending();
	if( ((res->vertex_used+15)&~(size_t)15)+6*sizeof(BBWebGPUVertex)>res->vertex_capacity ||
	    ((res->uniform_used+255)&~(size_t)255)+256>res->uniform_capacity ) res->flush();
	if( !blit_view || blit_width!=width || blit_height!=height || blit_format!=format() ){
		releaseBlitSnapshot();
		WGPUTextureDescriptor desc={};
		desc.label=bbStrView( "bb.screen.snapshot" );
		desc.usage=WGPUTextureUsage_TextureBinding|WGPUTextureUsage_CopyDst;
		desc.dimension=WGPUTextureDimension_2D;
		desc.size={ (uint32_t)width,(uint32_t)height,1 };
		desc.format=format();desc.mipLevelCount=1;desc.sampleCount=1;
		blit_texture=wgpuDeviceCreateTexture( res->device,&desc );
		blit_view=wgpuTextureCreateView( blit_texture,0 );
		blit_width=width;blit_height=height;blit_format=format();
	}
	blit_snapshot_valid=false;
	res->ensureEncoder();
	WGPUTexelCopyTextureInfo from={},to={};
	from.texture=is_surface?res->surface_texture:texture;from.aspect=WGPUTextureAspect_All;
	to.texture=blit_texture;to.aspect=WGPUTextureAspect_All;
	WGPUExtent3D extent={ (uint32_t)width,(uint32_t)height,1 };
	wgpuCommandEncoderCopyTextureToTexture( res->encoder,&from,&to,&extent );
	BBWebGPURenderState state={};
	state.xywh[2]=state.res[0]=(float)width;
	state.xywh[3]=state.res[1]=(float)height;
	state.texscale[0]=state.texscale[1]=state.scale[0]=state.scale[1]=1.f;
	state.color[0]=state.color[1]=state.color[2]=1.f;
	state.texenabled=5; // FXAA only; no UI dithering, colour key or blending.
	uint32_t uoffset=res->pushUniforms(state);
	static const BBWebGPUVertex verts[6]={
		{0.f,0.f,0.f,0.f},{0.f,1.f,0.f,1.f},{1.f,0.f,1.f,0.f},
		{1.f,0.f,1.f,0.f},{0.f,1.f,0.f,1.f},{1.f,1.f,1.f,1.f}
	};
	uint32_t voffset=res->pushVertices(verts,6);
	WGPURenderPassColorAttachment att={};
	att.view=dst;att.depthSlice=WGPU_DEPTH_SLICE_UNDEFINED;
	att.loadOp=WGPULoadOp_Load;att.storeOp=WGPUStoreOp_Store;
	WGPURenderPassDescriptor desc={};
	desc.label=bbStrView("bb.fxaa");desc.colorAttachmentCount=1;desc.colorAttachments=&att;
	res->profilePass(desc,s2prof::TwoD);
	WGPURenderPassEncoder pass=wgpuCommandEncoderBeginRenderPass(res->encoder,&desc);
	wgpuRenderPassEncoderSetPipeline(pass,res->getPipeline(WGPUPrimitiveTopology_TriangleList,false,format()));
	wgpuRenderPassEncoderSetBindGroup(pass,0,res->getBindGroup(blit_view,res->sampler_linear),1,&uoffset);
	wgpuRenderPassEncoderSetVertexBuffer(pass,0,res->vertex_buffer,voffset,sizeof(verts));
	wgpuRenderPassEncoderDraw(pass,6,1,0,0);
	s2prof::draw(s2prof::TwoD,2);
	wgpuRenderPassEncoderEnd(pass);wgpuRenderPassEncoderRelease(pass);
	pixmap_stale=true;gpu_written=true;hit_valid=false;
}

bool WebGPUCanvas::blitSelf( int x,int y,int src_x,int src_y,int src_w,int src_h,bool solid ){
	// Keep scaled/clamped sampling on the original path. Integer CopyRect
	// strips can share a snapshot whenever their sources are still unchanged.
	if( scale_x!=1.f || scale_y!=1.f ) return false;
	if( x>=width || y>=height || (float)x+src_w<=0 || (float)y+src_h<=0 ) return true;
	if( src_x<0 || src_y<0 || src_x>width-src_w || src_y>height-src_h ) return false;
	if( is_surface ){
		if( !res->acquireSurfaceView() ) return true;
	}else{
		if( dirty ) uploadData();
		ensureTexture();
	}
	bool matching=blit_view && blit_width==width && blit_height==height && blit_format==format();
	bool independent=matching && blit_snapshot_valid && res->pass && res->pass_target==this &&
	                 (src_x+src_w<=blit_left || src_x>=blit_right ||
	                  src_y+src_h<=blit_top || src_y>=blit_bottom);
	if( !independent ){
		// Complete prior draws before overwriting the texture sampled by them.
		res->endPass();
		res->ensureEncoder();
		if( !matching ){
			releaseBlitSnapshot();
			WGPUTextureDescriptor desc={};
			desc.label=bbStrView( "bb.blit.temp" );
			desc.usage=WGPUTextureUsage_TextureBinding|WGPUTextureUsage_CopyDst;
			desc.dimension=WGPUTextureDimension_2D;
			desc.size={ (uint32_t)width,(uint32_t)height,1 };
			desc.format=format();
			desc.mipLevelCount=1;
			desc.sampleCount=1;
			blit_texture=wgpuDeviceCreateTexture( res->device,&desc );
			blit_view=wgpuTextureCreateView( blit_texture,0 );
			blit_width=width;blit_height=height;blit_format=format();
		}
		WGPUTexelCopyTextureInfo from={};
		from.texture=is_surface?res->surface_texture:texture;
		from.aspect=WGPUTextureAspect_All;
		WGPUTexelCopyTextureInfo to={};
		to.texture=blit_texture;
		to.aspect=WGPUTextureAspect_All;
		WGPUExtent3D extent={ (uint32_t)width,(uint32_t)height,1 };
		wgpuCommandEncoderCopyTextureToTexture( res->encoder,&from,&to,&extent );
	}
	// Uniforms stay equal across strips; draw2d merges their vertex ranges.
	float l=(float)x,t=(float)y,r=l+src_w,b=t+src_h;
	float u0=(float)src_x/width,v0=(float)src_y/height;
	float u1=(float)(src_x+src_w)/width,v1=(float)(src_y+src_h)/height;
	const BBWebGPUVertex verts[6]={
		{ l,t,u0,v0 },{ l,b,u0,v1 },{ r,t,u1,v0 },
		{ r,t,u1,v0 },{ l,b,u0,v1 },{ r,b,u1,v1 }
	};
	float white[3]={ 1.f,1.f,1.f },xywh[4]={ 0.f,0.f,1.f,1.f },scale[2]={ 1.f,1.f };
	draw2d( WGPUPrimitiveTopology_TriangleList,!solid,blit_view,res->sampler_nearest,
	        xywh,white,scale,verts,6,solid?2:1,mask );
	if( independent ){
		blit_left=std::min( blit_left,x );blit_top=std::min( blit_top,y );
		blit_right=std::max( blit_right,x+src_w );blit_bottom=std::max( blit_bottom,y+src_h );
	}else{
		blit_left=x;blit_top=y;blit_right=x+src_w;blit_bottom=y+src_h;
	}
	blit_snapshot_valid=true;
	return true;
}

void WebGPUCanvas::blit( int x,int y,BBCanvas *s,int src_x,int src_y,int src_w,int src_h,bool solid ){
	if( !s || width<=0 || height<=0 || s->getWidth()<=0 || s->getHeight()<=0 || src_w<=0 || src_h<=0 ) return;

	WebGPUCanvas *src=(WebGPUCanvas*)s;
	if( src==this && blitSelf( x,y,src_x,src_y,src_w,src_h,solid ) ) return;

	float sx,sy,dx,dy;
	src->getScale( &sx,&sy );
	getScale( &dx,&dy );

	float sx0=src_x*sx,sy0=src_y*sy;
	float sw=src_w*sx,sh=src_h*sy;
	float dx0=x*dx,dy0=y*dy;
	float dw=src_w*dx,dh=src_h*dy;

	float srcW=(float)src->getWidth(),srcH=(float)src->getHeight();

	WGPUTextureView view=0;
	WGPUTexture temp_tex=0;
	WGPUTextureView temp_view=0;

	if( src==this ){
		if( is_surface ){
			if( !res->acquireSurfaceView() ) return;
		}else{
			if( dirty ) uploadData();
			ensureTexture();
		}
		res->endPass();
		res->ensureEncoder();

		int cw=(int)srcW,ch=(int)srcH;
		WGPUTextureDescriptor tdesc={};
		tdesc.label=bbStrView( "bb.blit.temp" );
		tdesc.usage=WGPUTextureUsage_TextureBinding|WGPUTextureUsage_CopyDst;
		tdesc.dimension=WGPUTextureDimension_2D;
		tdesc.size={ (uint32_t)cw,(uint32_t)ch,1 };
		tdesc.format=format();
		tdesc.mipLevelCount=1;
		tdesc.sampleCount=1;
		temp_tex=wgpuDeviceCreateTexture( res->device,&tdesc );
		temp_view=wgpuTextureCreateView( temp_tex,0 );

		WGPUTexelCopyTextureInfo from={};
		from.texture=is_surface?res->surface_texture:texture;
		from.aspect=WGPUTextureAspect_All;
		WGPUTexelCopyTextureInfo to={};
		to.texture=temp_tex;
		to.aspect=WGPUTextureAspect_All;
		WGPUExtent3D extent={ (uint32_t)cw,(uint32_t)ch,1 };
		wgpuCommandEncoderCopyTextureToTexture( res->encoder,&from,&to,&extent );

		view=temp_view;
	}else{
		view=src->sampleView();
	}
	if( !view ){
		if( temp_view ) wgpuTextureViewRelease( temp_view );
		if( temp_tex ) wgpuTextureRelease( temp_tex );
		return;
	}

	float u0=sx0/srcW,v0=sy0/srcH;
	float u1=(sx0+sw)/srcW,v1=(sy0+sh)/srcH;

	// Destination positions go in the vertices so every glyph of a bitmap-font
	// run (DrawImageRect from one page) shares uniforms and becomes one draw.
	float l=dx0,t=dy0;
	float r=l+dw,b=t+dh;
	const BBWebGPUVertex verts[6]={
		{ l,t,u0,v0 },
		{ l,b,u0,v1 },
		{ r,t,u1,v0 },
		{ r,t,u1,v0 },
		{ l,b,u0,v1 },
		{ r,b,u1,v1 }
	};

	float white[3]={ 1.f,1.f,1.f };
	float xywh[4]={ 0.f,0.f,1.f,1.f };
	float scale[2]={ 1.f,1.f };
	// Blitz images use a per-draw RGB colour key. Keep the source pixels intact
	// so DrawBlock, CopyImage, animated frames and changed masks all work too.
	draw2d( WGPUPrimitiveTopology_TriangleList,!solid,view,res->sampler_nearest,
	        xywh,white,scale,verts,6,solid?2:1,src->mask );

	if( temp_view ){
		res->invalidateBindGroupsFor( temp_view );
		wgpuTextureViewRelease( temp_view );
	}
	if( temp_tex ) wgpuTextureRelease( temp_tex );

	pixmap_stale=true;
}

void WebGPUCanvas::blitScaled( int x,int y,int w,int h,BBCanvas *s,int src_x,int src_y,int src_w,int src_h,bool filter ){
	if( !s || width<=0 || height<=0 || s->getWidth()<=0 || s->getHeight()<=0 ||
	    src_w<=0 || src_h<=0 || w<=0 || h<=0 ) return;

	WebGPUCanvas *src=(WebGPUCanvas*)s;

	float dx,dy;
	getScale( &dx,&dy );

	float dx0=x*dx,dy0=y*dy;
	float dw=w*dx,dh=h*dy;

	float srcW=(float)src->getWidth(),srcH=(float)src->getHeight();

	WGPUTextureView view=src->sampleView();
	if( !view ) return;

	float u0=(float)src_x/srcW,v0=(float)src_y/srcH;
	float u1=(float)(src_x+src_w)/srcW,v1=(float)(src_y+src_h)/srcH;

	const BBWebGPUVertex verts[6]={
		{ 0.f,0.f,u0,v0 },
		{ 0.f,1.f,u0,v1 },
		{ 1.f,0.f,u1,v0 },
		{ 1.f,0.f,u1,v0 },
		{ 0.f,1.f,u0,v1 },
		{ 1.f,1.f,u1,v1 }
	};

	float white[3]={ 1.f,1.f,1.f };
	float xywh[4]={ dx0,dy0,dw,dh };
	float scale[2]={ 1.f,1.f };
	draw2d( WGPUPrimitiveTopology_TriangleList,false,view,
	        filter?res->sampler_linear:res->sampler_nearest,xywh,white,scale,verts,6 );

	pixmap_stale=true;
}

bool WebGPUCanvas::stretchTo( BBCanvas *c,bool filter ){
	WebGPUCanvas *dest=(WebGPUCanvas*)c;
	if( !res->device || width<=0 || height<=0 || dest->width<=0 || dest->height<=0 ) return false;

	WGPUTextureView view=sampleView();
	if( !view ) return false;

	static const BBWebGPUVertex verts[6]={
		{ 0.f,0.f,0.f,0.f },
		{ 0.f,1.f,0.f,1.f },
		{ 1.f,0.f,1.f,0.f },
		{ 1.f,0.f,1.f,0.f },
		{ 0.f,1.f,0.f,1.f },
		{ 1.f,1.f,1.f,1.f }
	};
	float white[3]={ 1.f,1.f,1.f };
	float xywh[4]={ 0.f,0.f,(float)dest->width,(float)dest->height };
	float scale[2]={ 1.f,1.f };

	int ovx=dest->vx,ovy=dest->vy,ovw=dest->vw,ovh=dest->vh;
	dest->vx=0;dest->vy=0;dest->vw=dest->width;dest->vh=dest->height;
	dest->draw2d( WGPUPrimitiveTopology_TriangleList,false,view,
	              filter?res->sampler_linear:res->sampler_nearest,xywh,white,scale,verts,6 );
	dest->vx=ovx;dest->vy=ovy;dest->vw=ovw;dest->vh=ovh;

	dest->pixmap_stale=true;
	return true;
}

void WebGPUCanvas::image( BBCanvas *c,int x,int y,bool solid ){
	WebGPUCanvas *src=(WebGPUCanvas*)c;
	blit( x-src->handle_x,y-src->handle_y,c,0,0,src->getWidth(),src->getHeight(),solid );
}

void WebGPUCanvas::readPixels( std::vector<unsigned char> &out ){
	out.resize( (size_t)width*height*4 );
	if( pixmap && pixmap->bits && !pixmap_stale && pixmap->bpp==4 &&
	    pixmap->width==width && pixmap->height==height ){
		memcpy( out.data(),pixmap->bits,out.size() );
		return;
	}
	if( !readbackInto( out.data() ) ) std::fill( out.begin(),out.end(),0 );
}

bool WebGPUCanvas::buildHitMask(){
	if( width<=0 || height<=0 ) return false;
	if( hit_valid ) return true;

	std::vector<unsigned char> rgba;
	readPixels( rgba );

	bool keyed=( flags&CANVAS_TEX_MASK )!=0;
	unsigned char kr=(mask>>16)&255,kg=(mask>>8)&255,kb=mask&255;

	hit_mask.assign( (size_t)width*height,0 );
	for( size_t i=0;i<hit_mask.size();i++ ){
		const unsigned char *p=&rgba[i*4];
		bool visible=p[3]!=0;
		if( keyed && p[0]==kr && p[1]==kg && p[2]==kb ) visible=false;
		hit_mask[i]=visible?1:0;
	}
	hit_valid=true;
	return true;
}

bool WebGPUCanvas::collide( int x,int y,const BBCanvas *s,int src_x,int src_y,bool solid ){
	WebGPUCanvas *other=(WebGPUCanvas*)s;

	int x1=x-handle_x,y1=y-handle_y;
	int x2=src_x-other->handle_x,y2=src_y-other->handle_y;

	if( x1+width<=x2 || x1>=x2+other->width ) return false;
	if( y1+height<=y2 || y1>=y2+other->height ) return false;
	if( solid ) return true;

	if( !buildHitMask() || !other->buildHitMask() ) return false;

	int left=std::max( x1,x2 ),right=std::min( x1+width,x2+other->width );
	int top=std::max( y1,y2 ),bottom=std::min( y1+height,y2+other->height );

	for( int py=top;py<bottom;py++ ){
		const unsigned char *a=hit_mask.data()+(ptrdiff_t)(py-y1)*width-x1;
		const unsigned char *b=other->hit_mask.data()+(ptrdiff_t)(py-y2)*other->width-x2;
		for( int px=left;px<right;px++ ){
			if( a[px] && b[px] ) return true;
		}
	}
	return false;
}

bool WebGPUCanvas::rect_collide( int x,int y,int rect_x,int rect_y,int rect_w,int rect_h,bool solid ){
	int x1=x-handle_x,y1=y-handle_y;

	if( x1+width<=rect_x || x1>=rect_x+rect_w ) return false;
	if( y1+height<=rect_y || y1>=rect_y+rect_h ) return false;
	if( solid ) return true;

	if( !buildHitMask() ) return false;

	int left=std::max( x1,rect_x ),right=std::min( x1+width,rect_x+rect_w );
	int top=std::max( y1,rect_y ),bottom=std::min( y1+height,rect_y+rect_h );

	for( int py=top;py<bottom;py++ ){
		const unsigned char *a=hit_mask.data()+(ptrdiff_t)(py-y1)*width-x1;
		for( int px=left;px<right;px++ ){
			if( a[px] ) return true;
		}
	}
	return false;
}


bool WebGPUCanvas::lock(){
	if( lock_count++ ) return true;
	pixels=new unsigned char[(size_t)width*height*4];
	downloadData();
	return true;
}

void WebGPUCanvas::setPixel( int x,int y,unsigned argb ){
	lock();
	setPixelFast( x,y,argb );
	unlock();
}

#define UC(c) static_cast<unsigned char>(c)

void WebGPUCanvas::setPixelFast( int x,int y,unsigned argb ){
	if( !pixels || x<0 || y<0 || x>=width || y>=height ) return;
	unsigned char *p=pixels+((size_t)y*width+x)*4;
	p[0]=UC( (argb>>16)&0xff );
	p[1]=UC( (argb>>8)&0xff );
	p[2]=UC( argb&0xff );
	p[3]=UC( (argb>>24)&0xff );
}

void WebGPUCanvas::copyPixel( int x,int y,BBCanvas *src,int src_x,int src_y ){
	setPixel( x,y,src->getPixel( src_x,src_y ) );
}

void WebGPUCanvas::copyPixelFast( int x,int y,BBCanvas *src,int src_x,int src_y ){
	setPixelFast( x,y,src->getPixelFast( src_x,src_y ) );
}

unsigned WebGPUCanvas::getPixel( int x,int y ){
	lock();
	unsigned rgb=getPixelFast( x,y );
	unlock();
	return rgb;
}

unsigned WebGPUCanvas::getPixelFast( int x,int y ){
	if( !pixels || x<0 || y<0 || x>=width || y>=height ) return 0;
	unsigned char *p=pixels+((size_t)y*width+x)*4;
	return (UC(p[3])<<24)|(UC(p[0])<<16)|(UC(p[1])<<8)|UC(p[2]);
}

void WebGPUCanvas::unlock(){
	if( !pixels ) return;
	if( --lock_count>0 ) return;
	if( lock_count<0 ) lock_count=0;
	hit_valid=false;
	uploadData();
	delete[] pixels;pixels=0;
}


void WebGPUCanvas::set(){
}

void WebGPUCanvas::unset(){
	if( res->pass_target==this ) res->endPass();
}

void WebGPUCanvas::setFont( BBFont *f ){
	font=reinterpret_cast<BBImageFont*>( f );
}

void WebGPUCanvas::setMask( unsigned argb ){
	mask=argb;
	flags|=CANVAS_TEX_MASK;
	if( flags&CANVAS_TEXTURE ){
		ensureCpuCopy();
		dirty=true;
	}
	hit_valid=false;
}

void WebGPUCanvas::setColor( unsigned argb ){
	color[0]=((argb>>16)&255)/255.0f;
	color[1]=((argb>>8)&255)/255.0f;
	color[2]=(argb&255)/255.0f;
}

void WebGPUCanvas::setClsColor( unsigned argb ){
	cls_argb=argb;
	cls_color[0]=((argb>>16)&255)/255.0f;
	cls_color[1]=((argb>>8)&255)/255.0f;
	cls_color[2]=(argb&255)/255.0f;
}

void WebGPUCanvas::setOrigin( int x,int y ){
	origin_x=x;
	origin_y=y;
}

void WebGPUCanvas::setScale( float x,float y ){
	scale_x=x;
	scale_y=y;
}

void WebGPUCanvas::setHandle( int x,int y ){
	handle_x=x;
	handle_y=y;
}

void WebGPUCanvas::setViewport( int x,int y,int w,int h ){
	vx=x;vy=y;vw=w;vh=h;
}

void WebGPUCanvas::setCubeMode( int mode ){
	cube_mode=mode;
}

void WebGPUCanvas::setCubeFace( int face ){
	cube_face=face;
}


int WebGPUCanvas::getWidth()const{
	return width;
}

int WebGPUCanvas::getHeight()const{
	return height;
}

int WebGPUCanvas::getDepth()const{
	return 8;
}

int WebGPUCanvas::cubeMode()const{
	return cube_mode;
}

void WebGPUCanvas::getOrigin( int *x,int *y )const{
	*x=origin_x;*y=origin_y;
}

void WebGPUCanvas::getScale( float *x,float *y )const{
	*x=scale_x;*y=scale_y;
}

void WebGPUCanvas::getHandle( int *x,int *y )const{
	*x=handle_x;*y=handle_y;
}

void WebGPUCanvas::getViewport( int *x,int *y,int *w,int *h )const{
	*x=vx;*y=vy;*w=vw;*h=vh;
}

unsigned WebGPUCanvas::getMask()const{
	return mask;
}

unsigned WebGPUCanvas::getColor()const{
	unsigned r=(unsigned)(color[0]*255.0f+0.5f);
	unsigned g=(unsigned)(color[1]*255.0f+0.5f);
	unsigned b=(unsigned)(color[2]*255.0f+0.5f);
	return (r<<16)|(g<<8)|b;
}

unsigned WebGPUCanvas::getClsColor()const{
	return cls_argb;
}
