# Passing world information to a vanilla 26.3 shader: channels and limits

2026-11-05. Target: Minecraft Java 26.3 (resource pack 97.1, data pack 121.0), **unmodified client**. Engine
claims below come from the pinned client (decompiled with Vineflower 1.12.0). "Seen in game" means observed in
the Fabric dev client (block-scraper, 1920×1080), which uses the vanilla renderer. It is **not** a clean-client
or Vulkan check, and those are still outstanding.

## The short version

| Channel | What it can tell the shader | Exact? | Flicker / coverage | Needs | Status |
|---|---|---|---|---|---|
| **Camera position** (`Globals.CameraBlockPos/CameraOffset`) | Camera X, Y, Z every frame | Exact | None | Nothing | Height: existing. **X/Z area box: new**, seen in game |
| **`GameTime`** (`Globals`) | `(gameTime % 24000 + partial) / 24000` | Exact | None | Nothing | Elapsed ticks, **not** time of day (`/time set` and sleeping change day time, not game time). Used for fades |
| **`/posteffect` stack** (server → client) | Anything a command can test: exact biome, structure, dimension, weather, health, scores, items, regions, game events | Exact (server side) | None; changes at most once per tick | Datapack function or command block with op level 2; resource pack holds the effects | **New**: command-switched layers, fade in/out, helper datapack, demo. Seen in game |
| **Sky colour** (core `sky.fsh` → framebuffer alpha → post) | Hue of visible sky: a shared visual group, or a datapack-set unique colour | Group, or exact with a datapack | Needs sky on screen to *change*; **now held** while hidden | Optional datapack (`visual/sky_color`) | Existing; **hold + 1 s cross-fade new**, seen in game |
| **Render signals** (core `lightmap.fsh`/`terrain.fsh` → framebuffer alpha → post) | Fog colour/distances, raw lighting colours (ambient, block-light tint, skylight, night-vision colour), grass/leaf vertex tint | Exact when a datapack writes a unique value | Needs solid terrain on screen to *change*; **now held** | Optional datapack (biome `attributes`) | Existing; **false positives fixed, hold new**, datapack biome ID seen in game |
| Post-pass `Fog` block | Nothing: the post stage gets `FogMode.NONE` (zero colour, max distances) | No | — | — | Dead end |
| Lightmap or any dynamic texture as a post input | Nothing: post `TextureInput` always resolves to `textures/effect/<path>.png` (a static file) | No | — | — | Dead end |
| Custom post uniforms | Constants only. JSON `uniforms` are written once when the chain loads, with no per-frame setter | No | — | — | Used for per-effect constants (fade time, direction) |

## 1. `/posteffect` (new in 26.3): the exact, server-driven channel

How it works (`PostEffectCommand`, `ServerPlayer`, `ClientCommonPacketListenerImpl`, `GameRenderer`):

- `/posteffect add|remove|clear|list <players> [id]` needs permission level 2. Datapack functions have it by default. Command blocks have it.
- The server keeps a **per-player list**, saved in player data (`post_effects`), so it survives relogs. `add` is idempotent: adding an effect that is already present fails harmlessly. The list is sent to the client at most once per tick, and only when it changed. Spamming `add` from a tick function costs no network traffic.
- Each frame the client runs `minecraft:end_of_frame` **first**, then the player's effects in list order, then the spectator effect. So command layers grade *on top of* the always-on look and stack in the order they were added.
- An id that is missing or fails to compile is logged once and skipped until the next resource reload. It never crashes.
- **Persistent targets**: a chain target with `"persistent": true` keeps its contents between frames for as long as that chain stays applied (`PostChain.getOrCreatePersistentTarget`). It is cleared when the chain is first applied, when it is removed, and on resize or resource reload. This is what makes fades possible with no client code.

What Shader Maker now generates (Conditions → **Command switch**, project v10):

- `shader_maker:<layer>`: the layer's grade with a **fade-in**. A 1×1 persistent clock stores the `GameTime` of the first applied frame. The fade then advances with game ticks, so it is frame-rate independent and pauses with the game. Once finished, the clock latches, so the 20-minute `GameTime` wrap cannot restart it.
- `shader_maker:<layer>_off`: the same grade fading **out**. To switch off smoothly: `remove <layer>` and `add <layer>_off`. The helper datapack does this and later removes `_off`.
- Height and area conditions still apply inside command layers. Sky and render-signal conditions are rejected (export issue), because each would need its own copy of the core overrides and detector.
- **Helper datapack** (Export, or "Install helpers in this world" in singleplayer): `function shader_maker:menu` (clickable chat toggles), `on/<id>`, `off/<id>`, `toggle/<id>`, `clear`, and `give/<id>` (a command block preloaded with the toggle).
- **Demo**: `download/command-demo-26.3/`, built by `tools/CommandDemo.java` from the normal compiler. It has 10 looks and an automatic mode driven by exact checks: biome tags (desert/badlands/savanna, snowy/frozen, jungle/lush/swamp), eyes in water, below Y 0, raining with sky visible (predicate), inside `#minecraft:village` (structure predicate) and health ≤ 6. Sepia and Dream are manual only.

Seen in the dev client: add, fade-in (3 s), fade-out, cleanup by the tick function, clickable menu, toggle, command-block giver (correct `block_entity_data`), automatic Frozen in snowy plains, and Deep dark stacking under Y 0.

Limits: needs operator permission or a datapack, so it is not "drop-in" for a pack that should work on any server. Switching is server-tick granular, and it is per player, not per camera (spectating does not follow). A resize or resource reload restarts any running fade-in.
26.3 data-format gotcha: predicates use `"type"`, not `"condition"`. The first demo build failed `/reload` with "No key type".

## 2. Holding framebuffer signals (fixes the sky-colour flicker)

Cause of the flicker: the sky and render-signal detectors re-decided every frame from what was on screen. Looking at the ground (no sky), or in a cave, meant "no signal", so the layer switched off.

Fix: each detector feeds a hold pass with persistent memory (`sky_memory` 17×1, `context_memory` 5×1).
- Texel 0 stores the last `GameTime` (24-bit packed in RGB). The others store each signal's weight (16-bit packed in RG).
- With a signal this frame, the weights move toward it at 1/20 per game tick (about 1 s).
- With **no** signal this frame (no sky, or no marked terrain), the weights are **held**.
- The first frame after a load snaps rather than fades.
- The grade uses `smoothstep(weight)`, so layer switches are now cross-fades rather than hard cuts.

Seen in game: the snowy-sky layer stays on when looking straight down at snow, and turns off in plains with sky visible.
Behaviour to know: entering a new area while looking down keeps the old look until the sky (or terrain) is seen.

## 3. Exact biome identity from a datapack (render signals)

Recipe (verified in game):

1. Datapack overrides `data/minecraft/worldgen/biome/<biome>.json`. Copy the vanilla file and add one attribute: `"minecraft:visual/night_vision_color": "#XXXXXX"`.
2. In Shader Maker, give a layer a render signal: Night-vision colour input, that colour, RGB match.
3. The core `lightmap.fsh` override compares the raw `NightVisionColor` uniform each tick. Terrain carries the result to the post pass, which grades the screen.

Test: snowy_plains set to `#123456` gave magenta in snowy plains and none in plains. It stayed on while looking only at the sky.

Why night-vision colour: it reaches the shader every tick, but it is only *visible* while the Night Vision effect is active. Default `#999999`. To stay invisible even then, encode the id in the low bits near the default (e.g. `#9A9999`, `#999A99`, …; ±3 steps per channel ≈ 343 ids) and use tolerance ≈ 0.002 (½ step). The attribute is spatially interpolated, so a biome border reads as "no match" for a moment and the layers cross-fade.

Costs and caveats:
- Overriding worldgen biomes makes Minecraft show **"Worlds using Experimental Settings are not supported"** on every world load ("I Know What I'm Doing!").
- Registry changes need a world reload, not `/reload`.
- The override replaces the whole biome file, so it must be updated for each game version.
- At most 4 distinct render-signal sets per pack (4 bits). Sky colour allows 16 groups.

Compared with `/posteffect`: the attribute route is client-side, per frame, and needs no op permission or tick functions, but it needs terrain on screen to *change*. `/posteffect` needs a datapack with commands, but nothing on screen.

## 4. Bug found in game and fixed: false render-signal matches from water

The terrain marker was alpha = `15·(1+mask)`. Every 8-bit alpha is an integer, so about 1 in 15 blended pixels mimics a valid code. The 26.3 OIT composite writes translucent coverage into alpha, so distant water produced stable fake codes. The three-sample quorum then switched layers on everywhere: a colour that could never match still activated. This was the first in-game test of this path; earlier evidence was software raster only.

Fix: the terrain shader also stamps an RGB low-bit checksum (R odd, G even, B odd; ≤1/255 per channel, not visible). Decoding requires it, which cuts random matches to about 1 in 120. Full-screen sets now also need ≥ 1/16 of marked terrain samples, as well as ≥ 3. Seen in game: water rejected, and the never-match colour no longer activates. Sky markers have the same weak alpha-only code, but are limited to sky-depth pixels; no false sky match was seen.

## 5. Not possible in a vanilla pack (checked)

- **Reading the lightmap, map textures or any dynamic texture in a post pass.** `PostChain.createPass` maps every `TextureInput` to `textures/effect/<path>.png` through the texture manager.
- **World fog in post passes.** `GameRenderer` sets `FogMode.NONE` before post effects.
- **Per-frame custom uniforms.** Post uniform blocks are filled once at chain creation.
- **Sharing memory between chains.** Persistent targets belong to one chain. Different `/posteffect` effects can only talk through `minecraft:main` in list order.
- **Player state on the client** (health, effects, held item, biome id) except through `/posteffect`, attributes a datapack writes, or what is visible on screen.
- **Time of day without a datapack.** `GameTime` is elapsed time. A datapack can drive day phases through `/posteffect` (e.g. `time query daytime` into a score).

## Open follow-ups

- Clean vanilla client, both backends, for: command effects, fades, held signals, the new terrain checksum, and the area condition.
- `tools/verify-world-signals-gl.py` and the visual-biome raster harness still bind the old `ContextSampler`/`EnvironmentSampler` grade inputs. They need updating to the held-memory chain and the RGB checksum before `--context-gl` can pass again. The default `verify-offline.py` run (unit, contract and 720 native ShaderC cases) passes.
- Possible next steps: a "biome id" helper that writes the night-vision datapack for chosen biomes, and a server-variable channel (an earlier `/posteffect` writes a code into `main` for a later one).
