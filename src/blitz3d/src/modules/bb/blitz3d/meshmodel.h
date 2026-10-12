
#ifndef MESHMODEL_H
#define MESHMODEL_H

#include "model.h"
#include "surface.h"

class MeshCollider;

class MeshModel : public Model{
public:
	typedef std::vector<Surface*> SurfaceList;

	MeshModel();
	MeshModel( const MeshModel &t );
	~MeshModel();

	virtual MeshModel *getMeshModel(){ return this; }
	virtual Entity *clone(){ return d_new MeshModel( *this ); }

	virtual bool collide( const Line &line,float radius,Collision *curr_coll,const Transform &t );

	virtual void setRenderBrush( const Brush &b );
	virtual bool render( const RenderContext &rc );
	virtual void renderQueue( int type );
	virtual bool deferrable()const{ return surf_bones.empty(); }

	void createBones();

	Surface *createSurface( const Brush &b );
	void setCullBox( const Box &box );
	void updateNormals();
	void flipTriangles();
	void transform( const Transform &t );
	void paint( const Brush &b );
	void add( const MeshModel &t );

	const SurfaceList &getSurfaces()const;
	Surface *findSurface( const Brush &b )const;
	bool intersects( const MeshModel &m )const;
	MeshCollider *getCollider()const;
	const Box &getBox()const;

private:
	struct Rep;

	Rep *rep;
	int brush_changes;
	Brush render_brush;
	std::vector<Brush> brushes;

	std::vector<Surface::Bone> surf_bones;

	MeshModel &operator=(const MeshModel &);
};

#endif
