package io.github.tjhgibbs.shadermaker;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import com.google.gson.*;

/** Vanilla 26.3 post effects and optional core sky signal. No client runtime dependency. */
public final class PackCompiler {
    private PackCompiler() {}
    public enum Activation { ALWAYS, COMMAND }
    public static Map<String, String> files(Project p, Activation activation) {
        var problems=issues(p,activation);
        if(!problems.isEmpty())throw new IllegalArgumentException(String.join(" ",problems));
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
        if(usesVisualBiomes(p)) {
            String path="assets/"+effect;
            var chain=JsonParser.parseString(files.get(path)).getAsJsonObject();
            var env=new JsonObject();env.addProperty("width",1);env.addProperty("height",1);
            chain.getAsJsonObject("targets").add("environment",env);
            var passes=chain.getAsJsonArray("passes");
            var detector=JsonParser.parseString("""
                {"vertex_shader":"minecraft:core/screenquad","fragment_shader":"shader_maker:post/detect_environment",
                "inputs":[{"sampler_name":"In","target":"minecraft:main"},
                {"sampler_name":"Depth","target":"minecraft:main","use_depth_buffer":true}],"output":"environment"}
                """);
            var grade=passes.get(0).getAsJsonObject();
            grade.getAsJsonArray("inputs").add(JsonParser.parseString("{\"sampler_name\":\"SkyHeld\",\"target\":\"sky_next\"}"));
            grade.getAsJsonArray("inputs").add(JsonParser.parseString("{\"sampler_name\":\"Depth\",\"target\":\"minecraft:main\",\"use_depth_buffer\":true}"));
            var ordered=new JsonArray();ordered.add(detector);
            memory(chain,ordered,"sky",17,"environment","shader_maker:post/hold_sky");
            for(var pass:passes)ordered.add(pass);chain.add("passes",ordered);
            files.put(path,new GsonBuilder().setPrettyPrinting().create().toJson(chain));
            files.put("assets/minecraft/shaders/core/sky.fsh",VisualBiomeShaders.sky(p));
            files.put("assets/shader_maker/shaders/post/detect_environment.fsh",VisualBiomeShaders.detect());
            files.put("assets/shader_maker/shaders/post/hold_sky.fsh",SignalMemoryShaders.holdSky());
        }
        files.put("assets/shader_maker/shaders/post/grade.fsh",fragment(p));
        if(FogShaders.active(p)) {
            files.put("assets/minecraft/shaders/core/terrain.vsh",FogShaders.vertex());
            files.put("assets/minecraft/shaders/core/terrain.fsh",FogShaders.fragment(p));
        }
        if(WorldSignals.active(p)) {
            String path="assets/"+effect;
            var chain=JsonParser.parseString(files.get(path)).getAsJsonObject();
            var target=new JsonObject();target.addProperty("width",1);target.addProperty("height",1);
            chain.getAsJsonObject("targets").add("context",target);
            var passes=chain.getAsJsonArray("passes");
            var detector=JsonParser.parseString("""
                {"vertex_shader":"minecraft:core/screenquad","fragment_shader":"shader_maker:post/detect_context",
                "inputs":[{"sampler_name":"In","target":"minecraft:main"},
                {"sampler_name":"Depth","target":"minecraft:main","use_depth_buffer":true}],"output":"context"}
                """);
            for(var pass:passes)if(pass.getAsJsonObject().get("fragment_shader").getAsString().equals("shader_maker:post/grade")) {
                var inputs=pass.getAsJsonObject().getAsJsonArray("inputs");
                inputs.add(JsonParser.parseString("{\"sampler_name\":\"ContextHeld\",\"target\":\"context_next\"}"));
                if(!usesVisualBiomes(p))inputs.add(JsonParser.parseString("{\"sampler_name\":\"Depth\",\"target\":\"minecraft:main\",\"use_depth_buffer\":true}"));
            }
            var ordered=new JsonArray();ordered.add(detector);
            memory(chain,ordered,"context",5,"context","shader_maker:post/hold_context");
            for(var pass:passes)ordered.add(pass);chain.add("passes",ordered);
            files.put(path,new GsonBuilder().setPrettyPrinting().create().toJson(chain));
            files.put("assets/shader_maker/shaders/post/detect_context.fsh",WorldSignalShaders.detect());
            files.put("assets/shader_maker/shaders/post/hold_context.fsh",SignalMemoryShaders.holdContext());
            files.put("assets/minecraft/shaders/core/terrain.vsh",WorldSignalShaders.vertex(p));
            files.put("assets/minecraft/shaders/core/terrain.fsh",WorldSignalShaders.fragment(p));
            if(WorldSignals.lighting(p)) {
                files.put("assets/minecraft/shaders/core/lightmap.fsh",WorldSignalShaders.lightmap(p));
                files.put("assets/minecraft/shaders/include/sample_lightmap.glsl",WorldSignalShaders.sampleLightmap());
            }
        }
        files.put("shader-maker-project.json",p.encode());
        files.put("README.txt", "Shader Maker 0.1 · Minecraft Java 26.3 only.\nFull-screen display-referred color grading; ordered layers and camera-height conditions.\nInstall this ZIP in resourcepacks and enable it.\n" +
            (activation == Activation.ALWAYS ? "Applies automatically. Disable this pack to remove it. Other end_of_frame packs compete by priority.\n" :
                "Requires permission: /posteffect add @s shader_maker:grade\nRemove: /posteffect remove @s shader_maker:grade\n") +
            "No mod required. Camera height and area conditions follow the vanilla camera each frame. No source images included. OpenGL/Vulkan game validation must be recorded before distribution.\n");
        if(usesVisualBiomes(p))files.put("README.txt",files.get("README.txt")+
            "\nSKY COLOUR CONTEXT\n"+VisualBiomes.WARNING+"\n"+
            "This version senses normalized sky colour, not biome IDs. Set minecraft:visual/sky_color in a datapack to drive a custom signal. A 32x32 sample grid needs at least three unoccluded matching sky samples. While no sky is visible (looking at the ground, indoors, caves) the last seen sky colour is HELD; a newly seen colour cross-fades over about one second of game ticks. Entering a new area while looking down keeps the previous look until sky is seen. Blended borders, darkness, immersion, overlays and other shader packs can suppress or change detection.\n"+
            "Overrides assets/minecraft/shaders/core/sky.fsh; reserves opaque sky alpha as a signal, leaving its RGB unchanged. The post pass restores recognized sky alpha to 1. Core overrides also load in Command mode, even before command activation, and conflict with other sky overrides or uses of framebuffer alpha. No client runtime dependency. Minecraft OpenGL/Vulkan validation is still pending.\n"+
            p.layers().stream().filter(l->l.visualBiomeGroup()!=null).map(l->l.name()+": "+l.visualBiomeGroup()+" = "+String.join(", ",VisualBiomes.get(l.visualBiomeGroup()).biomes())+"\n").reduce("",String::concat));
        if(FogShaders.active(p))files.put("README.txt",files.get("README.txt")+
            "\nAUTHORED TERRAIN FOG (26.3 experimental)\n"+
            "Adds distance/height/volume fog to terrain, including native terrain colour variants. Entities, block entities, particles, sky, clouds and hand keep native fog. Minecraft device/OIT testing is pending.\n"+
            "Uses the resolved native FogColor, including datapack environment changes. No datapack is required. Native environmental/status/boundary fog remains after the authored medium; post grades affect both.\n"+
            "Overrides core/terrain.vsh and core/terrain.fsh. All active media add optical depth; ordering does not change the result. Disable the resource pack to remove fog.\n");
        if(WorldSignals.active(p))files.put("README.txt",files.get("README.txt")+
            "\nADDITIONAL CONTEXT CONTROLS\n"+WorldSignals.WARNING+"\n"+
            "Classifies resolved world Fog inputs, raw LightmapInfo colours and/or terrain vertex tint before the lightmap. No exact biome, water immersion or block-type identification. Camera signals do not require visible haze, skylight or torches. Grass/foliage/water tint is a visible-surface signal; translucent water cannot transport it.\n"+
            "Up to four distinct active sets of conditions. Conditions within a set use AND on the same terrain fragment; full-screen activation needs three matching samples. Full-screen results are held while no marked terrain is visible and cross-fade over about one second of game ticks. Blocks only uses the current fragment marker.\n"+
            "Solid and cutout opaque alpha carry checksummed condition masks; sorted translucency and OIT alpha are untouched. RGB is unchanged before grading. Lightmap alpha carries raw lighting classifications; the shared vanilla sampling helper restores alpha 1 for all native consumers. Conflicts with other terrain/lightmap/helper overrides or framebuffer-alpha uses. Core overrides load while the resource pack is enabled, including Command mode. Disable the pack to remove those overrides.\n"+
            p.layers().stream().filter(l->l.contextControls().active()).map(l->l.name()+": "+(l.contextControls().blocksOnly()?"Blocks only":"Full-screen")+"; "+l.contextControls().conditions().stream().map(WorldSignals.Condition::label).reduce((a,b)->a+"; "+b).orElse("any marked terrain")+"\n").reduce("",String::concat));
        CommandEffects.add(files,p);
        return Collections.unmodifiableMap(files);
    }
    public static List<String> issues(Project p,Activation activation) {
        var result=new ArrayList<>(issues(p));
        if(activation==Activation.COMMAND&&FogShaders.active(p))result.add("Terrain fog is active while its resource pack is loaded; /posteffect cannot switch it. Choose Auto pack or disable authored fog.");
        return List.copyOf(result);
    }
    /** Shared by Apply, Export and authoring diagnostics. Never flatten unsupported conditions. */
    public static List<String> issues(Project p) {
        var issues = new ArrayList<String>();
        if(WorldSignals.palette(p).size()>4)issues.add("Render context supports at most four distinct active condition sets. Reuse a set, clear conditions, or disable a layer.");
        if(VisualBiomes.palette(p).size()>16)issues.add("Sky colour conditions support at most 16 distinct active hues. Disable or merge a sky colour layer.");
        var skyPalette=VisualBiomes.palette(p);
        for(int i=0;i<skyPalette.size();i++)for(int j=i+1;j<skyPalette.size();j++)if(VisualBiomes.distance(skyPalette.get(i),skyPalette.get(j))<.000004)
            issues.add("Sky colours #"+String.format(Locale.ROOT,"%06x",skyPalette.get(i).rgb())+" and #"+String.format(Locale.ROOT,"%06x",skyPalette.get(j).rgb())+" are too close in hue to distinguish reliably. Use more distinct signals.");
        for (var layer : p.layers()) if (layer.enabled()) issues.addAll(layerIssues(layer));
        var ids=new HashMap<String,String>();
        for (var layer : p.layers()) if (layer.enabled()&&layer.options().command()) {
            var previous=ids.put(layer.commandId(),layer.name());
            if(previous!=null)issues.add("Command layers \""+previous+"\" and \""+layer.name()+"\" both become shader_maker:"+layer.commandId()+". Rename one.");
        }
        return List.copyOf(issues);
    }
    /** Capabilities of one layer, independent of whether it is currently enabled. */
    public static List<String> layerIssues(Project.Layer layer) {
        var issues = new ArrayList<String>();
        if (layer.altitude()!=null && (Math.abs(layer.altitude().min())>Float.MAX_VALUE || Math.abs(layer.altitude().max())>Float.MAX_VALUE || layer.altitude().feather()>Float.MAX_VALUE))
            issues.add(layer.name()+": camera-height values exceed the vanilla shader numeric range.");
        if (!layer.biomes().isEmpty()) issues.add(layer.name()+": exact biome IDs are unavailable; choose a Sky colour in Conditions to explicitly convert this legacy selector. Clear this condition or disable this layer.");
        if (layer.weather()!=null) issues.add(layer.name()+": rain / thunder conditions are not implemented for standalone packs. Clear this condition or disable this layer.");
        if (layer.lightning()!=null) issues.add(layer.name()+": visible-flash conditions are not implemented for standalone packs. Clear this condition or disable this layer.");
        if (layer.time()!=null) issues.add(layer.name()+": daylight conditions are not implemented for standalone packs; GameTime is elapsed time. Clear this condition or disable this layer.");
        if (layer.options().command()&&(layer.visualBiomeGroup()!=null||layer.contextControls().active()))
            issues.add(layer.name()+": command-switched layers support height and area conditions only. Remove the sky colour / render signal, or switch the layer back to automatic.");
        return List.copyOf(issues);
    }
    public static boolean usesVisualBiomes(Project p){return p.layers().stream().anyMatch(l->inMain(l)&&l.visualBiomeGroup()!=null);}
    /** Layers graded inside the always-on (or shader_maker:grade) chain; command layers export separately. */
    public static boolean inMain(Project.Layer l){return l.enabled()&&!l.options().command();}
    /** Adds a persistent memory target, its hold pass and the copy back. Grade samples {@code <name>_next}. */
    private static void memory(JsonObject chain,JsonArray passes,String name,int width,String current,String shader){
        var memory=new JsonObject();memory.addProperty("width",width);memory.addProperty("height",1);memory.addProperty("persistent",true);
        var next=new JsonObject();next.addProperty("width",width);next.addProperty("height",1);
        chain.getAsJsonObject("targets").add(name+"_memory",memory);chain.getAsJsonObject("targets").add(name+"_next",next);
        passes.add(JsonParser.parseString("{\"vertex_shader\":\"minecraft:core/screenquad\",\"fragment_shader\":\""+shader+"\",\"inputs\":[{\"sampler_name\":\"Current\",\"target\":\""+current+"\"},{\"sampler_name\":\"Prev\",\"target\":\""+name+"_memory\"}],\"output\":\""+name+"_next\"}"));
        passes.add(copyPass(name+"_next",name+"_memory"));
    }
    static JsonElement copyPass(String from,String to){
        return JsonParser.parseString("{\"vertex_shader\":\"minecraft:core/screenquad\",\"fragment_shader\":\"minecraft:post/blit\",\"inputs\":[{\"sampler_name\":\"In\",\"target\":\""+from+"\"}],\"output\":\""+to+"\",\"uniforms\":{\"BlitConfig\":[{\"name\":\"ColorModulate\",\"type\":\"vec4\",\"value\":[1,1,1,1]}]}}");
    }
    public static String fragment(Project p) {
        var issues = issues(p);
        if (!issues.isEmpty()) throw new IllegalArgumentException(String.join(" ", issues));
        boolean height = p.layers().stream().anyMatch(l -> inMain(l) && l.altitude()!=null);
        boolean area = p.layers().stream().anyMatch(l -> inMain(l) && l.options().area()!=null);
        boolean memory = usesVisualBiomes(p)||WorldSignals.active(p);
        var shader = new StringBuilder("""
            #version 330
            #extension GL_ARB_separate_shader_objects : require
            uniform sampler2D InSampler;
            layout(location = 0) in vec2 texCoord;
            layout(location = 0) out vec4 fragColor;
            """);
        if (height||area||memory) shader.append("#include <minecraft:globals.glsl>\n");
        if (memory) shader.append(SignalMemoryShaders.PACK_GLSL);
        if(usesVisualBiomes(p)||WorldSignals.active(p))shader.append("uniform sampler2D DepthSampler;\n");
        if(usesVisualBiomes(p))shader.append("uniform sampler2D SkyHeldSampler;\n").append(VisualBiomeShaders.decodeGlsl());
        if(WorldSignals.active(p))shader.append("uniform sampler2D ContextHeldSampler;\n").append(WorldSignalShaders.decode());
        shader.append(gradeFunction(p, "base"));
        for (int i=0;i<p.layers().size();i++) if(inMain(p.layers().get(i)))
            shader.append(gradeFunction(p.layers().get(i).project(), "layer"+i));
        shader.append("void main() {\n    vec4 source = texture(InSampler, texCoord);\n    vec3 result = grade_base(source.rgb);\n");
        if(usesVisualBiomes(p))shader.append("    if (skyMarker(source.a)>=0 && skyDepth(texture(DepthSampler,texCoord).r)) source.a=1.0;\n");
        if(WorldSignals.active(p))shader.append("    int smPixelMask = smTerrainMask(source,texture(DepthSampler,texCoord).r);\n    if(smPixelMask>=0) source.a=1.0;\n");
        if (height) shader.append("    float cameraY = float(CameraBlockPos.y) - CameraOffset.y;\n");
        if (area) shader.append(CAMERA_XZ);
        for (int i=0;i<p.layers().size();i++) {
            var l=p.layers().get(i); if(!inMain(l)) continue;
            String weight=Double.toString(l.strength());
            // Held, cross-faded sky weight: survives frames with no visible sky.
            if(l.visualBiomeGroup()!=null)weight+=" * smoothstep(0.0, 1.0, smUnpack16(texelFetch(SkyHeldSampler,ivec2("+VisualBiomes.code(p,l.visualBiomeGroup())/15+",0),0).rg))";
            if(l.contextControls().active()) {
                int signal=WorldSignals.index(p,l.contextControls());
                weight+=" * "+(l.contextControls().blocksOnly()?"(smPixelMask >= 0 && (smPixelMask & "+(1<<signal)+") != 0 ? 1.0 : 0.0)":"smoothstep(0.0, 1.0, smUnpack16(texelFetch(ContextHeldSampler,ivec2("+(signal+1)+",0),0).rg))");
            }
            weight+=placementWeight(l);
            shader.append("    result = mix(result, grade_layer"+i+"(source.rgb), clamp("+weight+", 0.0, 1.0));\n");
        }
        return shader.append("    fragColor = vec4(result, source.a);\n}\n").toString();
    }
    static final String CAMERA_XZ="    float cameraX = float(CameraBlockPos.x) - CameraOffset.x;\n    float cameraZ = float(CameraBlockPos.z) - CameraOffset.z;\n";
    /** Height and area factors, matching Atmosphere.Range and Project.Area (inclusive, inward smooth feather). */
    static String placementWeight(Project.Layer l){
        String weight="";
        if(l.altitude()!=null) {
            var a=l.altitude();
            weight+=" * (cameraY < "+a.min()+" || cameraY > "+a.max()+" ? 0.0 : "+
                (a.feather()==0?"1.0":"smoothstep(0.0, 1.0, min((cameraY - "+a.min()+") / "+a.feather()+", ("+a.max()+" - cameraY) / "+a.feather()+"))")+")";
        }
        var b=l.options().area();
        if(b!=null) {
            weight+=" * (cameraX < "+b.minX()+" || cameraX > "+b.maxX()+" || cameraZ < "+b.minZ()+" || cameraZ > "+b.maxZ()+" ? 0.0 : "+
                (b.feather()==0?"1.0":"smoothstep(0.0, 1.0, min(min(cameraX - "+b.minX()+", "+b.maxX()+" - cameraX), min(cameraZ - "+b.minZ()+", "+b.maxZ()+" - cameraZ)) / "+b.feather()+")")+")";
        }
        return weight;
    }
    static String gradeFunction(Project p, String id) {
        String shader = """
            @curves
            vec3 levels(vec3 c) {
                c = max(vec3(0.0), (c * exp2(@exposure) - @black) / (@white - @black));
                vec3 soft = mix(c, vec3(0.75) + 0.25 * (1.0 - exp(-(c - 0.75) / 0.25)), greaterThan(c, vec3(0.75)));
                c = mix(c, soft, @shoulder);
                return clamp((pow(clamp(c, 0.0, 1.0), vec3(1.0 / @gamma)) - 0.5) * @contrast + 0.5, 0.0, 1.0);
            }
            vec3 grade(vec3 source) {
                vec3 c = levels(source);
                c = vec3(curve_red(curve_master(c.r)), curve_green(curve_master(c.g)), curve_blue(curve_master(c.b)));
                float luma = dot(c, vec3(0.2126, 0.7152, 0.0722));
                float chroma = max(max(c.r,c.g),c.b) - min(min(c.r,c.g),c.b);
                float sat = @saturation * (1.0 + @vibrance * (1.0 - chroma));
                float edge = clamp(length(texCoord * 2.0 - 1.0) / sqrt(2.0), 0.0, 1.0);
                float shade = 1.0 - @vignette * edge * edge;
                c = clamp((vec3(luma) + (c - luma) * sat) * vec3(@red,@green,@blue) * shade, 0.0, 1.0);
                return mix(source, c, @opacity);
            }
            """;
        shader = shader.replace("@curves",p.curves().master().glsl("master")+p.curves().red().glsl("red")+p.curves().green().glsl("green")+p.curves().blue().glsl("blue"));
        for (Project.Control c : Project.CONTROLS) shader = shader.replace("@"+c.key(),Double.toString(p.get(c.key())));
        return shader.replace("curve_", "curve_"+id+"_").replace("levels(", "levels_"+id+"(").replace("vec3 grade(", "vec3 grade_"+id+"(");
    }
    public static void writeZip(Path target, Project project, Activation activation) throws IOException {
        writeFiles(target, files(project,activation));
    }
    /** Deterministic ZIP of generated text files (resource pack or datapack). */
    public static void writeFiles(Path target, Map<String,String> files) throws IOException {
        Path tmp = temporary(target);
        try {
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(tmp),StandardCharsets.UTF_8)) {
                for (var file : new TreeMap<>(files).entrySet()) {
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
