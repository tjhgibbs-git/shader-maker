import io.github.tjhgibbs.shadermaker.*;
import java.nio.file.*;
import java.util.*;

/** Ready-made projects for the in-game test guide. Usage: TestProjects.java <projects dir> <command demo project json> */
public class TestProjects {
    public static void main(String[] a) throws Exception {
        Path dir = Path.of(a[0]); Files.createDirectories(dir);
        Files.copy(Path.of(a[1]), dir.resolve("Test-1-Command-demo.json"), StandardCopyOption.REPLACE_EXISTING);
        var none = WorldSignals.Controls.NONE;
        var orange = Project.neutral().with("red", 1.5).with("green", .85).with("blue", .5);
        write(dir, "Test-2-Area", new Project.Layer("Spawn area", orange, true, 1, Set.of(), null, null, null, null, null, none,
            Project.Options.NONE.withArea(new Project.Area(-124, -100, -552, -528, 4))));
        var snowy = VisualBiomes.forBiome("minecraft:snowy_plains").orElseThrow().id();
        write(dir, "Test-3-Sky-hold", new Project.Layer("Snowy sky", Project.neutral().with("saturation", .3).with("blue", 1.5).with("red", .75), true, 1, Set.of(), null, null, null, null, snowy, none, Project.Options.NONE));
        var nv = new WorldSignals.Controls(List.of(WorldSignals.Condition.colour(WorldSignals.Source.NIGHT_VISION_COLOR, 0x9C9999, .006, false)), false);
        write(dir, "Test-4-Biome-id", new Project.Layer("Snowy plains (datapack id)", Project.neutral().with("red", 1.6).with("green", .6).with("blue", 1.6), true, 1, Set.of(), null, null, null, null, null, nv, Project.Options.NONE));
    }
    static void write(Path dir, String name, Project.Layer layer) throws Exception {
        var p = Project.neutral().withLayers(List.of(layer));
        if (!PackCompiler.issues(p).isEmpty()) throw new IllegalStateException(PackCompiler.issues(p).toString());
        Files.writeString(dir.resolve(name + ".json"), p.encode());
    }
}
