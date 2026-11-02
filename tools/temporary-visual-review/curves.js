/* Shape-preserving cubic curves. Shared by editor, preview and shader compiler. */
(function(root) {
'use strict';
const channels=['master','red','green','blue'];
const identity=()=>Object.fromEntries(channels.map(c=>[c,[[0,0],[1,1]]]));
function validate(curves) {
  if(!curves||typeof curves!=='object'||Array.isArray(curves))throw Error('Invalid curves');
  for(const channel of channels) {
    const points=curves[channel];
    if(!Array.isArray(points)||points.length<2||points.length>16)throw Error('Each curve needs 2–16 points');
    for(let i=0;i<points.length;i++) {
      const p=points[i];
      if(!Array.isArray(p)||p.length!==2||p.some(v=>!Number.isFinite(v)||v<0||v>1)||i>0&&p[0]-points[i-1][0]<1/255-1e-10)throw Error('Curve points must be normalized and ordered at least 1/255 apart');
    }
    if(points[0][0]!==0||points.at(-1)[0]!==1)throw Error('Curve input endpoints must be 0 and 1');
  }
  return curves;
}
function compile(points) {
  const h=[],d=[],m=[];
  for(let i=0;i<points.length-1;i++){h[i]=points[i+1][0]-points[i][0];d[i]=(points[i+1][1]-points[i][1])/h[i]}
  m[0]=d[0];m[points.length-1]=d.at(-1);
  for(let i=1;i<points.length-1;i++){
    const a=d[i-1],b=d[i],w1=2*h[i]+h[i-1],w2=h[i]+2*h[i-1];
    m[i]=a*b<=0?0:(w1+w2)/(w1/a+w2/b);
  }
  return h.map((width,i)=>({x:points[i][0],end:points[i+1][0],width,a:points[i][1],b:width*m[i],c:3*(points[i+1][1]-points[i][1])-width*(2*m[i]+m[i+1]),d:2*(points[i][1]-points[i+1][1])+width*(m[i]+m[i+1])}));
}
function evaluate(segments,x) {
  x=Math.max(0,Math.min(1,x));
  const s=segments.find(s=>x<=s.end)||segments.at(-1),t=(x-s.x)/s.width;
  return Math.max(0,Math.min(1,((s.d*t+s.c)*t+s.b)*t+s.a));
}
const cache=new WeakMap();
const neutral=identity();
function prepare(curves=neutral) {
  // Curve objects are immutable: edits replace the object rather than mutating cached points.
  if(!cache.has(curves)){validate(curves);for(const channel of channels){curves[channel].forEach(Object.freeze);Object.freeze(curves[channel])}Object.freeze(curves);cache.set(curves,Object.fromEntries(channels.map(c=>[c,compile(curves[c])])))}
  return cache.get(curves);
}
function apply(prepared,rgb){return rgb.map((v,i)=>evaluate(prepared[channels[i+1]],evaluate(prepared.master,v)))}
const n=v=>Number.isInteger(v)?v+'.0':String(v);
function glsl(curves) {
  const prepared=prepare(curves);
  return channels.map(channel=>`float curve_${channel}(float x) {\n    x = clamp(x, 0.0, 1.0);\n`+prepared[channel].map((s,i)=>`    ${i===prepared[channel].length-1?'':`if (x <= ${n(s.end)}) `}{ float t = (x - ${n(s.x)}) / ${n(s.width)}; return clamp(((${n(s.d)} * t + ${n(s.c)}) * t + ${n(s.b)}) * t + ${n(s.a)}, 0.0, 1.0); }`).join('\n')+'\n}').join('\n');
}
root.ShaderCurves={channels,identity,validate,compile,evaluate,prepare,apply,glsl};
if(typeof module!=='undefined')module.exports=root.ShaderCurves;
})(globalThis);
