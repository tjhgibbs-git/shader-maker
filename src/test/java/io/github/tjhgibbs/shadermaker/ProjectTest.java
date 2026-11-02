package io.github.tjhgibbs.shadermaker;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public final class ProjectTest {
    static int checks;
    static void check(boolean ok,String message) {checks++;if(!ok)throw new AssertionError(message);}
    static void rejects(Runnable action) {try {action.run();throw new AssertionError("Expected rejection");}catch(IllegalArgumentException e){checks++;}}
    public static void main(String[] args) throws Exception {
        Project neutral=Project.neutral();
        for(String preset:Project.PRESETS) {
            Project p=Project.preset(preset);
            check(Project.decode(p.encode()).encode().equals(p.encode()),"Preset roundtrip: "+preset);
            for(PackCompiler.Activation activation:PackCompiler.Activation.values()) {
                var files=PackCompiler.files(p,activation);
                check(files.size()==5,"Pack entry count");
                check(files.containsKey("assets/"+(activation==PackCompiler.Activation.ALWAYS?"minecraft/post_effect/end_of_frame.json":"shader_maker/post_effect/grade.json")),"Activation resource");
                for(var file:files.entrySet())if(file.getKey().endsWith(".json")||file.getKey().endsWith(".mcmeta"))JsonParser.parseString(file.getValue());
                check(!PackCompiler.fragment(p).contains("@"),"All shader constants compiled");
                check(PackCompiler.fragment(p).contains("source.a"),"Alpha retained");
                Path dir=Files.createTempDirectory("shader-maker-test-");
                try {
                    Path a=dir.resolve("a.zip"),b=dir.resolve("b.zip");
                    PackCompiler.writeZip(a,p,activation);PackCompiler.writeZip(b,p,activation);
                    check(Arrays.equals(Files.readAllBytes(a),Files.readAllBytes(b)),"Deterministic ZIP");
                    try(ZipFile zip=new ZipFile(a.toFile())) {check(zip.size()==5,"ZIP readable");}
                    Path json=dir.resolve("project.json");PackCompiler.saveProject(json,p);
                    check(Files.readString(json).equals(p.encode()),"Atomic project save");
                    PackCompiler.saveProject(json,neutral);check(Project.decode(Files.readString(json)).encode().equals(neutral.encode()),"Replace existing save");
                    try(var entries=Files.list(dir)){check(entries.noneMatch(f->f.toString().endsWith(".tmp")),"No temporary residue");}
                }finally{try(var entries=Files.list(dir)){for(Path f:entries.toList())Files.delete(f);}Files.delete(dir);}
            }
        }
        for(int version:List.of(1,2,3)) {
            JsonObject root=neutral.json();root.addProperty("version",version);root.getAsJsonObject("state").remove("curves");
            check(Project.decode(root.toString()).curves().master().sample(.27)==.27,"Legacy identity curves");
        }
        for(String flag:List.of("altitudeEnabled","timeEnabled","facingEnabled")){
            var root=neutral.json();root.getAsJsonObject("state").addProperty(flag,true);rejects(()->Project.decode(root.toString()));
        }
        var biome=neutral.json();biome.getAsJsonObject("state").addProperty("biome","minecraft:plains");rejects(()->Project.decode(biome.toString()));
        var pulse=neutral.json();pulse.getAsJsonObject("state").addProperty("pulsePeriod",2);pulse.getAsJsonObject("state").addProperty("pulseDepth",.5);rejects(()->Project.decode(pulse.toString()));
        pulse.getAsJsonObject("state").addProperty("pulseDepth",0);check(Project.decode(pulse.toString())!=null,"Zero-depth pulse allowed");
        rejects(()->Project.decode("{}"));rejects(()->Project.decode("[]"));rejects(()->Project.decode("not JSON"));
        rejects(()->Project.decode(neutral.encode().replace("26.3","26.2")));
        rejects(()->neutral.with("exposure",Double.NaN));rejects(()->neutral.with("gamma",0));rejects(()->neutral.with("saturation",3));
        var bad=neutral.json();bad.getAsJsonObject("state").addProperty("black",1);final var badBlack=bad;rejects(()->Project.decode(badBlack.toString()));
        bad=neutral.json();bad.getAsJsonObject("state").addProperty("gamma","1");final var badString=bad;rejects(()->Project.decode(badString.toString()));
        var nonIdentity=new Curves.Curve(List.of(new Curves.Point(0,.04),new Curves.Point(.3,.25),new Curves.Point(.7,.8),new Curves.Point(1,.97)));
        Project custom=neutral.withCurve(0,nonIdentity).withCurve(2,nonIdentity).with("exposure",.3).with("shoulder",.6);
        check(Project.decode(custom.encode()).encode().equals(custom.encode()),"Curves retained exactly");
        check(PackCompiler.fragment(custom).contains("curve_green"),"Channel GLSL");
        check(neutral.with("black",.9).with("white",.5).get("white")>.9,"Slider level ordering");
        for(int i=0;i<=100;i++) {
            double v=i/100.;var source=new Atmosphere.Color(v,1-v,.3,.4);
            var result=neutral.grade().apply(source,.2,-.7,1,neutral.curves());
            check(Math.abs(result.r()-source.r())<1e-12&&Math.abs(result.g()-source.g())<1e-12&&result.a()==.4,"Neutral identity");
        }
        var conditional = new Project.Layer(neutral.with("exposure",1), true, .5, Set.of("minecraft:plains"),
            new Atmosphere.Range(0,100,10), new Atmosphere.CircularRange(22000,2000,24000,1000));
        var layered = neutral.withLayers(List.of(conditional));
        check(Project.decode(layered.encode()).encode().equals(layered.encode()),"Layer roundtrip");
        var named = new Project.Layer("Forest dusk", neutral, true, .7, Set.of(), null, null);
        check(Project.decode(neutral.withLayers(List.of(named)).encode()).layers().get(0).name().equals("Forest dusk"),"Layer name roundtrip");
        var unnamed = layered.json(); unnamed.getAsJsonArray("layers").get(0).getAsJsonObject().remove("name");
        check(Project.decode(unnamed.toString()).layers().get(0).name().equals("Layer 1"),"Unnamed v4 layer migration");
        unnamed.getAsJsonArray("layers").get(0).getAsJsonObject().addProperty("name", " ");
        rejects(()->Project.decode(unnamed.toString()));
        var legacy = neutral.json(); legacy.addProperty("version",3); legacy.remove("layers");
        check(Project.decode(legacy.toString()).layers().isEmpty(),"Legacy migration has no layers");
        check(layered.with("gamma",1.2).withCurves(Curves.identity()).layers().equals(layered.layers()),"Edits preserve layers");
        var source = new Atmosphere.Color(.2,.3,.4,1);
        var ctx = new Atmosphere.Context(0,50,0,"minecraft:plains",0);
        check(Math.abs(layered.evaluate(source,0,0,ctx).r()-.3)<1e-9,"Conditional layer blending");
        check(Math.abs(layered.evaluate(source,0,0,null).r()-.2)<1e-9,"Missing context suppresses conditions");
        var blend = Atmosphere.biomeTransition("minecraft:plains","minecraft:desert",.5,1);
        check(Math.abs(layered.evaluate(source,0,0,ctx,blend).r()-.25)<1e-9,"Biome transition blends");
        check(Math.abs(layered.evaluate(source,0,0,new Atmosphere.Context(22500,5,0,"minecraft:plains",0)).r()-.225)<1e-9,"Altitude and wrapped time feathers multiply");
        rejects(()->PackCompiler.files(layered,PackCompiler.Activation.ALWAYS));
        var disabledLayer = new Project.Layer(neutral.with("exposure",1),false,.5,Set.of("minecraft:plains"),null,null);
        var disabledProject = neutral.withLayers(List.of(disabledLayer));
        var exported = PackCompiler.files(disabledProject,PackCompiler.Activation.ALWAYS);
        check(Project.decode(exported.get("shader-maker-project.json")).encode().equals(disabledProject.encode()),"Disabled layer retained in exported snapshot");
        var dark = new Project.Layer(neutral.with("exposure",-1),true,1,Set.of(),null,null);
        var bright = new Project.Layer(neutral.with("exposure",1),true,1,Set.of(),null,null);
        check(Math.abs(neutral.withLayers(List.of(dark,bright)).evaluate(source,0,0,null).r()-.4)<1e-9,"Last layer overrides previous color");
        check(Math.abs(neutral.withLayers(List.of(bright,dark)).evaluate(source,0,0,null).r()-.1)<1e-9,"Layer order matters");
        rejects(()->new Project.Layer(neutral,true,1,Set.of("Plains"),null,null));
        rejects(()->new Project.Layer(neutral,true,2,Set.of(),null,null));
        var malformed=layered.json(); malformed.getAsJsonArray("layers").get(0).getAsJsonObject().addProperty("strength","0.5");
        rejects(()->Project.decode(malformed.toString()));
        History layerHistory = new History(neutral); layerHistory.commit(layered);
        check(layerHistory.undo().equals(neutral) && layerHistory.redo().equals(layered),"Layer undo and redo");
        History h=new History(neutral);h.commit(custom);h.commit(custom);check(h.undo().encode().equals(neutral.encode()),"Duplicate commits ignored");
        check(h.redo().encode().equals(custom.encode()),"Redo");h.undo();h.commit(Project.preset("Moonlit"));check(!h.canRedo(),"Redo branch discarded");
        for(int i=0;i<130;i++)h.commit(neutral.with("exposure",i%2));int undoCount=0;while(h.canUndo()){h.undo();undoCount++;}check(undoCount==100,"History bounded at 100 edits");
        if(args.length>0){Path path=Path.of(args[0]);Files.createDirectories(path);Files.writeString(path.resolve("project.json"),custom.encode());Files.writeString(path.resolve("grade.fsh"),PackCompiler.fragment(custom));PackCompiler.writeZip(path.resolve("resources.zip"),custom,PackCompiler.Activation.ALWAYS);}
        System.out.println("ProjectTest: "+checks+" checks passed");
    }
}
