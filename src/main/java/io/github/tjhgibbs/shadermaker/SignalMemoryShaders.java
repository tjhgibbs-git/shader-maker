package io.github.tjhgibbs.shadermaker;

/**
 * Vanilla 26.3 post-chain memory. A chain's {@code "persistent": true} internal target survives
 * between frames while the chain stays applied (PostChain.getOrCreatePersistentTarget). Shader Maker
 * uses tiny persistent targets to (a) hold the last observed framebuffer signal when the current
 * frame has none (no sky / no terrain on screen) and (b) fade weights over game ticks rather than
 * snapping. Time comes from Globals.GameTime ((gameTime % 24000 + partial) / 24000), so fades
 * follow game ticks, pause with the game and are frame-rate independent. No CPU input or mod.
 *
 * Memory layout (RGBA8, width 1 + N, height 1): texel 0 = last GameTime packed into RGB (24 bit)
 * with A = 1 once initialised; texel i = weight of signal i-1 packed into RG (16 bit).
 */
public final class SignalMemoryShaders {
    private SignalMemoryShaders() {}
    /** Ticks for a held signal weight to travel 0 → 1. */
    public static final double SIGNAL_FADE_TICKS = 20;

    public static final String PACK_GLSL = """
        vec3 smPack24(float v) {
            float n = floor(clamp(v, 0.0, 1.0) * 16777215.0 + 0.5);
            float r = floor(n / 65536.0);
            float g = floor((n - r * 65536.0) / 256.0);
            return vec3(r, g, n - r * 65536.0 - g * 256.0) / 255.0;
        }
        float smUnpack24(vec3 c) {
            vec3 b = floor(c * 255.0 + 0.5);
            return (b.r * 65536.0 + b.g * 256.0 + b.b) / 16777215.0;
        }
        vec2 smPack16(float v) {
            float n = floor(clamp(v, 0.0, 1.0) * 65535.0 + 0.5);
            float hi = floor(n / 256.0);
            return vec2(hi, n - hi * 256.0) / 255.0;
        }
        float smUnpack16(vec2 c) {
            vec2 b = floor(c * 255.0 + 0.5);
            return (b.x * 256.0 + b.y) / 65535.0;
        }
        /** Raw game ticks since a stored time, modulo the 24000-tick GameTime cycle. */
        float smSinceTicks(vec4 clock) { return fract(GameTime - smUnpack24(clock.rgb) + 1.0) * 24000.0; }
        /** Per-frame step for held memory: 0 if uninitialised or apparently negative. */
        float smElapsedTicks(vec4 clock) {
            if (clock.a < 0.5) return 0.0;
            float dt = fract(GameTime - smUnpack24(clock.rgb) + 1.0) * 24000.0;
            return dt > 12000.0 ? 0.0 : dt;
        }
        """;

    private static String header(String samplers) {
        return """
            #version 330
            #extension GL_ARB_separate_shader_objects : require
            #include <minecraft:globals.glsl>
            """ + samplers + """
            layout(location = 0) in vec2 texCoord;
            layout(location = 0) out vec4 fragColor;
            """ + PACK_GLSL;
    }

    /**
     * Sky codes: Current is the 1x1 detector (R = (15 + 15 * index) / 255, or 0 for none / tie).
     * With a winner, that code's weight rises and every other weight falls. With none, all hold.
     */
    public static String holdSky() {
        return header("uniform sampler2D CurrentSampler;\nuniform sampler2D PrevSampler;\n") + """
            const float FADE_TICKS = %s;
            void main() {
                int x = int(gl_FragCoord.x);
                vec4 clock = texelFetch(PrevSampler, ivec2(0, 0), 0);
                if (x == 0) { fragColor = vec4(smPack24(GameTime), 1.0); return; }
                bool initialised = clock.a > 0.5;
                float prev = initialised ? smUnpack16(texelFetch(PrevSampler, ivec2(x, 0), 0).rg) : 0.0;
                float code = floor(texelFetch(CurrentSampler, ivec2(0), 0).r * 255.0 + 0.5);
                int winner = code > 0.0 ? int(code) / 15 - 1 : -1;
                float target = winner < 0 ? prev : (winner == x - 1 ? 1.0 : 0.0);
                // First frame after load/resize snaps; afterwards move at most dt / FADE_TICKS.
                float step = initialised ? smElapsedTicks(clock) / FADE_TICKS : 1.0;
                fragColor = vec4(smPack16(prev + clamp(target - prev, -step, step)), 0.0, 1.0);
            }
            """.formatted(SIGNAL_FADE_TICKS);
    }

    /** Render-signal sets: Current RGBA = four set bits, or 0.5 in every channel when unknown. */
    public static String holdContext() {
        return header("uniform sampler2D CurrentSampler;\nuniform sampler2D PrevSampler;\n") + """
            const float FADE_TICKS = %s;
            void main() {
                int x = int(gl_FragCoord.x);
                vec4 clock = texelFetch(PrevSampler, ivec2(0, 0), 0);
                if (x == 0) { fragColor = vec4(smPack24(GameTime), 1.0); return; }
                bool initialised = clock.a > 0.5;
                float prev = initialised ? smUnpack16(texelFetch(PrevSampler, ivec2(x, 0), 0).rg) : 0.0;
                vec4 current = texelFetch(CurrentSampler, ivec2(0), 0);
                bool known = abs(current.r - 0.5) > 0.25;
                float target = known ? current[min(x - 1, 3)] : prev;
                float step = initialised ? smElapsedTicks(clock) / FADE_TICKS : 1.0;
                fragColor = vec4(smPack16(prev + clamp(target - prev, -step, step)), 0.0, 1.0);
            }
            """.formatted(SIGNAL_FADE_TICKS);
    }

    /** JSON uniform block shared by a command effect's clock and grade passes. */
    public static final String FADE_BLOCK = "layout(std140) uniform CommandFade {\n    float FadeOut;\n    float FadeTicks;\n};\n";

    /**
     * Command-effect clock. A = 0: never written (persistent targets start cleared). A = 0.6: fading,
     * RGB = GameTime of the first applied frame. A = 1: fade finished; latched so the 24000-tick
     * GameTime wrap can never restart it. Removing the effect closes the target; re-adding restarts.
     */
    public static String startClock() {
        return header("uniform sampler2D PrevSampler;\n" + FADE_BLOCK) + """
            void main() {
                vec4 prev = texelFetch(PrevSampler, ivec2(0), 0);
                if (prev.a < 0.5) fragColor = vec4(smPack24(GameTime), 0.6);
                else if (prev.a < 0.9 && smSinceTicks(prev) >= FadeTicks) fragColor = vec4(prev.rgb, 1.0);
                else fragColor = prev;
            }
            """;
    }

    /** GLSL: 0 → 1 fade-in progress (or 1 → 0 when FadeOut) from the clock texel. */
    public static final String FADE_GLSL = """
        float smCommandFade(vec4 clock) {
            float progress = FadeTicks <= 0.0 || clock.a > 0.9 ? 1.0
                : smoothstep(0.0, 1.0, clamp(smSinceTicks(clock) / FadeTicks, 0.0, 1.0));
            return FadeOut > 0.5 ? 1.0 - progress : progress;
        }
        """;
}
