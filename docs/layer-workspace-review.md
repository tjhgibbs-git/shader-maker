# Layer workspace comparison · 2026-11-02

This is a working UI comparison in the Fabric editor, not a separate browser mockup. Open **Layers…**, then choose **1 Stack**, **2 Graph**, or **3 Overview**. The choice lasts for the client session; switching views follows the same selected layer. Every view edits the same ordered project.

| Layout | What it emphasizes | Best comparison task |
|---|---|---|
| Stack | Large rows, conditions and evaluated preview weight; topmost blends last | Add a grade, hide it, move it up/down and inspect overlap |
| Graph | Original-image branches into grade nodes, feeding a running mix chain; execution flows down | Understand where each grade comes from and how later layers replace earlier contributions |
| Overview | Compact layer / conditions / evaluated-weight rows | Scan a larger composition and compare which rules are contributing |

The graph is automatically arranged. Grade and mix nodes select the layer; order controls change its place in the chain. There are no editable wires or free node placement in this comparison. Each grade still reads the original image, so presenting these grades as a simple serial filter chain would be incorrect.

All views include add, copy, rename, show/hide, reorder, delete, undo/redo, grade/condition editing and a right-side Minecraft reference image. Pagination handles compositions beyond the available space. Hover a row/node for the full name, complete conditions and preview/export status. On smaller screens the Conditions action is labeled **When…**. Ctrl/Cmd Z and Shift Z / Y support history outside text fields.

The reference controls compare Final / Original / Solo. Solo ignores the selected layer's conditions without mutating its saved state. Choose a local PNG/JPEG or the bundled game capture. **Inputs…** cycles inline camera Y, daylight tick, biome, rain, thunder and visible flash. These values affect layer weights on the reference image; they do not change the image's scene lighting or weather. The separate Sample Context screen is gone.

Conditions have Height, Time, Biome, Weather and Lightning tabs. Tabs share one draft; Cancel/Escape discards it and Save commits all tabs as one history edit. Blank fields mean unrestricted. Biome IDs accept comma-separated alternatives and default omitted namespaces to `minecraft:`; they are syntax checked, not validated against a world's registry. Daylight ranges wrap over 24000 ticks. Rain and thunder are independent global strengths from 0–1, not local precipitation; flash is visible-flash strength after accessibility suppression. Range feathers fade inward; for normalized weather/flash signals, domain endpoints 0 and 1 remain inclusive so full storms/flashes do not fade back out. Larger screens include Day/Night, biome, Clear/Rain/Thunder and During visible flash presets.

Weather/flash settings use project version 5. Older projects remain readable; projects without those settings continue writing version 4. Editing grades, names, visibility and order preserves all conditions. Disabled conditions survive exported source snapshots.

## Runtime boundary

The user explicitly retained **standalone vanilla exports**. Current output implements unconditional and camera-Y grading. Daylight, exact camera-biome IDs, global rain/thunder and visible lightning flash are editable and evaluated in the image preview, but **their vanilla runtime implementations remain unfinished**. Enabled conditions using those inputs block Apply/Export with the affected layer and missing input. No condition is silently dropped, no mod renderer substitutes for the export and no datapack dependency is added.

Source inspection: post Globals exposes camera Y and elapsed game time, not the default daylight clock or exact biome IDs. Sky core stages receive sun angle via transforms and rain brightness; the lightmap stage receives sky factor/color. Those are potential inputs, not an established exact condition system or cross-stage transport. A sky factor of one alone cannot distinguish bright daylight from a lightning flash. See [the feasibility evidence](vanilla-feasibility.md) and the new core lightmap/sky diagnostic fixtures.

## Validation

Use [the current in-game handoff](in-game-test-handoff.md) for the ordered test checklist, numeric weight cases and clean-vanilla probes. It also records an unverified Add/Copy pagination risk to investigate first.

Offline compilation and existing reference/curve/world/sample tests pass, with 276 project checks including combined condition evaluation, wrapped time, weather/flash feathering, biome alternatives/transitions, missing context, alpha, v5 persistence, malformed imports, history and explicit compiler rejection/preservation. Existing production shader contracts still pass 14 actual post-chain decodes and 14 native ShaderC/SPIR-V checks.

A normal Gradle build could not acquire a lock in the read-only home cache. The documented cached-dependency offline builder produced the production JAR. The dev client launch reached Minecraft but failed before creating a window: `Unable to initialize SDL: No available video device`. **The new layouts have not been visually inspected in game in this session.** Check all three views at 340×220, 480×270, 640×360 and larger GUI dimensions; inspect tooltip overflow, pagination with many layers, and the graph's branch/mix wiring. Confirm grade editing, renaming followed by Hide all, view switching, resize, disabled-condition preservation and save/load in the actual client.

## References

Photoshop uses a layer panel with order and visibility controls: [Adobe's layer-panel documentation](https://helpx.adobe.com/photoshop/desktop/create-manage-layers/get-started-layers/work-with-the-layers-panel.html). Blender compositing pairs nodes with a viewer: [Blender's Viewer node documentation](https://developer.blender.org/docs/features/nodes/viewer_node/). The Overview layout is a comparison design for this project, intended to make the full composition easier to scan.

## In-game pass (2026-11-02, dev client, Linux)

- **Reproduced Priority 0 crash:** at 427x240 GUI units (1280x720, scale 3) in Stack view, **+ Add** on a project with no extra layers threw `IndexOutOfBoundsException` in `LayerScreen.extractRenderState` (crash report `crash-2026-11-02_21.37.32-client.txt`). `add()`/Copy computed the page from the pre-insert layer count (display position -1) and `init()` had no lower clamp.
- **Fixed:** `displayPosition(index,size)` now uses the new list size for Add/Copy, and the page is clamped to `>= 0`. Re-tested Add, Copy, Add in Stack, Graph and Overview at 427x240: no crash, selection stays on its page. Evidence: `shots/layer-review/small-{1,2,3}.png`; 640x360 Stack: `layers-stack.png`.
- Still untested from the handoff: the other priorities (condition math, persistence, export gating, other sizes).

## Export authoring correction (2026-11-02)

The user's request to prevent creating unexportable work supersedes the earlier unrestricted preview-condition authoring requirement. New biome/time/weather/flash conditions are unavailable until implemented. Preserve imported values read-only with explicit Clear + Save, allow project saving regardless of compiler capability, and offer Resolve export to inspect each affected layer and reason or explicitly disable that layer. No automatic stripping or unconditional conversion is permitted. The missing runtime paths remain open work; this correction does not establish their feasibility or completion.
