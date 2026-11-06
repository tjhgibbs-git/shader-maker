package io.github.tjhgibbs.shadermaker;

import java.util.*;
import static io.github.tjhgibbs.shadermaker.WorldSignals.*;

public final class WorldSignalsTest {
    static int checks;
    static void check(boolean v,String label){if(!v)throw new AssertionError(label);checks++;}
    static void rejects(Runnable r){try{r.run();}catch(IllegalArgumentException e){checks++;return;}throw new AssertionError("Expected rejection");}
    public static Project.Layer layer(Controls controls,boolean enabled){return new Project.Layer("Context",Project.neutral().with("exposure",1),enabled,1,Set.of(),null,null,null,null,null,controls);}
    public static Project fixture(){
        return Project.neutral().withLayers(List.of(layer(new Controls(List.of(
            Condition.colour(Source.FOG_COLOR,0xc0d8ff,.02,false),
            Condition.range(Source.FOG_END,100,200),
            Condition.colour(Source.AMBIENT_COLOR,0x0a0a0a,.001,false)),false),true)));
    }
    public static void main(String[] args){
        for(var source:Source.values()){
            var condition=source.colour()?Condition.colour(source,0x4090ff,.01,false):Condition.range(source,-8,100);
            check(Condition.read(condition.json()).equals(condition),"Condition persistence "+source);
            check(condition.matches(source.colour()?Sample.colour(0x4090ff):Sample.distance(100)),"Inclusive matching "+source);
            check(!condition.matches(null),"Unavailable never matches "+source);
            var controls=new Controls(List.of(condition),true);var p=Project.neutral().withLayers(List.of(layer(controls,true)));
            check(Project.decode(p.encode()).encode().equals(p.encode()),"End-to-end persistence "+source);
            check(p.json().get("version").getAsInt()==9,"v9 opt-in");
            var samples=new Samples(Map.of(source,source.colour()?Sample.colour(0x4090ff):Sample.distance(50)),true,true);
            var context=new Atmosphere.Context(6000,64,0,"minecraft:plains",0,0,0,0,samples);
            check(p.layers().getFirst().weight(context)==1,"Reference preview match");
            check(!controls.matches(new Samples(samples.values(),true,false)),"Blocks only excludes other scene");
            check(new Controls(controls.conditions(),false).matches(new Samples(samples.values(),true,false)),"Full-screen applies to other scene");
            check(!controls.matches(new Samples(samples.values(),false,true)),"No stale marker");
            for(var mode:PackCompiler.Activation.values())check(PackCompiler.files(p,mode).containsKey("assets/shader_maker/shaders/post/detect_context.fsh"),"Shared compiler applies both activation modes");
        }
        var hue=Condition.colour(Source.FOG_COLOR,0x4080ff,.001,true);
        check(hue.matches(new Sample(.12549,.25098,.5)),"Hue ignores brightness");
        check(!hue.matches(Sample.colour(0)),"Black has no hue signal");
        check(Condition.colour(Source.AMBIENT_COLOR,0,0,false).matches(Sample.colour(0)),"Raw RGB permits black ambient colour");
        rejects(()->Condition.colour(Source.FOG_COLOR,0,.01,true));
        rejects(()->Condition.range(Source.FOG_END,100,0));
        rejects(()->Condition.range(Source.FOG_END,0,Double.NaN));
        rejects(()->Condition.colour(Source.FOG_COLOR,0xffffff,-.1,false));
        rejects(()->Condition.colour(Source.FOG_END,0xffffff,.1,false));
        rejects(()->Condition.range(Source.FOG_COLOR,0,100));
        rejects(()->new Controls(List.of(hue,hue),false));
        var controls=new Controls(List.of(hue,Condition.range(Source.FOG_END,0,100)),false);
        check(!controls.matches(new Samples(Map.of(Source.FOG_COLOR,Sample.colour(0x4080ff)),true)),"AND requires every input");
        check(!controls.matches(Samples.EMPTY),"No terrain means inactive");
        var p=fixture();check(Project.decode(p.encode()).encode().equals(p.encode()),"Mixed inputs preserved");
        var grade=p.evaluate(new Atmosphere.Color(.2,.3,.4,1),0,0,new Atmosphere.Context(6000,64,0,"minecraft:plains",0,0,0,0,new Samples(Map.of(Source.FOG_COLOR,Sample.colour(0xc0d8ff),Source.FOG_END,Sample.distance(150),Source.AMBIENT_COLOR,Sample.colour(0x0a0a0a)),true)));
        check(Math.abs(grade.r()-.4)<1e-10,"CPU grading evaluates actual signal samples");
        var old=Project.neutral().withLayers(List.of(new Project.Layer("Old",Project.neutral(),true,1,Set.of("minecraft:plains"),null,null)));
        check(Project.decode(old.encode()).encode().equals(old.encode()),"Legacy exact IDs and meanings preserved");
        var legacy=p.json();legacy.addProperty("version",8);rejects(()->Project.decode(legacy.toString()));
        var malformed=p.json();malformed.getAsJsonArray("layers").get(0).getAsJsonObject().getAsJsonObject("contextControls").addProperty("blocksOnly","false");rejects(()->Project.decode(malformed.toString()));
        var many=new ArrayList<Project.Layer>();for(int i=0;i<5;i++)many.add(layer(new Controls(List.of(Condition.range(Source.FOG_END,i,i+10)),false),true));
        check(!PackCompiler.issues(Project.neutral().withLayers(many)).isEmpty(),"Five distinct sets blocked before generating invalid transport");
        many.set(4,layer(many.get(4).contextControls(),false));check(PackCompiler.issues(Project.neutral().withLayers(many)).isEmpty(),"Disabled sets preserved without consuming transport bits");
        many.set(4,layer(many.get(0).contextControls(),true));check(palette(Project.neutral().withLayers(many)).size()==4,"Duplicate sets share a bit");
        var f=PackCompiler.files(fixture().withFog(List.of(Fog.create(Fog.Shape.EVERYWHERE))),PackCompiler.Activation.ALWAYS);
        check(f.get("assets/minecraft/shaders/core/terrain.fsh").contains("smFogTransmission"),"Context bridge composes with authored fog");
        check(f.get("assets/minecraft/shaders/core/lightmap.fsh").contains("smLightmapMarker"),"Raw lightmap data has transport");
        check(f.get("assets/minecraft/shaders/include/sample_lightmap.glsl").contains(".rgb, 1.0"),"Lightmap alpha cannot change vanilla surface opacity");
        System.out.println("WorldSignalsTest: "+checks+" matching, scope, migration, persistence and export checks passed");
    }
}
