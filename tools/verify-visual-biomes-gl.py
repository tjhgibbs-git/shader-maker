#!/usr/bin/env python3
"""Raster-test exported production sky/detection/grade shaders using surfaceless Mesa EGL.

Usage: python3 tools/verify-visual-biomes-gl.py <26.3-client.jar> <fixture-directory>
Generate fixtures with tools/VisualBiomeRenderFixtures.java against offline production classes.
No Minecraft window or client mod is used. This does not validate Minecraft's device bindings.
"""
import ctypes as C
import json
from pathlib import Path
import re
import sys
import zipfile

client = zipfile.ZipFile(sys.argv[1])
root = Path(sys.argv[2])
egl = C.CDLL('libEGL.so.1')
egl.eglGetProcAddress.argtypes = [C.c_char_p]
egl.eglGetProcAddress.restype = C.c_void_p

def eglfn(name, result, *args):
    f = getattr(egl, name)
    f.restype, f.argtypes = result, list(args)
    return f

def glfn(name, result, *args):
    pointer = egl.eglGetProcAddress(name.encode())
    if not pointer:
        raise RuntimeError('Missing ' + name)
    return C.CFUNCTYPE(result, *args)(pointer)

I, U, P, F = C.c_int, C.c_uint, C.c_void_p, C.c_float
platform = C.CFUNCTYPE(P, U, P, C.POINTER(I))(egl.eglGetProcAddress(b'eglGetPlatformDisplayEXT'))
display = platform(0x31DD, None, None)  # EGL_PLATFORM_SURFACELESS_MESA
major, minor = I(), I()
assert eglfn('eglInitialize', U, P, C.POINTER(I), C.POINTER(I))(display, C.byref(major), C.byref(minor))
assert eglfn('eglBindAPI', U, U)(0x30A2)  # desktop OpenGL
config, count = P(), I()
attrs = (I * 13)(0x3033, 1, 0x3040, 8, 0x3024, 8, 0x3023, 8, 0x3022, 8, 0x3021, 8, 0x3038)
assert eglfn('eglChooseConfig', U, P, C.POINTER(I), C.POINTER(P), I, C.POINTER(I))(display, attrs, C.byref(config), 1, C.byref(count)) and count.value
context_attrs = (I * 7)(0x3098, 3, 0x30FB, 3, 0x30FD, 1, 0x3038)
context = eglfn('eglCreateContext', P, P, P, P, C.POINTER(I))(display, config, None, context_attrs)
assert context
assert eglfn('eglMakeCurrent', U, P, P, P, P)(display, None, None, context)
get_string = glfn('glGetString', C.c_char_p, U)
print('Raster backend:', get_string(0x1F01).decode(), get_string(0x1F02).decode())

create_shader = glfn('glCreateShader', U, U)
shader_source = glfn('glShaderSource', None, U, I, C.POINTER(C.c_char_p), C.POINTER(I))
compile_shader = glfn('glCompileShader', None, U)
shader_iv = glfn('glGetShaderiv', None, U, U, C.POINTER(I))
shader_log = glfn('glGetShaderInfoLog', None, U, I, C.POINTER(I), P)
create_program = glfn('glCreateProgram', U)
attach = glfn('glAttachShader', None, U, U)
link = glfn('glLinkProgram', None, U)
program_iv = glfn('glGetProgramiv', None, U, U, C.POINTER(I))
program_log = glfn('glGetProgramInfoLog', None, U, I, C.POINTER(I), P)
use = glfn('glUseProgram', None, U)
get_uniform = glfn('glGetUniformLocation', I, U, C.c_char_p)
uniform_1i = glfn('glUniform1i', None, I, I)
gen_texture = glfn('glGenTextures', None, I, C.POINTER(U))
bind_texture = glfn('glBindTexture', None, U, U)
active_texture = glfn('glActiveTexture', None, U)
tex_image = glfn('glTexImage2D', None, U, I, I, I, I, I, U, U, P)
tex_param = glfn('glTexParameteri', None, U, U, I)
gen_fbo = glfn('glGenFramebuffers', None, I, C.POINTER(U))
bind_fbo = glfn('glBindFramebuffer', None, U, U)
attach_texture = glfn('glFramebufferTexture2D', None, U, U, U, U, I)
fbo_status = glfn('glCheckFramebufferStatus', U, U)
viewport = glfn('glViewport', None, I, I, I, I)
draw = glfn('glDrawArrays', None, U, I, I)
read = glfn('glReadPixels', None, I, I, I, I, U, U, P)
gen_vao = glfn('glGenVertexArrays', None, I, C.POINTER(U))
bind_vao = glfn('glBindVertexArray', None, U)
gen_buffer = glfn('glGenBuffers', None, I, C.POINTER(U))
bind_buffer = glfn('glBindBuffer', None, U, U)
buffer_data = glfn('glBufferData', None, U, C.c_ssize_t, P, U)
bind_base = glfn('glBindBufferBase', None, U, U, U)
block_index = glfn('glGetUniformBlockIndex', U, U, C.c_char_p)
block_binding = glfn('glUniformBlockBinding', None, U, U, U)
clear_color = glfn('glClearColor', None, F, F, F, F)
clear = glfn('glClear', None, U)
enable = glfn('glEnable', None, U)
disable = glfn('glDisable', None, U)
scissor = glfn('glScissor', None, I, I, I, I)
get_error = glfn('glGetError', U)
vao = U()
gen_vao(1, C.byref(vao))
bind_vao(vao)

def includes(s):
    return re.sub(r'#include <minecraft:([^>]+)>', lambda m: includes(client.read('assets/minecraft/shaders/include/' + m[1]).decode()), s)

quad = '''#version 330
#extension GL_ARB_separate_shader_objects : require
layout(location=0) out vec2 texCoord;
void main(){vec2 p=vec2(float((gl_VertexID<<1)&2),float(gl_VertexID&2));texCoord=p;gl_Position=vec4(p*2.0-1.0,0,1);}
'''
sky_vertex = '''#version 330
#extension GL_ARB_separate_shader_objects : require
layout(location=0) out float sphericalVertexDistance;
layout(location=1) out float cylindricalVertexDistance;
void main(){vec2 p=vec2(float((gl_VertexID<<1)&2),float(gl_VertexID&2));gl_Position=vec4(p*2.0-1.0,0,1);sphericalVertexDistance=0;cylindricalVertexDistance=0;}
'''

def program(vertex, fragment):
    out = create_program()
    for kind, source in [(0x8B31, vertex), (0x8B30, includes(fragment))]:
        s = create_shader(kind)
        src = C.c_char_p(source.encode())
        shader_source(s, 1, C.byref(src), None)
        compile_shader(s)
        ok = I()
        shader_iv(s, 0x8B81, C.byref(ok))
        if not ok.value:
            log = C.create_string_buffer(32768)
            shader_log(s, len(log), None, log)
            raise AssertionError(log.value.decode())
        attach(out, s)
    link(out)
    ok = I()
    program_iv(out, 0x8B82, C.byref(ok))
    if not ok.value:
        log = C.create_string_buffer(32768)
        program_log(out, len(log), None, log)
        raise AssertionError(log.value.decode())
    return out

sky = program(sky_vertex, (root / 'assets/minecraft/shaders/core/sky.fsh').read_text())
detect = program(quad, (root / 'assets/shader_maker/shaders/post/detect_environment.fsh').read_text())
grade = program(quad, (root / 'assets/shader_maker/shaders/post/grade.fsh').read_text().replace('#include <minecraft:globals.glsl>', includes('#include <minecraft:globals.glsl>')))

def texture(width, height, depth=False, data=None):
    t = U()
    gen_texture(1, C.byref(t))
    active_texture(0x84C0)
    bind_texture(0x0DE1, t.value)
    for p in [0x2801, 0x2800]:
        tex_param(0x0DE1, p, 0x2600)  # nearest; exact byte transport
    tex_image(0x0DE1, 0, 0x8CAC if depth else 0x8058, width, height, 0, 0x1902 if depth else 0x1908, 0x1406 if depth else 0x1401, data)
    return t.value

def target(width, height):
    tex = texture(width, height)
    fbo = U()
    gen_fbo(1, C.byref(fbo))
    bind_fbo(0x8D40, fbo.value)
    attach_texture(0x8D40, 0x8CE0, 0x0DE1, tex, 0)
    assert fbo_status(0x8D40) == 0x8CD5
    return fbo.value, tex, width, height

scene = target(32, 32)
environment = target(1, 1)
output = target(32, 32)
depth_values = (F * 1024)(*([.5] * 512 + [0.0] * 512))
depth = texture(32, 32, True, depth_values)

def ubo(name, binding, values):
    index = block_index(sky, name.encode())
    assert index != 0xFFFFFFFF, name
    block_binding(sky, index, binding)
    buffer = U()
    gen_buffer(1, C.byref(buffer))
    bind_buffer(0x8A11, buffer.value)
    array = (F * len(values))(*values)
    buffer_data(0x8A11, C.sizeof(array), array, 0x88E4)
    bind_base(0x8A11, binding, buffer.value)

ubo('Fog', 0, [0, 0, 0, 1] + [0, 1e9, 0, 1e9, 1e9, 1e9] + [0, 0])
identity = [1 if i % 5 == 0 else 0 for i in range(16)]

def render(prog, dest, textures=()):
    bind_fbo(0x8D40, dest[0])
    viewport(0, 0, dest[2], dest[3])
    use(prog)
    for unit, (name, tex) in enumerate(textures):
        active_texture(0x84C0 + unit)
        bind_texture(0x0DE1, tex)
        uniform_1i(get_uniform(prog, name.encode()), unit)
    draw(0x0004, 0, 3)
    assert get_error() == 0

def pixel(dest, x=0, y=0):
    bind_fbo(0x8D40, dest[0])
    data = (C.c_ubyte * 4)()
    read(x, y, 1, 1, 0x1908, 0x1401, data)
    return list(data)

def clear_terrain():
    bind_fbo(0x8D40, scene[0])
    enable(0x0C11)
    scissor(0, 0, 32, 16)
    clear_color(.1, .15, .2, 1)
    clear(0x4000)
    disable(0x0C11)

def sensor():
    render(detect, environment, [('InSampler', scene[1]), ('DepthSampler', depth)])
    return pixel(environment)[0]

checks = 0
palette = json.loads((root / 'palette.json').read_text())
for family in palette:
    rgb = [(family['rgb'] >> shift & 255) / 255 for shift in [16, 8, 0]]
    ubo('DynamicTransforms', 1, identity + identity + rgb + [1] + [0] * 4)
    render(sky, scene)
    raw = pixel(scene)
    assert raw[:3] == [family['rgb'] >> shift & 255 for shift in [16, 8, 0]], ('Sky RGB changed', family, raw)
    assert raw[3] == family['code'], ('Sky marker changed', family, raw)
    clear_terrain()
    actual = sensor()
    assert actual == family['code'], ('Transport failed', family, actual)
    render(grade, output, [('InSampler', scene[1]), ('EnvironmentSampler', environment[1]), ('DepthSampler', depth)])
    terrain = pixel(output)
    expected = [52, 76, 102] if family['id'] == 'sky_78a7ff' else [26, 38, 51]
    assert all(abs(a-b) <= 1 for a, b in zip(terrain[:3], expected)), ('Full-screen grading wrong', family, terrain, expected)
    assert pixel(output, 0, 31)[3] == 255, 'Opaque sky alpha not restored'
    checks += 5

for rgb in [[1, 0, 0], [0, 0, 0], [.01, .01, .01]]:
    ubo('DynamicTransforms', 1, identity + identity + rgb + [1] + [0] * 4)
    render(sky, scene)
    clear_terrain()
    assert sensor() == 0, ('Unknown/dark sky must not match', rgb)
    checks += 1

# Stock-looking RGB without our sky marker must never activate a group.
bind_fbo(0x8D40, scene[0])
clear_color(120/255, 167/255, 1, 1)
clear(0x4000)
assert sensor() == 0, 'Scene RGB was mistaken for tagged sky'
checks += 1

# No stale state; two equally represented valid signatures suppress ambiguous detection.
for x, family in [(0, palette[0]), (16, palette[1])]:
    bind_fbo(0x8D40, scene[0])
    enable(0x0C11)
    scissor(x, 0, 16, 32)
    clear_color(.2, .3, .4, family['code']/255)
    clear(0x4000)
    disable(0x0C11)
assert sensor() == 0, 'Ambiguous equal votes must not pick an arbitrary group'
checks += 1

for samples in [0, 1, 2, 3]:
    data = [26, 38, 51, 255] * 1024
    for x in range(samples):
        data[(16*32+x)*4+3] = palette[0]['code']
    active_texture(0x84C0)
    bind_texture(0x0DE1, scene[1])
    pixels = (C.c_ubyte * len(data))(*data)
    tex_image(0x0DE1, 0, 0x8058, 32, 32, 0, 0x1908, 0x1401, pixels)
    assert sensor() == (palette[0]['code'] if samples >= 3 else 0), 'Insufficient visible sky activated the layer'
    checks += 1

# A valid marker hidden behind geometry must never count as visible sky.
ubo('DynamicTransforms', 1, identity + identity + [120/255, 167/255, 1, 1] + [0] * 4)
render(sky, scene)
active_texture(0x84C0)
bind_texture(0x0DE1, depth)
all_geometry = (F * 1024)(*([.5] * 1024))
tex_image(0x0DE1, 0, 0x8CAC, 32, 32, 0, 0x1902, 0x1406, all_geometry)
assert sensor() == 0, 'Occluded sky marker activated layer'
checks += 1
print(f'Visual biome raster smoke: {checks} checks passed across all 16 groups, unknown/dark colors, alpha restoration, shared-signal grading and occlusion.')
print('Synthetic EGL framebuffers and actual exported shader source; Minecraft pipeline/window and clean-client validation remain pending.')
