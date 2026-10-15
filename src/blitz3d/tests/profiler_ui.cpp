// Offscreen UI with synthetic measurements: verifies layout, not rendering FPS.
#include <bb/debugui/debugui.h>
#include <bb/debugui/profiler.h>
#include <bb/graphics.webgpu/canvas.webgpu.h>
#include "imgui.h"
#include <fstream>
#include <string>
#include <vector>
#include <cstdlib>
extern bool blitz_create();
extern bool blitz_destroy();

static bool device(WebGPUContextResources &res){
  res.instance=wgpuCreateInstance(nullptr);bool done=false;
  WGPURequestAdapterOptions options={};
#ifdef BB_MACOS
  options.backendType=WGPUBackendType_Metal;
#else
  options.backendType=WGPUBackendType_D3D12;
#endif
  WGPURequestAdapterCallbackInfo adapter={};adapter.mode=BB_WGPU_CALLBACK_MODE;adapter.userdata1=&res;adapter.userdata2=&done;
  adapter.callback=[](WGPURequestAdapterStatus status,WGPUAdapter value,WGPUStringView,void *ctx,void *done){if(status==WGPURequestAdapterStatus_Success)static_cast<WebGPUContextResources*>(ctx)->adapter=value;*static_cast<bool*>(done)=true;};
  wgpuInstanceRequestAdapter(res.instance,&options,adapter);while(!done)wgpuInstanceProcessEvents(res.instance);if(!res.adapter)return false;
  done=false;WGPUDeviceDescriptor desc={};WGPURequestDeviceCallbackInfo callback={};callback.mode=BB_WGPU_CALLBACK_MODE;callback.userdata1=&res;callback.userdata2=&done;
  callback.callback=[](WGPURequestDeviceStatus status,WGPUDevice value,WGPUStringView,void *ctx,void *done){if(status==WGPURequestDeviceStatus_Success)static_cast<WebGPUContextResources*>(ctx)->device=value;*static_cast<bool*>(done)=true;};
  wgpuAdapterRequestDevice(res.adapter,&desc,callback);while(!done)wgpuInstanceProcessEvents(res.instance);if(!res.device)return false;
  res.queue=wgpuDeviceGetQueue(res.device);res.ensurePipelineObjects();return true;
}
struct Target:WebGPUCanvas {using WebGPUCanvas::WebGPUCanvas;using WebGPUCanvas::readPixels;};
int main(int argc,char **argv){
  if((argc!=4 && argc!=6) || SDL_Init(SDL_INIT_VIDEO)!=0)return 2;
  const int width=argc==6?std::atoi(argv[4]):1000,height=argc==6?std::atoi(argv[5]):970;
  blitz_create();
  auto *window=SDL_CreateWindow("profiler fixture",0,0,1024,1000,SDL_WINDOW_HIDDEN);
  WebGPUContextResources res;if(!window || !device(res))return 2;
  std::ifstream catalog(argv[2]);std::string line,title="Profiler";
  while(std::getline(catalog,line)){
    auto tab=line.find('\t');if(tab==std::string::npos)continue;
    auto key=line.substr(0,tab),value=line.substr(tab+1);
    if(key=="dev.profiler")title=value;
    if(key.rfind("dev.profiler.",0)==0)bbDebugUiProfileLabel(new BBStr(key.substr(13)),new BBStr(value));
  }
  using namespace s2prof;
  backend("offscreen WebGPU (synthetic UI fixture)");recorder().gpuSupport(true);gpuStatus("ready");recorder().capture(true);
  recorder().context("edition=classic resolution=1920x1080 fps_limit=144 shadows=1 water=1 antialias=1",1000.0/144);recorder().boundary(0);
  double time=0;
  for(int i=0;i<120;++i){
    auto id=recorder().frameId();double start=time;
    recorder().begin(Game,start);recorder().begin(Shadows,start+1);recorder().end(Shadows,start+2.5);recorder().end(Game,start+4);
    recorder().draw(Scene,4000,120);std::array<GpuSample,GpuCount> gpu{};
    gpu[Scene].ms=2.4;gpu[Scene].passes=1;gpu[ShadowNear].ms=0.5;gpu[ShadowNear].passes=1;
    recorder().gpuResult(id,gpu,GpuStatus::Ready);if(i==119)recorder().capture(false);time=start+7+(i==80?12:0);recorder().boundary(time);
  }
  recorder().capture(false);recorder().boundary(850);
  s2DebugUiAttach(window,res.device,WGPUTextureFormat_RGBA8Unorm);
  {
    Target target(&res,1024,1000,BBCanvas::CANVAS_TEX_VIDMEM);
    for(int i=0;i<3;++i){
      bbDebugUiOpen(1);bbDebugUiFrame(new BBStr("Profiler"),new BBStr(argv[1]));
      if(bbDebugUiTab(new BBStr(title))){bbDebugUiProfiler();bbDebugUiTabEnd();}bbDebugUiFinish();
      ImGui::SetWindowSize("Profiler###developer-menu",ImVec2(width,height));
      target.setClsColor(0xff101010);target.cls();s2DebugUiRender(res.ensurePass(&target),1024,1000);res.flush();
    }
    std::vector<unsigned char> pixels;target.readPixels(pixels);
    std::ofstream image(argv[3],std::ios::binary);image<<"P6\n1024 1000\n255\n";
    for(size_t i=0;i<pixels.size();i+=4)image.write(reinterpret_cast<const char*>(pixels.data()+i),3);
    auto *snapshot=bbDebugUiProfileSnapshot();std::ofstream report(std::string(argv[3])+".txt");report<<*snapshot;delete snapshot;
  }
  s2DebugUiDetach(window);res.destroy();SDL_DestroyWindow(window);SDL_Quit();blitz_destroy();return 0;
}
