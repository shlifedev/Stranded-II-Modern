#ifndef S2_LEGACY32_H
#define S2_LEGACY32_H
#include "blitz.h"

// Public Blitz handles are IDs. Native pointers never pass through a Blitz int.
bb_int_t s2Handle(void *pointer);
void *s2Pointer(bb_int_t handle);
void s2ForgetHandle(bb_int_t handle);
void s2ForgetPointer(void *pointer);
#endif
