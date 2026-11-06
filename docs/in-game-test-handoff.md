# In-game handoff: layer workspace and conditional authoring

**2026-11-03 update:** start with [Fog and Sky colour testing](fog-sky-in-game-handoff.md) for the newest authoring JAR, standalone fixtures and added tests. “Biome detection” is now an explicit Sky colour context with custom datapack signals; terrain density fog is implemented. The older layer/UI checks below still apply.

Updated 2026-11-02. Start here for checks of the current build. This replaces the earlier handoff's stale statements that all layers block export and node views are deferred.

The checkout is the nested `shader-maker` repository, currently on `main` at `eef9ca1` **with substantial uncommitted changes**. The layout comparison, condition changes, diagnostic fixtures and updated JAR are in the working tree, not that commit. Inspect `git status` before changing anything; preserve existing work. User direction: build three switchable layouts, remove Sample Context, add time/biome/weather/lightning, and **keep standalone vanilla exports**. The authoring changes exist; the additional vanilla runtime implementations remain unfinished.

## What exists and what has been verified

| Area | Current behavior | In-game status |
|---|---|---|
| Layers workspace | Stack, Graph and Overview edit the same project; right-side Minecraft PNG/JPEG preview | New layouts have not been visually inspected |
| Layer operations | Add, copy, rename, show/hide, reorder, delete, undo/redo; Grade and Conditions inspector actions | New workspace needs a full interaction pass |
| Sample Context | Separate screen removed; Image chooser and inline preview inputs replace its entry points | Check navigation and discoverability |
| Conditions | Height, circular daylight, exact biome alternatives, global rain/thunder and visible-flash ranges; strengths and feathers | CPU/model tests pass; UI needs checking |
| Persistence | Existing v1–v4 imports; v5 for weather/flash; drafts, history and embedded export snapshots | New condition persistence needs restart/UI checks |
| Vanilla output | Base grade, unconditional layers and camera-height layers compile | Clean-client graphics validation remains pending |
| Time/biome/weather/flash output | Enabled conditions explicitly block Apply/Export; hidden layers retain their settings | Verify rejection and recovery; these features do not yet run in vanilla |

Already passed: cached-dependency offline compilation, reference/curve/world/sample tests, **276 project checks**, **14 production post-chain decodes and 14 native ShaderC/SPIR-V checks**. Eight diagnostic packs additionally passed **4 post-chain decodes and 20 native fragment checks**. These establish model/compiler contracts, not rendered output.

The last dev-client launch reached Minecraft but failed before window creation: `Unable to initialize SDL: No available video device`. The normal Gradle wrapper also could not acquire a lock in the read-only home cache. Neither failure establishes a product bug. No screenshots or graphics acceptance were obtained for the new UI.

## Prepare and capture evidence

Use Java **25**, Minecraft Java **26.3**, Fabric Loader **0.20.1** and Fabric API **0.165.0+26.3** for the editor. The current artifact is [download/shader-maker-0.1.0.jar](../download/shader-maker-0.1.0.jar); the corresponding build artifact is `build/libs/shader-maker-0.1.0.jar`. Record the JAR checksum, Git revision/dirty state, OS, GPU/driver, backend, window dimensions, **actual GUI dimensions/scale**, project and pack order.

Run from the nested repository:

```sh
export JAVA_HOME=/home/tjhgi/.local/jdk/jdk-25.0.5+8
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew build
# Only if the existing cache is populated and Gradle cannot run:
# python3 tools/verify-offline.py

../block-scraper/block-scraper status
# If no client is running:
../block-scraper/block-scraper start --project-dir . --task runBlockScraper
../block-scraper/block-scraper open shader-maker
../block-scraper/block-scraper widgets
../block-scraper/block-scraper shot shots/layer-review/editor.png
```

Use the actual local Java 25 path if it differs. If rebuilding, update the download JAR only after successful checks. Back up any useful `config/shader-maker` data before recovery/destructive test cases. Use a disposable project and test world.

Read [block-scraper's GUI workflow](../../block-scraper/AGENTS.md). Prefer `click --text`, inspect `widgets` for field coordinates, and use `key ctrl+a` / `type` for field edits. Screenshot paths are pixels; clicks use GUI coordinates. **Open every captured PNG and inspect it.** A successful command/reload does not establish a correct screen. Structural Java changes require a client restart. The existing `tools/block-scraper-smoke.bs` covers older editor flows; it does not cover these new layouts.

Save evidence under `shots/layer-review/` and append observations to [layer-workspace-review.md](layer-workspace-review.md). Capture both before/after states for fixes, and relevant game logs for crashes or shader failures.

## Priority 0: investigate a source-review risk before broad testing

**Unverified finding; no in-game reproduction yet:** in `LayerScreen.add()` and the Copy action, `displayPosition(...)` reads the current project's layer count before `replace(ls)` installs the larger list. Stack/Overview order depends on that count. Adding the first layer or copying the current top layer can therefore calculate display position `-1`; when only one row fits, integer division preserves page `-1`. `init()` only clamps the page's upper bound. Building rows may then access a nonexistent layer.

Reproduce with a disposable project at **340×220 GUI units**, or another size where Stack fits one row:

1. With no extra layers, open Layers and choose **1 Stack**, then **+ Add**.
2. If it survives, select the top layer and **Copy**. Repeat with enough layers to cross a page boundary.
3. Repeat in Overview at a size/count that exposes its page boundaries; Graph uses the opposite display mapping and is a useful comparison.
4. Record whether the action crashes, selects the wrong page, or hides the new selection. Capture the log and fix the mapping/count/page clamp if confirmed, then repeat Add/Copy in all three views and rebuild the JAR.

Do not describe this as a reproduced failure until tested. It is a reason to check these actions first. Do not rely solely on a lower-bound clamp if it still places a newly added/copied selection on the wrong page.

## Priority 1: compare the three layouts using the same project

Start with a neutral base and make these two unconditional layers:

- **Bright:** Exposure +1, strength 1, enabled, no conditions.
- **Dark:** Exposure -1, strength 1, enabled, no conditions.

Use **Grade…** to return to the existing editor and edit the selected layer. Return through **Layers…**; avoid `open shader-maker` between steps because constructing a fresh editor selects the base grade. Rename using the bottom name field, then select another item or invoke an action to commit the name.

| Check | Expected result |
|---|---|
| Switch Stack → Graph → Overview repeatedly | Same project, selected layer, order, visibility and preview result; selection's page follows it |
| Stack and Overview order | Final/topmost blend first in the displayed list; base last |
| Graph order | Base first, then grades/mixes in execution order; original-image branch separate from the running mix chain |
| Reorder Bright/Dark | At full strength, the later layer replaces the earlier grade; Dark last is dark, Bright last is bright |
| Graph **Later / Earlier** | Same order changes as Stack/Overview **Up / Down**, with the opposite visual flow direction |
| Select a grade or mix node | Both select the same layer and its inspector; nodes do not create a different project |
| Click **On / Off**, then **Hide all** | Image and evaluated weight update; project/settings remain present; undo restores visibility |
| Copy | Independent grade/conditions, unique name, selected copy; editing it leaves the source unchanged |
| Delete, undo, redo | Correct layer/order/selection recovery; base stays present and cannot be deleted |
| Rename then immediately **Hide all**, change views, or resize | Valid rename survives; visibility/order actions do not restore an old name |
| Blank/duplicate/long names | Invalid names explain the problem; valid long names remain accessible through tooltips |
| **Final → Original → Solo** | Final shows composition, Original shows source, Solo shows selected grade ignoring conditions/enable state; export flags stay unchanged |
| Add 12+ layers | Every layer/base reachable through pagination; first/last-page buttons correct; selection/copy/delete/reorder across boundaries safe |
| Shortcuts outside text fields | Ctrl/Cmd Z, Shift Z and Y operate history; text fields retain native editing |

The graph is **automatically arranged**, with selectable grade/mix nodes. Free node dragging, editable wires, pan/zoom and arbitrary routing are not implemented; do not file their absence as regressions. Record whether this graph is useful enough to compare with the other options. No favorite layout has been chosen by the user.

For each view, capture the same populated project with the same selected layer and inputs. Assess how easily a person can understand order/overlap, find an inactive layer, edit a grade and scan a larger composition. Note how much pagination and tooltip reading is required.

## Priority 2: image preview and removal of Sample Context

1. Check the main editor's **Image…** and Layers' **Choose PNG / JPEG…**. Both should open the image chooser. No **Context** or **Sample context** button/screen should remain in normal navigation.
2. Try the bundled Minecraft capture, synthetic specimen and a local PNG/JPEG. Bad paths/unsupported images must leave the previous reference intact. Return to the same workspace/selection.
3. In Layers, **Inputs…** cycles `Y → Time → Biome → Rain → Thunder → Flash → image-only`. Enter values, cycle away/back and switch layouts. Values should remain available across layout changes.
4. Confirm changing an input re-evaluates the image and contribution display without requiring Apply. These inputs simulate layer weights; changing rain/time does **not** turn the screenshot itself rainy or nighttime. Actual world movement/time/weather does not automatically update these simulated inputs.
5. Try invalid/partial values: blank numeric field, text, NaN, infinity, time -1/24000, weather/flash -0.1/1.1, malformed biome ID. Show an error and keep the last valid preview value; do not crash or change project conditions.
6. Compare PNG aspect ratio, clipping, calibration strip, preview space while editing inputs and whether the active input values are understandable. Reference images are independently selected and must not be embedded in exported packs.

## Priority 3: conditions, weights, drafts and presets

Use a single visibly different enabled layer with strength **1**; hide all other layers for individual tests. Set the indicated condition, leave the others blank, then change inputs beside the image. **Conditions…** is labeled **When…** on narrow screens. Weight appears in Overview or the row/node tooltip; Stack also shows it when space permits.

| Condition | Settings and input | Expected preview weight |
|---|---|---|
| Height | Min 0, max 100, feather 10; Y -1 / 0 / 5 / 50 / 95 / 100 / 101 | 0 / 0 / 50 / 100 / 50 / 0 / 0 percent |
| Wrapped daylight | Start 18000, end 6000, feather 1000; ticks 18000 / 18500 / 0 / 5500 / 6000 / 12000 | 0 / 50 / 100 / 50 / 0 / 0 percent |
| Biome alternatives | `plains, minecraft:forest`; preview plains / forest / desert | 100 / 100 / 0 percent; omitted namespaces normalize to `minecraft:` |
| Rain | Rain min 0.2, max 1, feather 0.2; thunder unrestricted; rain 0 / 0.2 / 0.3 / 0.4 / 1 | 0 / 0 / 50 / 100 / 100 percent |
| Thunder | Thunder min 0.2, max 1, feather 0.2; rain unrestricted; thunder 0 / 0.3 / 1 | 0 / 50 / 100 percent |
| Visible flash | Min 0.2, max 1, feather 0.2; flash 0 / 0.2 / 0.3 / 0.4 / 1 | 0 / 0 / 50 / 100 / 100 percent |

Height/daylight feathers fade inward at both bounds. For weather/flash, domain endpoints **0 and 1 remain inclusive**; a feather must not suppress a fully developed storm/flash merely because its value is 1. An unrestricted weather/flash range 0–1 contributes fully even with a nonzero feather. Global rain and thunder differ from local precipitation; flash is defined as visible strength after accessibility suppression. The current preview inputs are manual simulations, not a measured flash detector.

Combined test: strength **0.8**, height as above at Y **5**, wrapped daylight at tick **0**, matching biome **forest**, rain **0.3** in the above rain range, unrestricted thunder and flash **0.3** in the above flash range. Expect **10%** contribution (`0.8 × 0.5 × 1 × 1 × 0.5 × 1 × 0.5`). A failed condition should zero the contribution. Biome alternatives use OR; different condition types use AND.

Also check:

- Edit several tabs, switch tabs and resize before saving. Drafts persist; **Save conditions** commits them together. One undo restores the previous conditions. Cancel/Escape discards all condition-draft edits.
- **Clear this condition** clears only the current tab's condition. On Weather it clears both rain and thunder; it does not delete height/time/biome/flash. Cancel after clearing must retain the original settings.
- At GUI height **280+**, try Day/Night, Plains/Desert, Clear/Rain/Thunder and During visible flash presets. Smaller layouts omit these shortcuts while keeping fields accessible. Presets must not change unrelated tabs.
- Reject strength outside 0–1, min > max, negative feathers, daylight outside 0–23999 or feather >12000, weather/flash outside 0–1, missing range bounds and empty entries in comma-separated biome lists. Invalid Save stays on screen with the original project untouched.
- Syntactically valid biome IDs are not checked against a live registry. A nonexistent ID accepting syntactically is a current limitation, not proof of working biome selection in vanilla.

## Priority 4: persistence, recovery and export gating

1. Save a project with every condition type, multiple layers, a hidden layer, nondefault curves/strengths and a distinctive order. Load another project, then reload this one. Compare every field and the preview across views.
2. Copy/rename/reorder/toggle and edit a grade or curve. Ensure rain/thunder/flash and all older conditions survive. Exercise history and client restart/draft recovery.
3. Inspect the JSON: weather/flash opts into **version 5**. Projects without those settings continue writing **version 4**. Import existing v1–v4 samples; existing conditional data must remain intact. An invalid file must leave the active project unchanged.
4. An **enabled** time, biome, weather or flash condition must block both Apply and Export, naming the affected layer/missing capability. Test each independently and combined. A zero current preview weight does not make the condition exportable.
5. Hide all unresolved layers. Apply/Export should become available if the remaining layers are unconditional/camera-height only. The disabled layers and complete conditions must remain in the exported `shader-maker-project.json` snapshot. Reload the project and re-enable them: the original rules should return.
6. Clearing a condition must be explicit; Cancel must not broaden the layer. Never silently strip an unsupported condition to make an export succeed.

## Priority 5: window sizes and device regressions

Check all three layouts and all five condition tabs near **340×220**, **480×270**, **640×360** and larger **GUI-unit** dimensions; also go below the minimum to check the fallback. Use `status` to record actual dimensions because Minecraft can clamp GUI scale. Useful window/scale starting points are 1280×720 at scale 2, 1920×1080 at scale 4, and 854×480 at scale 2; these are not all the same GUI size.

Look for clipped tab/action labels, buttons overlapping graph wires or each other, preview/field overlap, hidden errors/save controls, tooltip accessibility, selected-name retention on resize and pages becoming invalid after layer deletion or view changes. Inspect the small **When…** action and preset appearance at the 280-unit threshold. Record responsiveness when cycling Final/Original/Solo or changing an input with 12+ layers. Preview grading is cached, so a static image should not constantly re-evaluate the entire stack.

Keep earlier regressions in scope: Windows apply/re-apply file locking; save/load recovery; curves; pack selection persistence; preservation of unrelated selected packs; Disable restoring the game; world/dimension/reload transitions. Record actual backends/platforms tested rather than assuming Linux or ShaderC results cover Windows/Vulkan.

## Priority 6: standalone vanilla graphics acceptance

Use a **separate clean Minecraft 26.3 instance without Fabric or any mods**. Record instance directory, GPU/driver, OpenGL/Vulkan backend and resource-pack order. Use resource format **97.1** and a disposable creative world with commands. Disable other shader packs and the authoring preview before comparing exported output.

First export only unconditional/camera-height layers. Test both Automatic and Command packs. Command activation uses `/posteffect add @s shader_maker:grade`, removal `/posteffect remove @s shader_maker:grade` with permission. Verify blend order, strengths, curves, camera-height bounds/feathers, negative/fractional heights, first/third person changes, hand/HUD coverage, dimension changes, resize, reload, pack priority and restoration after disable. Camera height is not terrain surface height. Repeat on OpenGL and Vulkan before claiming both work.

Generate the **eight** diagnostics from a verified official 26.3 client JAR:

```sh
python3 tools/vanilla-shader-probes.py \
  ~/.gradle/caches/fabric-loom/26.3/minecraft-client.jar \
  /tmp/shader-maker-vanilla-probes
```

Enable **one probe at a time**, confirm restoration after disabling it, and save durable evidence outside `/tmp`:

| Probe | Check |
|---|---|
| camera-y | Brightness = clamp((cameraY + 64) / 384, 0, 1); test known/negative/fractional Y and camera modes |
| elapsed-time | Compare ticking with `/time` changes and world-clock pause; expected to follow elapsed ticks, not daylight |
| post-fog | Source predicts empty/NONE fog here; black may be a valid reading |
| terrain-fog | Compare biomes, borders, caves, dimensions, weather and immersion; fog signatures are not exact biome IDs |
| raw-depth | Near/far/sky/hand/water/translucency, view bob, reload and depth conventions; output is not linear distance |
| lightmap-sky-factor | Compare noon/night, rain/thunder, lightning, Hide Lightning Flashes and End flashes; factor 1 alone is ambiguous |
| lightmap-sky-color | Compare biome/time/weather/dimension signatures; record collisions and interpolation |
| sky-color | Inspect sky-stage color without its final fog operation; test occlusion/view direction and compare with terrain/lightmap |

Source-backed candidate paths and harness commands are in [vanilla-feasibility.md](vanilla-feasibility.md). Daylight needs the real sky clock, not Globals GameTime. Exact biome selection needs to distinguish similar-looking biomes and custom server data, including caves/absent tinted terrain. Weather needs to distinguish global strengths from local rain/snow/shelter. Flash needs to distinguish lightning from bright noon/End flashes and respect Hide Lightning Flashes. Any cross-stage signal transport must prove its actual target/channel, coverage, stability and appearance preservation.

**Do not mark time/biome/weather/lightning runtime features complete because their image previews or diagnostic shader compilation work.** Do not add a mod-fed uniform or silently require a datapack. Optional event/Blood Moon integration remains a separate future task described in [the spec](vanilla-only-handoff.md), not a prerequisite for comparing these layouts.

## Results to leave for the next session

For each case record: ID, environment/backend, project, exact actions/values, expected result, observed result, pass/fail/blocked, screenshot/log paths and any fix/retest. Keep unverified source-review findings separate from reproduced failures. Mark unavailable backends or devices as untested.

Suggested case groups: `L` layouts/operations, `P` image/preview inputs, `C` condition math/drafts, `S` persistence/history, `E` export/recovery, `V` clean-vanilla graphics. Prioritize crashes/data loss, incorrect evaluation/output, inaccessible controls, then visual polish. Update [layer-workspace-review.md](layer-workspace-review.md) with the layout comparison and [vanilla-feasibility.md](vanilla-feasibility.md) with device/input evidence. Rebuild/repackage after fixes and record the new artifact checksum.

## Experimental visual biome groups: next clean-client check

No client dependency is permitted. Install only the exported resource pack in vanilla 26.3. The editor's Biome tab now has a visual sky-group selector with the complete shared stock-biome list, explicit legacy-ID conversion, and a warning that datapacks changing sky colors can change/break detection. The model opts into v6; old projects remain readable.

Use `download/visual-biome-demo-always.zip`: it brightens the whole scene only when the shared plains/beach/swamp/etc sky signature is detected. At clear noon, face a broad open sky and compare a known group member against forest/desert. Look down or enter a covered space: losing enough sky samples should deactivate the layer. Do not mistake that documented limitation for exact camera-biome selection. A datapack changing plains sky to the forest signature should cause forest-group behavior, not follow the plains registry ID. Fog-only changes should not directly alter this pre-fog sky classifier.

Check all selector details at 340×220 and larger GUI sizes; shared names must be readable via details pages and tooltip. Save/load, edit grade, duplicate, reorder, toggle, undo/redo and export must preserve `visualBiomeGroup`. Legacy exact IDs must remain until explicitly selecting a group and saving conditions. Check the warning in selector, Apply/Export tooltip and exported README.

Run Auto and Command modes separately. Verify no visible RGB change before Command activation, restoration after command removal/pack removal, resource priority and interactions with other sky/alpha packs. Test dawn/dusk/night, rain/thunder/lightning, accessibility settings, biome borders, custom sky data, End/Nether, immersion, clouds/translucency/OIT, hand/screen overlays and resize. Record backend/GPU/log and actual results; only shader and synthetic Mesa raster tests have passed in this revision.
