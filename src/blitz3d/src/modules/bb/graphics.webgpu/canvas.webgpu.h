#ifndef BB_CANVAS_WEBGPU_H
#define BB_CANVAS_WEBGPU_H

#include <bb/graphics.webgpu/graphics.webgpu.h>
#include <bb/pixmap/pixmap.h>

class WebGPUCanvas : public BBCanvas{
protected:
	void backup()const{}

	WebGPUContextResources *res;

	int width,height;
	mutable unsigned char *pixels;
	mutable int lock_count;
	BBImageFont *font;

	int vx,vy,vw,vh;
	float scale_x,scale_y;
	int origin_x,origin_y;
	int handle_x,handle_y;
	unsigned mask;
	float color[3];
	float cls_color[3];
	unsigned cls_argb;
	BBPixmap *pixmap;

	bool dirty;
	bool pixmap_stale;
	bool clear_pending;
	bool gpu_written;

	bool is_surface;
	bool wide_format;
	bool use_mips;
	int mip_levels;
	WGPUTextureView level0_view;
	bool mips_dirty;

	// A snapshot may serve consecutive self-blits while their source rectangles
	// do not overlap writes made since the snapshot. Water/underwater strips
	// then share one copy and one render pass per independent group.
	WGPUTexture blit_texture=0;
	WGPUTextureView blit_view=0;
	int blit_width=0,blit_height=0;
	WGPUTextureFormat blit_format=WGPUTextureFormat_Undefined;
	bool blit_snapshot_valid=false;
	int blit_left=0,blit_top=0,blit_right=0,blit_bottom=0;
	void releaseBlitSnapshot();
	bool blitSelf( int x,int y,int src_x,int src_y,int src_w,int src_h,bool solid );

	std::vector<unsigned char> hit_mask;
	bool hit_valid;
	bool font_pass;

	bool buildHitMask();
	void readPixels( std::vector<unsigned char> &out );

	void draw2d( WGPUPrimitiveTopology topology,bool blend,WGPUTextureView tex,
	             WGPUSampler sampler,const float xywh[4],const float col[3],
	             const float scale[2],const BBWebGPUVertex *verts,int nverts,
	             unsigned image_mode=0,unsigned image_mask=0 );
	void quad( int x,int y,int w,int h,bool solid,bool blend,
	           WGPUTextureView tex,float col[3] );

	void uploadData();
	void downloadData();
	bool readbackInto( unsigned char *dst );
	void syncPixmapFromTexture();
	void ensureCpuCopy();

public:
	WebGPUCanvas( WebGPUContextResources *res,int f );
	WebGPUCanvas( WebGPUContextResources *res,int w,int h,int f );

	~WebGPUCanvas();

	int cube_face,cube_mode;

	WGPUTexture texture;
	int twidth,theight;
	WGPUTextureView texture_view;

	void setPixmap( BBPixmap *pm );
	void uploadRGBA( const unsigned char *rgba,int w,int h );

	void resize( int w,int h,float d );

	bool isSurface()const{ return is_surface; }
	void useWideFormat(){ wide_format=true; }
	bool needsUpload()const{ return dirty||mips_dirty; }
	bool gpuWritten()const{ return gpu_written; }
	WGPUTextureFormat format()const;
	WGPUTextureView targetView();
	WGPUTextureView sampleView();
	bool consumeClearPending();
	void getClsColorf( float col[4] )const;

	void ensureTexture();

	void set();
	void unset();

	void setFont( BBFont *f );
	void setMask( unsigned argb );
	void setColor( unsigned argb );
	void setClsColor( unsigned argb );
	void setOrigin( int x,int y );
	void setScale( float x,float y );
	void setHandle( int x,int y );
	void setViewport( int x,int y,int w,int h );

	void cls();
	void plot( int x,int y );
	void line( int x,int y,int x2,int y2 );
	void rect( int x,int y,int w,int h,bool solid );
	void oval( int x,int y,int w,int h,bool solid );
	void text( int x,int y,const std::string &t );
	void blit( int x,int y,BBCanvas *s,int src_x,int src_y,int src_w,int src_h,bool solid );
	void blitScaled( int x,int y,int w,int h,BBCanvas *s,int src_x,int src_y,int src_w,int src_h,bool filter );
	void image( BBCanvas *c,int x,int y,bool solid );

	bool collide( int x,int y,const BBCanvas *src,int src_x,int src_y,bool solid );
	bool rect_collide( int x,int y,int rect_x,int rect_y,int rect_w,int rect_h,bool solid );

	BBPixmap *getPixmap(){ ensureCpuCopy();return pixmap; }
	bool stretchTo( BBCanvas *dest,bool filter );

	bool lock();
	void setPixel( int x,int y,unsigned argb );
	void setPixelFast( int x,int y,unsigned argb );
	void copyPixel( int x,int y,BBCanvas *src,int src_x,int src_y );
	void copyPixelFast( int x,int y,BBCanvas *src,int src_x,int src_y );
	unsigned getPixel( int x,int y );
	unsigned getPixelFast( int x,int y );
	void unlock();

	void setCubeMode( int mode );
	void setCubeFace( int face );

	int getWidth()const;
	int getHeight()const;
	int getDepth()const;
	int cubeMode()const;
	void getOrigin( int *x,int *y )const;
	void getScale( float *x,float *y )const;
	void getHandle( int *x,int *y )const;
	void getViewport( int *x,int *y,int *w,int *h )const;
	unsigned getMask()const;
	unsigned getColor()const;
	unsigned getClsColor()const;
};

#endif
