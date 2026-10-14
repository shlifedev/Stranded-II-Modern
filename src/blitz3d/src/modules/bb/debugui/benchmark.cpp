#include "benchmark.h"
#include "benchmark_model.h"
#include "profiler.h"
#include "debugui.h"
#include "imgui.h"
#include <SDL.h>
#include <filesystem>
#include <chrono>
#include <fstream>
#include <iomanip>
#include <memory>
#include <sstream>
#ifdef BB_EMSCRIPTEN
#include <emscripten.h>
EM_JS(void,s2BenchmarkDownload,(const char *name,const char *body,int json),{
  const url=URL.createObjectURL(new Blob([UTF8ToString(body)],{type:json?'application/json':'text/csv;charset=utf-8'}));
  const a=document.createElement('a');a.href=url;a.download=UTF8ToString(name);a.click();
  setTimeout(()=>URL.revokeObjectURL(url),1000);
});
#endif
namespace s2bench {
namespace {
// Callbacks run on the presentation thread (ProcessEvents), or the browser JS thread.
struct State { int phase=0;uint64_t epoch=0;bool savedRecording=false;double started=0,duration=8000;Collector collector; };
std::shared_ptr<State> state=std::make_shared<State>();
WGPUQueue currentQueue=nullptr;
std::string exportStatus;
struct Ticket { std::weak_ptr<State> state;uint64_t epoch;bool start; };
void restore(){s2prof::recorder().capture(state->savedRecording);}
void fence(bool start){
  if(!currentQueue){invalidate("gpu_completion_unavailable");return;}
  auto *ticket=new Ticket{state,state->epoch,start};
  WGPUQueueWorkDoneCallbackInfo cb={};
#ifdef BB_EMSCRIPTEN
  cb.mode=WGPUCallbackMode_AllowSpontaneous;
#else
  cb.mode=WGPUCallbackMode_AllowProcessEvents;
#endif
  cb.userdata1=ticket;
  cb.callback=[](WGPUQueueWorkDoneStatus status,WGPUStringView,void *userdata,void*){
    std::unique_ptr<Ticket> ticket(static_cast<Ticket*>(userdata));auto s=ticket->state.lock();
    if(!s || s->epoch!=ticket->epoch)return;
    if(status!=WGPUQueueWorkDoneStatus_Success){invalidate("gpu_completion_failed");return;}
    if(ticket->start)s->phase=2;
    else {s->collector.finish(s2prof::now()-s->started,true);s->phase=5;restore();}
  };
  wgpuQueueOnSubmittedWorkDone(currentQueue,cb);
}
std::string quoted(const std::string &s,bool json){
  std::string out="\"";for(unsigned char c:s){
    if(c=='"')out+=json?"\\\"":"\"\"";
    else if(json && c=='\\')out+="\\\\";
    else if(json && c<32){const char *hex="0123456789abcdef";out+="\\u00";out+=hex[c>>4];out+=hex[c&15];}
    else out+=char(c);
  }return out+'"';
}
const char *cpuNames[]={"game","physics","objects","grass","shadows","water","reflection","main","post","hud","ui","pace","flip"};
const char *gpuNames[]={"scene","shadow_near","shadow_far","reflections","2d","present","mips"};
const char *statusNames[]={"unsupported","pending","ready","skipped","partial","failed"};
struct GpuAggregate {double ms=0,passes=0,draws=0,triangles=0,instances=0;};
struct Summary {
  std::array<double,s2prof::CpuCount> cpu{},self{};
  std::array<GpuAggregate,s2prof::GpuCount> gpu{};
  std::array<size_t,6> statuses{};
  size_t samples=0,timed=0;
  explicit Summary(const Trial &t){
    for(const auto &f:t.frames){
      ++statuses[size_t(f.gpu)];if(!f.valid)continue;++samples;
      if(f.gpu==s2prof::GpuStatus::Ready || f.gpu==s2prof::GpuStatus::Partial)++timed;
      for(int i=0;i<s2prof::CpuCount;++i){cpu[i]+=f.cpu[i].total;self[i]+=f.cpu[i].self;}
      for(int i=0;i<s2prof::GpuCount;++i){gpu[i].draws+=f.graphics[i].draws;gpu[i].triangles+=f.graphics[i].triangles;gpu[i].instances+=f.graphics[i].instances;
        if(f.gpu==s2prof::GpuStatus::Ready || f.gpu==s2prof::GpuStatus::Partial){gpu[i].ms+=f.graphics[i].ms;gpu[i].passes+=f.graphics[i].passes;}}
    }
    if(samples)for(int i=0;i<s2prof::CpuCount;++i){cpu[i]/=samples;self[i]/=samples;}
    for(auto &g:gpu){if(timed){g.ms/=timed;g.passes/=timed;}if(samples){g.draws/=samples;g.triangles/=samples;g.instances/=samples;}}
  }
};
double baseline(){std::vector<double> values;for(const auto &t:state->collector.trials)if(t.condition==0 && t.valid && t.finished && !t.frames.empty())values.push_back(t.stats().average);return median(values);}
std::string report(bool json){
  std::ostringstream out;out<<std::fixed<<std::setprecision(6);double base=baseline();
  if(json)out<<"{\"schema\":1,\"baseline_ms\":"<<base<<",\"trials\":[";
  else {
    out<<"name,condition,repetition,valid,reason,context,samples,completed_elapsed_ms,completed_fps,mean_ms,p95_ms,max_ms,delta_ms,delta_percent";
    for(auto n:cpuNames)out<<",cpu_"<<n<<"_total_ms,cpu_"<<n<<"_self_ms";
    for(auto n:gpuNames)out<<",gpu_"<<n<<"_ms,gpu_"<<n<<"_passes,draws_"<<n<<",triangles_"<<n<<",instances_"<<n;
    for(auto n:statusNames)out<<",gpu_"<<n<<"_frames";out<<"\n";
  }
  bool first=true;
  for(const auto &t:state->collector.trials){auto st=t.stats();Summary s(t);double delta=st.average-base;
    if(json){
      if(!first)out<<',';out<<"{\"name\":"<<quoted(t.name,true)<<",\"condition\":"<<t.condition<<",\"repetition\":"<<t.repetition
        <<",\"valid\":"<<(t.valid && t.finished?"true":"false")<<",\"reason\":"<<quoted(t.reason,true)<<",\"context\":"<<quoted(t.context,true)
        <<",\"samples\":"<<st.count<<",\"completed_elapsed_ms\":"<<t.elapsed<<",\"completed_fps\":"<<t.fps()
        <<",\"mean_ms\":"<<st.average<<",\"p95_ms\":"<<st.p95<<",\"max_ms\":"<<st.maximum<<",\"delta_ms\":"<<delta<<",\"delta_percent\":"<<(base>0?delta/base*100:0)<<",\"cpu\":{";
      for(int i=0;i<s2prof::CpuCount;++i){if(i)out<<',';out<<quoted(cpuNames[i],true)<<":{\"total_ms\":"<<s.cpu[i]<<",\"self_ms\":"<<s.self[i]<<'}';}
      out<<"},\"gpu\":{";
      for(int i=0;i<s2prof::GpuCount;++i){if(i)out<<',';auto &g=s.gpu[i];out<<quoted(gpuNames[i],true)<<":{\"ms\":"<<(s.timed?std::to_string(g.ms):"null")<<",\"passes\":"<<g.passes<<",\"draws\":"<<g.draws<<",\"triangles\":"<<g.triangles<<",\"instances\":"<<g.instances<<'}';}
      out<<"},\"gpu_status\":{";for(int i=0;i<6;++i){if(i)out<<',';out<<quoted(statusNames[i],true)<<':'<<s.statuses[i];}out<<"}}";
    }else{
      out<<quoted(t.name,false)<<','<<t.condition<<','<<t.repetition<<','<<(t.valid && t.finished)<<','<<quoted(t.reason,false)<<','<<quoted(t.context,false)<<','<<st.count<<','<<t.elapsed<<','<<t.fps()<<','<<st.average<<','<<st.p95<<','<<st.maximum<<','<<delta<<','<<(base>0?delta/base*100:0);
      for(int i=0;i<s2prof::CpuCount;++i)out<<','<<s.cpu[i]<<','<<s.self[i];
      for(auto &g:s.gpu){out<<',';if(s.timed)out<<g.ms;out<<','<<g.passes<<','<<g.draws<<','<<g.triangles<<','<<g.instances;}
      for(auto n:s.statuses)out<<','<<n;out<<'\n';
    }first=false;
  }
  if(json)out<<"]}";return out.str();
}
}
bool busy(){return (state->phase>0 && state->phase<5) || state->phase==6;}
void submitted(WGPUQueue queue){
  if(currentQueue!=queue){if(currentQueue)wgpuQueueRelease(currentQueue);currentQueue=queue;if(queue)wgpuQueueAddRef(queue);}
  if(state->phase==1){state->phase=6;fence(true);}
}
void detach(WGPUQueue queue){if(queue && currentQueue==queue){if(busy() || state->phase==6)invalidate("device_lost");wgpuQueueRelease(currentQueue);currentQueue=nullptr;}}
void invalidate(const char *reason){
  if(!busy() && state->phase!=6)return;
  ++state->epoch;state->collector.invalidate(reason);state->phase=5;restore();
}
void boundary(){
  if(state->phase==2){state->started=s2prof::now();state->collector.resume();state->phase=3;}
  else if(state->phase==3 && s2prof::now()-state->started>=state->duration){state->collector.stop();state->phase=4;fence(false);}
}
}
void BBCALL bbDebugUiBenchmarkBegin(BBStr *name,BBStr *context,bb_int_t condition,bb_int_t repetition,bb_int_t duration){
  std::string n=name?*name:"",c=context?*context:"";delete name;delete context;if(s2bench::busy() || s2bench::state->phase==6)return;
  auto &s=*s2bench::state;++s.epoch;s.savedRecording=s2prof::recorder().capturing();s.phase=1;s.duration=std::max(1,int(duration));
  s.collector.begin(std::move(n),std::move(c),condition,repetition);s.collector.stop();
  s2prof::recorder().capture(true);
  s2prof::recorder().completed=[](const s2prof::Frame &f){if(s2bench::state->phase==3)s2bench::state->collector.frame(f);};
  s2prof::recorder().resolved=[](uint64_t id,const auto &samples,s2prof::GpuStatus status){s2bench::state->collector.gpu(id,samples,status);};
}
bb_int_t BBCALL bbDebugUiBenchmarkState(){return s2bench::state->phase==6?1:s2bench::state->phase;}
void BBCALL bbDebugUiBenchmarkCancel(){s2bench::invalidate("cancelled");}
void BBCALL bbDebugUiBenchmarkClear(){if(s2bench::busy() || s2bench::state->phase==6)return;s2bench::state->collector.clear();s2bench::state->phase=0;}
BBStr *BBCALL bbDebugUiBenchmarkReport(bb_int_t json){return new BBStr(s2bench::report(json!=0));}
bb_int_t BBCALL bbDebugUiBenchmarkExport(BBStr *directory,bb_int_t json){
  std::string dir=directory?*directory:"";delete directory;
  std::replace(dir.begin(),dir.end(),'\\','/');
  auto body=s2bench::report(json!=0);auto stamp=std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::system_clock::now().time_since_epoch()).count();
  auto name="benchmark-"+std::to_string(stamp)+"-"+std::to_string(SDL_GetTicks64())+(json?".json":".csv");
#ifdef BB_EMSCRIPTEN
  s2BenchmarkDownload(name.c_str(),body.c_str(),json);s2bench::exportStatus=name;return 1;
#else
  std::error_code ec;std::filesystem::create_directories(std::filesystem::u8path(dir),ec);
  auto path=std::filesystem::u8path(dir)/name;
  for(unsigned n=1;std::filesystem::exists(path,ec);++n)path=std::filesystem::u8path(dir)/(name+"."+std::to_string(n));
  std::ofstream out(path,std::ios::binary);out<<body;out.close();
  s2bench::exportStatus=out?path.u8string():"export_failed";return bool(out);
#endif
}
void BBCALL bbDebugUiBenchmarkResults(){
  using namespace s2bench;using s2prof::label;
  if(state->collector.trials.empty())return;
  if(ImGui::Button(label("benchmark.copy")))ImGui::SetClipboardText(report(true).c_str());
  ImGui::TextWrapped("%s",label("benchmark.metric_help"));
  if(!exportStatus.empty())ImGui::TextWrapped("%s",exportStatus.c_str());
  double base=baseline();
  if(ImGui::BeginTable("benchmark-results",7,ImGuiTableFlags_Borders|ImGuiTableFlags_RowBg|ImGuiTableFlags_ScrollX)){
    for(auto key:{"benchmark.trial","average","p95","maximum","benchmark.fps","benchmark.delta","benchmark.valid"})ImGui::TableSetupColumn(label(key));ImGui::TableHeadersRow();
    for(const auto &t:state->collector.trials){auto s=t.stats();ImGui::TableNextRow();ImGui::TableNextColumn();ImGui::TextWrapped("%s / %d",t.name.c_str(),t.repetition);
      for(double v:{s.average,s.p95,s.maximum,t.fps()}){ImGui::TableNextColumn();ImGui::Text("%.2f",v);}
      ImGui::TableNextColumn();if(base>0)ImGui::Text("%+.2f ms / %+.1f%%",s.average-base,(s.average/base-1)*100);else ImGui::TextUnformatted("—");
      ImGui::TableNextColumn();ImGui::TextUnformatted(t.valid && t.finished?label("benchmark.valid"):label("invalid"));
    }ImGui::EndTable();
  }
  if(ImGui::CollapsingHeader(label("benchmark.details"))){
    // Repetition median is calculated only from completed valid trials.
    for(const auto &t:state->collector.trials){
      if(!ImGui::TreeNode(&t,"%s / %d",t.name.c_str(),t.repetition))continue;
      ImGui::TextWrapped("%s",t.context.c_str());if(!t.reason.empty())ImGui::TextWrapped("%s",label(t.reason.c_str()));
      Summary s(t);std::vector<double> means,fps;
      for(const auto &r:state->collector.trials)if(r.condition==t.condition && r.valid && r.finished){means.push_back(r.stats().average);fps.push_back(r.fps());}
      ImGui::Text("%s: %.3f ms / %.1f FPS (%zu)",label("benchmark.median"),median(means),median(fps),means.size());
      for(int i=0;i<6;++i)ImGui::Text("GPU %s: %zu / %.1f%%",label(statusNames[i]),s.statuses[i],t.frames.empty()?0.:100.*s.statuses[i]/t.frames.size());
      for(int i=0;i<s2prof::CpuCount;++i)ImGui::Text("CPU %s: %.3f / %.3f ms",label((std::string("cpu.")+cpuNames[i]).c_str()),s.cpu[i],s.self[i]);
      for(int i=0;i<s2prof::GpuCount;++i){auto &g=s.gpu[i];
        ImGui::Text("GPU %s: %s ms / %.1f draws / %.1f triangles / %.1f instances",gpuNames[i],s.timed?std::to_string(g.ms).c_str():"—",g.draws,g.triangles,g.instances);}
      ImGui::TreePop();
    }
  }
}
