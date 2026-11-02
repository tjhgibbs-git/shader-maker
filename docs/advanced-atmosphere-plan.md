# Advanced grading and atmosphere plan

## Goal and delivery boundaries

Extend the current static, full-screen grade into a world-aware editor with biome, camera altitude and time conditions, adjustable distance fog, and volumetric fog. Keep v0.1 projects visually unchanged. The existing resource-pack export bakes constants into a color-only post effect; it cannot be assumed to provide live biome, camera position, depth or lighting. Treat mod-driven live preview and standalone pack export as separate capabilities. Disable unsupported exports with a specific explanation; never silently drop active settings.

## 1. Rendering feasibility and contracts

- Verify Minecraft 26.3/Fabric world-render hooks and the actual availability, lifetime and encoding of depth, projection matrices and scene color on supported graphics backends. Identify a point before HUD rendering, and check sky, water, transparency and vanilla fog interaction in-game.
- Prototype live per-frame parameters without resource reloads. Confirm how the current camera biome, camera Y and world time are obtained, including dimension changes and unloaded worlds. Decide how to transfer values to shaders with the actual engine API.
- Document a capability matrix for live mod preview, automatic export and command export. Prove any standalone dynamic behavior before offering it. Record backend limitations and fallback behavior.
- Gate later fog work on this spike; native shader compilation alone does not establish that a depth buffer or render hook works on a graphics device.

## 2. Project model and conditional grading

- Version the project schema and migrate v0.1 projects to one unconditional base grade with no fog. Preserve existing curve data and browser import validation; validate ranges, identifiers and malformed data without replacing the active project on failure.
- Add ordered grade layers, each with its own grade and optional biome IDs, feathered altitude range and circular time range. Conditions within a layer multiply; empty conditions match everywhere. Define layer ordering explicitly: evaluate from first to last, blending each result over the preceding grade by its condition weight and layer strength. Blend evaluated colors, including curves, rather than interpolating curve control points.
- Specify smooth transitions at altitude/time boundaries and across biome changes; make biome transition duration configurable or choose and document a fixed default. Use camera biome and camera Y as the initial semantics, not per-pixel world positions. Handle midnight wrap and missing world context deterministically.
- Reuse and extend the pure range and mask logic in `Atmosphere` where appropriate. Add a runtime context provider and shader parameter path; do not regenerate/reload packs every tick. Set a documented layer limit based on measured shader/backend constraints.

## 3. Editing and preview

- Add a layer list with add, duplicate, reorder, enable and remove actions, condition controls, and grade/curve editing for the selected layer. Replace brittle hard-coded control index groupings with explicit groups. Maintain undo/redo as single gestures and keep the editor usable at supported minimum sizes.
- Show the current world context and allow a simulated biome, Y and time for the specimen preview. Clearly distinguish the synthetic specimen from the in-world result; provide an easy way to test transitions and reset simulation.
- Save, load, draft recovery and embedded export snapshots must retain all new settings. Present invalid identifiers, invalid ranges and unsupported features as actionable errors. Keep neutral defaults.

## 4. Distance fog

- After the rendering spike, implement adjustable fog start/end or density and color in a world-render path with verified distance/depth. Define how it combines with vanilla fog and whether the sky is affected; default to no additional effect.
- Provide live controls and a depth-aware preview where practical. Address camera projection, resize, sky depth and transparent surfaces explicitly. Do not represent a full-screen tint as distance fog.
- Expose standalone export only where its inputs and behavior are demonstrated; otherwise provide a clear mod-required message.

## 5. Volumetric fog

- Build on the verified depth path with bounded ray marching through a configurable density field, including height falloff and color. Bound sample count and distance; offer quality presets and measure frame cost at representative resolutions.
- Treat lighting and shadows as a separate capability: introduce them only when reliable scene light/shadow inputs are available. Describe the initial implementation accurately if it is unlit density fog. Define behavior for sky, water, translucent geometry and other dimensions.
- Fail gracefully or disable the effect when required render inputs are unavailable; preserve the project settings.

## 6. Validation and release

- Unit-test schema migration, round trips, malformed input, layer ordering and blending, biome matching/transitions, altitude feathers, time wrap, neutral settings and undo/redo. Compare CPU and shader grading at representative contexts.
- Compile and validate shaders and post-chain contracts for each supported backend. Run in-game graphics tests for all conditions, fog depth, sky, water, transparency, dimensions, HUD, window resize and resource reload; test changes while moving and across midnight/biome boundaries.
- Measure frame time for quality presets and multiple layers; document tested hardware and limitations. Test export installation and unsupported-feature errors separately from mod preview. Update user documentation and version notes only after verifying behavior.

## Suggested sequence

Complete the rendering feasibility spike first; then build the schema/runtime context and layer evaluation, editor and preview, distance fog, and finally volumetric fog. Deliver each phase with its own tests and documentation. The spike may change the rendering design or export capability matrix before implementation begins.
