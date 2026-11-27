
#include "ode.h"
#include <ode/ode.h>

BBLIB bb_ptr_t BBCALL _odeGeomTriMeshDataCreate(){
  return (bb_ptr_t)dGeomTriMeshDataCreate();
}
