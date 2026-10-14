#pragma once
#include "profiler_model.h"

namespace s2prof {
Recorder &recorder();
double now();
void gpuStatus(const char *status);
void backend(const char *name);
void draw(int group,uint64_t triangles,uint64_t instances=1);
int renderGroup();
struct RenderGroup {
  int previous;
  explicit RenderGroup(int value);
  ~RenderGroup();
};
const char *label(const char *key);
void panel();
struct Scope {
  int id;bool active;
  explicit Scope(int value):id(value),active(recorder().active()){if(active)recorder().begin(id,now());}
  ~Scope(){if(active)recorder().end(id,now());}
};
}
