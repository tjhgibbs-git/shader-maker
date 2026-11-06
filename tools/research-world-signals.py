#!/usr/bin/env python3
"""Inventory pinned vanilla environment data and lightmap consumers. No world or mod input.
Usage: research-world-signals.py <minecraft-client.jar> <output.json>
Explicit values are not resolved camera values: dimensions, timelines and modifiers still apply.
"""
import collections
import hashlib
import json
from pathlib import Path
import sys
import zipfile
jar=Path(sys.argv[1])
attributes=['fog_color','fog_start_distance','fog_end_distance','sky_fog_end_distance','cloud_fog_end_distance','water_fog_color','water_fog_start_distance','water_fog_end_distance','ambient_light_color','block_light_tint','sky_light_color','night_vision_color']
result={'client_sha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'note':'Explicit data only; inheritance is NOT a unique biome signature. Shader inputs may be further modified.','biomes':{},'biome_surface_effects':{},'dimensions':{},'timelines':{},'lightmap_consumers':{}}
with zipfile.ZipFile(jar) as z:
    for name in z.namelist():
        if name.startswith('data/minecraft/worldgen/biome/') and name.endswith('.json'):
            data=json.loads(z.read(name));biome=name.rsplit('/',1)[-1][:-5];result['biomes'][biome]={a:data.get('attributes',{}).get('minecraft:visual/'+a,'<inherit>') for a in attributes}
            result['biome_surface_effects'][biome]={k:v for k,v in data.get('effects',{}).items() if k in ['water_color','foliage_color','dry_foliage_color','grass_color','grass_color_modifier']}
        if name.startswith('data/minecraft/dimension_type/') and name.endswith('.json'):
            data=json.loads(z.read(name));result['dimensions'][name.rsplit('/',1)[-1][:-5]]={k:v for k,v in data.get('attributes',{}).items() if k.startswith('minecraft:visual/')}
        if name.startswith('data/minecraft/timeline/') and name.endswith('.json'):
            data=json.loads(z.read(name));result['timelines'][name.rsplit('/',1)[-1][:-5]]=data
        if name.startswith('assets/minecraft/shaders/') and name.endswith(('.vsh','.fsh','.glsl')):
            source=z.read(name).decode()
            if 'Sampler2' in source:
                result['lightmap_consumers'][name]=[line.strip() for line in source.splitlines() if 'Sampler2' in line]
                assert 'sample_lightmap(Sampler2, UV2)' in source, 'Unaudited lightmap consumer: '+name
    result['explicit_fog_groups']=dict(collections.defaultdict(list))
    for biome,data in result['biomes'].items():result['explicit_fog_groups'].setdefault(str(data['fog_color']),[]).append(biome)
Path(sys.argv[2]).write_text(json.dumps(result,indent=2)+'\n')
print('Pinned inventory:',len(result['biomes']),'biomes;',len(result['lightmap_consumers']),'native lightmap consumers;',result['client_sha256'])
