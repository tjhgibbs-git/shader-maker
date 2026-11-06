#!/usr/bin/env python3
"""Generate diagnostic resource packs from the pinned vanilla 26.3 client JAR.

These packs do not use Fabric or Shader Maker. Enable one probe at a time.
This generates evidence fixtures; it does not claim a graphics-device test.
"""
import argparse
import json
from pathlib import Path
import zipfile


def generate(client_jar, output):
    output.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(client_jar) as client:
        globals_glsl = client.read('assets/minecraft/shaders/include/globals.glsl').decode()
        fog_glsl = client.read('assets/minecraft/shaders/include/fog.glsl').decode()
        terrain = client.read('assets/minecraft/shaders/core/terrain.fsh').decode()
        lightmap = client.read('assets/minecraft/shaders/core/lightmap.fsh').decode()
        sky = client.read('assets/minecraft/shaders/core/sky.fsh').decode()
    header = '''#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D InSampler;
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;
'''
    probes = {
        'camera-y': (globals_glsl, 'float y = float(CameraBlockPos.y) - CameraOffset.y;\n    fragColor = vec4(vec3(clamp((y + 64.0) / 384.0, 0.0, 1.0)), 1.0);'),
        'elapsed-time': (globals_glsl, 'fragColor = vec4(GameTime, 0.0, 1.0 - GameTime, 1.0);'),
        'post-fog': (fog_glsl, 'fragColor = vec4(FogColor.rgb, 1.0);'),
        'raw-depth': ('uniform sampler2D DepthSampler;\n', 'float d = texture(DepthSampler, texCoord).r;\n    fragColor = vec4(vec3(d), 1.0);'),
    }
    metadata = {'pack': {'description': 'Vanilla 26.3 diagnostic; enable one at a time', 'min_format': [97, 1], 'max_format': [97, 1]}}

    def write(name, files):
        files['pack.mcmeta'] = json.dumps(metadata, indent=2) + '\n'
        files['README.txt'] = 'Diagnostic only. Minecraft Java 26.3. No mods required. Enable one probe at a time. Record backend, GPU, world, conditions and log. Disable to restore. See docs/vanilla-feasibility.md.\n'
        path = output / (name + '.zip')
        with zipfile.ZipFile(path, 'w', zipfile.ZIP_DEFLATED) as pack:
            for filename, content in sorted(files.items()):
                entry = zipfile.ZipInfo(filename, date_time=(1980, 1, 1, 0, 0, 0))
                entry.compress_type = zipfile.ZIP_DEFLATED
                pack.writestr(entry, content)
        print(path)

    for name, (uniforms, body) in probes.items():
        inputs = [{'sampler_name': 'In', 'target': 'minecraft:main'}]
        if name == 'raw-depth':
            inputs.append({'sampler_name': 'Depth', 'target': 'minecraft:main', 'use_depth_buffer': True})
        effect = {'targets': {'swap': {}}, 'passes': [
            {'vertex_shader': 'minecraft:core/screenquad', 'fragment_shader': 'shader_maker_probe:post/probe', 'inputs': inputs, 'output': 'swap'},
            {'vertex_shader': 'minecraft:core/screenquad', 'fragment_shader': 'minecraft:post/blit', 'inputs': [{'sampler_name': 'In', 'target': 'swap'}], 'output': 'minecraft:main', 'uniforms': {'BlitConfig': [{'name': 'ColorModulate', 'type': 'vec4', 'value': [1, 1, 1, 1]}]}}
        ]}
        if name == 'post-fog':
            # Declare the binding without allocating a custom buffer: vanilla
            # bindDefaultUniforms supplies the engine's current Fog buffer.
            effect['passes'][0]['uniforms'] = {'Fog': []}
        write(name, {
            'assets/minecraft/post_effect/end_of_frame.json': json.dumps(effect, indent=2) + '\n',
            'assets/shader_maker_probe/shaders/post/probe.fsh': header + uniforms + '\nvoid main() {\n    ' + body + '\n}\n',
        })

    # Keep the pinned terrain shader's OIT/cutout paths and change its final color.
    old = 'return apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, fogColor);'
    if terrain.count(old) != 1:
        raise ValueError('26.3 terrain contract changed: expected exactly one final fog operation')
    write('terrain-fog', {'assets/minecraft/shaders/core/terrain.fsh': terrain.replace(old, 'return vec4(fogColor.rgb, color.a);')})

    # Inspect environment signals at their actual core-stage binding. These are
    # diagnostic outputs, not exact daylight / weather / flash classifiers.
    lightmap_output = 'fragColor = vec4(color, 1.0);'
    if lightmap.count(lightmap_output) != 1:
        raise ValueError('26.3 lightmap contract changed: expected one final color')
    write('lightmap-sky-factor', {'assets/minecraft/shaders/core/lightmap.fsh': lightmap.replace(lightmap_output,
        'fragColor = vec4(vec3(clamp(lightmapInfo.SkyFactor, 0.0, 1.0)), 1.0);')})
    write('lightmap-sky-color', {'assets/minecraft/shaders/core/lightmap.fsh': lightmap.replace(lightmap_output,
        'fragColor = vec4(clamp(lightmapInfo.SkyLightColor, 0.0, 1.0), 1.0);')})
    sky_output = 'fragColor = apply_fog(ColorModulator, sphericalVertexDistance, cylindricalVertexDistance, 0.0, FogSkyEnd, FogSkyEnd, FogSkyEnd, FogColor);'
    if sky.count(sky_output) != 1:
        raise ValueError('26.3 sky contract changed: expected one final color')
    write('sky-color', {'assets/minecraft/shaders/core/sky.fsh': sky.replace(sky_output,
        'fragColor = vec4(ColorModulator.rgb, 1.0);')})



if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('client_jar', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    generate(args.client_jar, args.output)
