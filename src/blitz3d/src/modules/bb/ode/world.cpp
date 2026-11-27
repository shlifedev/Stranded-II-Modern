
#include "ode.h"
#include <ode/ode.h>

BBLIB bb_ptr_t BBCALL _odeWorldCreate(){
  return (bb_ptr_t)dWorldCreate();
}

BBLIB void BBCALL _odeWorldDestroy( bb_ptr_t w ){
  dWorldDestroy( (dWorldID)w );
}

BBLIB void BBCALL _odeWorldSetGravity( bb_ptr_t w,bb_float_t x,bb_float_t y,bb_float_t z){
  dWorldSetGravity( (dWorldID)w,x,y,z );
}

BBLIB bb_int_t BBCALL _odeWorldQuickStep( bb_ptr_t w,bb_float_t stepsize ){
  return dWorldQuickStep( (dWorldID)w,stepsize );
}

BBLIB void BBCALL _odeWorldSetAutoDisableFlag( bb_ptr_t w,bb_int_t do_auto_disable){
  dWorldSetAutoDisableFlag( (dWorldID)w,do_auto_disable );
}
