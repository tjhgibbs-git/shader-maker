#!/usr/bin/env python3
"""Production fog fragments on synthetic inputs; actual vertex/fragment linking; custom sky transport.
Runs existing visual-sky regression fixtures in the same EGL context to reuse that harness.
Usage: verify-fog-gl.py <client.jar> <visual-fixtures> <fog-fixtures>
This is software raster validation, not Minecraft pipeline or clean-client validation.
"""
import runpy
import struct
import sys
from pathlib import Path
fog_root = Path(sys.argv[3])
sys.argv = sys.argv[:3]
gl = runpy.run_path(str(Path(__file__).with_name('verify-visual-biomes-gl.py')))
globals().update({k: v for k, v in gl.items() if not k.startswith('__')})
# run_path's own globals remain attached to helper functions; their EGL context is still current.
uniform_3f = glfn('glUniform3f', None, I, F, F, F)
uniform_1f = glfn('glUniform1f', None, I, F)
synthetic = '''#version 330
#extension GL_ARB_separate_shader_objects : require
uniform vec3 TestRay;
uniform float TestVisibility;
layout(location=0) out float sphericalVertexDistance;
layout(location=1) out float cylindricalVertexDistance;
layout(location=2) out vec4 vertexColor;
layout(location=3) out vec2 texCoord0;
layout(location=4) out float chunkVisibility;
layout(location=5) out vec3 smFogPosition;
void main(){vec2 p=vec2(float((gl_VertexID<<1)&2),float(gl_VertexID&2));gl_Position=vec4(p*2.0-1.0,0,1);
smFogPosition=TestRay;sphericalVertexDistance=length(TestRay);cylindricalVertexDistance=max(length(TestRay.xz),abs(TestRay.y));
vertexColor=vec4(1);texCoord0=vec2(0.5);chunkVisibility=TestVisibility;}
'''

def bind_ubo(prog, name, binding, data):
    index = block_index(prog, name.encode())
    if index == 0xFFFFFFFF:
        return
    block_binding(prog, index, binding)
    buf = U()
    gen_buffer(1, C.byref(buf))
    bind_buffer(0x8A11, buf.value)
    contents = C.create_string_buffer(data)
    buffer_data(0x8A11, len(data), contents, 0x88E4)
    bind_base(0x8A11, binding, buf.value)
    return buf.value

identity = [float(i % 5 == 0) for i in range(16)]
source = texture(1, 1, data=(C.c_ubyte * 4)(51, 76, 102, 179))
dest = target(1, 1)
programs = {}
linked = 0
for directory in sorted(fog_root.iterdir()):
    fragment = directory / 'assets/minecraft/shaders/core/terrain.fsh'
    if not fragment.exists():
        continue
    vertex = directory / 'assets/minecraft/shaders/core/terrain.vsh'
    # Link the production shader pair too, even though the deterministic raster uses synthetic varyings.
    program(includes(vertex.read_text()), fragment.read_text())
    linked += 1
    programs[directory.name] = program(synthetic, fragment.read_text())

cases = json.loads((fog_root / 'cases.json').read_text())
import math
checks = 0
max_error = 0
for case in cases:
    prog = programs[case['pack']]
    camera = case['camera']
    blocks = [math.floor(x) for x in camera]
    offset = [b-c for b, c in zip(blocks, camera)]
    use(prog)
    uniform_3f(get_uniform(prog, b'TestRay'), *case['ray'])
    uniform_1f(get_uniform(prog, b'TestVisibility'), 1)
    bind_ubo(prog, 'Globals', 0, struct.pack('<3if3ff2f2i', *blocks, 0, *offset, 0, 1, 1, 0, 0))
    bind_ubo(prog, 'Fog', 1, struct.pack('<12f', .7, .8, .9, 1, 0, 1e30, 0, 1e30, 1e30, 1e30, 0, 0))
    bind_ubo(prog, 'TerrainUniform', 2, struct.pack('<16f4i', *identity, 1, 1, 0, 0))
    render(prog, dest, [('Sampler0', source)])
    actual = pixel(dest)
    t = case['transmission']
    expected = [round((s*t+f*(1-t))*255) for s, f in zip([51/255, 76/255, 102/255], [.7, .8, .9])]
    error = max(abs(a-b) for a, b in zip(actual[:3], expected))
    max_error = max(max_error, error)
    assert error <= 2, (case, actual, expected)
    assert actual[3] == 179, ('Surface alpha changed', case, actual)
    checks += 2

# Native fog is deliberately retained after the authored medium, and chunk fade still wins at zero visibility.
prog = programs['everywhere']
use(prog)
uniform_3f(get_uniform(prog, b'TestRay'), 0, 0, 64)
bind_ubo(prog, 'Fog', 1, struct.pack('<12f', .1, .2, .3, 1, 0, 1, 0, 1, 1, 1, 0, 0))
render(prog, dest, [('Sampler0', source)])
assert all(abs(a-b) <= 1 for a, b in zip(pixel(dest), [26, 51, 76, 179])), 'Native fog must remain after authored fog'
checks += 1

# Custom sky: production sky override -> reduction -> actual grade, including alpha restoration.
custom_root = fog_root / 'custom-sky'
custom_sky = program(sky_vertex, (custom_root / 'assets/minecraft/shaders/core/sky.fsh').read_text())
custom_grade = program(quad, (custom_root / 'assets/shader_maker/shaders/post/grade.fsh').read_text())
bind_ubo(custom_sky, 'Fog', 0, struct.pack('<12f', 0, 0, 0, 1, 0, 1e9, 0, 1e9, 1e9, 1e9, 0, 0))
# Restore the sky half's depth after the previous visual regression occlusion test.
active_texture(0x84C0)
bind_texture(0x0DE1, depth)
tex_image(0x0DE1, 0, 0x8CAC, 32, 32, 0, 0x1902, 0x1406, depth_values)
for rgb, expected_code in [([1, 128/255, 64/255], 15), ([.5, 64/255, 32/255], 15), ([0, 0, 0], 0), ([.2, .3, .8], 0)]:
    bind_ubo(custom_sky, 'DynamicTransforms', 1, struct.pack('<40f', *identity, *identity, *rgb, 1, 0, 0, 0, 0))
    render(custom_sky, scene)
    clear_terrain()
    assert sensor() == expected_code, ('Custom sky signal', rgb, sensor())
    render(custom_grade, output, [('InSampler', scene[1]), ('EnvironmentSampler', environment[1]), ('DepthSampler', depth)])
    expected = [52, 76, 102] if expected_code else [26, 38, 51]
    assert all(abs(a-b) <= 1 for a, b in zip(pixel(output)[:3], expected)), ('Custom full-screen grade', rgb, pixel(output))
    assert pixel(output, 0, 31)[3] == 255
    checks += 3
print(f'Fog raster: {checks} assertions, {len(cases)} ray/camera cases, {linked} actual terrain pairs linked; max RGB error {max_error}/255.')
print('Includes custom sky transport, full-screen grade, surface/sky alpha, native fog retention; Minecraft device/OIT/window checks still required.')
