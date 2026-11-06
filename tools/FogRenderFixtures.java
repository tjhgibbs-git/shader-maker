import io.github.tjhgibbs.shadermaker.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
/** Export production fog packs and independent CPU expectations for software raster checks. */
class FogRenderFixtures {
    static JsonArray array(double... values){var a=new JsonArray();for(double n:values)a.add(n);return a;}
    public static void main(String[] args)throws Exception{
        var root=Path.of(args[0]);Files.createDirectories(root);var cases=new JsonArray();
        var objects=new LinkedHashMap<String,List<Fog>>();
        for(var shape:Fog.Shape.values()){
            objects.put(shape.name().toLowerCase(Locale.ROOT),List.of(Fog.create(shape)));
            objects.put(shape.name().toLowerCase(Locale.ROOT)+"-soft",List.of(Fog.create(shape).with("fade",3)));
        }
        objects.put("linear",List.of(Fog.create(Fog.Shape.EVERYWHERE).falloff(Fog.Falloff.LINEAR).with("start",-16).with("end",128)));
        objects.put("overlap",List.of(Fog.create(Fog.Shape.HEIGHT_BAND),Fog.create(Fog.Shape.BOX).with("fade",2),Fog.create(Fog.Shape.SPHERE).with("fade",3).samples(32)));
        objects.put("condition",List.of(Fog.create(Fog.Shape.EVERYWHERE).altitude(new Atmosphere.Range(60,80,8))));
        objects.put("far-box",List.of(Fog.create(Fog.Shape.BOX).with("x",30000000.25).with("z",-30000000.5)));
        objects.put("far-sphere",List.of(Fog.create(Fog.Shape.SPHERE).with("z",30000000).with("radius",2)));
        for(int n:List.of(8,32))objects.put("box-soft-"+n,List.of(Fog.create(Fog.Shape.BOX).with("fade",3).samples(n)));
        int index=0;
        for(var entry:objects.entrySet()){
            var p=Project.neutral().withFog(entry.getValue());var dir=root.resolve(entry.getKey());Files.createDirectories(dir);
            for(var e:PackCompiler.files(p,PackCompiler.Activation.ALWAYS).entrySet()){var path=dir.resolve(e.getKey());Files.createDirectories(path.getParent());Files.writeString(path,e.getValue());}
            PackCompiler.writeZip(root.resolve(entry.getKey()+".zip"),p,PackCompiler.Activation.ALWAYS);
            for(double cy:List.of(54d,62d,70d,78d,90d))for(double[] ray:List.of(new double[]{0,0,0},new double[]{0,0,16},new double[]{0,0,64},new double[]{0,30,120},new double[]{20,-40,120},new double[]{40,0,120},new double[]{0,0,256})){
                double cx=entry.getKey().equals("far-box")?30000000.125:0,cz=entry.getKey().equals("far-box")?-30000032.25:-32;
                var j=new JsonObject();j.addProperty("pack",entry.getKey());j.add("camera",array(cx,cy,cz));j.add("ray",array(ray));j.addProperty("transmission",Fog.transmittance(p.fog(),cx,cy,cz,ray[0],ray[1],ray[2]));cases.add(j);index++;
            }
            if(entry.getKey().equals("far-sphere")){var j=new JsonObject();j.addProperty("pack",entry.getKey());j.add("camera",array(0,70,0));j.add("ray",array(0,0,30000004));j.addProperty("transmission",Fog.transmittance(p.fog(),0,70,0,0,0,30000004));cases.add(j);}
        }
        // One combined pack makes the UI/handoff easy to inspect without enabling competing packs.
        var demo=Project.neutral().withFog(List.of(Fog.create(Fog.Shape.HEIGHT_BAND).with("fade",3),Fog.create(Fog.Shape.BOX).enabled(false),Fog.create(Fog.Shape.SPHERE).enabled(false)));
        PackCompiler.writeZip(root.resolve("fog-demo-always.zip"),demo,PackCompiler.Activation.ALWAYS);
        var sky=new Project.Layer("Custom sky signal",Project.neutral().with("exposure",1),true,1,Set.of(),null,null,null,null,"custom_ff8040");
        var signal=Project.neutral().withLayers(List.of(sky));var signalDir=root.resolve("custom-sky");
        for(var e:PackCompiler.files(signal,PackCompiler.Activation.ALWAYS).entrySet()){var path=signalDir.resolve(e.getKey());Files.createDirectories(path.getParent());Files.writeString(path,e.getValue());}
        PackCompiler.writeZip(root.resolve("custom-sky-always.zip"),signal,PackCompiler.Activation.ALWAYS);
        Files.writeString(root.resolve("cases.json"),new GsonBuilder().setPrettyPrinting().create().toJson(cases));
        System.out.println("Fog raster fixtures: "+cases.size()+" camera/ray cases; "+objects.size()+" production shader pairs and standalone ZIPs");
    }
}
