# Fog controls: vanilla research and interface proposal

Research date: **2026-11-03**. Target: **Minecraft Java 26.3**, resource pack only. This is a design proposal and source investigation, not an implemented fog feature or a graphics validation result. The Fabric editor remains an authoring application only.

**Implementation follow-up:** the user accepted the geometry/density path and requested the build. Fog colour editing is excluded in favour of native/datapack colour, animated noise is parked, and lighting/sunbeams are deliberately excluded. Terrain fog and custom Sky colour conditions are now implemented; see [the implementation/testing handoff](fog-sky-in-game-handoff.md) for current scope, evidence and artifacts. The broader proposal below is historical research.

## Recommendation

Add a dedicated **Fog** workspace beside grading/layers. Start by proving distance haze and height mist through world/core shaders, then investigate local volumes and animated density. Treat illumination and light shafts as separate capabilities. A volumetric density field does not inherently require a shadow map or a full-screen depth reconstruction; lighting that field correctly is a further problem.

Keep author-facing concepts stable while the renderer develops: colour, visibility distance, vertical thickness, placement and softness. Describe actual scene coverage in the workspace. Do not ship editable controls that preview through a private mod renderer and disappear on export.

## What is already in this checkout

- `Atmosphere.java` contains grade math, context masks and range helpers. Its name does not indicate an existing fog implementation.
- `Project.java` stores base grading and ordered grade layers, including v6 visual sky groups. There are no fog settings, density fields or fog migrations.
- `PackCompiler.files` generates post-effect grading resources and, when needed, a core sky override for visual-group signalling. It does not generate terrain fog.
- `Session.apply` uses the compiler's resources. Fog must extend that same path and project history/persistence.
- `EditorScreen` has Light, Levels, Balance and Curves controls; `LayerScreen` offers Stack, Graph and Overview. Fog should have its own entry rather than becoming another colour slider or another grade of the original image.
- `tools/vanilla-shader-probes.py` already generates raw-depth, post-fog and terrain-fog diagnostics. They establish useful starting points, not authored distance/height/volume behaviour. See [the existing spike](fog-rendering-spike.md) and [evidence](vanilla-feasibility.md).

## Interfaces available in vanilla

There are three distinct mechanisms. An editor-side Fabric fog hook would not establish an exported capability.

| Mechanism | Available controls/inputs | Relevance to this product |
| --- | --- | --- |
| Environment attributes | `visual/fog_color`, `fog_start_distance`, `fog_end_distance`, `sky_fog_end_distance`, `cloud_fog_end_distance`, `water_fog_color`, `water_fog_start_distance`, `water_fog_end_distance` (all in the `minecraft` namespace) | Official world-data interface. Biome/dimension/timeline changes belong to world/server datapacks; placing them under a resource pack's `data/` folder does not configure a remote world. Optional paired export only, never an implicit requirement. |
| Core shader resource overrides | World-stage Fog block; terrain camera-relative positions, camera position, matrices and lightmap samples | Best first resource-only route for authored distance and height fog. Pin to the exact game version and cover the actual shader variants and scene categories. |
| Post-effect resource definitions | Main colour/depth samplers, extra passes/targets and declared engine/custom uniform blocks | Useful for later full-screen fog and light-shaft investigations. Availability of depth alone does not establish correct world reconstruction, transparency handling or lighting. |

Mojang documents the [environment attribute system](https://www.minecraft.net/en-us/article/minecraft-snapshot-25w42a) and the [distance attributes introduced in 25w44a](https://www.minecraft.net/en-us/article/minecraft-snapshot-25w44a). These are historical introductions; the current names, defaults and consumers below were checked against the pinned 26.3 client.

The [26.3 release notes](https://www.minecraft.net/en-us/article/minecraft-java-edition-26-3) describe automatic `minecraft:end_of_frame` post effects and `/posteffect` activation. Core overrides run in vanilla but remain an unsupported internal interface. Their activation follows resource-pack loading; `/posteffect remove` cannot unload a core shader override. A combined fog/grade pack therefore needs honest activation semantics in Command mode, not just a command targeting its grading pass.

### Evidence inspected in this revision

Official cached client: `~/.gradle/caches/fabric-loom/26.3/minecraft-client.jar`, SHA-256 `4508d006323f24fa02876310c192d739af56516eb259000ac50f0909a68c9a2d`.

Extracted shader sources directly from the JAR. Inspected `EnvironmentAttributes`, `FogRenderer`, `FogData`, the fog environments, `PostPass` and `GameRenderer` using cached Vineflower 1.12.0. Temporary decompiler output is under `/tmp/shader-maker-fog-research/output`; it is not a project dependency.

| Pinned source | Finding | Design consequence |
| --- | --- | --- |
| `EnvironmentAttributes` | Defaults: air start/end 0/1024 blocks; sky end 512; clouds end 2048; water start/end -8/96. Colour and distance attributes are spatially interpolated and syncable. | Attribute defaults are not the final rendered ranges. Negative starts have meaning; do not reject them globally. |
| `AtmosphericFogEnvironment.setupFog` | Rain changes environmental start by up to -160 blocks and end by up to -256, with an end floor of `min(96, originalEnd)`. Its smoothed multiplier depends on local skylight and biome precipitation as well as rain. Boss fog also constrains ranges. | “Follow vanilla” inherits an already combined environment signal; it is not an independent weather slider or exact weather detector. |
| `FogRenderer.setupFog` | Render-distance fog has its own range: end = render-distance blocks; span = clamp(end/10, 4, 64). | Keep the view-distance boundary separate from authored environmental haze. Extending fog distance cannot render missing chunks. |
| `include/fog.glsl` | Environmental spherical fog and render-distance cylindrical fog are linear ramps combined with `max`. The cylindrical metric is `max(length(pos.xz), abs(pos.y))`. Fog changes RGB while preserving input alpha. | Match the actual metric and compositing; “cylindrical” here is not just horizontal distance. Never repurpose surface alpha as fog opacity. |
| `core/terrain.vsh` | `pos = Position + (ChunkPosition - CameraBlockPos) + CameraOffset`; world projection uses `ProjMat * ModelViewMat`. | There is a camera-relative world vector before projection. Carry a vec3 to the fragment stage for per-fragment distance/height and ray integration; interpolating vertex distances alone loses accuracy on large triangles. |
| `include/globals.glsl` and terrain position formula | Camera position is `vec3(CameraBlockPos) - CameraOffset`; terrain world Y is camera Y + `pos.y`. | Surface height, ray height and camera altitude can be controlled independently. Keep calculations relative to camera/chunk coordinates where possible for precision. |
| `core/entity.vsh`, `block.vsh`, `particle.vsh` | Separate shaders compute fog distances; block uses `Position + ModelOffset`, entity/particle use `Position`. | A terrain patch does not automatically cover chests, mobs, particles, items, sky or clouds. Establish each stage's coordinate convention before reusing height math. |
| `core/terrain.fsh`, `entity.fsh` | OIT accumulation premultiplies fog colour by surface alpha; alpha-only variants execute their own depth/transmittance operations. | Preserve OIT contracts. Fog colour work belongs in colour-producing variants; do not add fog opacity to OIT coverage. |
| `PostPass.addToFrame`, `GameRenderer.render3dHud` | Post uses an orthographic projection; HUD rendering resets Fog to NONE before post effects. Depth inputs can bind a target's depth texture. | Inverting post `ProjMat` does not recover world rays, and post `FogColor` is not a reliable world-fog colour source. |
| `Fog` block | Colour and six distances; no explicit air/water/lava/status-effect discriminator. | Separate “air only”, underwater overrides or preserving selected status fog cannot be promised from this block alone. Establish a discriminator or leave those features unresolved. |
| `include/light.glsl`, terrain lightmap sampling | Two directional lighting vectors; sampled surface lightmap values in terrain. | Neither supplies a general sun shadow map or the illumination at every sample inside a fog volume. Surface lighting is not volume lighting. |

## What we can reasonably pursue

“Source-backed” below means an input path exists. None of these new fog effects has been rendered by this revision.

| Feature | Resource-only candidate | Confidence / remaining proof |
| --- | --- | --- |
| Start/end distance haze, colour and falloff | Modify final world-stage fog calculations; preserve vanilla boundary fog | Source-backed on terrain. Prove native bindings, variants, colour math and wider coverage. |
| Different densities at different world heights | Integrate a height-dependent density along the camera-to-surface ray | Position inputs source-backed on terrain; integration is a proposed implementation. Test looking into, across and out of a layer. |
| Ground mist / vertical fog bands | Analytic slab intersections or exponential height-density integration | Mathematical route exists without a ray march. A world-Y plane will also fill enclosed spaces at that Y; it does not follow the ground or detect caves. |
| Local box/sphere fog volumes | Clip each surface ray to authored bounds and integrate only that segment | Plausible shader-only prototype. Must handle entering a volume, overlapping media, sky/background rays and large world coordinates. No scene voxel buffer is required for a simple density field. |
| Moving, patchy volumetric density | Bounded procedural/texture noise sampling along the ray | Research. Terrain overdraw can multiply sample cost; texture availability, temporal stability and cost require measurement. |
| Directional glow / lit fog | Prove light direction/colour inputs and integrate a scattering model | Research. An authored constant glow is possible in principle but must not be labelled sun-occluded scattering. |
| Sunbeams / shadowed volumetrics | Screen-space occlusion approximation or a separately proven vanilla lighting/transport path | Research. No general shadow-map path established here. Screen-space methods lose off-screen occluders and are a distinct effect. |
| Independently changing sky/cloud/underwater fog | Their own shader consumers, or explicit optional world-data integration | Native attributes exist. Resource-only controls require stage-specific proof; do not imply terrain fog changes all these systems. |

[GPU Gems 3, chapter 13](https://developer.nvidia.com/gpugems/gpugems3/part-ii-light-and-shadows/chapter-13-volumetric-light-scattering-post-process) provides a primary-source example of screen-space light shafts and their occlusion limitations. That technique is a research reference, not evidence that Minecraft provides the necessary light position or masks to our post pass.

### Volume integration proposal

For a surface vector `v`, let `L = length(v)`, `r = v/L` and `p(s) = camera + s*r`, for `0 <= s <= L`. Compute optical depth `tau = integral sigma(p(s)) ds`; transmittance is `T = exp(-tau)`. For constant effective fog colour `F`, the simple composite is `T * surfaceRGB + (1-T) * F`. This is a density/constant-source model, not a full light-transport solution.

For a uniform fog slab, clip the ray interval against its lower/upper world Y. Its optical depth is density times the length inside the slab. This correctly fogs a distant mountain seen through a low bank even when the mountain's surface is above the bank. Merely tinting fragments whose endpoint Y is low would fail this case. Handle horizontal rays, camera-inside cases, zero-length rays and soft boundaries explicitly.

Exponential height profiles can also be integrated analytically, with a stable horizontal-ray limit and bounds to avoid exponential overflow. Prefer these inexpensive models before adding sample-count controls. Noise-modulated density generally needs numerical sampling. A cap on integration distance must not silently leave long rays clear; specify how the remaining medium is handled.

Overlapping volumes describe participating media. Sum extinction and combine source contributions along the ray; list order should not change physical placement or create grade-like replacement. Different colours/densities need a specified integration model before stacking is enabled. Initially allow one authored medium until overlap math is implemented and checked.

## Proposed Fog workspace

Add a **Fog…** entry alongside **Layers…** in the current editor. It opens a dedicated workspace; Light/Levels/Balance/Curves remain grade controls. Reuse the existing selection, undo/redo, Apply and Export patterns. A future shared navigation bar can expose Grade / Layers / Fog without changing effect semantics.

Wide-screen sketch (example values only; capability text is shown below the preview):

```text
FOG                     Preview: Test scene | In game        Apply
------------------------------------------------------------------
Atmosphere              [ Final | No authored fog | Density ]
  Ground mist   on      [ distance markers + movable camera        ]
  Distant haze  off     [ side view showing camera and fog band    ]
  + Add fog

Selected: Ground mist   Look | Shape | Conditions | Quality
                        Colour       Follow world / Custom
                        Falloff      Natural
                        Visibility   64 blocks to 50% transmission
                        Clear near   8 blocks
                        Height       Lower Y 62 / Upper Y 78
                        Edge fade    4 blocks

Coverage: Terrain prototype; other scene categories unverified
------------------------------------------------------------------
Undo  Redo       Save project       Export ZIP       Disable
```

At small sizes, use List / Settings / Preview pages, retaining selection and scroll. Keep Apply/Export status visible. The sketch's multiple rows describe the eventual workspace; the first renderer can support one active fog object without pretending overlap is implemented.

### Add and inspect an effect

Offer templates **Distance haze**, **Ground mist**, **Height band**, then **Local volume** when proved. Templates populate the same fog model rather than selecting unrelated renderers. Each row has name, enabled state and a compact summary such as “Y 62–78 · 50% at 64 blocks”. Avoid ordering arrows when list order has no visual meaning.

| Inspector | Primary controls | Meaning and presentation |
| --- | --- | --- |
| Look | Follow world / Custom colour; Linear / Natural falloff | Follow world uses the world-stage resolved fog colour, including its environment influences. Custom colour has a picker, hex field and numeric channels. |
| Look, Linear | Start distance; Full-fog distance | Blocks from camera, with `end > start`. Plot the ramp. Start may be negative. This is a stylised distance ramp, not a volumetric scattering claim. |
| Look, Natural | Visibility distance; clear-near distance | Visibility means the path length through uniform fog required for 50% transmission: `density = ln(2)/distance`. For shaped fog it refers to distance **inside the medium**, excluding clear-near and empty space. Natural fog approaches full opacity; do not show a misleading “fully opaque at” endpoint. |
| Shape | Everywhere / Below height / Height band / Box / Sphere | Height is in world Y. Band exposes lower/upper Y and edge fade; box exposes centre/size; sphere exposes centre/radius. Reveal only fields relevant to the shape. Spatial integration initially uses Natural falloff; do not silently reinterpret a Linear endpoint ramp as a density field. |
| Shape, later | Noise amount, feature size in blocks, drift direction/speed | Separate patch size from density and animation speed. Require stable world anchoring; camera movement must not drag the fog texture around. |
| Conditions | Enable rules and feathering | Reuse proven condition vocabulary and evaluated-weight display. Camera-altitude activation is labelled separately from the medium's height/placement. No silent biome-selector conversion. |
| Quality, only for sampled modes | Measured quality presets; advanced sample limit | Hide for analytic fog. Add presets only after measuring cost and artifacts; no invented frame-rate promises. |

Use editable numeric fields beside sliders, clear units, reset-per-field and per-effect reset. Enforce finite values, positive visibility lengths, ordered bounds and valid colours. Slider ranges should make common distances easy while numeric entry permits useful larger values. Avoid three coupled controls for end distance, density and visibility: expose the pair appropriate to the chosen falloff and show derived values read-only.

“Depth” needs explicit labels: **distance from camera**, **vertical thickness**, **path length through a volume**, and **density** are different quantities. The UI should never call all four “fog depth”. Presets such as light haze or valley mist are parameter starting points, not separate capability claims.

### Preview and conditions

Keep a small distance/visibility graph and vertical cross-section beside the inspector. Provide a deterministic 3D test scene with known-distance pillars, a hillside, a valley and an enclosed room; allow the camera to move above/inside/below the bank. It should answer “what do these settings mean?” immediately.

A colour-only reference photograph lacks depth and position, so it cannot faithfully preview height fog. Label a synthetic test-scene preview as simulated. The in-game Apply preview must load exactly the resources exported. Diagnostics can show ray length, medium intersection, optical depth and affected categories; ordinary users see the current coverage and any export blocker.

Existing visual biome groups are detected by the post pass from visible-sky markers. A terrain core shader executes earlier and cannot simply read that later 1×1 reduction. Reusing the current condition widget does not establish that condition for core fog. Prove an earlier signal/transport or reject the enabled condition explicitly. Keep known members/limitations visible and preserve exact-ID data until explicit conversion. Time, rain and lightning conditions likewise need their own demonstrated inputs at the consuming stage.

### Compositing and scope

Initial policy: apply authored fog to the lit surface, then retain vanilla fog and its render-distance boundary, followed by the existing post grade stack. This adds authored haze; it does not promise to reduce existing environmental fog. At full vanilla fog the vanilla result wins. Removing/replacing environmental fog is a separate feature requiring an explicit policy for immersion, darkness/blindness and boss fog; the Fog block has no mode flag to make that distinction trivial.

The eventual frame relationship is `world shading → authored medium → native fog/boundary → transparency composition → post grades → later GUI`, subject to each native pipeline's actual ordering. OIT requires compositing in its expected representation, not mechanically pasting a straight-alpha formula after accumulation. The initial order intentionally lets grading affect fog colour. Do not promise a post-grade fog switch without a second proven path.

Publish a coverage matrix for terrain solid/cutout/translucent, entities, block entities, particles/precipitation, sky, clouds and hand. “Terrain only” must be a visibly selected scope or a prototype, never advertised as complete world fog. Preserve native alpha, depth/discard behaviour, chunk visibility fades and the current sky marker encoding. Do not globally replace `include/fog.glsl` without auditing its other consumers.

For initial Automatic export, core resources can remain active while the pack is enabled. In Command export, do not claim `/posteffect` switches fog: either prove a faithful activation mechanism or block that combination with an actionable issue. Removing/disabling the resource pack must restore native rendering.

## Implementation sequence and acceptance

1. **Distance/height fixtures.** Extend the diagnostic generator with per-fragment terrain distance, world Y, uniform Natural fog and a constant-density height band. Verify the vertex/fragment interface, JSON where applicable and all relevant MULTIDRAW/cutout/OIT/depth-range variants. Compare analytic band integration against CPU numerical integration, including horizontal and camera-inside cases.
2. **Clean-client proof and coverage.** Run the generated packs without mods on OpenGL and Vulkan. Measure known distances/heights; inspect large quads, negative/far coordinates, third person, FOV/bobbing/hurt transforms, water/glass, particles, hand, sky, clouds, boundaries, status effects and boss fog. Test reload, resize, dimension change, pack removal and conflicts. Record actual coverage and timing before selecting a production scope.
3. **End-to-end first controls.** Implement a dedicated Fog model and workspace only for the proved scope. Wire persistence, history, embedded pack snapshots, CPU/test-scene preview, compiler, Apply and Export. Default old projects to no authored fog; reserve the next schema version when code is implemented (current checkout supports v1–v6). Preserve all existing grade/condition meanings. Disabled future settings must survive saving; enabled unsupported settings must block output.
4. **Analytic shape expansion.** Prove height falloff and local box/sphere intervals, sky/background treatment and overlapping media. Expand the coverage matrix; a surface endpoint alone does not render a fog bank against empty sky.
5. **Sampled density and lighting research.** Measure noise sampling cost versus analytic modes, stable animation and aliasing. Investigate directional/ambient scattering inputs, then separately light-shaft occlusion. Do not assume post targets provide temporal history, motion vectors or shadow maps.

Graphics evidence remains required even after native shader compilation and headless synthetic raster tests pass. This revision inspected source and produced this proposal; it changes no runtime code, project schema, exported packs or renderer and ran no new graphics tests.
