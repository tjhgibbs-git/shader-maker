# Shader Maker

An early, version-independent foundation for an in-game atmospheric shader editor. This repository is **not yet a working Minecraft mod**: it has no in-game UI, rendering hook, live preview, persistence, or resource-pack export.

## Implemented

`Atmosphere` models exposure, input levels and gamma, contrast, saturation, RGB tint and vignette. Grades blend with the original color and preserve alpha. Context masks support biome IDs, altitude, wrapped time of day and facing ranges, soft edges, and a periodic intensity pulse. These are CPU-side reference calculations, not GLSL shaders. Coordinates for the vignette are normalized around the screen center (corners at ±1).

Run the dependency-free checks with Java 17 or newer:

```sh
mkdir -p out
javac -d out $(find src -name '*.java')
java -cp out io.github.tjhgibbs.shadermaker.AtmosphereTest
```

## Design constraints and next steps

Vanilla resource-pack shaders replace specific game programs and only receive the inputs that those programs supply. Biome, world altitude and facing are not universally available. An arbitrary post-processing effect also needs an activation mechanism; installing a resource pack alone does not guarantee it runs. Shader interfaces and pack formats change between Minecraft versions. The reference tree generator targets Fabric 26.3 and Java 25; selecting and testing a specific Minecraft version is essential before implementing a render hook or claiming portable export.

The intended next step is a Fabric client integration with a full-screen post-process, feeding context from the client, and a simple/advanced screen with before/after preview, presets, levels and curves, undo, save and apply. The mod must export a working `resources.zip` for its target Minecraft version, following the contract in [docs/resource-pack-export.md](docs/resource-pack-export.md). Validate the renderer in game before treating the CPU reference as representative of the final image; color spaces and framebuffer behavior matter.

## Temporary visual review harness — delete before release

`tools/temporary-visual-review/` is disposable cloud visual-review tooling, not part of the mod or its distribution. Delete it once the in-game editor can be reviewed. Open `tools/temporary-visual-review/index.html` in a modern browser. The editor offers immediate image preview, presets, simple and advanced grading controls, condition simulation, hold-to-compare, and JSON project save/load. Imported images remain local and are not included in project files. The built-in landscape is illustrative; import a game screenshot to judge a particular scene. The editor does not apply effects to Minecraft or export shaders yet.
