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
    public static void main(String[] args) throws Exception {
        List<Project> projects=new ArrayList<>();for(String name:Project.PRESETS)projects.add(Project.preset(name));
        List<Curves.Point> points=new ArrayList<>();for(int i=0;i<16;i++)points.add(new Curves.Point(i/15.0,Math.pow(i/15.0,.7)));
        var curve=new Curves.Curve(points);
        projects.add(Project.neutral().withCurve(0,curve).withCurve(1,curve).withCurve(2,curve).withCurve(3,curve).with("vibrance",-.4));
        int compiled=0,decoded=0;
        for(Project project:projects) {
            for(var activation:PackCompiler.Activation.values()) {
                var files=PackCompiler.files(project,activation);
                String definition=files.entrySet().stream().filter(e->e.getKey().contains("post_effect/")).findFirst().orElseThrow().getValue();
                var config=PostChainConfig.CODEC.parse(JsonOps.INSTANCE,JsonParser.parseString(definition)).getOrThrow();
                if(config.passes().size()!=2||config.internalTargets().size()!=1)throw new AssertionError("Invalid pass topology");decoded++;
            }
            for(boolean zeroToOne:List.of(false,true)) {
                try(var module=compile(PackCompiler.fragment(project),ShaderType.FRAGMENT,zeroToOne)) {
                    if(module.spv().remaining()==0)throw new AssertionError("Empty SPIR-V");
                    var reflection=module.reflect();
                    if(reflection.inputs().size()!=1||reflection.outputs().size()!=1)throw new AssertionError("Shader interface mismatch");
                    compiled++;
                }
            }
        }
        try(var stream=MinecraftContractTest.class.getResourceAsStream("/assets/minecraft/shaders/core/screenquad.vsh")){
            if(stream==null)throw new AssertionError("Missing vanilla shader");String vertex=new String(stream.readAllBytes(),StandardCharsets.UTF_8);
            try(var module=compile(vertex,ShaderType.VERTEX,false)){
                if(module.reflect().outputs().size()!=1)throw new AssertionError("Vanilla vertex output changed");
            }
        }
        System.out.println("MinecraftContractTest: "+decoded+" actual post-chain decodes; "+compiled+" native ShaderC compiles / Minecraft SPIR-V reflections passed");
    }
}
