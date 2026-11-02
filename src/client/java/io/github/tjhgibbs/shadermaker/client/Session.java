package io.github.tjhgibbs.shadermaker.client;

import io.github.tjhgibbs.shadermaker.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** One authoring session per client. Disk failures never replace the current project. */
final class Session {
    static final Path ROOT = FabricLoader.getInstance().getConfigDir().resolve("shader-maker");
    private static Path draftPath = ROOT.resolve("draft.json");
    static Project project = Project.neutral();
    static History history;
    static int selectedLayer = -1;
    static String sampleBiome = "minecraft:plains";
    static double sampleY = 64, sampleTime = 6000;
    static Project grade() {
        if (selectedLayer < 0 || selectedLayer >= project.layers().size()) { selectedLayer = -1; return project; }
        return project.layers().get(selectedLayer).project();
    }
    static void editGrade(Project grade) {
        if (selectedLayer < 0 || selectedLayer >= project.layers().size()) { selectedLayer = -1; edit(grade.withLayers(project.layers())); return; }
        var layers = new ArrayList<>(project.layers()); var old = layers.get(selectedLayer);
        layers.set(selectedLayer, new Project.Layer(old.name(),grade,old.enabled(),old.strength(),old.biomes(),old.altitude(),old.time()));
        edit(project.withLayers(layers));
    }
    static String name = "My look", message = "Choose a preset or start with Neutral.";
    static boolean busy;
    static String applied = "";
    static PackCompiler.Activation activation = PackCompiler.Activation.ALWAYS;
    static final String PREVIEW = "file/shader-maker-preview.zip";
    static {
        Path draft = draftPath;
        if (Files.exists(draft)) {
            try { project = read(draft); message = "Restored your last draft."; }
            catch (Exception e) {
                draftPath = ROOT.resolve("draft-recovery.json");
                message = "Invalid draft retained; using a separate recovery draft.";
                if (Files.exists(draftPath)) {
                    try { project = read(draftPath); }
                    catch (Exception ignored) {
                        // Preserve both unreadable files instead of replacing either one.
                        draftPath = null;
                        message = "Drafts could not be restored; originals retained. Save the project manually.";
                    }
                }
            }
        }
        history = new History(project);
    }
    static void edit(Project next) { project = next; }
    static void commit() {
        history.commit(project);
        try { if (draftPath != null) PackCompiler.saveProject(draftPath,project); }
        catch (IOException e) { message = "Draft could not be saved: " + e.getMessage(); }
    }
    static void undo(boolean redo) {
        String before = selectedLayer >= 0 && selectedLayer < project.layers().size() ? project.layers().get(selectedLayer).name() : null;
        project = redo ? history.redo() : history.undo();
        // Keep editing the same layer when it is still there; otherwise fall back to the base and say so.
        if (before != null && selectedLayer < project.layers().size() && project.layers().get(selectedLayer).name().equals(before)) { saveDraft(); return; }
        if (selectedLayer >= 0) message = "Undo changed the layer list; now editing the base grade.";
        selectedLayer = -1; saveDraft();
    }
    private static String shownMessage = ""; private static long shownAt;
    /** Status text fades after a few seconds so old results don't look current. */
    static String visibleMessage() {
        long now = System.currentTimeMillis();
        if (!message.equals(shownMessage)) { shownMessage = message; shownAt = now; }
        if (!message.isEmpty() && now - shownAt > 8000) { message = ""; shownMessage = ""; }
        return message;
    }
    /** Smallest "Layer N" not already used. */
    static String freeLayerName() {
        var used = new HashSet<String>(); project.layers().forEach(l -> used.add(l.name()));
        for (int i = 1; ; i++) if (!used.contains("Layer " + i)) return "Layer " + i;
    }
    /** base + suffix (" copy", " copy 2"…) truncated so the whole name stays within 64 characters and is unused. */
    static String freeCopyName(String base) {
        var used = new HashSet<String>(); project.layers().forEach(l -> used.add(l.name()));
        for (int i = 1; ; i++) {
            String suffix = i == 1 ? " copy" : " copy " + i;
            String name = (base.length() + suffix.length() > 64 ? base.substring(0, 64 - suffix.length()) : base) + suffix;
            if (!used.contains(name)) return name;
        }
    }
    private static void saveDraft() {
        try { if (draftPath != null) PackCompiler.saveProject(draftPath,project); }
        catch (IOException e) { message = "Draft could not be saved: " + e.getMessage(); }
    }
    static Project read(Path path) throws IOException {
        if (Files.size(path)>1_048_576) throw new IOException("Project exceeds 1 MB");
        return Project.decode(Files.readString(path));
    }
    static Path projects() { return ROOT.resolve("projects"); }
    static String fileName() {
        String slug = name.strip().replaceAll("[^a-zA-Z0-9 _-]", "").replaceAll(" +", "-");
        if (slug.isBlank()) throw new IllegalArgumentException("Enter a project name using letters or numbers");
        return slug.substring(0,Math.min(48,slug.length()));
    }
    static void save() {
        try { Path path = projects().resolve(fileName()+".json"); PackCompiler.saveProject(path,project); commit(); message = "Saved " + path.getFileName(); }
        catch (Exception e) { message = "Save failed: " + e.getMessage(); }
    }
    static void load(Path file) {
        try { Project next = read(file); project = next; selectedLayer = -1; name = file.getFileName().toString().replaceFirst("\\.json$", ""); commit(); message = "Loaded " + file.getFileName(); }
        catch (Exception e) { message = "Load failed: " + e.getMessage(); }
    }
    static void export() {
        try {
            Path path = Minecraft.getInstance().gameDirectory.toPath().resolve("shader-maker/exports").resolve(fileName()+"-"+activation.name().toLowerCase(Locale.ROOT)+".zip");
            PackCompiler.writeZip(path,project,activation);
            message = "Exported " + path.getFileName();
        } catch (Exception e) { message = "Export failed: " + e.getMessage(); }
    }
    static boolean active() { return Minecraft.getInstance().getResourcePackRepository().getSelectedIds().contains(PREVIEW); }
    static void apply(boolean enable) {
        if (busy) return;
        if (enable && project.layers().stream().anyMatch(Project.Layer::enabled)) {
            message = "Preview failed: active grade layers require a live renderer.";
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (enable && active()) {
            // Windows cannot replace a zip the game still has open. Unselect and reload to release it, then write the new one.
            try {
                var repository = mc.getResourcePackRepository();
                repository.removePack(PREVIEW);
                persistSelection(mc,repository);
            } catch (Exception e) { message = "Preview failed: " + e.getMessage(); return; }
            busy = true; message = "Releasing previous preview…";
            mc.reloadResourcePacks().whenComplete((ignored,error) -> mc.execute(() -> {
                busy = false;
                if (error != null) { message = "Resource reload failed. Check the game log."; return; }
                applyNow(true);
            }));
            return;
        }
        applyNow(enable);
    }
    private static void persistSelection(Minecraft mc, net.minecraft.server.packs.repository.PackRepository repository) {
        mc.options.resourcePacks.clear(); mc.options.incompatibleResourcePacks.clear();
        for (var pack : repository.getSelectedPacks()) {
            if (pack.isFixedPosition()) continue;
            mc.options.resourcePacks.add(pack.getId());
            if (!pack.getCompatibility().isCompatible()) mc.options.incompatibleResourcePacks.add(pack.getId());
        }
        mc.options.save();
    }
    private static void applyNow(boolean enable) {
        Minecraft mc = Minecraft.getInstance();
        try {
            if (enable) PackCompiler.writeZip(mc.getResourcePackDirectory().resolve("shader-maker-preview.zip"),project,PackCompiler.Activation.ALWAYS);
            var repository = mc.getResourcePackRepository();
            repository.reload();
            if (enable) {
                if (!repository.isAvailable(PREVIEW)) throw new IOException("Preview resource pack was not discovered");
                repository.addPack(PREVIEW);
            } else repository.removePack(PREVIEW);
            // updateResourcePacks also starts an unobservable reload. Persist the selected IDs here
            // so this action owns exactly one reload future, including reapplying an active pack.
            persistSelection(mc,repository);
            busy = true; message = "Reloading game resources…";
            String snapshot = project.encode();
            mc.reloadResourcePacks().whenComplete((ignored,error) -> mc.execute(() -> {
                busy = false;
                if (error != null) { message = "Resource reload failed. Check the game log."; return; }
                if (enable && (!active() || mc.getShaderManager().getPostChain(Identifier.withDefaultNamespace("end_of_frame"),Set.of(Identifier.withDefaultNamespace("main")))==null)) {
                    applied=""; message="Game rejected the preview shader. Check the log or Disable."; return;
                }
                applied = enable ? snapshot : "";
                message = enable ? "Grade applied to game. Use Disable to restore." : "Preview disabled.";
            }));
        } catch (Exception e) { busy = false; message = "Preview failed: " + e.getMessage(); }
    }
}
