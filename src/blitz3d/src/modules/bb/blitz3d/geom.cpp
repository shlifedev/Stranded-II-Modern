
#include "std.h"
#include "geom.h"

Matrix Matrix::tmps[64];
Transform Transform::tmps[64];

Quat rotationQuat( float p,float y,float r ){
	return yawQuat(y)*pitchQuat(p)*rollQuat(r);
}

