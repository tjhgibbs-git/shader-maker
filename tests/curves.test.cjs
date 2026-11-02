'use strict';
const assert=require('node:assert/strict');
const C=require('../tools/temporary-visual-review/curves.js');
const P=require('../tools/temporary-visual-review/pipeline.js');
const History=require('../tools/temporary-visual-review/history.js');
const identity=C.identity();
for(let i=0;i<=1000;i++)assert.ok(Math.abs(C.evaluate(C.prepare(identity).master,i/1000)-i/1000)<1e-12);
const s={...identity,master:[[0,0],[.25,.19],[.75,.81],[1,1]]};
const curve=C.prepare(s).master;assert.ok(Math.abs(C.evaluate(curve,.25)-.19)<1e-12);assert.ok(Math.abs(C.evaluate(curve,.75)-.81)<1e-12);
assert.ok(Math.abs(C.evaluate(curve,.5)-.5)<1e-12);
let previous=0;
for(let i=0;i<=1000;i++){const v=C.evaluate(curve,i/1000);assert.ok(v>=previous);previous=v}
const nonmonotone=[[0,1],[.3,.2],[.5,.8],[.7,.1],[1,.9]],compiled=C.compile(nonmonotone);
for(let i=0;i<nonmonotone.length-1;i++)for(let j=0;j<=100;j++){
 const a=nonmonotone[i],b=nonmonotone[i+1],v=C.evaluate(compiled,a[0]+(b[0]-a[0])*j/100);
 assert.ok(v>=Math.min(a[1],b[1])-1e-12&&v<=Math.max(a[1],b[1])+1e-12,'Local overshoot');
}
assert.throws(()=>C.validate({...identity,red:[[0,0],[.5,.5],[.5,.6],[1,1]]}),/ordered/);
assert.throws(()=>C.validate({...identity,blue:[[0,NaN],[1,1]]}),/normalized/);
assert.throws(()=>C.validate({...identity,master:[[.1,0],[1,1]]}),/endpoints/);
assert.throws(()=>C.validate({...identity,green:Array.from({length:17},(_,i)=>[i/16,i/16])}),/2–16/);
assert.throws(()=>C.validate([]),/Invalid curves/);
const values=C.apply(C.prepare({...identity,master:[[0,.1],[1,1]],red:[[0,0],[1,.8]]}),[.25,.5,.75]);
[.26,.55,.775].forEach((v,i)=>assert.ok(Math.abs(values[i]-v)<1e-12));
const stats=P.histogram(new Uint8ClampedArray([0,32,255,255,128,128,128,128,0,0,0,0]));
assert.equal(stats.total,2);assert.equal(stats.shadows,1);assert.equal(stats.highlights,1);assert.equal(stats.channels[1][128],1);
const initial={curves:C.identity(),exposure:0},history=new History(initial,3);
initial.exposure=9;assert.equal(history.undo().exposure,0);assert.equal(history.canUndo,false);
history.commit({curves:C.identity(),exposure:1});assert.equal(history.commit({curves:C.identity(),exposure:1}),false);
const second={curves:C.identity(),exposure:2};history.commit(second);second.curves.red[0][1]=.5;
const restored=history.undo();assert.equal(restored.exposure,1);assert.equal(history.redo().curves.red[0][1],0);
history.undo();history.commit({curves:C.identity(),exposure:3});assert.equal(history.canRedo,false);
history.commit({curves:C.identity(),exposure:4});assert.equal(history.entries.length,3);
console.log('Curve interpolation, histogram and history checks passed');
