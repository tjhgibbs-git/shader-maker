# Shader Maker 0.1 · Minecraft Java 26.3

A visual authoring tool for **vanilla Minecraft resource pack shaders**. The current editor runs as a client-only Fabric mod; exported packs run without it. The product direction includes conditional grading and atmosphere driven by biome/environment, Y-level, daylight, weather and lightning. An optional paired datapack mode is planned for server-controlled events such as a blood moon. See the [authoritative spec](docs/vanilla-only-handoff.md) and [vanilla feasibility evidence](docs/vanilla-feasibility.md). The [next-session handoff](docs/in-game-test-handoff.md) gives the concrete modless test sequence.

The implementation currently exports static full-screen color grades. Open **Shader Maker** from the title/pause screen, or press **N** in a world (rebindable in Controls).

**Verified in the block-scraper dev client (2026-11-02):** editor, presets, all sliders, curve add/drag/numeric/delete with left and right mouse, undo/redo, save/load (including invalid files), both export modes (an exported command pack was applied with `/posteffect`), apply/re-apply/disable in a world, persistence across restart, several window sizes. Not yet tested: a clean modless client, the Vulkan backend, and re-applying on Windows (the file-lock failure is fixed by releasing the pack before rewriting it, but can only be reproduced on Windows).

## Use

- Choose a starting preset: Neutral, Moonlit, Golden dusk, Dappled forest or Cold mist.
- Adjust intensity, exposure, contrast, saturation, vibrance, highlight roll-off and vignette under **Light**. **Levels** provides black/white points and gamma; **Balance** provides RGB gains. Controls paginate to fit the window.
- The graded specimen updates immediately. **Compare original** toggles the ungraded specimen. The reference can be the bundled game capture, synthetic specimen or a local PNG/JPEG through **Context → Sample image…**; it is independent of your project and export.
- **Curves → Edit curves** provides master RGB and individual channel curves. Left- or right-click to add a point, drag to move, or enter input/output values from 0–255 and press **Set point**. Arrow keys nudge; Shift moves ten steps; Delete removes interior points. Endpoints stay anchored in input. Each channel supports 2–16 points with shape-preserving cubic interpolation.
- **Undo/Redo** retains 100 edits. Slider and curve drags commit as one gesture. Ctrl/⌘ Z, Ctrl/⌘ Shift Z and Ctrl/⌘ Y also work; project name fields keep native text editing.
- **Layers** provides experimental named, ordered grades with biome, altitude and time conditions. Use **Context** beside the specimen to set a simulated biome, height and time; cycle the sample view between All, Original and Solo. Solo previews the selected grade without its conditions. These layers currently affect only the CPU sample: Apply/Export reject enabled layers. This is an implementation gap. The planned compiler evaluates conditions through vanilla rendering inputs; a mod renderer is not the delivery path. Biome project data stays intact while exact-ID export is investigated.
- **Apply to game** compiles an automatic resource pack, selects it and reloads game resources. Reapply after editing. **Disable** removes the preview pack and reloads. Other selected resource packs are retained. The preview pack selection persists across game restarts.
- **Save project** writes `<name>.json` under `config/shader-maker/projects`; **Load project** opens that library. Settings also recover from `config/shader-maker/draft.json`. **Path** copies the configuration folder path. Import old browser projects by placing their JSON in the projects directory; unsupported legacy active conditions are rejected rather than discarded. Projects with layers use the newer v4 format and cannot be opened by older v0.1 builds.
- Choose **Auto pack** or **Command pack**, then **Export ZIP**. Exports go to `.minecraft/shader-maker/exports/<name>-always.zip` or `<name>-command.zip`. Repeated saves/exports replace the same named file atomically. Packs contain the project snapshot and installation instructions; the mod is not needed to use them.

Automatic packs define `minecraft:end_of_frame`. Command packs define `shader_maker:grade`; activate with `/posteffect add @s shader_maker:grade`, remove with `/posteffect remove @s shader_maker:grade` (permission required). Disable the editor's preview before testing an exported pack so you don't apply the grade twice.

The current compiler performs display-referred color grading. Conditional layers, weather/lightning response and atmosphere are planned vanilla-pack features with different levels of feasibility evidence, not implemented effects. Source inspection confirms camera Y is available to post shaders; exact biome identification remains open. `GameTime` is elapsed game ticks, not daylight time. Effects defining `minecraft:end_of_frame` compete by resource-pack priority. Source inspection places post effects after hand rendering and before the 2D GUI; actual coverage and framebuffer encoding still need clean-client confirmation. Legacy imports with active masks or pulses currently fail explicitly.

## Build and verify

Requires Java **25**, Minecraft **26.3**, Fabric Loader **0.20.1** and Fabric API **0.165.0+26.3**. Dependencies are pinned in `gradle.properties`.

```sh
./gradlew build
# build/libs/shader-maker-0.1.0.jar
```

`check` runs reference/curve tests, project persistence/export/history checks, Minecraft's actual post-chain decoder, and native ShaderC with the game's Vulkan 1.3 settings plus Minecraft SPIR-V reflection. Native compilation does not replace a graphics-device or framebuffer test. The offline fallback, for an already populated Loom/Maven cache, is `python3 tools/verify-offline.py` with `JAVA_HOME` set to Java 25 and optionally `GRADLE_USER_HOME` pointing to the cache. It creates the same production JAR layout without invoking Gradle; confirm the normal Gradle build in the next environment.

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
