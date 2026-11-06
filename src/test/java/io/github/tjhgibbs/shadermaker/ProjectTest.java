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
        check(PackCompiler.fragment(custom).contains("curve_base_green"),"Channel GLSL");
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
        check(PackCompiler.issues(layered).size()==2,"Both unresolved input paths diagnosed");
        check(PackCompiler.issues(layered).get(0).contains(conditional.name()),"Issue names affected layer");
        var heightLayer=new Project.Layer("Height",neutral.with("exposure",1),true,.5,Set.of(),new Atmosphere.Range(-64,100,10),null);
        var heightProject=neutral.withLayers(List.of(heightLayer));
        check(PackCompiler.issues(heightProject).isEmpty(),"Camera Y exports");
        String heightShader=PackCompiler.fragment(heightProject);
        check(heightShader.contains("float(CameraBlockPos.y) - CameraOffset.y"),"Signed fractional camera position");
        check(heightShader.contains("smoothstep(0.0, 1.0"),"Smooth inward feather compiled");
        check(heightShader.contains("grade_layer0(source.rgb)"),"Layer grades original sample");
        check(Project.decode(PackCompiler.files(heightProject,PackCompiler.Activation.ALWAYS).get("shader-maker-project.json")).encode().equals(heightProject.encode()),"Conditional output retains source project");
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
        environmentalConditions();
        visualBiomes();
        if(args.length>0){Path path=Path.of(args[0]);Files.createDirectories(path);Files.writeString(path.resolve("project.json"),custom.encode());Files.writeString(path.resolve("grade.fsh"),PackCompiler.fragment(custom));PackCompiler.writeZip(path.resolve("resources.zip"),custom,PackCompiler.Activation.ALWAYS);}
        System.out.println("ProjectTest: "+checks+" checks passed");
    }
    private static void visualBiomes() {
        check(VisualBiomes.GROUPS.size()==16,"Pinned visible-sky palette has sixteen groups");
        var seen=new HashSet<String>();var codes=new HashSet<Integer>();
        for(var group:VisualBiomes.GROUPS) {
            check(codes.add(group.code())&&group.code()>0&&group.code()<255,"Transport codes are unique and exclude empty/opaque alpha");
            check(VisualBiomes.classify(((group.rgb()>>16)&255)/255.0,((group.rgb()>>8)&255)/255.0,(group.rgb()&255)/255.0)==group.code(),"Every stock signature classifies, including grey pale garden");
            for(String biome:group.biomes())check(seen.add(biome),"Stock biome belongs to one sky signature");
        }
        check(VisualBiomes.classify(0,0,0)==0&&VisualBiomes.classify(1,0,0)==0,"Darkness and unknown custom colors suppress detection");
        var group=VisualBiomes.forBiome("minecraft:plains").orElseThrow();
        check(group.biomes().contains("minecraft:sunflower_plains"),"Shared members are explicit");
        check(VisualBiomes.forBiome("minecraft:nether_wastes").isEmpty(),"No implied Nether sky support");
        var layer=new Project.Layer("Shared sky",Project.preset("Moonlit"),true,.6,Set.of(),new Atmosphere.Range(0,100,0),null,null,null,group.id());
        var p=Project.neutral().withLayers(List.of(layer));
        check(p.json().get("version").getAsInt()==6,"Visual semantics explicitly versioned");
        check(Project.decode(p.encode()).encode().equals(p.encode()),"Visual group roundtrip");
        check(layer.weight(new Atmosphere.Context(6000,64,0,"minecraft:sunflower_plains",0))==.6,"Preview includes explicitly grouped stock biomes");
        check(layer.weight(new Atmosphere.Context(6000,64,0,"minecraft:desert",0))==0,"Preview excludes different groups");
        check(layer.weight(null)==0,"No preview context cannot activate group");
        check(PackCompiler.issues(p).isEmpty(),"Experimental group is exportable for testing");
        for(var mode:PackCompiler.Activation.values()) {
            var files=PackCompiler.files(p,mode);
            check(files.containsKey("assets/minecraft/shaders/core/sky.fsh")&&files.containsKey("assets/shader_maker/shaders/post/detect_environment.fsh"),"Both activation modes include vanilla sky transport and reduction");
            check(files.get("README.txt").contains("Datapacks can set the sky colour")&&files.get("README.txt").contains("minecraft:sunflower_plains"),"Output discloses datapack risk and expanded group members");
            check(Project.decode(files.get("shader-maker-project.json")).encode().equals(p.encode()),"Embedded snapshot retains explicit group");
        }
        check(PackCompiler.fragment(p).contains("SkyHeldSampler,ivec2("+VisualBiomes.code(p,group.id())/15+",0)"),"Held detected code actually controls layer weight");
        check(!PackCompiler.files(Project.neutral(),PackCompiler.Activation.ALWAYS).containsKey("assets/minecraft/shaders/core/sky.fsh"),"Ordinary exports add no sky override");
        rejects(()->new Project.Layer("Unknown",Project.neutral(),true,1,Set.of(),null,null,null,null,"missing"));
        rejects(()->new Project.Layer("Ambiguous",Project.neutral(),true,1,Set.of("minecraft:plains"),null,null,null,null,group.id()));
        var legacy=p.json();legacy.addProperty("version",5);rejects(()->Project.decode(legacy.toString()));
        var exact=new Project.Layer("Exact legacy",Project.neutral(),true,1,Set.of("minecraft:plains"),null,null);
        check(Project.decode(Project.neutral().withLayers(List.of(exact)).encode()).layers().getFirst().visualBiomeGroup()==null,"Legacy IDs never silently convert to broader groups");
    }
    private static void environmentalConditions() {
        var all=new Atmosphere.Range(0,1,0);
        var weather=new Atmosphere.Weather(new Atmosphere.Range(.2,1,.2),new Atmosphere.Range(.4,1,.2));
        var flash=new Atmosphere.Range(.2,1,.2);
        var l=new Project.Layer("Storm flash",Project.neutral().with("exposure",1),true,.8,Set.of("minecraft:plains","minecraft:forest"),new Atmosphere.Range(0,100,10),new Atmosphere.CircularRange(18000,6000,24000,1000),weather,flash);
        var p=Project.neutral().withLayers(List.of(l));
        var c=new Atmosphere.Context(0,50,0,"minecraft:forest",0,.3,.5,.3);
        check(Math.abs(l.weight(c)-.1)<1e-10,"AND multiplies weather, flash and strength; biome alternatives use OR");
        var source=new Atmosphere.Color(.2,.3,.4,.7);
        var actual=p.evaluate(source,0,0,c);
        check(Math.abs(actual.r()-.22)<1e-10&&actual.a()==.7,"Weather and flash participate in composition and retain alpha");
        check(l.weight(new Atmosphere.Context(12000,50,0,"minecraft:forest",0,1,1,1))==0,"Daylight exclusion");
        check(l.weight(new Atmosphere.Context(0,50,0,"minecraft:desert",0,1,1,1))==0,"Exact biome exclusion");
        check(l.weight(new Atmosphere.Context(0,-1,0,"minecraft:forest",0,1,1,1))==0,"Height exclusion");
        check(l.weight(new Atmosphere.Context(0,50,0,"minecraft:forest",0,1,1,0))==0,"No visible flash suppresses flash condition");
        check(l.weight(new Atmosphere.Context(0,50,0,"minecraft:forest",0,0,0,1))==0,"Clear weather suppresses storm");
        check(Math.abs(l.weight(new Atmosphere.Context(0,50,0,"minecraft:forest",0,1,1,1))-.8)<1e-10,"Full storm and maximum flash remain active with feathers at domain endpoints");
        check(Atmosphere.unitWeight(new Atmosphere.Range(0,1,.5),0)==1&&Atmosphere.unitWeight(new Atmosphere.Range(0,1,.5),1)==1,"Unrestricted normalized ranges do not fade away at zero or one");
        check(l.weight(null)==0,"Missing context never activates environmental conditions");
        check(Math.abs(p.evaluate(source,0,0,c,Map.of("minecraft:forest",.5)).r()-.21)<1e-10,"Biome transition also multiplies new weather / flash weights");
        check(p.json().get("version").getAsInt()==5,"New conditions opt into v5");
        var roundtrip=Project.decode(p.encode());
        check(roundtrip.encode().equals(p.encode()),"All environmental conditions survive v5 persistence");
        var history=new History(Project.neutral());history.commit(p);check(history.undo().layers().isEmpty()&&history.redo().equals(p),"Environmental condition history preserves data");
        check(PackCompiler.issues(p).size()==4,"Every missing runtime input is diagnosed");
        for(var activation:PackCompiler.Activation.values())rejects(()->PackCompiler.files(p,activation));
        var hidden=new Project.Layer(l.name(),l.project(),false,l.strength(),l.biomes(),l.altitude(),l.time(),l.weather(),l.lightning());
        var hiddenProject=Project.neutral().withLayers(List.of(hidden));
        check(hidden.weight(c)==0,"Hidden environmental layer has no contribution");
        check(PackCompiler.layerIssues(hidden).size()==4,"Disabled unsupported layers cannot be enabled as exportable");
        check(PackCompiler.issues(hiddenProject).isEmpty(),"Explicitly disabling preserves settings and repairs export");
        check(PackCompiler.layerIssues(hidden).stream().allMatch(i->i.contains("Clear this condition or disable this layer.")),"Every unsupported input has an actionable repair");
        try {
            Path saved=Files.createTempFile("unavailable-project-", ".json");
            try {PackCompiler.saveProject(saved,p);check(Project.decode(Files.readString(saved)).encode().equals(p.encode()),"Export limitations never prevent project saving");}
            finally {Files.deleteIfExists(saved);}
        } catch(java.io.IOException e) {throw new AssertionError(e);}

        check(Project.decode(PackCompiler.files(hiddenProject,PackCompiler.Activation.ALWAYS).get("shader-maker-project.json")).encode().equals(hiddenProject.encode()),"Disabled conditions remain in exported snapshots");
        var malformed=p.json();malformed.addProperty("version",4);rejects(()->Project.decode(malformed.toString()));
        var missing=p.json();missing.getAsJsonArray("layers").get(0).getAsJsonObject().getAsJsonObject("weather").remove("rain");rejects(()->Project.decode(missing.toString()));
        rejects(()->new Atmosphere.Weather(new Atmosphere.Range(-.1,1,0),all));
        rejects(()->new Atmosphere.Weather(all,new Atmosphere.Range(0,2,0)));
        rejects(()->new Atmosphere.Weather(all,new Atmosphere.Range(0,1,2)));
        rejects(()->new Atmosphere.Context(0,0,0,"minecraft:plains",0,Double.NaN,0,0));
        rejects(()->new Atmosphere.Context(0,0,0,"minecraft:plains",0,0,-1,0));
        rejects(()->new Atmosphere.Context(0,0,0,"minecraft:plains",0,0,0,2));
        rejects(()->new Project.Layer("Invalid flash",Project.neutral(),true,1,Set.of(),null,null,null,new Atmosphere.Range(0,2,0)));
        check(new Atmosphere.Context(0,0,0,"minecraft:plains",0).flash()==0,"Legacy contexts default to clear weather without a flash");
        check(Project.neutral().json().get("version").getAsInt()==4,"Existing project schema is unchanged without new conditions");
    }

}
