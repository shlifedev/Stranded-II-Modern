#ifndef BB_BLITZ3D_WEBGPU_H
#define BB_BLITZ3D_WEBGPU_H


#include <bb/blitz3d/blitz3d.h>
#include <bb/blitz3d/graphics.h>
#include <bb/graphics.webgpu/graphics.webgpu.h>

#include <cstddef>
#include <cstdint>


struct BBWebGPUFrameState{
	float proj[16];
	float view[16];
	struct LightData{
		float dir[4];
		float pos[4];
		float color[4];
		float params[4];
	}lights[8];
	int32_t lights_used;
	float lod_bias;
	int32_t _pad[2];
};
static_assert( sizeof(BBWebGPUFrameState)==656,"BBFrameState layout mismatch" );

struct BBWebGPUEntityState{
	float world[16];
	float normal_mat[16];
	float ambient[4];
	float brush_color[4];
	float fog_color[4];
	float fog_range[2];
	int32_t texs_used;
	int32_t use_vertex_color;
	float brush_shininess;
	int32_t fullbright;
	int32_t fog_mode;
	int32_t alpha_test;
	float alpha_ref;
	float _pad[3];
	struct TexState{
		float mat[16];
		int32_t blend,sphere_map,flags,alpha;
	}texs[8];

	static size_t usedSize( int used ){
		return offsetof( BBWebGPUEntityState,texs )+sizeof( TexState )*(size_t)used;
	}
};
static_assert( sizeof(BBWebGPUEntityState)==864,"BBEntityState layout mismatch" );
static_assert( offsetof(BBWebGPUEntityState,texs)==224,"BBEntityState texs offset mismatch" );

struct BBWebGPUClearState{
	float color[4];
	float z;
	float _pad[3];
};
static_assert( sizeof(BBWebGPUClearState)==32,"BBClearState layout mismatch" );

struct BBWebGPU3DVertex{
	float coords[3];
	float normal[3];
	uint8_t color[4];
	float tex_coord0[2];
	float tex_coord1[2];
};
static_assert( sizeof(BBWebGPU3DVertex)==44,"3D vertex layout mismatch" );


class WebGPUB3DGraphics : public B3DGraphics{
public:
	BBScene *createScene( int w,int h,float d,int flags );
};

#endif
