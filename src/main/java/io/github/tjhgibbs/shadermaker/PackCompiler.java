package io.github.tjhgibbs.shadermaker;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Vanilla 26.3 post-effects. No feedback loops, textures or mod dependency in exported packs. */
public final class PackCompiler {
    private PackCompiler() {}
    public enum Activation { ALWAYS, COMMAND }
    public static Map<String, String> files(Project p, Activation activation) {
        Map<String, String> files = new TreeMap<>();
        files.put("pack.mcmeta", """
            {"pack":{"description":"Shader Maker · Java 26.3","min_format":[97,1],"max_format":[97,1]}}
            """);
        String effect = activation == Activation.ALWAYS ? "minecraft/post_effect/end_of_frame.json" : "shader_maker/post_effect/grade.json";
        files.put("assets/"+effect,"""
            {"targets":{"swap":{}},"passes":[
              {"vertex_shader":"minecraft:core/screenquad","fragment_shader":"shader_maker:post/grade",
               "inputs":[{"sampler_name":"In","target":"minecraft:main"}],"output":"swap"},
              {"vertex_shader":"minecraft:core/screenquad","fragment_shader":"minecraft:post/blit",
               "inputs":[{"sampler_name":"In","target":"swap"}],"output":"minecraft:main",
               "uniforms":{"BlitConfig":[{"name":"ColorModulate","type":"vec4","value":[1,1,1,1]}]}}
            ]}
            """);
        files.put("assets/shader_maker/shaders/post/grade.fsh",fragment(p));
        files.put("shader-maker-project.json",p.encode());
        files.put("README.txt", "Shader Maker 0.1 · Minecraft Java 26.3 only.\nFull-screen display-referred color grading, including HUD.\nInstall this ZIP in resourcepacks and enable it.\n" +
            (activation == Activation.ALWAYS ? "Applies automatically. Disable this pack to remove it. Other end_of_frame packs compete by priority.\n" :
                "Requires permission: /posteffect add @s shader_maker:grade\nRemove: /posteffect remove @s shader_maker:grade\n") +
            "No mod required. No source images included. OpenGL/Vulkan game validation must be recorded before distribution.\n");
        return Collections.unmodifiableMap(files);
    }
    public static String fragment(Project p) {
        if (p.layers().stream().anyMatch(Project.Layer::enabled))
            throw new IllegalArgumentException("Active grade layers require a live renderer; static packs cannot apply them");
        String shader = """
            #version 330
            #extension GL_ARB_separate_shader_objects : require
            uniform sampler2D InSampler;
            layout(location = 0) in vec2 texCoord;
            layout(location = 0) out vec4 fragColor;
            @curves
            vec3 levels(vec3 c) {
                c = max(vec3(0.0), (c * exp2(@exposure) - @black) / (@white - @black));
                vec3 soft = mix(c, vec3(0.75) + 0.25 * (1.0 - exp(-(c - 0.75) / 0.25)), greaterThan(c, vec3(0.75)));
                c = mix(c, soft, @shoulder);
                return clamp((pow(clamp(c, 0.0, 1.0), vec3(1.0 / @gamma)) - 0.5) * @contrast + 0.5, 0.0, 1.0);
            }
            void main() {
                vec4 source = texture(InSampler, texCoord);
                vec3 c = levels(source.rgb);
                c = vec3(curve_red(curve_master(c.r)), curve_green(curve_master(c.g)), curve_blue(curve_master(c.b)));
                float luma = dot(c, vec3(0.2126, 0.7152, 0.0722));
                float chroma = max(max(c.r,c.g),c.b) - min(min(c.r,c.g),c.b);
                float sat = @saturation * (1.0 + @vibrance * (1.0 - chroma));
                float edge = clamp(length(texCoord * 2.0 - 1.0) / sqrt(2.0), 0.0, 1.0);
                float shade = 1.0 - @vignette * edge * edge;
                c = clamp((vec3(luma) + (c - luma) * sat) * vec3(@red,@green,@blue) * shade, 0.0, 1.0);
                fragColor = vec4(mix(source.rgb, c, @opacity), source.a);
            }
            """;
        shader = shader.replace("@curves",p.curves().master().glsl("master")+p.curves().red().glsl("red")+p.curves().green().glsl("green")+p.curves().blue().glsl("blue"));
        for (Project.Control c : Project.CONTROLS) shader = shader.replace("@"+c.key(),Double.toString(p.get(c.key())));
        return shader;
    }
    public static void writeZip(Path target, Project project, Activation activation) throws IOException {
        Path tmp = temporary(target);
        try {
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(tmp),StandardCharsets.UTF_8)) {
                for (var file : files(project,activation).entrySet()) {
                    ZipEntry entry = new ZipEntry(file.getKey()); entry.setTimeLocal(java.time.LocalDateTime.of(1980,1,1,0,0));
                    zip.putNextEntry(entry); zip.write(file.getValue().getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
                }
            }
            replace(tmp,target);
        } finally { Files.deleteIfExists(tmp); }
    }
    public static void saveProject(Path target, Project project) throws IOException {
        Path tmp = temporary(target);
        try { Files.writeString(tmp,project.encode(),StandardCharsets.UTF_8); replace(tmp,target); }
        finally { Files.deleteIfExists(tmp); }
    }
    private static Path temporary(Path target) throws IOException {
        Files.createDirectories(target.toAbsolutePath().getParent());
        return Files.createTempFile(target.toAbsolutePath().getParent(),".shader-maker-",".tmp");
    }
    private static void replace(Path from, Path to) throws IOException {
        try { Files.move(from,to,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException e) { Files.move(from,to,StandardCopyOption.REPLACE_EXISTING); }
    }
}
