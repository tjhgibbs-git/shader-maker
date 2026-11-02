# Advanced grading: in-game validation handoff

The layer editor and specimen are experimental. Enabled layers are intentionally limited to the synthetic specimen: game preview and static pack export reject them. Fog and live world-aware grading are not implemented. See `world-context-spike.md` and `fog-rendering-spike.md` for rendering investigation and prerequisites.

Run the normal Gradle build and test in an actual Minecraft 26.3 client on a graphics-capable system. The offline verifier compiles client code and checks model/export behavior, but does not exercise screen layout, mouse/keyboard interaction or game rendering.

## Layer editing

- At 340×220 GUI units and larger sizes, check the editor, layer list and all condition pages for clipping, overlap, readable labels and reachable buttons. Check keyboard navigation, screen scaling and window resizing.
- Add, name, duplicate, select, reorder, disable and remove layers; check the displayed order, names, conditions, selected grade and persistence after save/load and restart. Undo/redo returns editing to the base grade so selection cannot silently move to another layer.
- Edit sliders, presets and curves on both base and named layers. Verify only the selected grade changes and other layer settings are preserved. Verify an invalid name or condition leaves the project intact.
- Enter changes on multiple condition tabs, switch tabs, then apply or use Done. Verify invalid input shows the appropriate page, preserves the drafts and does not partially change the project. Try empty ranges, wrapped time, feather edges and day/night presets.
- Test the All, Original and Solo sample modes. Solo shows the selected grade without its layer conditions; All combines the ordered layers with the simulated context. Change context from the main editor and verify mismatch/weight feedback where space permits. Confirm the synthetic sample is not mistaken for an in-world preview.
- Confirm enabled layers are clearly identified as sample-only and that Apply and Export report their limitation without deselecting an already active preview pack. Disabled layers must remain in saved project snapshots.
- Verify invalid draft files remain untouched on later edits and that the separate recovery draft restores correctly. Verify history, keyboard shortcuts and draft recovery across structural changes.

## Known follow-up

Project names are normalized to file names and saving replaces an existing file with the same normalized name; different entered names can collide. Consider a collision-aware save flow. Name editing in the layer list has an explicit Save action; evaluate whether that is discoverable and whether unsaved changes need a prompt. Inspect sample context labels and layer feedback at the minimum size and adapt the layout if needed.

Graphics-device work remains separate: verify world render hooks and writable per-frame parameters before enabling live layers, and verify scene depth, projection, sky and transparency before implementing distance or volumetric fog.

Planned work (real sample picture, clock/sync, live renderer, animated pulses) is tracked under "Next actions" in [v0.1-handoff.md](v0.1-handoff.md).

## Added sample-reference checks

The editor now defaults to a bundled neutral game capture (the existing flat-world test screenshot, HUD included), with **Context → Sample image…** for the bundled/synthetic/custom PNG/JPEG reference. Check source labels, All/Original/Solo and the greyscale strip, failure preserving the previous image, and long/invalid local paths. References are session-only. **Use world** copies a snapshot into context drafts, leaving them unchanged if biome/time is missing. This does not establish clock synchronization or live grading. The new screen, buttons and client diagnostic commands compile offline but have not been exercised in game in this restricted session.
