#ifdef BB_WEBGPU
#include <bb/debugui/profiler.h>
#endif

#include <bb/graphics/graphics.h>
#include "std.h"
#include <queue>
#include <algorithm>
#include "world.h"

float stats3d[10];

extern BBScene *bbScene;

static std::vector<Object*> _enabled,_visible;

static void enumEnabled(){
	_enabled.clear();
	for( Entity *e=Entity::orphans();e;e=e->successor() ){
		e->enumEnabled( _enabled );
	}
}

static void enumVisible(){
	_visible.clear();
	for( Entity *e=Entity::orphans();e;e=e->successor() ){
		e->enumVisible( _visible );
	}
}

static std::vector<Object*> _objsByType[1000];

static std::vector<ObjCollision*> free_colls,used_colls;

static ObjCollision *allocObjColl( Object *with,const Vector &coords,const Collision &coll ){
	ObjCollision *c;
	if( free_colls.size() ){
		c=free_colls.back();
		free_colls.pop_back();
	}else{
		c=new ObjCollision();
	}
	used_colls.push_back( c );
	c->with=with;
	c->coords=coords;
	c->collision=coll;
	return c;
}

static void collided( Object *src,Object *dest,const Line &line,const Collision &coll,float y_scale ){

	ObjCollision *c;
	const Vector &coords=line*coll.time-coll.normal*src->getCollisionRadii().x;

	c=allocObjColl( dest,coords,coll );
	c->coords.y*=y_scale;
	src->addCollision( c );

	c=allocObjColl( src,coords,coll );
	c->coords.y*=y_scale;
	dest->addCollision( c );
}

void World::clearCollisions(){
	for( int k=0;k<1000;++k ){
		_collInfo[k].clear();
	}
}

void World::addCollision( int src_type,int dst_type,int method,int response ){

	std::vector<CollInfo> &info=_collInfo[src_type];
	for( unsigned int k=0;k<info.size();++k ){
		const CollInfo &t=info[k];
		if( dst_type==t.dst_type ) return;
	}

	CollInfo co={dst_type,method,response};
	_collInfo[src_type].push_back(co);
}

bool World::hitTest( const Line &line,float radius,Object *obj,const Transform &tf,int method,Collision *curr_coll  ){
	switch( method ){
	case COLLISION_METHOD_SPHERE:
		return curr_coll->sphereCollide( line,radius,tf.v,obj->getCollisionRadii().x );
	case COLLISION_METHOD_POLYGON:
		return obj->collide( line,radius,curr_coll,tf );
	case COLLISION_METHOD_BOX:
		Transform t=tf;
		t.m.i.normalize();t.m.j.normalize();t.m.k.normalize();
		if( curr_coll->boxCollide( ~t*line,radius,obj->getCollisionBox() ) ){
			curr_coll->normal=t.m*curr_coll->normal;
			return true;
		}
	}
	return false;
}

bool World::checkLOS( Object *src,Object *dest ){

	enumEnabled();

	Collision curr_coll;

	Line line( src->getWorldPosition(),dest->getWorldPosition()-src->getWorldPosition() );

	std::vector<Object*>::const_iterator it;

	for( it=_enabled.begin();it!=_enabled.end();++it ){
		Object *obj=*it;

		if( obj==src || obj==dest || !obj->getPickGeometry() || !obj->getObscurer() ) continue;

		if( hitTest( line,0,obj,obj->getWorldTform(),obj->getPickGeometry(),&curr_coll ) ){
			return false;
		}
	}
	return true;
}

Object *World::traceRay( const Line &line,float radius,ObjCollision *curr_coll ){

	enumEnabled();

	Object *coll_obj=0;

	std::vector<Object*>::const_iterator it;
	for( it=_enabled.begin();it!=_enabled.end();++it ){
		Object *obj=*it;

		if( !obj->getPickGeometry() ) continue;

		if( hitTest( line,radius,obj,obj->getWorldTform(),obj->getPickGeometry(),&curr_coll->collision ) ){
			coll_obj=obj;
		}
	}
	if( (curr_coll->with=coll_obj) ){
		curr_coll->coords=line*curr_coll->collision.time-curr_coll->collision.normal*radius;
	}
	return coll_obj;
}

void World::collide( Object *src ){

	static const int MAX_HITS=10;

	Vector dv=src->getWorldTform().v;
	Vector sv=src->getPrevWorldTform().v;

	if( sv==dv ){
		if( dv.x!=sv.x || dv.y!=sv.y || dv.z!=sv.z ){
			src->setWorldPosition( sv );
		}
		return;
	}

	Vector panic=sv;

	static Transform y_tform;

	const Vector &radii=src->getCollisionRadii();

	float radius=radii.x,inv_y_scale;
	float y_scale=inv_y_scale=y_tform.m.j.y=1;

	if( radii.x!=radii.y ){
		y_scale=y_tform.m.j.y=radius/radii.y;
		inv_y_scale=1/y_scale;
		sv.y*=y_scale;
		dv.y*=y_scale;
	}

	int n_hit=0;
	Plane planes[2];
	Line coll_line( sv,dv-sv );
	Vector dir=coll_line.d;

	float td=coll_line.d.length();
	float td_xz=Vector( coll_line.d.x,0,coll_line.d.z ).length();

	const std::vector<CollInfo> &collinfos=_collInfo[src->getCollisionType()];

	int hits=0;
	for(;;){

		Collision coll;
		Object *coll_obj=0;
		std::vector<CollInfo>::const_iterator coll_it,coll_info;

		for( coll_it=collinfos.begin();coll_it!=collinfos.end();++coll_it ){

			std::vector<Object*>::const_iterator dst_it;

			const std::vector<Object*> &dst_objs=_objsByType[coll_it->dst_type];

			for( dst_it=dst_objs.begin();dst_it!=dst_objs.end();++dst_it ){

				Object *dst=*dst_it;

				if( src==dst ) continue;

				const Transform &dst_tform=dst->getPrevWorldTform();

				if( y_scale==1 ){
					if( hitTest(
					coll_line,radius,dst,dst_tform,
					coll_it->method,&coll ) ){
						coll_obj=dst;
						coll_info=coll_it;
					}
				}else{
					if( hitTest(
					coll_line,radius,dst,y_tform * dst_tform,
					coll_it->method,&coll ) ){
						coll_obj=dst;
						coll_info=coll_it;
					}
				}
			}
		}
		if( !coll_obj ) break;

		if( ++hits==MAX_HITS ){
			break;
		}

		collided( src,coll_obj,coll_line,coll,inv_y_scale );

		Plane coll_plane( coll_line*coll.time,coll.normal );

		coll_plane.d-=COLLISION_EPSILON;
		coll.time=coll_plane.t_intersect( coll_line );

		if( coll.time>0 ){
			sv=coll_line*coll.time;
			td*=1-coll.time;
			td_xz*=1-coll.time;
		}

		if( coll_info->response==COLLISION_RESPONSE_STOP ){
			dv=sv;
			break;
		}

		Vector nv=coll_plane.nearest( dv );

		if( n_hit==0 ){
			dv=nv;
		}else if( n_hit==1 ){
			if( planes[0].distance(nv)>=0 ){
				dv=nv;n_hit=0;
			}else if( fabs( planes[0].n.dot( coll_plane.n ) )<1-EPSILON ){
				dv=coll_plane.intersect( planes[0] ).nearest( dv );
			}else{
				hits=MAX_HITS;break;
			}
		}else if( planes[0].distance(nv)>=0 && planes[1].distance(nv)>=0 ){
			dv=nv;n_hit=0;
		}else{
			dv=sv;break;
		}

		Vector dd( dv-sv );

		if( dd.dot( dir )<=0 ){ dv=sv;break; }

		if( coll_info->response==COLLISION_RESPONSE_SLIDE ){
			float d=dd.length();
			if( d<=EPSILON ){ dv=sv;break; }
			if( d>td ) dd*=td/d;
		}else if( coll_info->response==COLLISION_RESPONSE_SLIDEXZ ){
			float d=Vector( dd.x,0,dd.z ).length();
			if( d<=EPSILON ){ dv=sv;break; }
			if( d>td_xz ) dd*=td_xz/d;
		}

		coll_line.o=sv;
		coll_line.d=dd;dv=sv+dd;
		planes[n_hit++]=coll_plane;
	}

	if( hits ){
		if( hits<MAX_HITS ){
			dv.y*=inv_y_scale;
			src->setWorldPosition( dv );
		}else{
			src->setWorldPosition( panic );
		}
	}
}

void World::update( float elapsed ){

	stats3d[0]=0;

	for( ;used_colls.size();used_colls.pop_back() ){
		free_colls.push_back( used_colls.back() );
	}

	enumEnabled();

	std::vector<Object*>::const_iterator it;
	for( it=_enabled.begin();it!=_enabled.end();++it ){
		Object *o=*it;

		if( int n=o->getCollisionType() ){
			_objsByType[n].push_back(o);
		}
	}

	for( it=_enabled.begin();it!=_enabled.end();++it ){
		Object *o=*it;

		o->beginUpdate( elapsed );

		if( o->getCollisionType() ) collide( o );

		o->endUpdate();
	}

	for( int k=0;k<1000;++k ){
		_objsByType[k].clear();
	}
}

static Transform cam_tform;

static std::vector<BBLightRep*> _lights;
static std::vector<Mirror*> _mirrors;
static std::vector<Listener*> _listeners;

struct OrderComp{
	bool operator()( Object *a,Object *b ){
		return a->getOrder()<b->getOrder();
	}
};

struct TransparentDraw{
	Model *model;
	float distance;
};

struct TransComp{
	bool operator()( const TransparentDraw &a,const TransparentDraw &b )const{
		return a.distance<b.distance;
	}
};

static std::vector<Model*> ord_mods,unord_mods;

static std::vector<Model::Draw> opaque_draws;

static std::priority_queue<Model*,std::vector<Model*>,OrderComp> ord_que;

static std::priority_queue<Camera*,std::vector<Camera*>,OrderComp> cam_que;

static std::priority_queue<TransparentDraw,std::vector<TransparentDraw>,TransComp> transparents;

void World::capture(){

	enumVisible();

	std::vector<Object*>::const_iterator it;
	for( it=_visible.begin();it!=_visible.end();++it ){
		(*it)->capture();
	}
}

void World::render( float tween ){
	prepareRender( tween );
	renderPrepared( true );
}

void World::renderCubeMap( Camera *camera,BBCanvas *texture,float tween ){
#ifdef BB_WEBGPU
	s2prof::RenderGroup profileGroup(s2prof::Reflections);
#endif
	// This call owns all six views: no script can mutate the scene between
	// them. Keep the prepared scene local to this capture, never across frames.
	static const float angles[6][2]={ {0,90},{0,0},{0,-90},{0,180},{-90,0},{90,0} };
	const float radians=0.01745329251994329577f;
	// Camera children move with each face. Repeated tween preparation also
	// has legacy semantics (notably Sprite), so retain it outside tween=1.
	const bool reprepare=camera->children()!=0 || tween!=1;
	BBCanvas *source=camera->getCanvas() ? camera->getCanvas() : gx_canvas;
	for( int face=0;face<6;++face ){
		texture->setCubeFace( face );
		camera->setLocalRotation( rotationQuat( angles[face][0]*radians,angles[face][1]*radians,0 ) );
		if( face==0 || reprepare ) prepareRender( tween );
		camera->beginRender( tween );
		// A cubemap capture uses only its selected camera, even if other
		// cameras are visible. Per-view culling, fading and sorting still run.
		while( !cam_que.empty() ) cam_que.pop();
		cam_que.push( camera );
		renderPrepared();
		texture->blit( 0,0,source,0,0,texture->getWidth(),texture->getHeight(),true );
	}
}

void World::renderPlanar( Camera *camera,BBCanvas *texture,float height,float tween ){
#ifdef BB_WEBGPU
	s2prof::RenderGroup profileGroup(s2prof::Reflections);
#endif
	// A dedicated capture renders only this view, without moving the caller's
	// camera or its children. Keep the source camera's projection/aspect ratio.
	prepareRender( tween );
	camera->beginRender( tween );
	if( !bbScene->begin( _lights ) ) return;
	BBCanvas *saved=gx_canvas;
	if( saved ) saved->unset();
	gx_canvas=texture;texture->set();
	if( bbScene->setCamera( camera,texture ) ){
		bbScene->setViewport( 0,0,texture->getWidth(),texture->getHeight() );
		bbScene->clear( &(camera->getClsColor().x),1,1,true,true );
		const float clip[4]={0,1,0,-height};
		bbScene->setClipPlane( clip );
		Mirror mirror;
		mirror.setLocalPosition( Vector( 0,height,0 ) );
		mirror.beginRender( 1 );
		render( camera,&mirror );
		bbScene->captureReflectionMatrix();
		const float no_clip[4]={0,0,0,0};
		bbScene->setClipPlane( no_clip );
	}
	bbScene->end();
	texture->unset();gx_canvas=saved;
	if( saved ) saved->set();
	// Queues and culling caches belong to this preparation only.
	while( !cam_que.empty() ) cam_que.pop();
}

void World::renderShadows( Camera *camera,Light *light,float distance,float tween ){
	const float disabled[4]={};
	bbScene->setShadowLight( 0,disabled,0,0 );
	if( distance<=0 || !light || light->getType()!=Light::LIGHT_DISTANT ) return;
	distance=std::min( distance,camera->getFrustumFar() );
	if( distance<=camera->getFrustumNear() ) return;
	prepareRender( tween );
	camera->beginRender( tween );
	light->beginRender( tween );
	while( !cam_que.empty() ) cam_que.pop();
	if( !bbScene->begin( _lights ) ) return;
	bbScene->setCanvas( gx_canvas->getWidth(),gx_canvas->getHeight() );
	const Transform &eye=camera->getRenderTform();
	Transform inverse=-eye;
	const float eye_depth[4]={ inverse.m.i.z,inverse.m.j.z,inverse.m.k.z,inverse.v.z };
	const float split=distance*0.3f;
	bbScene->setShadowLight( light->getRep(),eye_depth,split,distance );
	const Frustum &view=camera->getFrustum();
	const float clip_near=camera->getFrustumNear();
	const float hx=std::max( fabsf(view.getLeft()),fabsf(view.getRight()) )/clip_near;
	const float hy=std::max( fabsf(view.getTop()),fabsf(view.getBottom()) )/clip_near;
	Matrix basis=light->getRenderTform().m;
	basis.i.normalize();basis.j.normalize();basis.k.normalize();
	for( int cascade=0;cascade<2;++cascade ){
		const float start=cascade?split*0.85f:clip_near,end=cascade?distance:split;
		Vector center=eye*Vector(0,0,(start+end)*0.5f);
		float radius=sqrtf(end*end*(hx*hx+hy*hy)+(end-start)*(end-start)*0.25f);
		radius=ceilf(radius/16)*16;
		// Stable texel-sized translation reduces shimmering during camera motion.
		Transform light_frame(basis);
		Vector local=(-light_frame)*center;
		const float texel=radius*2/1024;
		local.x=floorf(local.x/texel+0.5f)*texel;
		local.y=floorf(local.y/texel+0.5f)*texel;
		center=light_frame*local;
		const float depth=radius+800;
		cam_tform=Transform(basis,center-basis.k*depth);
		if( !bbScene->beginShadow(cascade) ) break;
		bbScene->setFlippedTris(false);
		bbScene->setViewMatrix((BBScene::Matrix*)&(-cam_tform));
		bbScene->setOrthoProj(1,depth*2,-radius,radius,-radius,radius);
		bbScene->setFogMode(BBScene::FOG_NONE);
		bbScene->setZMode(BBScene::ZMODE_NORMAL);
		RenderContext rc(cam_tform,Frustum::orthographic(1,depth*2,radius*2,radius*2),false);
		for( Model *mod:unord_mods ){
			// Preserve player-distance fading, even though the shadow camera is distant.
			if( mod->doAutoFade(eye.v) ) render(mod,rc,true);
		}
		flushOpaque();
		flushTransparent();
		bbScene->endShadow(cascade);
	}
	bbScene->end();
}

void World::prepareRender( float tween ){
	ord_mods.clear();
	unord_mods.clear();

	_visible.clear();
	_lights.clear();
	_mirrors.clear();
	_listeners.clear();

	enumVisible();

	std::vector<Object*>::const_iterator it;
	for( it=_visible.begin();it!=_visible.end();++it ){
		Object *o=*it;

		if( !o->beginRender(tween) ) continue;

		if( Light *t=o->getLight() ) _lights.push_back(t->getRep());
		else if( Camera *t=o->getCamera() ) cam_que.push(t);
		else if( Mirror *t=o->getMirror() ) _mirrors.push_back(t);
		else if( Listener *t=o->getListener() ) _listeners.push_back(t);
		else if( Model *t=o->getModel() ){
			if( t->getOrder() ) ord_que.push( t );
			else unord_mods.push_back( t );
		}
	}

	for( ;ord_que.size();ord_que.pop() ) ord_mods.push_back( ord_que.top() );
}

void World::renderPrepared( bool antialias ){
	bbScene->setAntialiasTarget( antialias?gx_canvas:nullptr );
	if( !bbScene->begin( _lights ) ){
		bbScene->setAntialiasTarget( nullptr );
		while( !cam_que.empty() ) cam_que.pop();
		return;
	}

	BBCanvas *org_canvas=gx_canvas;
	bool rendered_main=false;
	for( ;cam_que.size();cam_que.pop() ){

		Camera *cam=cam_que.top();

		BBCanvas *canvas=cam->getCanvas();
		if( canvas==0 ) canvas=org_canvas;

		if( gx_canvas!=canvas ){
			if( gx_canvas ) gx_canvas->unset();
			gx_canvas=canvas;
			gx_canvas->set();
		}

		if( !bbScene->setCamera( cam,gx_canvas ) ) continue;
		if( gx_canvas==org_canvas ) rendered_main=true;

		std::vector<Mirror*>::const_iterator mir_it;
		for( mir_it=_mirrors.begin();mir_it!=_mirrors.end();++mir_it ){
			render( cam,*mir_it );
		}

		render( cam,0 );
	}

	if( gx_canvas!=org_canvas ){
		if( gx_canvas ) gx_canvas->unset();
		gx_canvas=org_canvas;
		gx_canvas->set();
	}

	bbScene->end();
	// Captures call renderPrepared() without this final, pre-UI screen pass.
	if( antialias && rendered_main ) bbScene->applyAntialias( org_canvas );
	bbScene->setAntialiasTarget( nullptr );

	std::vector<Listener*>::const_iterator lis_it;
	for( lis_it=_listeners.begin();lis_it!=_listeners.end();++lis_it ){
		(*lis_it)->renderListener();
	}
}

void World::render( Camera *cam,Mirror *mirror ){

	if( mirror ){
		const Transform &t=mirror->getRenderTform();
		cam_tform=t * Transform( scaleMatrix( 1,-1,1 ) ) * -t * cam->getRenderTform();
		bbScene->setFlippedTris( true );
	}else{
		cam_tform=cam->getRenderTform();
		bbScene->setFlippedTris( false );
	}

	bbScene->setViewMatrix( (BBScene::Matrix*)&(-cam_tform) );

	RenderContext rc( cam_tform,cam->getFrustum(),mirror!=0 );

	unsigned int ord=0;
	bbScene->setZMode( BBScene::ZMODE_DISABLE );
	while( ord<ord_mods.size() && ord_mods[ord]->getOrder()>0 ){
		Model *mod=ord_mods[ord++];
		if( !mod->doAutoFade( cam_tform.v ) ) continue;
		render( mod,rc );
		flushTransparent();
	}

	bbScene->setZMode( BBScene::ZMODE_NORMAL );
	for( unsigned int k=0;k<unord_mods.size();++k ){
		Model *mod=unord_mods[k];
		if( !mod->doAutoFade( cam_tform.v ) ) continue;
		render( mod,rc,true );
	}
	flushOpaque();
	bbScene->setZMode( BBScene::ZMODE_CMPONLY );
	flushTransparent();

	bbScene->setZMode( BBScene::ZMODE_DISABLE );
	while( ord<ord_mods.size() ){
		Model *mod=ord_mods[ord++];
		if( !mod->doAutoFade( cam_tform.v ) ) continue;
		render( mod,rc );
		flushTransparent();
	}
}

void World::render( Model *mod,const RenderContext &rc,bool defer ){

	bool trans=mod->render( rc );

	if( mod->queueSize( Model::QUEUE_OPAQUE ) ){
		if( defer&&mod->deferrable() ){
			mod->takeQueue( Model::QUEUE_OPAQUE,opaque_draws );
		}else{
			if( mod->getRenderSpace()==Model::RENDER_SPACE_LOCAL ){
				bbScene->setWorldMatrix( (BBScene::Matrix*)&mod->getRenderTform() );
			}else{
				bbScene->setWorldMatrix( 0 );
			}
			mod->renderQueue( Model::QUEUE_OPAQUE );
		}
	}

	if( trans || mod->queueSize( Model::QUEUE_TRANSPARENT ) ){
		// Distance is constant while this camera's queue is drained. Keep the
		// same heap/order, computing it once rather than for every comparison.
		TransparentDraw draw={ mod,cam_tform.v.distance( mod->getRenderTform().v ) };
		transparents.push( draw );
	}
}

void World::flushOpaque(){

	std::stable_sort( opaque_draws.begin(),opaque_draws.end() );

	Model *current=0;
	for( size_t k=0;k<opaque_draws.size();++k ){
		const Model::Draw &d=opaque_draws[k];
		if( d.model!=current ){
			current=d.model;
			if( current->getRenderSpace()==Model::RENDER_SPACE_LOCAL ){
				bbScene->setWorldMatrix( (BBScene::Matrix*)&current->getRenderTform() );
			}else{
				bbScene->setWorldMatrix( 0 );
			}
		}
		Model::drawQueued( d );
	}
	opaque_draws.clear();
}

void World::flushTransparent(){

	bool local=true;

	for( ;transparents.size();transparents.pop() ){
		Model *mod=transparents.top().model;
		if( mod->getRenderSpace()==Model::RENDER_SPACE_LOCAL ){
			bbScene->setWorldMatrix( (BBScene::Matrix*)&mod->getRenderTform() );
			local=true;
		}else if( local ){
			bbScene->setWorldMatrix( 0 );
			local=false;
		}
		mod->renderQueue( Model::QUEUE_TRANSPARENT );
	}
}
