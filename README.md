# Shader Maker · Minecraft Java 26.3

A look-development prototype with a CPU reference and a vanilla **26.3 resource-pack compiler**. It is not yet a Fabric mod or an in-game editor. Generated packs still require a clean-client smoke test before release.

## Try it

Open `tools/temporary-visual-review/index.html` in a modern browser (or serve that directory with `python3 -m http.server`). Import a Minecraft screenshot, choose a preset and adjust the look. Use **Split view** or hold **Compare** to inspect the original; save a PNG to review the result. Images stay on your machine.

New controls include highlight roll-off, which preserves bright detail under positive exposure, and vibrance, which boosts muted colors more than already saturated colors. Golden dusk is restrained to avoid a heavy orange cast; Dappled forest is a new warm forest starting point. These are artistic grades, not simulated lighting or ray tracing.

**Export resources.zip** produces an experimental vanilla pack targeting Java 26.3 / resource format **97.1**, with two activation choices:

- **Always on:** defines `minecraft:end_of_frame`; enabling the pack activates the grade. Disable the pack to remove it. Other packs using the same effect compete by pack priority.
- **Command:** defines `shader_maker:grade`; use `/posteffect add @s shader_maker:grade`, then `/posteffect remove @s shader_maker:grade` to remove it. Commands require permission.

The shader uses a separate intermediate target before copying back to the main target. It preserves alpha and uses explicit shader input/output locations. Biome, altitude, facing, time masks and active pulses block export with an explanation; the compiler does not silently bake the current simulated context. Disable them explicitly to create an unconditional grade. The original project remains unchanged by exporting.

Project JSON version 2 records the 26.3 target and new controls. Version 1 projects still load with the new controls at zero, retaining their previous calculation. Project files do not embed the source image.

## Verify

Java 25 is the target runtime for the planned 26.3 client integration. The reference currently also compiles on Java 17+.

```sh
mkdir -p out
javac -d out $(find src -name '*.java')
java -cp out io.github.tjhgibbs.shadermaker.AtmosphereTest
node tests/pipeline.test.cjs
```

The optional browser/GPU test needs Playwright and Chromium: `node tests/browser.test.cjs` (set `CHROMIUM_PATH` if necessary). It checks the generated fragment math using WebGL2, export gating and responsive layout; it does **not** validate Minecraft's ShaderC loader or framebuffer color space.

## Scope and remaining work

The grading math is deliberately display-referred, matching the existing 8-bit screenshot preview. It is not an HDR scene-linear pipeline. The preview, Java reference and generated GLSL share the same operation order. Actual Minecraft framebuffer encoding must be checked in-game before claiming image parity.

The disposable browser harness must be deleted once an in-game editor replaces it; the reusable compiler must move into the production implementation first. There is no live client context feed, undo history, Fabric rendering hook or mod JAR yet. See [the 26.3 review and implementation notes](docs/26.3-review.md) and [export contract and smoke test](docs/resource-pack-export.md).
