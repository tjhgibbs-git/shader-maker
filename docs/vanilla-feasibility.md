# Vanilla shader feasibility evidence

Investigation: **2026-11-02**, pinned Minecraft Java 26.3. Product requirements live in [the spec](vanilla-only-handoff.md). Source inspection and native compilation below are verified here; **no graphics-device or clean-client probe run was performed in this revision**. The block-scraper status check found no running client. This document does not classify an untested feature as impossible.

## Sources and reproducibility

Inspected the cached official `minecraft-client.jar` at `~/.gradle/caches/fabric-loom/26.3/`, SHA-256 `4508d006323f24fa02876310c192d739af56516eb259000ac50f0909a68c9a2d`. Extracted GLSL directly and inspected classes using cached Vineflower 1.12.0. Decompiler files are temporary evidence under `/tmp/vanilla-shader-source`, `/tmp/vanilla-shader-source-extra` and `/tmp/vanilla-biome-source`, not project dependencies.

The [official 26.3 release notes](https://www.minecraft.net/en-us/article/minecraft-java-edition-26-3) document post-effect activation, automatic pack priority, and core shader replacement's unsupported interface status. Their shader compilation changes also explain why both backend tests remain necessary. [Mojang's environment attribute introduction](https://www.minecraft.net/ja-jp/article/minecraft-snapshot-25w42a) describes biome/dimension attributes; the pinned client's implementation was inspected rather than assuming that earlier snapshot's format remains unchanged.

## Findings

| Input or path | Verified source evidence | Consequence |
|---|---|---|
| Camera altitude | `include/globals.glsl`; `GlobalSettingsUniform.update` writes floor(camera position) and **floor minus position** | `float(CameraBlockPos.y) - CameraOffset.y` reconstructs camera Y, including negative/fractional positions. Earlier “fractional offset” wording omitted the sign |
| Automatic per-frame updates | `GameRenderer.render` updates globals; `PostPass.addToFrame` invokes `RenderSystem.bindDefaultUniforms`; `RenderPipelines.POST_PROCESSING_SNIPPET` inherits `GLOBALS_SNIPPET` | Camera-Y conditional post shaders have a source-backed vanilla path. No mod-owned writable buffer is needed |
| Timer | `LevelExtractor.extract` assigns `level.getGameTime()` to render-state gameTime; `GameRenderer.render` passes it to `GlobalSettingsUniform.update` | `GameTime` = `(gameTime % 24000 + partialTick) / 24000`; this is elapsed-tick phase, not synchronized daylight |
| Daylight | `SkyRenderer.extractRenderState` obtains `SUN_ANGLE` from the camera attribute probe; `TimeCommand` changes world clocks separately | Keep daylight investigation open. Reading the mod's default clock or labeling `GameTime` “time of day” does not prove exported behavior |
| Post uniform layouts | Post pipeline snippet declares Globals; `PostChain.createPass` adds sampler/custom block layouts; default-binding code attempts Projection/Fog/Globals/Lighting | A declaration in GLSL plus `bindDefaultUniforms` is not a complete pipeline-binding proof. A fixture explicitly declares `Fog: []` in pass uniforms to request that engine-bound block without a custom buffer |
| World fog in post | `GameRenderer.renderLevel → render3dHud` sets shader fog to `FogMode.NONE` before `render → applyPostEffects`; empty fog has zero color and maximum distances | Directly reading post `FogColor` is not a proven biome/weather input. Source predicts empty fog at this point; test the fixture rather than promising world fog |
| Post projection | `PostPass.addToFrame` installs an orthographic projection | Inverting its `ProjMat` does not recover the original world camera projection |
| Depth | `PostPass.TargetInput` can bind a target's depth; world post effects run before the main depth clear; `render3dHud` integrates hand depth when post effects are active | A vanilla depth path exists to investigate. Linear distance, hand/world separation and translucent coverage remain unverified |
| Biome-driven world fog | `EnvironmentAttributeProbe.tick` accumulates sampled biomes' attributes; `AtmosphericFogEnvironment.getBaseColor/setupFog` uses fog/sky colors and ranges; world rendering binds WORLD fog | Core-stage shaders can use biome-influenced environment signals. This retains a plausible resource-only biome-aware path even when post fog is empty |
| Terrain height | `core/terrain.vsh` uses Position and ChunkPosition plus camera offsets; `include/terrainglobals.glsl` has the terrain matrices | Surface/world Y and camera Y are distinct attainable source inputs at terrain stage; propagation to full-screen behavior needs proof |
| Terrain tint | `BiomeColors`, `Biome.getGrassColor/getFoliageColor`, `GrassColor.get`, `core/terrain.vsh` | Vanilla colormaps influence biome-colored geometry; raw Color is available before lightmap multiplication. This does not automatically give the camera's biome ID |
| Light level | `include/light.glsl` Lighting contains only two directional vectors | Do not expose “world light level” from this block. Terrain UV2/lightmap inputs are a separate avenue |
| Weather | `SkyRenderer` passes `1 - rainLevel` as sun/moon transform alpha; `AtmosphericFogEnvironment` modifies fog using rain/thunder and local skylight; precipitation has its own render path | Weather influences vanilla rendering. Inspect stage-specific data and distinguish weather strength from precipitation visible at the camera |
| Lightning flash | `ClientLevel.addEnvironmentAttributeLayers` modifies SKY_COLOR and forces SKY_LIGHT_FACTOR to 1 during skyFlashTime; its getter respects Hide Lightning Flashes. `LightmapRenderStateExtractor` consumes SKY_LIGHT_FACTOR | Flash information reaches vanilla sky/lightmap rendering indirectly. Daylight can also produce sky factor 1, so a threshold alone is not an exact lightning event detector |
| Lightmap data | `core/lightmap.fsh` LightmapInfo contains SkyFactor, light colors, darkness and other factors | A core lightmap path may support flash/daylight response. These fields are not automatically bound to post effects; signal transfer, ambiguity and scene coverage need tests |

Source ordering also corrects the older blanket HUD claim: `render → applyPostEffects` precedes main depth clearing and `guiRenderer.render`. The hand and screen effects are rendered earlier. Confirm individual overlays in game rather than claiming all HUD/menus are always graded.

## What “biome support” must prove

Biome-aware rendering already exists in vanilla. The unresolved requirement is selecting an **arbitrary exact biome ID** for a full-screen conditional layer using only a resource pack. None of the inspected standard GLSL blocks contains a biome ID or a biome lookup texture. That finding rules out that specific direct-uniform route, not every possible resource pack technique.

Investigate these separately:

1. **World fog/sky signatures in core stages.** Source confirms their biome dependence. Measure uniqueness, spatial interpolation, time/weather/view-direction changes and dimensions. Two biomes with the same values cannot be distinguished by those values alone.
2. **Terrain tint/colormap techniques.** Texture changes are vanilla resources; biome sampling supplies colors to geometry. Colormaps are indexed by climate values, some biomes override colors, and biome blending modifies boundaries. Any encoding technique needs a collision analysis, preservation of normal appearance and tests where tinted geometry is absent. Surface-biome signals cannot silently substitute for camera-biome semantics.
3. **Cross-stage information transport.** If a world stage encodes a signal for a later post effect, prove the actual render target/channel, persistence, filtering, visibility and recovery behavior. A core override cannot invent an engine-provided biome ID merely by naming a new uniform.
4. **Server-owned biome attributes/commands.** Biome attributes are synchronized world data, not arbitrary client resource definitions. An optional data pack could enable other methods, but does not establish resource-pack-only support. Never silently add that dependency.

Keep existing biome project fields and the exact-ID requirement while this work is open. An environmental heuristic can be useful as an additional feature, with a different name and visible limitations. It is not a migration replacement for exact biome matching.

## Generated vanilla diagnostic packs

Run from the repository root (the nested `shader-maker` checkout):

```sh
python3 tools/vanilla-shader-probes.py \
  ~/.gradle/caches/fabric-loom/26.3/minecraft-client.jar \
  /tmp/shader-maker-vanilla-probes
```

The generator embeds only the uniform definitions needed by post probes and derives the terrain probe from the supplied vanilla shader. It does not alter the production compiler. Generated ZIPs live outside the repository. Enable **one at a time** in an unmodified 26.3 client, with other shader packs disabled:

| Pack | Display/observation | Required checks |
|---|---|---|
| `camera-y.zip` | Greyscale maps camera Y -64..320 to 0..1 | Walk/fly through known and negative Y; cross integer boundaries slowly; compare first/third person camera positions |
| `elapsed-time.zip` | Red/blue phase from GameTime | Compare ordinary ticking, `/time set`, world-clock pause/rate, game pause and server tick freeze. Source predicts it will not follow daylight changes |
| `post-fog.zip` | Current post FogColor, opaque output | Compare with terrain fog across biomes, underwater/lava, weather and dimensions. Expected empty black does not mean the world has black fog |
| `raw-depth.zip` | Raw main-target depth, **not linear distance** | Sky/near/far, water/translucency, hand, view bob, resize and both depth conventions. Far depths may look black; that alone is not failure |
| `terrain-fog.zip` | Replaces terrain's final surface color with its stage fog color | Compare different biomes, view directions, rain, day/night, underwater and dimensions. This tests world-stage signal availability, not unique biome detection or a finished grade |

Weather, daylight and lightning need further targeted fixtures. Compare clear/rain/thunder, snow/dry biomes, indoor/outdoor, day/night, natural or summoned lightning, the Hide Lightning Flashes toggle, End flashes and multiplayer/dimension changes. A flash detector must not trigger on bright daytime or unrelated brightness changes. Do not infer a clean event signal from the presence of the vanilla lightning geometry shader.

## Validation performed here

- Generated all five standalone diagnostic ZIPs.
- Parsed all four post-chain definitions with Minecraft's actual `PostChainConfig.CODEC`.
- Native ShaderC Vulkan 1.3 target compilation and Minecraft SPIR-V reflection passed **14 fragment cases**: four post fragments with/without the depth-range macro, and terrain with default, MULTIDRAW_TERRAIN and OIT depth-bounds defines, each with/without that macro. Includes were expanded from the pinned client JAR. The first OIT harness attempt omitted engine defines; adding the source-defined rank/coefficient values fixed that harness configuration.
- This is shader/JSON validation only. It does not validate pipeline creation, GPU bindings, image results, OpenGL/Vulkan device behavior or other OIT variants.

The harness is [tools/VanillaProbeCheck.java](../tools/VanillaProbeCheck.java). After compiling the existing `MinecraftContractTest`, compile/run this harness with the same Minecraft/LWJGL dependency classpath; it reuses that test's ShaderC options and actual reflection. Arguments are the client JAR and generated-probe directory. For the offline build, the dependency classpath can be reconstructed from the same populated cache:

```sh
# Run tools/verify-offline.py first if build/offline/classes is absent.
SHADER_PROBE_CP=$(python3 - <<'PYCP'
from pathlib import Path
cache = Path.home() / '.gradle/caches'
paths = [Path('build/offline/classes').resolve(),
         *sorted((cache / 'modules-2/files-2.1').rglob('*.jar')),
         cache / 'fabric-loom/26.3/minecraft-merged.jar']
print(':'.join(map(str, paths)))
PYCP
)
mkdir -p /tmp/shader-maker-probe-check
"$JAVA_HOME/bin/javac" -cp "$SHADER_PROBE_CP" -d /tmp/shader-maker-probe-check tools/VanillaProbeCheck.java
"$JAVA_HOME/bin/java" --enable-native-access=ALL-UNNAMED \
  -cp "/tmp/shader-maker-probe-check:$SHADER_PROBE_CP" VanillaProbeCheck \
  ~/.gradle/caches/fabric-loom/26.3/minecraft-client.jar /tmp/shader-maker-vanilla-probes
```

Use Java 25 and adjust the cache path if using a separate Gradle cache. No production renderer or conditional-export code changed.

For every graphics test, record client version, backend, GPU/driver, exact probe and pack order, world/dimension, command/input changes, expected versus observed output, screenshots and relevant shader logs. A result in the Fabric development client is useful authoring evidence; repeat it without mods before marking the standalone path verified.
