package io.github.tjhgibbs.shadermaker;

import java.util.Set;

/** Version-independent color grading and context blending for the editor and renderer. */
public final class Atmosphere {
    private Atmosphere() {}

    public record Color(double r, double g, double b, double a) {}

    /** Levels use normalized input black/white points and a positive midtone gamma. */
    public record Grade(double exposure, double black, double white, double gamma,
                        double contrast, double saturation, double red, double green, double blue,
                        double vignette) {
        public Grade {
            if (!finite(exposure, black, white, gamma, contrast, saturation, red, green, blue, vignette)
                    || black < 0 || white > 1 || black >= white || gamma <= 0
                    || contrast < 0 || saturation < 0 || red < 0 || green < 0 || blue < 0
                    || vignette < 0 || vignette > 1) throw new IllegalArgumentException("Invalid grade");
        }
        public static Grade neutral() { return new Grade(0, 0, 1, 1, 1, 1, 1, 1, 1, 0); }
        public Color apply(Color color, double x, double y, double strength) {
            double amount = clamp(strength);
            double r = level(color.r), g = level(color.g), b = level(color.b);
            double luminance = .2126*r + .7152*g + .0722*b;
            double edge = clamp(Math.sqrt(x*x + y*y) / Math.sqrt(2));
            double shade = 1 - vignette * edge * edge;
            r = clamp((luminance + (r-luminance)*saturation)*red*shade);
            g = clamp((luminance + (g-luminance)*saturation)*green*shade);
            b = clamp((luminance + (b-luminance)*saturation)*blue*shade);
            return new Color(mix(color.r, r, amount), mix(color.g, g, amount), mix(color.b, b, amount), color.a);
        }
        private double level(double value) {
            double v = clamp((value * Math.pow(2, exposure) - black) / (white-black));
            return clamp((Math.pow(v, 1/gamma)-.5)*contrast+.5);
        }
    }

    public record Context(double dayTime, double y, double yaw, String biome, double seconds) {}

    /** Optional masks multiply together. Wrapped angles/time are measured in degrees/ticks. */
    public record Mask(Set<String> biomes, Range altitude, CircularRange time, CircularRange facing,
                       double pulsePeriod, double pulseDepth) {
        public Mask {
            biomes = Set.copyOf(biomes);
            if (!finite(pulsePeriod, pulseDepth) || pulsePeriod < 0 || pulseDepth < 0 || pulseDepth > 1)
                throw new IllegalArgumentException("Invalid pulse");
        }
        public double strength(Context c) {
            double result = biomes.isEmpty() || biomes.contains(c.biome) ? 1 : 0;
            if (altitude != null) result *= altitude.weight(c.y);
            if (time != null) result *= time.weight(c.dayTime);
            if (facing != null) result *= facing.weight(c.yaw);
            if (pulsePeriod > 0) result *= 1 - pulseDepth * (0.5 - 0.5 * Math.cos(2 * Math.PI * c.seconds / pulsePeriod));
            return result;
        }
    }
    public record Range(double min, double max, double feather) {
        public Range { if (!finite(min,max,feather) || min > max || feather < 0) throw new IllegalArgumentException("Invalid range"); }
        public double weight(double v) {
            if (v < min || v > max) return 0;
            return feather == 0 ? 1 : smooth(Math.min((v-min)/feather, (max-v)/feather));
        }
    }
    public record CircularRange(double start, double end, double period, double feather) {
        public CircularRange {
            if (!finite(start,end,period,feather) || period <= 0 || feather < 0 || feather > period/2)
                throw new IllegalArgumentException("Invalid circular range");
        }
        public double weight(double v) {
            double span = mod(end-start, period), position = mod(v-start, period);
            if (position > span) return 0;
            return feather == 0 ? 1 : smooth(Math.min(position/feather, (span-position)/feather));
        }
    }
    private static double mod(double x, double p) { return ((x % p) + p) % p; }
    private static double smooth(double x) { x=clamp(x); return x*x*(3-2*x); }
    private static double clamp(double x) { return Math.max(0, Math.min(1, x)); }
    private static double mix(double a, double b, double t) { return a+(b-a)*t; }
    private static boolean finite(double... values) { for (double v : values) if (!Double.isFinite(v)) return false; return true; }
}
