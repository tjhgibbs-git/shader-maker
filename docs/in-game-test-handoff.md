# Next session: vanilla in-game shader feasibility tests

Prepared 2026-11-02. Start with [the authoritative spec](vanilla-only-handoff.md) and [the evidence/probe instructions](vanilla-feasibility.md). The project is the nested `shader-maker` Git repository, on `main`. This handoff accompanies the vanilla-spec commit; use `git log -1` for its ID.

## Scope and current state

The deliverable is vanilla resource pack shader authoring. Fabric is the editor host only. An optional paired datapack mode can coordinate gameplay events; it does not replace standalone resource-pack output. Keep biome, camera Y, daylight, weather, lightning and atmosphere requirements. Investigate vanilla post/core paths before dropping a capability. Preserve existing biome project fields.

No production Java renderer, model or compiler behavior changed in this session. Static base-grade export works as previously recorded; **all enabled layers still block Apply/Export**. There is no conditional exporter, blood-moon preset, event editor or paired datapack exporter yet. The spec chooses a persistent layer stack with condition/contribution feedback, plus an optional event integration boundary; editable nodes are deferred until actual branching/reuse needs them.

Added tools:

- `tools/vanilla-shader-probes.py`: creates five standalone diagnostic resource packs from a pinned vanilla client JAR.
- `tools/VanillaProbeCheck.java`: uses the existing MinecraftContractTest ShaderC helper, actual game JSON codec and SPIR-V reflection. It is diagnostic tooling, not shipped code.

Verified in this session: five generated ZIPs have valid structure; four post-chain JSON definitions decode; fourteen ShaderC/reflection fragment cases pass. Python syntax, local Markdown links and Git whitespace checks pass. **No graphics device, clean vanilla client or in-game event was tested.** The development harness status check found no running client. Do not promote source/compile evidence to runtime proof.

## Prepare the test client

1. Use Minecraft Java **26.3**, resource format **97.1**, Java **25**. Record GPU, driver and graphics backend. Use a disposable creative test world with commands enabled.
2. Start a **clean vanilla instance without mods** for acceptance. Record the instance directory and pack order; disable other shader/core/post-effect packs. The existing Fabric/block-scraper client is useful for screenshots and preliminary diagnostics, but does not establish modless support.
3. Regenerate fixtures; `/tmp` files from this session are disposable:

   ```sh
   python3 tools/vanilla-shader-probes.py \
     ~/.gradle/caches/fabric-loom/26.3/minecraft-client.jar \
     /tmp/shader-maker-vanilla-probes
   ```

   Substitute a verified official 26.3 client JAR if the cache differs. The inspected JAR hash and offline harness commands are in the evidence document. On this machine Java 25 was available at `/home/tjhgi/.local/jdk/jdk-25.0.5+8`; set JAVA_HOME and PATH if needed. Do not depend on temporary decompiler output.
4. Install the five probe ZIPs in the clean instance's `resourcepacks` folder. Enable **only one probe at a time**. Each post probe replaces the automatic end_of_frame resource; terrain-fog replaces a core fragment. Disable each probe and confirm restoration before continuing.

## First priority: run the existing probes

| Order | Probe | Test and record |
|---|---|---|
| 1 | camera-y | Known Y values, negative/fractional movement, integer boundaries, first/third person camera changes. Expected brightness = clamp((cameraY + 64) / 384, 0, 1) |
| 2 | elapsed-time | Record `/time query gametime`, ordinary ticking and `/time set midnight`/`noon`. Test world-clock pause/resume separately from game pause and tick freeze. Expected shader phase follows elapsed ticks, not the sun |
| 3 | post-fog | Test overworld/Nether/End, biome changes, rain and immersion. Source predicts NONE/empty fog at post stage. Black can be a valid result, not a shader failure |
| 4 | terrain-fog | Repeat comparable biome/weather/immersion tests. Inspect terrain versus sky/entities/hand. This probes biome-influenced world fog; it does not prove exact biome-ID matching |
| 5 | raw-depth | Known near/far objects, sky, hand, water/translucency, view bob, resize, reload and OIT on/off. Output is raw depth; do not call it linear distance |

Repeat on **OpenGL and Vulkan**. Save screenshots and relevant shader logs, including failures. Confirm post versus hand/2D HUD coverage: source ordering contradicts earlier “including all HUD” wording. Exercise pack disable/re-enable, world unload/reload and dimension changes; check for stale signals or resource errors.

Record a result even when it disproves the proposed route. Do not replace a failed vanilla probe with a mod-fed uniform and call the capability proven.

## Second priority: add targeted vanilla fixtures

After the basic probes produce reliable results, implement small diagnostic packs for the unresolved inputs, preserving the no-mod test contract:

- **Exact biome versus environment classification:** inspect core-stage world fog/sky and raw biome terrain tint. Test biomes with similar attributes/climate, blended borders, caves, occluded sky, absent tinted geometry and custom server biomes. Document collisions. A matching fog color is not a biome ID. Any cross-stage encoding needs proof of its real target/channel and appearance preservation.
- **Daylight:** Globals GameTime comes from elapsed world ticks. Find a signal that follows the actual sky clock, including clock jumps, pause/rate, dimensions and multiplayer. Do not copy the Java WorldContext clock into a shader through a mod.
- **Weather:** compare clear/rain/thunder, snow and dry biomes, shelter, day/night and dimensions. Distinguish global weather strength from local precipitation. Core sky transform alpha, world fog and lightmap paths are candidates, not a finished export implementation.
- **Lightning:** ClientLevel modifies sky color and sky-light factor for a flash; lightmap rendering consumes the latter. Compare a real/summoned flash, bright noon, End flashes and Hide Lightning Flashes. Do not claim a reliable event detector from sky factor == 1 alone.
- **Fog:** reconstruct known distance/height or use suitable world/core stage inputs. Post Projection is orthographic. Measure scene coverage and transparency rather than assuming a generic inverse-projection recipe works.

## Optional paired-mode test: Blood Moon

Build a minimal disposable datapack/resource-pack prototype after the above basics, not a full gameplay editor:

1. Give the datapack explicit start/stop functions and one server-owned active flag. Start activates a generated, namespaced command effect; stop removes only that effect. Do not clear effects owned by other packs.
2. Prove `/posteffect add/remove <player> <effect>` from the datapack works in a modless client with the matching resource pack. Handle player join, dimension eligibility and event end. The server command cannot confirm that a client actually rendered the effect.
3. First establish visual activation with a simple diagnostic grade. Then prove **sky-specific** red coloration and moon appearance separately; a global red tint does not satisfy the final sky-only requirement.
4. Use a full-moon night for the first test. The pinned vanilla `data/minecraft/timeline/moon.json` has a `visual/moon_phase` track with `full_moon`. A custom phase/environment timeline is a later alternative; do not assume `/posteffect` changes the moon phase.
5. Demonstrate a bounded monster wave around active eligible players, controlled by the datapack event. Natural spawn-list changes do not automatically bypass mob caps or placement rules. Stop scheduling waves at event end; specify whether already spawned mobs remain.
6. Check start/stop/restart, disconnect/rejoin and pack removal. Event state coordinates gameplay and presentation in one direction; no arbitrary scoreboard-to-uniform or shader-to-server channel has been established. GameTime is not reset when an effect activates.

This optional prototype requires a datapack installed server-side and a resource pack client-side. Label both dependencies. Preserve the standalone visual export mode.

## Write back and next implementation

Update vanilla-feasibility.md with a table of tested version, backend, GPU/driver, probe, exact conditions/commands, expected and observed behavior, screenshots/log paths, pass/fail and limitations. Reference exact fixture changes and commits. Keep failures and ambiguity visible. Store durable evidence in the repository's existing shots/docs conventions or a clearly linked artifact, not only `/tmp`.

Once camera-Y/device tests pass, the next production change is vanilla compilation of unconditional and camera-Y layers using the existing blend/curve/range semantics, with CPU/GPU parity and Apply/Export using the same resources. Keep unresolved conditional projects intact and make errors specific. Do not start a separate mod renderer.

For authoring UI follow-up, the sibling block-scraper AGENTS.md documents its screenshot-driven loop. The existing `docs/v0.1-handoff.md` retains Windows re-apply, reference-image, UI and recovery checks; those remain outstanding independently of these probes.
