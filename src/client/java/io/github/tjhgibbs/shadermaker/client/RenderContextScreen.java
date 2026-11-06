package io.github.tjhgibbs.shadermaker.client;

import io.github.tjhgibbs.shadermaker.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Consumer;

/** Atomic draft for resolved rendering conditions; no implicit conversion of legacy IDs. */
final class RenderContextScreen extends Screen {
    private final Screen parent;
    private final Consumer<WorldSignals.Controls> save;
    private final EnumMap<WorldSignals.Source,WorldSignals.Condition> conditions=new EnumMap<>(WorldSignals.Source.class);
    private WorldSignals.Source source=WorldSignals.Source.FOG_COLOR;
    private boolean blocksOnly,hue,dirty;
    private String first="#C0D8FF",second="0.04",message="";
    RenderContextScreen(Screen parent,WorldSignals.Controls initial,Consumer<WorldSignals.Controls> save){
        super(Component.literal("Render context"));this.parent=parent;this.save=save;blocksOnly=initial.blocksOnly();
        initial.conditions().forEach(c->conditions.put(c.source(),c));load();
    }
    private Button button(String text,int x,int y,int w,Runnable action){return addRenderableWidget(Button.builder(Component.literal(text),b->action.run()).bounds(x,y,w,20).build());}
    private Button button(String text,int x,int y,int w,String tip,Runnable action){var b=button(text,x,y,w,action);b.setTooltip(Tooltip.create(Component.literal(tip)));return b;}
    private void load(){var c=conditions.get(source);dirty=false;hue=c!=null&&c.hue();first=source.colour()?String.format(Locale.ROOT,"#%06X",c==null?0xc0d8ff:c.rgb()):LayerScreen.num(c==null?0:c.min());second=c==null?(source.colour()?"0.04":"1024"):source.colour()?Double.toString(c.tolerance()):LayerScreen.num(c.max());}
    @Override protected void init(){
        if(width<340||height<240){button("Cancel",10,height-28,80,this::onClose);return;}
        addRenderableWidget(CycleButton.<WorldSignals.Source>builder(s->Component.literal(s.label+(conditions.containsKey(s)?"  ●":"")),source).withValues(WorldSignals.Source.values()).create(10,30,width-20,20,Component.literal("Signal"),(b,s)->{if(!keepTyped())return;source=s;load();message="";rebuildWidgets();})).setTooltip(Tooltip.create(Component.literal(source.help+"\n"+WorldSignals.WARNING)));
        button(blocksOnly?"Scope: Blocks only":"Scope: Full-screen",10,56,width-20,"Full-screen grades the whole view after three matching terrain samples. Blocks only grades matching solid/cutout terrain pixels. Unmarked entities, translucent blocks, sky and particles are excluded. Transparency/overlays can corrupt or mimic markers; this is experimental.",()->{blocksOnly=!blocksOnly;rebuildWidgets();});
        int half=(width-24)/2;
        var a=addRenderableWidget(new EditBox(font,10,100,half,20,Component.literal(source.colour()?"Colour #RRGGBB":"Minimum blocks")));a.setMaxLength(64);a.setValue(first);a.setResponder(v->{first=v;dirty=true;});
        var b=addRenderableWidget(new EditBox(font,14+half,100,half,20,Component.literal(source.colour()?"Tolerance (0–1)":"Maximum blocks")));b.setMaxLength(64);b.setValue(second);b.setResponder(v->{second=v;dirty=true;});
        var live=LiveSignals.read(source);
        var use=button("Use current world value",10,124,half,"Copy the value the game is using where you stand right now.",()->{
            var v=LiveSignals.read(source);if(v.isEmpty())return;
            if(source.colour()){a.setValue(String.format(Locale.ROOT,"#%06X",LiveSignals.rgb(v.get())));}
            else{double d=v.get().r();a.setValue(LayerScreen.num(Math.floor(d*.9)));b.setValue(LayerScreen.num(Math.ceil(d*1.1)));}
            message="Filled from the world. Add or Done keeps it.";});
        use.active=live.isPresent();
        if(live.isEmpty())use.setTooltip(Tooltip.create(Component.literal(source==WorldSignals.Source.SURFACE_TINT?"Surface tint differs per block; enter a colour.":"Join a world to read the current value.")));
        if(source.colour())button(hue?"Match: hue (ignores brightness)":"Match: exact RGB",14+half,124,half,"RGB compares each raw channel; hue divides by the brightest channel and ignores brightness. Tolerance is maximum per-channel error, 0–1. Black cannot use hue matching.",()->{hue=!hue;dirty=true;rebuildWidgets();});
        button(conditions.containsKey(source)?"Update this signal":"Add this signal",10,150,half,()->{try{conditions.put(source,parse());dirty=false;message="Added. Done keeps it.";rebuildWidgets();}catch(IllegalArgumentException e){message=e.getMessage();}});
        button("Remove this signal",14+half,150,half,()->{conditions.remove(source);dirty=false;load();message="Removed.";rebuildWidgets();}).active=conditions.containsKey(source);
        button("Cancel",width-160,height-28,70,"Discard changes made on this page",this::onClose);
        button("Done",width-80,height-28,70,"Keep these signals and return",()->{if(!keepTyped())return;save.accept(new WorldSignals.Controls(new ArrayList<>(conditions.values()),blocksOnly));onClose();});
    }
    /** Typed-but-not-added values are kept rather than silently dropped. */
    private boolean keepTyped(){
        if(!dirty)return true;
        try{conditions.put(source,parse());dirty=false;return true;}catch(IllegalArgumentException e){message="Fix or Remove the typed values first: "+e.getMessage();return false;}
    }
    private WorldSignals.Condition parse(){
        double n=number(second);
        if(source.colour()){if(!first.strip().matches("#[0-9a-fA-F]{6}"))throw new IllegalArgumentException("Enter a colour as #RRGGBB.");return WorldSignals.Condition.colour(source,Integer.parseInt(first.strip().substring(1),16),n,hue);}
        return WorldSignals.Condition.range(source,number(first),n);
    }
    private double number(String text){try{double n=Double.parseDouble(text.strip());if(Double.isFinite(n))return n;}catch(NumberFormatException ignored){}throw new IllegalArgumentException("Enter finite numbers.");}
    private void line(GuiGraphicsExtractor g,String text,int x,int y,int w,int colour){g.text(font,font.plainSubstrByWidth(text,w),x,y,colour);}
    @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float d){g.fill(0,0,width,height,LayerScreen.BG);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float d){
        super.extractRenderState(g,x,y,d);line(g,"RENDER CONTEXT · experimental vanilla signals",10,10,width-20,LayerScreen.ACCENT);
        if(width<340||height<240)return;
        int half=(width-24)/2;
        line(g,source.colour()?"Colour #RRGGBB":"Minimum distance (blocks)",10,88,half,LayerScreen.DIM);
        line(g,source.colour()?"Tolerance per channel (0–1)":"Maximum distance (blocks)",14+half,88,half,LayerScreen.DIM);
        var live=LiveSignals.read(source);
        int y0=178;
        if(live.isPresent()){var v=live.get();line(g,"Here now: "+(source.colour()?String.format(Locale.ROOT,"#%06X",LiveSignals.rgb(v)):LayerScreen.num(Math.round(v.r()*10)/10d)+" blocks"),10,y0,width-20,0xFF8FD18F);
            if(source.colour())g.fill(10+font.width("Here now: #RRGGBB ")+2,y0-1,10+font.width("Here now: #RRGGBB ")+22,y0+8,0xFF000000|LiveSignals.rgb(v));y0+=14;}
        for(var l:font.split(Component.literal(source.help),width-20)){if(y0>=height-110)break;g.text(font,l,10,y0,LayerScreen.WARN);y0+=11;}
        y0+=6;line(g,conditions.isEmpty()?"No signals yet"+(blocksOnly?": Blocks only applies to all marked terrain.":"."):"Active signals (all must match):",10,y0,width-20,LayerScreen.ACCENT);y0+=12;
        for(var c:conditions.values()){if(y0>=height-58)break;line(g,"• "+c.label(),10,y0,width-20,LayerScreen.TEXT);y0+=11;}
        line(g,message.isEmpty()?"Limit: 4 distinct signal sets per pack. Reads solid terrain on screen; keeps the last result while none is visible.":message,10,height-43,width-20,LayerScreen.STATUS);
    }
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
