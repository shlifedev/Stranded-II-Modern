
#include "ode.h"
#include <ode/ode.h>
#include <bb/blitz3d/geom.h>
#include "common.h"

BBLIB void BBCALL _odeBodySetAutoDisableFlag( bb_ptr_t b,bb_int_t do_auto_disable ){
  dBodySetAutoDisableFlag( (dBodyID)b,do_auto_disable );
}

BBLIB bb_ptr_t BBCALL _odeBodyCreate( bb_ptr_t w ){
  return (bb_ptr_t)dBodyCreate( (dWorldID)w );
}

BBLIB void BBCALL _odeBodySetPosition( bb_ptr_t b,bb_float_t x,bb_float_t y,bb_float_t z ){
  dBodySetPosition( (dBodyID)b,x,y,-z );
}

BBLIB void BBCALL _odeBodySetRotation( bb_ptr_t b,bb_float_t p,bb_float_t y,bb_float_t r ){
  Matrix mat=rotationMatrix( p*dtor,y*dtor,r*dtor );

  dMatrix3 m;
  m[ 0]=mat.i.x;
  m[ 1]=mat.i.y;
  m[ 2]=mat.i.z;
  m[ 3]=0.0f;
  m[ 4]=mat.j.x;
  m[ 5]=mat.j.y;
  m[ 6]=mat.j.z;
  m[ 7]=0.0f;
  m[ 8]=mat.k.x;
  m[ 9]=mat.k.y;
  m[10]=mat.k.z;
  m[11]=0.0f;
  dBodySetRotation( (dBodyID)b,m );
}
