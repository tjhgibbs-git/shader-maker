import io.github.tjhgibbs.shadermaker.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
/** Export actual production resources for the headless raster smoke test. */
class VisualBiomeRenderFixtures {
    public static void main(String[] args) throws Exception {
        var root=Path.of(args[0]);Files.createDirectories(root);
        var group=VisualBiomes.forBiome("minecraft:plains").orElseThrow();
        var project=Project.neutral().withLayers(List.of(new Project.Layer("Visual test",Project.neutral().with("exposure",1),true,1,Set.of(),null,null,null,null,group.id())));
        for(var entry:PackCompiler.files(project,PackCompiler.Activation.ALWAYS).entrySet()){
            var path=root.resolve(entry.getKey());Files.createDirectories(path.getParent());Files.writeString(path,entry.getValue());
        }
        PackCompiler.writeZip(root.resolve("visual-biome-demo-always.zip"),project,PackCompiler.Activation.ALWAYS);
        var groups=new JsonArray();
        for(var g:VisualBiomes.GROUPS){var j=new JsonObject();j.addProperty("id",g.id());j.addProperty("rgb",g.rgb());j.addProperty("code",g.code());groups.add(j);}
        Files.writeString(root.resolve("palette.json"),new GsonBuilder().setPrettyPrinting().create().toJson(groups));
    }
}
