const assert=require('node:assert/strict');
const fs=require('node:fs');
const {pixel,fragment,pack,zip}=require('../tools/temporary-visual-review/pipeline.js');
const s={shoulder:0,vibrance:0,exposure:0,black:0,white:1,gamma:1,contrast:1,saturation:1,red:1,green:1,blue:1,vignette:0,opacity:1,altitudeEnabled:false,timeEnabled:false,facingEnabled:false,biome:'',pulsePeriod:0,pulseDepth:0};
assert.deepEqual(pixel(.2,.4,.8,0,0,s,1),[.2,.4,.8]);
assert.deepEqual(pixel(.2,.4,.8,1,1,{...s,exposure:3},0),[.2,.4,.8]);
assert.ok(pixel(.8,.8,.8,0,0,{...s,exposure:1,shoulder:1},1)[0]<pixel(.9,.9,.9,0,0,{...s,exposure:1,shoulder:1},1)[0]);
for(const key of ['altitudeEnabled','timeEnabled','facingEnabled','biome','pulsePeriod']) {
  const value=key==='biome'?'minecraft:dappled_forest':key==='pulsePeriod'?2:true;
  assert.throws(()=>pack({...s,[key]:value,pulseDepth:1}),/cannot preserve/);
}
assert.throws(()=>pack({...s,gamma:NaN}),/Invalid gamma/);
assert.throws(()=>pack({...s,black:.8,white:.2}),/Invalid levels/);
const always=pack(s),command=pack(s,'command');
assert.deepEqual(JSON.parse(always['pack.mcmeta']).pack.min_format,[97,1]);
assert.ok(always['assets/minecraft/post_effect/end_of_frame.json']);
assert.ok(command['assets/shader_maker/post_effect/grade.json']);
assert.ok(!command['assets/minecraft/post_effect/end_of_frame.json']);
const effect=JSON.parse(always['assets/minecraft/post_effect/end_of_frame.json']);
assert.equal(effect.passes[0].output,effect.passes[1].inputs[0].target);
assert.notEqual(effect.passes[0].inputs[0].target,effect.passes[0].output);
assert.equal(effect.passes[1].output,'minecraft:main');
assert.deepEqual(zip(always),zip(pack(s)));
fs.mkdirSync('out',{recursive:true});fs.writeFileSync('out/resources.zip',zip(always));
console.log('Pipeline and 26.3 pack checks passed');
