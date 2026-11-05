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
static std::vector<uint32_t> readWideNative(WebGPUContextResources &res,Target &target){
  res.flush();
  const uint32_t pitch=(target.getWidth()*4+255)&~255u;
  WGPUBufferDescriptor desc={};desc.size=pitch*target.getHeight();
  desc.usage=WGPUBufferUsage_CopyDst|WGPUBufferUsage_MapRead;
  auto buffer=wgpuDeviceCreateBuffer(res.device,&desc);
  WGPUTexelCopyTextureInfo source={};source.texture=target.texture;source.aspect=WGPUTextureAspect_All;
  WGPUTexelCopyBufferInfo dest={};dest.buffer=buffer;dest.layout.bytesPerRow=pitch;
  dest.layout.rowsPerImage=target.getHeight();
  WGPUExtent3D extent={uint32_t(target.getWidth()),uint32_t(target.getHeight()),1};
  wgpuCommandEncoderCopyTextureToBuffer(res.ensureEncoder(),&source,&dest,&extent);res.flush();
  struct Request{volatile bool done=false;bool ok=false;} request;
  WGPUBufferMapCallbackInfo callback={};callback.mode=BB_WGPU_CALLBACK_MODE;callback.userdata1=&request;
  callback.callback=[](WGPUMapAsyncStatus status,WGPUStringView,void *ctx,void*){
    auto &r=*static_cast<Request*>(ctx);r.ok=status==WGPUMapAsyncStatus_Success;r.done=true;
  };
  wgpuBufferMapAsync(buffer,WGPUMapMode_Read,0,desc.size,callback);bbWebGPUWait(&res,&request.done);
  std::vector<uint32_t> pixels;
  if(request.ok){
    auto *bytes=static_cast<const unsigned char*>(wgpuBufferGetConstMappedRange(buffer,0,desc.size));
    if(bytes){pixels.resize(size_t(target.getWidth())*target.getHeight());
      for(int y=0;y<target.getHeight();++y)std::memcpy(pixels.data()+y*target.getWidth(),bytes+y*pitch,target.getWidth()*4);
    }
    wgpuBufferUnmap(buffer);
  }
  wgpuBufferRelease(buffer);return pixels;
}
static void checkWidePixelWrites(WebGPUContextResources &res){
  Target target(&res,7,3,BBCanvas::CANVAS_TEX_VIDMEM);
  target.useWideFormat();target.setClsColor(0xff183040);target.cls();
  target.lock();target.setPixelFast(2,1,0xffc86432);target.unlock();
  expect(target.getPixel(2,1)==0xffc86432,"WritePixelFast updates the RGB10A2 framebuffer");
  expect(target.getPixel(0,0)==0xff183040,"framebuffer pixel writes retain surrounding pixels and pending clears");
  target.lock();target.lock();target.setPixelFast(6,2,0x55123456);target.unlock();
  expect(target.getPixelFast(6,2)==0x55123456,"nested locks retain the pending pixel edit");
  target.unlock();
  expect(target.getPixel(6,2)==0x55123456,"the final unlock uploads nested RGB10A2 edits and two-bit alpha");
  target.setPixel(0,2,0xaa765432);
  target.copyPixel(1,2,&target,0,2);
  expect(target.getPixel(1,2)==0xaa765432,"WritePixel and CopyPixel update wide framebuffers");

  // These values cannot round-trip through RGBA8. Read-only locks and an edit
  // elsewhere must leave every untouched ten-bit texel bit-for-bit intact.
  std::vector<uint32_t> native(21);
  for(size_t i=0;i<native.size();++i)native[i]=uint32_t((17+i*41)|((239+i*17)<<10)|((601+i*11)<<20)|(3u<<30));
  WGPUTexelCopyTextureInfo dst={};dst.texture=target.texture;dst.aspect=WGPUTextureAspect_All;
  WGPUTexelCopyBufferLayout layout={};layout.bytesPerRow=7*4;layout.rowsPerImage=3;
  WGPUExtent3D extent={7,3,1};res.flush();
  wgpuQueueWriteTexture(res.queue,&dst,native.data(),native.size()*4,&layout,&extent);
  target.lock();target.unlock();
  expect(readWideNative(res,target)==native,"read-only locks preserve all ten-bit framebuffer precision");
  target.setPixel(3,1,0xffc86432);
  native[10]=((200u*1023+127)/255)|(((100u*1023+127)/255)<<10)|(((50u*1023+127)/255)<<20)|(3u<<30);
  expect(readWideNative(res,target)==native,"pixel edits preserve the native bits of every untouched texel");
}
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
  checkWidePixelWrites(unsupported);
  unsupported.destroy();recorder().capture(false);return failures?1:0;
}
