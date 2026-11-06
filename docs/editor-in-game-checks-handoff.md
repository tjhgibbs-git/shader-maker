# Editor in-game checks handoff

Updated **2026-11-03**. Minecraft **26.3**, Fabric Loader **0.20.1**, Fabric API **0.165.0+26.3**, Java **25**. Scope: verify the recent editor changes, then resume outstanding fog/sky acceptance. Do not add new features as part of this check pass.

## Build and install first

- Both `download/shader-maker-0.1.0.jar` and `download/shader-maker-fog-sky-26.3.jar` contain the same latest build, including the removal of title-screen access. Install **one**, replacing the existing Shader Maker JAR, then restart Minecraft.
- Latest JAR SHA-256, valid for either filename: `d403045794f5cea6215afa344d1865ac3e6789d145673a13300784265ab7cf34`.
- Intended Prism destination: `%APPDATA%\PrismLauncher\instances\26.3\minecraft\mods\shader-maker-0.1.0.jar`.
- **Prism updated 2026-11-03:** `.../instances/26.3/minecraft/mods/shader-maker-0.1.0.jar` now has the hash above (verified after copy; previous JAR was `7113dc08…c4d9`). Re-verify before reporting results from Prism.
- The authoring instance has `Axiom-6.1.5-for-MC26.3.jar`; use it for the shortcut coexistence checks below. Use a disposable world/project and keep saved originals available.

## Evidence and limits

The latest build passed `JAVA_HOME=/home/tjhgi/.local/jdk/jdk-25.0.5+8 python3 tools/verify-offline.py`: production/client/test compilation, Atmosphere, Curves, **391** Project checks, **349** fog/sky assertions, **16** post-chain decodes, **66** native shader compile/reflection checks, WorldContext and SampleImage.

Before the final world-access-only change, `--fog-gl` also passed **1,205** fog raster assertions and **90** sky transport assertions on llvmpipe. The final change only gates editor entry; the raster suites were not rerun after it. Neither result establishes Minecraft rendering or Axiom coexistence.

**None of the new Reset/New/Enter/world-access interactions has been observed in-game.** The current environment cannot open display `:0`, has no Xvfb, and the attempted cached client launch failed with `Unable to initialize SDL: No available video device`. Use a working interactive client for these checks.

## 1. World-only entry

| Check | Expected |
| --- | --- |
| Launch to the home/title screen | No Shader Maker button. |
| Press N on the title screen and other out-of-world menus | Editor does not open. |
| Join a world; press N with no screen open | Editor opens. |
| Open the in-world pause menu | Shader Maker button is present and opens the editor. |
| Type N in project/layer names, chat and other text fields | Character input works; the editor is not reopened and selection is not reset. |
| Press N with inventory or another mod's screen open | Shader Maker does not replace that screen. |
| Leave the world and return to the title screen | No button or hotkey access remains. Repeat after rejoining. |

## 2. Reset the grade

Prepare a non-Neutral base grade, two differently named layers, a non-default layer strength/condition and at least one fog object. Change sliders and curves so reset and preservation are obvious.

1. Edit one layer. Choose **Reset grade…** in the editor header. Confirm the dialog names that layer and explains the scope. **Cancel**, then separately **Escape**, must leave the grade intact.
2. Confirm **Reset grade**. Only that layer's grade should become Neutral, with identity/straight curves. Its name, enabled state, strength and conditions must survive. The base, other layers and fog must survive.
3. Use **Undo**, then **Redo**. They must recover and reapply all affected grade values/curves. Repeat using Ctrl+Z and Ctrl+Y or Ctrl+Shift+Z with no text field focused.
4. Repeat on the base grade. Base reset must preserve the layer list and fog.
5. Save/reload and inspect the image preview; export and inspect the embedded project snapshot. Reset values must persist. Check at the minimum supported GUI size (**340×220 GUI units**) and a larger size for header/button overlap and clipped labels.

Reset edits the project and saves the draft; it does **not** automatically reapply the game preview. Use Apply when checking the world result.

## 3. Start a new pack

1. With the populated project still available, open **Layers… → New…** beside Undo/Redo/Hide all. **Cancel** and **Escape** must preserve the pack.
2. Confirm **New pack**. Expect a Neutral base, no grade layers, no fog, base selection, project name **My look**, and **Auto pack** mode. The Layers page returns to its first page.
3. Verify previously saved projects and exported ZIPs remain on disk.
4. Undo must restore the previous **project settings**, including layers, conditions, curves and fog. Redo must return to the empty Neutral project. **Project name and Auto/Command mode are session settings, not part of Undo history**; do not expect Undo to restore them.
5. Save/reload the new project and check the empty state. Check all three Layers views and the footer at small/large GUI sizes.

New also does not automatically replace or disable an already applied game preview. Apply or Disable separately when comparing the world.

## 4. Enter / Done navigation

1. On Layers, select a non-base layer and choose **Grade…** or its row's **Edit**. Change a value. Press plain **Enter**: it must commit and return to the same Layers screen, retaining selection and values.
2. Repeat with **keypad Enter** and the **Done** button. Repeat after editing the base grade from Layers, on another Layers page and in Stack/Graph/Overview views.
3. Try Enter with a grade slider, an ordinary button, the Reset button and the project-name field focused. It must perform Done rather than activate the focused button or reset the grade. Text already entered in the name should remain.
4. On a freshly opened editor that has not been entered from Layers, Done/Enter closes to its original parent/game. **Escape retains the existing close behavior; only Done/Enter gained the Layers return path.**
5. Confirmation dialogs have their own normal keyboard handling. The grade editor's Enter shortcut must not run through a modal dialog.

## 5. Undo and Axiom coexistence

- With Shader Maker's editor or Layers screen open and no text field focused, test **Undo/Redo buttons**, **Ctrl+Z**, **Ctrl+Y**, and **Ctrl+Shift+Z**. Each shortcut should affect Shader Maker history once.
- Make a disposable Axiom world edit first. While Shader Maker is open, undo a Shader Maker grade edit and verify the world edit remains. Close Shader Maker; verify Axiom's normal undo still works. Repeat with Axiom's editor active before opening Shader Maker if that workflow is available.
- With a name/text field focused, Ctrl+Z belongs to text editing rather than project history. Check that typing names does not unexpectedly undo a layer or a world edit.
- Installed Axiom 6.1.5 bytecode was inspected: its SDL keyboard handler defers to Minecraft when a screen is open, and Shader Maker consumes its recognized history shortcuts. **This is source/bytecode evidence, not a live coexistence pass.** No Axiom-specific mixin or runtime dependency was added. If another mod intercepts input before the screen, record the mod/version and exact sequence.

## 6. Resume graphics acceptance

The two largest earlier gaps remain open:

1. **No visible in-world authored-fog render has been observed.** Previous screenshots were reload overlays. In the authoring client, use Everywhere haze for a quick Apply → visible world → Disable comparison. The flat test surface is near **Y −60**; fixtures targeting **Y 62–78** will not demonstrate their intended band there without moving the camera/scene or editing placement. Capture actual terrain after the reload overlay disappears.
2. **Unmodified vanilla 26.3 acceptance is still untested.** Follow [Clean-client fog acceptance](fog-sky-in-game-handoff.md#clean-client-fog-acceptance) and [Sky colour and datapack signalling acceptance](fog-sky-in-game-handoff.md#sky-colour-and-datapack-signalling-acceptance). Use a separate vanilla client with **no editor mod, Axiom, shader loader or other runtime dependency**. Test the standalone ZIPs, OpenGL/Vulkan, terrain translucent/OIT/leaves/water, native fog interaction, far coordinates, frame times and the `#ff8040` datapack sky signal. The optional datapack signal is a separate authored mode, not a dependency of standalone fog packs.

Other earlier UI gaps are listed in [the outstanding-work handoff](fog-sky-outstanding-handoff.md#outstanding-lower-impact): legacy exact-ID conversion, export-issue wrapping, soft-edge previews, dirty-draft resize and v8 reload. Preserve exact-ID data unless the author explicitly confirms conversion to a wider sky-colour group.

## Harness recovery and recording results

If using block-scraper, read `../block-scraper/AGENTS.md`. Resource reloads inside a world have stalled its Fabric gametest hook in `Minecraft.postRunTasks`; this is not shader-compilation evidence. Recover with `block-scraper restart`; after Apply, poll using sleep and `shot`, rather than `wait`. Inspect every screenshot: an overlay with roughly 11 distinct colours is not a world-render pass. Use `block-scraper stop`, never `pkill -f KnotClient`. `click --text` needs the full widget label.

For each check record **PASS / FAIL / BLOCKED / NOT RUN**, the installed JAR hash, client/backend/GPU/driver, window and GUI size, relevant settings/mods, exact steps and observed result. For failures attach the smallest project, screenshot and game log; for rendering include camera coordinates/direction and enabled pack order. A clean resource reload alone is not visual acceptance. Update this handoff with observed results and keep the remaining gaps explicit.

## Observed results (2026-11-03, dev client via block-scraper, 1280x720, GUI 640x360, Minecraft 26.3, project sources; **not** the installed Prism JAR, no Axiom)

| Check | Result |
| --- | --- |
| 1. No title-screen button; N on title ignored | PASS |
| 1. N in world opens editor; pause-menu button present and opens editor | PASS |
| 1. N typed into project-name field and chat; N in inventory | PASS (editor not reopened, text typed) |
| 1. After leaving world: N ignored on title | PASS |
| 2. Reset dialog names layer; Cancel and Escape keep grade | PASS |
| 2. Reset layer: grade neutral; name kept; base/other layer kept; Undo/Redo via buttons, Ctrl+Z/Y/Ctrl+Shift+Z | PASS |
| 2. Base reset keeps layer list | PASS |
| 2. Save/reload, export snapshot, minimum GUI size | NOT RUN |
| 3. New: Cancel/Escape keep pack; confirm gives Neutral base, no layers, name "My look"; Undo restores layers, Redo empties | PASS |
| 3. Fog restore on New undo, other Layers views at small/large sizes | NOT RUN |
| 4. Enter, keypad Enter and Done return to Layers from layer/base edit; Enter with slider focused and from name field returns | PASS |
| 4. Enter does not act through a modal dialog | PASS (dialog stayed open) |
| 4. Fresh-editor Done closes; other-page / Stack/Graph/Overview variants | NOT RUN |
| 5. Axiom coexistence | NOT RUN (Axiom is only in the Prism instance) |
| 6. In-world fog render; clean vanilla acceptance | NOT RUN |

Harness note: `block-scraper key` takes Minecraft key names (`keypad.enter`), not GLFW numbers.

## Follow-up (2026-11-03, later)

The editor flow was reworked; see [ux-flow-handoff.md](ux-flow-handoff.md) for the new entry point (Layers home), in-world fog and render-context results, the Blocks-only marker fix and the `--idle-tick` harness finding. Reset/New/Enter checks above still describe the same actions; Enter/Done/Esc in the grade editor now always return to the Layers home.
