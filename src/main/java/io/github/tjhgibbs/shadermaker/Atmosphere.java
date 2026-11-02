package io.github.tjhgibbs.shadermaker;

import java.util.Set;
import java.util.Map;

/** Version-independent color grading and context blending for the editor and renderer. */
public final class Atmosphere {
    private Atmosphere() {}

    public record Color(double r, double g, double b, double a) {
        public Color { if (!finite(r,g,b,a) || r<0 || r>1 || g<0 || g>1 || b<0 || b>1 || a<0 || a>1)
            throw new IllegalArgumentException("Color must be normalized and finite"); }
    }

    /** Levels use normalized input black/white points and a positive midtone gamma. */
    public record Grade(double exposure, double black, double white, double gamma,
                        double contrast, double saturation, double red, double green, double blue,
                        double vignette, double shoulder, double vibrance) {
        /** Compatibility constructor for version 1 project grades. */
        public Grade(double exposure, double black, double white, double gamma, double contrast,
                     double saturation, double red, double green, double blue, double vignette) {
            this(exposure,black,white,gamma,contrast,saturation,red,green,blue,vignette,0,0);
        }
        public Grade {
            if (!finite(exposure, black, white, gamma, contrast, saturation, red, green, blue, vignette, shoulder, vibrance)
                    || black < 0 || white > 1 || black >= white || gamma <= 0
                    || contrast < 0 || saturation < 0 || red < 0 || green < 0 || blue < 0
                    || shoulder < 0 || shoulder > 1 || vibrance < -1 || vibrance > 1
                    || vignette < 0 || vignette > 1) throw new IllegalArgumentException("Invalid grade");
        }
        public static Grade neutral() { return new Grade(0, 0, 1, 1, 1, 1, 1, 1, 1, 0); }
        public Color apply(Color color, double x, double y, double strength) {
            return apply(color,x,y,strength,Curves.identity());
        }
        /** Curves run after levels/contrast, before saturation, tint and vignette. */
        public Color apply(Color color, double x, double y, double strength, Curves.Channels curves) {
            if (!finite(x,y,strength)) throw new IllegalArgumentException("Invalid sample coordinates or strength");
            double amount = clamp(strength);
            double r = curves.red().sample(curves.master().sample(level(color.r)));
            double g = curves.green().sample(curves.master().sample(level(color.g)));
            double b = curves.blue().sample(curves.master().sample(level(color.b)));
            double luminance = .2126*r + .7152*g + .0722*b;
            double chroma = Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b));
            double sat = saturation * (1 + vibrance * (1-chroma));
            double edge = clamp(Math.sqrt(x*x + y*y) / Math.sqrt(2));
            double shade = 1 - vignette * edge * edge;
            r = clamp((luminance + (r-luminance)*sat)*red*shade);
            g = clamp((luminance + (g-luminance)*sat)*green*shade);
            b = clamp((luminance + (b-luminance)*sat)*blue*shade);
            return new Color(mix(color.r, r, amount), mix(color.g, g, amount), mix(color.b, b, amount), color.a);
        }
        private double level(double value) {
            double v = Math.max(0, (value * Math.pow(2, exposure) - black) / (white-black));
            double soft = v <= .75 ? v : .75 + .25 * (1-Math.exp(-(v-.75)/.25));
            v = clamp(mix(v,soft,shoulder));
            return clamp((Math.pow(v, 1/gamma)-.5)*contrast+.5);
        }
    }

    public record Context(double dayTime, double y, double yaw, String biome, double seconds) {
        public Context { if (!finite(dayTime,y,yaw,seconds) || biome == null)
            throw new IllegalArgumentException("Invalid world context"); }
    }

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
            if (!Double.isFinite(v)) throw new IllegalArgumentException("Invalid range sample");
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
            if (!Double.isFinite(v)) throw new IllegalArgumentException("Invalid circular sample");
            double span = mod(end-start, period), position = mod(v-start, period);
            if (position > span) return 0;
            return feather == 0 ? 1 : smooth(Math.min(position/feather, (span-position)/feather));
        }
    }
    /** Interpolate biome membership across a caller-timed transition using smoothstep.
     * Duration is selected by the runtime; elapsed <= 0 starts at the old biome. */
    public static Map<String, Double> biomeTransition(String from, String to, double elapsed, double duration) {
        if (from == null || to == null || !finite(elapsed, duration) || duration < 0)
            throw new IllegalArgumentException("Invalid biome transition");
        if (from.equals(to)) return Map.of(to, 1.0);
        double t = duration == 0 ? 1 : smooth(elapsed / duration);
        return Map.of(from, 1-t, to, t);
    }
    public static Color blend(Color from, Color to, double weight) {
        if (!Double.isFinite(weight) || weight < 0 || weight > 1) throw new IllegalArgumentException("Invalid blend weight");
        return new Color(mix(from.r,to.r,weight),mix(from.g,to.g,weight),mix(from.b,to.b,weight),mix(from.a,to.a,weight));
    }
    private static double mod(double x, double p) { return ((x % p) + p) % p; }
    private static double smooth(double x) { x=clamp(x); return x*x*(3-2*x); }
    private static double clamp(double x) { return Math.max(0, Math.min(1, x)); }
    private static double mix(double a, double b, double t) { return a+(b-a)*t; }
    private static boolean finite(double... values) { for (double v : values) if (!Double.isFinite(v)) return false; return true; }
}
