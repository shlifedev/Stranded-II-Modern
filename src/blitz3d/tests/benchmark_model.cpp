#include <bb/debugui/benchmark_model.h>
#undef NDEBUG
#include <cassert>
#include <iostream>
int main(){
  using namespace s2prof;using namespace s2bench;
  Recorder rec;Collector c;
  rec.completed=[&](const Frame &f){c.frame(f);};
  rec.resolved=[&](uint64_t id,const auto &g,GpuStatus s){c.gpu(id,g,s);};
  rec.gpuSupport(true);rec.capture(true);rec.boundary(0);
  c.begin("baseline","object + unit + item",0,1);
  const auto first=rec.frameId();
  for(int i=1;i<=5000;++i){rec.begin(Game,i*10-9);rec.begin(Main,i*10-8);rec.end(Main,i*10-4);rec.end(Game,i*10-1);rec.boundary(i*10);}
  assert(rec.history().size()==Recorder::Capacity && c.trials[0].frames.size()==5000);
  assert(!rec.find(first));
  std::array<GpuSample,GpuCount> g{};g[Reflections].ms=2.5;g[Reflections].passes=6;
  // Late GPU data must reach the original trial even after ring eviction.
  rec.gpuResult(first,g,GpuStatus::Partial);c.stop();c.finish(50025,true);
  assert(c.trials[0].frames[0].graphics[Reflections].ms==2.5);
  assert(c.trials[0].frames[0].gpu==GpuStatus::Partial);
  assert(c.trials[0].stats().count==5000 && c.trials[0].stats().p95==10);
  assert(c.trials[0].fps()>99.9 && c.trials[0].fps()<100);
  c.begin("unsupported","",1,1);rec.gpuSupport(false);rec.boundary(50010);c.stop();c.finish(12,true);
  rec.gpuResult(first,g,GpuStatus::Ready);
  assert(c.trials[0].frames[0].gpu==GpuStatus::Ready);
  assert(c.trials[1].frames[0].graphics[Reflections].ms==0);
  c.begin("focus loss","",0,2);rec.invalidate();rec.boundary(50020);c.invalidate("focus_lost");
  assert(!c.trials[2].valid && c.trials[0].valid);
  assert(c.trials[2].fps()==0 && median({3,1,2})==2 && median({1,3})==2);
  c.clear();rec.gpuResult(first,g,GpuStatus::Failed);assert(c.trials.empty());
  std::cout<<"PASS: full trial samples beyond ring capacity, late GPU ownership, nested CPU, completed throughput, cancellation and medians\n";
}
