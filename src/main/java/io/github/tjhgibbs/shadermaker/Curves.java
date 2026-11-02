package io.github.tjhgibbs.shadermaker;

import java.util.List;
import java.util.Objects;

/** Immutable display-referred curves. Apply master first, then the channel curve. */
public final class Curves {
    private Curves() {}
    public record Point(double input, double output) {
        public Point {
            if (!Double.isFinite(input) || !Double.isFinite(output)
                    || input < 0 || input > 1 || output < 0 || output > 1)
                throw new IllegalArgumentException("Curve coordinates must be normalized and finite");
        }
    }
    public static final class Curve {
        private final List<Point> points;
        private final double[][] coefficients;
        public Curve(List<Point> points) {
            this.points = List.copyOf(points);
            int count = points.size();
            if (count < 2 || count > 16 || points.get(0).input != 0 || points.get(count-1).input != 1)
                throw new IllegalArgumentException("Curve needs 2–16 points with inputs from 0 to 1");
            double[] h = new double[count-1], d = new double[count-1], m = new double[count];
            for (int i=0; i<count-1; i++) {
                h[i] = points.get(i+1).input - points.get(i).input;
                if (h[i] < 1.0/255 - 1e-10) throw new IllegalArgumentException("Curve points must be ordered at least 1/255 apart");
                d[i] = (points.get(i+1).output-points.get(i).output)/h[i];
            }
            m[0]=d[0]; m[count-1]=d[count-2];
            for (int i=1; i<count-1; i++) {
                double a=d[i-1], b=d[i], w1=2*h[i]+h[i-1], w2=h[i]+2*h[i-1];
                m[i]=a*b<=0?0:(w1+w2)/(w1/a+w2/b);
            }
            coefficients=new double[count-1][4];
            for (int i=0; i<count-1; i++) {
                double delta=points.get(i+1).output-points.get(i).output;
                coefficients[i]=new double[]{points.get(i).output,h[i]*m[i],
                        3*delta-h[i]*(2*m[i]+m[i+1]),-2*delta+h[i]*(m[i]+m[i+1])};
            }
        }
        /** Compile the same cubic coefficients used by the CPU sampler. */
        public String glsl(String name) {
            StringBuilder out = new StringBuilder("float curve_" + name + "(float x) { x = clamp(x, 0.0, 1.0);\n");
            for (int i=0; i<coefficients.length; i++) {
                double[] c = coefficients[i];
                if (i<coefficients.length-1) out.append("if (x <= ").append(points.get(i+1).input).append(") ");
                out.append("{ float t = (x - ").append(points.get(i).input).append(") / ")
                    .append(points.get(i+1).input-points.get(i).input).append("; return clamp(((")
                    .append(c[3]).append(" * t + ").append(c[2]).append(") * t + ").append(c[1])
                    .append(") * t + ").append(c[0]).append(", 0.0, 1.0); }\n");
            }
            return out.append("}\n").toString();
        }
        public List<Point> points() { return points; }
        public double sample(double input) {
            if (!Double.isFinite(input)) throw new IllegalArgumentException("Invalid curve input");
            double x=Math.max(0,Math.min(1,input));
            int i=0; while(i<coefficients.length-1 && x>points.get(i+1).input) i++;
            double t=(x-points.get(i).input)/(points.get(i+1).input-points.get(i).input);
            double[] c=coefficients[i];
            return Math.max(0,Math.min(1,((c[3]*t+c[2])*t+c[1])*t+c[0]));
        }
    }
    private static final Curve LINEAR = new Curve(List.of(new Point(0,0),new Point(1,1)));
    private static final Channels IDENTITY = new Channels(LINEAR,LINEAR,LINEAR,LINEAR);
    public static Curve linear() { return LINEAR; }
    public static Channels identity() { return IDENTITY; }
    public record Channels(Curve master, Curve red, Curve green, Curve blue) {
        public Channels { Objects.requireNonNull(master);Objects.requireNonNull(red);Objects.requireNonNull(green);Objects.requireNonNull(blue); }
    }
}
