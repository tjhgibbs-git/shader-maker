package io.github.tjhgibbs.shadermaker.client;
import dev.blockscraper.mod.BlockScraper;
import dev.blockscraper.mod.BlockScraperPlugin;
public final class ScraperScreens implements BlockScraperPlugin {
    @Override public void registerScreens() {
        BlockScraper.register("shader-maker", args -> new LayerScreen(args.parent()));
        BlockScraper.register("shader-maker-grade", args -> new EditorScreen(new LayerScreen(args.parent())));
        BlockScraper.register("shader-maker-fog", args -> new FogScreen(new LayerScreen(args.parent())));
        BlockScraper.register("shader-maker-curves", args -> new CurveScreen(args.parent()));
        BlockScraper.register("shader-maker-projects", args -> new LibraryScreen(args.parent()));
    }
}
