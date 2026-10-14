#include "profiler.h"
#include "benchmark.h"
#include "debugui.h"
#include "imgui.h"
#include <SDL.h>
#include <iomanip>
#include <sstream>
#include <unordered_map>
#ifdef BB_EMSCRIPTEN
#include <emscripten.h>
#endif

namespace s2prof {
namespace {
Recorder data;
std::unordered_map<std::string,std::string> labels;
std::string gpuReason="unsupported",backendName;
uint64_t selected=0;
int currentRenderGroup=Scene;
const char *cpuKeys[]={"cpu.game","cpu.physics","cpu.objects","cpu.grass","cpu.shadows","cpu.water","cpu.reflection","cpu.main","cpu.post","cpu.hud","cpu.ui","cpu.pace","cpu.flip"};
const char *gpuKeys[]={"gpu.scene","gpu.near","gpu.far","gpu.reflection","gpu.2d","gpu.present","gpu.mips"};
const char *statusKey(GpuStatus status){
  switch(status){case GpuStatus::Pending:return "pending";case GpuStatus::Ready:return "ready";
    case GpuStatus::Skipped:return "skipped";case GpuStatus::Partial:return "partial";case GpuStatus::Failed:return "failed";
    default:return "unsupported";}
}
const char *tr(const char *key){auto it=labels.find(key);return it==labels.end()?key:it->second.c_str();}
Stats frameStats(const std::vector<const Frame*> &history,uint64_t segment){
  std::vector<double> values;for(auto *f:history)if(f->valid && f->segment==segment)values.push_back(f->ms);return statistics(std::move(values));
}
std::string report(const Frame &frame,const std::vector<const Frame*> &history){
  auto stats=frameStats(history,frame.segment);
  std::ostringstream out;out<<std::fixed<<std::setprecision(3);
  out<<"Stranded II profiler / "<<backendName<<"\n"<<frame.settings<<"\nframe="<<frame.id
     <<" segment="<<frame.segment<<" valid="<<frame.valid<<" frame_ms="<<frame.ms<<" budget_ms="<<frame.budget
     <<"\nsamples="<<stats.count<<" average_ms="<<stats.average<<" p95_ms="<<stats.p95<<" maximum_ms="<<stats.maximum
     <<"\nGPU="<<statusKey(frame.gpu)<<" ("<<gpuReason<<")\n";
  for(int i=0;i<CpuCount;++i)out<<tr(cpuKeys[i])<<": total_ms="<<frame.cpu[i].total<<" self_ms="<<frame.cpu[i].self<<" calls="<<frame.cpu[i].calls<<"\n";
  double total=0;
  for(int i=0;i<GpuCount;++i){auto &s=frame.graphics[i];total+=s.ms;
    out<<tr(gpuKeys[i])<<": ";
    if(frame.gpu==GpuStatus::Ready || frame.gpu==GpuStatus::Partial)out<<"gpu_ms="<<s.ms;else out<<"gpu_ms=unavailable";
    out<<" passes="<<s.passes<<" draws="<<s.draws<<" triangles="<<s.triangles<<" instances="<<s.instances<<"\n";
  }
  if(frame.gpu==GpuStatus::Ready || frame.gpu==GpuStatus::Partial)out<<"GPU render-pass sum_ms="<<total<<" (excludes transfers and gaps)\n";
  return out.str();
}
}
Recorder &recorder(){return data;}
double now(){
#ifdef BB_EMSCRIPTEN
  return emscripten_get_now();
#else
  return double(SDL_GetPerformanceCounter())*1000.0/double(SDL_GetPerformanceFrequency());
#endif
}
void gpuStatus(const char *value){gpuReason=value;}
void backend(const char *name){backendName=name;}
void draw(int group,uint64_t triangles,uint64_t instances){if(data.active())data.draw(group,triangles,instances);}
int renderGroup(){return currentRenderGroup;}
RenderGroup::RenderGroup(int value):previous(currentRenderGroup){currentRenderGroup=value;}
RenderGroup::~RenderGroup(){currentRenderGroup=previous;}
const char *label(const char *key){return tr(key);}
void panel(){
  ImGui::BeginDisabled(s2bench::busy());
  if(ImGui::Button(tr(data.capturing()?"stop":"start")))data.capture(!data.capturing());
  ImGui::SameLine();if(ImGui::Button(tr("clear"))){data.clear();selected=0;}
  ImGui::EndDisabled();
  auto history=data.history();
  ImGui::SameLine();ImGui::BeginDisabled(history.empty());
  bool copy=ImGui::Button(tr("copy"));ImGui::EndDisabled();
  ImGui::TextWrapped("%s",tr("help"));
  ImGui::Text("%s: %s",tr("backend"),backendName.c_str());
  if(history.empty()){ImGui::TextWrapped("%s",tr("empty"));return;}
  const Frame *frame=data.find(selected);
  if(!frame || !frame->complete)frame=history.back();
  auto stats=frameStats(history,frame->segment);
  auto *latest=history.back();
  ImGui::Text("FPS %.1f / %.3f ms",latest->ms>0?1000.0/latest->ms:0.0,latest->ms);
  ImGui::Text("%s: %.3f / %.3f / %.3f ms (%zu)",tr("statistics"),stats.average,stats.p95,stats.maximum,stats.count);
  float maximum=10;
  for(auto *f:history)maximum=std::max(maximum,float(std::max(f->ms,f->budget)));
  const ImVec2 size(std::max(100.0f,ImGui::GetContentRegionAvail().x),140);
  const auto origin=ImGui::GetCursorScreenPos();
  ImGui::InvisibleButton("##profiler-graph",size);
  auto *list=ImGui::GetWindowDrawList();list->AddRectFilled(origin,ImVec2(origin.x+size.x,origin.y+size.y),IM_COL32(25,28,32,255));
  auto point=[&](size_t i,double ms){return ImVec2(origin.x+size.x*float(i)/float(std::max(size_t(1),history.size()-1)),origin.y+size.y-size.y*float(ms)/maximum);};
  for(size_t i=1;i<history.size();++i){
    list->AddLine(point(i-1,history[i-1]->ms),point(i,history[i]->ms),history[i]->valid?IM_COL32(80,200,140,255):IM_COL32(180,100,100,255));
    if(history[i-1]->segment!=history[i]->segment){auto p=point(i,0);list->AddLine(ImVec2(p.x,origin.y),p,IM_COL32(230,180,70,200));}
  }
  if(frame->budget>0){float y=point(0,frame->budget).y;list->AddLine(ImVec2(origin.x,y),ImVec2(origin.x+size.x,y),IM_COL32(220,180,60,150));}
  if(ImGui::IsItemHovered()){
    float x=std::clamp((ImGui::GetIO().MousePos.x-origin.x)/size.x,0.0f,1.0f);
    auto *hover=history[size_t(x*float(history.size()-1))];
    ImGui::SetTooltip("#%llu: %.3f ms",static_cast<unsigned long long>(hover->id),hover->ms);
    if(ImGui::IsMouseClicked(ImGuiMouseButton_Left)){selected=hover->id;frame=hover;}
  }
  for(size_t i=0;i<history.size();++i)if(history[i]->id==frame->id){auto p=point(i,0);list->AddLine(ImVec2(p.x,origin.y),p,IM_COL32(120,170,255,255));break;}
  if(ImGui::Button(tr("latest")))selected=0;
  ImGui::SameLine();ImGui::Text("#%llu / %.3f ms",static_cast<unsigned long long>(frame->id),frame->ms);
  ImGui::TextWrapped("%s",frame->settings.c_str());
  if(!frame->valid)ImGui::TextWrapped("%s",tr("invalid"));
  ImGui::TextWrapped("%s",tr("cpu_help"));
  if(ImGui::BeginTable("cpu-profile",6,ImGuiTableFlags_Borders|ImGuiTableFlags_RowBg)){
    ImGui::TableSetupColumn(tr("scope"),ImGuiTableColumnFlags_WidthFixed,220);
    for(const char *key:{"total","self","average","p95","maximum"})ImGui::TableSetupColumn(tr(key));
    ImGui::TableHeadersRow();
    for(int i=0;i<CpuCount;++i){
      std::vector<double> values;for(auto *f:history)if(f->valid && f->segment==frame->segment)values.push_back(f->cpu[i].total);
      auto s=statistics(std::move(values));ImGui::TableNextRow();ImGui::TableNextColumn();ImGui::TextWrapped("%s",tr(cpuKeys[i]));
      for(double v:{frame->cpu[i].total,frame->cpu[i].self,s.average,s.p95,s.maximum}){ImGui::TableNextColumn();ImGui::Text("%.3f",v);}
    }
    ImGui::EndTable();
  }
  ImGui::Text("GPU: %s",tr(statusKey(frame->gpu)));
  if(frame->gpu==GpuStatus::Unsupported)ImGui::TextWrapped("%s",tr(gpuReason.c_str()));
  double gpuSum=0;
  if(ImGui::BeginTable("gpu-profile",6,ImGuiTableFlags_Borders|ImGuiTableFlags_RowBg)){
    ImGui::TableSetupColumn(tr("scope"),ImGuiTableColumnFlags_WidthFixed,220);
    for(const char *key:{"gpu_ms","passes","draws","triangles","instances"})ImGui::TableSetupColumn(tr(key));
    ImGui::TableHeadersRow();
    for(int i=0;i<GpuCount;++i){auto &s=frame->graphics[i];gpuSum+=s.ms;
      ImGui::TableNextRow();ImGui::TableNextColumn();ImGui::TextWrapped("%s",tr(gpuKeys[i]));ImGui::TableNextColumn();
      if(frame->gpu==GpuStatus::Ready || frame->gpu==GpuStatus::Partial)ImGui::Text("%.3f",s.ms);else ImGui::TextUnformatted("—");
      ImGui::TableNextColumn();ImGui::Text("%u",s.passes);
      for(uint64_t v:{s.draws,s.triangles,s.instances}){ImGui::TableNextColumn();ImGui::Text("%llu",static_cast<unsigned long long>(v));}
    }
    ImGui::EndTable();
  }
  if(frame->gpu==GpuStatus::Ready || frame->gpu==GpuStatus::Partial)ImGui::Text("%s: %.3f ms",tr("gpu_sum"),gpuSum);
  ImGui::TextWrapped("%s",tr("gpu_help"));
  if(copy)ImGui::SetClipboardText(report(*frame,history).c_str());
}
}
void BBCALL bbDebugUiProfileBegin(bb_int_t id){if(s2prof::recorder().active())s2prof::recorder().begin(id,s2prof::now());}
void BBCALL bbDebugUiProfileEnd(bb_int_t id){if(s2prof::recorder().active())s2prof::recorder().end(id,s2prof::now());}
void BBCALL bbDebugUiProfileBoundary(){if(s2prof::recorder().capturing() || s2prof::recorder().active())s2prof::recorder().boundary(s2prof::now());s2bench::boundary();}
void BBCALL bbDebugUiProfileInvalidate(){s2prof::recorder().invalidate();s2bench::invalidate("focus_lost");}
bb_int_t BBCALL bbDebugUiProfileActive(){return s2prof::recorder().active() || s2prof::recorder().capturing();}
void BBCALL bbDebugUiProfileContext(BBStr *value,bb_float_t budget){std::string text=value?*value:"";delete value;if(s2prof::recorder().active() || s2prof::recorder().capturing())s2prof::recorder().context(std::move(text),budget);}
void BBCALL bbDebugUiProfileLabel(BBStr *key,BBStr *value){std::string k=key?*key:"",v=value?*value:"";delete key;delete value;s2prof::labels[std::move(k)]=std::move(v);}
void BBCALL bbDebugUiProfiler(){s2prof::panel();}
bb_int_t BBCALL bbDebugUiProfileRecording(bb_int_t enabled){if(enabled>=0 && !s2bench::busy())s2prof::recorder().capture(enabled!=0);return s2prof::recorder().capturing();}
BBStr *BBCALL bbDebugUiProfileSnapshot(){
  auto history=s2prof::recorder().history();
  if(history.empty())return new BBStr("");
  const auto *frame=s2prof::recorder().find(s2prof::selected);
  if(!frame || !frame->complete)frame=history.back();
  return new BBStr(s2prof::report(*frame,history));
}
