
#include "std.h"
#include "model.h"

extern BBScene *bbScene;

class Model::MeshQueue{
	union{
		BBMesh *mesh;
		MeshQueue *next;
	};
	int fv,vc,ft,tc;
	Brush brush;
	int q_type;

	static MeshQueue *pool;

public:
	MeshQueue(){}

	MeshQueue( BBMesh *m,int fv,int vc,int ft,int tc,const Brush &b ):
	mesh(m),fv(fv),vc(vc),ft(ft),tc(tc),brush(b){
		int n=brush.getBlend();
		q_type=(n==BBScene::BLEND_REPLACE) ? QUEUE_OPAQUE : QUEUE_TRANSPARENT;
	}

	int getQueueType()const{
		return q_type;
	}
	void render(){
		bbScene->setRenderState( brush.getRenderState() );
		bbScene->render( mesh,fv,vc,ft,tc );
	}

	static unsigned long long mix( unsigned long long h,unsigned long long v ){
		return h^( v+0x9e3779b97f4a7c15ULL+(h<<6)+(h>>2) );
	}

	static unsigned long long bits( float f ){
		unsigned int u;
		memcpy( &u,&f,sizeof(u) );
		return u;
	}

	void sortKeys( unsigned long long &k0,unsigned long long &k1 )const{
		const BBScene::RenderState &rs=brush.getRenderState();

		k0=mix( 0,(unsigned long long)rs.fx );
		k0=mix( k0,(unsigned long long)rs.blend );
		k1=0;
		for( int k=0;k<BBScene::MAX_TEXTURES;++k ){
			const BBScene::RenderState::TexState &ts=rs.tex_states[k];
			if( !ts.canvas||!ts.blend ) continue;
			k0=mix( k0,(unsigned long long)(size_t)ts.canvas );
			k0=mix( k0,(unsigned long long)ts.flags );
			k0=mix( k0,(unsigned long long)ts.blend );
			k1=mix( k1,(unsigned long long)(size_t)ts.matrix );
		}
		k1=mix( k1,bits( rs.color[0] ) );
		k1=mix( k1,bits( rs.color[1] ) );
		k1=mix( k1,bits( rs.color[2] ) );
		k1=mix( k1,bits( rs.alpha ) );
		k1=mix( k1,bits( rs.shininess ) );
	}
	void *operator new( size_t sz ){
		static const int GROW=256;
		if( !pool ){
			pool=new MeshQueue[GROW];
			for( int k=0;k<GROW-1;++k ) pool[k].next=&pool[k+1];
			pool[GROW-1].next=0;
		}
		MeshQueue *t=pool;
		pool=t->next;
		return t;
	}
	void operator delete( void *q ){
		MeshQueue *t=(MeshQueue*)q;
		t->next=pool;
		pool=t;
	}
};

Model::MeshQueue *Model::MeshQueue::pool;

Model::Model():
space( RENDER_SPACE_LOCAL ),
auto_fade(false),
captured_alpha(1),w_brush(true){
}

Model::Model( const Model &t ):Object(t),
space(t.space),brush(t.brush),
auto_fade(t.auto_fade),auto_fade_nr(t.auto_fade_nr),auto_fade_fr(t.auto_fade_fr),
captured_alpha(t.captured_alpha),w_brush(true){
}

void Model::capture(){
	Object::capture();
	captured_alpha=brush.getAlpha();
}

bool Model::beginRender( float t ){
	Object::beginRender( t );
	tweened_alpha=brush.getAlpha();
	if( t!=1 && tweened_alpha!=captured_alpha ){

		tweened_alpha=(tweened_alpha-captured_alpha)*t+captured_alpha;
	}
	return tweened_alpha>0;
}

bool Model::doAutoFade( const Vector &eye ){
	float alpha=tweened_alpha;
	if( auto_fade ){

		float d=eye.distance( getRenderTform().v );
		if( d>=auto_fade_fr ) return false;
		if( d>=auto_fade_nr ){
			float t=1-(d-auto_fade_nr)/(auto_fade_fr-auto_fade_nr );
			alpha*=t;if( alpha<=0 ) return false;
		}
	}
	if( w_brush ) render_brush=brush;

	if( alpha!=render_brush.getAlpha() ){
		render_brush.setAlpha( alpha );
	}else if( !w_brush ){
		return true;
	}

	setRenderBrush( render_brush );
	w_brush=false;
	return true;
}

void Model::enqueue( MeshQueue *q ){
	queues[q->getQueueType()].push_back( q );
}

void Model::enqueue( BBMesh *mesh,int fv,int vc,int ft,int tc ){
	enqueue( new MeshQueue( mesh,fv,vc,ft,tc,render_brush ) );
}

void Model::enqueue( BBMesh *mesh,int fv,int vc,int ft,int tc,const Brush &brush ){
	enqueue( new MeshQueue( mesh,fv,vc,ft,tc,brush ) );
}

void Model::takeQueue( int type,std::vector<Draw> &out ){
	std::vector<MeshQueue*> &que=queues[type];
	for( size_t k=que.size();k>0;--k ){
		Draw d;
		d.model=this;
		d.queue=que[k-1];
		d.queue->sortKeys( d.key0,d.key1 );
		out.push_back( d );
	}
	que.clear();
}

void Model::drawQueued( const Draw &d ){
	d.queue->render();
	delete d.queue;
}

void Model::renderQueue( int type ){
	std::vector<MeshQueue*> *que=&queues[type];
	for( ;que->size();que->pop_back() ){
		MeshQueue *q=que->back();
		q->render();
		delete q;
	}
}
