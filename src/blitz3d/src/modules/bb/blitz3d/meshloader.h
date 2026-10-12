
#ifndef MESHLOADER_H
#define MESHLOADER_H

#include "model.h"
#include "surface.h"

class MeshLoader{
public:
	virtual ~MeshLoader(){}

	enum{
		HINT_COLLAPSE=1,
		HINT_ANIMONLY=2
	};

	virtual MeshModel *load( const std::string &f,const Transform &conv,int hint )=0;

	static void beginMesh();

	static void addVertex( const Surface::Vertex &v );

	static void addTriangle( const int verts[3],const Brush &b );

	static void addTriangle( int v0,int v1,int v2,const Brush &b );

	static void addBone( int vert,float weight,int bone );

	static Surface::Vertex &refVertex( int vert );

	static int numVertices();

	static void endMesh( MeshModel *mesh );
};

#endif
