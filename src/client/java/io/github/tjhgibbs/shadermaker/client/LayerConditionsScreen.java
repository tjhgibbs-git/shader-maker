package io.github.tjhgibbs.shadermaker.client;

import io.github.tjhgibbs.shadermaker.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

/**
 * "Where & when" for one layer. Each tab is one way the world can reach a vanilla shader, with a
 * plain statement of how reliable it is. Imported legacy settings are preserved until cleared.
 */
final class LayerConditionsScreen extends Screen {
    private static final int POSITION=0, TIME=1, SKY=2, WEATHER=3, FLASH=4, SIGNALS=5, COMMAND=6;
    // Draft fields: 0 strength, 1-3 height, 4-6 legacy time, 7 legacy biome IDs, 8-13 legacy weather,
    // 14-16 legacy flash, 17-20 area min X / max X / min Z / max Z, 21 area feather, 22 fade seconds.
    private static final int AREA=17, FADE=22;
    private final Screen parent;
    private final int layerIndex;
    private final String[] draft;
    private int tab;
    private String visualGroup;
    private WorldSignals.Controls contextControls;
    private boolean command;
    private String message="";
    LayerConditionsScreen(Screen parent) {
        super(Component.literal("Where and when"));
        this.parent=parent;layerIndex=Session.selectedLayer;
        var l=Session.project.layers().get(layerIndex);var a=l.altitude();var t=l.time();visualGroup=l.visualBiomeGroup();contextControls=l.contextControls();
        var o=l.options();var b=o.area();command=o.command();
        draft=new String[]{Double.toString(l.strength()),a==null?"":n(a.min()),a==null?"":n(a.max()),a==null?"":n(a.feather()),
            t==null?"":Double.toString(t.start()),t==null?"":Double.toString(t.end()),t==null?"":Double.toString(t.feather()),String.join(", ",new TreeSet<>(l.biomes())),
            l.weather()==null?"":Double.toString(l.weather().rain().min()),l.weather()==null?"":Double.toString(l.weather().rain().max()),l.weather()==null?"":Double.toString(l.weather().rain().feather()),
            l.weather()==null?"":Double.toString(l.weather().thunder().min()),l.weather()==null?"":Double.toString(l.weather().thunder().max()),l.weather()==null?"":Double.toString(l.weather().thunder().feather()),
            l.lightning()==null?"":Double.toString(l.lightning().min()),l.lightning()==null?"":Double.toString(l.lightning().max()),l.lightning()==null?"":Double.toString(l.lightning().feather()),
            b==null?"":n(b.minX()),b==null?"":n(b.maxX()),b==null?"":n(b.minZ()),b==null?"":n(b.maxZ()),b==null?"":n(b.feather()),n(o.fadeSeconds())};
        if(command)tab=COMMAND;
    }
    private static String n(double v){return LayerScreen.num(v);}
    private Button button(String label,int x,int y,int w,Runnable action) {
        return addRenderableWidget(Button.builder(Component.literal(label),b->action.run()).bounds(x,y,w,20).build());
    }
    private Button button(String label,int x,int y,int w,String tip,Runnable action){var b=button(label,x,y,w,action);b.setTooltip(Tooltip.create(Component.literal(tip)));return b;}
    private EditBox field(int index,String label,int x,int y,int w) {
        var f=addRenderableWidget(new EditBox(font,x,y,w,20,Component.literal(label)));
        f.setMaxLength(index==7?1_048_576:64);f.setValue(draft[index]);f.setResponder(v->{draft[index]=v;message="";});
        // Legacy time/biome/weather/flash values are read-only: they cannot export, only be cleared or converted.
        f.setEditable(index<4||index>=AREA);
        f.setTooltip(Tooltip.create(Component.literal(label)));
        return f;
    }
    private static final String[] TABS={"Position","Time (legacy)","Sky colour","Weather (legacy)","Flash (legacy)","Render signals","Command switch"};
    private static final String[] TAB_TIPS={
        "Camera height (Y) and a horizontal X/Z box. Exact: read from the camera every frame. Best for builds and maps.",
        "Not available in standalone packs. Existing values are kept until you clear them.",
        "Switches when the pack sees a particular sky colour. Approximate: several biomes share a colour. Holds while no sky is visible.",
        "Not available in standalone packs. Existing values are kept until you clear them.",
        "Not available in standalone packs. Existing values are kept until you clear them.",
        "Matches fog, lighting or grass/leaf colours the game gives its shaders. Advanced; best paired with a datapack that sets a unique value.",
        "Off until /posteffect turns it on: from chat, a command block or a datapack function. Server-side checks can be exact (biome, structure, weather, health…)."};
    @Override protected void init() {
        if(width<340||height<220){button("Cancel",width/2-40,height-26,80,this::onClose);return;}
        var visible=new ArrayList<Integer>(List.of(POSITION,SKY,SIGNALS,COMMAND));
        if(has(4))visible.add(TIME);if(has(8)||has(11))visible.add(WEATHER);if(has(14))visible.add(FLASH);
        if(!visible.contains(tab))tab=POSITION;
        int tabW=(width-20-(visible.size()-1)*4)/visible.size();
        for(int i=0;i<visible.size();i++){final int t=visible.get(i);
            var b=button(TABS[t]+(inUse(t)?" ●":""),10+i*(tabW+4),30,tabW,TAB_TIPS[t],()->{tab=t;message="";rebuildWidgets();});b.active=tab!=t;}
        field(0,"Strength (0–1)",110,56,60);
        button("Clear this tab",width-120,56,110,"Remove only this tab's condition. Done saves.",this::clearTab);
        int third=(width-28)/3, fifth=(width-36)/5, y=130;
        switch(tab){
            case POSITION->{
                for(int i=0;i<3;i++)field(1+i,new String[]{"Lowest camera Y","Highest camera Y","Fade distance (blocks)"}[i],10+i*(third+4),y,third);
                for(int i=0;i<5;i++)field(AREA+i,new String[]{"West edge (min X)","East edge (max X)","North edge (min Z)","South edge (max Z)","Fade distance (blocks)"}[i],10+i*(fifth+4),y+50,fifth);
                var here=button("Use where I'm standing",10,y+76,(width-24)/2,"Fill height ±8 and a 64×64 box centred on your camera. Edit the numbers afterwards.",this::useHere);
                here.active=Minecraft.getInstance().player!=null;
            }
            case TIME,WEATHER,FLASH->{
                int first=tab==TIME?4:tab==WEATHER?8:14;
                for(int i=0;i<3;i++)field(first+i,"Read-only legacy value",10+i*(third+4),y,third);
                if(tab==WEATHER)for(int i=0;i<3;i++)field(11+i,"Read-only legacy value",10+i*(third+4),y+40,third);
            }
            case SKY->{
                button(visualGroup==null?"Choose a sky colour…":"Sky colour: "+VisualBiomes.get(visualGroup).label()+"  (change…)",10,y,width-20,
                    "Pick a vanilla biome's sky colour (lists every biome that shares it) or type your own #RRGGBB for a datapack biome.",
                    ()->minecraft.setScreenAndShow(new VisualBiomeScreen(this,visualGroup,draft[7],value->{visualGroup=value;draft[7]="";message="Sky colour chosen. Done saves it.";})));
                if(!draft[7].isBlank())field(7,"Legacy exact biome IDs (read-only)",10,y+36,width-20);
            }
            case SIGNALS->button(contextControls.active()?"Edit render signals…":"Add a render signal…",10,y,width-20,
                "Choose a value (fog colour, fog distance, lighting colour, grass/leaf tint) and what it must match.",
                ()->minecraft.setScreenAndShow(new RenderContextScreen(this,contextControls,value->{contextControls=value;message="Render signals updated. Done saves them.";})));
            case COMMAND->{
                button(command?"Trigger: Command (/posteffect)":"Trigger: Automatic (always on)",10,y,(width-24)/2,
                    "Automatic layers are part of the always-on look. Command layers are separate effects that are off until switched on.",()->{command=!command;message=command?"Command trigger. Done saves it.":"Automatic. Done saves it.";rebuildWidgets();});
                field(FADE,"Fade in/out time (seconds, 0–30)",14+(width-24)/2,y,(width-24)/2);
                if(command){
                    String id=CommandEffects.NAMESPACE+":"+Project.Options.slug(Session.project.layers().get(layerIndex).name());
                    int q=(width-32)/4;
                    button("Try: on",10,y+50,q,"Runs /posteffect add @s "+id+" (needs cheats and an applied preview).",()->Session.command("posteffect add @s "+id));
                    button("Try: fade out",14+q,y+50,q,"Swaps to the fade-out effect: remove "+id+", add "+id+"_off.",()->{Session.command("posteffect remove @s "+id);Session.command("posteffect add @s "+id+"_off");});
                    button("Try: all off",18+2*q,y+50,q,"Runs /posteffect clear @s",()->Session.command("posteffect clear @s"));
                    button("Copy command",22+3*q,y+50,q,"Copy /posteffect add @s "+id+" for a command block.",()->{minecraft.keyboardHandler.setClipboard("/posteffect add @s "+id);message="Copied /posteffect add @s "+id;});
                    int h=(width-24)/2;
                    button("Install helpers in this world",10,y+74,h,"Singleplayer: write the helper datapack (menu, on/off/toggle functions, command-block givers) into this world and /reload. Save first: uses the project's command layers as they are now.",()->{if(apply()){Session.installDatapack();message=Session.message;}});
                    button("Export helper datapack",14+h,y+74,h,"Write the helper datapack ZIP to .minecraft/shader-maker/exports for any world or server.",()->{if(apply()){Session.exportDatapack();message=Session.message;}});
                }
            }
            default->{}
        }
        button("Cancel",width-160,height-28,70,"Discard changes made on this page",this::onClose);
        button("Done",width-80,height-28,70,"Save and return (Enter)",()->{if(apply())onClose();});
    }
    private boolean inUse(int t){
        return switch(t){case POSITION->has(1)||has(AREA)||!draft[AREA+3].isBlank();case SKY->visualGroup!=null||!draft[7].isBlank();case SIGNALS->contextControls.active();case COMMAND->command;
            case TIME->has(4);case WEATHER->has(8)||has(11);default->has(14);};
    }
    private void useHere(){
        var p=Minecraft.getInstance().player;if(p==null)return;
        long x=Math.round(p.getX()),y=Math.round(p.getEyeY()),z=Math.round(p.getZ());
        draft[1]=Long.toString(y-8);draft[2]=Long.toString(y+8);draft[3]="2";
        draft[AREA]=Long.toString(x-32);draft[AREA+1]=Long.toString(x+32);draft[AREA+2]=Long.toString(z-32);draft[AREA+3]=Long.toString(z+32);draft[AREA+4]="4";
        message="Filled from camera at "+x+", "+y+", "+z+". Clear the height row to use the box only.";rebuildWidgets();
    }
    private void clearTab(){
        switch(tab){
            case POSITION->{for(int i=1;i<=3;i++)draft[i]="";for(int i=AREA;i<AREA+5;i++)draft[i]="";}
            case TIME->{for(int i=4;i<=6;i++)draft[i]="";}
            case SKY->{visualGroup=null;draft[7]="";}
            case WEATHER->{for(int i=8;i<=13;i++)draft[i]="";}
            case FLASH->{for(int i=14;i<=16;i++)draft[i]="";}
            case SIGNALS->contextControls=WorldSignals.Controls.NONE;
            case COMMAND->{command=false;draft[FADE]="1";}
            default->{}
        }
        message="Cleared. Done saves it.";rebuildWidgets();
    }
    private double number(int i,String label) {
        try{double v=Double.parseDouble(draft[i].strip());if(Double.isFinite(v)&&Math.abs(v)<=Float.MAX_VALUE)return v;}catch(NumberFormatException ignored){}
        throw new IllegalArgumentException(label+" must be a number.");
    }
    private boolean has(int first){return !draft[first].isBlank()||!draft[first+1].isBlank()||!draft[first+2].isBlank();}
    private Atmosphere.Range unitRange(int first,String label) {
        if(!has(first))return new Atmosphere.Range(0,1,0);
        return new Atmosphere.Range(number(first,label+" minimum"),number(first+1,label+" maximum"),draft[first+2].isBlank()?0:number(first+2,label+" feather"));
    }
    private boolean apply() {
        try {
            double strength=number(0,"Strength");if(strength<0||strength>1)throw new IllegalArgumentException("Strength must be 0–1.");
            Atmosphere.Range altitude=has(1)?new Atmosphere.Range(number(1,"Lowest Y"),number(2,"Highest Y"),draft[3].isBlank()?0:number(3,"Height fade")):null;
            boolean anyArea=false;for(int i=AREA;i<AREA+4;i++)anyArea|=!draft[i].isBlank();
            Project.Area area=null;
            if(anyArea){for(int i=AREA;i<AREA+4;i++)if(draft[i].isBlank())throw new IllegalArgumentException("Fill all four area edges, or clear them.");
                double x0=number(AREA,"West edge"),x1=number(AREA+1,"East edge"),z0=number(AREA+2,"North edge"),z1=number(AREA+3,"South edge");
                if(x0>x1||z0>z1)throw new IllegalArgumentException("Area: west must be ≤ east and north ≤ south.");
                area=new Project.Area(x0,x1,z0,z1,draft[AREA+4].isBlank()?0:number(AREA+4,"Area fade"));}
            double fade=draft[FADE].isBlank()?1:number(FADE,"Fade time");if(fade<0||fade>30)throw new IllegalArgumentException("Fade time must be 0–30 seconds.");
            Atmosphere.CircularRange time=has(4)?new Atmosphere.CircularRange(number(4,"Start tick"),number(5,"End tick"),24000,draft[6].isBlank()?0:number(6,"Time feather")):null;
            var biomes=new TreeSet<String>();
            if(!draft[7].isBlank())for(String value:draft[7].split(",",-1)){String id=value.strip();if(id.isEmpty())throw new IllegalArgumentException("Enter comma-separated biome IDs without empty entries.");biomes.add(id.contains(":")?id:"minecraft:"+id);}
            var layers=new ArrayList<>(Session.project.layers());var l=layers.get(layerIndex);
            Atmosphere.Weather weather=has(8)||has(11)?new Atmosphere.Weather(unitRange(8,"Rain"),unitRange(11,"Thunder")):null;
            Atmosphere.Range lightning=has(14)?unitRange(14,"Flash"):null;
            layers.set(layerIndex,new Project.Layer(l.name(),l.project(),l.enabled(),strength,biomes,altitude,time,weather,lightning,visualGroup,contextControls,new Project.Options(area,command,fade)));
            Session.edit(Session.project.withLayers(layers));Session.commit();return true;
        }catch(IllegalArgumentException e){message=e.getMessage();return false;}
    }
    private int para(GuiGraphicsExtractor g,String s,int y,int color,int maxY){
        for(var l:font.split(Component.literal(s),width-20)){if(y>maxY)return y;g.text(font,l,10,y,color);y+=11;}return y+3;
    }
    private void line(GuiGraphicsExtractor g,String s,int x,int y,int w,int color){g.text(font,font.plainSubstrByWidth(s,w),x,y,color);}
    @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float d){g.fill(0,0,width,height,LayerScreen.BG);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float d) {
        super.extractRenderState(g,mx,my,d);
        line(g,"WHERE & WHEN · "+Session.project.layers().get(layerIndex).name(),10,10,width-20,LayerScreen.ACCENT);
        if(width<340||height<220)return;
        line(g,"Strength (0–1)",10,62,96,LayerScreen.DIM);
        int third=(width-28)/3, fifth=(width-36)/5, y=130, maxY=height-52;
        // Reliability statement: what this input really is.
        String[] badge=switch(tab){
            case POSITION->new String[]{"EXACT","Uses the camera position every frame. Never flickers. Blank fields = everywhere."};
            case SKY->new String[]{"APPROXIMATE","Reads the colour of visible sky. Holds the last colour while no sky is in view."};
            case SIGNALS->new String[]{"ADVANCED","Reads values from terrain on screen. Holds the last result while no terrain is in view."};
            case COMMAND->new String[]{"EXACT · NEEDS COMMANDS","Off until /posteffect turns it on. Needs cheats or a datapack/command block."};
            default->new String[]{"LEGACY","Cannot export to a vanilla pack. Kept until you clear it."};};
        int bw=font.width(badge[0])+8, badgeColour=tab==POSITION||tab==COMMAND?0xFF2E6B4F:tab==SKY||tab==SIGNALS?0xFF7A5A1E:0xFF6B2E2E;
        g.fill(10,82,10+bw,94,badgeColour);g.text(font,badge[0],14,84,LayerScreen.TEXT);
        line(g,badge[1],16+bw,84,width-26-bw,LayerScreen.TEXT);
        switch(tab){
            case POSITION->{
                line(g,"HEIGHT  (camera Y)",10,y-26,width-20,LayerScreen.ACCENT);
                String[] h={"Lowest Y","Highest Y","Fade (blocks)"};for(int i=0;i<3;i++)line(g,h[i],10+i*(third+4),y-12,third,LayerScreen.DIM);
                line(g,"AREA  (camera X / Z box)",10,y+26,width-20,LayerScreen.ACCENT);
                int ty=y+104;
                String[] a={"West: min X","East: max X","North: min Z","South: max Z","Fade (blocks)"};
                for(int i=0;i<5;i++)line(g,a[i],10+i*(fifth+4),y+38,fifth,LayerScreen.DIM);
                ty=para(g,"Fill one row or both (both must then match). Fade softens the edges inward, so the layer is at full strength once you are that far inside.",ty,LayerScreen.DIM,maxY);
                para(g,"Tip: F3 shows your coordinates. Use an area for a build, an arena or a region of a map; use height for caves, sky islands or the sea floor.",ty,LayerScreen.DIM,maxY);
            }
            case SKY->{
                int ty=y+(draft[7].isBlank()?30:64);
                if(!draft[7].isBlank())line(g,"Legacy exact IDs (read-only). Choose a sky colour above to convert them explicitly.",10,y+26,width-20,LayerScreen.WARN);
                ty=para(g,"How it works: every biome has a sky colour. The pack looks at the sky on screen and turns this layer on when it sees the chosen colour. It is matched by hue, so brightness alone does not matter; night, weather and sunsets can still shift it.",ty,LayerScreen.TEXT,maxY);
                ty=para(g,"Limits: many biomes share one colour (shown when you choose). The switch happens when you next see sky; in caves or looking down it keeps the last colour.",ty,LayerScreen.WARN,maxY);
                para(g,"Exact biomes: give a biome its own sky colour in a datapack (minecraft:visual/sky_color), then type that #RRGGBB here. Or use Command switch with a datapack.",ty,LayerScreen.DIM,maxY);
            }
            case SIGNALS->{
                int ty=y+30;
                if(contextControls.active()){
                    line(g,(contextControls.blocksOnly()?"Applies to matching blocks only. ":"Applies to the whole screen. ")+(contextControls.conditions().isEmpty()?"No values to match.":"All of these must match:"),10,ty,width-20,LayerScreen.ACCENT);ty+=12;
                    for(var c:contextControls.conditions()){line(g,"• "+c.label(),10,ty,width-20,LayerScreen.TEXT);ty+=11;}ty+=4;
                }
                ty=para(g,"How it works: the game hands its shaders the fog colour, fog distances and lighting colours for where you stand, plus grass/leaf tint on blocks. The pack marks terrain with the result, so it needs some solid blocks on screen.",ty,LayerScreen.TEXT,maxY);
                ty=para(g,"Limits: these values are shared by many places and change with weather, time and effects. Without visible terrain it keeps the last result.",ty,LayerScreen.WARN,maxY);
                para(g,"Best use: a datapack that sets a unique, normally invisible value per biome, such as minecraft:visual/night_vision_color one step away from the default #999999, then match it here with tolerance 0.002.",ty,LayerScreen.DIM,maxY);
            }
            case COMMAND->{
                int ty=y+26;
                String id=CommandEffects.NAMESPACE+":"+Project.Options.slug(Session.project.layers().get(layerIndex).name());
                line(g,"Fade in / out (seconds, 0–30)",14+(width-24)/2,y-12,(width-24)/2,LayerScreen.DIM);
                if(command){line(g,"Effect name: "+id+"   (fade-out: "+id+"_off)",10,ty,width-20,LayerScreen.ACCENT);ty+=74;}
                else ty+=4;
                ty=para(g,command?"This layer is a separate effect, off until switched on for a player. It grades on top of the always-on look. Height and area still apply; sky colour and render signals do not.":
                    "Switch the trigger to Command to make this layer a separate effect that commands turn on and off.",ty,LayerScreen.TEXT,maxY);
                ty=para(g,"Switch it from chat, a command block, or a datapack function. Datapacks can test exact biomes, structures, weather, health, scores… and run /posteffect.",ty,LayerScreen.DIM,maxY);
                para(g,"After editing, Apply to game so the effect exists. Effects stay on across relogs until removed or /posteffect clear.",ty,LayerScreen.DIM,maxY);
            }
            default->{
                String[] h=tab==TIME?new String[]{"Start tick","End tick","Feather"}:new String[]{"Minimum","Maximum","Feather"};
                for(int i=0;i<3;i++)line(g,h[i],10+i*(third+4),y-12,third,LayerScreen.DIM);
                if(tab==WEATHER)for(int i=0;i<3;i++)line(g,"Thunder "+h[i].toLowerCase(Locale.ROOT),10+i*(third+4),y+28,third,LayerScreen.DIM);
                para(g,"This condition came from an older project. Vanilla packs cannot read it, so this layer cannot export until you clear it (Clear this tab).",y+(tab==WEATHER?70:30),LayerScreen.WARN,maxY);
            }
        }
        line(g,message.isEmpty()?"Done saves every tab. Cancel discards changes.":message,10,height-43,width-20,LayerScreen.STATUS);
    }
    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event){
        if((event.key()==com.mojang.blaze3d.platform.InputConstants.KEY_RETURN||event.key()==com.mojang.blaze3d.platform.InputConstants.KEY_NUMPADENTER)&&!event.hasControlDown()){if(apply())onClose();return true;}
        return super.keyPressed(event);
    }
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
