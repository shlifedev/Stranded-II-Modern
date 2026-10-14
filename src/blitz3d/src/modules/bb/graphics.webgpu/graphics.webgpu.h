#ifndef BB_GRAPHICS_WEBGPU_H
#define BB_GRAPHICS_WEBGPU_H

#include <bb/graphics/graphics.h>

#include <webgpu/webgpu.h>

#include <cstring>
#include <map>
#include <utility>
#include <vector>
#include "profiler_gpu.h"

#ifdef BB_EMSCRIPTEN
#include <emscripten.h>
#include <emscripten/html5.h>
#define BB_WGPU_CALLBACK_MODE WGPUCallbackMode_AllowSpontaneous
#else
#define BB_WGPU_CALLBACK_MODE WGPUCallbackMode_AllowProcessEvents
#endif

#include <SDL.h>

class WebGPUCanvas;

struct WebGPUCanvasListener{
	void *ctx;
	void (*view_gone)( void *ctx,WGPUTextureView view );
	void (*canvas_gone)( void *ctx,WebGPUCanvas *canvas );
	void (*pre_flush)( void *ctx );
	void (*post_flush)( void *ctx );
};

inline WGPUStringView bbStrView( const char *s ){
	WGPUStringView v;
	v.data=s;
	v.length=s?strlen( s ):0;
	return v;
}

struct BBWebGPURenderState{
	float xywh[4];
	float res[2];
	float texscale[2];
	float color[3];
	int32_t texenabled;
	float scale[2];
	uint32_t image_mask;
	uint32_t image_mode;
};
static_assert( sizeof(BBWebGPURenderState)==64,"BBRenderState layout mismatch" );

struct BBWebGPUVertex{
	float x,y;
	float u,v;
};
static_assert( sizeof(BBWebGPUVertex)==16,"vertex layout mismatch" );

struct WebGPUContextResources{
	WGPUInstance instance=0;
#ifdef BB_MACOS
	void *metalView=0;
#endif
#ifndef BB_EMSCRIPTEN
	SDL_Window *window=0;
#endif
	WGPUAdapter adapter=0;
	WGPUDevice device=0;
	std::unique_ptr<WebGPUProfiler> profiler;
	bool profile_finish=false;
	WGPUQueue queue=0;
	WGPUSurface surface=0;
	bool antialias=false;
	int antialias_samples=1;
	WGPUTextureFormat surface_format=WGPUTextureFormat_BGRA8Unorm;
	WGPUPresentMode present_mode=WGPUPresentMode_Fifo;
	WGPUPresentMode unsynced_mode=WGPUPresentMode_Fifo;
	int surface_width=0,surface_height=0;

	WGPUTexture surface_texture=0;
	WGPUTextureView surface_view=0;

	WGPUShaderModule shader=0;
	WGPUBindGroupLayout bind_layout=0;
	WGPUPipelineLayout pipeline_layout=0;
	std::map<uint32_t,WGPURenderPipeline> pipelines;

	WGPUSampler sampler_linear=0,sampler_nearest=0;

	WGPUTexture white_tex=0;
	WGPUTextureView white_view=0;

	WGPUBuffer vertex_buffer=0;
	size_t vertex_capacity=0,vertex_used=0;
	WGPUBuffer uniform_buffer=0;
	size_t uniform_capacity=0,uniform_used=0;
	std::vector<unsigned char> vertex_stage,uniform_stage;

	WGPUShaderModule mip_shader=0;
	WGPUBindGroupLayout mip_layout=0;
	WGPUPipelineLayout mip_pipeline_layout=0;
	WGPURenderPipeline mip_pipeline=0;

	WGPUCommandEncoder encoder=0;
	WGPUCommandEncoder mip_encoder=0;
	WGPURenderPassEncoder pass=0;
	WebGPUCanvas *pass_target=0;

	WGPURenderPipeline bound_pipeline=0;
	WGPUBindGroup bound_group=0;
	uint32_t bound_group_offset=0;
	bool bound_group_offset_valid=false;
	WGPUBuffer bound_vertex_buffer=0;
	int bound_scissor[4]={};
	bool bound_scissor_valid=false;
	bool pending_draw=false;
	uint32_t pending_first=0,pending_count=0,pending_triangles=0;

	BBWebGPURenderState last_state;
	uint32_t last_state_offset=0;
	bool last_state_valid=false;

	std::map<std::pair<WGPUTextureView,WGPUSampler>,WGPUBindGroup> bind_groups;

	std::vector<WebGPUCanvasListener> listeners;

	struct FontTexture{
		WGPUTexture tex=0;
		WGPUTextureView view=0;
		int width=0,height=0;
	};
	std::map<BBImageFont*,FontTexture> font_textures;

	bool initDevice( const char *canvas_selector,int width,int height );
	void configureSurface( int width,int height );
	void ensurePipelineObjects();
	void ensureMipPipeline();
	void generateMips( WGPUTexture tex,int levels );

	WGPUTextureView acquireSurfaceView();
	void releaseSurfaceTexture();

	WGPUCommandEncoder ensureEncoder();
	WGPURenderPassEncoder ensurePass( WebGPUCanvas *target );
	void endPass();
	void flush();
	void profilePass(WGPURenderPassDescriptor &desc,int group){if(profiler)profiler->pass(desc,group);}
	void finishProfile(){profile_finish=true;flush();}

	WGPURenderPipeline getPipeline( WGPUPrimitiveTopology topology,bool blend,WGPUTextureFormat format,int samples=1 );
	bool seedMultisample( WebGPUCanvas *source,WGPUTextureView target,int samples );
	WGPUBindGroup getBindGroup( WGPUTextureView view,WGPUSampler sampler );
	uint32_t pushVertices( const BBWebGPUVertex *verts,int count );
	uint32_t pushUniforms( const BBWebGPURenderState &state );
	uint32_t pushUniformsCached( const BBWebGPURenderState &state );
	void resetPassState();
	void flushPendingDraw();

	void invalidateBindGroupsFor( WGPUTextureView view );

	void addListener( const WebGPUCanvasListener &l );
	void removeListener( void *ctx );
	void notifyCanvasDestroyed( WebGPUCanvas *canvas );

	void destroy();
};

void bbWebGPUWait( WebGPUContextResources *res,volatile bool *done );

class WebGPUGraphics : public BBGraphics{
protected:
	BBImageFont *def_font;

	WebGPUCanvas *fb;

	int window_width,window_height,drawable_width,drawable_height;

	unsigned short gamma_red[256],gamma_green[256],gamma_blue[256];

	static void onAppChange( void *data,void *context );
public:
	WebGPUContextResources res;

	SDL_Window *wnd;

	WebGPUGraphics( SDL_Window *wnd );
	~WebGPUGraphics();

	void resize();

	bool init();

	void present();

	BBFont *getDefaultFont()const;

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
    void mouseToLogical(int &x,int &y)const;
#ifdef BB_WINDOWS
    bool absoluteMousePosition(int &x,int &y)const;
#endif

	BBCanvas *createCanvas( int width,int height,int flags );
	BBCanvas *loadCanvas( const std::string &file,int flags );

	BBMovie *openMovie( const std::string &file,int flags );
};

class WebGPUContextDriver : public BBContextDriver{
public:
	WebGPUContextDriver();

	int numGraphicsDrivers();
	void graphicsDriverInfo( int driver,std::string *name,int *c );
	int numGraphicsModes( int driver );
	void graphicsModeInfo( int driver,int mode,int *w,int *h,int *d,int *c );
	void windowedModeInfo( int *c );

	BBGraphics *openGraphics( int w,int h,int d,int driver,int flags );
	void closeGraphics( BBGraphics *graphics );
	bool graphicsLost();

	void flip( bool vwait );
};

#endif
