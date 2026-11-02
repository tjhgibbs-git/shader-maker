package io.github.tjhgibbs.shadermaker.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.clock.ClockInstance;
import java.util.Optional;
import java.util.OptionalDouble;

/** A snapshot of the current camera context. Absence is distinct from any valid world value. */
public record WorldContext(String dimension, double cameraY, float yaw,
                           Optional<String> biome, OptionalDouble dayTime) {
    /** Call on the client thread after the camera has been initialized for the current world. */
    public static Optional<WorldContext> capture(Minecraft mc) {
        if (mc == null || mc.level == null) return Optional.empty();
        var level = mc.level;
        var camera = mc.gameRenderer.mainCamera();
        if (!camera.isInitialized() || camera.entity() == null || camera.entity().level() != level)
            return Optional.empty();
        var pos = camera.blockPosition();
        // Avoid sampling when the camera chunk is unloaded.
        Optional<String> biome = level.hasChunkAt(pos)
            ? level.getBiome(pos).unwrapKey().map(key -> key.identifier().toString()) : Optional.empty();
        OptionalDouble time = level.dimensionType().defaultClock().isPresent()
            ? timeOfDay(level.clockManager().getInstance(level.dimensionType().defaultClock().orElseThrow()))
            : OptionalDouble.empty();
        return Optional.of(new WorldContext(level.dimension().identifier().toString(), camera.position().y,
            camera.yaw(), biome, time));
    }

    /** Clock ticks are circular. Missing clocks never become an artificial time of zero. */
    static OptionalDouble timeOfDay(ClockInstance clock) {
        if (clock == null || !Float.isFinite(clock.partialTick())) return OptionalDouble.empty();
        double ticks = Math.floorMod(clock.totalTicks(), 24000L) + clock.partialTick();
        return OptionalDouble.of(((ticks % 24000) + 24000) % 24000);
    }
}
