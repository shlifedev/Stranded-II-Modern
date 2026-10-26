// CPU command-order fixture for the actual WebGPU self-blit methods.
// GPU calls are modeled; this does not measure GPU time or displayed FPS.
#include <algorithm>
#include <cassert>
#include <cmath>
#include <cstdint>
#include <iostream>
#include <vector>

struct Texture { int w,h; std::vector<unsigned> pixels;int refs=1; };
struct View { Texture *texture; };
using WGPUTexture=Texture*;
using WGPUTextureView=View*;
using WGPUTextureFormat=int;
constexpr int WGPUTextureFormat_Undefined=0,WGPUTextureUsage_TextureBinding=1,
    WGPUTextureUsage_CopyDst=2,WGPUTextureDimension_2D=1,WGPUTextureAspect_All=0,
    WGPUPrimitiveTopology_TriangleList=1;
struct WGPUExtent3D { uint32_t width,height,depthOrArrayLayers; };
struct WGPUTextureDescriptor {
    const char *label; int usage,dimension; WGPUExtent3D size;
    int format,mipLevelCount,sampleCount;
};
struct WGPUTexelCopyTextureInfo { Texture *texture; int aspect; };
struct BBWebGPUVertex { float x,y,u,v; };
const char *bbStrView(const char *s){ return s; }
int allocations=0,releases=0,copies=0;
Texture *wgpuDeviceCreateTexture(void *,const WGPUTextureDescriptor *d){
    ++allocations;
    return new Texture{int(d->size.width),int(d->size.height),std::vector<unsigned>(d->size.width*d->size.height)};
}
void dropTexture(Texture *t){if(--t->refs==0) delete t;}
View *wgpuTextureCreateView(Texture *t,void *){ ++t->refs;return new View{t}; }
void wgpuTextureViewRelease(View *v){ dropTexture(v->texture);delete v; }
void wgpuTextureRelease(Texture *t){ ++releases;dropTexture(t); }
void wgpuCommandEncoderCopyTextureToTexture(void *,const WGPUTexelCopyTextureInfo *from,
        const WGPUTexelCopyTextureInfo *to,const WGPUExtent3D *extent){
    assert(extent->width==unsigned(from->texture->w) && extent->height==unsigned(from->texture->h));
    assert(from->texture->w==to->texture->w && from->texture->h==to->texture->h);
    ++copies;to->texture->pixels=from->texture->pixels;
}

class WebGPUCanvas;
struct Draw { WebGPUCanvas *target;Texture *source;float l,t,r,b,u0,v0,u1,v1;unsigned mode,mask; };
struct Resources {
    void *device=this,*encoder=this;
    bool pass=false;
    WebGPUCanvas *pass_target=nullptr;
    Texture *surface_texture=nullptr;
    int sampler_nearest=0;
    std::vector<Draw> pending;
    void endPass();
    void ensureEncoder(){}
    View *acquireSurfaceView(){ return nullptr; }
    void invalidateBindGroupsFor(View *){}
};
struct BBCanvas {
    virtual int getWidth() const=0;
    virtual int getHeight() const=0;
};
class WebGPUCanvas:public BBCanvas {
public:
    Resources *res;int width,height;
    float scale_x=1,scale_y=1;
    bool is_surface=false,dirty=false,pixmap_stale=false;
    unsigned mask=0;
    int texture_format=1,vx=0,vy=0,vw,vh;
    Texture *texture;View *texture_view;
    // SNAPSHOT_FIELDS
    WebGPUCanvas(Resources &r,int w,int h):res(&r),width(w),height(h),vw(w),vh(h){
        texture=new Texture{w,h,std::vector<unsigned>(w*h)};
        texture_view=new View{texture};
        for(int i=0;i<w*h;++i) texture->pixels[i]=(unsigned(i*137+41)&0xffffff)|((i%4)*0x55000000u);
    }
    ~WebGPUCanvas(){res->endPass();releaseBlitSnapshot();delete texture_view;delete texture;}
    int getWidth()const override{return width;}
    int getHeight()const override{return height;}
    int format(){return texture_format;}
    void getScale(float *x,float *y){*x=scale_x;*y=scale_y;}
    void uploadData(){res->endPass();dirty=false;blit_snapshot_valid=false;}
    void ensureTexture(){}
    View *sampleView(){return texture_view;}
    void blit(int,int,BBCanvas*,int,int,int,int,bool);
    void draw2d(int,bool,View *view,int,const float xywh[4],const float[3],
                const float[2],const BBWebGPUVertex *v,int count,unsigned mode,unsigned key){
        // Like draw2d, ordinary writes invalidate the snapshot. Keep draws
        // deferred until endPass so a copy must flush preceding writes.
        blit_snapshot_valid=false;
        assert(count==6);
        if(res->pass_target!=this) res->endPass();
        res->pass=true;res->pass_target=this;
        ++view->texture->refs;
        res->pending.push_back({this,view->texture,
            xywh[0]+v[0].x*xywh[2],xywh[1]+v[0].y*xywh[3],
            xywh[0]+v[2].x*xywh[2],xywh[1]+v[1].y*xywh[3],
            v[0].u,v[0].v,v[2].u,v[1].v,mode,key});
    }
};
void Resources::endPass(){
    for(const Draw &d:pending){
        auto &c=*d.target;
        int cx=c.vx,cy=c.vy,cw=c.vw,ch=c.vh;
        if(cw<=0 || ch<=0){cx=cy=0;cw=c.width;ch=c.height;}
        for(int y=0;y<c.height;++y) for(int x=0;x<c.width;++x){
            if(x<cx || y<cy || x>=cx+cw || y>=cy+ch ||
               x+0.5f<d.l || x+0.5f>=d.r || y+0.5f<d.t || y+0.5f>=d.b) continue;
            float u=d.u0+(x+0.5f-d.l)/(d.r-d.l)*(d.u1-d.u0);
            float v=d.v0+(y+0.5f-d.t)/(d.b-d.t)*(d.v1-d.v0);
            int sx=std::clamp(int(std::floor(u*d.source->w)),0,d.source->w-1);
            int sy=std::clamp(int(std::floor(v*d.source->h)),0,d.source->h-1);
            unsigned rgb=d.source->pixels[sy*d.source->w+sx]&0xffffff;
            if(d.mode==1 && rgb==(d.mask&0xffffff)) continue;
            c.texture->pixels[y*c.width+x]=rgb|0xff000000;
        }
        dropTexture(d.source);
    }
    pending.clear();pass=false;pass_target=nullptr;
}

// SELF_BLIT_METHODS

void reference(std::vector<unsigned> &pixels,int width,int height,int sx,int sy,int w,int h,
               int dx,int dy,bool solid,unsigned mask,int vx=0,int vy=0,int vw=0,int vh=0){
    const auto original=pixels;
    if(vw<=0 || vh<=0){vx=vy=0;vw=width;vh=height;}
    for(int y=0;y<h;++y) for(int x=0;x<w;++x){
        int tx=dx+x,ty=dy+y;
        if(tx<0 || ty<0 || tx>=width || ty>=height || tx<vx || ty<vy || tx>=vx+vw || ty>=vy+vh) continue;
        unsigned rgb=original[std::clamp(sy+y,0,height-1)*width+std::clamp(sx+x,0,width-1)]&0xffffff;
        if(!solid && rgb==(mask&0xffffff)) continue;
        pixels[ty*width+tx]=rgb|0xff000000;
    }
}

int main(){
    for(int step:{1,2,4}){
        Resources r;WebGPUCanvas c(r,128,128);
        auto expected=c.texture->pixels;
        int initial_allocations=allocations;
        for(int frame=0;frame<3;++frame){
            int initial_copies=copies;
            // Match the real inclusive water loops, including offscreen strips.
            for(int x=0;x<=128;x+=step){
                int dy=(x*5+frame)%7-3;
                c.blit(x,dy,&c,x,0,step,128,true);
                reference(expected,128,128,x,0,step,128,x,dy,true,0);
            }
            for(int y=0;y<=128;y+=step){
                int dx=(y*5+frame)%7-3;
                c.blit(dx,y,&c,0,y,128,step,true);
                reference(expected,128,128,0,y,128,step,dx,y,true,0);
            }
            r.endPass();
            assert(c.texture->pixels==expected);
            assert(copies-initial_copies==2);
        }
        assert(allocations-initial_allocations==1);
    }
    {
        Resources r;WebGPUCanvas c(r,32,32);
        auto expected=c.texture->pixels;
        uint32_t random=42;
        auto next=[&](int n){random=random*1664525+1013904223;return int((random>>8)%n);};
        // Dependent overlaps, negative/offscreen coordinates, viewport clipping,
        // RGB keys and nonopaque sources must preserve sequential copy results.
        for(int i=0;i<1500;++i){
            if(i%31==0){
                r.endPass();assert(c.texture->pixels==expected);
                int at=next(1024);expected[at]=c.texture->pixels[at]=next(0xffffff);
                c.dirty=true;
            }
            if(i%23==0){
                r.endPass();assert(c.texture->pixels==expected);
                c.vx=next(8);c.vy=next(8);c.vw=8+next(24);c.vh=8+next(24);
                c.texture_format=1+next(2);
            }
            int sx=next(36)-2,sy=next(36)-2,w=1+next(32),h=1+next(32);
            int dx=next(48)-8,dy=next(48)-8;bool solid=next(2);
            c.mask=expected[next(1024)]&0xffffff;
            c.blit(dx,dy,&c,sx,sy,w,h,solid);
            reference(expected,32,32,sx,sy,w,h,dx,dy,solid,c.mask,c.vx,c.vy,c.vw,c.vh);
        }
        r.endPass();assert(c.texture->pixels==expected);
        c.scale_x=2;
        assert(!c.blitSelf(0,0,0,0,4,4,true));
    }
    assert(allocations==releases);
    std::cout<<"Self-blit CPU fixture: pixel parity, overlap/clipping/colour keys, "
        <<"2 snapshots per water update, 1 retained texture, release balance passed\n";
}
