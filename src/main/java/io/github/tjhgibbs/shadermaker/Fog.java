package io.github.tjhgibbs.shadermaker;

import com.google.gson.*;
import java.util.*;

/** Authored terrain medium. All objects use the resolved native fog colour and add optical depth. */
public record Fog(String name, boolean enabled, Shape shape, Falloff falloff,
                  Map<String,Double> values, Atmosphere.Range altitude, int samples) {
    public enum Shape { EVERYWHERE, BELOW_HEIGHT, HEIGHT_BAND, BOX, SPHERE }
    public enum Falloff { NATURAL, LINEAR }
    public static final Map<String,Double> DEFAULTS = Map.ofEntries(
        Map.entry("strength",1d),Map.entry("start",0d),Map.entry("end",128d),Map.entry("visibility",64d),
        Map.entry("lower",62d),Map.entry("upper",78d),Map.entry("x",0d),Map.entry("y",70d),Map.entry("z",0d),
        Map.entry("width",32d),Map.entry("height",16d),Map.entry("depth",32d),Map.entry("radius",16d),Map.entry("fade",0d));
    public Fog {
        if(name==null||name.isBlank()||name.length()>64||name.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Fog name must contain 1–64 printable characters");
        name=name.strip();Objects.requireNonNull(shape);Objects.requireNonNull(falloff);values=Map.copyOf(values);
        if(!values.keySet().equals(DEFAULTS.keySet()))throw new IllegalArgumentException("Incomplete fog parameters");
        for(var e:values.entrySet())if(!Double.isFinite(e.getValue())||Math.abs(e.getValue())>32_000_000)throw new IllegalArgumentException("Fog values must be finite and within ±32000000");
        if(values.get("strength")<0||values.get("strength")>1)throw new IllegalArgumentException("Fog strength must be 0–1");
        for(String k:List.of("visibility","width","height","depth","radius"))if(values.get(k)<.01)throw new IllegalArgumentException(k+" must be at least 0.01 blocks");
        if(values.get("end")-values.get("start")<.01)throw new IllegalArgumentException("Full-fog distance must exceed start by at least 0.01 blocks");
        if(values.get("upper")-values.get("lower")<.01)throw new IllegalArgumentException("Upper Y must exceed lower Y by at least 0.01 blocks");
        if(values.get("fade")<0)throw new IllegalArgumentException("Edge fade cannot be negative");
        if(shape!=Shape.EVERYWHERE&&falloff!=Falloff.NATURAL)throw new IllegalArgumentException("Shaped fog requires Natural falloff");
        if(falloff==Falloff.NATURAL&&values.get("start")<0)throw new IllegalArgumentException("Natural fog clear-near distance cannot be negative");
        double maxFade=switch(shape){case HEIGHT_BAND->(values.get("upper")-values.get("lower"))/2;case BOX->Math.min(values.get("width"),Math.min(values.get("height"),values.get("depth")))/2;case SPHERE->values.get("radius");default->32_000_000;};
        if(values.get("fade")>maxFade)throw new IllegalArgumentException("Edge fade exceeds the shape's half thickness");
        if(!List.of(8,16,32).contains(samples))throw new IllegalArgumentException("Integration samples must be 8, 16 or 32");
        if(altitude!=null&&(Math.abs(altitude.min())>32_000_000||Math.abs(altitude.max())>32_000_000||altitude.feather()>32_000_000))throw new IllegalArgumentException("Camera Y condition exceeds shader range");
    }
    public static Fog create(Shape shape) {return new Fog(switch(shape){case EVERYWHERE->"Distance haze";case BELOW_HEIGHT->"Ground mist";case HEIGHT_BAND->"Height band";case BOX->"Local box";case SPHERE->"Local sphere";},true,shape,Falloff.NATURAL,DEFAULTS,null,16);}
    /** Opinionated starting points, tuned in game. Heights are relative to the author's feet when added. */
    public static final List<String> PRESETS=List.of("Soft haze","Thick fog","Ground mist","Low cloud","Interior haze","Misty morning");
    public static String presetHelp(String preset){return switch(preset){
        case "Soft haze"->"Gentle distance haze. Distant terrain fades towards the sky colour; nearby blocks stay crisp.";
        case "Thick fog"->"Heavy fog everywhere. Half the light is lost every 24 blocks.";
        case "Ground mist"->"Low mist hugging the ground up to 4 blocks above the ground below you, softening into clear air.";
        case "Low cloud"->"A cloud layer 16–30 blocks above the ground below you. Hilltops and towers disappear into it.";
        case "Interior haze"->"A soft sphere of haze centred where you stand. Good for halls, naves and caves.";
        case "Misty morning"->"Soft haze plus ground mist: two fog objects you can tune separately.";
        default->throw new IllegalArgumentException("Unknown fog preset: "+preset);};}
    public static List<Fog> preset(String preset,double groundY,double x,double y,double z){
        double g=Math.floor(groundY);
        java.util.function.Function<Map<String,Double>,Map<String,Double>> v=m->{var all=new HashMap<>(DEFAULTS);all.putAll(m);return all;};
        return switch(preset){
            case "Soft haze"->List.of(new Fog("Soft haze",true,Shape.EVERYWHERE,Falloff.NATURAL,v.apply(Map.of("strength",.6,"start",24d,"visibility",160d)),null,16));
            case "Thick fog"->List.of(new Fog("Thick fog",true,Shape.EVERYWHERE,Falloff.NATURAL,v.apply(Map.of("strength",1d,"start",2d,"visibility",24d)),null,16));
            case "Ground mist"->List.of(new Fog("Ground mist",true,Shape.BELOW_HEIGHT,Falloff.NATURAL,v.apply(Map.of("strength",.9,"upper",g+4,"fade",3d,"visibility",18d)),null,16));
            case "Low cloud"->List.of(new Fog("Low cloud",true,Shape.HEIGHT_BAND,Falloff.NATURAL,v.apply(Map.of("strength",1d,"lower",g+16,"upper",g+30,"fade",5d,"visibility",12d)),null,16));
            case "Interior haze"->List.of(new Fog("Interior haze",true,Shape.SPHERE,Falloff.NATURAL,v.apply(Map.of("strength",.8,"x",Math.floor(x)+.5,"y",Math.floor(y)+1,"z",Math.floor(z)+.5,"radius",24d,"fade",10d,"visibility",40d)),null,16));
            case "Misty morning"->List.of(
                new Fog("Morning haze",true,Shape.EVERYWHERE,Falloff.NATURAL,v.apply(Map.of("strength",.45,"start",32d,"visibility",192d)),null,16),
                new Fog("Morning mist",true,Shape.BELOW_HEIGHT,Falloff.NATURAL,v.apply(Map.of("strength",.85,"upper",g+5,"fade",5d,"visibility",28d)),null,16));
            default->throw new IllegalArgumentException("Unknown fog preset: "+preset);
        };
    }
    /** One-line description for lists. */
    public String summary(){
        String look=falloff==Falloff.LINEAR?"linear "+n(get("start"))+"–"+n(get("end")):"50% over "+n(get("visibility"));
        String where=switch(shape){case EVERYWHERE->"Everywhere";case BELOW_HEIGHT->"Below Y "+n(get("upper"));case HEIGHT_BAND->"Y "+n(get("lower"))+"–"+n(get("upper"));case BOX->"Box "+n(get("width"))+"×"+n(get("height"))+"×"+n(get("depth"));case SPHERE->"Sphere r"+n(get("radius"));};
        return where+" · "+look+(get("strength")<1?" · "+Math.round(get("strength")*100)+"%":"")+(altitude!=null?" · camera Y "+n(altitude.min())+"–"+n(altitude.max()):"");
    }
    private static String n(double d){return d==Math.rint(d)?Long.toString((long)d):String.format(Locale.ROOT,"%.1f",d);}
    public double get(String key){return values.get(key);}
    public Fog with(String key,double value){var m=new HashMap<>(values);m.put(key,value);return new Fog(name,enabled,shape,falloff,m,altitude,samples);}
    public Fog named(String n){return new Fog(n,enabled,shape,falloff,values,altitude,samples);}
    public Fog enabled(boolean b){return new Fog(name,b,shape,falloff,values,altitude,samples);}
    public Fog falloff(Falloff f){return new Fog(name,enabled,shape,f,values,altitude,samples);}
    public Fog altitude(Atmosphere.Range a){return new Fog(name,enabled,shape,falloff,values,a,samples);}
    public Fog samples(int n){return new Fog(name,enabled,shape,falloff,values,altitude,n);}
    public boolean sampled(){return get("fade")>0&&(shape==Shape.BOX||shape==Shape.SPHERE);}
    public double weight(double cameraY){return enabled?get("strength")*(altitude==null?1:altitude.weight(cameraY)):0;}

    /** Integral of smoothstep(0, fade, x). */
    private static double primitive(double x,double fade){if(x<=0)return 0;if(x>=fade)return x-fade/2;double t=x/fade;return fade*(t*t*t-.5*t*t*t*t);}
    private static double smooth(double x){double t=Math.clamp(x,0,1);return t*t*(3-2*t);}
    private static boolean clip(double origin,double direction,double lo,double hi,double[] interval){
        if(Math.abs(direction)<1e-10)return origin>=lo&&origin<=hi;
        double a=(lo-origin)/direction,b=(hi-origin)/direction;
        interval[0]=Math.max(interval[0],Math.min(a,b));interval[1]=Math.min(interval[1],Math.max(a,b));return interval[1]>interval[0];
    }
    /** Ray endpoint is relative to camera; camera and shape placement are world coordinates. */
    public double integratedLength(double cx,double cy,double cz,double vx,double vy,double vz){
        double length=Math.sqrt(vx*vx+vy*vy+vz*vz);if(length<1e-10)return 0;
        double dx=vx/length,dy=vy/length,dz=vz/length;
        double[] interval={Math.max(0,get("start")),length};if(interval[0]>=length)return 0;
        double ox=cx-get("x"),oy=cy-get("y"),oz=cz-get("z");
        switch(shape){
            case BELOW_HEIGHT,HEIGHT_BAND->{if(!clip(cy,dy,shape==Shape.BELOW_HEIGHT?-1e30:get("lower"),get("upper"),interval))return 0;}
            case BOX->{if(!clip(ox,dx,-get("width")/2,get("width")/2,interval)||!clip(oy,dy,-get("height")/2,get("height")/2,interval)||!clip(oz,dz,-get("depth")/2,get("depth")/2,interval))return 0;}
            case SPHERE->{double b=ox*dx+oy*dy+oz*dz,px=ox-dx*b,py=oy-dy*b,pz=oz-dz*b,disc=get("radius")*get("radius")-(px*px+py*py+pz*pz);if(disc<=0)return 0;double q=Math.sqrt(disc);interval[0]=Math.max(interval[0],-b-q);interval[1]=Math.min(interval[1],-b+q);}
            default->{}
        }
        double a=interval[0],b=interval[1],span=b-a;if(span<=0)return 0;
        double fade=get("fade");if(fade==0||shape==Shape.EVERYWHERE)return span;
        if(shape==Shape.BELOW_HEIGHT||shape==Shape.HEIGHT_BAND){
            if(Math.abs(dy)<1e-7){double y=cy+(a+b)*.5*dy;return span*heightDensity(y);}
            // Work relative to the top edge to avoid cancellation at large world coordinates.
            double y0=cy-get("upper")+a*dy,y1=cy-get("upper")+b*dy;
            double upperIntegral=(primitive(y1+fade,fade)-primitive(y0+fade,fade))/dy;
            if(shape==Shape.BELOW_HEIGHT)return Math.clamp(span-upperIntegral,0,span);
            double thickness=get("upper")-get("lower");
            return Math.clamp((primitive(y1+thickness,fade)-primitive(y0+thickness,fade))/dy-upperIntegral,0,span);
        }
        double sum=0;
        for(int i=0;i<samples;i++){double s=a+(i+.5)*span/samples,x=ox+s*dx,y=oy+s*dy,z=oz+s*dz;
            double margin=shape==Shape.SPHERE?get("radius")-Math.sqrt(x*x+y*y+z*z):Math.min(get("width")/2-Math.abs(x),Math.min(get("height")/2-Math.abs(y),get("depth")/2-Math.abs(z)));
            sum+=smooth(margin/fade);
        }
        return span*sum/samples;
    }
    private double heightDensity(double y){double f=get("fade");double top=smooth((get("upper")-y)/f);return shape==Shape.BELOW_HEIGHT?top:Math.min(top,smooth((y-get("lower"))/f));}
    public double opticalDepth(double cx,double cy,double cz,double vx,double vy,double vz){
        double weight=weight(cy);if(weight==0)return 0;
        if(falloff==Falloff.LINEAR){double d=Math.sqrt(vx*vx+vy*vy+vz*vz);double alpha=Math.clamp((d-get("start"))/(get("end")-get("start")),0,1)*weight;return -Math.log(Math.max(1e-9,1-alpha));}
        return Math.log(2)*integratedLength(cx,cy,cz,vx,vy,vz)/get("visibility")*weight;
    }
    public static double transmittance(List<Fog> fog,double cx,double cy,double cz,double vx,double vy,double vz){double tau=0;for(var f:fog)tau+=f.opticalDepth(cx,cy,cz,vx,vy,vz);return Math.exp(-Math.min(80,tau));}
    public JsonObject json(){var j=new JsonObject();j.addProperty("name",name);j.addProperty("enabled",enabled);j.addProperty("shape",shape.name());j.addProperty("falloff",falloff.name());j.addProperty("samples",samples);var v=new JsonObject();new TreeMap<>(values).forEach(v::addProperty);j.add("values",v);if(altitude!=null){var a=new JsonObject();a.addProperty("min",altitude.min());a.addProperty("max",altitude.max());a.addProperty("feather",altitude.feather());j.add("altitude",a);}return j;}
    private static double number(JsonObject j,String key){var p=j.getAsJsonPrimitive(key);if(p==null||!p.isNumber())throw new IllegalArgumentException("Missing fog number: "+key);return p.getAsDouble();}
    private static String string(JsonObject j,String key){var p=j.getAsJsonPrimitive(key);if(p==null||!p.isString())throw new IllegalArgumentException("Missing fog string: "+key);return p.getAsString();}
    public static Fog read(JsonObject j){var v=new HashMap<String,Double>();var numbers=j.getAsJsonObject("values");for(String key:DEFAULTS.keySet())v.put(key,number(numbers,key));if(numbers.size()!=DEFAULTS.size())throw new IllegalArgumentException("Unknown fog parameters");var e=j.getAsJsonPrimitive("enabled");if(e==null||!e.isBoolean())throw new IllegalArgumentException("Invalid fog enabled flag");double count=number(j,"samples");if(count!=Math.rint(count))throw new IllegalArgumentException("Invalid sample count");Atmosphere.Range a=null;if(j.has("altitude")){var r=j.getAsJsonObject("altitude");a=new Atmosphere.Range(number(r,"min"),number(r,"max"),number(r,"feather"));}return new Fog(string(j,"name"),e.getAsBoolean(),Shape.valueOf(string(j,"shape")),Falloff.valueOf(string(j,"falloff")),v,a,(int)count);}
}
