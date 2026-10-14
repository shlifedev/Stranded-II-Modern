#include "profiler_gpu.h"
#include "graphics.webgpu.h"
#include <bb/debugui/profiler.h>
#include <array>
#include <cstring>

struct WebGPUProfiler::Slot {
  static constexpr unsigned MaxPasses=512;
  WGPUQuerySet queries=nullptr;
  WGPUBuffer resolveBuffer=nullptr,readBuffer=nullptr;
  std::array<WGPUPassTimestampWrites,MaxPasses> writes{};
  std::array<int,MaxPasses> groups{};
  uint64_t frame=0;
  unsigned count=0;
  bool alive=true,busy=false,resolved=false,partial=false;
  ~Slot(){if(queries)wgpuQuerySetRelease(queries);if(resolveBuffer)wgpuBufferRelease(resolveBuffer);if(readBuffer)wgpuBufferRelease(readBuffer);}
  bool initialize(WGPUDevice device){
    WGPUQuerySetDescriptor query={};query.label=bbStrView("bb.profiler.timestamps");query.type=WGPUQueryType_Timestamp;query.count=MaxPasses*2;
    queries=wgpuDeviceCreateQuerySet(device,&query);
    WGPUBufferDescriptor buffer={};buffer.label=bbStrView("bb.profiler.resolve");buffer.size=MaxPasses*2*sizeof(uint64_t);
    buffer.usage=WGPUBufferUsage_QueryResolve|WGPUBufferUsage_CopySrc;resolveBuffer=wgpuDeviceCreateBuffer(device,&buffer);
    buffer.label=bbStrView("bb.profiler.readback");buffer.usage=WGPUBufferUsage_CopyDst|WGPUBufferUsage_MapRead;
    readBuffer=wgpuDeviceCreateBuffer(device,&buffer);return queries && resolveBuffer && readBuffer;
  }
};
WebGPUProfiler::WebGPUProfiler(WGPUDevice value):device(value){
  supported=wgpuDeviceHasFeature(device,WGPUFeatureName_TimestampQuery);
  s2prof::recorder().gpuSupport(supported);s2prof::gpuStatus(supported?"ready":"unsupported");
}
WebGPUProfiler::~WebGPUProfiler(){
  for(auto &slot:slots)if(slot){slot->alive=false;
    if(slot->busy && slot->frame)s2prof::recorder().gpuResult(slot->frame,{},s2prof::GpuStatus::Failed);
  }
  s2prof::recorder().gpuSupport(false);s2prof::gpuStatus("device_lost");
}
void WebGPUProfiler::pass(WGPURenderPassDescriptor &desc,int group){
  auto &rec=s2prof::recorder();uint64_t frame=rec.frameId();if(!supported || !frame || frame==skippedFrame)return;
  if(current && current->frame!=frame){
    // An abandoned presentation must not reuse unresolved query indices.
    s2prof::recorder().gpuResult(current->frame,{},s2prof::GpuStatus::Skipped);
    current->alive=false;for(auto &slot:slots)if(slot==current)slot.reset();current.reset();
  }
  if(!current){
    for(auto &slot:slots){
      if(!slot){slot=std::make_shared<Slot>();if(!slot->initialize(device)){
        slot.reset();supported=false;rec.gpuSupport(false);s2prof::gpuStatus("gpu_init_failed");rec.gpuResult(frame,{},s2prof::GpuStatus::Failed);return;
      }}
      if(!slot->busy){current=slot;slot->busy=true;slot->resolved=false;slot->partial=false;slot->frame=frame;slot->count=0;break;}
    }
    if(!current){skippedFrame=frame;rec.gpuResult(frame,{},s2prof::GpuStatus::Skipped);return;}
  }
  if(current->count==Slot::MaxPasses){current->partial=true;return;}
  unsigned index=current->count++;
  auto &writes=current->writes[index];writes.querySet=current->queries;
  writes.beginningOfPassWriteIndex=index*2;writes.endOfPassWriteIndex=index*2+1;
  current->groups[index]=group;desc.timestampWrites=&writes;
}
bool WebGPUProfiler::needsResolve()const{return current && current->count && !current->resolved;}
void WebGPUProfiler::resolve(WGPUCommandEncoder encoder){
  if(!current){
    auto &rec=s2prof::recorder();if(supported && rec.active() && rec.frameId()!=skippedFrame)rec.gpuResult(rec.frameId(),{},s2prof::GpuStatus::Ready);
    return;
  }
  if(!current->count || current->resolved)return;
  wgpuCommandEncoderResolveQuerySet(encoder,current->queries,0,current->count*2,current->resolveBuffer,0);
  wgpuCommandEncoderCopyBufferToBuffer(encoder,current->resolveBuffer,0,current->readBuffer,0,current->count*2*sizeof(uint64_t));
  current->resolved=true;
}
void WebGPUProfiler::submitted(){
  if(!current || !current->resolved)return;
  auto slot=current;current.reset();
  auto *holder=new std::shared_ptr<Slot>(slot);
  WGPUBufferMapCallbackInfo callback={};callback.mode=BB_WGPU_CALLBACK_MODE;callback.userdata1=holder;
  callback.callback=[](WGPUMapAsyncStatus status,WGPUStringView,void *userdata,void*){
    std::unique_ptr<std::shared_ptr<Slot>> owner(static_cast<std::shared_ptr<Slot>*>(userdata));auto slot=*owner;
    std::array<s2prof::GpuSample,s2prof::GpuCount> samples{};
    bool success=status==WGPUMapAsyncStatus_Success;
    if(success){
      auto *bytes=static_cast<const unsigned char*>(wgpuBufferGetConstMappedRange(slot->readBuffer,0,slot->count*2*sizeof(uint64_t)));
      success=bytes!=nullptr;
      if(bytes)for(unsigned i=0;i<slot->count;++i){
        uint64_t begin=0,end=0;std::memcpy(&begin,bytes+i*16,8);std::memcpy(&end,bytes+i*16+8,8);
        if(end<begin){success=false;break;}
        auto &sample=samples[slot->groups[i]];sample.ms+=double(end-begin)/1000000.0;++sample.passes;
      }
      wgpuBufferUnmap(slot->readBuffer);
    }
    if(slot->alive)s2prof::recorder().gpuResult(slot->frame,samples,!success?s2prof::GpuStatus::Failed:
      (slot->partial?s2prof::GpuStatus::Partial:s2prof::GpuStatus::Ready));
    slot->busy=false;
  };
  wgpuBufferMapAsync(slot->readBuffer,WGPUMapMode_Read,0,slot->count*2*sizeof(uint64_t),callback);
}
