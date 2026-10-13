
#include "std.h"
#include "frustum.h"
#include <algorithm>

Frustum::Frustum(){
}

Frustum::Frustum( float nr,float fr,float w,float h ){
	verts[VERT_TLNEAR]=Vector( w*-.5f,h*+.5f,nr );
	verts[VERT_TRNEAR]=Vector( w*+.5f,h*+.5f,nr );
	verts[VERT_BRNEAR]=Vector( w*+.5f,h*-.5f,nr );
	verts[VERT_BLNEAR]=Vector( w*-.5f,h*-.5f,nr );
	float t=fr/nr;
	verts[VERT_TLFAR]=verts[VERT_TLNEAR] * t;
	verts[VERT_TRFAR]=verts[VERT_TRNEAR] * t;
	verts[VERT_BRFAR]=verts[VERT_BRNEAR] * t;
	verts[VERT_BLFAR]=verts[VERT_BLNEAR] * t;
	verts[VERT_EYE]=Vector();
	makePlanes();
}

Frustum::Frustum( float nr,float fr,float l,float r,float u,float d ){
	float nlx=-tan(l) * nr;
	float nrx=tan(r) * nr;
	float nuy=tan(u) * nr;
	float ndy=-tan(d) * nr;

	verts[VERT_TLNEAR]=Vector( nlx,nuy,nr );
	verts[VERT_TRNEAR]=Vector( nrx,nuy,nr );
	verts[VERT_BRNEAR]=Vector( nrx,ndy,nr );
	verts[VERT_BLNEAR]=Vector( nlx,ndy,nr );
	float t=fr/nr;
	verts[VERT_TLFAR]=verts[VERT_TLNEAR] * t;
	verts[VERT_TRFAR]=verts[VERT_TRNEAR] * t;
	verts[VERT_BRFAR]=verts[VERT_BRNEAR] * t;
	verts[VERT_BLFAR]=verts[VERT_BLNEAR] * t;
	verts[VERT_EYE]=Vector();
	makePlanes();
}

Frustum::Frustum( const Frustum &f,const Transform &t ){
	for( int k=0;k<9;++k ){
		verts[k]=t*f.verts[k];
	}
	makePlanes();
}

bool Frustum::cull( const Vector v[],int cnt )const{
	for( int n=0;n<6;++n ){
		int k;
		for( k=0;k<cnt && planes[n].distance( v[k] )<0;++k ){}
		if( k==cnt ) return false;
	}
	return true;
}

bool Frustum::cull( const Box &b )const{
	if( b.empty() ){
		Vector v[8];
		for( int k=0;k<8;++k ) v[k]=b.corner(k);
		return cull( v,8 );
	}
	for( int k=0;k<6;++k ){
		const Plane &p=planes[k];
		// The corner furthest along the plane normal decides whether every
		// corner is outside. Avoid testing all eight corners per plane.
		Vector v( p.n.x>=0?b.b.x:b.a.x,p.n.y>=0?b.b.y:b.a.y,p.n.z>=0?b.b.z:b.a.z );
		if( p.distance(v)<0 ) return false;
	}
	return true;
}

bool Frustum::cull( const Box &b,const Transform &t )const{
	// Pull each world plane into model space. This avoids inverting the model
	// and rebuilding/normalizing six frustum planes for every mesh and camera.
	// No normalization is needed: only the sign of each distance matters.
	for( int k=0;k<6;++k ){
		const Plane &p=planes[k];
		Vector n( p.n.dot(t.m.i),p.n.dot(t.m.j),p.n.dot(t.m.k) );
		Vector v( n.x>=0?b.b.x:b.a.x,n.y>=0?b.b.y:b.a.y,n.z>=0?b.b.z:b.a.z );
		float d=p.distance(t.v);
		// Roundoff at a touching plane may retain a tiny extra sliver, but
		// must not make a visible object pop out of the frame or reflection.
		float tolerance=1e-5f*(fabsf(n.x*v.x)+fabsf(n.y*v.y)+fabsf(n.z*v.z)+fabsf(d)+1);
		if( n.dot(v)+d < -tolerance ) return false;
	}
	return true;
}

Frustum::Bounds::Bounds( const Box &b,const Transform &t ){
	const Vector center=t*((b.a+b.b)*0.5f),half=(b.b-b.a)*0.5f;
	const Vector i(fabsf(t.m.i.x),fabsf(t.m.i.y),fabsf(t.m.i.z));
	const Vector j(fabsf(t.m.j.x),fabsf(t.m.j.y),fabsf(t.m.j.z));
	const Vector k(fabsf(t.m.k.x),fabsf(t.m.k.y),fabsf(t.m.k.z));
	const Vector extent=i*half.x+j*half.y+k*half.z;
	world=Box( center-extent,center+extent );
	// Upper bound on the legacy plane test's tolerance, even with large
	// local coordinates, negative scales, shear or cancelling translations.
	roundoff=i*std::max(fabsf(b.a.x),fabsf(b.b.x))+
		j*std::max(fabsf(b.a.y),fabsf(b.b.y))+
		k*std::max(fabsf(b.a.z),fabsf(b.b.z))+
		Vector(fabsf(t.v.x),fabsf(t.v.y),fabsf(t.v.z));
}

bool Frustum::cull( const Box &b,const Transform &t,const Bounds &bounds )const{
	for( int k=0;k<6;++k ){
		const Plane &p=planes[k];
		const Box &w=bounds.world;
		const Vector &r=bounds.roundoff;
		const float guard=1e-5f*(fabsf(p.n.x)*r.x+fabsf(p.n.y)*r.y+fabsf(p.n.z)*r.z+fabsf(p.d)+1);
		const Vector farthest(p.n.x>=0?w.b.x:w.a.x,p.n.y>=0?w.b.y:w.a.y,p.n.z>=0?w.b.z:w.a.z);
		if( p.distance(farthest)<-guard ) return false;
		const Vector nearest(p.n.x>=0?w.a.x:w.b.x,p.n.y>=0?w.a.y:w.b.y,p.n.z>=0?w.a.z:w.b.z);
		if( p.distance(nearest)>guard ) continue;
		// Only a box crossing this plane needs the original oriented test.
		// Keep its arithmetic and tolerance for identical boundary decisions.
		Vector n(p.n.dot(t.m.i),p.n.dot(t.m.j),p.n.dot(t.m.k));
		Vector v(n.x>=0?b.b.x:b.a.x,n.y>=0?b.b.y:b.a.y,n.z>=0?b.b.z:b.a.z);
		float d=p.distance(t.v);
		float tolerance=1e-5f*(fabsf(n.x*v.x)+fabsf(n.y*v.y)+fabsf(n.z*v.z)+fabsf(d)+1);
		if( n.dot(v)+d < -tolerance ) return false;
	}
	return true;
}

float Frustum::getLeft()const{
	return getVertex( Frustum::VERT_TLNEAR ).x;
}

float Frustum::getRight()const{
	return getVertex( Frustum::VERT_TRNEAR ).x;
}

float Frustum::getTop()const{
	return getVertex( Frustum::VERT_TLNEAR ).y;
}

float Frustum::getBottom()const{
	return getVertex( Frustum::VERT_BLNEAR ).y;
}

float Frustum::getNear()const{
	return getVertex( Frustum::VERT_TLNEAR ).z;
}

float Frustum::getFar()const{
	return getVertex( Frustum::VERT_TLFAR ).z;
}

void Frustum::makePlanes(){
	planes[PLANE_TOP]=Plane( verts[VERT_EYE],verts[VERT_TRFAR],verts[VERT_TLFAR] );
	planes[PLANE_LEFT]=Plane( verts[VERT_EYE],verts[VERT_TLFAR],verts[VERT_BLFAR] );
	planes[PLANE_BOTTOM]=Plane( verts[VERT_EYE],verts[VERT_BLFAR],verts[VERT_BRFAR] );
	planes[PLANE_RIGHT]=Plane( verts[VERT_EYE],verts[VERT_BRFAR],verts[VERT_TRFAR] );
	planes[PLANE_NEAR]=Plane( verts[VERT_TRNEAR],verts[VERT_TLNEAR],verts[VERT_BLNEAR] );
	planes[PLANE_FAR]=Plane( verts[VERT_TLFAR],verts[VERT_TRFAR],verts[VERT_BRFAR] );
	if( planes[PLANE_NEAR].distance( verts[VERT_EYE] )>0 ){
		for( int k=0;k<6;++k ) planes[k]=-planes[k];
	}
}
