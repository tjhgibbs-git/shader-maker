Shader Maker /posteffect demo · Minecraft Java 26.3 · vanilla, no mods

1. Put shader-maker-command-demo-resourcepack.zip in .minecraft/resourcepacks and enable it.
2. Create or open a world with cheats on. Put shader-maker-command-demo-datapack.zip in
   <world>/datapacks (or add it on the Create World > Data Packs screen), then /reload.
3. Click [Open menu] in chat, or run /function shader_maker_demo:menu

Automatic mode (default) switches looks for each player from exact server-side checks:
  Desert heat  - desert, badlands, savanna biomes
  Frozen       - snowy and frozen biomes
  Lush         - jungle, lush caves, swamps
  Underwater   - eyes in water
  Deep dark    - below Y 0
  Storm        - raining and the sky is visible
  Village glow - inside a village structure
  Danger       - 6 health (3 hearts) or less
Looks fade in and out and can stack. Clickable toggles work in either mode: automatic mode only
switches off looks it switched on itself. Sepia and Dream have no automatic trigger.

Command blocks: /function shader_maker:give/<look> gives a command block preloaded with the
toggle for the nearest player. Place it and add a button. Raw command:
  /posteffect add @s shader_maker:<look>   /posteffect remove @s shader_maker:<look>
  /posteffect clear @s
Look ids: desert_heat, frozen, lush, underwater, deep_dark, storm, village_glow, danger, sepia, dream

Limits: /posteffect needs operator permission (functions have it). Effects are per player and are
saved with the player. Fades follow game ticks. The look is switched by the server, so it changes
when the server's check changes (every tick here), not per frame.
