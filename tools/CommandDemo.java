import io.github.tjhgibbs.shadermaker.*;
import java.nio.file.*;
import java.util.*;

/**
 * Builds the /posteffect demo: a resource pack of command-switched looks (made by the normal
 * PackCompiler) plus a datapack with the generated helper functions and an example "auto" layer
 * that switches looks from exact server-side checks (biome, structure, weather, water, health, depth).
 * Vanilla only. Usage: java --source 25 -cp <classes:gson> tools/CommandDemo.java <output dir>
 */
public class CommandDemo {
    record Look(String name, Project grade, double fade) {}
    public static void main(String[] args) throws Exception {
        Path out = Path.of(args[0]);
        Files.createDirectories(out);
        var looks = List.of(
            new Look("Desert heat", Project.neutral().with("exposure", .15).with("contrast", 1.12).with("red", 1.12).with("blue", .82).with("vibrance", .25), 2),
            new Look("Frozen", Project.neutral().with("saturation", .55).with("red", .86).with("blue", 1.2).with("contrast", .92).with("exposure", .1), 2),
            new Look("Lush", Project.neutral().with("vibrance", .6).with("green", 1.08).with("contrast", 1.05), 2),
            new Look("Underwater", Project.neutral().with("red", .7).with("green", 1.05).with("blue", 1.2).with("vignette", .45).with("contrast", .9), .5),
            new Look("Deep dark", Project.neutral().with("exposure", -.45).with("saturation", .4).with("vignette", .7).with("blue", 1.1), 3),
            new Look("Storm", Project.neutral().with("saturation", .6).with("exposure", -.25).with("contrast", 1.1), 4),
            new Look("Village glow", Project.preset("Golden dusk"), 2),
            new Look("Danger", Project.neutral().with("red", 1.5).with("green", .65).with("blue", .65).with("vignette", .85), .3),
            new Look("Sepia", Project.neutral().with("saturation", .1).with("red", 1.18).with("green", 1.0).with("blue", .72).with("vignette", .35), 1),
            new Look("Dream", Project.neutral().with("exposure", .35).with("contrast", .8).with("vibrance", .5).with("shoulder", .8).with("vignette", .3), 3));
        var layers = new ArrayList<Project.Layer>();
        for (var l : looks)
            layers.add(new Project.Layer(l.name(), l.grade(), true, 1, Set.of(), null, null, null, null, null, WorldSignals.Controls.NONE, new Project.Options(null, true, l.fade())));
        var project = Project.neutral().withLayers(layers);
        if (!PackCompiler.issues(project).isEmpty()) throw new IllegalStateException(PackCompiler.issues(project).toString());
        var resources = new TreeMap<>(PackCompiler.files(project, PackCompiler.Activation.ALWAYS));
        resources.put("pack.mcmeta", "{\"pack\":{\"description\":\"Shader Maker /posteffect demo · Java 26.3\",\"min_format\":[97,1],\"max_format\":[97,1]}}\n");
        PackCompiler.writeFiles(out.resolve("shader-maker-command-demo-resourcepack.zip"), resources);
        // Effects only: no end_of_frame or grade shader, so it can sit above the editor's preview pack
        // (e.g. as a world resource pack, <world>/resourcepacks/resources.zip) without replacing it.
        var effects = new TreeMap<String, String>();
        effects.put("pack.mcmeta", "{\"pack\":{\"description\":\"Shader Maker /posteffect demo looks · Java 26.3\",\"min_format\":[97,1],\"max_format\":[97,1]}}\n");
        resources.forEach((k, v) -> {
            if (k.startsWith("assets/shader_maker/post_effect/") || k.startsWith("assets/shader_maker/shaders/post/command")) effects.put(k, v);
        });
        PackCompiler.writeFiles(out.resolve("shader-maker-command-demo-effects-only.zip"), effects);

        var data = new TreeMap<>(CommandEffects.datapack(project));
        data.put("pack.mcmeta", "{\"pack\":{\"description\":\"Shader Maker /posteffect demo · Java 26.3\",\"min_format\":[121,0],\"max_format\":[121,0]}}\n");
        data.put("data/minecraft/tags/function/load.json", "{\"values\":[\"shader_maker:load\",\"shader_maker_demo:load\"]}\n");
        data.put("data/minecraft/tags/function/tick.json", "{\"values\":[\"shader_maker:tick\",\"shader_maker_demo:tick\"]}\n");
        String ns = "data/shader_maker_demo/";
        data.put(ns + "tags/worldgen/biome/hot.json", tag("minecraft:desert", "minecraft:badlands", "minecraft:eroded_badlands", "minecraft:wooded_badlands", "minecraft:savanna", "minecraft:savanna_plateau", "minecraft:windswept_savanna"));
        data.put(ns + "tags/worldgen/biome/cold.json", tag("minecraft:snowy_plains", "minecraft:ice_spikes", "minecraft:snowy_taiga", "minecraft:snowy_beach", "minecraft:snowy_slopes", "minecraft:frozen_peaks", "minecraft:jagged_peaks", "minecraft:grove", "minecraft:frozen_river", "minecraft:frozen_ocean", "minecraft:deep_frozen_ocean"));
        data.put(ns + "tags/worldgen/biome/lush.json", tag("#minecraft:is_jungle", "minecraft:lush_caves", "minecraft:mangrove_swamp", "minecraft:swamp"));
        data.put(ns + "predicate/storm.json", """
            {"type":"minecraft:all_of","terms":[
              {"type":"minecraft:weather_check","raining":true},
              {"type":"minecraft:location_check","predicate":{"can_see_sky":true}}]}
            """);
        data.put(ns + "predicate/in_village.json", """
            {"type":"minecraft:location_check","predicate":{"structures":"#minecraft:village"}}
            """);
        data.put(ns + "function/load.mcfunction", """
            scoreboard objectives add smd.hp health
            tellraw @a [{"text":"Shader Maker demo loaded. ","color":"aqua"},{"text":"[Open menu]","color":"gold","click_event":{"action":"run_command","command":"/function shader_maker_demo:menu"}}]
            """);
        var tick = new StringBuilder("# Automatic looks from exact server-side checks. Players tagged smd.manual are skipped.\n");
        auto(tick, "desert_heat", "if biome ~ ~ ~ #shader_maker_demo:hot", "unless biome ~ ~ ~ #shader_maker_demo:hot");
        auto(tick, "frozen", "if biome ~ ~ ~ #shader_maker_demo:cold", "unless biome ~ ~ ~ #shader_maker_demo:cold");
        auto(tick, "lush", "if biome ~ ~ ~ #shader_maker_demo:lush", "unless biome ~ ~ ~ #shader_maker_demo:lush");
        auto(tick, "underwater", "anchored eyes positioned ^ ^ ^ if block ~ ~ ~ minecraft:water", "anchored eyes positioned ^ ^ ^ unless block ~ ~ ~ minecraft:water");
        auto(tick, "deep_dark", "if entity @s[y=-128,dy=128]", "unless entity @s[y=-128,dy=128]");
        auto(tick, "storm", "if predicate shader_maker_demo:storm", "unless predicate shader_maker_demo:storm");
        auto(tick, "village_glow", "if predicate shader_maker_demo:in_village", "unless predicate shader_maker_demo:in_village");
        auto(tick, "danger", "if entity @s[scores={smd.hp=..6}]", "unless entity @s[scores={smd.hp=..6}]");
        data.put(ns + "function/tick.mcfunction", tick.toString());
        data.put(ns + "function/menu.mcfunction", """
            tellraw @s {"text":"── Shader Maker /posteffect demo ──","color":"aqua"}
            tellraw @s [{"text":"Automatic: ","color":"gray"},{"text":"[On]","color":"green","click_event":{"action":"run_command","command":"/function shader_maker_demo:auto_on"},"hover_event":{"action":"show_text","value":"Looks follow biome, water, depth, weather, villages and low health"}},{"text":" "},{"text":"[Off]","color":"red","click_event":{"action":"run_command","command":"/function shader_maker_demo:auto_off"},"hover_event":{"action":"show_text","value":"Manual mode: clears looks and stops automatic switching"}}]
            tellraw @s {"text":"Manual toggles (best with Automatic off):","color":"gray"}
            function shader_maker:menu
            tellraw @s [{"text":"Command blocks: ","color":"gray"},{"text":"[Give Sepia toggle block]","color":"gold","click_event":{"action":"run_command","command":"/function shader_maker:give/sepia"}}]
            """);
        var forget = new StringBuilder();
        for (var id : AUTO) forget.append("tag @s remove smd.auto.").append(id).append('\n');
        data.put(ns + "function/auto_on.mcfunction", "tag @s remove smd.manual\n" + forget + "tellraw @s {\"text\":\"Automatic looks on. Looks you toggle by hand stay until you toggle them again.\",\"color\":\"green\"}\n");
        data.put(ns + "function/auto_off.mcfunction", "tag @s add smd.manual\n" + forget + "function shader_maker:clear\ntellraw @s {\"text\":\"Automatic looks off. Use the toggles.\",\"color\":\"yellow\"}\n");
        PackCompiler.writeFiles(out.resolve("shader-maker-command-demo-datapack.zip"), data);
        Files.writeString(out.resolve("README.txt"), """
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
            Look ids: %s

            Limits: /posteffect needs operator permission (functions have it). Effects are per player and are
            saved with the player. Fades follow game ticks. The look is switched by the server, so it changes
            when the server's check changes (every tick here), not per frame.
            """.formatted(String.join(", ", layers.stream().map(Project.Layer::commandId).toList())));
        Files.writeString(out.resolve("shader-maker-command-demo-project.json"), project.encode());
        System.out.println("Wrote demo to " + out.toAbsolutePath());
    }
    static final List<String> AUTO = new ArrayList<>();
    /**
     * Automatic mode owns only what it switched on (tag smd.auto.<id>). A look you toggled by hand is
     * never removed by it; a look you switch off by hand stays off until you leave and re-enter the zone.
     */
    static void auto(StringBuilder tick, String id, String when, String unless) {
        AUTO.add(id);
        String entering = "execute as @a[tag=!smd.manual,tag=!smd.auto." + id + "] at @s " + when + " run ";
        String leaving = "execute as @a[tag=!smd.manual,tag=smd.auto." + id + "] at @s " + unless + " run ";
        tick.append(entering).append("function shader_maker:on/").append(id).append('\n')
            .append(entering).append("tag @s add smd.auto.").append(id).append('\n')
            .append(leaving).append("function shader_maker:off/").append(id).append('\n')
            .append(leaving).append("tag @s remove smd.auto.").append(id).append('\n');
    }
    static String tag(String... values) {
        return "{\"values\":[" + String.join(",", Arrays.stream(values).map(v -> "\"" + v + "\"").toList()) + "]}\n";
    }
}
