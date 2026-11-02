/* Display-referred grading reference and Minecraft Java 26.3 pack compiler. */
(function(root) {
'use strict';
const clamp=x=>Math.max(0,Math.min(1,x));
function level(v,s) {
  v=Math.max(0,(v*2**s.exposure-s.black)/(s.white-s.black));
  // Optional exponential shoulder above 75%; blend zero preserves v1 projects.
  const soft=v<=.75?v:.75+.25*(1-Math.exp(-(v-.75)/.25));
  v=v+(soft-v)*(s.shoulder||0);
  return clamp((Math.pow(clamp(v),1/s.gamma)-.5)*s.contrast+.5);
}
function pixel(r,g,b,x,y,s,amount) {
  const src=[r,g,b],rgb=src.map(v=>level(v,s));
  const l=.2126*rgb[0]+.7152*rgb[1]+.0722*rgb[2];
  const chroma=Math.max(...rgb)-Math.min(...rgb);
  const saturation=s.saturation*(1+(s.vibrance||0)*(1-chroma));
  const shade=1-s.vignette*clamp(Math.hypot(x,y)/Math.SQRT2)**2;
  return rgb.map((v,i)=>src[i]+(clamp((l+(v-l)*saturation)*[s.red,s.green,s.blue][i]*shade)-src[i])*clamp(amount));
}
function number(v){if(!Number.isFinite(v))throw Error('Non-finite shader setting');return Number.isInteger(v)?v+'.0':String(v)}
function fragment(s) {
  const n=k=>number(s[k]||0);
  return `#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D InSampler;
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;
vec3 levels(vec3 c) {
    c = max(vec3(0.0), (c * exp2(${n('exposure')}) - ${n('black')}) / (${n('white')} - ${n('black')}));
    vec3 soft = mix(c, vec3(0.75) + 0.25 * (1.0 - exp(-(c - 0.75) / 0.25)), greaterThan(c, vec3(0.75)));
    c = mix(c, soft, ${n('shoulder')});
    return clamp((pow(clamp(c, 0.0, 1.0), vec3(1.0 / ${n('gamma')})) - 0.5) * ${n('contrast')} + 0.5, 0.0, 1.0);
}
void main() {
    vec4 source = texture(InSampler, texCoord);
    vec3 c = levels(source.rgb);
    float luma = dot(c, vec3(0.2126, 0.7152, 0.0722));
    float chroma = max(max(c.r,c.g),c.b) - min(min(c.r,c.g),c.b);
    float sat = ${n('saturation')} * (1.0 + ${n('vibrance')} * (1.0 - chroma));
    vec2 position = texCoord * 2.0 - 1.0;
    float edge = clamp(length(position) / sqrt(2.0), 0.0, 1.0);
    float shade = 1.0 - ${n('vignette')} * edge * edge;
    c = clamp((vec3(luma) + (c - luma) * sat) * vec3(${n('red')},${n('green')},${n('blue')}) * shade, 0.0, 1.0);
    fragColor = vec4(mix(source.rgb, c, ${n('opacity')}), source.a);
}
`;
}
function issues(s) {
  return [s.altitudeEnabled&&'Altitude mask',s.timeEnabled&&'Time mask',s.facingEnabled&&'Facing mask',s.biome.trim()&&'Biome mask',s.pulsePeriod>0&&s.pulseDepth>0&&'Animated pulse'].filter(Boolean);
}
function validate(s) {
  const bounds={exposure:[-3,3],black:[0,.95],white:[.05,1],gamma:[.2,3],contrast:[0,2],saturation:[0,2],red:[0,2],green:[0,2],blue:[0,2],vignette:[0,1],opacity:[0,1]};
  for(const [key,[min,max]] of Object.entries(bounds))
    if(!Number.isFinite(s[key])||s[key]<min||s[key]>max)throw Error('Invalid '+key);
  for(const key of ['shoulder','vibrance'])if(s[key]!==undefined&&(!Number.isFinite(s[key])||s[key]<(key==='vibrance'?-1:0)||s[key]>1))throw Error('Invalid '+key);
  if(s.black>=s.white||typeof s.biome!=='string')throw Error('Invalid levels or biome');
}
function pack(s,mode='always') {
  validate(s);
  const unsupported=issues(s);
  if(unsupported.length)throw Error('Vanilla export cannot preserve: '+unsupported.join(', ')+'. Disable these conditions explicitly before export.');
  if(!['always','command'].includes(mode))throw Error('Unknown activation mode');
  const pass=(shader,input,output,uniforms)=>({vertex_shader:'minecraft:core/screenquad',fragment_shader:shader,inputs:[{sampler_name:'In',target:input}],output,...(uniforms?{uniforms}:{})});
  const effect={targets:{swap:{}},passes:[pass('shader_maker:post/grade','minecraft:main','swap'),pass('minecraft:post/blit','swap','minecraft:main',{BlitConfig:[{name:'ColorModulate',type:'vec4',value:[1,1,1,1]}]})]};
  const path=mode==='always'?'minecraft/post_effect/end_of_frame.json':'shader_maker/post_effect/grade.json';
  return {'pack.mcmeta':JSON.stringify({pack:{description:'Shader Maker · Java 26.3',min_format:[97,1],max_format:[97,1]}},null,2),['assets/'+path]:JSON.stringify(effect,null,2),'assets/shader_maker/shaders/post/grade.fsh':fragment(s),'shader-maker-project.json':JSON.stringify({format:'shader-maker-project',version:2,target:'26.3',state:s},null,2),'README.txt':`Minecraft Java 26.3 only. Pack format 97.1. Experimental: in-game smoke test still required.\nInstall this ZIP in resourcepacks and enable it.\n${mode==='always'?'Applies automatically. Disable the pack to remove it. Overrides other end_of_frame effects; pack priority decides.':'Activate: /posteffect add @s shader_maker:grade\nRemove: /posteffect remove @s shader_maker:grade\nCommands require permission.'}\nFull-screen color grading only; no lighting, shadows, depth fog or context conditions.\n`};
}
// Small dependency-free ZIP writer (STORE). UTF-8 names, CRC32, fixed dates for reproducibility.
function zip(files) {
  const enc=new TextEncoder(),chunks=[],central=[];let offset=0;
  const crc=data=>{let c=0xffffffff;for(const b of data){c^=b;for(let j=0;j<8;j++)c=(c>>>1)^((c&1)?0xedb88320:0)}return (c^0xffffffff)>>>0};
  function header(size){let b=new Uint8Array(size);return [b,new DataView(b.buffer)]}
  for(const name of Object.keys(files).sort()) {
    const filename=enc.encode(name),data=enc.encode(files[name]),sum=crc(data);
    let [h,v]=header(30);v.setUint32(0,0x04034b50,true);v.setUint16(4,20,true);v.setUint16(6,0x800,true);v.setUint16(12,33,true);v.setUint32(14,sum,true);v.setUint32(18,data.length,true);v.setUint32(22,data.length,true);v.setUint16(26,filename.length,true);
    chunks.push(h,filename,data);
    let [c,w]=header(46);w.setUint32(0,0x02014b50,true);w.setUint16(4,20,true);w.setUint16(6,20,true);w.setUint16(8,0x800,true);w.setUint16(14,33,true);w.setUint32(16,sum,true);w.setUint32(20,data.length,true);w.setUint32(24,data.length,true);w.setUint16(28,filename.length,true);w.setUint32(42,offset,true);central.push(c,filename);offset+=h.length+filename.length+data.length;
  }
  let size=central.reduce((a,b)=>a+b.length,0),[end,v]=header(22);v.setUint32(0,0x06054b50,true);v.setUint16(8,central.length/2,true);v.setUint16(10,central.length/2,true);v.setUint32(12,size,true);v.setUint32(16,offset,true);
  let result=new Uint8Array(offset+size+22),at=0;for(const b of [...chunks,...central,end]){result.set(b,at);at+=b.length}return result;
}
root.ShaderPipeline={pixel,fragment,pack,zip,issues};
if(typeof module!=='undefined')module.exports=root.ShaderPipeline;
})(globalThis);
