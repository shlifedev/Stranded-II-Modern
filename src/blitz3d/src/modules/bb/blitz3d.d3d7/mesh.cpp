
#include <bb/system.windows/system.windows.h>
#include "mesh.h"

gxMesh::gxMesh( IDirect3DDevice7 *d,IDirect3DVertexBuffer7 *vs,WORD *is,int max_vs,int max_ts ):
dir3dDev(d),locked_verts(0),vertex_buff(vs),tri_indices(is),max_verts(max_vs),max_tris(max_ts),mesh_dirty(false){
}

gxMesh::~gxMesh(){
	unlock();

	vertex_buff->Release();

	delete[] tri_indices;
}

bool gxMesh::lock( bool all ){
	if( locked_verts ) return true;

	int flags=DDLOCK_WAIT|DDLOCK_WRITEONLY;

	if( bbWindowsSystemDriver->isXPorLess() ){
		flags|=(all ? DDLOCK_DISCARDCONTENTS : DDLOCK_NOOVERWRITE);
	}

	if( vertex_buff->Lock( flags,(void**)&locked_verts,0 )>=0 ){
		mesh_dirty=false;
		return true;
	}

	static dxVertex *err_verts=new dxVertex[32768];

	locked_verts=err_verts;
	return true;
}

void gxMesh::unlock(){
	if( locked_verts ){
		vertex_buff->Unlock();
		locked_verts=0;
	}
}

void gxMesh::backup(){
	unlock();
}

void gxMesh::restore(){
	mesh_dirty=true;
}

void gxMesh::render( int first_vert,int vert_cnt,int first_tri,int tri_cnt ){
	unlock();
	dir3dDev->DrawIndexedPrimitiveVB(
		D3DPT_TRIANGLELIST,
		vertex_buff,first_vert,vert_cnt,
		tri_indices+first_tri*3,tri_cnt*3,0 );
}
