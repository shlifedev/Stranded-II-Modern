#include "debugui.h"
#include "profiler.h"
#include "benchmark.h"
#include <bb/blitz/app.h>
#include "imgui.h"
#include <bb/graphics/canvas.h>
#include <bb/runtime.sdl/scancodes.cpp>
#include "widgets.h"
#include "imgui_impl_sdl2.h"
#include "imgui_impl_wgpu.h"
#include "imgui_stdlib.h"
#include <algorithm>
#include <cctype>
#include <deque>
#include <mutex>
#include <string>
#include <cstdio>
#include <unordered_map>
#include <vector>
#ifdef BB_EMSCRIPTEN
#include <emscripten.h>
EM_JS(void,s2DebugUiBrowserState,(int open),{
  Module.s2DebugUiSetOpen?.(!!open);
});
EM_JS(void,s2DebugUiBrowserIme,(int visible,float x,float y,float height),{
  Module.s2DebugUiIme?.(!!visible,x,y,height);
});
#endif
namespace {
SDL_Window *window=nullptr;
WGPUDevice device=nullptr;
WGPUTextureFormat format=WGPUTextureFormat_Undefined;
ImGuiContext *context=nullptr;
bool visible=false,building=false,drawReady=false,tabs=false,changed=false;
int generation=0;
int hotkey=0;
std::string loadedFont;
bool items=false;
s2debugui::LogScroll logScroll;
std::unordered_map<BBCanvas*,ImTextureData*> icons;
std::vector<ImTextureData*> retiredIcons;
void releaseIcon(ImTextureData *icon){
  icon->WantDestroyNextFrame=true;icon->UnusedFrames=1;
  icon->SetStatus(ImTextureStatus_WantDestroy);
  ImGui_ImplWGPU_UpdateTexture(icon);IM_DELETE(icon);
}
void collectIcons(){for(auto *icon:retiredIcons)releaseIcon(icon);retiredIcons.clear();}
ImTextureData *itemIcon(BBCanvas *canvas){
  if(!canvas || !context)return nullptr;
  auto found=icons.find(canvas);if(found!=icons.end())return found->second;
  int width=canvas->getWidth(),height=canvas->getHeight();
  if(width<=0 || height<=0 || width>1024 || height>1024 || !canvas->lock())return nullptr;
  auto *icon=IM_NEW(ImTextureData)();icon->Create(ImTextureFormat_RGBA32,width,height);
  auto *pixels=static_cast<unsigned char*>(icon->GetPixels());
  unsigned mask=canvas->getMask()&0xffffff;
  for(int y=0;y<height;++y)for(int x=0;x<width;++x){
    unsigned color=canvas->getPixelFast(x,y);size_t offset=(size_t(y)*width+x)*4;
    pixels[offset]=static_cast<unsigned char>(color>>16);
    pixels[offset+1]=static_cast<unsigned char>(color>>8);
    pixels[offset+2]=static_cast<unsigned char>(color);
    pixels[offset+3]=(color&0xffffff)==mask?0:static_cast<unsigned char>(color>>24);
  }
  canvas->unlock();icon->SetStatus(ImTextureStatus_WantCreate);
  ImGui_ImplWGPU_UpdateTexture(icon);icons.emplace(canvas,icon);return icon;
}
struct Entry { Uint64 time; std::string source,text; int level; };
std::deque<Entry> history;
std::mutex historyMutex;
size_t historyBytes=0;
std::string consume(BBStr *value){std::string result=value?*value:"";delete value;return result;}
void append(std::string source,int level,std::string text){
  if(text.size()>65536){size_t end=65536;while(end && (static_cast<unsigned char>(text[end])&0xc0)==0x80)--end;text.resize(end);}
  std::lock_guard<std::mutex> lock(historyMutex);
  historyBytes+=source.size()+text.size();
  history.push_back({SDL_GetTicks64(),std::move(source),std::move(text),level});
  while(history.size()>2048 || historyBytes>2*1024*1024){historyBytes-=history.front().source.size()+history.front().text.size();history.pop_front();}
}
void runtimeLog(int level,const char *text){append("runtime",level,text?text:"");}
std::string lower(std::string s){for(char &c:s) if(static_cast<unsigned char>(c)<128)c=char(std::tolower(static_cast<unsigned char>(c)));return s;}
bool matches(const Entry &e,const std::string &search,int level){return (level<0 || e.level==level) && (search.empty() || lower(e.source+" "+e.text).find(search)!=std::string::npos);}
void setVisible(bool value){
  if(visible==value)return;
  visible=value;++generation;
  if(window){SDL_SetRelativeMouseMode(SDL_FALSE);SDL_ShowCursor(SDL_ENABLE);}
  if(context){ImGui::SetCurrentContext(context);ImGui::GetIO().ClearEventsQueue();ImGui::GetIO().ClearInputKeys();ImGui::GetIO().ClearInputMouse();}
#ifdef BB_EMSCRIPTEN
  s2DebugUiBrowserState(visible);
  if(!visible)s2DebugUiBrowserIme(0,0,0,0);
#endif
}
#ifdef BB_EMSCRIPTEN
void browserIme(ImGuiContext*,ImGuiViewport*,ImGuiPlatformImeData *data){s2DebugUiBrowserIme(visible && data->WantVisible,data->InputPos.x,data->InputPos.y,data->InputLineHeight);}
#endif
bool initialize(){
  if(context)return true;
  if(!window || !device)return false;
  IMGUI_CHECKVERSION();context=ImGui::CreateContext();
  ImGui::StyleColorsDark();auto &io=ImGui::GetIO();io.IniFilename=nullptr;io.LogFilename=nullptr;
  io.ConfigFlags|=ImGuiConfigFlags_NavEnableKeyboard;
  io.ConfigFlags|=ImGuiConfigFlags_NoMouseCursorChange;
  if(!ImGui_ImplSDL2_InitForOther(window)){ImGui::DestroyContext(context);context=nullptr;return false;}
  ImGui_ImplWGPU_InitInfo info;info.Device=device;info.RenderTargetFormat=format;info.NumFramesInFlight=3;
  if(!ImGui_ImplWGPU_Init(&info)){ImGui_ImplSDL2_Shutdown();ImGui::DestroyContext(context);context=nullptr;return false;}
#ifdef BB_EMSCRIPTEN
  ImGui::GetPlatformIO().Platform_SetImeDataFn=browserIme;
#endif
  return true;
}
}
void s2DebugUiAttach(SDL_Window *w,WGPUDevice d,WGPUTextureFormat f){
  if(window && (window!=w || device!=d))s2DebugUiDetach(window);
  window=w;device=d;format=f;bbSetLogHandler(runtimeLog);
}
void s2DebugUiDetach(SDL_Window *w){
  if(w!=window)return;
  setVisible(false);
  if(context){ImGui::SetCurrentContext(context);if(building)ImGui::EndFrame();drawReady=false;collectIcons();for(auto &entry:icons)releaseIcon(entry.second);icons.clear();ImGui_ImplWGPU_Shutdown();ImGui_ImplSDL2_Shutdown();ImGui::DestroyContext(context);context=nullptr;}
  building=drawReady=tabs=items=false;logScroll={};window=nullptr;device=nullptr;loadedFont.clear();bbSetLogHandler(nullptr);
}
bool s2DebugUiVisible(){return visible;}
void s2DebugUiResetInput(){
  if(context){ImGui::SetCurrentContext(context);ImGui::GetIO().ClearEventsQueue();ImGui::GetIO().ClearInputKeys();ImGui::GetIO().ClearInputMouse();}
}
bool s2DebugUiEvent(const SDL_Event &event){
  int key=0;
  if(event.type==SDL_KEYDOWN && !event.key.repeat){
    auto code=event.key.keysym.scancode;
    if(code>=0 && code<MAX_SDL_SCANCODES)key=SDL_SCANCODE_MAP[code];
  }else if(event.type==SDL_MOUSEBUTTONDOWN){
    switch(event.button.button){case SDL_BUTTON_LEFT:key=256;break;case SDL_BUTTON_RIGHT:key=257;break;case SDL_BUTTON_MIDDLE:key=258;break;case SDL_BUTTON_X1:key=259;break;case SDL_BUTTON_X2:key=260;break;}
  }else if(event.type==SDL_MOUSEWHEEL && event.wheel.y)key=event.wheel.y>0?261:262;
  if(hotkey!=0 && key==hotkey){setVisible(!visible);return true;}
  if(context){ImGui::SetCurrentContext(context);ImGui_ImplSDL2_ProcessEvent(&event);}
  if(event.type==SDL_WINDOWEVENT && (event.window.event==SDL_WINDOWEVENT_FOCUS_LOST || event.window.event==SDL_WINDOWEVENT_MINIMIZED)){
    s2DebugUiResetInput();s2prof::recorder().invalidate();s2bench::invalidate("focus_lost");
  }
  return visible && (event.type==SDL_KEYDOWN || event.type==SDL_KEYUP || event.type==SDL_TEXTINPUT || event.type==SDL_TEXTEDITING || event.type==SDL_MOUSEMOTION || event.type==SDL_MOUSEWHEEL || event.type==SDL_MOUSEBUTTONDOWN || event.type==SDL_MOUSEBUTTONUP);
}
void s2DebugUiRender(WGPURenderPassEncoder pass,int width,int height){
  if(!context || !drawReady)return;
  ImGui::SetCurrentContext(context);
  auto *data=ImGui::GetDrawData();
  if(data && data->DisplaySize.x>0 && data->DisplaySize.y>0){
    data->FramebufferScale=ImVec2(float(width)/data->DisplaySize.x,float(height)/data->DisplaySize.y);
    if(pass){
      ImGui_ImplWGPU_RenderDrawData(data,pass);
      if(s2prof::recorder().active())for(auto *commands:data->CmdLists)for(const auto &cmd:commands->CmdBuffer){
        if(cmd.UserCallback || !cmd.ElemCount)continue;
        float x0=(cmd.ClipRect.x-data->DisplayPos.x)*data->FramebufferScale.x;
        float y0=(cmd.ClipRect.y-data->DisplayPos.y)*data->FramebufferScale.y;
        float x1=(cmd.ClipRect.z-data->DisplayPos.x)*data->FramebufferScale.x;
        float y1=(cmd.ClipRect.w-data->DisplayPos.y)*data->FramebufferScale.y;
        if(std::min(x1,float(width))>std::max(x0,0.0f) && std::min(y1,float(height))>std::max(y0,0.0f))s2prof::draw(s2prof::Present,cmd.ElemCount/3);
      }
    }
  }
  drawReady=false;
}
#ifdef BB_EMSCRIPTEN
extern "C" EMSCRIPTEN_KEEPALIVE void s2BenchmarkFocusLost(){s2prof::recorder().invalidate();s2bench::invalidate("focus_lost");}
extern "C" EMSCRIPTEN_KEEPALIVE void s2DebugUiCommit(const char *text){if(context && visible){ImGui::SetCurrentContext(context);ImGui::GetIO().AddInputCharactersUTF8(text);}}
#endif
bb_int_t BBCALL bbDebugUiOpen(bb_int_t value){if(value>=0)setVisible(value!=0);return visible;}
bb_int_t BBCALL bbDebugUiHotkey(bb_int_t value){if(value>=0)hotkey=std::min(int(value),262);return hotkey;}
bb_int_t BBCALL bbDebugUiGeneration(){return generation;}
bb_int_t BBCALL bbDebugUiReady(){return window && device;}
bb_int_t BBCALL bbDebugUiFrame(BBStr *title,BBStr *font){
  auto label=consume(title),path=consume(font);changed=false;
  if(!visible || building || !initialize())return 0;
  ImGui::SetCurrentContext(context);
  drawReady=false;collectIcons();
  if(path!=loadedFont){loadedFont=path;s2debugui::loadFonts(path);}
  ImGui_ImplWGPU_NewFrame();ImGui_ImplSDL2_NewFrame();ImGui::NewFrame();building=true;
  auto size=ImGui::GetIO().DisplaySize;
  ImGui::SetNextWindowSize(ImVec2(std::min(720.0f,size.x-20),std::min(660.0f,size.y-20)),ImGuiCond_FirstUseEver);
  ImGui::SetNextWindowPos(ImVec2(10,10),ImGuiCond_FirstUseEver);
  bool open=visible;bool expanded=ImGui::Begin(label.c_str(),&open);
  if(!open)setVisible(false);
  tabs=expanded && ImGui::BeginTabBar("developer-tabs");return 1;
}
void BBCALL bbDebugUiFinish(){if(!building)return;if(tabs)ImGui::EndTabBar();ImGui::End();ImGui::Render();building=false;tabs=false;drawReady=true;}
bb_int_t BBCALL bbDebugUiTab(BBStr *label){auto s=consume(label);return building && tabs && ImGui::BeginTabItem(s.c_str());}
void BBCALL bbDebugUiTabEnd(){if(building)ImGui::EndTabItem();}
void BBCALL bbDebugUiText(BBStr *text){auto s=consume(text);if(building)ImGui::TextWrapped("%s",s.c_str());}
bb_int_t BBCALL bbDebugUiButton(BBStr *label){auto s=consume(label);return building && ImGui::Button(s.c_str());}
bb_int_t BBCALL bbDebugUiCheck(BBStr *label,bb_int_t value){auto s=consume(label);bool v=value!=0;changed=building && ImGui::Checkbox(s.c_str(),&v);return v;}
bb_int_t BBCALL bbDebugUiInt(BBStr *label,bb_int_t value,bb_int_t minimum,bb_int_t maximum){auto s=consume(label);int v=value;changed=building && ImGui::SliderInt(s.c_str(),&v,minimum,maximum);return v;}
bb_float_t BBCALL bbDebugUiFloat(BBStr *label,bb_float_t value,bb_float_t minimum,bb_float_t maximum){auto s=consume(label);float v=value;changed=building && s2debugui::floatControl(s.c_str(),&v,minimum,maximum);return v;}
BBStr *BBCALL bbDebugUiInput(BBStr *label,BBStr *value,bb_int_t multiline){auto s=consume(label),v=consume(value);changed=false;if(building){if(multiline)changed=ImGui::InputTextMultiline(s.c_str(),&v,ImVec2(-1,180));else changed=ImGui::InputText(s.c_str(),&v);}return new BBStr(v);}
bb_int_t BBCALL bbDebugUiChanged(){return changed;}
void BBCALL bbDebugUiSameLine(){if(building)ImGui::SameLine();}
void BBCALL bbDebugUiDisabled(bb_int_t value){if(building)ImGui::BeginDisabled(value!=0);}
void BBCALL bbDebugUiDisabledEnd(){if(building)ImGui::EndDisabled();}
void BBCALL bbDebugUiLog(BBStr *source,bb_int_t level,BBStr *text){auto s=consume(source),t=consume(text);append(std::move(s),level,std::move(t));}
bb_int_t BBCALL bbDebugUiLogs(BBStr *filter,bb_int_t level,bb_int_t follow){auto search=lower(consume(filter));if(!building)return follow;std::deque<Entry> copy;{std::lock_guard<std::mutex> lock(historyMutex);copy=history;}
  ImGui::BeginChild("log-history",ImVec2(0,-1),ImGuiChildFlags_Borders);
  bool track=logScroll.update(follow!=0,ImGui::GetScrollY(),ImGui::GetScrollMaxY());
  for(const auto &e:copy)if(matches(e,search,level)){if(e.level==2)ImGui::PushStyleColor(ImGuiCol_Text,ImVec4(1,.45f,.45f,1));ImGui::TextWrapped("[%llu][%s] %s",static_cast<unsigned long long>(e.time),e.source.c_str(),e.text.c_str());if(e.level==2)ImGui::PopStyleColor();}
  if(track)ImGui::SetScrollHereY(1.0f);ImGui::EndChild();return logScroll.follow;
}
void BBCALL bbDebugUiClearLogs(){std::lock_guard<std::mutex> lock(historyMutex);history.clear();historyBytes=0;}
void BBCALL bbDebugUiCopyLogs(BBStr *filter,bb_int_t level){auto search=lower(consume(filter));std::string text;{std::lock_guard<std::mutex> lock(historyMutex);for(const auto &e:history)if(matches(e,search,level))text+="["+e.source+"] "+e.text+"\n";}if(context)ImGui::SetClipboardText(text.c_str());}
bb_int_t BBCALL bbDebugUiTextActive(){return context && visible && ImGui::GetIO().WantTextInput;}

bb_int_t BBCALL bbDebugUiSection(BBStr *label){auto s=consume(label);return building && ImGui::CollapsingHeader(s.c_str());}

bool debugui_create(){return true;}
bool debugui_destroy(){if(window)s2DebugUiDetach(window);return true;}

// Retire after the last draw data using this icon has been rendered or discarded.
void s2DebugUiForgetCanvas(BBCanvas *canvas){
  auto found=icons.find(canvas);if(found==icons.end())return;
  retiredIcons.push_back(found->second);icons.erase(found);
}
bb_int_t BBCALL bbDebugUiItemsBegin(){
  if(!building)return 0;
  int columns=std::max(1,int(ImGui::GetContentRegionAvail().x/92.0f));
  items=ImGui::BeginTable("item-grid",columns,ImGuiTableFlags_SizingStretchSame);
  return items;
}
bb_int_t BBCALL bbDebugUiItem(BBStr *label,BBCanvas *canvas){
  auto name=consume(label);if(!building || !items)return 0;
  ImGui::TableNextColumn();ImGui::PushID(name.c_str());
  float width=ImGui::GetContentRegionAvail().x;auto pos=ImGui::GetCursorScreenPos();
  bool clicked=ImGui::InvisibleButton("give",ImVec2(width,84));
  if(ImGui::IsItemVisible()){
    auto *draw=ImGui::GetWindowDrawList();
    auto color=ImGui::IsItemActive()?ImGuiCol_ButtonActive:ImGui::IsItemHovered()?ImGuiCol_ButtonHovered:ImGuiCol_Button;
    draw->AddRectFilled(pos,ImVec2(pos.x+width,pos.y+84),ImGui::GetColorU32(color),4);
    if(auto *icon=itemIcon(canvas)){
      float scale=48.0f/std::max(icon->Width,icon->Height),w=icon->Width*scale,h=icon->Height*scale;
      ImVec2 start(pos.x+(width-w)*.5f,pos.y+6+(48-h)*.5f);
      draw->AddImage(icon->GetTexRef(),start,ImVec2(start.x+w,start.y+h));
    }
    auto end=name.find("##");std::string caption=name.substr(0,end);
    ImVec4 clip(pos.x+4,pos.y+60,pos.x+width-4,pos.y+82);
    draw->AddText(ImGui::GetFont(),ImGui::GetFontSize(),ImVec2(pos.x+4,pos.y+60),ImGui::GetColorU32(ImGuiCol_Text),caption.c_str(),nullptr,0,&clip);
    if(ImGui::IsItemHovered())ImGui::SetTooltip("%s",caption.c_str());
  }
  ImGui::PopID();return clicked;
}
void BBCALL bbDebugUiItemsEnd(){if(building && items){ImGui::EndTable();items=false;}}

bb_int_t BBCALL bbDebugUiListBegin(bb_float_t height){if(!building)return 0;ImGui::BeginChild("benchmark-catalog",ImVec2(0,height),ImGuiChildFlags_Borders);return 1;}
void BBCALL bbDebugUiListEnd(){if(building)ImGui::EndChild();}
