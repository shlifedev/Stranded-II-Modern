/* Projection and snapping shared by the overlay and non-graphical tests. */
'use strict';
const StudioGizmo={
  axis(origin,end,scaleX=1,scaleY=1){
    const x=(end[0]-origin[0])*scaleX,y=(end[1]-origin[1])*scaleY,length=Math.hypot(x,y);
    if(origin[2]<=0||end[2]<=0||length<4)return null;
    return {x:x/length,y:y/length,length,z0:origin[2],z1:end[2]};
  },
  distance(axis,dx,dy){
    const screen=dx*axis.x+dy*axis.y,denominator=axis.length*axis.z1-screen*(axis.z1-axis.z0);
    if(denominator<=.001)return null;
    return 64*screen*axis.z0/denominator;
  },
  snapDelta(delta,anchor,snap){return snap?Math.round((anchor+delta)/snap)*snap-anchor:delta;},
  delta(axis,amount,anchor,snap){
    return {x:axis==='x'?this.snapDelta(amount,anchor.x,snap):0,
      y:axis==='y'?this.snapDelta(amount,anchor.y,snap):0,
      z:axis==='z'?this.snapDelta(amount,anchor.z,snap):0};
  }
};
if(typeof module!=='undefined')module.exports=StudioGizmo;
