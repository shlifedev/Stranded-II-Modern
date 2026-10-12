
#include "../stdutil/stdutil.h"
#include <bb/graphics.gl/graphics.gl.h>
#include <bb/system/system.h>
#include "blitz3d.gl.h"
#include "default.glsl.h"

#include <cmath>
#include <cstdio>
#include <cstring>
#include <vector>

static const float dtor=0.0174532925199432957692369076848861f;
static const float rtod=1/dtor;

class GLLight : public BBLightRep{
public:
	int type;

	float r,g,b;
	float matrix[16];

	float range;
	float inner_angle,outer_angle;

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

struct GLVertex{
	float coords[3];
	float normal[3];
	float color[4];
	float tex_coord0[2];
	float tex_coord1[2];
};

class GLMesh : public BBMesh{
protected:
	bool dirty()const{ return false; }

public:
	int max_verts,max_tris,flags;
	GLVertex *verts=0;
	unsigned int *tris=0;

	unsigned int vertex_array=0;
	unsigned int vertex_buffer=0,index_buffer=0;

	bool vbuf_alloc=false;
	int dirty_lo,dirty_hi=-1;
	unsigned int geom_gen=1;
	unsigned int up_gen=0;
	int up_ft=-1,up_tc=-1,up_bv=-1;

	GLMesh( int mv,int mt,int f ):max_verts(mv),max_tris(mt),flags(f),dirty_lo(mv){
		GL( glGenVertexArrays( 1,&vertex_array ) );
		GL( glGenBuffers( 1,&vertex_buffer ) );
		GL( glGenBuffers( 1,&index_buffer ) );

		GL( glBindVertexArray( vertex_array ) );

		GL( glBindBuffer( GL_ARRAY_BUFFER,vertex_buffer ) );
		GL( glBindBuffer( GL_ELEMENT_ARRAY_BUFFER,index_buffer ) );

		GL( glEnableVertexAttribArray( 0 ) );
		GL( glEnableVertexAttribArray( 1 ) );
		GL( glEnableVertexAttribArray( 2 ) );
		GL( glEnableVertexAttribArray( 3 ) );
		GL( glEnableVertexAttribArray( 4 ) );

		GL( glVertexAttribPointer( 0,3,GL_FLOAT,GL_FALSE,sizeof( GLVertex ),(void*)offsetof( GLVertex,coords ) ) );
		GL( glVertexAttribPointer( 1,3,GL_FLOAT,GL_FALSE,sizeof( GLVertex ),(void*)offsetof( GLVertex,normal ) ) );
		GL( glVertexAttribPointer( 2,4,GL_FLOAT,GL_FALSE,sizeof( GLVertex ),(void*)offsetof( GLVertex,color ) ) );
		GL( glVertexAttribPointer( 3,2,GL_FLOAT,GL_FALSE,sizeof( GLVertex ),(void*)offsetof( GLVertex,tex_coord0 ) ) );
		GL( glVertexAttribPointer( 4,2,GL_FLOAT,GL_FALSE,sizeof( GLVertex ),(void*)offsetof( GLVertex,tex_coord1 ) ) );

		GL( glBindVertexArray( 0 ) );
	}

	~GLMesh(){
		delete[] verts;
		delete[] tris;
	}

	bool lock( bool all ){
		if( !verts ) verts=new GLVertex[max_verts];
		if( !tris ) tris=new unsigned int[max_tris*3];

		return true;
	}

	void unlock(){
		if( dirty_hi>=dirty_lo || !vbuf_alloc ){
			GL( glBindBuffer( GL_ARRAY_BUFFER,vertex_buffer ) );
			if( !vbuf_alloc ){
				GL( glBufferData( GL_ARRAY_BUFFER,(size_t)max_verts*sizeof(GLVertex),verts,GL_STATIC_DRAW ) );
				vbuf_alloc=true;
			}else{
				GL( glBufferSubData( GL_ARRAY_BUFFER,(size_t)dirty_lo*sizeof(GLVertex),(size_t)(dirty_hi-dirty_lo+1)*sizeof(GLVertex),verts+dirty_lo ) );
			}
			GL( glBindBuffer( GL_ARRAY_BUFFER,0 ) );
		}
		dirty_lo=max_verts;dirty_hi=-1;
		++geom_gen;
	}

	void uploadIndices( int first_tri,int tri_cnt,int base_vert ){
		if( up_gen==geom_gen && up_ft==first_tri && up_tc==tri_cnt && up_bv==base_vert ) return;
		GL( glBindBuffer( GL_ELEMENT_ARRAY_BUFFER,index_buffer ) );
		const unsigned int *src=tris+(size_t)first_tri*3;
		if( base_vert==0 ){
			GL( glBufferData( GL_ELEMENT_ARRAY_BUFFER,(size_t)tri_cnt*3*sizeof(unsigned int),src,GL_STATIC_DRAW ) );
		}else{
			static std::vector<unsigned int> scratch;
			if( scratch.size()<(size_t)tri_cnt*3 ) scratch.resize( (size_t)tri_cnt*3 );
			for( size_t i=0;i<(size_t)tri_cnt*3;++i ) scratch[i]=src[i]+(unsigned int)base_vert;
			GL( glBufferData( GL_ELEMENT_ARRAY_BUFFER,(size_t)tri_cnt*3*sizeof(unsigned int),scratch.data(),GL_STATIC_DRAW ) );
		}
		GL( glBindBuffer( GL_ELEMENT_ARRAY_BUFFER,0 ) );
		up_gen=geom_gen;up_ft=first_tri;up_tc=tri_cnt;up_bv=base_vert;
	}

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
		verts[n].coords[0]=coords[0];verts[n].coords[1]=coords[1];verts[n].coords[2]=coords[2];
		verts[n].normal[0]=normal[0];verts[n].normal[1]=normal[1];verts[n].normal[2]=normal[2];
		verts[n].tex_coord0[0]=tex_coords[0][0];verts[n].tex_coord0[1]=tex_coords[0][1];
		verts[n].tex_coord1[0]=tex_coords[1][0];verts[n].tex_coord1[1]=tex_coords[1][1];
		verts[n].color[0]=((argb>>16)&255)/255.0f;verts[n].color[1]=((argb>>8)&255)/255.0f;verts[n].color[2]=(argb&255)/255.0f;verts[n].color[3]=((argb>>24)&255)/255.0f;
		if( n<dirty_lo ) dirty_lo=n;
		if( n>dirty_hi ) dirty_hi=n;
	}

	void setTriangle( int n,int v0,int v1,int v2 ){
		tris[n*3+0]=v2;
		tris[n*3+1]=v1;
		tris[n*3+2]=v0;
	}
};

struct LightState{
	struct LightData{
		float mat[16];
		float color[4];
		float params[4];
	} data[8];

	int lights_used;
	int _pad[3];
};

struct UniformState{
	float ambient[4];
	float brush_color[4];
	float fog_color[4];

	struct GLTexState{
		float mat[16];
		int blend, sphere_map, flags, cube_map;
	} texs[8];

	float fog_range[2];

	int texs_used;
	int use_vertex_color;
	float brush_shininess;
	int fullbright;
	int fog_mode;
	int alpha_test;
	float alpha_ref;
	float _pad[3];
};

class GLScene : public BBScene{
private:
	int context_width,context_height;
	bool wireframe;
	UniformState us={ 0 };
	UniformState last_us={ 0 };
	bool us_valid=false;
	GLuint defaultProgram=0;
	int viewport[4];

	GLuint light_ubo=0,rs_ubo=0;
	GLint loc_proj=-1,loc_view=-1,loc_world=-1;

	GLuint bound_tex[MAX_TEXTURES]={ 0 };
	unsigned char bound_kind[MAX_TEXTURES]={ 0 };

	int tris_drawn=0;

	std::vector<GLLight*> lights;

	void setLights(){
		LightState ls={ 0 };

		for( unsigned long i=0;i<8;i++ ){
			if( i>=lights.size() ){
				break;
			}

			ls.lights_used++;

			memcpy( ls.data[i].mat,lights[i]->matrix,sizeof(ls.data[i].mat) );
			ls.data[i].color[0]=lights[i]->r;ls.data[i].color[1]=lights[i]->g;ls.data[i].color[2]=lights[i]->b;ls.data[i].color[3]=1.0;

			ls.data[i].params[0]=(float)lights[i]->type;
			ls.data[i].params[1]=lights[i]->range;
			ls.data[i].params[2]=cosf( lights[i]->outer_angle*dtor*0.5f );
			ls.data[i].params[3]=cosf( lights[i]->inner_angle*0.5f );
		}

		if( light_ubo==0 ){
			GL( glGenBuffers( 1,&light_ubo ) );
			GL( glBindBuffer( GL_UNIFORM_BUFFER,light_ubo ) );
			GL( glBufferData( GL_UNIFORM_BUFFER,sizeof(ls),&ls,GL_DYNAMIC_DRAW ) );
			GL( glBindBufferRange( GL_UNIFORM_BUFFER,1,light_ubo,0,sizeof(ls) ) );
		}else{
			GL( glBindBuffer( GL_UNIFORM_BUFFER,light_ubo ) );
			GL( glBufferData( GL_UNIFORM_BUFFER,sizeof(ls),&ls,GL_DYNAMIC_DRAW ) );
		}

		GL( glBindBuffer( GL_UNIFORM_BUFFER,0 ) );
	}

public:
	GLScene():wireframe(false){
		memset( &us,0,sizeof(UniformState) );

		const float MIDLEVEL[]={ 0.5f,0.5f,0.5f,1.0f };
		setAmbient( MIDLEVEL );
	}

	int  hwTexUnits(){ return MAX_TEXTURES; }
	int  gfxDriverCaps3D(){ return 110; }

	void setWBuffer( bool enable ){}
	void setHWMultiTex( bool enable ){}
	void setDither( bool enable ){}

	void setAntialias( bool enable ){}
	void setWireframe( bool enable ){
		wireframe=enable;
	}
	void setFlippedTris( bool enable ){
		GL( glFrontFace( enable ? GL_CW : GL_CCW ) );
	}
	void setAmbient( const float rgb[3] ){
		us.ambient[0]=rgb[0];us.ambient[1]=rgb[1];us.ambient[2]=rgb[2];us.ambient[3]=1.0f;
	}

	void setAmbient2( const float rgb[3] ){
		setAmbient( rgb );
	}

	void setFogColor( const float rgb[3] ){
		us.fog_color[0]=rgb[0];us.fog_color[1]=rgb[1];us.fog_color[2]=rgb[2];us.fog_color[3]=1.0;
	}

	void setFogRange( float nr,float fr ){
		us.fog_range[0]=nr;us.fog_range[1]=fr;
	}

	void setFogMode( int mode ){
		us.fog_mode=mode;
	}

	void setZMode( int mode ){
		switch( mode ){
		case ZMODE_NORMAL:
			GL( glEnable( GL_DEPTH_TEST ) );
			GL( glDepthMask( GL_TRUE ) );
			break;
		case ZMODE_DISABLE:
			GL( glDisable( GL_DEPTH_TEST ) );
			GL( glDepthMask( GL_TRUE ) );
			break;
		case ZMODE_CMPONLY:
			GL( glEnable( GL_DEPTH_TEST ) );
			GL( glDepthMask( GL_FALSE ) );
			break;
		}
	}

	void setCanvas( int w,int h ){
		context_width=w;
		context_height=h;
	}

	void setViewport( int x,int y,int w,int h ){
		y=context_height-(h+y);

		GL( glViewport( x,y,w,h ) );
		GL( glScissor( x,y,w,h ) );
	}

	void setProj( const float matrix[16] ){
		GL( glUniformMatrix4fv( loc_proj,1,GL_FALSE,matrix ) );
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
		mat[10]=Q;
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
		mat[10] = -(fr+nr) / (fr-nr);
		mat[11] = -1.0f;
		mat[14] = -(2.0f*fr*nr) / (fr-nr);
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

		GL( glUniformMatrix4fv( loc_view,1,GL_FALSE,mat ) );
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

		GL( glUniformMatrix4fv( loc_world,1,GL_FALSE,mat ) );
	}

	void setRenderState( const RenderState &rs ){
		if( rs.fx&FX_ALPHATEST && !(rs.fx&FX_VERTEXALPHA) ){
			us.alpha_test = 1;
			us.alpha_ref = 128.0f*rs.alpha;
		} else {
			us.alpha_test = 0;
			us.alpha_ref = 0.0f;
		}

		int fog_mode=us.fog_mode;
		if( rs.fx&FX_NOFOG ){
			us.fog_mode = 0;
		}

		if( rs.fx&FX_DOUBLESIDED ){
			GL( glDisable( GL_CULL_FACE ) );
		}else{
			GL( glEnable( GL_CULL_FACE ) );
		}

		int blend=rs.blend;

#ifndef BB_EMSCRIPTEN
		if( rs.fx&FX_WIREFRAME||wireframe ){
			GL( glPolygonMode(GL_FRONT_AND_BACK,GL_LINE) );
		}else{
			GL( glPolygonMode(GL_FRONT_AND_BACK,GL_FILL) );
		}
#endif

		us.brush_color[0]=rs.color[0];us.brush_color[1]=rs.color[1];us.brush_color[2]=rs.color[2];us.brush_color[3]=rs.alpha;
		us.brush_shininess=rs.shininess;

		us.fullbright=rs.fx&FX_FULLBRIGHT;
		us.use_vertex_color=rs.fx&FX_VERTEXCOLOR;

		GLCanvas *canv[MAX_TEXTURES];
		int cflags[MAX_TEXTURES];
		const RenderState::TexState *tsp[MAX_TEXTURES];
		GLuint ids[MAX_TEXTURES];
		bool any_upload=false;
		int cnt=0;

		for( int i=0;i<MAX_TEXTURES;i++ ){
			const RenderState::TexState &ts=rs.tex_states[i];

			if( !ts.canvas || !ts.blend ) continue;

			GLCanvas *canvas=(GLCanvas*)ts.canvas;
			int flags=canvas->getFlags();

			if( (ts.blend==BLEND_MULTIPLY || ts.blend==BLEND_MULTIPLY2) &&
				(flags&BBCanvas::CANVAS_TEX_VIDMEM) && (flags&BBCanvas::CANVAS_TEXTURE) &&
				canvas->getWidth()<=2 && canvas->getHeight()<=2 ){
				continue;
			}

			if( canvas->isDirty() ) any_upload=true;
			ids[cnt]=canvas->textureId();
			canv[cnt]=canvas;
			cflags[cnt]=flags;
			tsp[cnt]=&ts;
			cnt++;
		}

		if( any_upload ){
			bound_kind[0]=0;
			bound_tex[0]=0;
		}

		us.texs_used=0;
		for( int k=0;k<cnt;k++ ){
			GLCanvas *canvas=canv[k];
			int flags=cflags[k];
			const RenderState::TexState &ts=*tsp[k];
			bool cube=(flags&BBCanvas::CANVAS_TEX_CUBE)!=0;
			unsigned char kind=cube?2:1;
			GLenum unit=cube?(GL_TEXTURE0+MAX_TEXTURES+k):(GL_TEXTURE0+k);

			bool needbind=bound_kind[k]!=kind || bound_tex[k]!=ids[k];

			int want=((flags&BBCanvas::CANVAS_TEX_NOFILTERING)?1:0)
				|((flags&BBCanvas::CANVAS_TEX_MIPMAP)?2:0)
				|((flags&BBCanvas::CANVAS_TEX_CLAMPU)?4:0)
				|((flags&BBCanvas::CANVAS_TEX_CLAMPV)?8:0);
			bool needparams=canvas->applied_params!=want;

			if( needbind || needparams ){
				if( needbind && bound_kind[k]==2 && !cube ){
					GL( glActiveTexture( GL_TEXTURE0+MAX_TEXTURES+k ) );
					GL( glBindTexture( GL_TEXTURE_CUBE_MAP,0 ) );
				}else if( needbind && bound_kind[k]==1 && cube ){
					GL( glActiveTexture( GL_TEXTURE0+k ) );
					GL( glBindTexture( GL_TEXTURE_2D,0 ) );
				}
				GL( glActiveTexture( unit ) );
				if( needbind ){
					GL( glBindTexture( canvas->target,ids[k] ) );
					bound_kind[k]=kind;
					bound_tex[k]=ids[k];
				}
				if( needparams ) canvas->applyTexParams( want );
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

			memcpy( us.texs[us.texs_used].mat,mat,sizeof(mat) );

			if( flags&BBCanvas::CANVAS_TEX_ALPHA ){
				us.alpha_test=1;
			}

			us.texs[us.texs_used].blend=ts.blend;
			us.texs[us.texs_used].sphere_map=flags&BBCanvas::CANVAS_TEX_SPHERE?1:0;
			us.texs[us.texs_used].cube_map=cube?1:0;
			us.texs[us.texs_used].flags=ts.flags;

			us.texs_used++;
		}

		for( int k=cnt;k<MAX_TEXTURES;k++ ){
			if( !bound_kind[k] ) continue;
			if( bound_kind[k]==1 ){
				GL( glActiveTexture( GL_TEXTURE0+k ) );
				GL( glBindTexture( GL_TEXTURE_2D,0 ) );
			}else{
				GL( glActiveTexture( GL_TEXTURE0+MAX_TEXTURES+k ) );
				GL( glBindTexture( GL_TEXTURE_CUBE_MAP,0 ) );
			}
			bound_kind[k]=0;
			bound_tex[k]=0;
		}

		switch( blend ){
		default:case BLEND_REPLACE:
			GL( glDisable( GL_BLEND ) );
			break;
		case BLEND_ALPHA:
			GL( glEnable( GL_BLEND ) );
			GL( glBlendFunc( GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA ) );
			break;
		case BLEND_MULTIPLY:
			GL( glEnable( GL_BLEND ) );
			GL( glBlendFunc( GL_DST_COLOR,GL_ZERO ) );
			break;
		case BLEND_ADD:
			GL( glEnable( GL_BLEND ) );
			GL( glBlendFunc( GL_SRC_ALPHA,GL_ONE ) );
			break;
		}

		if( !us_valid || memcmp( &us,&last_us,sizeof(us) )!=0 ){
			if( rs_ubo==0 ){
				GL( glGenBuffers( 1,&rs_ubo ) );
				GL( glBindBuffer( GL_UNIFORM_BUFFER,rs_ubo ) );
				GL( glBufferData( GL_UNIFORM_BUFFER,sizeof(us),&us,GL_DYNAMIC_DRAW ) );
				GL( glBindBufferRange( GL_UNIFORM_BUFFER,2,rs_ubo,0,sizeof(us) ) );
			}else{
				GL( glBindBuffer( GL_UNIFORM_BUFFER,rs_ubo ) );
				GL( glBufferData( GL_UNIFORM_BUFFER,sizeof(us),&us,GL_DYNAMIC_DRAW ) );
			}
			GL( glBindBuffer( GL_UNIFORM_BUFFER,0 ) );
			memcpy( &last_us,&us,sizeof(us) );
			us_valid=true;
		}

		us.fog_mode=fog_mode;
	}

	bool begin( const std::vector<BBLightRep*> &l ){
		if( !glIsProgram( defaultProgram ) ){
			GL( glUseProgram( 0 ) );

			std::string src( DEFAULT_GLSL,DEFAULT_GLSL+DEFAULT_GLSL_SIZE );
			defaultProgram=_bbGLCompileProgram( "default.glsl",src );
			if( !defaultProgram ){
				RTEX( "Failed to compile shader" );
			}

			GL( glUseProgram( defaultProgram ) );

			for( int i=0;i<MAX_TEXTURES;i++ ){
				char sampler_name[20];
				snprintf( sampler_name,sizeof(sampler_name),"bbTexture[%i]",i );
				GLint texLocation=GL( glGetUniformLocation( defaultProgram,sampler_name ) );
				GL( glUniform1i( texLocation,i ) );
			}

			for( int i=0;i<MAX_TEXTURES;i++ ){
				char sampler_name[25];
				snprintf( sampler_name,sizeof(sampler_name),"bbTextureCube[%i]",i );
				GLint texLocation=GL( glGetUniformLocation( defaultProgram,sampler_name ) );
				GL( glUniform1i( texLocation,MAX_TEXTURES+i ) );
			}

			GLint lightIdx=GL( glGetUniformBlockIndex( defaultProgram,"BBLightState" ) );
			GL( glUniformBlockBinding( defaultProgram,lightIdx,1 ) );

			GLint renderIdx=GL( glGetUniformBlockIndex( defaultProgram,"BBRenderState" ) );
			GL( glUniformBlockBinding( defaultProgram,renderIdx,2 ) );

			loc_proj=GL( glGetUniformLocation( defaultProgram,"bbProjMatrix" ) );
			loc_view=GL( glGetUniformLocation( defaultProgram,"bbViewMatrix" ) );
			loc_world=GL( glGetUniformLocation( defaultProgram,"bbWorldMatrix" ) );

			if( light_ubo ){
				GL( glBindBufferRange( GL_UNIFORM_BUFFER,1,light_ubo,0,sizeof(LightState) ) );
			}
			if( rs_ubo ){
				GL( glBindBufferRange( GL_UNIFORM_BUFFER,2,rs_ubo,0,sizeof(UniformState) ) );
			}
		}

		GL( glUseProgram( defaultProgram ) );

		GL( glEnable( GL_SCISSOR_TEST ) );
		GL( glEnable( GL_CULL_FACE ) );

		GL( glDepthFunc( GL_LEQUAL ) );

		memset( bound_kind,0,sizeof(bound_kind) );
		memset( bound_tex,0,sizeof(bound_tex) );
		us_valid=false;

		lights.clear();
		for( unsigned long i=0;i<l.size();i++ ) lights.push_back( dynamic_cast<GLLight*>(l[i]) );

		setLights();

		GL( glGetIntegerv( GL_VIEWPORT,viewport ) );

		return true;
	}

	void clear( const float rgb[3],float alpha,float z,bool clear_argb,bool clear_z ){
		if( !clear_argb && !clear_z ) return;

		GL( glClearColor( rgb[0],rgb[1],rgb[2],alpha ) );
		GL( glClearDepthf( z ) );
		GL( glClear( (clear_argb?GL_COLOR_BUFFER_BIT:0)|(clear_z?GL_DEPTH_BUFFER_BIT:0)  ) );
	}

	void render( BBMesh *m,int first_vert,int vert_cnt,int first_tri,int tri_cnt ){
		GLMesh *mesh=(GLMesh*)m;

#if defined(BB_EMSCRIPTEN)
		mesh->uploadIndices( first_tri,tri_cnt,first_vert );

		GL( glBindVertexArray( mesh->vertex_array ) );
		GL( glDrawElements( GL_TRIANGLES,tri_cnt*3,GL_UNSIGNED_INT,0 ) );
		GL( glBindVertexArray( 0 ) );
#else
		mesh->uploadIndices( first_tri,tri_cnt,0 );

		GL( glBindVertexArray( mesh->vertex_array ) );
		GL( glDrawElementsBaseVertex( GL_TRIANGLES,tri_cnt*3,GL_UNSIGNED_INT,0,first_vert ) );
		GL( glBindVertexArray( 0 ) );
#endif
		tris_drawn+=tri_cnt;
	}

	void end(){
		GL( glUseProgram( 0 ) );
		GL( glActiveTexture( GL_TEXTURE0 ) );
		GL( glDisable( GL_DEPTH_TEST ) );

		GL( glDisable( GL_BLEND ) );

		GL( glViewport( viewport[0],viewport[1],viewport[2],viewport[3] ) );
		GL( glScissor( viewport[0],viewport[1],viewport[2],viewport[3] ) );

		GL( glDisable( GL_SCISSOR_TEST ) );
	}

	BBLightRep *createLight( int flags ){
		return d_new GLLight();
	}
	void freeLight( BBLightRep *l ){
		delete static_cast<GLLight*>( l );
	}

	BBMesh *createMesh( int max_verts,int max_tris,int flags ){
		BBMesh *mesh=d_new GLMesh( max_verts,max_tris,flags );
		mesh_set.insert( mesh );
		return mesh;
	}

	int getTrianglesDrawn()const{ return tris_drawn; }
};

BBScene *GLB3DGraphics::createScene( int w,int h,float d,int flags ){
	if( scene_set.size() ) return 0;

	GLScene *scene=d_new GLScene();
	scene_set.insert( scene );
	return scene;
}

BBMODULE_CREATE( blitz3d_gl ){
	return true;
}

BBMODULE_DESTROY( blitz3d_gl ){
	return true;
}
