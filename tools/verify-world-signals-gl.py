#!/usr/bin/env python3
"""Actual raw lightmap -> native terrain -> detector -> grade on Mesa EGL.
No Minecraft window. Usage: <client.jar> <visual-fixtures> <context-fixtures>.
"""
import runpy
import struct
import sys
from pathlib import Path
context_root=Path(sys.argv[3])
sys.argv=sys.argv[:3]
gl=runpy.run_path(str(Path(__file__).with_name('verify-visual-biomes-gl.py')))
globals().update({k:v for k,v in gl.items() if not k.startswith('__')})

current_root=context_root

def exported_includes(source):
    def sub(m):
        path=current_root/'assets/minecraft/shaders/include'/m[1]
        return exported_includes(path.read_text() if path.exists() else client.read('assets/minecraft/shaders/include/'+m[1]).decode())
    return re.sub(r'#include <minecraft:([^>]+)>',sub,source)
program.__globals__['includes']=exported_includes

def bind_ubo(prog,name,binding,data):
    index=block_index(prog,name.encode())
    if index==0xffffffff:return
    block_binding(prog,index,binding)
    buf=U();gen_buffer(1,C.byref(buf));bind_buffer(0x8A11,buf.value)
    contents=C.create_string_buffer(data);buffer_data(0x8A11,len(data),contents,0x88E4);bind_base(0x8A11,binding,buf.value)

vertex_attrib_pointer=glfn('glVertexAttribPointer',None,U,I,U,U,I,P)
enable_attrib=glfn('glEnableVertexAttribArray',None,U)
vertex_attrib_4f=glfn('glVertexAttrib4f',None,U,F,F,F,F)
vertex_attrib_i2i=glfn('glVertexAttribI2i',None,U,I,I)
positions=(F*9)(-1,-1,0,3,-1,0,-1,3,0)
buf=U();gen_buffer(1,C.byref(buf));bind_buffer(0x8892,buf.value);buffer_data(0x8892,C.sizeof(positions),positions,0x88E4)
vertex_attrib_pointer(0,3,0x1406,0,0,None);enable_attrib(0)
vertex_attrib_4f(1,128/255,204/255,26/255,1)
vertex_attrib_4f(2,.5,.5,0,1)
vertex_attrib_i2i(3,240,240)
white=texture(1,1,data=(C.c_ubyte*4)(255,255,255,255))
surface=texture(1,1,data=(C.c_ubyte*4)(26,38,51,255))
light_target=target(16,16)
scene=target(32,32);env=target(1,1);output=target(32,32)
depth=texture(32,32,True,(F*1024)(*([.5]*1024)))
identity=[float(i%5==0) for i in range(16)]
checks=0

def clear_other_half():
    bind_fbo(0x8D40,scene[0]);enable(0x0C11);scissor(0,16,32,16);clear_color(.1,.15,.2,1);clear(0x4000);disable(0x0C11)

def fog_values(source,matching=True):
    colour=[192/255,216/255,1,1]
    distances=[1000,1e6,1000,1e6,1e6,1e6]
    lookup={'FOG_START':0,'FOG_END':1,'SKY_FOG_END':4,'CLOUD_FOG_END':5}
    if source in lookup:distances[lookup[source]]=150 if matching else 300
    if source=='FOG_COLOR' and not matching:colour=[1,0,0,1]
    return struct.pack('<12f',*colour,*distances,0,0)

def lights(source,matching=True):
    # std140: 6 scalars, vec3 starts at byte 32, then a 16-byte stride.
    tint=[1,216/255,140/255];sky=[1,1,1];ambient=[10/255]*3;night=[153/255]*3
    if not matching:
        if source=='AMBIENT_COLOR':ambient=[.25,.1,.1]
        if source=='BLOCK_LIGHT_TINT':tint=[.25,.1,.1]
        if source=='SKY_LIGHT_COLOR':sky=[.25,.1,.1]
        if source=='NIGHT_VISION_COLOR':night=[.25,.1,.1]
    return struct.pack('<24f',0,1,0,0,0,0,0,0,*tint,0,*sky,0,*ambient,0,*night,0)

cases=json.loads((context_root/'cases.json').read_text())
for case in cases:
    current_root=context_root/case['pack']
    v=(current_root/'assets/minecraft/shaders/core/terrain.vsh').read_text()
    f=(current_root/'assets/minecraft/shaders/core/terrain.fsh').read_text()
    terrain=program(exported_includes(v),f)
    cutout=program(exported_includes(v).replace('#version 330','#version 330\n#define ALPHA_CUTOUT 0.5'),f.replace('#version 330','#version 330\n#define ALPHA_CUTOUT 0.5'))
    translucent=program(exported_includes(v).replace('#version 330','#version 330\n#define ALPHA_CUTOUT 0.1'),f.replace('#version 330','#version 330\n#define ALPHA_CUTOUT 0.1'))
    detector=program(quad,(current_root/'assets/shader_maker/shaders/post/detect_context.fsh').read_text())
    grade=program(quad,(current_root/'assets/shader_maker/shaders/post/grade.fsh').read_text())
    lightfile=current_root/'assets/minecraft/shaders/core/lightmap.fsh'
    light=program(quad,lightfile.read_text()) if lightfile.exists() else None
    for prog in [terrain,cutout,translucent]:
        bind_ubo(prog,'Globals',0,struct.pack('<3if3ff2f2i',0,0,0,0,0,0,0,0,32,32,0,0))
        bind_ubo(prog,'Projection',1,struct.pack('<16f',*identity))
        bind_ubo(prog,'TerrainUniform',2,struct.pack('<16f4i',*identity,1,1,0,0))
        bind_ubo(prog,'ChunkSection',3,struct.pack('<3if',0,0,0,1))
    for matching in [True,False]:
        vertex_attrib_4f(1,*( [128/255,204/255,26/255,1] if matching or case['source']!='SURFACE_TINT' else [1,0,0,1]))
        lm=white
        if light:
            bind_ubo(light,'LightmapInfo',4,lights(case['source'],matching));render(light,light_target);lm=light_target[1]
            assert pixel(light_target)[3]==(30 if matching else 15),('Raw lighting classifier',case,matching,pixel(light_target));checks+=1
        for prog in [terrain,cutout]:
            bind_ubo(prog,'Fog',5,fog_values(case['source'],matching))
            render(prog,scene,[('Sampler0',surface),('Sampler2',lm)])
            before=pixel(scene)
            assert before[3]==(30 if matching else 15),('Terrain marker',case,matching,before);checks+=1
            clear_other_half()
            render(detector,env,[('InSampler',scene[1]),('DepthSampler',depth)])
            assert pixel(env)[0]==(255 if matching else 0),('Detection',case,matching,pixel(env));checks+=1
            render(grade,output,[('InSampler',scene[1]),('ContextSampler',env[1]),('DepthSampler',depth)])
            actual=pixel(output)
            expected=[min(255,2*c) if matching else c for c in before[:3]]
            assert max(abs(a-b) for a,b in zip(actual[:3],expected))<=2,('Blocks grade',case,matching,before,actual,expected)
            assert actual[3]==255,('Alpha restoration',case,actual);checks+=2
            other=pixel(output,0,31)
            expected=[52,76,102] if matching and not case['blocksOnly'] else [26,38,51]
            assert max(abs(a-b) for a,b in zip(other[:3],expected))<=1,('Full-screen versus blocks scope',case,matching,other,expected);checks+=1
    # Sorted translucency must retain native alpha, including alpha 1: same shader has a different cutout define.
    bind_ubo(translucent,'Fog',5,fog_values(case['source']))
    render(translucent,scene,[('Sampler0',surface),('Sampler2',lm)])
    assert pixel(scene)[3]==255,('Translucent alpha was marked',case,pixel(scene));checks+=1

# Every four-bit combination, with a simultaneously installed sky signal and its own post target.
current_root=context_root/'multi'
terrain=program(exported_includes((current_root/'assets/minecraft/shaders/core/terrain.vsh').read_text()),(current_root/'assets/minecraft/shaders/core/terrain.fsh').read_text())
detector=program(quad,(current_root/'assets/shader_maker/shaders/post/detect_context.fsh').read_text())
grade=program(quad,(current_root/'assets/shader_maker/shaders/post/grade.fsh').read_text())
empty_sky=texture(1,1,data=(C.c_ubyte*4)(0,0,0,0))
vertex_attrib_4f(1,128/255,204/255,26/255,1)
bind_ubo(terrain,'Globals',0,struct.pack('<3if3ff2f2i',0,0,0,0,0,0,0,0,32,32,0,0))
bind_ubo(terrain,'Projection',1,struct.pack('<16f',*identity))
bind_ubo(terrain,'TerrainUniform',2,struct.pack('<16f4i',*identity,1,1,0,0))
bind_ubo(terrain,'ChunkSection',3,struct.pack('<3if',0,0,0,1))
for mask in range(16):
    numbers=[150 if mask&(1<<i) else 1000 for i in range(4)]
    bind_ubo(terrain,'Fog',5,struct.pack('<12f',.2,.3,.4,1,numbers[0],numbers[1],1000,1e6,numbers[2],numbers[3],0,0))
    render(terrain,scene,[('Sampler0',surface),('Sampler2',white)])
    before=pixel(scene)
    assert before[3]==15*(1+mask),('Four-bit packing',mask,before)
    render(detector,env,[('InSampler',scene[1]),('DepthSampler',depth)])
    assert pixel(env)==[255 if mask&(1<<i) else 0 for i in range(4)],('Independent context bits',mask,pixel(env))
    render(grade,output,[('InSampler',scene[1]),('ContextSampler',env[1]),('DepthSampler',depth),('EnvironmentSampler',empty_sky)])
    expected=[min(255,c*2) if mask else c for c in before[:3]]
    assert max(abs(a-b) for a,b in zip(pixel(output)[:3],expected))<=2,('Four-bit layer grading',mask,pixel(output),expected)
    checks+=3

# Quorum rejects isolated valid codes; all-absent next frame cannot retain a positive result.
for samples in [3,2,1,0]:
    data=[26,38,51,255]*1024
    for x in range(samples):data[x*4+3]=30
    active_texture(0x84C0);bind_texture(0x0DE1,scene[1]);tex_image(0x0DE1,0,0x8058,32,32,0,0x1908,0x1401,(C.c_ubyte*len(data))(*data))
    render(detector,env,[('InSampler',scene[1]),('DepthSampler',depth)])
    assert pixel(env)[0]==(255 if samples>=3 else 0),('Terrain quorum / no stale state',samples,pixel(env));checks+=1

# Empty/sky depth cannot carry terrain conditions, and no state survives the next frame.
for alpha in [0,1,30/255]:
    bind_fbo(0x8D40,scene[0]);clear_color(.1,.15,.2,alpha);clear(0x4000)
    active_texture(0x84C0);bind_texture(0x0DE1,depth);tex_image(0x0DE1,0,0x8CAC,32,32,0,0x1902,0x1406,(F*1024)(*([0]*1024)))
    render(detector,env,[('InSampler',scene[1]),('DepthSampler',depth)])
    assert pixel(env)==[0,0,0,0],('Sky/empty depth activated context',alpha,pixel(env));checks+=1
print(f'World signal raster: {checks} checks, {len(cases)} exports; actual lightmap/terrain/solid/cutout/sorted-translucent/grade, all input types and both scopes.')
print('Synthetic native bindings on Mesa EGL; clean Minecraft OpenGL/Vulkan device validation remains pending.')
