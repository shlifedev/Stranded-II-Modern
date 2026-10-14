#include <bb/debugui/profiler_model.h>
#undef NDEBUG
#include <cassert>
#include <iostream>

int main(){
  using namespace s2prof;
  Recorder rec;
  rec.boundary(0);assert(!rec.active() && rec.history().empty());
  rec.capture(true);rec.context("shadows=0",1000.0/144);rec.boundary(10);
  uint64_t first=rec.frameId();
  rec.begin(Game,11);rec.begin(Physics,12);rec.end(Physics,15);rec.end(Game,19);
  rec.draw(Scene,24,8);rec.boundary(20);
  auto *frame=rec.find(first);
  assert(frame->ms==10 && frame->cpu[Game].total==8 && frame->cpu[Game].self==5);
  assert(frame->cpu[Physics].total==3 && frame->cpu[Physics].calls==1);
  assert(frame->graphics[Scene].draws==1 && frame->graphics[Scene].triangles==24);
  auto segment=frame->segment;
  rec.context("shadows=1",1000.0/144);rec.boundary(30);
  assert(rec.history().back()->segment!=segment);
  auto late=rec.history().back()->id;
  std::array<GpuSample,GpuCount> gpu{};gpu[ShadowNear].ms=1.25;gpu[ShadowNear].passes=2;
  rec.gpuResult(late,gpu,GpuStatus::Ready);
  assert(rec.find(late)->graphics[ShadowNear].ms==1.25 && rec.find(first)->graphics[ShadowNear].ms==0);
  auto invalid=rec.frameId();rec.begin(Game,31);rec.end(Physics,32);rec.boundary(40);
  assert(!rec.find(invalid)->valid);
  invalid=rec.frameId();rec.invalidate();rec.boundary(50);assert(!rec.find(invalid)->valid);
  for(size_t i=0;i<Recorder::Capacity+5;++i)rec.boundary(60+i);
  assert(!rec.find(first) && rec.history().size()==Recorder::Capacity);
  rec.capture(false);rec.boundary(2990);
  assert(!rec.active() && rec.history().size()==Recorder::Capacity);
  rec.capture(true);
  rec.clear();rec.gpuResult(late,gpu,GpuStatus::Ready);assert(rec.history().empty());
  auto serial=rec.latestId();rec.boundary(3000);assert(rec.frameId()>serial);
  rec.capture(false);rec.boundary(3010);assert(!rec.active());
  auto stats=statistics({1,2,3,4,100});assert(stats.count==5 && stats.average==22 && stats.p95==100 && stats.maximum==100);
  std::cout<<"PASS: nested CPU self times, segments, asynchronous GPU frame IDs, invalid frames, bounded history, clear/stop and p95\n";
}
