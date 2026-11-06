#!/usr/bin/env python3
"""Build the Shader Maker in-game test world from a 26.3 world save.

Usage: build-test-world.py <source world dir> <output world dir> <demo datapack zip> <minecraft jar> <effects-only resource pack zip>
Adds: the /posteffect demo datapack, a biome-id datapack (snowy_plains night_vision_color #9C9999)
and a guide datapack that gives each player the test book once, clears stale effects and moves them to spawn.
"""
import gzip, json, shutil, sys, zipfile
from pathlib import Path

src, out, demo, jar, effects = map(Path, sys.argv[1:6])
if out.exists():
    raise SystemExit(f"{out} exists; remove it first")
shutil.copytree(src, out, ignore=shutil.ignore_patterns("session.lock"))
packs = out / "datapacks"
for old in packs.glob("*.zip"):
    old.unlink()
shutil.copy(demo, packs / "shader-maker-command-demo-datapack.zip")
# 26.3 world resource pack location (LevelResource.MAP_RESOURCE_FILE): the demo looks load with the world.
(out / "resourcepacks").mkdir(exist_ok=True)
shutil.copy(effects, out / "resourcepacks" / "resources.zip")

def mcmeta(desc):
    return json.dumps({"pack": {"description": desc, "min_format": [121, 0], "max_format": [121, 0]}})

# Exact biome id: an invisible night-vision colour (default #999999) on snowy plains.
biome = json.loads(zipfile.ZipFile(jar).read("data/minecraft/worldgen/biome/snowy_plains.json"))
biome["attributes"]["minecraft:visual/night_vision_color"] = "#9c9999"
with zipfile.ZipFile(packs / "shader-maker-biome-id.zip", "w") as z:
    z.writestr("pack.mcmeta", mcmeta("Shader Maker test: snowy_plains night_vision_color #9C9999"))
    z.writestr("data/minecraft/worldgen/biome/snowy_plains.json", json.dumps(biome, indent=1))

PAGES = [
    "SHADER MAKER\nTEST GUIDE\n\nOne feature per test.\nBefore each test run\n/posteffect clear @s\n\nOpen the editor with N.\nOpen... loads a test project, Apply to game loads it.",
    "TEST 1: /posteffect\n\nNo editor needed: the looks load with this world (accept the world resource pack prompt).\n\n/function shader_maker_demo:menu\n\nClick a look to toggle it. Looks fade in and out.",
    "Automatic mode is on by default: looks follow biome, water, below Y0, rain, villages and low health.\n\nSnowy plains:\n/tp @s -112 90 -650\nUnder Y0:\n/tp @s -112 -30 -650",
    "By hand:\n/posteffect add @s shader_maker:sepia\nFade out:\n/posteffect remove @s shader_maker:sepia\n/posteffect add @s shader_maker:sepia_off\n\nCommand block:\n/function shader_maker:give/sepia",
    "Turn automatic off for manual testing:\n/function shader_maker_demo:auto_off\n\nYour own layers: in the editor, When > Command switch has Try buttons and Install helpers.",
    "TEST 2: X/Z AREA\n\nOpen Test-2-Area, Apply.\nSpawn is inside the box X -124..-100, Z -552..-528: orange.\nF3 shows X/Z. Walk out: it fades over 4 blocks.",
    "Make one: When > Position > Use where I'm standing, Done, Apply.\nIt never flickers: it reads the camera position every frame.",
    "TEST 3: SKY HOLD\n\nOpen Test-3-Sky-hold, Apply.\n/tp @s -112 90 -650\nLook up: cold look fades in. Now stare at the floor or dig down: it stays on.",
    "Back to plains:\n/tp @s -112 90 -540\nLook up at the sky: it fades out. While you look at the floor it keeps the last sky it saw.",
    "TEST 4: EXACT BIOME, NO COMMANDS\n\nThis world's datapack sets snowy plains' night-vision colour to #9C9999. Default is #999999, so it looks the same, even with Night Vision.",
    "Open Test-4-Biome-id, Apply.\n/tp @s -112 90 -650\nMagenta = snowy plains detected.\n/tp @s -112 90 -540\nPlains: no magenta. Floor-only views work. Sky-only views keep the last result.",
    "In the editor: When > Render signals > Edit, then cycle to Night-vision colour.\n'Here now' shows the live value. Use current world value reads any biome, including datapack ones.",
    "Need a new book?\n/function shader_maker_test:book\n\nFull notes: docs/world-to-shader-channels-26.3.md",
]
def snbt(s):
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"').replace("\n", "\\n") + '"'
book = ("give @s minecraft:written_book[minecraft:written_book_content={title:\"Shader Maker tests\",author:\"Shader Maker\",pages:["
        + ",".join(snbt(p) for p in PAGES) + "]}]\n")
with zipfile.ZipFile(packs / "shader-maker-test-guide.zip", "w") as z:
    z.writestr("pack.mcmeta", mcmeta("Shader Maker test guide book"))
    z.writestr("data/minecraft/tags/function/tick.json", json.dumps({"values": ["shader_maker_test:tick"]}))
    z.writestr("data/shader_maker_test/function/tick.mcfunction",
               "execute as @a[tag=!smt.started] run function shader_maker_test:start\n")
    z.writestr("data/shader_maker_test/function/start.mcfunction",
               "tag @s add smt.started\nposteffect clear @s\nfunction shader_maker:clear\ntp @s -112 90 -540 0 0\n"
               "function shader_maker_test:book\n"
               "tellraw @s {\"text\":\"Shader Maker test world: read the book in your inventory.\",\"color\":\"aqua\"}\n")
    z.writestr("data/shader_maker_test/function/book.mcfunction", book)

# Rename the save (LevelName string in level.dat).
def rename(path, name):
    data = gzip.decompress(path.read_bytes())
    key = b"\x08\x00\x09LevelName"
    i = data.index(key) + len(key)
    n = int.from_bytes(data[i:i + 2], "big")
    enc = name.encode()
    data = data[:i] + len(enc).to_bytes(2, "big") + enc + data[i + 2 + n:]
    path.write_bytes(gzip.compress(data))
rename(out / "level.dat", "Shader Maker Test World")
if (out / "level.dat_old").exists():
    (out / "level.dat_old").unlink()
print("built", out)
