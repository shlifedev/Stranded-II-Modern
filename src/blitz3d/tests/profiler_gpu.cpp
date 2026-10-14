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
int main(){
  using namespace s2prof;
  WebGPUContextResources res;if(!device(res,true))return 2;
  bool timestamps=wgpuDeviceHasFeature(res.device,WGPUFeatureName_TimestampQuery);
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
