package io.github.tjhgibbs.shadermaker;

import com.google.gson.*;
import java.util.*;

/**
 * Command-switched layers: each becomes its own vanilla 26.3 post effect, applied per player with
 * {@code /posteffect add <player> shader_maker:<id>}. The server keeps a per-player list, saved in
 * player data, and sends it to the client; the client runs end_of_frame first, then the list in
 * the order effects were added. So a command layer grades on top of the always-on look.
 *
 * Each effect fades in from the first frame it is applied, using a persistent 1x1 clock target.
 * {@code shader_maker:<id>_off} is the same grade fading out, for a smooth switch-off.
 * Vanilla only: resource pack effects, plus an optional, explicitly exported datapack of helper
 * functions. Nothing here is required by standalone always-on packs.
 */
public final class CommandEffects {
    private CommandEffects() {}
    public static final String NAMESPACE = "shader_maker";

    public static List<Project.Layer> layers(Project p) {
        return p.layers().stream().filter(l -> l.enabled() && l.options().command()).toList();
    }

    public static String effectId(Project.Layer l) { return NAMESPACE + ":" + l.commandId(); }

    public static void add(Map<String, String> files, Project p) {
        var layers = layers(p);
        if (layers.isEmpty()) return;
        files.put("assets/shader_maker/shaders/post/command_clock.fsh", SignalMemoryShaders.startClock());
        for (var l : layers) {
            String id = l.commandId();
            files.put("assets/shader_maker/post_effect/" + id + ".json", chain(id, l.options().fadeSeconds(), false));
            files.put("assets/shader_maker/post_effect/" + id + "_off.json", chain(id, l.options().fadeSeconds(), true));
            files.put("assets/shader_maker/shaders/post/command/" + id + ".fsh", fragment(l));
        }
        var readme = new StringBuilder(files.getOrDefault("README.txt", ""));
        readme.append("\nCOMMAND-SWITCHED LAYERS (/posteffect)\n")
            .append("Each layer below is a separate post effect. It is OFF until a command turns it on for a player, and grades on top of the always-on look.\n")
            .append("Needs cheats / operator permission (or a datapack function or command block).\n");
        for (var l : layers) readme.append("  ").append(l.name()).append(": /posteffect add @s ").append(effectId(l))
            .append("   (fade out: /posteffect remove @s ").append(effectId(l)).append(" then /posteffect add @s ").append(effectId(l)).append("_off)\n");
        readme.append("Turn everything off: /posteffect clear @s. Effects stay on across relogs (saved with the player).\n")
            .append("Fades follow game ticks: they pause with the game and do not run under /tick freeze. A window resize or resource reload restarts a fade-in.\n")
            .append("The companion datapack (Export datapack) adds /function shader_maker:menu with clickable toggles, plus on/off/toggle functions for command blocks.\n");
        files.put("README.txt", readme.toString());
    }

    private static String chain(String id, double fadeSeconds, boolean fadeOut) {
        var fade = "\"uniforms\":{\"CommandFade\":[{\"name\":\"FadeOut\",\"type\":\"float\",\"value\":" + (fadeOut ? 1 : 0)
            + "},{\"name\":\"FadeTicks\",\"type\":\"float\",\"value\":" + fadeSeconds * 20 + "}]}";
        var json = """
            {"targets":{"swap":{},"clock":{"width":1,"height":1,"persistent":true},"clock_next":{"width":1,"height":1}},
             "passes":[
              {"vertex_shader":"minecraft:core/screenquad","fragment_shader":"shader_maker:post/command_clock",
               "inputs":[{"sampler_name":"Prev","target":"clock"}],"output":"clock_next",%s},
              %s,
              {"vertex_shader":"minecraft:core/screenquad","fragment_shader":"shader_maker:post/command/%s",
               "inputs":[{"sampler_name":"In","target":"minecraft:main"},{"sampler_name":"Clock","target":"clock_next"}],"output":"swap",%s},
              %s
             ]}
            """.formatted(fade, PackCompiler.copyPass("clock_next", "clock"), id, fade, PackCompiler.copyPass("swap", "minecraft:main"));
        return new GsonBuilder().setPrettyPrinting().create().toJson(JsonParser.parseString(json));
    }

    static String fragment(Project.Layer l) {
        var s = new StringBuilder("""
            #version 330
            #extension GL_ARB_separate_shader_objects : require
            #include <minecraft:globals.glsl>
            uniform sampler2D InSampler;
            uniform sampler2D ClockSampler;
            layout(location = 0) in vec2 texCoord;
            layout(location = 0) out vec4 fragColor;
            """);
        s.append(SignalMemoryShaders.FADE_BLOCK).append(SignalMemoryShaders.PACK_GLSL).append(SignalMemoryShaders.FADE_GLSL);
        s.append(PackCompiler.gradeFunction(l.project(), "layer"));
        s.append("void main() {\n    vec4 source = texture(InSampler, texCoord);\n");
        s.append("    float cameraY = float(CameraBlockPos.y) - CameraOffset.y;\n").append(PackCompiler.CAMERA_XZ);
        s.append("    float weight = ").append(l.strength()).append(" * smCommandFade(texelFetch(ClockSampler, ivec2(0), 0))")
            .append(PackCompiler.placementWeight(l)).append(";\n");
        s.append("    fragColor = vec4(mix(source.rgb, grade_layer(source.rgb), clamp(weight, 0.0, 1.0)), source.a);\n}\n");
        return s.toString();
    }

    // ---- optional server datapack -------------------------------------------------------------

    /**
     * Helper functions for the command layers. Explicit separate download: enable it in a world
     * (datapacks folder) alongside the resource pack. Functions run at permission level 2.
     */
    public static Map<String, String> datapack(Project p) {
        var layers = layers(p);
        if (layers.isEmpty()) throw new IllegalArgumentException("No enabled command layers. Set a layer's trigger to Command first.");
        Map<String, String> files = new TreeMap<>();
        files.put("pack.mcmeta", "{\"pack\":{\"description\":\"Shader Maker command helpers · Java 26.3\",\"min_format\":[121,0],\"max_format\":[121,0]}}\n");
        files.put("data/minecraft/tags/function/load.json", "{\"values\":[\"shader_maker:load\"]}\n");
        files.put("data/minecraft/tags/function/tick.json", "{\"values\":[\"shader_maker:tick\"]}\n");
        var load = new StringBuilder("scoreboard objectives add sm.was_on dummy\n# Fade-out timers, one per command layer (ticks remaining).\n");
        var tick = new StringBuilder("# Remove finished fade-out effects so they stop costing a pass.\n");
        var menu = new StringBuilder("tellraw @s {\"text\":\"Shader Maker effects (click to toggle)\",\"color\":\"aqua\"}\n");
        for (int i = 0; i < layers.size(); i++) {
            var l = layers.get(i);
            String id = l.commandId(), effect = effectId(l), timer = "sm.fade" + i, tag = "sm.on." + id;
            int ticks = (int) Math.ceil(l.options().fadeSeconds() * 20) + 2;
            load.append("scoreboard objectives add ").append(timer).append(" dummy\n");
            tick.append("execute as @a[scores={").append(timer).append("=1}] run posteffect remove @s ").append(effect).append("_off\n")
                .append("scoreboard players remove @a[scores={").append(timer).append("=1..}] ").append(timer).append(" 1\n");
            files.put("data/shader_maker/function/on/" + id + ".mcfunction",
                "# Fade " + l.name() + " in for the executing player.\n"
                + "posteffect remove @s " + effect + "_off\nscoreboard players set @s " + timer + " 0\n"
                + "posteffect add @s " + effect + "\ntag @s add " + tag + "\n");
            // The player's real effect list is the truth: "posteffect remove" succeeds only if the effect was on.
            files.put("data/shader_maker/function/off/" + id + ".mcfunction",
                "# Fade " + l.name() + " out (if it is on), then remove its fade-out effect.\n"
                + "tag @s remove " + tag + "\n"
                + "execute store success score @s sm.was_on run posteffect remove @s " + effect + "\n"
                + "execute if score @s sm.was_on matches 0 run return 0\n"
                + "posteffect add @s " + effect + "_off\nscoreboard players set @s " + timer + " " + ticks + "\n");
            files.put("data/shader_maker/function/toggle/" + id + ".mcfunction",
                "# On if it is off, fade out if it is on.\n"
                + "execute store success score @s sm.was_on run posteffect remove @s " + effect + "\n"
                + "execute if score @s sm.was_on matches 0 run return run function shader_maker:on/" + id + "\n"
                + "tag @s remove " + tag + "\nposteffect add @s " + effect + "_off\nscoreboard players set @s " + timer + " " + ticks + "\n");
            menu.append("tellraw @s [{\"text\":\" [").append(json(l.name())).append("]\",\"color\":\"gold\",\"click_event\":{\"action\":\"run_command\",\"command\":\"/function shader_maker:toggle/")
                .append(id).append("\"},\"hover_event\":{\"action\":\"show_text\",\"value\":\"/function shader_maker:toggle/").append(id).append("\"}}]\n");
            files.put("data/shader_maker/function/give/" + id + ".mcfunction",
                "# A command block that toggles " + l.name() + " for the nearest player. Place it and add a button.\n"
                + "give @s minecraft:command_block[minecraft:custom_name=" + snbtText("Toggle " + l.name())
                + ",minecraft:block_entity_data={id:\"minecraft:command_block\",Command:\"execute as @p run function shader_maker:toggle/" + id + "\"}]\n");
        }
        menu.append("tellraw @s [{\"text\":\" [All off]\",\"color\":\"red\",\"click_event\":{\"action\":\"run_command\",\"command\":\"/function shader_maker:clear\"}}]\n");
        files.put("data/shader_maker/function/load.mcfunction", load.toString());
        files.put("data/shader_maker/function/tick.mcfunction", tick.toString());
        files.put("data/shader_maker/function/menu.mcfunction", menu.toString());
        var clear = new StringBuilder("posteffect clear @s\n");
        for (int i = 0; i < layers.size(); i++) clear.append("tag @s remove sm.on.").append(layers.get(i).commandId()).append("\nscoreboard players set @s sm.fade").append(i).append(" 0\n");
        files.put("data/shader_maker/function/clear.mcfunction", clear.toString());
        files.put("README.txt", "Shader Maker command helpers (datapack, Java 26.3)\n"
            + "Put this ZIP in <world>/datapacks and the matching resource pack in resourcepacks. Requires cheats / operator permission.\n"
            + "/function shader_maker:menu  - clickable toggles in chat\n"
            + "/function shader_maker:on/<id>, off/<id> (fades out), toggle/<id>, clear\n"
            + "/function shader_maker:give/<id>  - a command block preloaded with the toggle\n"
            + "Run them as a player (execute as <player> run function ...). Effects: "
            + String.join(", ", layers.stream().map(CommandEffects::effectId).toList()) + "\n"
            + "Biome or structure triggers: in your own tick function, e.g.\n"
            + "  execute as @a[tag=!sm.on.<id>] at @s if biome ~ ~ ~ minecraft:desert run function shader_maker:on/<id>\n"
            + "  execute as @a[tag=sm.on.<id>] at @s unless biome ~ ~ ~ minecraft:desert run function shader_maker:off/<id>\n");
        return Collections.unmodifiableMap(files);
    }

    private static String json(String text) { return text.replace("\\", "\\\\").replace("\"", "\\\""); }
    private static String snbtText(String text) { return "'" + text.replace("\\", "\\\\").replace("'", "\\'") + "'"; }
}
