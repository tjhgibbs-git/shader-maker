# Java 26.3 resource-pack export contract

The [vanilla resource pack spec](vanilla-only-handoff.md) defines the intended product. This page distinguishes the current compiler from the planned contract; [feasibility evidence](vanilla-feasibility.md) records verified inputs and remaining graphics tests.

The production compiler is `src/main/java/io/github/tjhgibbs/shadermaker/PackCompiler.java`; the earlier browser reference remains in `tools/temporary-visual-review/pipeline.js` until in-game visual review is available. It writes ZIP-root metadata, one post-effect definition, the custom fragment, a project snapshot and installation notes. The mod exports named ZIPs under `.minecraft/shader-maker/exports`; the browser reference downloads `resources.zip`; a failed capability check creates no download and leaves the project intact.

The target is pinned to resource format 97.1 using `min_format` and `max_format`. The activation mode is explicit: automatic `minecraft:end_of_frame`, or namespaced command effect `shader_maker:grade`. These modes must not both run the same grade and double-apply it.

## Supported settings

**2026-11-03 additions:** Auto packs can include authored terrain distance/height/box/sphere fog, using resolved native fog colour and preserving native fog afterward. Active fog blocks Command export. Grade-layer Sky colour conditions now accept custom `#RRGGBB` datapack signals as well as stock colour shortcuts, with visible-sky and hue/brightness limitations. Fog uses schema v7; custom sky colours v8; older data remains readable. See [the current fog/sky scope and validation handoff](fog-sky-in-game-handoff.md).

Exposure, levels, gamma, contrast, saturation, RGB balance, vignette, highlight shoulder, vibrance, master RGB and individual channel curves, and opacity. Cubic curve coefficients are compiled into GLSL; no additional texture input is needed. Preview and export use the same display-referred grading formula. Alpha passes through unchanged for ordinary grades; experimental visual groups restore recognized opaque-sky transport alpha to one. The compiler emits enabled layers in evaluation order, each grading the original scene before blending over the running result. Unconditional and camera-height layers export, including inward smooth feathering. Camera height uses the vanilla Globals include and reconstructs `float(CameraBlockPos.y) - CameraOffset.y`. Native shader/codec tests cover this path; clean-client graphics tests are still pending. Enabled exact biome/daylight/global rain/thunder/visible-flash conditions report the affected layer and unresolved input. Legacy active masks/pulses are also rejected. These are implementation limits, not evidence that conditions require a mod. The planned compiler retains biome/Y/daylight requirements and investigates weather, lightning and atmosphere through vanilla post/core resources. Camera Y has a source-backed path; exact biome-ID and daylight inputs still require investigation. Preserve unresolved settings rather than stripping or flattening them.

ZIPs are reproducible and contain no source image or temporary UI files. Reopening the embedded project reproduces editor settings, not the original screenshot. Export does not modify settings or silently disable unsupported features.

## Required clean-client smoke test — pending

1. Start an unmodified Java **26.3** client. Record backend (OpenGL/Vulkan), GPU and game log.
2. Enable an always-on Golden dusk pack; verify resource reload succeeds without shader errors and grading becomes visible without commands. Disable it and verify restoration.
3. Enable a command-mode pack. Confirm no effect before `/posteffect add @s shader_maker:grade`, visible change after it, and restoration after `/posteffect remove @s shader_maker:grade`.
4. Compare neutral output and a fixed screenshot against the reference, accounting for framebuffer color encoding. Check bright clouds, dark caves, transparent water and scene edges; confirm alpha behavior and resized/fullscreen windows.
5. Test priority against another pack defining `minecraft:end_of_frame`. Document the winning pack and confirm disabling the winner restores the other effect.
6. Repeat shader loading on OpenGL and Vulkan. Check relevant world, GUI and HUD behavior rather than assuming the hook's coverage. Source inspection places effects after hand/screen effects and before the 2D GUI, correcting earlier blanket HUD claims.
7. Verify the exported ZIP works with the authoring mod absent. For a custom map, document `resources.zip` placement and the chosen activation mode.

Passing ZIP/JSON and browser GPU checks establishes structure and math only. Until this checklist passes, describe exports as experimental and do not publish them as validated Minecraft packs.

## Required conditional-output contract

Apply and Export consume the same compiled resources. Enabled layers/effects must retain their order, strength, curves and complete conditions. An error identifies the affected item and missing capability; no condition is silently omitted and no mod-only renderer supplies runtime inputs. Version-pinned core shader resources are eligible when post effects lack the required inputs; report the resources overridden and test their activation/removal in both modes. Preserve exact biome selectors until a proven implementation or explicit reviewed conversion exists. See the spec for milestones and acceptance.

## Optional datapack integration (planned)

A paired export/integration mode may expose named effects to server-owned events using `/posteffect`. Keep its installation requirements explicit and preserve standalone resource-pack mode. The datapack can own exact biome/gameplay checks and event start/end; numeric uniforms and shader-to-server feedback are not supported by the inspected command interface. Blood Moon is the reference integration test in [the next-session handoff](in-game-test-handoff.md). No paired export or event preset is implemented yet.

## Authoring availability revision

The editor now prevents adding conditions without a standalone export implementation. Existing unsupported values remain read-only and recoverable; Clear + Save explicitly removes a condition. Resolve export opens per-layer diagnostics with each reason, review and disable actions, and project saving. Saving is independent of export capability. The previous unrestricted condition-authoring UI is superseded by this behavior. Exact biome, daylight, weather and flash runtime paths remain unimplemented.

## Experimental visual sky groups

Version 6 stores `visualBiomeGroup` separately from preserved legacy `biomes`. Only explicit selection converts old IDs. The authoring selector lists the 16 stock sky signatures and their shared biome names, with a visible warning: **datapacks changing sky colors can change or break detection**. It is visual classification, not exact biome identification. Fog-only data changes do not directly change this classifier, which samples the pre-fog sky signal.

The compiler emits a pinned `assets/minecraft/shaders/core/sky.fsh` override and a 1×1 detection target/pass before the ordered grade. It encodes normalized stock sky hue in opaque sky alpha, leaves sky RGB unchanged, and votes on a 32×32 grid using main depth to reject covered pixels. Three samples and a unique winning signature are required. Missing, dark, unknown or tied signals produce no match; sky must be visible. Weather, custom dimensions, biome blending, immersion and overlays may suppress or change matches. The image preview simulates stock daylight sky and visibility rather than measuring them.

Core resources load in both Auto and Command modes; the marker exists before command activation. The override and alpha use can conflict with other packs. Pack removal, command activation/removal, overlays, OIT/translucency and OpenGL/Vulkan operation still need clean-client testing. The generated README names the override, all selected group members and these limitations. Export is available experimentally so that it can be tested without installing any client runtime.


Additional context (v9): see [world-signals-research.md](world-signals-research.md). Render condition exports add `detect_context`, a 1×1 target, and terrain overrides. Raw lighting conditions also override `core/lightmap.fsh` and `include/sample_lightmap.glsl`, preserving native lighting RGB and restoring sampled alpha. The resulting full-screen grade or experimental marked-block scope needs no runtime mod. These overrides conflict with other terrain/lightmap/alpha consumers and load even before Command-mode post activation; their device validation remains pending.
