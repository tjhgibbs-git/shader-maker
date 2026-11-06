package io.github.tjhgibbs.shadermaker;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.mojang.renderpearl.api.pipeline.*;
import com.mojang.renderpearl.frontend.shaders.SPIRVModule;
import org.lwjgl.system.MemoryUtil;
import static org.lwjgl.util.shaderc.Shaderc.*;
import net.minecraft.client.renderer.PostChainConfig;
import net.minecraft.client.renderer.ShaderDefines;
import net.minecraft.resources.Identifier;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Actual game codec and SPIR-V reflection, plus native ShaderC using the game's target/options. No graphics device. */
public final class MinecraftContractTest {
    private static SPIRVModule compile(String source,ShaderType type,boolean zeroToOne) throws Exception {
        long compiler=shaderc_compiler_initialize(),options=shaderc_compile_options_initialize(),result=0;
        try {
            shaderc_compile_options_set_target_env(options,shaderc_target_env_vulkan,shaderc_env_version_vulkan_1_3);
            shaderc_compile_options_set_auto_bind_uniforms(options,true);
            shaderc_compile_options_set_preserve_bindings(options,false);
            shaderc_compile_options_set_generate_debug_info(options);
            shaderc_compile_options_set_optimization_level(options,shaderc_optimization_level_zero);
            if(zeroToOne)shaderc_compile_options_add_macro_definition(options,"RENDERPEARL_DEPTH_IS_ZERO_TO_ONE","");
            result=shaderc_compile_into_spv(compiler,source,type==ShaderType.FRAGMENT?shaderc_fragment_shader:shaderc_vertex_shader,"shader-maker-test","main",options);
            if(shaderc_result_get_compilation_status(result)!=shaderc_compilation_status_success)throw new AssertionError(shaderc_result_get_error_message(result));
            var spv=shaderc_result_get_bytes(result);
            return new SPIRVModule(MemoryUtil.memAlloc(spv.remaining()).put(spv).flip(),type);
        } finally {if(result!=0)shaderc_result_release(result);shaderc_compile_options_release(options);shaderc_compiler_release(compiler);}
    }
    private static String includes(String source) throws Exception {
        var matcher=java.util.regex.Pattern.compile("#include <minecraft:([^>]+)>").matcher(source);var result=new StringBuffer();
        while(matcher.find())try(var stream=MinecraftContractTest.class.getResourceAsStream("/assets/minecraft/shaders/include/"+matcher.group(1))) {
            if(stream==null)throw new AssertionError("Missing vanilla include");
            matcher.appendReplacement(result,java.util.regex.Matcher.quoteReplacement(includes(new String(stream.readAllBytes(),StandardCharsets.UTF_8))));
        }
        matcher.appendTail(result);return result.toString();
    }
    public static void main(String[] args) throws Exception {
        List<Project> projects=new ArrayList<>();for(String name:Project.PRESETS)projects.add(Project.preset(name));
        List<Curves.Point> points=new ArrayList<>();for(int i=0;i<16;i++)points.add(new Curves.Point(i/15.0,Math.pow(i/15.0,.7)));
        var curve=new Curves.Curve(points);
        projects.add(Project.neutral().withCurve(0,curve).withCurve(1,curve).withCurve(2,curve).withCurve(3,curve).with("vibrance",-.4));
        projects.add(Project.neutral().withLayers(List.of(
            new Project.Layer("Height feather",Project.preset("Moonlit"),true,.65,Set.of(),new Atmosphere.Range(-64,100,12),null),
            new Project.Layer("Hard edge",Project.neutral().withCurve(0,curve),true,.3,Set.of(),new Atmosphere.Range(100,100,0),null),
            new Project.Layer("Top",Project.preset("Golden dusk"),true,.2,Set.of(),null,null))));
        var group=VisualBiomes.forBiome("minecraft:plains").orElseThrow();
        projects.add(Project.neutral().withLayers(List.of(
            new Project.Layer("Shared sky",Project.preset("Moonlit"),true,.5,Set.of(),new Atmosphere.Range(-64,100,10),null,null,null,group.id()),
            new Project.Layer("Other sky",Project.preset("Golden dusk"),true,.3,Set.of(),null,null,null,null,VisualBiomes.forBiome("minecraft:forest").orElseThrow().id()))));
        for(var family:VisualBiomes.GROUPS)for(var biome:family.biomes())try(var resource=MinecraftContractTest.class.getResourceAsStream("/data/minecraft/worldgen/biome/"+biome.substring("minecraft:".length())+".json")) {
            if(resource==null)throw new AssertionError("Missing pinned biome");
            String actual=JsonParser.parseString(new String(resource.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("attributes").get("minecraft:visual/sky_color").getAsString();
            if(Integer.parseInt(actual.substring(1),16)!=family.rgb())throw new AssertionError("Pinned sky group drifted: "+biome);
        }
        int compiled=0,decoded=0;
        for(Project project:projects) {
            for(var activation:PackCompiler.Activation.values()) {
                var files=PackCompiler.files(project,activation);
                String definition=files.entrySet().stream().filter(e->e.getKey().contains("post_effect/")).findFirst().orElseThrow().getValue();
                var config=PostChainConfig.CODEC.parse(JsonOps.INSTANCE,JsonParser.parseString(definition)).getOrThrow();
                if(config.passes().size()!=(PackCompiler.usesVisualBiomes(project)?5:2)||config.internalTargets().size()!=(PackCompiler.usesVisualBiomes(project)?4:1))throw new AssertionError("Invalid pass topology");decoded++;
            }
            for(boolean zeroToOne:List.of(false,true)) {
                String fragment=includes(PackCompiler.fragment(project));
                try(var module=compile(fragment,ShaderType.FRAGMENT,zeroToOne)) {
                    if(module.spv().remaining()==0)throw new AssertionError("Empty SPIR-V");
                    var reflection=module.reflect();
                    if(reflection.inputs().size()!=1||reflection.outputs().size()!=1)throw new AssertionError("Shader interface mismatch");
                    compiled++;
                }
            }
            if(PackCompiler.usesVisualBiomes(project))for(boolean depth:List.of(false,true)) {
                for(String source:List.of(VisualBiomeShaders.sky(),VisualBiomeShaders.detect()))try(var module=compile(includes(source),ShaderType.FRAGMENT,depth)){module.reflect();compiled++;}
                try(var stream=MinecraftContractTest.class.getResourceAsStream("/assets/minecraft/shaders/core/sky.vsh")){
                    try(var module=compile(includes(new String(stream.readAllBytes(),StandardCharsets.UTF_8)),ShaderType.VERTEX,depth)){module.reflect();compiled++;}
                }
            }
        }
        try(var stream=MinecraftContractTest.class.getResourceAsStream("/assets/minecraft/shaders/core/screenquad.vsh")){
            if(stream==null)throw new AssertionError("Missing vanilla shader");String vertex=new String(stream.readAllBytes(),StandardCharsets.UTF_8);
            try(var module=compile(vertex,ShaderType.VERTEX,false)){
                if(module.reflect().outputs().size()!=1)throw new AssertionError("Vanilla vertex output changed");
            }
        }
        compiled+=fogContracts();
        compiled+=commandContracts();
        compiled+=contextContracts();
        System.out.println("MinecraftContractTest: "+decoded+" actual post-chain decodes; "+compiled+" native ShaderC compiles / Minecraft SPIR-V reflections passed");
    }
    private static String exportedIncludes(String source,Map<String,String> files)throws Exception {
        var matcher=java.util.regex.Pattern.compile("#include <minecraft:([^>]+)>").matcher(source);var result=new StringBuffer();
        while(matcher.find()) {
            String include=files.get("assets/minecraft/shaders/include/"+matcher.group(1));
            if(include==null)try(var stream=MinecraftContractTest.class.getResourceAsStream("/assets/minecraft/shaders/include/"+matcher.group(1))){if(stream==null)throw new AssertionError("Missing include");include=new String(stream.readAllBytes(),StandardCharsets.UTF_8);}
            matcher.appendReplacement(result,java.util.regex.Matcher.quoteReplacement(exportedIncludes(include,files)));
        }
        matcher.appendTail(result);return result.toString();
    }
    private static int contextContracts()throws Exception {
        var projects=new ArrayList<Project>();
        for(var source:WorldSignals.Source.values()) {
            var c=source.colour()?WorldSignals.Condition.colour(source,0x80a0ff,.05,true):WorldSignals.Condition.range(source,-8,1024);
            projects.add(Project.neutral().withLayers(List.of(WorldSignalsTest.layer(new WorldSignals.Controls(List.of(c),false),true))));
        }
        var combined=WorldSignalsTest.fixture().withFog(List.of(Fog.create(Fog.Shape.EVERYWHERE)));
        var layers=new ArrayList<>(combined.layers());
        layers.add(new Project.Layer("Sky and blocks",Project.preset("Moonlit"),true,.5,Set.of(),null,null,null,null,"custom_ff8040",new WorldSignals.Controls(List.of(),true)));
        layers.add(WorldSignalsTest.layer(new WorldSignals.Controls(List.of(WorldSignals.Condition.colour(WorldSignals.Source.SURFACE_TINT,0x408020,.1,true)),false),true));
        projects.add(combined.withLayers(layers));
        int count=0,decodes=0;
        for(var project:projects) {
            var files=PackCompiler.files(project,PackCompiler.Activation.ALWAYS);
            for(var activation:PackCompiler.Activation.values())if(activation==PackCompiler.Activation.ALWAYS||!FogShaders.active(project)) {
                var resources=PackCompiler.files(project,activation);
                var definition=resources.entrySet().stream().filter(e->e.getKey().contains("post_effect/")).findFirst().orElseThrow().getValue();
                var config=PostChainConfig.CODEC.parse(JsonOps.INSTANCE,JsonParser.parseString(definition)).getOrThrow();
                if(config.passes().size()!=(PackCompiler.usesVisualBiomes(project)?8:5))throw new AssertionError("Context pass order");
                decodes++;
            }
            String common="#define OIT\n#define OIT_COEFF_COUNT 8\n#define OIT_WAVELET_RANK 2\n#define OIT_COEFF_ATTACHMENT_COUNT 2\n";
            for(String variant:List.of("", "#define ALPHA_CUTOUT 0.5\n", "#define ALPHA_CUTOUT 0.1\n", common+"#define OIT_ACCUMULATE\n#define ALPHA_CUTOUT 0.1\n",common+"#define OIT_ALPHA_ONLY\n#define OIT_DEPTH_BOUNDS\n",common+"#define OIT_ALPHA_ONLY\n#define OIT_TRANSMITTANCE\n"))for(boolean multi:List.of(false,true))for(boolean depth:List.of(false,true)) {
                String defines=variant+(multi?"#define MULTIDRAW_TERRAIN\n":"");
                for(var type:List.of(ShaderType.VERTEX,ShaderType.FRAGMENT)) {
                    String source=files.get("assets/minecraft/shaders/core/terrain."+(type==ShaderType.VERTEX?"vsh":"fsh"));
                    try(var module=compile(exportedIncludes(source,files).replace("#version 330","#version 330\n"+defines),type,depth)){module.reflect();count++;}
                }
            }
            for(var entry:files.entrySet())if(entry.getKey().endsWith(".fsh")&&!entry.getKey().endsWith("terrain.fsh"))for(boolean depth:List.of(false,true))try(var module=compile(exportedIncludes(entry.getValue(),files),ShaderType.FRAGMENT,depth)){module.reflect();count++;}
        }
        // A helper override is safe only while every native lightmap consumer goes through it.
        for(String name:List.of("block","entity","item","particle","rendertype_leash","terrain","text"))try(var stream=MinecraftContractTest.class.getResourceAsStream("/assets/minecraft/shaders/core/"+name+".vsh")){
            String source=new String(stream.readAllBytes(),StandardCharsets.UTF_8);
            if(!source.contains("sample_lightmap(Sampler2, UV2)"))throw new AssertionError("Lightmap consumer drifted: "+name);
        }
        System.out.println("Context native contracts: "+decodes+" post chains and "+count+" ShaderC cases across all signals, scopes, terrain/OIT variants, sky and authored fog");
        return count;
    }

    /** Command-switched effects: real PostChainConfig decode of every effect and native compiles of every shader. */
    private static int commandContracts()throws Exception{
        var project=CommandLayersTest.fixture();
        int count=0,decodes=0;
        for(var activation:PackCompiler.Activation.values()){
            var files=PackCompiler.files(project,activation);
            for(var e:files.entrySet()){
                if(e.getKey().startsWith("assets/shader_maker/post_effect/")||e.getKey().startsWith("assets/minecraft/post_effect/")){
                    var config=PostChainConfig.CODEC.parse(JsonOps.INSTANCE,JsonParser.parseString(e.getValue())).getOrThrow();
                    if(e.getKey().contains("/post_effect/dusk")&&(config.passes().size()!=4||!config.internalTargets().get(net.minecraft.resources.Identifier.parse("clock")).persistent()))throw new AssertionError("Command chain topology");
                    decodes++;
                }
                if(e.getKey().endsWith(".fsh"))for(boolean depth:List.of(false,true))try(var module=compile(exportedIncludes(e.getValue(),files),ShaderType.FRAGMENT,depth)){module.reflect();count++;}
            }
        }
        System.out.println("Command/memory native contracts: "+decodes+" post chains and "+count+" ShaderC cases (command fades, held sky/context memory)");
        return count;
    }

    private static int fogContracts()throws Exception{
        var objects=new ArrayList<Fog>();
        for(var shape:Fog.Shape.values())objects.add(Fog.create(shape).with("fade",2));
        objects.add(Fog.create(Fog.Shape.EVERYWHERE).falloff(Fog.Falloff.LINEAR).with("start",-8).altitude(new Atmosphere.Range(-64,100,12)));
        objects.add(Fog.create(Fog.Shape.BOX).samples(8));objects.add(Fog.create(Fog.Shape.SPHERE).with("fade",3).samples(32));
        var project=Project.neutral().withFog(objects);
        var files=PackCompiler.files(project,PackCompiler.Activation.ALWAYS);
        String common="#define OIT\n#define OIT_COEFF_COUNT 8\n#define OIT_WAVELET_RANK 2\n#define OIT_COEFF_ATTACHMENT_COUNT 2\n";
        var variants=List.of("", "#define ALPHA_CUTOUT 0.1\n", common+"#define OIT_ACCUMULATE\n",common+"#define OIT_ALPHA_ONLY\n#define OIT_DEPTH_BOUNDS\n",common+"#define OIT_ALPHA_ONLY\n#define OIT_TRANSMITTANCE\n");
        int count=0;
        for(String variant:variants)for(boolean multi:List.of(false,true))for(boolean depth:List.of(false,true)){
            String defines=variant+(multi?"#define MULTIDRAW_TERRAIN\n":"");
            for(var type:List.of(ShaderType.VERTEX,ShaderType.FRAGMENT)){
                String source=files.get("assets/minecraft/shaders/core/terrain."+(type==ShaderType.VERTEX?"vsh":"fsh"));
                try(var module=compile(includes(source).replace("#version 330","#version 330\n"+defines),type,depth)){
                    var reflection=module.reflect();
                    if(type==ShaderType.VERTEX&&reflection.outputs().size()!=6)throw new AssertionError("Fog vertex varyings drifted");
                    if(type==ShaderType.FRAGMENT&&reflection.inputs().size()!=6)throw new AssertionError("Fog fragment varyings drifted");
                    count++;
                }
            }
        }
        var layer=new Project.Layer("Custom sky",Project.neutral(),true,1,Set.of(),null,null,null,null,"custom_ff8040");
        var custom=project.withLayers(List.of(layer));
        for(boolean depth:List.of(false,true))for(String source:List.of(VisualBiomeShaders.sky(custom),PackCompiler.fragment(custom)))try(var module=compile(includes(source),ShaderType.FRAGMENT,depth)){module.reflect();count++;}
        System.out.println("Fog native contracts: "+count+" vertex/fragment/sky cases, both depth conventions, MULTIDRAW, cutout and OIT colour/depth/transmittance");
        return count;
    }

}
