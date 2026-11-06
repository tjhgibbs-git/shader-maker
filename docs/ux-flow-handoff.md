# UX flow rework, fog presets and in-game checks (2026-11-03)

Minecraft 26.3, Fabric dev client via block-scraper, **started with `--idle-tick`**, 1920×1080 at GUI scale 2 (960×540, the author's setup), Mesa llvmpipe OpenGL, *Mods Demo World* copied from Prism. Editor mod present: this is **not** clean-client validation.

Build: `download/shader-maker-ux-26.3.jar` (SHA-256 in `download/ux-26.3.sha256`). The Prism instance was **not** updated.

## Harness finding

The earlier "reload stall" (threads parked in `postRunTasks`) and "world load stuck at 16%" are the Fabric client-gametest lockstep: without `--idle-tick` the game only advances during harness commands. With `--idle-tick`, Apply/Disable and world joins complete normally. After a resource reload llvmpipe rebuilds every chunk; at render distance 12 wait ~90 s before judging a frame (half-faded chunks look like heavy fog). The gametest framework forces render distance 5 at launch; set 12 in Video Settings after joining.

## In-game results

| Check | Result |
| --- | --- |
| Authored fog visible in world (Apply → look → Disable) | **PASS** — first observed in-world fog render; Disable restores vanilla. |
| Render context, Blocks only scope + desaturate | **FAIL → fixed.** Horizontal streaks of ungraded terrain: the marker required `fragColor.a == 1.0`, but filtered/mipmapped opaque texels land just below 1. Now `>= 254.5/255`. After the fix terrain is cleanly graded; sky, stained glass, hand unaffected. The synthetic raster suite did not catch this (its textures are exactly opaque). |
| Render context, Full-screen, fog colour #C0D8FF hue ±0.15 | PASS (matched at noon, whole screen graded) |
| Same with #FF4000 | PASS (inactive) |
| Live world value (Use current world value) | PASS — read #B5D1FF fog colour at the cathedral at noon |
| New Layers home, rename (double-click/✎ + Enter), delete + Ctrl+Z, Grade → Enter returns, Esc returns | PASS |
| Layouts at 960×540, 640×360, 427×240 GUI | PASS after fixes (header/footer overlap, fog field paging) |

## Fog presets (tuned in the demo world, render distance 12)

Shots in `shots/ux/4x–5x`. Heights are relative to the first solid block below the author.

| Preset | Settings | Review |
| --- | --- | --- |
| Soft haze | Everywhere, 60%, clear 24, 50% over 160 | Gentle aerial perspective; far towers hazier than facade. |
| Thick fog | Everywhere, 100%, clear 2, 50% over 24 | Moody silhouettes, still readable at ~60 blocks. |
| Ground mist | Below ground+4, fade 3, 90%, 50% over 18 | At eye level building bases sink into mist; subtle from above. |
| Low cloud | Band ground+16…+30, fade 5, 50% over 12 | Clean cloud deck swallowing towers above ~Y 80. |
| Interior haze | Sphere r24 at the author, fade 10, 80%, 50% over 40 | Inside the nave the back and vault fade; near pews stay readable. Entities/lanterns stay unfogged (terrain-only). |
| Misty morning | Soft haze (45%, 192) + mist (ground+5) | Two editable objects; combination of the above. |

## What changed in the flow

- N / pause button opens **Layers home** (was the base-grade editor). Per-row actions; fog listed on the same page with preset Add; project/history in header; game/export in footer. Stack/Graph/Overview switch removed (rows show conditions and preview weight).
- Grade editor: `< Layers` back, Done/Enter/Esc all return home; Save/Load/Export/Path/Layers/Fog buttons removed from it. Larger 16:9 preview.
- Fog workspace: list + presets on the left; live-applied fields (no Keep settings / Revert); Use my position; Apply/Disable/Done footer.
- Conditions: legacy Time/Weather/Flash tabs only appear when a layer still has such data; More context → Render context; Save conditions → Done (Enter).
- Render context: aligned labels, Use current world value, active-signal list, Done keeps typed-but-unadded values instead of dropping them.
- Undo keeps the selected layer by name.

## Still open

- Clean vanilla OpenGL/Vulkan acceptance of every export (unchanged).
- Cylinder fog shape (requested idea; not built). Animated pulses on sub-day cycles via `GameTime` (period must divide 24000 ticks) remain a roadmap item.
- Minecraft keeps translucent/entities on native fog; bats and lanterns look unfogged inside haze.
