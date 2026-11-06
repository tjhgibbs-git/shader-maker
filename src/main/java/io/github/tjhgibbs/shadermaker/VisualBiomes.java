package io.github.tjhgibbs.shadermaker;

import java.util.*;

/** Pinned vanilla 26.3 visual sky signatures, not biome registry lookups. */
public final class VisualBiomes {
    private VisualBiomes() {}
    public static final String WARNING="Sky colour signal, not biome identity. Datapacks can set the sky colour to drive this condition. Matching uses normalized RGB hue, not brightness; darkness, weather, blending and overlays can suppress or change the visible signal. Needs sky on screen to switch; while no sky is visible the last seen colour is kept, cross-fading over about one second.";
    public record Group(String id,int rgb,List<String> biomes) {
        public Group {biomes=List.copyOf(biomes);}
        public String label(){return biomes.isEmpty()?"Custom #"+String.format(Locale.ROOT,"%06x",rgb):biomes.getFirst().replace("minecraft:","").replace('_',' ')+(biomes.size()>1?" + "+(biomes.size()-1)+" shared":"");}
        public double component(int shift){int peak=Math.max((rgb>>16)&255,Math.max((rgb>>8)&255,rgb&255));return ((rgb>>shift)&255)/(double)peak;}
        public int code(){int i=GROUPS.indexOf(this);return 15+15*i;}
    }
    public static final List<Group> GROUPS=List.of(
        new Group("sky_6eb1ff", 0x6eb1ff, List.of("minecraft:badlands", "minecraft:desert", "minecraft:eroded_badlands", "minecraft:savanna", "minecraft:savanna_plateau", "minecraft:windswept_savanna", "minecraft:wooded_badlands")),
        new Group("sky_76a8ff", 0x76a8ff, List.of("minecraft:stony_peaks")),
        new Group("sky_77a8ff", 0x77a8ff, List.of("minecraft:bamboo_jungle", "minecraft:jungle", "minecraft:mushroom_fields", "minecraft:sparse_jungle")),
        new Group("sky_78a7ff", 0x78a7ff, List.of("minecraft:beach", "minecraft:deep_dark", "minecraft:dripstone_caves", "minecraft:mangrove_swamp", "minecraft:plains", "minecraft:sulfur_caves", "minecraft:sunflower_plains", "minecraft:swamp")),
        new Group("sky_79a6ff", 0x79a6ff, List.of("minecraft:dark_forest", "minecraft:flower_forest", "minecraft:forest")),
        new Group("sky_7aa5ff", 0x7aa5ff, List.of("minecraft:birch_forest", "minecraft:old_growth_birch_forest")),
        new Group("sky_7ba4ff", 0x7ba4ff, List.of("minecraft:cherry_grove", "minecraft:cold_ocean", "minecraft:deep_cold_ocean", "minecraft:deep_frozen_ocean", "minecraft:deep_lukewarm_ocean", "minecraft:deep_ocean", "minecraft:lukewarm_ocean", "minecraft:lush_caves", "minecraft:meadow", "minecraft:ocean", "minecraft:river", "minecraft:the_void", "minecraft:warm_ocean")),
        new Group("sky_7ca3ff", 0x7ca3ff, List.of("minecraft:dappled_forest", "minecraft:old_growth_pine_taiga")),
        new Group("sky_7da2ff", 0x7da2ff, List.of("minecraft:stony_shore", "minecraft:windswept_forest", "minecraft:windswept_gravelly_hills", "minecraft:windswept_hills")),
        new Group("sky_7da3ff", 0x7da3ff, List.of("minecraft:old_growth_spruce_taiga", "minecraft:taiga")),
        new Group("sky_7fa1ff", 0x7fa1ff, List.of("minecraft:frozen_ocean", "minecraft:frozen_river", "minecraft:ice_spikes", "minecraft:snowy_beach", "minecraft:snowy_plains")),
        new Group("sky_81a0ff", 0x81a0ff, List.of("minecraft:grove")),
        new Group("sky_829fff", 0x829fff, List.of("minecraft:snowy_slopes")),
        new Group("sky_839eff", 0x839eff, List.of("minecraft:snowy_taiga")),
        new Group("sky_859dff", 0x859dff, List.of("minecraft:frozen_peaks", "minecraft:jagged_peaks")),
        new Group("sky_b9b9b9", 0xb9b9b9, List.of("minecraft:pale_garden")));
    public static Group get(String id) {
        if(id!=null&&id.matches("custom_[0-9a-f]{6}")){int rgb=Integer.parseInt(id.substring(7),16);if(Math.max(rgb>>16&255,Math.max(rgb>>8&255,rgb&255))<16)throw new IllegalArgumentException("Sky signal colour must have a channel of at least 16/255");return new Group(id,rgb,List.of());}
        return GROUPS.stream().filter(g->g.id().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("Unknown sky colour: "+id));
    }
    public static Optional<Group> forBiome(String biome){return GROUPS.stream().filter(g->g.biomes().contains(biome)).findFirst();}
    /** Simulation of the stock daylight signature only; never reads live mod-owned inputs. */
    public static boolean previewMatch(String group,String biome){
        Group input;if(biome.startsWith("#")&&biome.matches("#[0-9a-fA-F]{6}"))input=new Group("preview",Integer.parseInt(biome.substring(1),16),List.of());else input=forBiome(biome).orElse(null);
        if(input==null||Math.max(input.rgb()>>16&255,Math.max(input.rgb()>>8&255,input.rgb()&255))<16)return false;
        return distance(get(group),input)<.000001;
    }
    public static double distance(Group a,Group b){double sum=0;for(int shift:List.of(16,8,0)){double d=a.component(shift)-b.component(shift);sum+=d*d;}return sum;}
    public static List<Group> palette(Project p){
        if(p.layers().stream().noneMatch(l->l.enabled()&&l.visualBiomeGroup()!=null&&l.visualBiomeGroup().startsWith("custom_")))return GROUPS;
        var result=new ArrayList<Group>();
        for(var l:p.layers())if(l.enabled()&&l.visualBiomeGroup()!=null){var g=get(l.visualBiomeGroup());if(result.stream().noneMatch(other->distance(other,g)<1e-12))result.add(g);}
        return List.copyOf(result);
    }
    public static int code(Project p,String id){var g=get(id);var palette=palette(p);for(int i=0;i<palette.size();i++)if(distance(g,palette.get(i))<1e-12)return 15+15*i;throw new IllegalArgumentException("Sky colour missing from palette");}

    public static int classify(double r,double g,double b) {
        double max=Math.max(r,Math.max(g,b));if(max<.06)return 0;
        double best=.000001;int code=0;
        for(var group:GROUPS){double dr=r/max-group.component(16),dg=g/max-group.component(8),db=b/max-group.component(0);double d=dr*dr+dg*dg+db*db;if(d<best){best=d;code=group.code();}}
        return code;
    }
    /** Called before vanilla fog changes the sky pixel color. Marker travels in unused opaque-sky alpha. */
    public static String classifierGlsl(){return classifierGlsl(GROUPS);}
    public static String classifierGlsl(List<Group> palette){
        var s=new StringBuilder("float visualBiomeCode(vec3 c) {\n    float peak = max(c.r, max(c.g,c.b));\n    if (peak < 0.06) return 0.0;\n    c /= peak;\n    float best = 0.000001; float code = 0.0; float d;\n");
        for(int i=0;i<palette.size();i++){var group=palette.get(i);s.append("    d = distance(c, vec3(").append(group.component(16)).append(", ").append(group.component(8)).append(", ").append(group.component(0)).append(")); d *= d;\n    if (d < best) {best=d; code=").append(15+15*i).append(".0;}\n");}
        return s.append("    return code;\n}\n").toString();
    }
}
