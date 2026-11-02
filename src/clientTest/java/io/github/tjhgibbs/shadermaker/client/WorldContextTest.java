package io.github.tjhgibbs.shadermaker.client;

import net.minecraft.world.clock.ClockInstance;

public final class WorldContextTest {
    private static ClockInstance clock(long ticks, float partial) {
        return new ClockInstance() {
            public long totalTicks() { return ticks; }
            public float partialTick() { return partial; }
            public float rate() { return 1; }
            public boolean isPaused() { return false; }
        };
    }
    public static void main(String[] args) {
        if (WorldContext.capture(null).isPresent() || WorldContext.timeOfDay(null).isPresent()) throw new AssertionError("Missing context");
        if (WorldContext.timeOfDay(clock(24001,.5f)).orElseThrow() != 1.5) throw new AssertionError("Clock wrap");
        if (WorldContext.timeOfDay(clock(-1,0)).orElseThrow() != 23999) throw new AssertionError("Negative clock wrap");
        if (WorldContext.timeOfDay(clock(0,Float.NaN)).isPresent()) throw new AssertionError("Invalid clock");
        System.out.println("WorldContextTest: missing context and circular clock passed");
    }
}
