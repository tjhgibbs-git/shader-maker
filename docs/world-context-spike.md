# World context: authoring snapshots and vanilla shader inputs

The [vanilla shader spec](vanilla-only-handoff.md) replaces the previous requirement for a separate mod renderer. [Feasibility evidence](vanilla-feasibility.md) records source-backed input paths and open questions.

## Exported context

Vanilla already updates Globals with camera block position and offset. A conditional shader can reconstruct camera Y as `float(CameraBlockPos.y) - CameraOffset.y`. The post pipeline includes the Globals binding; mod-owned writable custom uniform buffers are unnecessary for this input.

`LevelExtractor` supplies elapsed `level.getGameTime()` to that block. The resulting GameTime phase must not be labeled daylight time. Sky rendering separately reads the SUN_ANGLE environment attribute. A vanilla daylight path remains an investigation requirement, including `/time` changes, clock pause/rate, dimensions and multiplayer.

Biome attributes feed world fog/sky colors, and terrain vertices carry biome-driven tint. Exact biome IDs are not present in the inspected standard GLSL blocks. Investigate core-stage classification/transport and texture techniques before reaching a scope decision; preserve existing exact-biome selectors. Post-stage Fog is reset to NONE before post effects in the inspected renderer, so simply including fog.glsl does not establish a world-environment input.

Weather and lightning also affect vanilla sky, fog and lightmap paths. Their availability and semantics depend on stage. Inspect actual bindings and distinguish global weather, local precipitation and flash state; see the evidence and test matrix.

## Existing authoring aids

`WorldContext.capture` snapshots the initialized camera's Y/yaw/biome, dimension ID and raw default clock modulo 24000. It returns no context without a camera in the current level. Biome can be absent for an unavailable chunk/registry entry; time can be absent without a default clock. This Java snapshot is for authoring diagnostics and sample setup, never the runtime input source for an exported pack.

`/shadermaker context`, `/shadermaker capabilities` remain implemented. The Sample Context screen and its Use world control were removed in the layout comparison revision. Inline preview inputs beside the reference image are simulated; the commands still provide world diagnostics. The historical handoff records singleplayer UI/command tests, including time changes. Those observations do not prove that a GLSL input follows the same clock.

The clock manager can create a clock instance before its first server update; an initial zero cannot yet be distinguished through the inspected public API. Interpolation, custom clocks, dimension changes and multiplayer synchronization remain relevant authoring-snapshot checks.

## Historical uniform investigation

The earlier investigation correctly found that JSON custom uniform buffers are allocated at post-pass construction, have no public per-frame setter and lack writable usage flags. It incorrectly generalized that finding to all conditional output. Engine-owned default Globals updates are a separate path. No writable-buffer mixin, custom mod render pipeline or per-tick pack reload is part of the product roadmap.

## Explicit visual groups

The production visual-biome path now uses normalized sky-color signatures and an experimental vanilla core-sky/alpha-to-post transport, not `WorldContext` or mod-owned uniforms. The user accepts shared signatures and datapack changes affecting them, with clear UI warnings; no client dependency may be proposed. See the implementation follow-up in [feasibility evidence](vanilla-feasibility.md). It currently requires visible sky; fog-only edits are not directly used by the classifier.
