#include <bb/debugui/profiler.h>
#include <bb/graphics.webgpu/canvas.webgpu.h>
#include "../src/modules/bb/blitz3d.webgpu/blitz3d.webgpu.cpp"
#include <bb/blitz3d/world.h>
#include <bb/blitz3d/meshmodel.h>
#include <cstdio>
#include <vector>

static int failures=0;
static void expect(bool value,const char *message){std::printf("%s: %s\n",value?"PASS":"FAIL",message);if(!value)++failures;}
static bool device(WebGPUContextResources &res,bool timestamps){
  res.instance=wgpuCreateInstance(nullptr);bool ready=false;
  WGPURequestAdapterOptions options={};
#ifdef BB_MACOS
  options.backendType=WGPUBackendType_Metal;
#else
  options.backendType=WGPUBackendType_D3D12;
#endif
  WGPURequestAdapterCallbackInfo adapter={};adapter.mode=BB_WGPU_CALLBACK_MODE;adapter.userdata1=&res;adapter.userdata2=&ready;
  adapter.callback=[](WGPURequestAdapterStatus status,WGPUAdapter adapter,WGPUStringView,void *ctx,void *done){
    if(status==WGPURequestAdapterStatus_Success)static_cast<WebGPUContextResources*>(ctx)->adapter=adapter;
    *static_cast<bool*>(done)=true;
  };
  wgpuInstanceRequestAdapter(res.instance,&options,adapter);
  while(!ready)wgpuInstanceProcessEvents(res.instance);if(!res.adapter)return false;
  ready=false;WGPUDeviceDescriptor desc={};WGPUFeatureName feature=WGPUFeatureName_TimestampQuery;
  if(timestamps && wgpuAdapterHasFeature(res.adapter,feature)){desc.requiredFeatures=&feature;desc.requiredFeatureCount=1;}
  desc.uncapturedErrorCallbackInfo.callback=[](const WGPUDevice*,WGPUErrorType,WGPUStringView message,void*,void*){
    std::fprintf(stderr,"GPU validation: %.*s\n",int(message.length),message.data);++failures;
  };
  WGPURequestDeviceCallbackInfo callback={};callback.mode=BB_WGPU_CALLBACK_MODE;callback.userdata1=&res;callback.userdata2=&ready;
  callback.callback=[](WGPURequestDeviceStatus status,WGPUDevice device,WGPUStringView,void *ctx,void *done){
    if(status==WGPURequestDeviceStatus_Success)static_cast<WebGPUContextResources*>(ctx)->device=device;
    *static_cast<bool*>(done)=true;
  };
  wgpuAdapterRequestDevice(res.adapter,&desc,callback);
  while(!ready)wgpuInstanceProcessEvents(res.instance);if(!res.device)return false;
  res.queue=wgpuDeviceGetQueue(res.device);res.ensurePipelineObjects();
  res.profiler=std::make_unique<WebGPUProfiler>(res.device);return true;
}
struct Target:WebGPUCanvas {using WebGPUCanvas::WebGPUCanvas;using WebGPUCanvas::readPixels;};
static void render(WebGPUContextResources &res,Target &target){
  target.setClsColor(0xff183040);target.cls();
  target.setColor(0xffb0a080);for(int i=0;i<60;++i)target.rect(0,0,target.getWidth(),target.getHeight(),true);
  res.finishProfile();
}
static void drain(WebGPUContextResources &res,uint64_t id){
  double deadline=s2prof::now()+5000;
  while(s2prof::recorder().find(id)->gpu==s2prof::GpuStatus::Pending && s2prof::now()<deadline){wgpuInstanceProcessEvents(res.instance);SDL_Delay(1);}
}
static void checkImageBatches(WebGPUContextResources &res,bool timestamps){
  using namespace s2prof;
  Target target(&res,512,256,BBCanvas::CANVAS_TEX_VIDMEM);
  Target atlas(&res,64,32,BBCanvas::CANVAS_TEX_VIDMEM),other(&res,64,32,BBCanvas::CANVAS_TEX_VIDMEM);
  std::vector<unsigned char> pixels(64*32*4);
  for(int y=0;y<32;++y)for(int x=0;x<64;++x){
    size_t p=(y*64+x)*4;bool key=(x+y)%5==0;
    pixels[p]=key?255:32+x*3;pixels[p+1]=key?0:48+y*5;pixels[p+2]=key?255:80;
    pixels[p+3]=(x%3)*127; // Image copies ignore alpha; only the RGB key discards.
  }
  atlas.uploadRGBA(pixels.data(),64,32);
  for(size_t p=0;p<pixels.size();p+=4)if(pixels[p+2]!=255)pixels[p+2]=220;
  other.uploadRGBA(pixels.data(),64,32);
  bool separate=false;
  auto blit=[&](int x,int y,WebGPUCanvas *src,int sx,int sy,int w,int h,bool solid=false){
    target.blit(x,y,src,sx,sy,w,h,solid);if(separate)res.endPass();
  };
  auto reset=[&]{
    atlas.setScale(1,1);other.setScale(1,1);atlas.setMask(0xffff00ff);other.setMask(0xffff00ff);
    target.setScale(1,1);target.setViewport(0,0,512,256);target.setClsColor(0xff183040);target.cls();
  };
  auto check=[&](const char *name,uint64_t draws,uint64_t triangles,auto commands){
    std::vector<unsigned char> reference,actual;
    separate=true;reset();commands();target.readPixels(reference);
    separate=false;recorder().capture(true);recorder().boundary(now());uint64_t id=recorder().frameId();
    reset();commands();res.finishProfile();recorder().capture(false);recorder().boundary(now());
    if(timestamps)drain(res,id);
    auto *frame=recorder().find(id);auto sample=frame->graphics[TwoD];target.readPixels(actual);
    uint64_t hash=14695981039346656037ull;for(auto byte:actual){hash^=byte;hash*=1099511628211ull;}
    std::printf("Image batch %s: draws=%llu triangles=%llu gpu_ms=%.6f pixels=%016llx\n",name,
      (unsigned long long)sample.draws,(unsigned long long)sample.triangles,sample.ms,(unsigned long long)hash);
    expect(reference==actual,name);
    if(draws)expect(sample.draws==draws,"image batch draw count preserves adjacent compatible runs");
    expect(sample.triangles==triangles,"image batches retain every triangle");
    if(timestamps)expect(frame->gpu==GpuStatus::Ready,"image batch GPU timestamps resolve without validation errors");
  };
  check("512 atlas glyphs with varying positions and sizes",1,1024,[&]{
    for(int i=0;i<512;++i)blit((i%32)*16,(i/32)*16,&atlas,(i%8)*8,(i%4)*8,3+i%6,7+i%3);
  });
  check("overlapping keyed and solid image copies",2,12,[&]{
    for(int i=0;i<3;++i)blit(2+i*5,3+i*4,&atlas,0,0,24,16);
    for(int i=0;i<3;++i)blit(8+i*5,9+i*4,&atlas,0,0,24,16,true);
  });
  check("texture A B A keeps its original order",3,6,[&]{
    blit(2,2,&atlas,0,0,24,16);blit(6,6,&other,0,0,24,16);blit(10,10,&atlas,0,0,24,16);
  });
  check("mask and viewport changes split image batches",3,6,[&]{
    blit(2,2,&atlas,0,0,24,16);atlas.setMask(0xff203050);
    blit(6,6,&atlas,0,0,24,16);target.setViewport(15,15,8,8);blit(10,10,&atlas,0,0,24,16);
  });
  check("scaled and clipped image copies",1,6,[&]{
    atlas.setScale(0.75f,1.5f);target.setScale(1.25f,0.75f);target.setViewport(3,5,80,50);
    blit(-7,-3,&atlas,2,1,24,16);blit(15,10,&atlas,4,2,13,11);blit(63,54,&atlas,2,1,21,19);
  });
  check("image handle offsets",1,4,[&]{
    atlas.setHandle(7,9);target.image(&atlas,20,20,false);if(separate)res.endPass();
    target.image(&atlas,35,30,false);if(separate)res.endPass();atlas.setHandle(0,0);
  });
  check("CopyRect and readback retain earlier image draws",0,10,[&]{
    blit(2,2,&atlas,0,0,24,16);blit(15,8,&atlas,8,4,16,12);
    blit(60,30,&target,0,0,32,24,true);
    std::vector<unsigned char> intermediate;target.readPixels(intermediate);
    blit(5,4,&atlas,16,8,24,16);blit(100,60,&target,60,30,32,24,true);
  });
  check("scaled self-copy snapshot",0,6,[&]{
    blit(2,2,&atlas,0,0,24,16);target.setScale(1.25f,0.75f);
    blit(30,30,&target,0,0,32,24,true);blit(65,60,&target,25,20,32,24,true);
  });
  check("image upload between draws preserves old sampled pixels",2,4,[&]{
    pixels[2]=80;atlas.uploadRGBA(pixels.data(),64,32);blit(2,2,&atlas,0,0,24,16);
    pixels[2]=220;atlas.uploadRGBA(pixels.data(),64,32);blit(30,2,&atlas,0,0,24,16);
  });
  // Restrict the usable arena without replacing its allocation. Every third
  // quad must submit the preceding pair before reusing their staged vertices.
  res.flush();size_t capacity=res.vertex_capacity;res.vertex_capacity=12*sizeof(BBWebGPUVertex);
  check("vertex arena rollover preserves image vertices",3,10,[&]{
    for(int i=0;i<5;++i)blit(2+i*12,2,&atlas,i*8,0,8,12);
  });
  res.vertex_capacity=capacity;recorder().clear();
}
int main(){
  using namespace s2prof;
  WebGPUContextResources res;if(!device(res,true))return 2;
  bool timestamps=wgpuDeviceHasFeature(res.device,WGPUFeatureName_TimestampQuery);
  checkImageBatches(res,timestamps);
  {
    Target target(&res,512,512,BBCanvas::CANVAS_TEX_VIDMEM);
    std::vector<unsigned char> off,on;
    render(res,target);target.readPixels(off);
    recorder().capture(true);recorder().boundary(now());uint64_t id=recorder().frameId();
    render(res,target);recorder().boundary(now());
    if(timestamps)drain(res,id);
    target.readPixels(on);expect(off==on,"profiling retains actual rendered pixels");
    expect(recorder().find(id)->graphics[TwoD].draws>0,"counts encoded 2D draws");
    if(timestamps){
      auto *frame=recorder().find(id);
      std::printf("Measured GPU: status=%d, %.6f ms, %u passes\n",int(frame->gpu),frame->graphics[TwoD].ms,frame->graphics[TwoD].passes);
      expect(frame->gpu==GpuStatus::Ready && frame->graphics[TwoD].ms>0 && frame->graphics[TwoD].passes>0,"actual GPU timestamps return to the correct frame");
      {
        WebGPUScene scene(&res);bbScene=&scene;gx_canvas=&target;
        {
        World world;Camera camera;Light sun(Light::LIGHT_DISTANT);MeshModel ground;
        camera.setViewport(0,0,512,512);camera.setCanvas(&target);camera.setRange(1,100);
        camera.setLocalPosition(Vector(0,6,-8));camera.setLocalRotation(rotationQuat(0.35f,0,0));
        sun.setLocalRotation(rotationQuat(0.8f,-1.57f,0));
        auto *surface=ground.createSurface(Brush());
        for(Vector p:{Vector(-30,0,-30),Vector(30,0,-30),Vector(30,0,30),Vector(-30,0,30)}){
          Surface::Vertex v;v.coords=p;v.normal=Vector(0,1,0);surface->addVertex(v);
        }
        surface->addTriangle(Surface::Triangle{{0,2,1}});surface->addTriangle(Surface::Triangle{{0,3,2}});
        ground.setFX(BBScene::FX_DOUBLESIDED);
        WebGPUCanvas reflection(&res,64,64,BBCanvas::CANVAS_TEXTURE|BBCanvas::CANVAS_TEX_RGB);
        id=recorder().frameId();world.renderShadows(&camera,&sun,80,1);
        world.renderPlanar(&camera,&reflection,0,1);world.render(1);res.finishProfile();recorder().boundary(now());drain(res,id);
        auto *f=recorder().find(id);
        expect(f->gpu==GpuStatus::Ready && f->graphics[ShadowNear].passes>0 && f->graphics[ShadowFar].passes>0 &&
          f->graphics[Reflections].passes>0 && f->graphics[Scene].passes>0,"actual shadow, reflection and main passes are attributed separately");
        expect(f->graphics[ShadowNear].draws>0 && f->graphics[ShadowFar].draws>0 && f->graphics[Reflections].triangles>0,"3D statistics count encoded shadow and reflection draws");
        id=recorder().frameId();world.renderShadows(&camera,nullptr,0,1);world.render(1);res.finishProfile();recorder().boundary(now());drain(res,id);
        f=recorder().find(id);expect(f->graphics[ShadowNear].passes==0 && f->graphics[ShadowFar].draws==0 && f->graphics[Reflections].passes==0 && f->graphics[Scene].draws>0,"disabling captures removes their measurements without stale results");
        }
        bbScene=nullptr;gx_canvas=nullptr;
      }
      std::vector<uint64_t> ids;
      for(int i=0;i<5;++i){ids.push_back(recorder().frameId());render(res,target);recorder().boundary(now());}
      expect(recorder().find(ids.back())->gpu==GpuStatus::Skipped,"busy readback ring skips without waiting");
      for(auto id:ids)drain(res,id);
      id=recorder().frameId();
      for(int i=0;i<514;++i){target.cls();res.endPass();}
      res.finishProfile();recorder().boundary(now());drain(res,id);
      expect(recorder().find(id)->gpu==GpuStatus::Partial,"timestamp capacity reports a partial result");
      id=recorder().frameId();render(res,target);recorder().boundary(now());
      res.profiler.reset();expect(recorder().find(id)->gpu==GpuStatus::Failed,"detach invalidates outstanding GPU results");
      for(int i=0;i<100;++i){wgpuInstanceProcessEvents(res.instance);SDL_Delay(1);}
      expect(recorder().find(id)->gpu==GpuStatus::Failed,"late callback cannot revive a detached measurement");
    }else std::puts("SKIP: timestamp-query unavailable; CPU/draw fallback verified");
  }
  res.destroy();recorder().clear();
  WebGPUContextResources unsupported;if(!device(unsupported,false))return 2;
  {Target target(&unsupported,32,32,BBCanvas::CANVAS_TEX_VIDMEM);recorder().boundary(now());uint64_t id=recorder().frameId();render(unsupported,target);recorder().boundary(now());
    expect(recorder().find(id)->gpu==GpuStatus::Unsupported && recorder().find(id)->graphics[TwoD].draws>0,"device without timestamp feature retains draw statistics");}
  unsupported.destroy();recorder().capture(false);return failures?1:0;
}
