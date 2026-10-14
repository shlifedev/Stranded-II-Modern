#pragma once
#include "profiler_model.h"
#include <unordered_map>

namespace s2bench {
struct Trial {
  std::string name,context,reason;
  int repetition=0,condition=0;
  bool valid=true,finished=false;
  double elapsed=0;
  std::vector<s2prof::Frame> frames;
  double fps()const{return valid && finished && elapsed>0?frames.size()*1000./elapsed:0;}
  s2prof::Stats stats()const{
    std::vector<double> values;for(const auto &f:frames)if(f.valid)values.push_back(f.ms);
    return s2prof::statistics(std::move(values));
  }
};
class Collector {
  std::unordered_map<uint64_t,std::pair<size_t,size_t>> owners;
  int active=-1;
public:
  std::vector<Trial> trials;
  void clear(){active=-1;owners.clear();trials.clear();}
  void begin(std::string name,std::string context,int condition,int repetition){
    trials.emplace_back();active=int(trials.size()-1);auto &t=trials.back();
    t.name=std::move(name);t.context=std::move(context);t.condition=condition;t.repetition=repetition;
  }
  void frame(const s2prof::Frame &f){
    if(active<0)return;
    auto &t=trials[size_t(active)];
    if(!f.valid){t.valid=false;t.reason="invalid_frame";}
    owners[f.id]={size_t(active),t.frames.size()};t.frames.push_back(f);t.frames.back().settings.clear();
  }
  void gpu(uint64_t id,const std::array<s2prof::GpuSample,s2prof::GpuCount> &samples,s2prof::GpuStatus status){
    auto it=owners.find(id);if(it==owners.end())return;
    auto &f=trials[it->second.first].frames[it->second.second];
    for(int i=0;i<s2prof::GpuCount;++i){f.graphics[i].ms=samples[i].ms;f.graphics[i].passes=samples[i].passes;}
    f.gpu=status;
  }
  void resume(){active=trials.empty()?-1:int(trials.size()-1);}
  void stop(){active=-1;}
  void finish(double elapsed,bool success){
    if(trials.empty())return;auto &t=trials.back();t.elapsed=elapsed;t.finished=true;
    if(!success){t.valid=false;t.reason="gpu_completion_failed";}
  }
  void invalidate(const std::string &reason){
    if(trials.empty())return;auto &t=trials.back();t.valid=false;t.reason=reason;active=-1;
  }
};
inline double median(std::vector<double> v){
  if(v.empty())return 0;std::sort(v.begin(),v.end());
  return v.size()%2?v[v.size()/2]:(v[v.size()/2-1]+v[v.size()/2])/2.;
}
}
