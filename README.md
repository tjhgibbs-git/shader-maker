# Shader Maker 0.1 · Minecraft Java 26.3

A visual authoring tool for **vanilla Minecraft resource pack shaders**. The current editor runs as a client-only Fabric mod; exported packs run without it. The product direction includes conditional grading and atmosphere driven by biome/environment, Y-level, daylight, weather and lightning. An optional paired datapack mode is planned for server-controlled events such as a blood moon. See the [authoritative spec](docs/vanilla-only-handoff.md) and [vanilla feasibility evidence](docs/vanilla-feasibility.md). The [next-session handoff](docs/in-game-test-handoff.md) gives the concrete modless test sequence.

The implementation exports full-screen color grades, ordered grade layers, camera-height conditions and Sky colour conditions (vanilla colour shortcuts or custom datapack signals), and terrain distance/height/volume fog as vanilla resources. No client runtime dependency is permitted; see [AGENTS.md](AGENTS.md). Camera-height graphics validation in a clean client remains pending. Open **Shader Maker** from the pause screen while in a world, or press **N** in a world (rebindable in Controls).

**Verified in the block-scraper dev client (2026-11-02):** editor, presets, all sliders, curve add/drag/numeric/delete with left and right mouse, undo/redo, save/load (including invalid files), both export modes (an exported command pack was applied with `/posteffect`), apply/re-apply/disable in a world, persistence across restart, several window sizes. Not yet tested: a clean modless client, the Vulkan backend, and re-applying on Windows (the file-lock failure is fixed by releasing the pack before rewriting it, but can only be reproduced on Windows).

## Use

- Choose a starting preset: Neutral, Moonlit, Golden dusk, Dappled forest or Cold mist.
- Adjust intensity, exposure, contrast, saturation, vibrance, highlight roll-off and vignette under **Light**. **Levels** provides black/white points and gamma; **Balance** provides RGB gains. Controls paginate to fit the window.
- The graded specimen updates immediately. **Compare original** toggles the ungraded specimen. The reference can be the bundled game capture, synthetic specimen or a local PNG/JPEG through **Image…** in the editor or **Choose PNG / JPEG…** in Layers; it is independent of your project and export.
- **Curves → Edit curves** provides master RGB and individual channel curves. Left- or right-click to add a point, drag to move, or enter input/output values from 0–255 and press **Set point**. Arrow keys nudge; Shift moves ten steps; Delete removes interior points. Endpoints stay anchored in input. Each channel supports 2–16 points with shape-preserving cubic interpolation.
- **Undo/Redo** retains 100 edits. Slider and curve drags commit as one gesture. Ctrl/⌘ Z, Ctrl/⌘ Shift Z and Ctrl/⌘ Y also work; project name fields keep native text editing.
- **Shader Maker opens on the Layers home page** (N in a world, or the pause-menu button). Every layer row has its own On/Off, Grade, When… (conditions), ✎ rename, ⧉ duplicate, ▲/▼ reorder and ✕ delete; double-click a name to rename, Del deletes, Enter edits the selected grade, Ctrl+Z/Ctrl+Y undo/redo. Deletes are immediate and undoable. Fog objects are listed on the same page with a preset picker. Project name, Undo/Redo, New/Open/Save sit in the header; Apply to game, Disable, Auto/Command mode and Export sit in the footer. The grade editor and fog workspace return to this page with Done, Enter or Esc.
- The right side previews the bundled Minecraft capture or your PNG/JPEG. **Final / Original / Solo** compares the composition, source and selected grade without changing the export. **Inputs…** cycles inline camera Y, daylight tick, sky #RRGGBB (or a vanilla biome shortcut), global rain, thunder and visible-flash inputs. These simulate conditions on the image, not the actual weather/lighting in that image. The separate Sample Context screen has been removed.
- **Conditions → Sky colour → Choose sky colour…** offers vanilla-biome colour buttons and a custom `#RRGGBB` signal. Datapacks can set `minecraft:visual/sky_color` to drive a layer at chosen locations. Matching uses normalized RGB hue, not brightness or biome identity. It needs visible sky; darkness, weather, blending and overlays can suppress or change it. Shared stock biome names are shown in tooltips and the picker. Up to 16 distinct active hues are supported; indistinguishable hues block export.
- **Fog** (Edit on a fog row, or Add with a preset) opens the fog workspace: fog objects on the left, settings in the middle (Look / Shape / Camera / Quality), simulated preview on the right. Valid field edits apply immediately (invalid text stays visible with the reason and is never clamped); Undo covers every change. Presets — Soft haze, Thick fog, Ground mist, Low cloud, Interior haze, Misty morning — are placed relative to the ground below where you stand; **Use my position** fills coordinates. Fog builds up along each view ray, so deeper parts of a box/sphere (e.g. the back of a nave) get hazier. Distance haze, ground mist, height bands, boxes and spheres add optical depth and share native fog colour. **Terrain only**: entities, block entities, particles, sky, clouds and hand keep native fog. Auto export only; Command fog export is blocked. See the [UX/in-game handoff](docs/ux-flow-handoff.md).
- Strength and camera-height conditions export normally. Daylight, weather and lightning remain read-only for existing projects because their export paths are unimplemented. Different conditions combine with AND. Older exact biome IDs are preserved until you explicitly convert to a Sky colour signal, review its meaning and save conditions; they are never silently converted. Hiding a blocked layer retains its settings in project snapshots.
- **Apply to game** compiles an automatic resource pack, selects it and reloads game resources. Reapply after editing. **Disable** removes the preview pack and reloads. Other selected resource packs are retained. The preview pack selection persists across game restarts.
- **Save project** writes `<name>.json` under `config/shader-maker/projects`; **Load project** opens that library. Settings also recover from `config/shader-maker/draft.json`. **Path** copies the configuration folder path. Import old browser projects by placing their JSON in the projects directory; unsupported legacy active conditions are rejected rather than discarded. Projects with layers use v4; weather/lightning opt into v5, legacy stock sky selectors v6, fog v7 and custom sky colours v8. Versions 1–8 remain readable; existing exact-ID selectors are retained until explicit conversion.
- Choose **Auto pack** or **Command pack**, then **Export ZIP**. Exports go to `.minecraft/shader-maker/exports/<name>-always.zip` or `<name>-command.zip`. Repeated saves/exports replace the same named file atomically. Packs contain the project snapshot and installation instructions; the mod is not needed to use them.

Automatic packs define `minecraft:end_of_frame`. Command packs define `shader_maker:grade`; activate with `/posteffect add @s shader_maker:grade`, remove with `/posteffect remove @s shader_maker:grade` (permission required). Disable the editor's preview before testing an exported pack so you don't apply the grade twice.

The layout/condition changes pass offline compilation and model/native shader contracts; in-game visual review remains pending because this session could not initialize an SDL video device. See [layout comparison notes](docs/layer-workspace-review.md).

The current compiler performs display-referred color grading. Exact biome/daylight conditions, weather/lightning response remain planned vanilla-pack features with different levels of feasibility evidence. Source inspection confirms camera Y is available to post shaders; sky-colour signals replace exact identification only through explicit conversion. `GameTime` is elapsed game ticks, not daylight time. Effects defining `minecraft:end_of_frame` compete by resource-pack priority. Source inspection places post effects after hand rendering and before the 2D GUI; actual coverage and framebuffer encoding still need clean-client confirmation. Legacy imports with active masks or pulses currently fail explicitly.

## Build and verify

Requires Java **25**, Minecraft **26.3**, Fabric Loader **0.20.1** and Fabric API **0.165.0+26.3**. Dependencies are pinned in `gradle.properties`.

```sh
./gradlew build
# build/libs/shader-maker-0.1.0.jar
```

`check` runs reference/curve tests, project persistence/export/history checks, Minecraft's actual post-chain decoder, and native ShaderC with the game's Vulkan 1.3 settings plus Minecraft SPIR-V reflection. Native compilation does not replace a graphics-device or framebuffer test. Add `--fog-gl` for fog/custom-sky raster checks, or `--visual-gl` to the offline script to generate production visual-biome fixtures and run 90 actual pixel checks with system Mesa EGL; this still does not validate Minecraft bindings. The offline fallback, for an already populated Loom/Maven cache, is `python3 tools/verify-offline.py` with `JAVA_HOME` set to Java 25 and optionally `GRADLE_USER_HOME` pointing to the cache. It creates the same production JAR layout without invoking Gradle; confirm the normal Gradle build in the next environment.

## Block-scraper

The sibling `../block-scraper` checkout is picked up as development tooling automatically; override with `-Pblockscraper.dir=/path/to/block-scraper`. None of it is included in the shipped mod. Its separate source set registers `shader-maker`, `shader-maker-curves` and `shader-maker-projects`.

```sh
../block-scraper/block-scraper start --project-dir . --task runBlockScraper
../block-scraper/block-scraper open shader-maker
../block-scraper/block-scraper shot shots/editor.png
../block-scraper/block-scraper run tools/block-scraper-smoke.bs
# Open and inspect the PNGs; continue with docs/v0.1-handoff.md.
```

Use `reload` for method-body edits and `restart` for structural changes. The editor requires at least 340×220 GUI units; smaller windows/scales show a request to lower GUI scale.

The old browser harness remains in `tools/temporary-visual-review` only until real in-game visual review is available. The production Java model, curve compiler, project I/O and ZIP compiler are independent of it. It and all test/dev classes are excluded from the mod and exported packs. Its reference tests still run with `node tests/pipeline.test.cjs` and `node tests/curves.test.cjs`.

### Export availability and recovery

New conditions include camera height, strength and Sky colour signals. Legacy exact biome IDs, daylight, weather and lightning show preserved values read-only, with an explicit Clear action. The picker labels conversion from legacy exact IDs explicitly. This is an implementation limit of standalone export, not a claim that all possible vanilla techniques are impossible.

For an older project, **Resolve export…** lists the affected layers and each reason. Review/clear conditions or explicitly disable a layer while keeping its settings. These edits support Undo. Saving a project always preserves its conditions, even while export is unavailable, and save results/errors remain visible. A disabled unsupported layer cannot be re-enabled until its conditions are repaired. No conditions are silently discarded or flattened.

### Sky colour implementation and validation

Sky colour exports override vanilla `core/sky.fsh`, keeping its RGB formula and carrying a recognized stock or custom sky signature in opaque sky alpha. A 1×1 post pass votes over a 32×32 grid of main color/depth samples; three agreeing visible sky samples are required and tied winners return no signal. The full-screen compiler then evaluates ordered layers against the detected group, preserving the original-scene grading formula and height/strength weights. Recognized sky alpha is restored to one. No CPU parameters, mod renderer or datapack is involved.

The core override also loads in Command mode before activation; it conflicts with other sky overrides and uses of framebuffer alpha. Both activation modes, pack removal, transparency/overlays and backend behavior need clean-client checks. The image preview simulates a supplied sky colour (or stock shortcut) and enough visible sky; an image cannot prove detection.

Validation: 391 project checks; 16 actual Minecraft post-chain decodes; 66 native ShaderC/SPIR-V checks, including 44 terrain/custom-sky variant checks. Fog adds 349 model/export/sky assertions and 1,205 software raster assertions across 596 camera/ray cases; the 90 existing sky raster assertions also pass. The optional [headless raster smoke](tools/verify-visual-biomes-gl.py), using production exports generated by [VisualBiomeRenderFixtures.java](tools/VisualBiomeRenderFixtures.java), checks actual shader pixels on Mesa EGL. This is separate from Minecraft's pipeline/device binding and clean-client validation, which remain pending because SDL cannot initialize a video device in this session.


### Additional render context and Blocks only

Open **Layers → Conditions → More context → Render signals / effect scope** for experimental resolved fog colour/ranges, raw ambient/block-light/skylight/night-vision colours, and terrain vertex-tint conditions. They use vanilla world/lightmap inputs and can trigger without visible sky. Full-screen mode needs three matching solid/cutout terrain samples; **Blocks only** grades marked terrain pixels. Four distinct active condition sets are supported. Conditions within each set use AND; colour matching offers RGB or normalized hue and a tolerance, distances use inclusive ranges. **Use current world value** copies the fog/lighting value the game resolved where you stand (authoring aid only). Typed values are kept when you press Done; Conditions → Done commits the layer.

These are shared render signals, not biome IDs, an underwater detector, a Night Vision effect detector or exact block identities. Water's resolved fog reaches the generic fog controls, but translucent water cannot carry a terrain-tint marker. Transparency/overlays and other packs can invalidate or mimic alpha markers, so Blocks only is not guaranteed segmentation through glass/water. With no marked terrain on screen the last result is held (persistent post target) and changes cross-fade over about one second; terrain markers carry an RGB low-bit checksum so OIT-composited water cannot mimic them. Source help and exported README describe coverage.

The preview-input cycle adds **Render inputs…** with explicit simulated raw values, signal availability and a whole-specimen block/other assumption; screenshots have no terrain mask. v9 persistence, history, grade edits, copy/hide and Apply/Export preserve the new controls and existing conditions. No client runtime or implicit datapack is added.

See [the research, source inventory, implementation and clean-client checklist](docs/world-signals-research.md). `--context-gl` adds 491 Mesa EGL checks across all source types, both scopes, all four-bit masks and sky coexistence; 133 new reference checks and 586 new native shader cases also pass. These tests do not establish clean-client OpenGL/Vulkan bindings. The current additional-context build is [download/shader-maker-context-26.3.jar](download/shader-maker-context-26.3.jar).

### Where am I? Position, held signals and /posteffect (2026-11-05)

Conditions is now **Where & when**, with one tab per input and a plain reliability line:
- **Position**: camera height plus an exact **X/Z area box** with a fade distance.
- **Sky colour**: now held while no sky is visible, so it no longer flickers in caves or when looking down.
- **Render signals**.
- **Command switch**: the layer becomes its own `/posteffect` effect `shader_maker:<name>` with a fade-in, plus `<name>_off` to fade out. It has Try buttons, Copy command, and a helper datapack (clickable chat menu, on/off/toggle functions, command-block givers) to export or install into the current singleplayer world.

Project v10 stores area, command and fade options. A ready-made demo resource pack and datapack is in [download/command-demo-26.3](download/command-demo-26.3/README.txt): looks switch automatically from exact biome, water, depth, weather, village and health checks. Findings, the datapack biome-id recipe and hard limits are in [world-to-shader channels](docs/world-to-shader-channels-26.3.md).
