#pragma once
#include <webgpu/webgpu.h>
#include <memory>

class WebGPUProfiler {
  struct Slot;
  WGPUDevice device;
  std::shared_ptr<Slot> slots[4],current;
  bool supported=false;
  uint64_t skippedFrame=0;
public:
  explicit WebGPUProfiler(WGPUDevice);
  ~WebGPUProfiler();
  void pass(WGPURenderPassDescriptor&,int group);
  void resolve(WGPUCommandEncoder);
  bool needsResolve()const;
  void submitted();
};
