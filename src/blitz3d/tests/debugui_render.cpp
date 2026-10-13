// Actual D3D12 ImGui output, using a hidden SDL window and offscreen canvas.
// No swapchain presentation, OS input, game resources or player data.
#include <bb/debugui/debugui.h>
#include <bb/graphics.webgpu/canvas.webgpu.h>
#include "imgui.h"
#include <cstdio>
#include <new>
#include <vector>

static int failures=0;
static void expect(bool ok,const char *name){
  std::printf("%s: %s\n",ok?"PASS":"FAIL",name);if(!ok)++failures;
}
static bool device(WebGPUContextResources &res){
  res.instance=wgpuCreateInstance(nullptr);bool ready=false;
  WGPURequestAdapterOptions options={};options.backendType=WGPUBackendType_D3D12;
  WGPURequestAdapterCallbackInfo adapter={};adapter.mode=WGPUCallbackMode_AllowProcessEvents;
  adapter.userdata1=&res;adapter.userdata2=&ready;
  adapter.callback=[](WGPURequestAdapterStatus status,WGPUAdapter value,WGPUStringView,void *ctx,void *done){
    if(status==WGPURequestAdapterStatus_Success)static_cast<WebGPUContextResources*>(ctx)->adapter=value;
    *static_cast<bool*>(done)=true;
  };
  wgpuInstanceRequestAdapter(res.instance,&options,adapter);
  while(!ready)wgpuInstanceProcessEvents(res.instance);if(!res.adapter)return false;
  ready=false;WGPUDeviceDescriptor desc={};
  desc.uncapturedErrorCallbackInfo.callback=[](const WGPUDevice*,WGPUErrorType,WGPUStringView message,void*,void*){
    std::fprintf(stderr,"GPU validation: %.*s\n",int(message.length),message.data);++failures;
  };
  WGPURequestDeviceCallbackInfo callback={};callback.mode=WGPUCallbackMode_AllowProcessEvents;
  callback.userdata1=&res;callback.userdata2=&ready;
  callback.callback=[](WGPURequestDeviceStatus status,WGPUDevice value,WGPUStringView,void *ctx,void *done){
    if(status==WGPURequestDeviceStatus_Success)static_cast<WebGPUContextResources*>(ctx)->device=value;
    *static_cast<bool*>(done)=true;
  };
  wgpuAdapterRequestDevice(res.adapter,&desc,callback);
  while(!ready)wgpuInstanceProcessEvents(res.instance);if(!res.device)return false;
  res.queue=wgpuDeviceGetQueue(res.device);res.ensurePipelineObjects();return true;
}
struct Target:WebGPUCanvas{
  using WebGPUCanvas::WebGPUCanvas;
  using WebGPUCanvas::readPixels;
};
static void iconPixels(WebGPUCanvas &icon,int red,int blue){
  std::vector<unsigned char> pixels(8*8*4);
  for(size_t i=0;i<pixels.size();i+=4){pixels[i]=red;pixels[i+2]=blue;pixels[i+3]=255;}
  // A color-key hole must show the button underneath rather than a black square.
  pixels[0]=pixels[2]=0;icon.uploadRGBA(pixels.data(),8,8);icon.setMask(0);
}
static void frame(const char *font,BBCanvas *icon){
  bbDebugUiOpen(1);bbDebugUiFrame(new BBStr("Unicode / icons"),new BBStr(font));
  if(bbDebugUiTab(new BBStr("Items"))){
    bbDebugUiText(new BBStr("\xed\x95\x9c\xea\xb8\x80 / \xc3\xbc / \xe6\x97\xa5"));
    bbDebugUiItemsBegin();bbDebugUiItem(new BBStr("Item##1"),icon);bbDebugUiItemsEnd();bbDebugUiTabEnd();
  }
  bbDebugUiFinish();
}
static std::vector<unsigned char> render(WebGPUContextResources &res,Target &target){
  target.setClsColor(0xff000000);target.cls();
  s2DebugUiRender(res.ensurePass(&target),512,384);res.endPass();res.flush();
  std::vector<unsigned char> pixels;target.readPixels(pixels);return pixels;
}
static int colored(const std::vector<unsigned char> &pixels,bool blue){
  int count=0;
  for(size_t i=0;i<pixels.size();i+=4)
    if(pixels[i+(blue?2:0)]>240 && pixels[i+(blue?0:2)]<10 && pixels[i+1]<10)++count;
  return count;
}
int main(int argc,char **argv){
  if(argc!=2 || SDL_Init(SDL_INIT_VIDEO)!=0)return 2;
  auto *window=SDL_CreateWindow("hidden ImGui check",0,0,512,384,SDL_WINDOW_HIDDEN);
  WebGPUContextResources res;if(!window || !device(res))return 2;
  s2DebugUiAttach(window,res.device,WGPUTextureFormat_RGBA8Unorm);
  {
    Target target(&res,512,384,BBCanvas::CANVAS_TEX_VIDMEM);
    alignas(WebGPUCanvas) unsigned char slot[sizeof(WebGPUCanvas)];
    auto *icon=new(slot) WebGPUCanvas(&res,8,8,BBCanvas::CANVAS_TEX_VIDMEM);
    iconPixels(*icon,255,0);frame(argv[1],icon);
    auto *font=ImGui::GetIO().FontDefault->GetFontBaked(17.0f);
    auto *glyph=font->FindGlyphNoFallback(0xd55c);
    expect(glyph && glyph->Visible && glyph->U1>glyph->U0,"Korean glyph rasterized into dynamic atlas");
    icon->~WebGPUCanvas(); // Draw data must remain usable until presentation.
    auto pixels=render(res,target);
    expect(colored(pixels,false)>1000,"icon survives source release before rendering");
    icon=new(slot) WebGPUCanvas(&res,8,8,BBCanvas::CANVAS_TEX_VIDMEM);
    iconPixels(*icon,0,255);frame(argv[1],icon);icon->~WebGPUCanvas();
    pixels=render(res,target);
    expect(colored(pixels,true)>1000 && colored(pixels,false)==0,"reused canvas address gets fresh icon pixels");
    icon=new(slot) WebGPUCanvas(&res,8,8,BBCanvas::CANVAS_TEX_VIDMEM);
    iconPixels(*icon,255,0);frame(argv[1],icon);
    s2DebugUiDetach(window);icon->~WebGPUCanvas();
    expect(!s2DebugUiVisible(),"device detach discards pending UI and cached icons");
  }
  res.destroy();SDL_DestroyWindow(window);SDL_Quit();return failures?1:0;
}
