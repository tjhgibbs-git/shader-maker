# Vanilla shader feasibility evidence

Earlier investigation: **2026-11-02**, pinned Minecraft Java 26.3. See the visual-group implementation follow-up below for newer production code and headless raster results. Product requirements live in [the spec](vanilla-only-handoff.md). Source inspection and native compilation below are verified here; **no graphics-device or clean-client probe run was performed in this revision**. The block-scraper status check found no running client. This document does not classify an untested feature as impossible.

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
| `lightmap-sky-factor.zip` | Replaces lightmap RGB with clamped SkyFactor | Compare noon, midnight, rain/thunder and lightning with Hide Lightning Flashes on/off; equal values demonstrate ambiguity, not event identity |
| `lightmap-sky-color.zip` | Replaces lightmap RGB with SkyLightColor | Compare time, weather, dimensions and biome boundaries; test whether candidate signatures collide |
| `sky-color.zip` | Shows core sky ColorModulator without its final fog operation | Compare sky colors with biome fog/lightmap values, with occlusion and different view directions; no exact biome-ID classifier is implied |


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

## Layout / condition revision follow-up

Added the three core-stage fixtures above and checked the pinned `EnvironmentAttributes`, `SkyRenderState`, `LightmapRenderState`, `SkyRenderer` and pipeline bytecode. `SUN_ANGLE` and `rainBrightness` reach celestial rendering; the sun's dynamic transform color alpha is the rain-brightness argument. The lightmap's bound fields expose sky factor and color, not an exact camera biome ID or an independent visible-lightning field. The renderer's camera/celestial transforms and the framebuffer are possible transport investigations, not a proven daylight/weather input for a post effect. No production runtime input implementation is claimed.

All **eight** generated diagnostic packs passed the harness: **four** actual post-chain JSON decodes and **20** native ShaderC/SPIR-V fragment checks. Core terrain uses its relevant MULTIDRAW/OIT variants; the lightmap and sky cases use their ordinary core-stage defines, with both depth-range conventions. These checks establish syntax and reflection, not actual device binding, scene coverage, signal uniqueness or cross-stage transport.

A dev-client launch failed before window creation with `Unable to initialize SDL: No available video device`; this environment cannot complete the graphics validation. The user retained the standalone vanilla requirement. Continue with clean-client measurements of these fixtures before exposing those inputs as working exported conditions. Source snapshots and authored conditions remain preserved in the meantime.

## Visual-group implementation follow-up

The user explicitly accepts shared visual signatures and datapack sensitivity, and forbids any client runtime dependency. Exact-ID requirements do not apply to a newly and explicitly chosen visual group; older IDs are still preserved until conversion. This supersedes the earlier exact-ID-only scope discussion.

`VisualBiomes` pins 16 normalized stock `visual/sky_color` signatures and lists every associated stock biome. Contract tests compare every entry against the official cached 26.3 biome JSON. The stock day timeline multiplies sky color by a grey scalar, so normalization retains its hue above the darkness cutoff. Weather, lightning, biome interpolation and custom attributes can alter that hue; black/very dark values and unknown signatures return no match. Pale-garden grey is inherently shared by any matching grey signal. No exact identity, full-night coverage or camera-biome guarantee is implied.

Production output now includes a core sky fragment retaining vanilla RGB/fog, adding a signature byte only to recognized opaque sky alpha. Source inspection confirms `MainTarget` is RGBA8_UNORM, SKY uses the default unblended color target, and the 3D HUD integrates depth without a full-screen color overwrite. The new 1×1 reduction samples the main color/depth pair on a 32×32 grid and requires at least three agreeing samples with a unique winning group. The ordinary ordered grade reads this code; it restores recognized sky alpha to one. The override remains loaded in Command mode before activation and can conflict with other sky/alpha resources. This is an experimental transport, not a guarantee that later overlays preserve every marker.

Verified in this revision: **391** project checks, **16** actual Minecraft post-chain decodes, **22** native ShaderC/SPIR-V cases, and **90** actual raster assertions on surfaceless Mesa llvmpipe 4.5 (Mesa 25.2.8). Raster checks use the production exported shaders with synthetic engine buffers/textures, covering all 16 groups, untouched sky RGB, opaque sky alpha restoration, full-screen grading of non-sky pixels, changed/unknown/dark colors, unmarked stock-looking RGB, tied signals, minimum sample counts, no stale match, and depth occlusion. Synthetic vertex setup is used for the raster fixture; the actual vanilla sky vertex is separately compiled/reflected in native tests.

Reproduce from a populated cache with Java 25 and system Mesa EGL:

```sh
python3 tools/verify-offline.py --visual-gl
```

This generates `build/offline/visual-biomes` using `tools/VisualBiomeRenderFixtures.java`, runs `tools/verify-visual-biomes-gl.py`, and packages the authoring JAR. It does not run Minecraft's device pipeline. Gradle is unavailable here because its cache is read-only; the fallback client launch reaches SDL and fails with `No available video device`. UI screenshots, actual Minecraft bindings, clean-client OpenGL/Vulkan activation/removal, overlays, translucency/OIT, water, third person, resize and measured frame cost remain pending. No modless validation is claimed.

## 2026-11-03 terrain fog and Sky colour follow-up

Terrain distance/height/box/sphere density integration and a dedicated Fog workspace now exist. Authored fog follows native fog colour; colour editing is deliberately excluded. Soft height profiles integrate analytically; soft box/sphere edges use a bounded 8/16/32 sample count. Media add optical depth before the retained native fog operation, preserving native alpha/OIT structure. Scene coverage remains explicitly terrain and Auto export only. Animated noise is parked for a future experimental branch; lighting/sunbeams are deliberately excluded.

The condition is now labelled Sky colour. Vanilla biome buttons are colour shortcuts; custom #RRGGBB signals can be supplied by datapacks. The same visible-sky alpha transport is used, with per-project marker allocation when custom hues are active, a 16-hue maximum and explicit rejection of overlapping tolerances. Legacy exact-ID selectors remain preserved until explicit conversion. Fog uses schema v7; custom sky colours v8.

Validation: all existing tests pass, plus **349** fog/sky assertions, **66** total native shader checks (**44** new cases), and **1,205** fog/custom-sky software-raster assertions across **596** camera/ray cases, with **17** actual terrain shader pairs linked and maximum RGB error **1/255** against CPU expectations. The earlier **90** visual-sky raster assertions pass unchanged. These use synthetic Mesa inputs, not a Minecraft device pipeline. Gradle cannot lock its read-only home cache, and the display socket is inaccessible. New UI screenshots, clean-client bindings, actual OIT and hardware performance remain pending. See [the detailed handoff and artifacts](fog-sky-in-game-handoff.md).
