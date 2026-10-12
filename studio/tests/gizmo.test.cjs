'use strict';
const {test}=require('node:test');
const assert=require('node:assert/strict');
const math=require('../engine/gizmo.js');
test('axis dragging uses perspective depth and CSS-scaled canvas coordinates',()=>{
  const axis=math.axis([100,100,100],[180,100,200],.5,.5);
  // At world +32, projected displacement is 2/3 of the endpoint (+64).
  assert.ok(Math.abs(math.distance(axis,40*2/3,0)-32)<1e-9);
  assert.equal(math.distance(axis,40,0),64);
  assert.equal(math.distance(axis,100,0),null); // Cannot cross the camera plane.
  assert.equal(math.axis([0,0,-1],[100,0,1]),null);
  assert.equal(math.axis([0,0,1],[1,1,1]),null); // Nearly view-aligned axis.
});
test('snapping uses the active anchor and only changes the requested world axis',()=>{
  assert.deepEqual(math.delta('x',20,{x:10,y:40,z:5},16),{x:22,y:0,z:0});
  assert.deepEqual(math.delta('y',-13,{x:10,y:40,z:5},16),{x:0,y:-8,z:0});
  assert.deepEqual(math.delta('z',10.5,{x:10,y:40,z:5},0),{x:0,y:0,z:10.5});
});
