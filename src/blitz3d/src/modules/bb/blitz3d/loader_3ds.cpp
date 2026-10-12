
#include "std.h"
#include "loader_3ds.h"
#include "meshmodel.h"
#include "animation.h"
#include <bb/filesystem/filesystem.h>

#define _bbDebugLog( X )

static std::streambuf *in=0;
static int chunk_end;
static std::vector<int> parent_end;
static unsigned short anim_len;

static bool conv,flip_tris;
static Transform conv_tform;
static bool collapse,animonly;

struct Face3DS{
	int verts[3];
	Brush brush;
};

static std::vector<Face3DS> faces;

static std::map<std::string,Brush> materials_map;
static std::map<std::string,MeshModel*> name_map;
static std::map<int,MeshModel*> id_map;

static int nextChunk(){
	in->pubseekoff( chunk_end,std::ios_base::beg );
	if( chunk_end>=parent_end.back() ) return 0;
	unsigned short id=0;int len=0;
	if( in->sgetn( (char*)&id,2 )!=2 ) return 0;
	if( in->sgetn( (char*)&len,4 )!=4 ) return 0;
	if( len<6 ) return 0;
	chunk_end=(int)in->pubseekoff( 0,std::ios_base::cur )+len-6;
	return id;
}

static void enterChunk(){
	parent_end.push_back( chunk_end );
	chunk_end=(int)in->pubseekoff( 0,std::ios_base::cur );
}

static void leaveChunk(){
	chunk_end=parent_end.back();
	parent_end.pop_back();
}

static std::string parseString(){
	std::string t;
	int c;
	while( (c=in->sbumpc())>0 ) t+=char(c);
	return t;
}

enum {
	CHUNK_RGBF      	= 0x0010,
	CHUNK_RGBB      	= 0x0011,

	CHUNK_MAIN      	= 0x4D4D,
     CHUNK_SCENE        = 0x3D3D,
      CHUNK_BKGCOLOR    = 0x1200,
	  CHUNK_AMBCOLOR  	= 0x2100,
	  CHUNK_OBJECT  	= 0x4000,
	   CHUNK_TRIMESH   	= 0x4100,
		CHUNK_VERTLIST  = 0x4110,
		CHUNK_FACELIST  = 0x4120,
		CHUNK_FACEMAT   = 0x4130,
		CHUNK_MAPLIST   = 0x4140,
		CHUNK_SMOOLIST  = 0x4150,
		CHUNK_TRMATRIX  = 0x4160,
	   CHUNK_LIGHT     	= 0x4600,
		CHUNK_SPOTLIGHT = 0x4610,
	   CHUNK_CAMERA    	= 0x4700,
	 CHUNK_MATERIAL  	= 0xAFFF,
	  CHUNK_MATNAME   	= 0xA000,
	  CHUNK_AMBIENT   	= 0xA010,
	  CHUNK_DIFFUSE   	= 0xA020,
	  CHUNK_SPECULAR  	= 0xA030,
	  CHUNK_TEXTURE   	= 0xA200,
	  CHUNK_BUMPMAP   	= 0xA230,
	  CHUNK_MAPFILE   	= 0xA300,
	 CHUNK_KEYFRAMER 	= 0xB000,
	  CHUNK_MESHINFO	= 0xB002,
	   CHUNK_HIERPOS	= 0xB030,
	   CHUNK_HIERINFO	= 0xB010,
	  CHUNK_FRAMES      = 0xB008
};

static Vector parseColor(){
	Vector v;
	unsigned char rgb[3];
	enterChunk();
	while( int id=nextChunk() ){
		switch( id ){
		case CHUNK_RGBF:
			in->sgetn( (char*)&v,12 );
			break;
		case CHUNK_RGBB:
			in->sgetn( (char*)rgb,3 );
			v=Vector( rgb[0]/255.0f,rgb[1]/255.0f,rgb[2]/255.0f );
		}
	}
	leaveChunk();
	return v;
}

static void parseVertList(){
	unsigned short cnt;
	in->sgetn( (char*)&cnt,2 );
	_bbDebugLog( "VertList cnt="+itoa(cnt) );
	while( cnt-- ){
		Surface::Vertex v;
		in->sgetn( (char*)&v.coords,12 );
		if( conv ) v.coords=conv_tform * v.coords;
		MeshLoader::addVertex( v );
	}
}

static void parseFaceMat(){
	std::string name=parseString();
	_bbDebugLog( "FaceMat: "+name );
	Brush mat=materials_map[name];
	unsigned short cnt;
	in->sgetn( (char*)&cnt,2 );
	while( cnt-- ){
		unsigned short face;
		in->sgetn( (char*)&face,2 );
		if( face<faces.size() ) faces[face].brush=mat;
	}
}

static void parseFaceList(){
	unsigned short cnt;
	in->sgetn( (char*)&cnt,2 );
	_bbDebugLog( "FaceList cnt="+itoa(cnt) );
	while( cnt-- ){
		unsigned short v[4];
		in->sgetn( (char*)v,8 );
		Face3DS face;
		face.verts[0]=v[0];
		face.verts[1]=v[1];
		face.verts[2]=v[2];
		if( flip_tris ) std::swap( face.verts[1],face.verts[2] );
		faces.push_back( face );
	}
	enterChunk();
	while( int id=nextChunk() ){
		switch( id ){
		case CHUNK_FACEMAT:
			parseFaceMat();
			break;
		}
	}
	leaveChunk();
}

static void parseMapList(){
	_bbDebugLog( "MapList" );
	unsigned short cnt;
	in->sgetn( (char*)&cnt,2 );
	int n_verts=MeshLoader::numVertices();
	if( cnt>n_verts ) cnt=n_verts;
	for( int k=0;k<cnt;++k ){
		float uv[2];
		in->sgetn( (char*)uv,8 );
		Surface::Vertex &v=MeshLoader::refVertex( k );
		v.tex_coords[0][0]=v.tex_coords[1][0]=uv[0];
		v.tex_coords[0][1]=v.tex_coords[1][1]=1-uv[1];

	}
}

static void parseTriMesh( MeshModel *mesh ){
	_bbDebugLog( "TriMesh" );
	enterChunk();
	Transform tform;

	faces.clear();

	MeshLoader::beginMesh();

	while( int id=nextChunk() ){
		switch( id ){
		case CHUNK_VERTLIST:
			if( !animonly ) parseVertList();
			break;
		case CHUNK_MAPLIST:
			if( !animonly ) parseMapList();
			break;
		case CHUNK_FACELIST:
			if( !animonly ) parseFaceList();
			break;
		case CHUNK_TRMATRIX:
			in->sgetn( (char*)&tform,48 );
			if( conv ) tform=conv_tform * tform * -conv_tform;
			break;
		}
	}
	leaveChunk();

	mesh->setWorldTform( tform );

	if( animonly ){
		MeshLoader::endMesh( 0 );
		return;
	}

	Transform inv_tform=-tform;
	for( int k=0;k<MeshLoader::numVertices();++k ){
		Surface::Vertex &v=MeshLoader::refVertex( k );
		v.coords=inv_tform * v.coords;
	}

	int n_verts=MeshLoader::numVertices();
	for( unsigned int k=0;k<faces.size();++k ){
		const Face3DS &f=faces[k];
		if( f.verts[0]>=n_verts || f.verts[1]>=n_verts || f.verts[2]>=n_verts ) continue;
		MeshLoader::addTriangle( f.verts,f.brush );
	}

	MeshLoader::endMesh( mesh );
	mesh->updateNormals();

	faces.clear();
}

static void parseObject( MeshModel *root ){

	std::string name=parseString();
	_bbDebugLog( "Object:"+name );
	MeshModel *mesh=0;

	enterChunk();
	while( int id=nextChunk() ){
		switch( id ){
		case CHUNK_TRIMESH:
			mesh=d_new MeshModel();
			mesh->setName( name );
			mesh->setParent( root );
			name_map[name]=mesh;
			parseTriMesh( mesh );
			break;
		}
	}
	leaveChunk();
}

static void parseMaterial(){
	_bbDebugLog( "Material" );
	Brush mat;
	std::string name,tex_name;
	enterChunk();
	while( int id=nextChunk() ){
		switch( id ){
		case CHUNK_MATNAME:
			name=parseString();
			break;
		case CHUNK_DIFFUSE:
			mat.setColor( parseColor() );
			break;
		case CHUNK_AMBIENT:
			break;
		case CHUNK_SPECULAR:
			break;
		case CHUNK_TEXTURE:
			enterChunk();
			while( int id=nextChunk() ){
				switch( id ){
				case CHUNK_MAPFILE:
					tex_name=parseString();
					break;
				}
			}
			leaveChunk();
			break;
		}
	}
	if( tex_name.size() ){
		mat.setTexture( 0,Texture( tex_name,0 ),0 );
		mat.setColor( Vector( 1,1,1 ) );
	}
	if( name.size() ){
		materials_map[name]=mat;
	}
	leaveChunk();
}

static void parseScene( MeshModel *root ){
	_bbDebugLog( "Scene" );
	enterChunk();
	while( int id=nextChunk() ){
		switch( id ){
		case CHUNK_OBJECT:
			parseObject( root );
			break;
		case CHUNK_MATERIAL:
			if( !animonly ) parseMaterial();
			break;
		}
	}
	leaveChunk();
}

static void parseAnimKeys( Animation *anim,int type ){

	int cnt=0;
	short t_flags;
	in->sgetn( (char*)&t_flags,2 );
	in->pubseekoff( 8,std::ios_base::cur );
	in->sgetn( (char*)&cnt,2 );
	in->pubseekoff( 2,std::ios_base::cur );
	_bbDebugLog( "ANIM_TRACK: frames="+itoa( cnt ) );
	Vector pos,axis,scale;
	float angle;
	Quat quat;
	for( int k=0;k<cnt;++k ){
		int time;
		short flags;
		in->sgetn( (char*)&time,4 );
		in->sgetn( (char*)&flags,2 );
		float tens=0,cont=0,bias=0,ease_to=0,ease_from=0;
		if( flags & 1 ) in->sgetn( (char*)&tens,4 );
		if( flags & 2 ) in->sgetn( (char*)&cont,4 );
		if( flags & 4 ) in->sgetn( (char*)&bias,4 );
		if( flags & 8 ) in->sgetn( (char*)&ease_to,4 );
		if( flags & 16 ) in->sgetn( (char*)&ease_from,4 );
		switch( type ){
		case 0xb020:
			in->sgetn( (char*)&pos,12 );
			if( conv ) pos=conv_tform*pos;

			if( time<=anim_len ) anim->setPositionKey( time,pos );
			break;
		case 0xb021:
			in->sgetn( (char*)&angle,4 );
			in->sgetn( (char*)&axis,12 );

			if( axis.length()>EPSILON ){
				if( flip_tris ) angle=-angle;
				if( conv ) axis=conv_tform.m*axis;
				quat=Quat( cosf( angle/2 ),axis.normalized()*sinf( angle/2 ) )*quat;
				quat.normalize();
			}
			if( time<=anim_len ) anim->setRotationKey( time,quat );
			break;
		case 0xb022:
			in->sgetn( (char*)&scale,12 );
			if( conv ) scale=conv_tform.m*scale;

			_bbDebugLog( "SCL_KEY: time="+itoa(time)+" scale="+ftoa( scale.x )+","+ftoa( scale.y )+","+ftoa( scale.z ) );
			if( time<=anim_len ) anim->setScaleKey( time,scale );
			break;
		}
	}
}

static void parseMeshInfo( MeshModel *root,float curr_time ){
	_bbDebugLog( "OBJECT_NODE_TAG" );
	enterChunk();
	std::string name,inst;
	Vector pivot;
	Animation anim;
	unsigned short id=65535,parent=65535,flags1,flags2;
	Box box;
	Vector box_centre;
	while( int chunk_id=nextChunk() ){
		switch( chunk_id ){
		case 0xb030:
			in->sgetn( (char*)&id,2 );
			_bbDebugLog( "NODE_ID: "+itoa(id) );
			break;
		case 0xb010:
			name=parseString();
			in->sgetn( (char*)&flags1,2 );
			in->sgetn( (char*)&flags2,2 );
			in->sgetn( (char*)&parent,2 );
			_bbDebugLog( "NODE_HDR: name="+name+" parent="+itoa(parent) );
			break;
		case 0xb011:
			inst=parseString();
			_bbDebugLog( "INSTANCE_NAME: "+inst );
			break;
		case 0xb013:
			in->sgetn( (char*)&pivot,12 );
			if( conv ) pivot=conv_tform * pivot;
			_bbDebugLog( "PIVOT: "+ftoa(pivot.x)+","+ftoa(pivot.y)+","+ftoa(pivot.z) );
			break;
		case 0xb014:
			in->sgetn( (char*)&box.a,12 );
			in->sgetn( (char*)&box.b,12 );
			box_centre=box.centre();
			if( conv ) box_centre=conv_tform * box_centre;
			_bbDebugLog( "BOUNDBOX: min="+ftoa(box.a.x)+","+ftoa(box.a.y)+","+ftoa(box.a.z)+" max="+ftoa(box.b.x)+","+ftoa(box.b.y)+","+ftoa(box.b.z) );
			break;
		case 0xb020:
		case 0xb021:
		case 0xb022:
			if( !collapse ) parseAnimKeys( &anim,chunk_id );
			break;
		}
	}
	leaveChunk();

	MeshModel *p=root;
	if( parent!=65535 ){
		std::map<int,MeshModel*>::const_iterator it=id_map.find( parent );
		if( it==id_map.end() ) return;
		p=it->second;
	}
	MeshModel *mesh=0;
	if( name=="$$$DUMMY" ){
		mesh=d_new MeshModel();
		mesh->setName( inst );
		mesh->setParent( p );
	}else{
		std::map<std::string,MeshModel*>::const_iterator it=name_map.find( name );
		if( it==name_map.end() ) return;
		mesh=it->second;
		name_map.erase( name );
		if( pivot!=Vector() ){
			mesh->transform( -pivot );
		}
		Transform t=
		mesh->getWorldTform();
		mesh->setParent( p );
		mesh->setWorldTform( t );
	}

	mesh->setAnimation( anim );

	if( id!=65535 ) id_map[id]=mesh;
}

static void parseKeyFramer( MeshModel *root ){
	_bbDebugLog( "KeyFramer" );
	enterChunk();
	std::string file_3ds;
	unsigned short rev,curr_time=0;
	while( int id=nextChunk() ){
		switch( id ){
		case 0xb009:
			in->sgetn( (char*)&curr_time,2 );
			_bbDebugLog( "CURR_TIME: "+itoa(curr_time) );
			break;
		case 0xb00a:
			in->sgetn( (char*)&rev,2 );
			file_3ds=parseString();
			in->sgetn( (char*)&anim_len,2 );
			_bbDebugLog( "KFHDR: revision="+itoa(rev)+" 3dsfile="+file_3ds+" anim_len="+itoa(anim_len) );
			break;
		case 0xb002:
			parseMeshInfo( root,curr_time );
			break;
		}
	}

	if( !collapse ){
		root->setAnimator( d_new Animator( root,anim_len ) );
	}

	leaveChunk();
}

static MeshModel *parseFile(){
	unsigned short id;int len;
	in->sgetn( (char*)&id,2 );
	in->sgetn( (char*)&len,4 );
	if( id!=CHUNK_MAIN ) return 0;
	chunk_end=(int)in->pubseekoff( 0,std::ios_base::cur )+len-6;

	enterChunk();
	MeshModel *root=d_new MeshModel();
	while( int id=nextChunk() ){
		switch( id ){
		case CHUNK_SCENE:
			parseScene( root );
			break;
		case CHUNK_KEYFRAMER:
			parseKeyFramer( root );
			break;
		}
	}
	leaveChunk();
	return root;
}

MeshModel *Loader_3DS::load( const std::string &filename,const Transform &t,int hint ){

	conv_tform=t;
	conv=flip_tris=false;
	if( conv_tform!=Transform() ){
		conv=true;
		if( conv_tform.m.i.cross(conv_tform.m.j).dot(conv_tform.m.k)<0 ) flip_tris=true;
	}

	collapse=!!(hint&MeshLoader::HINT_COLLAPSE);
	animonly=!!(hint&MeshLoader::HINT_ANIMONLY);
	anim_len=0;

	if( !(in=gx_filesys->openFile( filename.c_str(),std::ios_base::in )) ){
		return 0;
	}

	MeshModel *root=parseFile();
	delete in;

	materials_map.clear();
	name_map.clear();
	id_map.clear();

	return root;
}
