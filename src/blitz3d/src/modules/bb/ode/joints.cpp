
#include "ode.h"
#include <ode/ode.h>

BBLIB bb_ptr_t BBCALL _odeJointGroupCreate( bb_int_t max_size ){
  return (bb_ptr_t)dJointGroupCreate( max_size );
}

BBLIB void BBCALL _odeJointGroupDestroy( bb_ptr_t g ){
  dJointGroupDestroy( (dJointGroupID)g );
}

BBLIB void BBCALL _odeJointGroupEmpty( bb_ptr_t g ){
  dJointGroupEmpty( (dJointGroupID)g );
}
