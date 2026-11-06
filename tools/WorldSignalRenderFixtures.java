import io.github.tjhgibbs.shadermaker.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;

/** Actual exported resources, for the raw lightmap -> native terrain -> post bridge raster test. */
class WorldSignalRenderFixtures {
    public static void main(String[] args)throws Exception {
        var root=Path.of(args[0]);var cases=new JsonArray();
        for(var source:WorldSignals.Source.values())for(boolean blocks:List.of(false,true)) {
            var condition=source.colour()?WorldSignals.Condition.colour(source,source==WorldSignals.Source.SURFACE_TINT?0x80cc1a:source==WorldSignals.Source.AMBIENT_COLOR?0x0a0a0a:source==WorldSignals.Source.BLOCK_LIGHT_TINT?0xffd88c:source==WorldSignals.Source.NIGHT_VISION_COLOR?0x999999:source==WorldSignals.Source.SKY_LIGHT_COLOR?0xffffff:0xc0d8ff,.01,false):WorldSignals.Condition.range(source,100,200);
            var controls=new WorldSignals.Controls(List.of(condition),blocks);
            var layer=new Project.Layer("Signal test",Project.neutral().with("exposure",1),true,1,Set.of(),null,null,null,null,null,controls);
            var project=Project.neutral().withLayers(List.of(layer));
            String name=source.name().toLowerCase(Locale.ROOT)+(blocks?"-blocks":"-screen");
            for(var entry:PackCompiler.files(project,PackCompiler.Activation.ALWAYS).entrySet()) {
                var path=root.resolve(name).resolve(entry.getKey());Files.createDirectories(path.getParent());Files.writeString(path,entry.getValue());
            }
            var c=new JsonObject();c.addProperty("pack",name);c.addProperty("source",source.name());c.addProperty("blocksOnly",blocks);cases.add(c);
            PackCompiler.writeZip(root.resolve(name+".zip"),project,PackCompiler.Activation.ALWAYS);
        }
        var multi=new ArrayList<Project.Layer>();
        for(var source:List.of(WorldSignals.Source.FOG_START,WorldSignals.Source.FOG_END,WorldSignals.Source.SKY_FOG_END,WorldSignals.Source.CLOUD_FOG_END))
            multi.add(new Project.Layer("Mask "+source.name(),Project.neutral().with("exposure",1),true,1,Set.of(),null,null,null,null,null,new WorldSignals.Controls(List.of(WorldSignals.Condition.range(source,100,200)),false)));
        multi.add(new Project.Layer("Sky coexistence",Project.neutral(),true,0,Set.of(),null,null,null,null,"sky_78a7ff"));
        var multiProject=Project.neutral().withLayers(multi);
        for(var entry:PackCompiler.files(multiProject,PackCompiler.Activation.ALWAYS).entrySet()) {
            var path=root.resolve("multi").resolve(entry.getKey());Files.createDirectories(path.getParent());Files.writeString(path,entry.getValue());
        }
        Files.writeString(root.resolve("cases.json"),new GsonBuilder().setPrettyPrinting().create().toJson(cases));
    }
}
