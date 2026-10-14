#pragma once
#include <algorithm>
#include <array>
#include <cmath>
#include <cstdint>
#include <functional>
#include <string>
#include <vector>

namespace s2prof {
enum Cpu { Game, Physics, Objects, Grass, Shadows, Water, Reflection, Main, Post, Hud, Ui, Pace, Flip, CpuCount };
enum Gpu { Scene, ShadowNear, ShadowFar, Reflections, TwoD, Present, Mips, GpuCount };
enum class GpuStatus { Unsupported, Pending, Ready, Skipped, Partial, Failed };
struct CpuSample { double total=0,self=0; unsigned calls=0; };
struct GpuSample { double ms=0; uint64_t draws=0,triangles=0,instances=0; unsigned passes=0; };
struct Frame {
  uint64_t id=0,segment=0;
  double ms=0,budget=0;
  bool complete=false,valid=true;
  GpuStatus gpu=GpuStatus::Unsupported;
  std::string settings;
  std::array<CpuSample,CpuCount> cpu{};
  std::array<GpuSample,GpuCount> graphics{};
};
struct Stats { double average=0,p95=0,maximum=0; size_t count=0; };
inline Stats statistics(std::vector<double> values){
  Stats result;result.count=values.size();if(values.empty())return result;
  for(double value:values)result.average+=value;
  result.average/=values.size();std::sort(values.begin(),values.end());
  result.p95=values[size_t(std::ceil(values.size()*0.95))-1];result.maximum=values.back();return result;
}
class Recorder {
  struct Open { int id; double start,children; };
  std::vector<Frame> frames;
  std::array<Open,64> stack{};
  size_t depth=0;
  uint64_t serial=0,current=0,segment=0;
  double start=0;
  bool enabled=false,supported=false;
  std::string settings;
  double budget=0;
public:
  // The benchmark keeps its own samples before the display ring expires.
  std::function<void(const Frame&)> completed;
  std::function<void(uint64_t,const std::array<GpuSample,GpuCount>&,GpuStatus)> resolved;
  static constexpr size_t Capacity=2048;
  bool capturing()const{return enabled;}
  bool active()const{return current!=0;}
  uint64_t frameId()const{return current;}
  uint64_t latestId()const{return serial;}
  void capture(bool value){enabled=value;if(value && frames.empty())frames.resize(Capacity+1);}
  Frame *find(uint64_t id){if(!id || frames.empty())return nullptr;auto &f=frames[id%(Capacity+1)];return f.id==id?&f:nullptr;}
  const Frame *find(uint64_t id)const{return const_cast<Recorder*>(this)->find(id);}
  void clear(){for(auto &f:frames)f=Frame{};current=0;depth=0;}
  void context(std::string value,double target){
    if(value!=settings){settings=std::move(value);++segment;}
    budget=target;
    if(auto *f=find(current)){f->settings=settings;f->segment=segment;f->budget=budget;}
  }
  void gpuSupport(bool value){supported=value;}
  void invalidate(){if(auto *f=find(current))f->valid=false;depth=0;}
  void boundary(double now){
    if(auto *f=find(current)){
      if(depth){f->valid=false;depth=0;}
      f->ms=std::max(0.0,now-start);f->complete=true;
      if(completed)completed(*f);
      // Keep the same completed-frame limit when recording is stopped.
      if(current>Capacity)if(auto *expired=find(current-Capacity))*expired=Frame{};
    }
    current=0;
    if(!enabled)return;
    current=++serial;start=now;
    auto &f=frames[current%(Capacity+1)];f=Frame{};f.id=current;f.segment=segment;
    f.settings=settings;f.budget=budget;f.gpu=supported?GpuStatus::Pending:GpuStatus::Unsupported;
  }
  void begin(int id,double now){
    if(!active() || id<0 || id>=CpuCount)return;
    if(depth==stack.size()){invalidate();return;}
    stack[depth++]={id,now,0};
  }
  void end(int id,double now){
    if(!active())return;
    if(!depth || stack[depth-1].id!=id){invalidate();return;}
    auto entry=stack[--depth];double elapsed=std::max(0.0,now-entry.start);
    auto &sample=find(current)->cpu[id];sample.total+=elapsed;
    sample.self+=std::max(0.0,elapsed-entry.children);++sample.calls;
    if(depth)stack[depth-1].children+=elapsed;
  }
  void draw(int group,uint64_t triangles,uint64_t instances){
    if(auto *f=find(current))if(group>=0 && group<GpuCount){
      auto &s=f->graphics[group];++s.draws;s.triangles+=triangles;s.instances+=instances;
    }
  }
  void gpuResult(uint64_t id,const std::array<GpuSample,GpuCount> &samples,GpuStatus status){
    if(auto *f=find(id)){for(int i=0;i<GpuCount;++i){f->graphics[i].ms=samples[i].ms;f->graphics[i].passes=samples[i].passes;}f->gpu=status;}
    if(resolved)resolved(id,samples,status);
  }
  std::vector<const Frame*> history()const{
    std::vector<const Frame*> result;
    for(const auto &f:frames)if(f.id && f.complete)result.push_back(&f);
    std::sort(result.begin(),result.end(),[](auto *a,auto *b){return a->id<b->id;});return result;
  }
};
}
