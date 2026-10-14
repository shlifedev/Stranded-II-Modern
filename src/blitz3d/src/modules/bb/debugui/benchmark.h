#pragma once
#include <webgpu/webgpu.h>
namespace s2bench {
bool busy();
void boundary();
void submitted(WGPUQueue queue);
void detach(WGPUQueue queue);
void invalidate(const char *reason);
}
