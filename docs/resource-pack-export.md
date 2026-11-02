# Java 26.3 resource-pack export contract

The experimental compiler is `tools/temporary-visual-review/pipeline.js`. It writes ZIP-root metadata, one post-effect definition, the custom fragment, a project snapshot and installation notes. The browser's export button downloads `resources.zip`; a failed capability check creates no download and leaves the project intact.

The target is pinned to resource format 97.1 using `min_format` and `max_format`. The activation mode is explicit: automatic `minecraft:end_of_frame`, or namespaced command effect `shader_maker:grade`. These modes must not both run the same grade and double-apply it.

## Supported settings

Exposure, levels, gamma, contrast, saturation, RGB balance, vignette, highlight shoulder, vibrance, master RGB and individual channel curves, and opacity. Cubic curve coefficients are compiled into GLSL; no additional texture input is needed. Preview and export use the same display-referred grading formula. Alpha passes through unchanged. Feathered context masks remain preview/reference features; any enabled biome, altitude, facing or time condition, or active pulse, blocks export. A zero-depth pulse has no effect and is permitted.

ZIPs are reproducible and contain no source image or temporary UI files. Reopening the embedded project reproduces editor settings, not the original screenshot. Export does not modify settings or silently disable unsupported features.

## Required clean-client smoke test — pending

1. Start an unmodified Java **26.3** client. Record backend (OpenGL/Vulkan), GPU and game log.
2. Enable an always-on Golden dusk pack; verify resource reload succeeds without shader errors and grading becomes visible without commands. Disable it and verify restoration.
3. Enable a command-mode pack. Confirm no effect before `/posteffect add @s shader_maker:grade`, visible change after it, and restoration after `/posteffect remove @s shader_maker:grade`.
4. Compare neutral output and a fixed screenshot against the reference, accounting for framebuffer color encoding. Check bright clouds, dark caves, transparent water and scene edges; confirm alpha behavior and resized/fullscreen windows.
5. Test priority against another pack defining `minecraft:end_of_frame`. Document the winning pack and confirm disabling the winner restores the other effect.
6. Repeat shader loading on OpenGL and Vulkan. Check relevant world, GUI and HUD behavior rather than assuming the hook's coverage.
7. Verify the exported ZIP works with the authoring mod absent. For a custom map, document `resources.zip` placement and the chosen activation mode.

Passing ZIP/JSON and browser GPU checks establishes structure and math only. Until this checklist passes, describe exports as experimental and do not publish them as validated Minecraft packs.
