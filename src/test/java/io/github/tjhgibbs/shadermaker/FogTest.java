package io.github.tjhgibbs.shadermaker;

import java.util.*;
import java.nio.file.*;

/** Physical invariants and independently sampled reference integrals, plus persistence/export boundaries. */
public final class FogTest {
    private static int checks;
    private static void near(double actual,double expected,double error,String name){checks++;if(!Double.isFinite(actual)||Math.abs(actual-expected)>error)throw new AssertionError(name+": "+actual+" != "+expected);}
    private static void check(boolean b,String name){checks++;if(!b)throw new AssertionError(name);}
    private static void rejects(Runnable r){try{r.run();throw new AssertionError("Expected rejection");}catch(IllegalArgumentException e){checks++;}}
    private static double density(Fog f,double x,double y,double z){
        double m=switch(f.shape()){
            case EVERYWHERE->1e10;case BELOW_HEIGHT->f.get("upper")-y;case HEIGHT_BAND->Math.min(y-f.get("lower"),f.get("upper")-y);
            case BOX->Math.min(f.get("width")/2-Math.abs(x-f.get("x")),Math.min(f.get("height")/2-Math.abs(y-f.get("y")),f.get("depth")/2-Math.abs(z-f.get("z"))));
            case SPHERE->f.get("radius")-Math.sqrt(Math.pow(x-f.get("x"),2)+Math.pow(y-f.get("y"),2)+Math.pow(z-f.get("z"),2));};
        if(m<0)return 0;if(f.get("fade")==0)return 1;double t=Math.clamp(m/f.get("fade"),0,1);return t*t*(3-2*t);
    }
    private static double reference(Fog f,double[] c,double[] v){int n=16000;double len=Math.sqrt(v[0]*v[0]+v[1]*v[1]+v[2]*v[2]),sum=0;for(int i=0;i<n;i++){double u=(i+.5)/n;if(u*len>=f.get("start"))sum+=density(f,c[0]+u*v[0],c[1]+u*v[1],c[2]+u*v[2]);}return sum*len/n;}
    public static void main(String[] args)throws Exception{
        var haze=Fog.create(Fog.Shape.EVERYWHERE);
        near(Fog.transmittance(List.of(haze),0,70,0,0,0,64),.5,1e-12,"Half transmission is defined by path length");
        near(Fog.transmittance(List.of(haze,haze),0,70,0,0,0,64),.25,1e-12,"Optical depths add");
        near(haze.with("start",16).integratedLength(0,70,0,0,0,80),64,1e-12,"Clear-near excludes first segment");
        // Presets: valid, placed relative to the ground below the author, and round-trip through persistence.
        for(String name:Fog.PRESETS){
            var fogs=Fog.preset(name,64.7,-241.3,70.2,12.9);
            check(!fogs.isEmpty()&&fogs.stream().allMatch(Fog::enabled),"Preset "+name+" creates enabled fog");
            check(!Fog.presetHelp(name).isBlank(),"Preset "+name+" has help");
            var pr=Project.neutral().withFog(fogs);
            check(Project.decode(pr.encode()).fog().equals(fogs),"Preset "+name+" round-trips");
            check(PackCompiler.issues(pr).isEmpty(),"Preset "+name+" exports");
            for(var f:fogs)check(!f.summary().isBlank(),"Preset summary");
        }
        check(Fog.preset("Ground mist",64.7,0,0,0).getFirst().get("upper")==68,"Ground mist sits 4 blocks above floor(ground)");
        var lowCloud=Fog.preset("Low cloud",100,0,0,0).getFirst();
        check(lowCloud.get("lower")==116&&lowCloud.get("upper")==130,"Low cloud band relative to ground");
        var interior=Fog.preset("Interior haze",64,-241.3,70.2,12.9).getFirst();
        check(interior.get("x")==-241.5&&interior.get("z")==12.5&&interior.get("y")==71,"Interior haze centred on author's block");
        check(Fog.preset("Misty morning",64,0,0,0).size()==2,"Misty morning is two editable objects");
        rejects(()->Fog.preset("Nope",64,0,0,0));
        near(Fog.transmittance(Fog.preset("Thick fog",64,0,0,0),0,70,0,0,0,26),.5,1e-9,"Thick fog halves light 24 blocks beyond its 2-block clear zone");
        var band=Fog.create(Fog.Shape.HEIGHT_BAND);
        near(band.integratedLength(0,70,0,0,0,100),100,1e-12,"Horizontal inside band");
        near(band.integratedLength(0,90,0,0,0,100),0,1e-12,"Horizontal outside band");
        near(band.integratedLength(0,50,0,0,40,0),16,1e-12,"Ray through layer to a surface above it");
        near(band.with("fade",4).integratedLength(0,50,0,0,40,0),12,1e-10,"Analytic soft band integral");
        near(band.integratedLength(0,70,0,0,0,0),0,0,"Degenerate ray");
        var box=Fog.create(Fog.Shape.BOX);
        near(box.integratedLength(0,70,-32,0,0,64),32,1e-12,"Box entry and exit");
        near(box.integratedLength(0,70,0,0,0,64),16,1e-12,"Camera inside box");
        near(box.integratedLength(50,70,-32,0,0,64),0,0,"Parallel outside box");
        var sphere=Fog.create(Fog.Shape.SPHERE);
        near(sphere.integratedLength(0,70,-32,0,0,64),32,1e-12,"Sphere diameter");
        near(sphere.integratedLength(16,70,-32,0,0,64),0,0,"Tangent sphere");
        near(sphere.with("z",30000000).integratedLength(0,70,0,0,0,30000032),32,1e-6,"Far sphere precision");
        near(haze.altitude(new Atmosphere.Range(80,90,2)).opticalDepth(0,70,0,0,0,64),0,0,"Camera activation separate from surface height");
        var linear=haze.falloff(Fog.Falloff.LINEAR).with("start",-16).with("end",48);
        near(Fog.transmittance(List.of(linear),0,70,0,0,0,16),.5,1e-12,"Negative linear start");
        var random=new Random(261103);
        for(var shape:Fog.Shape.values())for(double fade:List.of(0d,3d)){
            var f=Fog.create(shape).with("fade",fade).samples(32);
            for(int i=0;i<30;i++){
                double[] c={random.nextDouble()*60-30,random.nextDouble()*60+40,random.nextDouble()*60-30};
                double[] v={random.nextDouble()*120-60,random.nextDouble()*120-60,random.nextDouble()*120-60};
                double value=f.integratedLength(c[0],c[1],c[2],v[0],v[1],v[2]);
                near(value,reference(f,c,v),f.sampled()?.3:.025,"Independent integral "+shape+" fade "+fade);
            }
        }
        var p=Project.preset("Golden dusk").withFog(List.of(haze,band.with("fade",4),box.with("fade",2).samples(8),sphere.samples(32)));
        check(p.json().get("version").getAsInt()==7,"Fog schema v7");
        check(Project.decode(p.encode()).encode().equals(p.encode()),"Fog roundtrip");
        check(p.with("exposure",1).withLayers(List.of()).withCurves(Curves.identity()).fog().equals(p.fog()),"Grade and layer edits retain fog");
        var history=new History(Project.neutral());history.commit(p);check(history.undo().fog().isEmpty()&&history.redo().equals(p),"History retains fog");
        var files=PackCompiler.files(p,PackCompiler.Activation.ALWAYS);
        check(Project.decode(files.get("shader-maker-project.json")).encode().equals(p.encode()),"Embedded snapshot retains fog");
        check(files.containsKey("assets/minecraft/shaders/core/terrain.vsh")&&files.containsKey("assets/minecraft/shaders/core/terrain.fsh"),"Core pair exported together");
        check(files.get("README.txt").contains("Entities, block entities"),"Scope disclosed");
        rejects(()->PackCompiler.files(p,PackCompiler.Activation.COMMAND));
        var disabled=p.withFog(p.fog().stream().map(f->f.enabled(false)).toList());
        check(!PackCompiler.files(disabled,PackCompiler.Activation.COMMAND).containsKey("assets/minecraft/shaders/core/terrain.fsh"),"Disabled settings retained without override");
        check(Project.decode(disabled.encode()).fog().equals(disabled.fog()),"Disabled persistence");
        var bad=p.json();bad.addProperty("version",6);rejects(()->Project.decode(bad.toString()));
        var missing=p.json();missing.getAsJsonArray("fog").get(0).getAsJsonObject().getAsJsonObject("values").remove("visibility");rejects(()->Project.decode(missing.toString()));
        rejects(()->band.falloff(Fog.Falloff.LINEAR));rejects(()->haze.with("visibility",0));rejects(()->box.with("fade",100));rejects(()->haze.samples(0));rejects(()->haze.with("strength",Double.NaN));
        Path dir=Files.createTempDirectory("fog-pack-");try{var a=dir.resolve("a.zip");var b=dir.resolve("b.zip");PackCompiler.writeZip(a,p,PackCompiler.Activation.ALWAYS);PackCompiler.writeZip(b,p,PackCompiler.Activation.ALWAYS);check(Arrays.equals(Files.readAllBytes(a),Files.readAllBytes(b)),"Deterministic fog ZIP");}finally{try(var stream=Files.list(dir)){for(var f:stream.toList())Files.delete(f);}Files.delete(dir);}
        customSky();
        System.out.println("FogTest: "+checks+" physical, reference-integration, persistence, export and sky-colour assertions passed");
    }
    private static void customSky(){
        var custom=new Project.Layer("Datapack signal",Project.neutral().with("exposure",1),true,1,Set.of(),null,null,null,null,"custom_ff8040");
        var p=Project.neutral().withLayers(List.of(custom));
        check(p.json().get("version").getAsInt()==8,"Custom sky schema v8");
        check(Project.decode(p.encode()).encode().equals(p.encode()),"Custom sky roundtrip");
        check(VisualBiomes.previewMatch(custom.visualBiomeGroup(),"#ff8040"),"Custom preview input");
        check(VisualBiomes.previewMatch("custom_fe8040","#7f4020"),"Brightness-independent hue");
        check(!VisualBiomes.previewMatch(custom.visualBiomeGroup(),"#000000"),"Dark sky suppresses");
        check(VisualBiomes.code(p,custom.visualBiomeGroup())==15,"Custom palette mapping");
        check(PackCompiler.files(p,PackCompiler.Activation.ALWAYS).get("assets/minecraft/shaders/core/sky.fsh").contains("1.0, 0.5019607843137255, 0.25098039215686274"),"Custom signal compiled into sky shader");
        var q=p.withFog(List.of(Fog.create(Fog.Shape.BOX)));check(Project.decode(q.encode()).encode().equals(q.encode()),"Custom sky and fog coexist");
        var old=p.json();old.addProperty("version",6);rejects(()->Project.decode(old.toString()));rejects(()->VisualBiomes.get("custom_000000"));
        var stock=new Project.Layer("Stock sky",Project.neutral(),true,1,Set.of(),null,null,null,null,"sky_78a7ff");
        var mixed=p.withLayers(List.of(stock,custom));check(VisualBiomes.code(mixed,stock.visualBiomeGroup())==15&&VisualBiomes.code(mixed,custom.visualBiomeGroup())==30,"Mixed stock and custom palette maps grades correctly");
        var alias=new Project.Layer("Same hue",Project.neutral(),true,1,Set.of(),null,null,null,null,"custom_ff0000");
        var alias2=new Project.Layer("Same hue dimmer",Project.neutral(),true,1,Set.of(),null,null,null,null,"custom_800000");
        var same=Project.neutral().withLayers(List.of(alias,alias2));check(VisualBiomes.palette(same).size()==1&&VisualBiomes.code(same,alias.visualBiomeGroup())==VisualBiomes.code(same,alias2.visualBiomeGroup()),"Identical hues intentionally share a marker");
        var near=new Project.Layer("Near hue",Project.neutral(),true,1,Set.of(),null,null,null,null,"custom_fd7f3f");
        check(!PackCompiler.issues(p.withLayers(List.of(custom,near))).isEmpty(),"Overlapping hue tolerances rejected");
        check(Project.decode(Project.neutral().withLayers(List.of(new Project.Layer("Legacy IDs",Project.neutral(),false,1,Set.of("minecraft:plains"),null,null))).encode()).layers().getFirst().biomes().contains("minecraft:plains"),"Legacy exact-ID data retained");
        var many=new ArrayList<Project.Layer>();for(int i=0;i<17;i++)many.add(new Project.Layer("Hue "+i,Project.neutral(),true,1,Set.of(),null,null,null,null,String.format(Locale.ROOT,"custom_ff%02x40",i*12)));
        check(!PackCompiler.issues(Project.neutral().withLayers(many)).isEmpty(),"Palette limit blocks output instead of aliasing marker codes");
    }
}
