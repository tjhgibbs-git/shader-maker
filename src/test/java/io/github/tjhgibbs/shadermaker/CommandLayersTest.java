package io.github.tjhgibbs.shadermaker;

import com.google.gson.*;
import java.util.*;

/** Area conditions, command-switched layers, the helper datapack and held signal memory. */
public final class CommandLayersTest {
    static int checks;
    static void check(boolean v,String label){if(!v)throw new AssertionError(label);checks++;}
    static void rejects(Runnable r,String label){try{r.run();}catch(IllegalArgumentException e){checks++;return;}throw new AssertionError("Expected rejection: "+label);}
    static Project.Layer layer(String name,Project grade,Atmosphere.Range y,String sky,WorldSignals.Controls signals,Project.Options options){
        return new Project.Layer(name,grade,true,1,Set.of(),y,null,null,null,sky,signals,options);
    }
    /** Automatic sky, render-signal and area layers plus two command layers. */
    public static Project fixture(){
        var area=new Project.Area(-100,100,-50,50,8);
        return Project.neutral().withLayers(List.of(
            layer("Sky",Project.preset("Moonlit"),null,"custom_ff8040",WorldSignals.Controls.NONE,Project.Options.NONE),
            layer("Fog signal",Project.preset("Cold mist"),null,null,new WorldSignals.Controls(List.of(WorldSignals.Condition.colour(WorldSignals.Source.NIGHT_VISION_COLOR,0x123456,.01,false)),false),Project.Options.NONE),
            layer("Arena",Project.preset("Golden dusk"),null,null,WorldSignals.Controls.NONE,Project.Options.NONE.withArea(area)),
            layer("Dusk",Project.preset("Golden dusk"),new Atmosphere.Range(-64,320,4),null,WorldSignals.Controls.NONE,new Project.Options(area,true,2.5)),
            layer("Danger red!",Project.neutral().with("red",1.6).with("vignette",.6),null,null,WorldSignals.Controls.NONE,new Project.Options(null,true,0))));
    }
    public static void main(String[] args){
        var p=fixture();
        // Persistence
        check(p.json().get("version").getAsInt()==10,"v10 when options are used");
        for(int i=0;i<p.layers().size();i++)check(Project.decode(p.encode()).layers().get(i).options().equals(p.layers().get(i).options()),"v10 options round trip "+i);
        check(Project.decode(p.encode()).encode().equals(p.encode()),"stable encoding");
        check(WorldSignalsTest.fixture().json().get("version").getAsInt()==9,"older features keep v9");
        var v9=p.json();v9.addProperty("version",9);
        rejects(()->Project.decode(v9.toString()),"area in v9");
        rejects(()->new Project.Area(5,1,0,0,0),"inverted area");
        rejects(()->new Project.Area(0,1,0,1,-1),"negative feather");
        rejects(()->new Project.Area(Double.NaN,1,0,1,0),"NaN area");
        rejects(()->new Project.Options(null,true,31),"fade too long");
        // Area weights
        var a=new Project.Area(-100,100,-50,50,8);
        check(a.weight(0,0)==1,"centre");check(a.weight(-100,0)==0,"edge inside feather is 0");check(a.weight(-101,0)==0,"outside");
        check(a.weight(-96,0)>0&&a.weight(-96,0)<1,"feathered");check(new Project.Area(0,0,0,0,0).weight(0,0)==1,"inclusive hard edge");
        var ctx=new Atmosphere.Context(6000,64,0,"minecraft:plains",0,0,0,0,WorldSignals.Samples.EMPTY,0,0,false);
        var arena=p.layers().get(2);
        check(arena.weight(ctx)==1,"preview area");
        check(arena.weight(new Atmosphere.Context(6000,64,0,"minecraft:plains",0,0,0,0,WorldSignals.Samples.EMPTY,500,0,false))==0,"preview outside area");
        var dusk=p.layers().get(3);
        check(dusk.weight(ctx)==0,"command layer off in preview by default");
        check(dusk.weight(new Atmosphere.Context(6000,64,0,"minecraft:plains",0,0,0,0,WorldSignals.Samples.EMPTY,0,0,true))==1,"command layer on when simulated");
        // Slugs and issues
        check(Project.Options.slug("Danger red!").equals("danger_red"),"slug");check(Project.Options.slug("!!!").equals("layer"),"empty slug");
        check(PackCompiler.issues(p).isEmpty(),"fixture exports: "+PackCompiler.issues(p));
        var dup=new ArrayList<>(p.layers());dup.add(dup.get(4).withName("Danger  red"));
        check(PackCompiler.issues(p.withLayers(dup)).stream().anyMatch(s->s.contains("Rename one")),"duplicate command ids");
        var bad=new ArrayList<>(p.layers());bad.set(0,bad.get(0).withOptions(Project.Options.NONE.withCommand(true)));
        check(PackCompiler.issues(p.withLayers(bad)).stream().anyMatch(s->s.contains("height and area conditions only")),"command + sky rejected");
        // Compiler: command layers leave the always-on chain
        for(var mode:PackCompiler.Activation.values()){
            var files=PackCompiler.files(p,mode);
            String main=files.get("assets/shader_maker/shaders/post/grade.fsh");
            check(main.contains("grade_layer2(")&&!main.contains("grade_layer3(")&&!main.contains("grade_layer4("),"command layers not in main grade "+mode);
            check(main.contains("cameraX")&&main.contains("-100.0"),"area GLSL");
            check(main.contains("SkyHeldSampler")&&main.contains("ContextHeldSampler"),"held memory used");
            for(String id:List.of("dusk","danger_red")){
                check(files.containsKey("assets/shader_maker/post_effect/"+id+".json"),"effect "+id);
                check(files.containsKey("assets/shader_maker/post_effect/"+id+"_off.json"),"fade-out "+id);
                check(files.containsKey("assets/shader_maker/shaders/post/command/"+id+".fsh"),"shader "+id);
            }
            var chain=JsonParser.parseString(files.get("assets/shader_maker/post_effect/dusk.json")).getAsJsonObject();
            check(chain.getAsJsonObject("targets").getAsJsonObject("clock").get("persistent").getAsBoolean(),"persistent clock");
            check(chain.toString().contains("\"FadeTicks\"")&&chain.toString().contains("50.0"),"fade ticks uniform");
            String effectPath=mode==PackCompiler.Activation.ALWAYS?"assets/minecraft/post_effect/end_of_frame.json":"assets/shader_maker/post_effect/grade.json";
            var targets=JsonParser.parseString(files.get(effectPath)).getAsJsonObject().getAsJsonObject("targets");
            check(targets.getAsJsonObject("sky_memory").get("persistent").getAsBoolean()&&targets.getAsJsonObject("sky_memory").get("width").getAsInt()==17,"sky memory");
            check(targets.getAsJsonObject("context_memory").get("persistent").getAsBoolean()&&targets.getAsJsonObject("context_memory").get("width").getAsInt()==5,"context memory");
            check(files.get("README.txt").contains("/posteffect add @s shader_maker:dusk"),"readme command");
            String fragment=files.get("assets/shader_maker/shaders/post/command/danger_red.fsh");
            check(fragment.contains("smCommandFade")&&!fragment.contains("cameraX <"),"command shader without area");
        }
        // Projects without command layers or signals are unchanged in shape.
        var plain=PackCompiler.files(Project.preset("Moonlit"),PackCompiler.Activation.ALWAYS);
        check(plain.keySet().stream().noneMatch(k->k.contains("command")||k.contains("hold_")),"no extra resources when unused");
        // Helper datapack
        var data=CommandEffects.datapack(p);
        check(data.get("pack.mcmeta").contains("[121,0]"),"26.3 data format");
        for(String f:List.of("menu","clear","load","tick","on/dusk","off/dusk","toggle/dusk","give/dusk","on/danger_red"))check(data.containsKey("data/shader_maker/function/"+f+".mcfunction"),"function "+f);
        check(data.get("data/shader_maker/function/on/dusk.mcfunction").contains("posteffect add @s shader_maker:dusk"),"on adds effect");
        check(data.get("data/shader_maker/function/off/dusk.mcfunction").contains("posteffect add @s shader_maker:dusk_off"),"off fades out");
        check(data.get("data/shader_maker/function/off/dusk.mcfunction").contains("sm.fade0 52"),"fade-out timer 2.5 s + 2 ticks");
        check(data.get("data/shader_maker/function/tick.mcfunction").contains("posteffect remove @s shader_maker:dusk_off"),"cleanup");
        check(data.get("data/shader_maker/function/menu.mcfunction").contains("Danger red!"),"menu label");
        rejects(()->CommandEffects.datapack(Project.preset("Moonlit")),"datapack without command layers");
        // Shared memory GLSL constants stay in sync with the documented layout.
        check(SignalMemoryShaders.holdSky().contains("const float FADE_TICKS = 20.0"),"sky fade ticks");
        System.out.println("CommandLayersTest: "+checks+" checks passed");
    }
}
