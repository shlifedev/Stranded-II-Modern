

#include "../../../stdutil/stdutil.h"
#include <bb/blitz/blitz.h>
#include <bb/graphics.webgpu/graphics.webgpu.h>
#include <bb/graphics.webgpu/canvas.webgpu.h>
#include "blitz3d.webgpu.h"
#include "default3d.wgsl.h"

#include <cmath>
#include <cstddef>
#include <cstring>
#include <array>
#include <map>
#include <set>
#include <vector>

static const float dtor=0.0174532925199432957692369076848861f;
static const float rtod=1/dtor;

class WebGPUScene;


static void bbMat4Identity( float m[16] ){
	memset( m,0,16*sizeof(float) );
	m[0]=m[5]=m[10]=m[15]=1.0f;
}

static void bbMat4Mul( const float a[16],const float b[16],float out[16] ){
	for( int c=0;c<4;++c ){
		for( int r=0;r<4;++r ){
			out[c*4+r]=a[0*4+r]*b[c*4+0]+a[1*4+r]*b[c*4+1]+a[2*4+r]*b[c*4+2]+a[3*4+r]*b[c*4+3];
		}
	}
}

static void bbCross3( const float a[3],const float b[3],float out[3] ){
	out[0]=a[1]*b[2]-a[2]*b[1];
	out[1]=a[2]*b[0]-a[0]*b[2];
	out[2]=a[0]*b[1]-a[1]*b[0];
}

static void bbNormalMatrix( const float view[16],const float world[16],float out[16] ){
	float mv[16];
	bbMat4Mul( view,world,mv );

	float c0[3]={ mv[0],mv[1],mv[2] };
	float c1[3]={ mv[4],mv[5],mv[6] };
	float c2[3]={ mv[8],mv[9],mv[10] };

	float x0[3],x1[3],x2[3];
	bbCross3( c1,c2,x0 );
	bbCross3( c2,c0,x1 );
	bbCross3( c0,c1,x2 );

	float det=c0[0]*x0[0]+c0[1]*x0[1]+c0[2]*x0[2];

	bbMat4Identity( out );
	if( fabsf( det )>1e-20f ){
		float inv=1.0f/det;
		out[0]=x0[0]*inv;out[1]=x0[1]*inv;out[2]=x0[2]*inv;
		out[4]=x1[0]*inv;out[5]=x1[1]*inv;out[6]=x1[2]*inv;
		out[8]=x2[0]*inv;out[9]=x2[1]*inv;out[10]=x2[2]*inv;
	}else{
		out[0]=c0[0];out[1]=c0[1];out[2]=c0[2];
		out[4]=c1[0];out[5]=c1[1];out[6]=c1[2];
		out[8]=c2[0];out[9]=c2[1];out[10]=c2[2];
	}
}


class WebGPULight : public BBLightRep{
public:
	int type=0;

	float r=1,g=1,b=1;
	float matrix[16];

	float range=0;
	float inner_angle=0,outer_angle=0;

	WebGPULight(){ bbMat4Identity( matrix ); }

	void update( Light *light ){
		type=light->getType();

		r=light->getColor().x;
		g=light->getColor().y;
		b=light->getColor().z;

		range=light->getRange();
		light->getConeAngles( inner_angle,outer_angle );
		outer_angle*=rtod;

		const BBScene::Matrix *m=(BBScene::Matrix*)&(light->getRenderTform());

		matrix[ 0]=m->elements[0][0]; matrix[ 1]=m->elements[0][1]; matrix[ 2]=m->elements[0][2]; matrix[ 3]=0.0f;
		matrix[ 4]=m->elements[1][0]; matrix[ 5]=m->elements[1][1]; matrix[ 6]=m->elements[1][2]; matrix[ 7]=0.0f;
		matrix[ 8]=m->elements[2][0]; matrix[ 9]=m->elements[2][1]; matrix[10]=m->elements[2][2]; matrix[11]=0.0f;
		matrix[12]=m->elements[3][0]; matrix[13]=m->elements[3][1]; matrix[14]=m->elements[3][2]; matrix[15]=1.0f;
	}
};


class WebGPUMesh : public BBMesh{
protected:
	bool dirty()const{ return false; }

public:
	WebGPUScene *scene;
	int max_verts,max_tris,flags;
	BBWebGPU3DVertex *verts=0;
	unsigned int *tris=0;

	WGPUBuffer vertex_buffer=0,index_buffer=0;
	bool index16=false;

	unsigned drawn_marker=0;
	int drawn_v0=0,drawn_v1=0,drawn_t0=0,drawn_t1=0;

	int dirty_v0=0,dirty_v1=0,dirty_t0=0,dirty_t1=0;

	WGPUBuffer wire_buffer=0;
	unsigned tri_version=1,wire_version=0;

	WebGPUMesh( WebGPUScene *scene,int mv,int mt,int f );
	~WebGPUMesh();

	WGPUBuffer wireBuffer();

	bool lock( bool all ){
		if( !verts ){
			verts=new BBWebGPU3DVertex[max_verts]();
			dirty_v0=max_verts;dirty_v1=0;
		}
		if( !tris ){
			tris=new unsigned int[max_tris*3]();
			dirty_t0=max_tris;dirty_t1=0;
		}
		return true;
	}

	void writeVertex( int n,const BBWebGPU3DVertex &v ){
		if( n<0||n>=max_verts||!verts ) return;
		if( !memcmp( &verts[n],&v,sizeof( v ) ) ) return;
		verts[n]=v;
		if( n<dirty_v0 ) dirty_v0=n;
		if( n+1>dirty_v1 ) dirty_v1=n+1;
	}

	// Keep edits on the CPU until the mesh is actually drawn. Sprites share a
	// mesh, so their separate lock/unlock calls become one contiguous upload.
	void unlock(){}
	void upload();

	void setVertex( int n,const void *_v ){
		const Surface::Vertex *v=(const Surface::Vertex*)_v;
		float coords[3]={ v->coords.x,v->coords.y,v->coords.z };
		float normal[3]={ v->normal.x,v->normal.y,v->normal.z };
		setVertex( n,coords,normal,v->color,v->tex_coords );
	}

	void setVertex( int n,const float coords[3],const float normal[3],const float tex_coords[2][2] ){
		setVertex( n,coords,normal,0xffffffff,tex_coords );
	}

	void setVertex( int n,const float coords[3],const float normal[3],unsigned argb,const float tex_coords[2][2] ){
		BBWebGPU3DVertex v;
		v.coords[0]=coords[0];v.coords[1]=coords[1];v.coords[2]=coords[2];
		v.normal[0]=normal[0];v.normal[1]=normal[1];v.normal[2]=normal[2];
		v.tex_coord0[0]=tex_coords[0][0];v.tex_coord0[1]=tex_coords[0][1];
		v.tex_coord1[0]=tex_coords[1][0];v.tex_coord1[1]=tex_coords[1][1];
		v.color[0]=(uint8_t)((argb>>16)&255);
		v.color[1]=(uint8_t)((argb>>8)&255);
		v.color[2]=(uint8_t)(argb&255);
		v.color[3]=(uint8_t)((argb>>24)&255);
		writeVertex( n,v );
	}

	void setTriangle( int n,int v0,int v1,int v2 ){
		if( n<0||n>=max_tris||!tris ) return;
		unsigned int *t=tris+n*3;
		if( t[0]==(unsigned)v2&&t[1]==(unsigned)v1&&t[2]==(unsigned)v0 ) return;
		t[0]=v2;
		t[1]=v1;
		t[2]=v0;
		++tri_version;
		if( n<dirty_t0 ) dirty_t0=n;
		if( n+1>dirty_t1 ) dirty_t1=n+1;
	}
};


static unsigned g_mesh_generation=1;

class WebGPUScene : public BBScene{
public:
	WebGPUContextResources *res;

private:
	int context_width=0,context_height=0;
	bool wireframe=false;
	bool flipped_tris=false;
	bool doublesided=false;
	int zmode=ZMODE_NORMAL;
	int blend_mode=BLEND_REPLACE;
	int fog_mode=FOG_NONE;
	int viewport[4]={ 0,0,0,0 };
	int tris_drawn=0;

	float view_matrix[16];
	float world_matrix[16];

	BBWebGPUFrameState frame;
	BBWebGPUEntityState entity;
	bool frame_dirty=true,entity_dirty=true;
	uint32_t frame_offset=0,entity_offset=0;

	struct UniformCache{
		std::vector<unsigned char> bytes;
		uint32_t offset=0;
		bool valid=false;
	};
	UniformCache frame_cache,entity_cache;

	WGPURenderPipeline bound_pipeline=0;
	bool bound_frame=false,bound_entity=false;
	uint32_t bound_frame_offset=0,bound_entity_offset=0;
	WGPUBindGroup bound_tex_group=0;
	WGPUBuffer bound_vertex_buffer=0,bound_index_buffer=0;
	bool bound_instances=false;
	unsigned bound_mesh_generation=0;
	uint32_t pending_instances=0,pending_first_instance=0;
	int pending_index_first=0,pending_index_count=0,pending_base_vertex=0;

	WGPUTextureView last_views[8];
	WGPUSampler last_samplers[8];
	bool last_tex_valid=false;

	WGPUSampler sampler_fast[16]={};

	void resetPassState(){
		bound_pipeline=0;
		bound_frame=bound_entity=false;
		bound_tex_group=0;
		bound_vertex_buffer=bound_index_buffer=0;
		bound_instances=false;
	}

	std::vector<WebGPULight*> lights;
	float light_world[8][16];

	void updateLightsEye(){
		const float rot_y=cosf( 1.5708f ),rot_z=-sinf( 1.5708f );
		for( int i=0;i<frame.lights_used;i++ ){
			float mv[16];
			bbMat4Mul( frame.view,light_world[i],mv );

			float d[3];
			for( int k=0;k<3;k++ ) d[k]=mv[4+k]*rot_y+mv[8+k]*rot_z;
			float len=sqrtf( d[0]*d[0]+d[1]*d[1]+d[2]*d[2] );
			if( len>0.0f ){ d[0]/=len;d[1]/=len;d[2]/=len; }
			frame.lights[i].dir[0]=d[0];frame.lights[i].dir[1]=d[1];frame.lights[i].dir[2]=d[2];frame.lights[i].dir[3]=0.0f;

			const float *w=light_world[i];
			for( int k=0;k<3;k++ ){
				frame.lights[i].pos[k]=frame.view[0+k]*w[12]+frame.view[4+k]*w[13]+frame.view[8+k]*w[14]+frame.view[12+k];
			}
			frame.lights[i].pos[3]=1.0f;
		}
	}

	WGPUShaderModule shader=0;
	WGPUBindGroupLayout bgl_frame=0,bgl_entity=0,bgl_textures=0,bgl_clear=0;
	WGPUPipelineLayout scene_layout=0,clear_layout=0;

	std::map<uint32_t,WGPURenderPipeline> scene_pipelines;
	std::map<uint32_t,WGPURenderPipeline> clear_pipelines;

	WGPUBuffer uniform_buffer=0;
	size_t uniform_capacity=0,uniform_used=0,uniform_uploaded=0;
	std::vector<unsigned char> uniform_stage;
	WGPUBuffer instance_buffer=0;
	size_t instance_capacity=0,instance_used=0;
	std::vector<unsigned char> instance_stage;
	WGPUBindGroup frame_group=0,entity_group=0,clear_group=0;

	std::map<std::array<uintptr_t,16>,WGPUBindGroup> tex_groups;
	WGPUBindGroup current_tex_group=0,default_tex_group=0;

	std::map<uint32_t,WGPUSampler> samplers;

	struct DepthTarget{
		WGPUTexture tex=0;
		WGPUTextureView view=0;
		int width=0,height=0;
		bool initialized=false;
	};
	std::map<WebGPUCanvas*,DepthTarget> depth_targets;

	WebGPUCanvas *target=0;
	WGPURenderPassEncoder pass=0;
	WebGPUCanvas *pass_canvas=0;

	bool pending_clear_color=false,pending_clear_z=false;
	float clear_color[4]={ 0,0,0,1 };
	float clear_depth=1.0f;

	static void viewGoneThunk( void *ctx,WGPUTextureView view ){
		((WebGPUScene*)ctx)->onViewGone( view );
	}
	static void canvasGoneThunk( void *ctx,WebGPUCanvas *canvas ){
		((WebGPUScene*)ctx)->onCanvasGone( canvas );
	}
	static void preFlushThunk( void *ctx ){
		WebGPUScene *scene=(WebGPUScene*)ctx;
		scene->endPass();
		scene->uploadStagedUniforms();
		if( scene->instance_used ){
			wgpuQueueWriteBuffer( scene->res->queue,scene->instance_buffer,0,
			                      scene->instance_stage.data(),scene->instance_used );
		}
	}
	static void postFlushThunk( void *ctx ){
		((WebGPUScene*)ctx)->resetAfterFlush();
	}

	void uploadStagedUniforms(){
		if( uniform_used>uniform_uploaded ){
			wgpuQueueWriteBuffer( res->queue,uniform_buffer,uniform_uploaded,
			                      uniform_stage.data()+uniform_uploaded,uniform_used-uniform_uploaded );
			uniform_uploaded=uniform_used;
		}
	}

	void onViewGone( WGPUTextureView view ){
		flushPendingDraw();
		uintptr_t v=(uintptr_t)view;
		std::map<std::array<uintptr_t,16>,WGPUBindGroup>::iterator it=tex_groups.begin();
		while( it!=tex_groups.end() ){
			bool hit=false;
			for( int i=0;i<8;i++ ){
				if( it->first[i*2]==v ){ hit=true;break; }
			}
			if( hit ){
				if( it->second==current_tex_group ) current_tex_group=0;
				if( it->second==default_tex_group ) default_tex_group=0;
				if( it->second==bound_tex_group ) bound_tex_group=0;
				wgpuBindGroupRelease( it->second );
				it=tex_groups.erase( it );
			}else{
				++it;
			}
		}
	}

	void onCanvasGone( WebGPUCanvas *canvas ){
		if( pass_canvas==canvas ) endPass();
		if( target==canvas ){
			endPass();
			target=0;
			pending_clear_color=false;
			pending_clear_z=false;
		}
		std::map<WebGPUCanvas*,DepthTarget>::iterator it=depth_targets.find( canvas );
		if( it!=depth_targets.end() ){
			if( it->second.view ) wgpuTextureViewRelease( it->second.view );
			if( it->second.tex ) wgpuTextureRelease( it->second.tex );
			depth_targets.erase( it );
		}
	}

public:
	unsigned submit_marker=1;

	WebGPUScene( WebGPUContextResources *res ):res(res){
		memset( &frame,0,sizeof(frame) );
		memset( &entity,0,sizeof(entity) );
		bbMat4Identity( frame.proj );
		bbMat4Identity( frame.view );
		bbMat4Identity( entity.world );
		bbMat4Identity( entity.normal_mat );
		bbMat4Identity( view_matrix );
		bbMat4Identity( world_matrix );

		entity.brush_color[0]=entity.brush_color[1]=entity.brush_color[2]=entity.brush_color[3]=1.0f;

		const float MIDLEVEL[]={ 0.5f,0.5f,0.5f };
		setAmbient( MIDLEVEL );

		ensurePipelineObjects();

		WebGPUCanvasListener l;
		l.ctx=this;
		l.view_gone=viewGoneThunk;
		l.canvas_gone=canvasGoneThunk;
		l.pre_flush=preFlushThunk;
		l.post_flush=postFlushThunk;
		res->addListener( l );
	}

	~WebGPUScene(){
		res->removeListener( this );
		endPass();

		for( std::map<std::array<uintptr_t,16>,WGPUBindGroup>::iterator it=tex_groups.begin();it!=tex_groups.end();++it ){
			wgpuBindGroupRelease( it->second );
		}
		tex_groups.clear();
		default_tex_group=0;

		for( std::map<uint32_t,WGPUSampler>::iterator it=samplers.begin();it!=samplers.end();++it ){
			wgpuSamplerRelease( it->second );
		}
		samplers.clear();

		for( std::map<WebGPUCanvas*,DepthTarget>::iterator it=depth_targets.begin();it!=depth_targets.end();++it ){
			if( it->second.view ) wgpuTextureViewRelease( it->second.view );
			if( it->second.tex ) wgpuTextureRelease( it->second.tex );
		}
		depth_targets.clear();

		for( std::map<uint32_t,WGPURenderPipeline>::iterator it=scene_pipelines.begin();it!=scene_pipelines.end();++it ){
			wgpuRenderPipelineRelease( it->second );
		}
		scene_pipelines.clear();
		for( std::map<uint32_t,WGPURenderPipeline>::iterator it=clear_pipelines.begin();it!=clear_pipelines.end();++it ){
			wgpuRenderPipelineRelease( it->second );
		}
		clear_pipelines.clear();

		if( frame_group ){ wgpuBindGroupRelease( frame_group );frame_group=0; }
		if( entity_group ){ wgpuBindGroupRelease( entity_group );entity_group=0; }
		if( clear_group ){ wgpuBindGroupRelease( clear_group );clear_group=0; }
		if( uniform_buffer ){ wgpuBufferRelease( uniform_buffer );uniform_buffer=0; }
		if( instance_buffer ){ wgpuBufferRelease( instance_buffer );instance_buffer=0; }
		if( scene_layout ){ wgpuPipelineLayoutRelease( scene_layout );scene_layout=0; }
		if( clear_layout ){ wgpuPipelineLayoutRelease( clear_layout );clear_layout=0; }
		if( bgl_frame ){ wgpuBindGroupLayoutRelease( bgl_frame );bgl_frame=0; }
		if( bgl_entity ){ wgpuBindGroupLayoutRelease( bgl_entity );bgl_entity=0; }
		if( bgl_textures ){ wgpuBindGroupLayoutRelease( bgl_textures );bgl_textures=0; }
		if( bgl_clear ){ wgpuBindGroupLayoutRelease( bgl_clear );bgl_clear=0; }
		if( shader ){ wgpuShaderModuleRelease( shader );shader=0; }
	}


	void ensurePipelineObjects(){
		if( shader||!res->device ) return;

		std::string src( (const char*)DEFAULT3D_WGSL,DEFAULT3D_WGSL_SIZE );
		WGPUShaderSourceWGSL wgsl={};
		wgsl.chain.sType=WGPUSType_ShaderSourceWGSL;
		wgsl.code.data=src.data();
		wgsl.code.length=src.size();
		WGPUShaderModuleDescriptor smdesc={};
		smdesc.nextInChain=&wgsl.chain;
		smdesc.label=bbStrView( "default3d.wgsl" );
		shader=wgpuDeviceCreateShaderModule( res->device,&smdesc );

		{
			WGPUBindGroupLayoutEntry e={};
			e.binding=0;
			e.visibility=WGPUShaderStage_Vertex|WGPUShaderStage_Fragment;
			e.buffer.type=WGPUBufferBindingType_Uniform;
			e.buffer.hasDynamicOffset=1;
			e.buffer.minBindingSize=sizeof( BBWebGPUFrameState );
			WGPUBindGroupLayoutDescriptor d={};
			d.label=bbStrView( "bb.3d.bgl.frame" );
			d.entryCount=1;
			d.entries=&e;
			bgl_frame=wgpuDeviceCreateBindGroupLayout( res->device,&d );
		}

		{
			WGPUBindGroupLayoutEntry e={};
			e.binding=0;
			e.visibility=WGPUShaderStage_Vertex|WGPUShaderStage_Fragment;
			e.buffer.type=WGPUBufferBindingType_Uniform;
			e.buffer.hasDynamicOffset=1;
			e.buffer.minBindingSize=sizeof( BBWebGPUEntityState );
			WGPUBindGroupLayoutDescriptor d={};
			d.label=bbStrView( "bb.3d.bgl.entity" );
			d.entryCount=1;
			d.entries=&e;
			bgl_entity=wgpuDeviceCreateBindGroupLayout( res->device,&d );
		}

		{
			WGPUBindGroupLayoutEntry e[16]={};
			for( int i=0;i<MAX_TEXTURES;i++ ){
				e[i*2].binding=i*2;
				e[i*2].visibility=WGPUShaderStage_Fragment;
				e[i*2].texture.sampleType=WGPUTextureSampleType_Float;
				e[i*2].texture.viewDimension=WGPUTextureViewDimension_2D;
				e[i*2+1].binding=i*2+1;
				e[i*2+1].visibility=WGPUShaderStage_Fragment;
				e[i*2+1].sampler.type=WGPUSamplerBindingType_Filtering;
			}
			WGPUBindGroupLayoutDescriptor d={};
			d.label=bbStrView( "bb.3d.bgl.textures" );
			d.entryCount=16;
			d.entries=e;
			bgl_textures=wgpuDeviceCreateBindGroupLayout( res->device,&d );
		}

		{
			WGPUBindGroupLayoutEntry e={};
			e.binding=0;
			e.visibility=WGPUShaderStage_Vertex|WGPUShaderStage_Fragment;
			e.buffer.type=WGPUBufferBindingType_Uniform;
			e.buffer.hasDynamicOffset=1;
			e.buffer.minBindingSize=sizeof( BBWebGPUClearState );
			WGPUBindGroupLayoutDescriptor d={};
			d.label=bbStrView( "bb.3d.bgl.clear" );
			d.entryCount=1;
			d.entries=&e;
			bgl_clear=wgpuDeviceCreateBindGroupLayout( res->device,&d );
		}

		{
			WGPUBindGroupLayout layouts[3]={ bgl_frame,bgl_entity,bgl_textures };
			WGPUPipelineLayoutDescriptor d={};
			d.label=bbStrView( "bb.3d.layout" );
			d.bindGroupLayoutCount=3;
			d.bindGroupLayouts=layouts;
			scene_layout=wgpuDeviceCreatePipelineLayout( res->device,&d );
		}
		{
			WGPUPipelineLayoutDescriptor d={};
			d.label=bbStrView( "bb.3d.clear.layout" );
			d.bindGroupLayoutCount=1;
			d.bindGroupLayouts=&bgl_clear;
			clear_layout=wgpuDeviceCreatePipelineLayout( res->device,&d );
		}

		uniform_capacity=(size_t)4*1024*1024;
		WGPUBufferDescriptor bd={};
		bd.label=bbStrView( "bb.3d.uniform.arena" );
		bd.usage=WGPUBufferUsage_Uniform|WGPUBufferUsage_CopyDst;
		bd.size=uniform_capacity;
		uniform_buffer=wgpuDeviceCreateBuffer( res->device,&bd );
		uniform_used=0;
		uniform_uploaded=0;
		uniform_stage.resize( uniform_capacity );

		instance_capacity=(size_t)4*1024*1024;
		bd.label=bbStrView( "bb.3d.instance.arena" );
		bd.usage=WGPUBufferUsage_Vertex|WGPUBufferUsage_CopyDst;
		bd.size=instance_capacity;
		instance_buffer=wgpuDeviceCreateBuffer( res->device,&bd );
		instance_stage.resize( instance_capacity );

		{
			WGPUBindGroupEntry e={};
			e.binding=0;
			e.buffer=uniform_buffer;
			e.offset=0;
			e.size=sizeof( BBWebGPUFrameState );
			WGPUBindGroupDescriptor d={};
			d.label=bbStrView( "bb.3d.group.frame" );
			d.layout=bgl_frame;
			d.entryCount=1;
			d.entries=&e;
			frame_group=wgpuDeviceCreateBindGroup( res->device,&d );

			e.size=sizeof( BBWebGPUEntityState );
			d.label=bbStrView( "bb.3d.group.entity" );
			d.layout=bgl_entity;
			entity_group=wgpuDeviceCreateBindGroup( res->device,&d );

			e.size=sizeof( BBWebGPUClearState );
			d.label=bbStrView( "bb.3d.group.clear" );
			d.layout=bgl_clear;
			clear_group=wgpuDeviceCreateBindGroup( res->device,&d );
		}

		prewarmPipelines();
	}

	WGPUSampler getSampler( int canvas_flags ){
		bool no_filter=canvas_flags&BBCanvas::CANVAS_TEX_NOFILTERING;
		bool mipmap=canvas_flags&BBCanvas::CANVAS_TEX_MIPMAP;
		bool clamp_u=canvas_flags&BBCanvas::CANVAS_TEX_CLAMPU;
		bool clamp_v=canvas_flags&BBCanvas::CANVAS_TEX_CLAMPV;

		uint32_t key=(no_filter?1u:0u)|(mipmap?2u:0u)|(clamp_u?4u:0u)|(clamp_v?8u:0u);
		if( sampler_fast[key] ) return sampler_fast[key];
		std::map<uint32_t,WGPUSampler>::iterator it=samplers.find( key );
		if( it!=samplers.end() ){
			sampler_fast[key]=it->second;
			return it->second;
		}

		WGPUSamplerDescriptor d={};
		d.label=bbStrView( "bb.3d.sampler" );
		d.addressModeU=clamp_u?WGPUAddressMode_ClampToEdge:WGPUAddressMode_Repeat;
		d.addressModeV=clamp_v?WGPUAddressMode_ClampToEdge:WGPUAddressMode_Repeat;
		d.addressModeW=WGPUAddressMode_Repeat;
		d.magFilter=no_filter?WGPUFilterMode_Nearest:WGPUFilterMode_Linear;
		d.minFilter=no_filter?WGPUFilterMode_Nearest:WGPUFilterMode_Linear;
		d.mipmapFilter=mipmap?WGPUMipmapFilterMode_Linear:WGPUMipmapFilterMode_Nearest;
		d.lodMinClamp=0.f;
		d.lodMaxClamp=32.f;
		d.maxAnisotropy=( mipmap && !no_filter )?8:1;

		WGPUSampler s=wgpuDeviceCreateSampler( res->device,&d );
		samplers[key]=s;
		sampler_fast[key]=s;
		return s;
	}

	WGPUBindGroup getTexBindGroup( WGPUTextureView views[8],WGPUSampler samps[8] ){
		std::array<uintptr_t,16> key;
		for( int i=0;i<8;i++ ){
			key[i*2]=(uintptr_t)views[i];
			key[i*2+1]=(uintptr_t)samps[i];
		}
		std::map<std::array<uintptr_t,16>,WGPUBindGroup>::iterator it=tex_groups.find( key );
		if( it!=tex_groups.end() ) return it->second;

		WGPUBindGroupEntry e[16]={};
		for( int i=0;i<8;i++ ){
			e[i*2].binding=i*2;
			e[i*2].textureView=views[i];
			e[i*2+1].binding=i*2+1;
			e[i*2+1].sampler=samps[i];
		}
		WGPUBindGroupDescriptor d={};
		d.label=bbStrView( "bb.3d.group.textures" );
		d.layout=bgl_textures;
		d.entryCount=16;
		d.entries=e;
		WGPUBindGroup group=wgpuDeviceCreateBindGroup( res->device,&d );
		tex_groups[key]=group;
		return group;
	}

	WGPUBindGroup defaultTexGroup(){
		if( !default_tex_group ){
			WGPUTextureView views[8];
			WGPUSampler samps[8];
			for( int i=0;i<8;i++ ){ views[i]=res->white_view;samps[i]=res->sampler_linear; }
			default_tex_group=getTexBindGroup( views,samps );
		}
		return default_tex_group;
	}

	WGPURenderPipeline getScenePipeline( WGPUTextureFormat format ){
		return getScenePipeline( blend_mode,zmode,flipped_tris,doublesided,wireframe,format );
	}

	void prewarmPipelines(){
		WGPUTextureFormat formats[2]={ WGPUTextureFormat_RGBA8Unorm,WGPUTextureFormat_RGB10A2Unorm };
		for( int f=0;f<2;++f ){
			for( int blend=BLEND_REPLACE;blend<=BLEND_ADD;++blend ){
				for( int z=ZMODE_NORMAL;z<=ZMODE_CMPONLY;++z ){
					for( int flip=0;flip<2;++flip ){
						for( int dbl=0;dbl<2;++dbl ){
							getScenePipeline( blend,z,flip!=0,dbl!=0,false,formats[f] );
						}
					}
				}
			}
			getClearPipeline( true,true,formats[f] );
			getClearPipeline( true,false,formats[f] );
			getClearPipeline( false,true,formats[f] );
		}
	}

	WGPURenderPipeline getScenePipeline( int blend_mode,int zmode,bool flipped_tris,bool doublesided,bool wireframe,WGPUTextureFormat format ){
		uint32_t key=(uint32_t)blend_mode
			|((uint32_t)zmode<<3)
			|((flipped_tris?1u:0u)<<5)
			|((doublesided?1u:0u)<<6)
			|((wireframe?1u:0u)<<7)
			|((uint32_t)format<<8);
		std::map<uint32_t,WGPURenderPipeline>::iterator it=scene_pipelines.find( key );
		if( it!=scene_pipelines.end() ) return it->second;

		WGPUVertexAttribute attrs[5]={};
		attrs[0].format=WGPUVertexFormat_Float32x3;
		attrs[0].offset=offsetof( BBWebGPU3DVertex,coords );
		attrs[0].shaderLocation=0;
		attrs[1].format=WGPUVertexFormat_Float32x3;
		attrs[1].offset=offsetof( BBWebGPU3DVertex,normal );
		attrs[1].shaderLocation=1;
		attrs[2].format=WGPUVertexFormat_Unorm8x4;
		attrs[2].offset=offsetof( BBWebGPU3DVertex,color );
		attrs[2].shaderLocation=2;
		attrs[3].format=WGPUVertexFormat_Float32x2;
		attrs[3].offset=offsetof( BBWebGPU3DVertex,tex_coord0 );
		attrs[3].shaderLocation=3;
		attrs[4].format=WGPUVertexFormat_Float32x2;
		attrs[4].offset=offsetof( BBWebGPU3DVertex,tex_coord1 );
		attrs[4].shaderLocation=4;

		WGPUVertexAttribute instance_attrs[7]={};
		for( int i=0;i<7;++i ){
			instance_attrs[i].format=WGPUVertexFormat_Float32x4;
			instance_attrs[i].offset=i*4*sizeof(float);
			instance_attrs[i].shaderLocation=5+i;
		}
		WGPUVertexBufferLayout vbl[2]={};
		vbl[0].stepMode=WGPUVertexStepMode_Vertex;
		vbl[0].arrayStride=sizeof( BBWebGPU3DVertex );
		vbl[0].attributeCount=5;
		vbl[0].attributes=attrs;
		vbl[1].stepMode=WGPUVertexStepMode_Instance;
		vbl[1].arrayStride=sizeof( BBWebGPUInstance );
		vbl[1].attributeCount=7;
		vbl[1].attributes=instance_attrs;

		WGPUBlendState blend_state={};
		blend_state.color.operation=WGPUBlendOperation_Add;
		blend_state.alpha.operation=WGPUBlendOperation_Add;
		bool blended=true;
		switch( blend_mode ){
		default:
		case BLEND_REPLACE:
			blended=false;
			break;
		case BLEND_ALPHA:
			blend_state.color.srcFactor=WGPUBlendFactor_SrcAlpha;
			blend_state.color.dstFactor=WGPUBlendFactor_OneMinusSrcAlpha;
			blend_state.alpha.srcFactor=WGPUBlendFactor_One;
			blend_state.alpha.dstFactor=WGPUBlendFactor_OneMinusSrcAlpha;
			break;
		case BLEND_MULTIPLY:
			blend_state.color.srcFactor=WGPUBlendFactor_Dst;
			blend_state.color.dstFactor=WGPUBlendFactor_Zero;
			blend_state.alpha.srcFactor=WGPUBlendFactor_Dst;
			blend_state.alpha.dstFactor=WGPUBlendFactor_Zero;
			break;
		case BLEND_ADD:
			blend_state.color.srcFactor=WGPUBlendFactor_SrcAlpha;
			blend_state.color.dstFactor=WGPUBlendFactor_One;
			blend_state.alpha.srcFactor=WGPUBlendFactor_SrcAlpha;
			blend_state.alpha.dstFactor=WGPUBlendFactor_One;
			break;
		}

		WGPUColorTargetState target_state={};
		target_state.format=format;
		target_state.blend=blended?&blend_state:0;
		target_state.writeMask=WGPUColorWriteMask_All;

		WGPUFragmentState frag={};
		frag.module=shader;
		frag.entryPoint=bbStrView( "fs_main" );
		frag.targetCount=1;
		frag.targets=&target_state;

		WGPUDepthStencilState ds={};
		ds.format=WGPUTextureFormat_Depth24Plus;
		switch( zmode ){
		default:
		case ZMODE_NORMAL:
			ds.depthWriteEnabled=WGPUOptionalBool_True;
			ds.depthCompare=WGPUCompareFunction_LessEqual;
			break;
		case ZMODE_DISABLE:
			ds.depthWriteEnabled=WGPUOptionalBool_False;
			ds.depthCompare=WGPUCompareFunction_Always;
			break;
		case ZMODE_CMPONLY:
			ds.depthWriteEnabled=WGPUOptionalBool_False;
			ds.depthCompare=WGPUCompareFunction_LessEqual;
			break;
		}

		WGPURenderPipelineDescriptor desc={};
		desc.label=bbStrView( "bb.3d.pipeline" );
		desc.layout=scene_layout;
		desc.vertex.module=shader;
		desc.vertex.entryPoint=bbStrView( "vs_main" );
		desc.vertex.bufferCount=2;
		desc.vertex.buffers=vbl;
		desc.primitive.topology=wireframe?WGPUPrimitiveTopology_LineList:WGPUPrimitiveTopology_TriangleList;
		desc.primitive.stripIndexFormat=WGPUIndexFormat_Undefined;
		desc.primitive.frontFace=flipped_tris?WGPUFrontFace_CW:WGPUFrontFace_CCW;
		desc.primitive.cullMode=( doublesided||wireframe )?WGPUCullMode_None:WGPUCullMode_Back;
		desc.depthStencil=&ds;
		desc.multisample.count=1;
		desc.multisample.mask=0xffffffff;
		desc.fragment=&frag;

		WGPURenderPipeline pipeline=wgpuDeviceCreateRenderPipeline( res->device,&desc );
		scene_pipelines[key]=pipeline;
		return pipeline;
	}

	WGPURenderPipeline getClearPipeline( bool clear_argb,bool clear_z,WGPUTextureFormat format ){
		uint32_t key=(clear_argb?1u:0u)|((clear_z?1u:0u)<<1)|((uint32_t)format<<8);
		std::map<uint32_t,WGPURenderPipeline>::iterator it=clear_pipelines.find( key );
		if( it!=clear_pipelines.end() ) return it->second;

		WGPUColorTargetState target_state={};
		target_state.format=format;
		target_state.blend=0;
		target_state.writeMask=clear_argb?WGPUColorWriteMask_All:WGPUColorWriteMask_None;

		WGPUFragmentState frag={};
		frag.module=shader;
		frag.entryPoint=bbStrView( "fs_clear" );
		frag.targetCount=1;
		frag.targets=&target_state;

		WGPUDepthStencilState ds={};
		ds.format=WGPUTextureFormat_Depth24Plus;
		ds.depthWriteEnabled=clear_z?WGPUOptionalBool_True:WGPUOptionalBool_False;
		ds.depthCompare=WGPUCompareFunction_Always;

		WGPURenderPipelineDescriptor desc={};
		desc.label=bbStrView( "bb.3d.clear.pipeline" );
		desc.layout=clear_layout;
		desc.vertex.module=shader;
		desc.vertex.entryPoint=bbStrView( "vs_clear" );
		desc.primitive.topology=WGPUPrimitiveTopology_TriangleList;
		desc.primitive.frontFace=WGPUFrontFace_CCW;
		desc.primitive.cullMode=WGPUCullMode_None;
		desc.depthStencil=&ds;
		desc.multisample.count=1;
		desc.multisample.mask=0xffffffff;
		desc.fragment=&frag;

		WGPURenderPipeline pipeline=wgpuDeviceCreateRenderPipeline( res->device,&desc );
		clear_pipelines[key]=pipeline;
		return pipeline;
	}


	uint32_t pushUniforms( const void *data,size_t size,size_t bind_size=0 ){
		size_t extent=size>bind_size?size:bind_size;
		size_t offset=(uniform_used+255)&~(size_t)255;
		if( offset+extent>uniform_capacity ){
			flushAll();
			offset=0;
		}
		memcpy( uniform_stage.data()+offset,data,size );
		uniform_used=offset+size;
		return (uint32_t)offset;
	}

	uint32_t pushCached( UniformCache &cache,const void *data,size_t size,size_t bind_size,size_t skip=0 ){
		if( cache.valid&&cache.bytes.size()==size&&
		    memcmp( cache.bytes.data()+skip,(const unsigned char*)data+skip,size-skip )==0 ){
			return cache.offset;
		}
		uint32_t offset=pushUniforms( data,size,bind_size );
		cache.bytes.assign( (const unsigned char*)data,(const unsigned char*)data+size );
		cache.offset=offset;
		cache.valid=true;
		return offset;
	}

	void flushPendingDraw(){
		if( !pending_instances ) return;
		wgpuRenderPassEncoderDrawIndexed( pass,(uint32_t)pending_index_count,pending_instances,
		                                 (uint32_t)pending_index_first,pending_base_vertex,pending_first_instance );
		pending_instances=0;
	}

	void endPass(){
		flushPendingDraw();
		if( pass ){
			wgpuRenderPassEncoderEnd( pass );
			wgpuRenderPassEncoderRelease( pass );
			pass=0;
		}
		pass_canvas=0;
		resetPassState();
	}

	void flushAll(){
		endPass();
		res->flush();
	}

	void resetAfterFlush(){
		uniform_used=0;
		uniform_uploaded=0;
		instance_used=0;
		++submit_marker;
		frame_dirty=true;
		entity_dirty=true;
		frame_cache.valid=false;
		entity_cache.valid=false;
	}

	DepthTarget *ensureDepth( WebGPUCanvas *canvas ){
		int w=canvas->getWidth(),h=canvas->getHeight();
		if( w<=0||h<=0 ) return 0;

		DepthTarget &dt=depth_targets[canvas];
		if( dt.tex&&dt.width==w&&dt.height==h ) return &dt;

		if( dt.view ){ wgpuTextureViewRelease( dt.view );dt.view=0; }
		if( dt.tex ){ wgpuTextureRelease( dt.tex );dt.tex=0; }

		WGPUTextureDescriptor d={};
		d.label=bbStrView( "bb.3d.depth" );
		d.usage=WGPUTextureUsage_RenderAttachment;
		d.dimension=WGPUTextureDimension_2D;
		d.size={ (uint32_t)w,(uint32_t)h,1 };
		d.format=WGPUTextureFormat_Depth24Plus;
		d.mipLevelCount=1;
		d.sampleCount=1;
		dt.tex=wgpuDeviceCreateTexture( res->device,&d );
		if( !dt.tex ) return 0;
		dt.view=wgpuTextureCreateView( dt.tex,0 );
		dt.width=w;
		dt.height=h;
		dt.initialized=false;
		return &dt;
	}

	void applyViewport(){
		if( !pass||!pass_canvas ) return;
		int cw=pass_canvas->getWidth(),ch=pass_canvas->getHeight();
		int x=viewport[0],y=viewport[1],w=viewport[2],h=viewport[3];
		if( w<=0||h<=0 ){ x=0;y=0;w=cw;h=ch; }
		if( x<0 ){ w+=x;x=0; }
		if( y<0 ){ h+=y;y=0; }
		if( x+w>cw ) w=cw-x;
		if( y+h>ch ) h=ch-y;
		if( w<=0||h<=0 ) return;
		wgpuRenderPassEncoderSetViewport( pass,(float)x,(float)y,(float)w,(float)h,0.0f,1.0f );
		wgpuRenderPassEncoderSetScissorRect( pass,(uint32_t)x,(uint32_t)y,(uint32_t)w,(uint32_t)h );
	}

	WGPURenderPassEncoder ensurePass(){
		if( pass&&pass_canvas==target ) return pass;

		endPass();
		if( !target||!res->device ) return 0;

		WGPUTextureView view=target->targetView();
		if( !view ) return 0;

		DepthTarget *dt=ensureDepth( target );
		if( !dt||!dt->view ) return 0;

		res->endPass();
		WGPUCommandEncoder enc=res->ensureEncoder();

		WGPURenderPassColorAttachment att={};
		att.view=view;
		att.depthSlice=WGPU_DEPTH_SLICE_UNDEFINED;
		att.loadOp=pending_clear_color?WGPULoadOp_Clear:WGPULoadOp_Load;
		att.storeOp=WGPUStoreOp_Store;
		att.clearValue={ clear_color[0],clear_color[1],clear_color[2],clear_color[3] };

		WGPURenderPassDepthStencilAttachment datt={};
		datt.view=dt->view;
		bool zclear=pending_clear_z||!dt->initialized;
		datt.depthLoadOp=zclear?WGPULoadOp_Clear:WGPULoadOp_Load;
		datt.depthStoreOp=WGPUStoreOp_Store;
		datt.depthClearValue=pending_clear_z?clear_depth:1.0f;
		dt->initialized=true;

		WGPURenderPassDescriptor desc={};
		desc.label=bbStrView( "bb.3d.pass" );
		desc.colorAttachmentCount=1;
		desc.colorAttachments=&att;
		desc.depthStencilAttachment=&datt;

		pass=wgpuCommandEncoderBeginRenderPass( enc,&desc );
		pass_canvas=target;
		pending_clear_color=false;
		pending_clear_z=false;

		applyViewport();
		return pass;
	}


	int  hwTexUnits(){ return MAX_TEXTURES; }
	int  gfxDriverCaps3D(){ return 110; }

	void setWBuffer( bool enable ){}
	void setHWMultiTex( bool enable ){}
	void setDither( bool enable ){}
	void setAntialias( bool enable ){ res->antialias=enable; }

	void setTextureLodBias( float bias ){
		frame.lod_bias=bias;
		frame_dirty=true;
	}

	void setWireframe( bool enable ){
		wireframe=enable;
	}

	void setFlippedTris( bool enable ){
		flipped_tris=enable;
	}

	void setAmbient( const float rgb[3] ){
		entity.ambient[0]=rgb[0];entity.ambient[1]=rgb[1];entity.ambient[2]=rgb[2];entity.ambient[3]=1.0f;
		entity_dirty=true;
	}

	void setAmbient2( const float rgb[3] ){
		setAmbient( rgb );
	}

	void setFogColor( const float rgb[3] ){
		entity.fog_color[0]=rgb[0];entity.fog_color[1]=rgb[1];entity.fog_color[2]=rgb[2];entity.fog_color[3]=1.0f;
		entity_dirty=true;
	}

	void setFogRange( float nr,float fr ){
		entity.fog_range[0]=nr;entity.fog_range[1]=fr;
		entity_dirty=true;
	}

	void setFogMode( int mode ){
		fog_mode=mode;
		entity.fog_mode=mode;
		entity_dirty=true;
	}

	void setZMode( int mode ){
		zmode=mode;
	}

	void setCanvas( int w,int h ){
		context_width=w;
		context_height=h;

		WebGPUCanvas *t=dynamic_cast<WebGPUCanvas*>( gx_canvas );
		if( t!=target ){
			if( ( pending_clear_color||pending_clear_z )&&target ){
				ensurePass();
			}
			endPass();
			target=t;
			pending_clear_color=false;
			pending_clear_z=false;
		}
	}

	void setViewport( int x,int y,int w,int h ){
		flushPendingDraw();
		viewport[0]=x;viewport[1]=y;viewport[2]=w;viewport[3]=h;
		if( pass&&pass_canvas==target ) applyViewport();
	}

	void setProj( const float matrix[16] ){
		memcpy( frame.proj,matrix,sizeof(frame.proj) );
		frame_dirty=true;
	}

	void setOrthoProj( float nr,float fr,float nr_l,float nr_r,float nr_t,float nr_b ){
		float mat[16]={
			1.0,0.0,0.0,0.0,
			0.0,1.0,0.0,0.0,
			0.0,0.0,1.0,0.0,
			0.0,0.0,0.0,1.0
		};

		float w=nr_r-nr_l;
		float h=nr_b-nr_t;

		float W=2/w;
		float H=2/h;
		float Q=1/(fr-nr);
		mat[0]=W;
		mat[5]=H;
		mat[10]=-Q;
		mat[11]=0;
		mat[14]=-Q*nr;
		mat[15]=1;

		setProj( mat );
	}

	void setPerspProj( float nr,float fr,float nr_l,float nr_r,float nr_t,float nr_b ){
		float mat[16]={
			1.0,0.0,0.0,0.0,
			0.0,1.0,0.0,0.0,
			0.0,0.0,1.0,0.0,
			0.0,0.0,0.0,1.0
		};

		mat[0] = (2.0f*nr) / (nr_r-nr_l);
		mat[5] = (2.0f*nr) / (nr_t-nr_b);
		mat[8] = (nr_r+nr_l) / (nr_r-nr_l);
		mat[9] = (nr_t+nr_b) / (nr_t-nr_b);
		mat[10] = -fr / (fr-nr);
		mat[11] = -1.0f;
		mat[14] = -(fr*nr) / (fr-nr);
		mat[15] = 0.0f;

		setProj( mat );
	}

	void setViewMatrix( const Matrix *matrix ){
		float mat[16]={
			1.0,0.0, 0.0,0.0,
			0.0,1.0, 0.0,0.0,
			0.0,0.0,-1.0,0.0,
			0.0,0.0, 0.0,1.0
		};

		if( matrix ){
			const Matrix *m=matrix;
			mat[ 0]=m->elements[0][0]; mat[ 1]=m->elements[0][1]; mat[ 2]=-m->elements[0][2];
			mat[ 4]=m->elements[1][0]; mat[ 5]=m->elements[1][1]; mat[ 6]=-m->elements[1][2];
			mat[ 8]=m->elements[2][0]; mat[ 9]=m->elements[2][1]; mat[10]=-m->elements[2][2];
			mat[12]=m->elements[3][0]; mat[13]=m->elements[3][1]; mat[14]=-m->elements[3][2];
		}

		memcpy( view_matrix,mat,sizeof(view_matrix) );
		memcpy( frame.view,mat,sizeof(frame.view) );
		frame_dirty=true;

		bbNormalMatrix( view_matrix,world_matrix,entity.normal_mat );
		entity_dirty=true;
	}

	void setWorldMatrix( const Matrix *matrix ){
		float mat[16]={
			1.0,0.0,0.0,0.0,
			0.0,1.0,0.0,0.0,
			0.0,0.0,1.0,0.0,
			0.0,0.0,0.0,1.0
		};

		if( matrix ){
			const Matrix *m=matrix;
			mat[ 0]=m->elements[0][0];  mat[ 1]=m->elements[0][1]; mat[ 2]=m->elements[0][2];
			mat[ 4]=m->elements[1][0];  mat[ 5]=m->elements[1][1]; mat[ 6]=m->elements[1][2];
			mat[ 8]=m->elements[2][0];  mat[ 9]=m->elements[2][1]; mat[10]=m->elements[2][2];
			mat[12]=m->elements[3][0];  mat[13]=m->elements[3][1]; mat[14]=m->elements[3][2];
		}

		memcpy( world_matrix,mat,sizeof(world_matrix) );
		memcpy( entity.world,mat,sizeof(entity.world) );
		bbNormalMatrix( view_matrix,world_matrix,entity.normal_mat );
		entity_dirty=true;
	}

	void setRenderState( const RenderState &rs ){
		int alpha_test;
		if( rs.fx&FX_ALPHATEST && !(rs.fx&FX_VERTEXALPHA) ){
			alpha_test=1;
			entity.alpha_ref=128.0f*rs.alpha;
		}else{
			alpha_test=0;
			entity.alpha_ref=0.0f;
		}

		entity.fog_mode=(rs.fx&FX_NOFOG)?FOG_NONE:fog_mode;

		doublesided=( rs.fx&FX_DOUBLESIDED )!=0;
		blend_mode=rs.blend;


		entity.brush_color[0]=rs.color[0];entity.brush_color[1]=rs.color[1];entity.brush_color[2]=rs.color[2];
		entity.brush_color[3]=rs.alpha;
		entity.brush_shininess=rs.shininess;

		entity.fullbright=rs.fx&FX_FULLBRIGHT;
		entity.use_vertex_color=rs.fx&FX_VERTEXCOLOR;

		WGPUTextureView views[8];
		WGPUSampler samps[8];
		for( int i=0;i<8;i++ ){ views[i]=res->white_view;samps[i]=res->sampler_linear; }

		int used=0;
		for( int i=0;i<MAX_TEXTURES;i++ ){
			const RenderState::TexState &ts=rs.tex_states[i];
			if( !ts.canvas || !ts.blend ) continue;

			WebGPUCanvas *canvas=dynamic_cast<WebGPUCanvas*>( ts.canvas );
			if( !canvas||canvas->isSurface() ) continue;

			int flags=canvas->getFlags();

			if( canvas->needsUpload() ) endPass();
			WGPUTextureView view=canvas->sampleView();
			if( !view ) continue;

			if( flags&BBCanvas::CANVAS_TEX_ALPHA ){
				alpha_test=1;
			}

			float mat[16]={
				1.0,0.0, 0.0,0.0,
				0.0,1.0, 0.0,0.0,
				0.0,0.0,-1.0,0.0,
				0.0,0.0, 0.0,1.0
			};
			const Matrix *m=ts.matrix;
			if( m ){
				mat[ 0]=m->elements[0][0]; mat[ 1]=m->elements[0][1]; mat[ 2]=-m->elements[0][2];
				mat[ 4]=m->elements[1][0]; mat[ 5]=m->elements[1][1]; mat[ 6]=-m->elements[1][2];
				mat[ 8]=m->elements[2][0]; mat[ 9]=m->elements[2][1]; mat[10]=-m->elements[2][2];
				mat[12]=m->elements[3][0]; mat[13]=m->elements[3][1]; mat[14]=-m->elements[3][2];
			}

			BBWebGPUEntityState::TexState &es=entity.texs[used];
			memcpy( es.mat,mat,sizeof(mat) );
			es.blend=ts.blend;
			es.sphere_map=( flags&BBCanvas::CANVAS_TEX_SPHERE )?1:0;
			es.alpha=( flags&BBCanvas::CANVAS_TEX_ALPHA )?1:0;
			es.flags=ts.flags;

			// WebGPU uploads, 2D draws and render targets all use a top-left
			// texture origin. Flipping GPU-written textures here mirrors the
			// CopyRect history used by motion blur (and every dynamic texture).

			views[used]=view;
			samps[used]=getSampler( flags );
			used++;
		}


		entity.texs_used=used;
		entity.alpha_test=alpha_test;
		entity_dirty=true;

		if( !last_tex_valid||!current_tex_group||
		    memcmp( views,last_views,sizeof(views) )||memcmp( samps,last_samplers,sizeof(samps) ) ){
			current_tex_group=getTexBindGroup( views,samps );
			memcpy( last_views,views,sizeof(views) );
			memcpy( last_samplers,samps,sizeof(samps) );
			last_tex_valid=true;
		}
	}


	bool begin( const std::vector<BBLightRep*> &l ){
		if( !res->device ) return false;
		ensurePipelineObjects();
		if( !shader ) return false;

		// Camera changes only require a new pass. Keep earlier camera uniforms
		// and draws until a real submission or a conflicting mesh update.
		endPass();
		res->endPass();

		if( tex_groups.size()>2048 ){
			for( std::map<std::array<uintptr_t,16>,WGPUBindGroup>::iterator it=tex_groups.begin();it!=tex_groups.end();++it ){
				wgpuBindGroupRelease( it->second );
			}
			tex_groups.clear();
			default_tex_group=0;
			current_tex_group=0;
			bound_tex_group=0;
		}

		lights.clear();
		for( unsigned long i=0;i<l.size();i++ ){
			WebGPULight *lt=dynamic_cast<WebGPULight*>( l[i] );
			if( lt ) lights.push_back( lt );
		}

		memset( frame.lights,0,sizeof(frame.lights) );
		frame.lights_used=0;
		for( unsigned long i=0;i<8&&i<lights.size();i++ ){
			memcpy( light_world[i],lights[i]->matrix,sizeof(light_world[i]) );
			frame.lights[i].color[0]=lights[i]->r;
			frame.lights[i].color[1]=lights[i]->g;
			frame.lights[i].color[2]=lights[i]->b;
			frame.lights[i].color[3]=1.0f;
			frame.lights[i].params[0]=(float)lights[i]->type;
			frame.lights[i].params[1]=lights[i]->range;
			frame.lights[i].params[2]=cosf( lights[i]->outer_angle*dtor*0.5f );
			frame.lights[i].params[3]=cosf( lights[i]->inner_angle*0.5f );
			frame.lights_used++;
		}
		frame_dirty=true;

		return true;
	}

	void clear( const float rgb[3],float alpha,float z,bool clear_argb,bool clear_z ){
		if( !clear_argb&&!clear_z ) return;
		if( !target ) return;
		flushPendingDraw();

		int cw=target->getWidth(),ch=target->getHeight();
		bool full=( viewport[2]>0&&viewport[3]>0&&
		            viewport[0]<=0&&viewport[1]<=0&&
		            viewport[0]+viewport[2]>=cw&&viewport[1]+viewport[3]>=ch );

		bool pass_open=( pass&&pass_canvas==target );

		if( full&&!pass_open ){
			if( clear_argb ){
				pending_clear_color=true;
				clear_color[0]=rgb[0];clear_color[1]=rgb[1];clear_color[2]=rgb[2];clear_color[3]=alpha;
			}
			if( clear_z ){
				pending_clear_z=true;
				clear_depth=z;
			}
			return;
		}

		BBWebGPUClearState cs={};
		cs.color[0]=rgb[0];cs.color[1]=rgb[1];cs.color[2]=rgb[2];cs.color[3]=alpha;
		cs.z=z;
		uint32_t offset=pushUniforms( &cs,sizeof(cs) );

		WGPURenderPassEncoder p=ensurePass();
		if( !p ) return;

		wgpuRenderPassEncoderSetPipeline( p,getClearPipeline( clear_argb,clear_z,target->format() ) );
		wgpuRenderPassEncoderSetBindGroup( p,0,clear_group,1,&offset );
		wgpuRenderPassEncoderDraw( p,3,1,0,0 );

		bound_pipeline=0;
		bound_frame=false;
	}

	void render( BBMesh *m,int first_vert,int vert_cnt,int first_tri,int tri_cnt ){
		WebGPUMesh *mesh=(WebGPUMesh*)m;
		if( !mesh||!mesh->vertex_buffer||!mesh->index_buffer||tri_cnt<=0 ) return;
		if( !target ) return;

		mesh->upload();
		WGPUBuffer index_buffer=mesh->index_buffer;
		bool index16=mesh->index16;
		int index_first=first_tri*3,index_count=tri_cnt*3;
		if( wireframe ){
			index_buffer=mesh->wireBuffer();
			if( !index_buffer ) return;
			index_first=first_tri*6;
			index_count=tri_cnt*6;
		}
		if( instance_used+sizeof(BBWebGPUInstance)>instance_capacity ) flushAll();

		for( int attempt=0;attempt<4;++attempt ){
			if( frame_dirty ){
				updateLightsEye();
				frame_offset=pushCached( frame_cache,&frame,sizeof(frame),sizeof(frame) );
				frame_dirty=false;
			}
			if( entity_dirty ){
				entity_offset=pushCached( entity_cache,&entity,BBWebGPUEntityState::usedSize( entity.texs_used ),
				                          sizeof(entity),offsetof(BBWebGPUEntityState,ambient) );
				entity_dirty=false;
			}
			if( !frame_dirty&&!entity_dirty ) break;
		}

		WGPURenderPassEncoder p=ensurePass();
		if( !p ) return;

		if( bound_mesh_generation!=g_mesh_generation ){
			flushPendingDraw();
			bound_vertex_buffer=bound_index_buffer=0;
			bound_mesh_generation=g_mesh_generation;
		}

		WGPURenderPipeline pipeline=getScenePipeline( target->format() );
		WGPUBindGroup tex_group=current_tex_group?current_tex_group:defaultTexGroup();
		uint32_t first_instance=(uint32_t)(instance_used/sizeof(BBWebGPUInstance));
		BBWebGPUInstance instance;
		memcpy( instance.world,entity.world,sizeof(instance.world) );
		memcpy( instance.normal,entity.normal_mat,sizeof(instance.normal) );
		memcpy( instance_stage.data()+instance_used,&instance,sizeof(instance) );
		instance_used+=sizeof(instance);

		bool batchable=blend_mode==BLEND_REPLACE&&zmode==ZMODE_NORMAL&&!wireframe;
		if( pending_instances&&(!batchable||pipeline!=bound_pipeline||
		    !bound_frame||bound_frame_offset!=frame_offset||!bound_entity||bound_entity_offset!=entity_offset||
		    tex_group!=bound_tex_group||mesh->vertex_buffer!=bound_vertex_buffer||index_buffer!=bound_index_buffer||
		    index_first!=pending_index_first||index_count!=pending_index_count||first_vert!=pending_base_vertex||
		    first_instance!=pending_first_instance+pending_instances) ) flushPendingDraw();
		if( pipeline!=bound_pipeline ){
			wgpuRenderPassEncoderSetPipeline( p,pipeline );
			bound_pipeline=pipeline;
		}
		if( !bound_frame||bound_frame_offset!=frame_offset ){
			wgpuRenderPassEncoderSetBindGroup( p,0,frame_group,1,&frame_offset );
			bound_frame=true;
			bound_frame_offset=frame_offset;
		}
		if( !bound_entity||bound_entity_offset!=entity_offset ){
			wgpuRenderPassEncoderSetBindGroup( p,1,entity_group,1,&entity_offset );
			bound_entity=true;
			bound_entity_offset=entity_offset;
		}
		if( tex_group!=bound_tex_group ){
			wgpuRenderPassEncoderSetBindGroup( p,2,tex_group,0,0 );
			bound_tex_group=tex_group;
		}
		if( mesh->vertex_buffer!=bound_vertex_buffer ){
			wgpuRenderPassEncoderSetVertexBuffer( p,0,mesh->vertex_buffer,0,WGPU_WHOLE_SIZE );
			bound_vertex_buffer=mesh->vertex_buffer;
		}
		if( index_buffer!=bound_index_buffer ){
			wgpuRenderPassEncoderSetIndexBuffer( p,index_buffer,index16?WGPUIndexFormat_Uint16:WGPUIndexFormat_Uint32,0,WGPU_WHOLE_SIZE );
			bound_index_buffer=index_buffer;
		}
		if( !bound_instances ){
			wgpuRenderPassEncoderSetVertexBuffer( p,1,instance_buffer,0,WGPU_WHOLE_SIZE );
			bound_instances=true;
		}
		if( batchable ){
			if( !pending_instances ){
				pending_first_instance=first_instance;
				pending_index_first=index_first;pending_index_count=index_count;pending_base_vertex=first_vert;
			}
			++pending_instances;
		}else{
			wgpuRenderPassEncoderDrawIndexed( p,(uint32_t)index_count,1,(uint32_t)index_first,first_vert,first_instance );
		}

		if( mesh->drawn_marker!=submit_marker ){
			mesh->drawn_marker=submit_marker;
			mesh->drawn_v0=first_vert;mesh->drawn_v1=first_vert+vert_cnt;
			mesh->drawn_t0=first_tri;mesh->drawn_t1=first_tri+tri_cnt;
		}else{
			mesh->drawn_v0=std::min(mesh->drawn_v0,first_vert);
			mesh->drawn_v1=std::max(mesh->drawn_v1,first_vert+vert_cnt);
			mesh->drawn_t0=std::min(mesh->drawn_t0,first_tri);
			mesh->drawn_t1=std::max(mesh->drawn_t1,first_tri+tri_cnt);
		}
		tris_drawn+=tri_cnt;
	}

	void end(){
		if( ( pending_clear_color||pending_clear_z )&&target ){
			ensurePass();
		}
		endPass();
	}


	BBLightRep *createLight( int flags ){
		return d_new WebGPULight();
	}

	void freeLight( BBLightRep *l ){
	}

	BBMesh *createMesh( int max_verts,int max_tris,int flags ){
		BBMesh *mesh=d_new WebGPUMesh( this,max_verts,max_tris,flags );
		mesh_set.insert( mesh );
		return mesh;
	}

	int getTrianglesDrawn()const{ return tris_drawn; }
};


WebGPUMesh::WebGPUMesh( WebGPUScene *scene,int mv,int mt,int f ):
scene(scene),max_verts(mv>0?mv:1),max_tris(mt>0?mt:1),flags(f){
	dirty_v0=max_verts;
	dirty_t0=max_tris;

	WGPUBufferDescriptor vd={};
	vd.label=bbStrView( "bb.3d.mesh.verts" );
	vd.usage=WGPUBufferUsage_Vertex|WGPUBufferUsage_CopyDst;
	vd.size=(uint64_t)max_verts*sizeof( BBWebGPU3DVertex );
	vertex_buffer=wgpuDeviceCreateBuffer( scene->res->device,&vd );

	WGPUBufferDescriptor id={};
	id.label=bbStrView( "bb.3d.mesh.indices" );
	id.usage=WGPUBufferUsage_Index|WGPUBufferUsage_CopyDst;
	index16=max_verts<=65536;
	id.size=index16?(((uint64_t)max_tris*3*sizeof( uint16_t )+3)&~(uint64_t)3):(uint64_t)max_tris*3*sizeof( unsigned int );
	index_buffer=wgpuDeviceCreateBuffer( scene->res->device,&id );
}

WebGPUMesh::~WebGPUMesh(){
	++g_mesh_generation;
	if( vertex_buffer ){ wgpuBufferRelease( vertex_buffer );vertex_buffer=0; }
	if( index_buffer ){ wgpuBufferRelease( index_buffer );index_buffer=0; }
	if( wire_buffer ){ wgpuBufferRelease( wire_buffer );wire_buffer=0; }
	delete[] verts;
	delete[] tris;
}

WGPUBuffer WebGPUMesh::wireBuffer(){
	if( !tris ) return 0;
	if( wire_buffer&&wire_version==tri_version ) return wire_buffer;

	if( wire_buffer&&drawn_marker==scene->submit_marker ){
		scene->flushAll();
	}

	size_t count=(size_t)max_tris*6;
	size_t stride=index16?sizeof( uint16_t ):sizeof( unsigned int );
	if( !wire_buffer ){
		WGPUBufferDescriptor bd={};
		bd.label=bbStrView( "bb.3d.mesh.wire" );
		bd.usage=WGPUBufferUsage_Index|WGPUBufferUsage_CopyDst;
		bd.size=count*stride;
		wire_buffer=wgpuDeviceCreateBuffer( scene->res->device,&bd );
	}

	std::vector<unsigned int> edges( count );
	for( int t=0;t<max_tris;t++ ){
		const unsigned int *tri=tris+t*3;
		edges[t*6+0]=tri[0];edges[t*6+1]=tri[1];
		edges[t*6+2]=tri[1];edges[t*6+3]=tri[2];
		edges[t*6+4]=tri[2];edges[t*6+5]=tri[0];
	}
	if( index16 ){
		std::vector<uint16_t> narrow( count );
		for( size_t i=0;i<count;i++ ) narrow[i]=(uint16_t)edges[i];
		wgpuQueueWriteBuffer( scene->res->queue,wire_buffer,0,narrow.data(),count*stride );
	}else{
		wgpuQueueWriteBuffer( scene->res->queue,wire_buffer,0,edges.data(),count*stride );
	}
	wire_version=tri_version;
	return wire_buffer;
}

void WebGPUMesh::upload(){
	if( !verts||!tris ) return;

	bool verts_dirty=dirty_v1>dirty_v0;
	bool tris_dirty=dirty_t1>dirty_t0;
	if( !verts_dirty&&!tris_dirty ) return;

	// Updating unused ranges cannot affect queued draws. Overwriting data they
	// reference must still submit first, so animation/reflection order is kept.
	bool vertex_overlap=verts_dirty&&dirty_v0<drawn_v1&&dirty_v1>drawn_v0;
	bool index_overlap=tris_dirty&&dirty_t0<drawn_t1&&dirty_t1>drawn_t0;
	if( drawn_marker==scene->submit_marker&&(vertex_overlap||index_overlap) ){
		scene->flushAll();
	}

	if( verts_dirty ){
		wgpuQueueWriteBuffer( scene->res->queue,vertex_buffer,(uint64_t)dirty_v0*sizeof( BBWebGPU3DVertex ),
		                      verts+dirty_v0,(size_t)( dirty_v1-dirty_v0 )*sizeof( BBWebGPU3DVertex ) );
	}

	if( tris_dirty ){
		size_t total=(size_t)max_tris*3;
		size_t first=(size_t)dirty_t0*3,last=(size_t)dirty_t1*3;
		if( index16 ){
			static std::vector<uint16_t> narrow;
			first&=~(size_t)1;
			last=( last+1 )&~(size_t)1;
			narrow.resize( last-first );
			for( size_t i=first;i<last;i++ ) narrow[i-first]=i<total?(uint16_t)tris[i]:0;
			wgpuQueueWriteBuffer( scene->res->queue,index_buffer,(uint64_t)first*sizeof( uint16_t ),
			                      narrow.data(),narrow.size()*sizeof( uint16_t ) );
		}else{
			wgpuQueueWriteBuffer( scene->res->queue,index_buffer,(uint64_t)first*sizeof( unsigned int ),
			                      tris+first,( last-first )*sizeof( unsigned int ) );
		}
	}

	dirty_v0=max_verts;dirty_v1=0;
	dirty_t0=max_tris;dirty_t1=0;
}


BBScene *WebGPUB3DGraphics::createScene( int w,int h,float d,int flags ){
	if( scene_set.size() ) return 0;

	BBGraphics *g=bbContextDriver?bbContextDriver->getGraphics():0;
	WebGPUGraphics *wg=dynamic_cast<WebGPUGraphics*>( g );
	if( !wg ){
		LOGD( "%s","[blitz3d.webgpu] active graphics is not WebGPU - no 3D scene" );
		return 0;
	}
	if( !wg->res.device ){
		LOGD( "%s","[blitz3d.webgpu] WebGPU device not initialized" );
		return 0;
	}

	WebGPUScene *scene=d_new WebGPUScene( &wg->res );
	scene_set.insert( scene );
	return scene;
}

static WebGPUB3DGraphics webgpu_scene_driver;

BBMODULE_CREATE( blitz3d_webgpu ){
	bbSceneDriver=&webgpu_scene_driver;

	return true;
}

BBMODULE_DESTROY( blitz3d_webgpu ){
	if( bbSceneDriver==&webgpu_scene_driver ){
        extern void blitz3d_close();
        blitz3d_close();
        bbSceneDriver=0;
    }
	return true;
}
