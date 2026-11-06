# Shader Maker: vanilla resource pack shader authoring spec

**Authoritative product spec, revised 2026-11-02; fog/sky implementation follow-up 2026-11-03.** This replaces the live-renderer roadmap in the earlier handoffs. Implementation status is separate from requirements; this revision does not implement conditional export. Engine findings and diagnostic packs are recorded in [vanilla-feasibility.md](vanilla-feasibility.md).

## Product and deliverable

Shader Maker is a visual tool for building Minecraft Java **26.3 resource pack shaders**. The exported ZIP runs in an unmodified vanilla client. The existing Fabric editor is the authoring application; players using the output need no authoring mod, shader loader, client mixin or background parameter service.

**Non-negotiable user direction:** no client runtime dependency, ever. Do not ask to relax this requirement. See [AGENTS.md](../AGENTS.md).

The intended output includes conditional grading and atmosphere, with biome/environment, altitude, time, weather and lightning behavior where vanilla rendering can supply the inputs. Do not reduce the product to static color grading because a mod renderer would be easier. Investigate vanilla post effects, core shaders, textures and combinations of those paths before declaring a feature unavailable. Core shader overrides remain eligible: they run in vanilla, although Mojang treats their interfaces as unsupported and they require exact-version validation.

Offer two explicit delivery modes: **Resource pack only**, for standalone visual behavior, and optional **Resource pack + datapack integration**, for server-controlled events and exact gameplay conditions. The paired mode remains vanilla but requires the datapack installed in the world/server. It does not replace the resource-pack-only deliverable or silently add a server dependency. A player installing an automatic standalone pack needs only to enable that pack. The existing explicit command activation mode is also retained.

## Non-negotiable behavior

- Apply previews the **same generated resources** that Export writes. The editor never substitutes a private mod renderer for the exported effect.
- Vanilla updates engine inputs each frame; the compiler emits their use into GLSL. Resource reloads happen on edited-pack application, never to track movement, time or weather.
- A missing uniform in one stage is a reason to inspect other vanilla stages, not evidence that the feature requires a mod.
- An unproven feature stays an explicit research requirement. Do not delete its project data, replace its meaning or advertise it as working.
- Every enabled effect must be represented faithfully in output. A compiler error names the layer/effect, missing input and current limitation. No silent dropping, condition flattening or automatic disabling.
- Release claims distinguish source inspection, native compilation and actual clean-client graphics tests. The current implementation remains experimental.

See [the 2026-11-03 handoff](fog-sky-in-game-handoff.md) for the current Fog workspace, explicit/custom Sky colour context and deliberately excluded features. Its implementation evidence supersedes older “not implemented” atmosphere statements below.

## Current implementation versus required output

| Area | Implemented today | Required direction |
|---|---|---|
| Base grade | Exposure, levels, gamma, contrast, saturation, balance, vignette, shoulder, vibrance, curves and opacity export | Preserve controls, neutral identity, alpha and color math |
| Ordered layers | CPU evaluation and vanilla shader compilation of unconditional/camera-Y layers; clean-client graphics tests pending | Compile layers into vanilla shaders, starting with unconditional and camera-Y layers |
| Y-level | Sample condition and compiled `Globals` camera-Y range/feather | Keep; camera Y is `float(CameraBlockPos.y) - CameraOffset.y`. Investigate surface world Y separately |
| Biomes | Explicit experimental visual sky groups, preserving legacy exact selectors | Shared biome signatures are accepted. Clearly list all known members and warn that datapack sky-color changes, blending, weather, darkness and missing visible sky can change/suppress matches; no claim of exact camera-biome IDs |
| Time of day | Editable circular condition and simulated daylight input; no export path | Keep; find a vanilla daylight signal. `GameTime` is elapsed ticks modulo 24000, **not** the daylight clock |
| Weather | Editable global rain/thunder ranges and simulation; no export path | Investigate rain/snow, wetness and thunder response using vanilla sky, fog, precipitation and lightmap paths |
| Lightning | Editable visible-flash range and simulation; no export path | Investigate flash response; separate a flash from thunder weather and a visible lightning bolt |
| Distance/height fog | Terrain distance/height/box/sphere integration implemented; native and software-raster checks pass, Minecraft device checks pending | Native fog colour; terrain scope; see [fog/sky handoff](fog-sky-in-game-handoff.md) |
| Volumetric atmosphere | Analytic terrain density and bounded soft-edge integration implemented | Animated noise parked for a future experimental branch; lit fog/sunbeams deliberately excluded by user scope |
| Animation/pulses | Legacy active pulses rejected | Investigate elapsed-tick animation, wrap and pause behavior; do not reject all animation because the available timer wraps |
| Facing/screen masks | Legacy math exists; no facing export | Research core-stage matrices for facing; screen gradients need no world data |
| Reference preview | Bundled/synthetic/custom reference; inline simulated inputs and world diagnostic commands | Retain authoring aids; match their inputs and formulas to the chosen export path |

This matrix is a work plan, not a list of shipping promises. A static first milestone does not close biome, weather, lightning or fog requirements.

## Vanilla rendering design

Use one versioned compiler with resource output chosen by each effect's inputs. The initial post-effect path remains useful for image grading, screen masks and camera-Y conditions. Core-stage resources may provide world positions, biome-influenced tint, world fog, lightmap or celestial inputs unavailable to a post effect. A combined pack may be necessary; prove any signal transfer between stages rather than assuming arbitrary uniforms or render targets can be created from JSON.

Inspect binding layouts and actual values **at the consuming stage**. `bindDefaultUniforms` alone does not prove every block is present or still contains world data. Source inspection found that the post-processing snippet includes `Globals`, that `render3dHud` resets fog to NONE before post effects, and that a post pass replaces `Projection` with an orthographic matrix. The current handoff's old “read world fog and inverse projection in the post shader” proposal is therefore not established.

The compiler reports resources it overrides and expected conflicts. Automatic packs defining `minecraft:end_of_frame` compete by pack priority; core overrides can also conflict with other packs. Pin generated resources to the tested version. Keep Automatic and Command output behavior explicit, and verify that any core-stage portion has the promised activation/removal behavior in both modes.

## Layers, conditions and priority

Retain the current ordered blend semantics for grading. Let `S` be the original sampled color. Apply the base grade first. For each enabled layer in evaluation order:

```
w = strength * altitudeWeight * timeWeight * biomeWeight * otherConditionWeights
R = mix(R, gradeThisLayer(S), clamp(w, 0, 1))
```

Each layer grades the original sample, including its own grade opacity, then blends over the running result. Later layers contribute last; a fully weighted later layer replaces the preceding grade result. This is not a winner-takes-all priority rule. Conditions on one layer combine with AND (multiply); an explicit selection of alternatives within one condition uses OR. Preserve existing formulas unless a deliberate, versioned change is made.

Camera-altitude and surface-height masks have different meanings and controls. Biome-at-camera and biome-at-surface also differ. A visible terrain tint must not be presented as the camera's exact biome. A daylight condition must follow the sun and the actual world clock rather than elapsed game ticks. Weather response must distinguish global storm strength from local precipitation. Lightning flash strength must respect the game's Hide Lightning Flashes setting.

Atmosphere may require a separate ordered effect stage rather than another grade of the original image. Define where fog combines with vanilla fog and grading, which scene elements it affects, and its compositing/alpha rules before implementing it. Do not force those semantics into the grade-layer model.

## Interface: three comparable composition views

**User-directed revision:** implement Stack, Graph and Overview switches in the Layers workspace so the user can choose after trying all three. Retain the existing ordered blend semantics in every view. The graph is an automatically arranged, selectable dependency view with original-image grade branches and a running mix chain; it does not introduce arbitrary routing, reusable masks or new blend semantics. See [the comparison notes](layer-workspace-review.md). The separate Sample Context screen is removed; reference inputs live beside the image.

The target workspace has a sample or in-world preview, a persistent stack, and an inspector for the selected item. At smaller sizes these can use tabs without losing selection or scroll position.

- Draw the final/topmost layer at the top of the stack, with a “blends over layers below” cue. Evaluate the internal list in the existing order and map it explicitly to the display order.
- Each row shows enabled state, name, strength, compact condition summary and current evaluated contribution. Allow add, duplicate, delete and drag reorder, plus keyboard move actions. Keep the base grade anchored at the bottom.
- Selecting a row opens Grade, Conditions and (where applicable) Effect settings. A condition editor shows input value, range and feather, and resulting weight. Explain why a layer currently contributes zero.
- Provide final/original/selected-layer comparisons and a non-destructive solo preview. Solo must not alter the saved enable flags or exported output.
- Label inline reference inputs as simulated. Camera Y, daylight, biome, global rain/thunder and visible flash are authorable and previewable; their availability in vanilla output is a separate, explicit capability. Color-only reference images cannot validate depth or exact biome behavior; use appropriate fixtures for those.
- Keep research status in authoring diagnostics. Authoring controls must distinguish image simulation from proven exported behavior; do not build a second mod-only effects library. The comparison workspace also authors time, biome, weather and lightning conditions, as requested by the user. Those settings remain recoverable and explicitly block enabled output until their vanilla paths are established.

For example, the stack could read:

```
Final output
  Lightning response   [conditions • strength • evaluated weight]
  Storm grade          [conditions • strength • evaluated weight]
  Night grade          [conditions • strength • evaluated weight]
  Base grade
Original scene
```

This is an interface sketch, not a claim that those effects already export. A selected layer's inspector explains its measured/simulated inputs and blend weight. Higher rows blend over lower ones; they do not automatically exclude them.

### Optional event integration

A datapack can own an event state and activate/remove named resource-pack post effects through `/posteffect`. Exported effect IDs and start/stop examples form a stable integration contract. Arbitrary scoreboard/storage-to-uniform transfer and shader-to-datapack feedback are not established interfaces; do not promise them. Shared elapsed time can coordinate periodic behavior, but does not automatically reset animation phase on event activation or provide frame-exact synchronization.

A **Blood Moon** is the reference use case: the datapack starts/stops the event and controls monster waves; the visual pack supplies the authored red sky/moon and atmosphere. Schedule on a full-moon night or investigate a dedicated moon-phase/environment timeline. Sky-only coloration and a conditional moon appearance require their own proven rendering path; a full-screen tint is not equivalent. Natural-spawn configuration remains subject to its normal constraints; controlled extra waves are datapack gameplay logic around active players.

Represent an event above its visual stack, with a name, effect IDs, activation/removal hooks and preview state. Keep event state separate from blend priority. This adds a useful integration boundary without requiring a node graph or turning Shader Maker into a general gameplay/datapack editor. Joined players, dimension changes, event end and pack removal must have defined behavior. The blood-moon example is a prototype/test requirement, not an implemented preset.

An optional read-only contribution view can show `input → base → layer blends → atmosphere/output`, with each weight and reason. It should help explain evaluation without introducing sockets, wiring or new behavior. Reconsider editable nodes only when there is an actual routing/reuse problem a stack cannot express.

## Project compatibility

Preserve existing v1–v5 projects and embedded snapshots. Version 5 stores optional rain/thunder and visible-flash ranges; projects without those settings continue to write version 4. Keep biome IDs, altitude and time conditions while their vanilla paths are investigated. **Do not implement the previous proposal to strip biome fields on import.** Unproven export is not grounds for data loss.

Version the next schema only when implemented semantics require it. Preserve names, order, enabled flags, strengths, curves and range feathers. A migration must not broaden a masked layer into an unconditional grade. If a feature ultimately cannot be reproduced, preserve the original file and present an explicit unresolved item with the affected settings; any user-selected conversion or removal must show its effect before saving. Invalid imports leave the current project intact.

Define a practical layer/pass limit from compiled shader size and measured frame cost on tested backends. Do not invent an unlimited stack or an arbitrary limit before measuring it.

## Implementation sequence and acceptance

1. **Vanilla evidence fixtures.** Run the generated probes in a modless 26.3 client on OpenGL and Vulkan. Record backend/GPU/log, camera Y and fractional movement, post versus terrain fog, raw depth, elapsed clock versus `/time` changes, world/HUD ordering and dimension/reload behavior. Extend fixtures for biome colors, daylight, weather and lightning; record ambiguous cases as well as successful ones.
2. **Exportable stack.** Compile unconditional and camera-Y layers; match CPU range/curve/blend math and test native shader contracts. Keep other projects recoverable while remaining input routes are investigated. Apply and Export use identical resources.
3. **Biome/time/weather/flash paths.** Prove each signal and scope, including core-stage implementations where necessary. For biome identification, test similar-looking biomes, underground/occluded views, transition blending and custom server biome data. A visual group is a separately named capability; the user explicitly accepts shared signatures with clear member lists and limitations. Existing exact-ID selectors require explicit reviewed conversion. For weather, test dry biomes, shelter, snow, day/night and dimensions; for flash, compare daytime brightness, lightning, End flashes and the accessibility toggle.
4. **Stack workspace.** Implement the persistent priority view, condition/weight explanations and sample controls alongside the corresponding exportable behavior. Retain history, draft recovery, project library and usable minimum-size layouts.
5. **Atmosphere.** Prove distance/height fog and effect order, then assess volumetric feasibility and animation with actual vanilla data and measured costs. Check sky, water, translucency, hand, view bob, portal/hurt transforms, resize and reload.
6. **Release evidence.** Install exported packs with the authoring mod absent. Verify both activation modes, disabling/restoration, pack priority, error recovery, CPU/GPU agreement and performance. Publish only the capabilities/backends actually tested; leave remaining requirements open.

Completion means the exported pack delivers the authored behavior in vanilla. A successful mod preview, shader compilation or a narrowed static-only demo is not completion of the conditional shader product.

## Export authoring correction (2026-11-02)

The user's request to prevent creating unexportable work supersedes the earlier unrestricted preview-condition authoring requirement. New biome/time/weather/flash conditions are unavailable until implemented. Preserve imported values read-only with explicit Clear + Save, allow project saving regardless of compiler capability, and offer Resolve export to inspect each affected layer and reason or explicitly disable that layer. No automatic stripping or unconditional conversion is permitted. The missing runtime paths remain open work; this correction does not establish their feasibility or completion.

## Accepted visual biome semantics

The user explicitly allows biome detection inferred from fog/sky and accepts that datapacks editing those inputs can change or break detection. The warning must appear during authoring, alongside the group selector. Exact biome identity is no longer required for explicitly selected visual groups. The current implementation uses **sky color only**, with a vanilla core sky alpha marker and full-screen reduction; it requires visible matching sky. A lack of signal disables the conditioned contribution. No client runtime, loader or mod may supply data. Native and Mesa raster tests do not establish clean-client compatibility.
