
#include "ode.h"
#include <ode/ode.h>
#include <bb/blitz3d/geom.h>
#include "common.h"

BBLIB void BBCALL _odeGeomSetBody( bb_ptr_t g,bb_ptr_t b ){
  dGeomSetBody( (dGeomID)g,(dBodyID)b );
}

BBLIB bb_float_t BBCALL _odeGeomGetPositionX( bb_ptr_t g ){
  return dGeomGetPosition( (dGeomID)g )[0];
}

BBLIB bb_float_t BBCALL _odeGeomGetPositionY( bb_ptr_t g ){
  return dGeomGetPosition( (dGeomID)g )[1];
}

BBLIB bb_float_t BBCALL _odeGeomGetPositionZ( bb_ptr_t g ){
  return -dGeomGetPosition( (dGeomID)g )[2];
}

void get_euler(const dReal * matrix,dReal &kx,dReal &ky,dReal &kz)
{
const dReal epsilon=0.0000001;

if(matrix[8] < 1-epsilon && matrix[8] > -1+epsilon){
        ky=-asin(matrix[8]);
        dReal c=cos(ky);
        kx= atan2(matrix[9]/c,matrix[10]/c);
        kz= atan2(matrix[4]/c,matrix[0]/c);
        }
else
        {
        kz=0;
        ky=-atan2(matrix[8],0);
        kx= atan2(-matrix[6],matrix[5]);
        }
}


BBLIB bb_float_t BBCALL _odeGeomGetPitch( bb_ptr_t g ){
  float p,y,r;
  const dReal *m=dGeomGetRotation( (dGeomID)g );
  get_euler( m,p,y,r );
  return r*rtod;
}

BBLIB bb_float_t BBCALL _odeGeomGetYaw( bb_ptr_t g ){
  float p,y,r;
  const dReal *m=dGeomGetRotation( (dGeomID)g );
  get_euler( m,p,y,r );
  return y*rtod;
}

BBLIB bb_float_t BBCALL _odeGeomGetRoll( bb_ptr_t g ){
  float p,y,r;
  const dReal *m=dGeomGetRotation( (dGeomID)g );
  get_euler( m,p,y,r );
  return p*rtod;
}

BBLIB bb_ptr_t BBCALL _odeCreateBox( bb_ptr_t s,bb_float_t lx,bb_float_t ly,bb_float_t lz ){
  return (bb_ptr_t)dCreateBox( (dSpaceID)s,lx,ly,lz );
}

BBLIB bb_ptr_t BBCALL _odeCreatePlane( bb_ptr_t s,bb_float_t a,bb_float_t b,bb_float_t c,bb_float_t d ){
  return (bb_ptr_t)dCreatePlane( (dSpaceID)s,a,b,c,d );
}
