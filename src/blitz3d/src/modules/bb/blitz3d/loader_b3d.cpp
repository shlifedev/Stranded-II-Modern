
#include "std.h"
#include "loader_b3d.h"
#include "meshmodel.h"
#include "pivot.h"
#include "meshutil.h"
#include <cerrno>
#include <cstring>
#ifdef BB_EMSCRIPTEN
#include <emscripten.h>
#endif

static FILE *in;
static std::vector<int> chunk_stack;
static std::vector<Texture> textures;
static std::vector<Brush> brushes;
static std::vector<Object*> bones;

static bool collapse;
static bool animonly;

static int swap_endian( int n ){
	return ((n&0xff)<<24)|((n&0xff00)<<8)|((n&0xff0000)>>8)|((n&0xff000000)>>24);
}

static void clear(){
	bones.clear();
	brushes.clear();
	textures.clear();
	chunk_stack.clear();
}

static int readChunk(){
	int header[2];
	if( fread( header,8,1,in )<1 ){
		chunk_stack.push_back( (int)ftell( in ) );
		return 0;
	}
	int sz=header[1]<0 ? 0 : header[1];
	chunk_stack.push_back( ftell( in )+sz );
	return swap_endian( header[0] );
}

static void exitChunk(){
	fseek( in,chunk_stack.back(),SEEK_SET );
	chunk_stack.pop_back();
}

static int chunkSize(){
	if( feof( in ) ) return 0;
	int n=chunk_stack.back()-ftell( in );
	return n>0 ? n : 0;
}

static void read( void *buf,int n ){
	if( n<=0 ) return;
	if( fread( buf,n,1,in )<1 ) memset( buf,0,n );
}

static void skip( int n ){
	fseek( in,n,SEEK_CUR );
}

static int readInt(){
	int n;
	read( &n,4 );
	return n;
}

static void readIntArray( int t[],int n ){
	read( t,n*4 );
}

static float readFloat(){
	float n;
	read( &n,4 );
	return n;
}

static void readFloatArray( float t[],int n ){
	read( t,n*4 );
}

static void readColor( unsigned *t ){
	float r=readFloat();if(r<0) r=0;else if(r>1) r=1;
	float g=readFloat();if(g<0) g=0;else if(g>1) g=1;
	float b=readFloat();if(b<0) b=0;else if(b>1) b=1;
	float a=readFloat();if(a<0) a=0;else if(a>1) a=1;
	*t=(int(a*255)<<24)|(int(r*255)<<16)|(int(g*255)<<8)|int(b*255);
}

static std::string readString(){
	std::string t;
	for(;;){
		char c=0;
		read( &c,1 );
		if( !c ) return t;
		t+=c;
	}
}

static void readTextures(){
	while( chunkSize() ){
		std::string name=readString();
		int flags=readInt();
		int blend=readInt();
		float pos[2],scl[2];
		readFloatArray( pos,2 );
		readFloatArray( scl,2 );
		float rot=readFloat();

		Texture tex( name,flags & 0xffff );

		tex.setBlend( blend );
		if( flags & 0x10000 ) tex.setFlags( BBScene::TEX_COORDS2 );

		if( pos[0]!=0 || pos[1]!=0 ) tex.setPosition( pos[0],pos[1] );
		if( scl[0]!=1 || scl[1]!=1 ) tex.setScale( scl[0],scl[1] );
		if( rot!=0 ) tex.setRotation( rot );

		textures.push_back( tex );
	}
}

static void readBrushes(){
	int n_texs=readInt();
	if( n_texs<0 ) n_texs=0;
	int n_read=n_texs<8 ? n_texs : 8;

	int tex_id[8]={-1,-1,-1,-1,-1,-1,-1,-1};

	while( chunkSize() ){
		std::string name=readString();
		float col[4];
		readFloatArray( col,4 );
		float shi=readFloat();
		int blend=readInt();
		int fx=readInt();
		readIntArray( tex_id,n_read );
		if( n_texs>n_read ) skip( (n_texs-n_read)*4 );

		Brush bru;

		bru.setColor( Vector( col[0],col[1],col[2] ) );
		bru.setAlpha( col[3] );
		bru.setShininess( shi );
		bru.setBlend( blend );
		bru.setFX( fx );

		for( int k=0;k<8;++k ){
			if( tex_id[k]<0 || tex_id[k]>=(int)textures.size() ) continue;
			bru.setTexture( k,textures[tex_id[k]],0 );
		}

		brushes.push_back( bru );
	}
}

static int readVertices(){

	int flags=readInt();
	int tc_sets=readInt();
	int tc_size=readInt();
	if( tc_sets<0 ) tc_sets=0;
	if( tc_size<0 ) tc_size=0;
	int tc_read=tc_size<4 ? tc_size : 4;

	float tc[4]={0};

	Surface::Vertex t;
	while( chunkSize() ){
		readFloatArray( t.coords,3 );
		if( flags&1 ){
			readFloatArray( t.normal,3 );
		}
		if( flags&2 ){
			readColor( &t.color );
		}
		for( int k=0;k<tc_sets;++k ){
			readFloatArray( tc,tc_read );
			if( tc_size>tc_read ) skip( (tc_size-tc_read)*4 );
			if( k<2 ) memcpy( t.tex_coords[k],tc,8 );
		}
		MeshLoader::addVertex( t );
	}

	return flags;
}

static void readTriangles(){
	int brush_id=readInt();
	Brush b=( brush_id>=0 && brush_id<(int)brushes.size() ) ? brushes[brush_id] : Brush();
	int n_verts=MeshLoader::numVertices();
	while( chunkSize() ){
		int verts[3];
		readIntArray( verts,3 );
		if( verts[0]<0||verts[1]<0||verts[2]<0||
			verts[0]>=n_verts||verts[1]>=n_verts||verts[2]>=n_verts ) continue;
		MeshLoader::addTriangle( verts,b );
	}
}

static int readMesh(){
	int flags=0;
	while( chunkSize() ){
		switch( readChunk() ){
		case 'VRTS':
			flags=readVertices();
			break;
		case 'TRIS':
			readTriangles();
			break;
		}
		exitChunk();
	}
	return flags;
}

static Object *readBone(){

	Pivot *bone=d_new Pivot();

	bones.push_back( bone );

	int n_verts=MeshLoader::numVertices();
	while( chunkSize() ){
		int vert=readInt();
		float weight=readFloat();
		if( vert<0 || vert>=n_verts ) continue;
		MeshLoader::addBone( vert,weight,bones.size() );
	}
	return bone;
}

static void readKeys( Animation &anim ){
	int flags=readInt();
	while( chunkSize() ){
		int frame=readInt();
		if( flags&1 ){
			float pos[3];
			readFloatArray( pos,3 );
			anim.setPositionKey( frame,Vector(pos[0],pos[1],pos[2]) );
		}
		if( flags&2 ){
			float scl[3];
			readFloatArray( scl,3 );
			anim.setScaleKey( frame,Vector(scl[0],scl[1],scl[2]) );
		}
		if( flags&4 ){
			float rot[4];
			readFloatArray( rot,4 );
			anim.setRotationKey( frame,Quat(rot[0],Vector(rot[1],rot[2],rot[3])) );
		}
	}
}

static Object *readObject( Object *parent ){

	Object *obj=0;

	std::string name=readString();
	float pos[3],scl[3],rot[4];
	readFloatArray( pos,3 );
	readFloatArray( scl,3 );
	readFloatArray( rot,4 );

	Animation keys;
	int anim_len=0;
	MeshModel *mesh=0;
	int mesh_flags,mesh_brush;

	while( chunkSize() ){
		switch( readChunk() ){
		case 'MESH':
			MeshLoader::beginMesh();
			obj=mesh=d_new MeshModel();
			mesh_brush=readInt();
			mesh_flags=readMesh();
			break;
		case 'BONE':
			obj=readBone();
			break;
		case 'KEYS':
			readKeys( keys );
			break;
		case 'ANIM':
			readInt();
			anim_len=readInt();
			readFloat();
			break;
		case 'NODE':
			if( !obj ) obj=d_new MeshModel();
			readObject( obj );
			break;
		}
		exitChunk();
	}

	if( !obj ) obj=d_new MeshModel();

	obj->setName( name );
	obj->setLocalPosition( Vector( pos[0],pos[1],pos[2] ) );
	obj->setLocalScale( Vector( scl[0],scl[1],scl[2] ) );
	obj->setLocalRotation( Quat( rot[0],Vector( rot[1],rot[2],rot[3] ) ) );
	obj->setAnimation( keys );

	if( mesh ){
		MeshLoader::endMesh( mesh );
		if( !(mesh_flags&1) ) mesh->updateNormals();
		if( mesh_brush>=0 && mesh_brush<(int)brushes.size() ) mesh->setBrush( brushes[mesh_brush] );
	}

	if( mesh && bones.size() ){
		bones.insert( bones.begin(),mesh );
		mesh->setAnimator( d_new Animator( bones,anim_len ) );
		mesh->createBones();
		bones.clear();
	}else if( anim_len ){
		obj->setAnimator( d_new Animator( obj,anim_len ) );
	}

	if( parent ) obj->setParent( parent );

	return obj;
}

MeshModel *Loader_B3D::load( const std::string &f,const Transform &conv,int hint ){

	collapse=!!(hint&MeshLoader::HINT_COLLAPSE);
	animonly=!!(hint&MeshLoader::HINT_ANIMONLY);

	in=fopen( f.c_str(),"rb" );
	if( !in ){
		fprintf( stderr,"[b3d] fopen failed: %s errno=%d %s\n",f.c_str(),errno,strerror( errno ) );
#ifdef BB_EMSCRIPTEN
		EM_ASM({
			try{ console.log( "[fd] streams="+FS.streams.filter( function(s){ return s; } ).length+" cwd="+FS.cwd() ); }catch(e){}
			try{ console.log( "[fd] exists="+FS.analyzePath( UTF8ToString( $0 ) ).exists ); }catch(e){}
			try{
				var d=FS.readdir( "/GFX/NPCs" );
				console.log( "[fd] dir066="+d.filter( function(n){ return n.indexOf( "066" )>=0; } ).join( "," ) );
			}catch(e){ console.log( "[fd] readdir fail: "+e ); }
		},f.c_str());
#endif
		return 0;
	}

	::clear();

	int tag=readChunk();
	if( tag!='BB3D' ){
		fprintf( stderr,"[b3d] bad tag %08x: %s\n",tag,f.c_str() );
		fclose( in );
		return 0;
	}

	int version=readInt();
	if( version>1 ){
		fprintf( stderr,"[b3d] bad version %d: %s\n",version,f.c_str() );
		fclose( in );
		return 0;
	}

	Object *obj=0;
	while( chunkSize() ){
		switch( readChunk() ){
		case 'TEXS':
			readTextures();
			break;
		case 'BRUS':
			readBrushes();
			break;
		case 'NODE':
			obj=readObject( 0 );
			break;
		}
		exitChunk();
	}
	fclose( in );

	::clear();

	if( !obj ){ fprintf( stderr,"[b3d] no NODE object: %s\n",f.c_str() );return 0; }
	if( Model *m=obj->getModel() ){
		if( MeshModel *mm=m->getMeshModel() ) return mm;
	}
	fprintf( stderr,"[b3d] no mesh model: %s\n",f.c_str() );
	delete obj;
	return 0;
}
