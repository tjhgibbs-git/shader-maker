package io.github.tjhgibbs.shadermaker;

import java.util.ArrayList;
import java.util.List;
import static io.github.tjhgibbs.shadermaker.Curves.*;

public class CurvesTest {
    private static void near(double a,double b) { if (!Double.isFinite(a)||Math.abs(a-b)>1e-9) throw new AssertionError(a+" != "+b); }
    private static void invalid(Runnable r) { try { r.run(); } catch (IllegalArgumentException expected) { return; } throw new AssertionError("Expected invalid curve"); }
    public static void main(String[] args) {
        for(int i=0;i<=255;i++) near(linear().sample(i/255.0),i/255.0);
        var points=new ArrayList<>(List.of(new Point(0,0),new Point(.25,.19),new Point(.75,.81),new Point(1,1)));
        var s=new Curve(points); points.clear();
        near(s.sample(.25),.19);near(s.sample(.75),.81);near(s.sample(.5),.5);
        double previous=0;
        for(int i=0;i<=1000;i++) { double v=s.sample(i/1000.0);if(v<previous)throw new AssertionError("Overshoot");previous=v; }
        var lift=new Curve(List.of(new Point(0,.1),new Point(1,1)));
        var red=new Curve(List.of(new Point(0,0),new Point(1,.8)));
        var input=new Atmosphere.Color(.25,.5,.75,.3);
        var out=Atmosphere.Grade.neutral().apply(input,0,0,1,new Channels(lift,red,linear(),linear()));
        near(out.r(),(.1+.9*.25)*.8);near(out.g(),.55);near(out.b(),.775);near(out.a(),.3);
        near(Atmosphere.Grade.neutral().apply(input,0,0,0,new Channels(lift,red,linear(),linear())).r(),input.r());
        invalid(()->new Curve(List.of(new Point(0,0),new Point(.5,.5),new Point(.5,.6),new Point(1,1))));
        invalid(()->linear().sample(Double.NaN));
        invalid(()->new Curve(List.of(new Point(.1,0),new Point(1,1))));
        System.out.println("Curves checks passed");
    }
}
