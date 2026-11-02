package io.github.tjhgibbs.shadermaker;

import java.util.Set;

public class AtmosphereTest {
    private static void near(double actual, double expected) {
        if (Math.abs(actual-expected)>1e-9) throw new AssertionError(actual + " != " + expected);
    }
    public static void main(String[] args) {
        var color = new Atmosphere.Color(.2,.4,.8,.3);
        var neutral = Atmosphere.Grade.neutral().apply(color, 1, 1, 1);
        near(neutral.r(), .2); near(neutral.g(), .4); near(neutral.b(), .8); near(neutral.a(), .3);
        var grade = new Atmosphere.Grade(1,0,1,1,1,1,1,1,1,.5);
        near(grade.apply(color,0,0,0).r(), .2);
        near(grade.apply(color,0,0,1).r(), .4);
        var mask = new Atmosphere.Mask(Set.of("minecraft:plains"), new Atmosphere.Range(0,100,10),
                new Atmosphere.CircularRange(22000,2000,24000,0),
                new Atmosphere.CircularRange(350,10,360,0), 4, 1);
        near(mask.strength(new Atmosphere.Context(0,50,0,"minecraft:plains",0)),1);
        near(mask.strength(new Atmosphere.Context(12000,50,0,"minecraft:plains",0)),0);
        near(mask.strength(new Atmosphere.Context(0,50,180,"minecraft:plains",0)),0);
        near(mask.strength(new Atmosphere.Context(0,50,0,"minecraft:desert",0)),0);
        near(mask.strength(new Atmosphere.Context(0,50,0,"minecraft:plains",2)),0);
        near(new Atmosphere.Range(0,100,10).weight(5),.5);
        System.out.println("Atmosphere checks passed");
    }
}
