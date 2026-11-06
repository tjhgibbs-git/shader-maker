package io.github.tjhgibbs.shadermaker;

import com.google.gson.*;
import java.util.*;

/** Resolved vanilla render inputs, never biome or immersion identities. */
public final class WorldSignals {
    private WorldSignals() {}
    public static final String WARNING = "Experimental vanilla 26.3 signal. Full-screen mode needs matching solid/cutout terrain samples on a 32×32 grid (at least 3 and 1/16 of marked terrain); with no marked terrain on screen the last result is kept and changes cross-fade over about one second. Blocks only covers marked solid/cutout terrain pixels, not entities, block entities, translucent blocks, sky or particles. Transparency/overlays can suppress or mimic markers; this is not exact segmentation. Other packs can alter the signal. Minecraft device validation pending.";
    public enum Source {
        FOG_COLOR("Resolved fog colour", "FogColor.rgb", false, "Air, underwater, lava or status fog, whichever vanilla supplies. Not an underwater detector. Sky mix, sunset direction, weather and effects can change it."),
        FOG_START("Resolved fog start", "FogEnvironmentalStart", false, "Blocks. Weather, immersion, boss and status fog modify or replace this value."),
        FOG_END("Resolved fog end", "FogEnvironmentalEnd", false, "Blocks. Weather, water vision, immersion, boss and status fog modify or replace this value."),
        SKY_FOG_END("Resolved sky fog end", "FogSkyEnd", false, "Blocks. Available on terrain without visible sky; capped by render distance and replaced by some fog environments."),
        CLOUD_FOG_END("Resolved cloud fog end", "FogCloudsEnd", false, "Blocks. Available without visible clouds; capped by cloud distance and replaced by some fog environments."),
        AMBIENT_COLOR("Ambient light colour", "lightmapInfo.AmbientColor", true, "Raw lightmap input at the camera, before gamma, local light levels and night vision composition. Datapack attributes can change it."),
        BLOCK_LIGHT_TINT("Block-light tint", "lightmapInfo.BlockLightTint", true, "Raw global block-light tint, not a particular torch colour or the light level at the camera."),
        SKY_LIGHT_COLOR("Skylight colour", "lightmapInfo.SkyLightColor", true, "Raw lightmap colour input, not the visible sky colour or local skylight level. Timelines and environment attributes can change it."),
        NIGHT_VISION_COLOR("Night-vision colour input", "lightmapInfo.NightVisionColor", true, "The configured colour is available even without Night Vision. This does not detect whether the effect is active."),
        SURFACE_TINT("Terrain vertex tint (grass / leaves)", "smSurfaceTint", false, "Terrain vertex RGB before the lightmap; may include baked shading/AO. Any tinted terrain can match; cannot distinguish grass, foliage and water types. Untinted white terrain also matches white. Translucent water cannot carry a marker; water-only views have no signal.");
        public final String label, expression, help;
        public final boolean lightmap;
        Source(String label,String expression,boolean lightmap,String help){this.label=label;this.expression=expression;this.lightmap=lightmap;this.help=help;}
        public boolean colour(){return this==FOG_COLOR || lightmap || this==SURFACE_TINT;}
        @Override public String toString(){return label;}
    }
    /** Range bounds are inclusive. Colour tolerance is maximum per-channel error, in RGB or peak-normalized hue. */
    public record Condition(Source source, int rgb, double tolerance, boolean hue, double min, double max) {
        public Condition {
            Objects.requireNonNull(source);
            if(rgb<0||rgb>0xffffff||!Double.isFinite(tolerance)||tolerance<0||tolerance>1||!Double.isFinite(min)||!Double.isFinite(max)||min>max||Math.abs(min)>1e30||Math.abs(max)>1e30)
                throw new IllegalArgumentException("Invalid render signal condition");
            if(source.colour()) {
                if(min!=0||max!=0)throw new IllegalArgumentException("Colour signals do not have distance bounds");
                if(hue&&rgb==0)throw new IllegalArgumentException("Black has no hue; choose RGB matching");
            } else if(rgb!=0||tolerance!=0||hue)throw new IllegalArgumentException("Distance signals require range bounds only");
        }
        public static Condition colour(Source source,int rgb,double tolerance,boolean hue){return new Condition(source,rgb,tolerance,hue,0,0);}
        public static Condition range(Source source,double min,double max){return new Condition(source,0,0,false,min,max);}
        public boolean matches(Sample value) {
            if(value==null)return false;
            if(!source.colour())return value.r>=min&&value.r<=max;
            double[] actual={value.r,value.g,value.b},target={(rgb>>16&255)/255.0,(rgb>>8&255)/255.0,(rgb&255)/255.0};
            if(hue){double a=Math.max(actual[0],Math.max(actual[1],actual[2])),b=Math.max(target[0],Math.max(target[1],target[2]));if(a<.001)return false;for(int i=0;i<3;i++){actual[i]/=a;target[i]/=b;}}
            for(int i=0;i<3;i++)if(Math.abs(actual[i]-target[i])>tolerance)return false;
            return true;
        }
        public String label(){return source.label+": "+(source.colour()?String.format(Locale.ROOT,"#%06X ±%.3f (%s)",rgb,tolerance,hue?"hue":"RGB"):min+"–"+max+" blocks");}
        public String glsl(){
            if(!source.colour())return "("+source.expression+" >= "+min+" && "+source.expression+" <= "+max+")";
            return "smColourMatch("+source.expression+", vec3("+(rgb>>16&255)/255.0+", "+(rgb>>8&255)/255.0+", "+(rgb&255)/255.0+"), "+tolerance+", "+hue+")";
        }
        public JsonObject json(){var j=new JsonObject();j.addProperty("source",source.name());if(source.colour()){j.addProperty("rgb",rgb);j.addProperty("tolerance",tolerance);j.addProperty("hue",hue);}else{j.addProperty("min",min);j.addProperty("max",max);}return j;}
        public static Condition read(JsonObject j){var source=Source.valueOf(j.get("source").getAsString());if(source.colour()){var h=j.getAsJsonPrimitive("hue");if(h==null||!h.isBoolean())throw new IllegalArgumentException("Missing colour match mode");double rgb=number(j,"rgb");if(rgb!=Math.rint(rgb))throw new IllegalArgumentException("RGB must be an integer");return colour(source,(int)rgb,number(j,"tolerance"),h.getAsBoolean());}return range(source,number(j,"min"),number(j,"max"));}
    }
    public record Controls(List<Condition> conditions, boolean blocksOnly) {
        public static final Controls NONE=new Controls(List.of(),false);
        public Controls {conditions=List.copyOf(conditions);var seen=EnumSet.noneOf(Source.class);for(var c:conditions)if(!seen.add(c.source()))throw new IllegalArgumentException("One condition per render signal; edit or remove the existing condition");conditions=conditions.stream().sorted(Comparator.comparing(c->c.source().ordinal())).toList();}
        public boolean active(){return blocksOnly||!conditions.isEmpty();}
        public boolean matches(Samples samples){return samples!=null&&samples.terrainVisible&&(!blocksOnly||samples.terrainPixel)&&conditions.stream().allMatch(c->c.matches(samples.values.get(c.source())));}
        public JsonObject json(){var j=new JsonObject();j.addProperty("blocksOnly",blocksOnly);var a=new JsonArray();conditions.forEach(c->a.add(c.json()));j.add("conditions",a);return j;}
        public static Controls read(JsonObject j){var scope=j.getAsJsonPrimitive("blocksOnly");if(scope==null||!scope.isBoolean())throw new IllegalArgumentException("Missing signal scope");var list=new ArrayList<Condition>();for(var c:j.getAsJsonArray("conditions"))list.add(Condition.read(c.getAsJsonObject()));return new Controls(list,scope.getAsBoolean());}
    }
    public record Sample(double r,double g,double b) {
        public Sample {if(!Double.isFinite(r)||!Double.isFinite(g)||!Double.isFinite(b))throw new IllegalArgumentException("Invalid signal sample");}
        public static Sample colour(int rgb){return new Sample((rgb>>16&255)/255.0,(rgb>>8&255)/255.0,(rgb&255)/255.0);}
        public static Sample distance(double value){return new Sample(value,0,0);}
    }
    public record Samples(Map<Source,Sample> values,boolean terrainVisible,boolean terrainPixel){public Samples(Map<Source,Sample> values,boolean terrainVisible){this(values,terrainVisible,true);}public Samples {values=Map.copyOf(values);}public static final Samples EMPTY=new Samples(Map.of(),false,false);}
    public static List<List<Condition>> palette(Project p){var result=new ArrayList<List<Condition>>();for(var l:p.layers())if(l.enabled()&&l.contextControls().active()&&!result.contains(l.contextControls().conditions()))result.add(l.contextControls().conditions());return List.copyOf(result);}
    public static boolean active(Project p){return !palette(p).isEmpty();}
    public static boolean lighting(Project p){return palette(p).stream().flatMap(List::stream).anyMatch(c->c.source.lightmap);}
    public static int index(Project p,Controls controls){return palette(p).indexOf(controls.conditions());}
    private static double number(JsonObject j,String key){var v=j.getAsJsonPrimitive(key);if(v==null||!v.isNumber())throw new IllegalArgumentException("Missing signal number: "+key);return v.getAsDouble();}
    public static final String COLOUR_GLSL="""
        bool smColourMatch(vec3 actual, vec3 target, float tolerance, bool hue) {
            if (hue) {
                float peak = max(actual.r,max(actual.g,actual.b));
                if (peak < 0.001) return false;
                actual /= peak;
                target /= max(target.r,max(target.g,target.b));
            }
            return all(lessThanEqual(abs(actual-target),vec3(tolerance)));
        }
        """;
}
