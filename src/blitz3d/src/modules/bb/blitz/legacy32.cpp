#include "legacy32.h"
#include <unordered_map>
#include <limits>

static_assert(sizeof(bb_int_t)==4 && sizeof(bb_float_t)==4, "Blitz numeric ABI must stay 32-bit");
static_assert(sizeof(void*)==8 && sizeof(BBField)==8 && sizeof(BBObj)==40 && sizeof(BBData)==16, "Native Blitz pointer layout mismatch");

namespace {
std::unordered_map<void*, bb_int_t> handles;
std::unordered_map<bb_int_t, void*> pointers;
uint32_t nextHandle=0;
}

bb_int_t s2Handle(void *pointer) {
    if(!pointer) return 0;
    auto found=handles.find(pointer);
    if(found!=handles.end()) return found->second;
    if(nextHandle>=uint32_t(std::numeric_limits<bb_int_t>::max())) {
        RTEX("Native handle space exhausted");
        return 0;
    }
    bb_int_t handle=++nextHandle;
    handles.emplace(pointer,handle);
    pointers.emplace(handle,pointer);
    return handle;
}

void *s2Pointer(bb_int_t handle) {
    if(!handle) return nullptr;
    auto found=pointers.find(handle);
    if(found==pointers.end()) {
        RTEX("Invalid or released native handle");
        return nullptr;
    }
    return found->second;
}

void s2ForgetHandle(bb_int_t handle) {
    auto found=pointers.find(handle);
    if(found==pointers.end()) return;
    handles.erase(found->second);
    pointers.erase(found);
}

void s2ForgetPointer(void *pointer){
    auto found=handles.find(pointer);
    if(found!=handles.end()) s2ForgetHandle(found->second);
}
