package io.github.tjhgibbs.shadermaker.client;

import io.github.tjhgibbs.shadermaker.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Ordered layers and explicit synthetic context for the specimen. */
final class LayerScreen extends Screen {
    private final Screen parent;
    private int page;
    private EditBox name;
    private String message = "";
    LayerScreen(Screen parent) { super(Component.literal("Grade layers")); this.parent=parent; }
    private Button button(String text,int x,int y,int w,Runnable action) {
        // Every action first keeps a pending (valid) rename so it can't be lost silently.
        return addRenderableWidget(Button.builder(Component.literal(text), b -> { if(flushName()) action.run(); }).bounds(x,y,w,20).build());
    }
    private void replace(List<Project.Layer> layers) { Session.edit(Session.project.withLayers(layers)); Session.commit(); rebuildWidgets(); }
    private boolean nameTaken(String value,int except) {
        var layers=Session.project.layers();
        for(int i=0;i<layers.size();i++) if(i!=except&&layers.get(i).name().equals(value.strip())) return true;
        return false;
    }
    /** Applies the name box if it changed. Returns false (and explains) when it is invalid, leaving the project untouched. */
    private boolean flushName() {
        if(name==null||Session.selectedLayer<0||Session.selectedLayer>=Session.project.layers().size()) return true;
        var ls=new ArrayList<>(Session.project.layers()); var l=ls.get(Session.selectedLayer);
        String value=name.getValue();
        if(value.strip().equals(l.name())) return true;
        try {
            if(nameTaken(value,Session.selectedLayer)) { message="Another layer already uses that name."; return false; }
            ls.set(Session.selectedLayer,new Project.Layer(value,l.project(),l.enabled(),l.strength(),l.biomes(),l.altitude(),l.time()));
            Session.edit(Session.project.withLayers(ls)); Session.commit(); message="";
            return true;
        } catch(IllegalArgumentException e) { message="Enter a name (1-64 characters)."; return false; }
    }
    private static Project.Layer copyOf(Project.Layer l,String name,boolean enabled) {
        return new Project.Layer(name,l.project(),enabled,l.strength(),l.biomes(),l.altitude(),l.time());
    }
    private String listLabel(int i,Project.Layer layer,int width) {
        String prefix=(i==Session.selectedLayer?"> ":"")+(i+1)+". ";
        String suffix="  ["+(layer.enabled()?"on":"off")+", "+Math.round(layer.strength()*100)+"%]";
        int room=width-font.width(prefix)-font.width(suffix);
        String n=layer.name();
        if(font.width(n)>room) n=font.plainSubstrByWidth(n,Math.max(0,room-font.width("…")))+"…";
        return prefix+n+suffix;
    }
    @Override protected void init() {
        name=null;
        if(width<340||height<220){button("Done",width/2-40,height-26,80,this::onClose);return;}
        var layers=Session.project.layers();
        if(Session.selectedLayer>=layers.size()) Session.selectedLayer=-1;
        int left=(width-30)/2, rightX=20+left, right=width-rightX-10, half=(right-5)/2;
        int rows=Math.max(1,(height-115)/23), pages=Math.max(1,(layers.size()+rows-1)/rows);
        page=Math.min(page,pages-1);
        button(Session.selectedLayer<0?"> Base grade":"Base grade",10,30,left,()->{Session.selectedLayer=-1;message="";rebuildWidgets();});
        for(int i=page*rows;i<Math.min(layers.size(),(page+1)*rows);i++) {
            int index=i; var layer=layers.get(i);
            var row=button(listLabel(i,layer,left-10),10,55+(i-page*rows)*23,left,()->{Session.selectedLayer=index;message="";rebuildWidgets();});
            row.setTooltip(Tooltip.create(Component.literal(layer.name())));
        }
        var previous=button("<",10,height-55,35,()->{page--;rebuildWidgets();}); previous.active=page>0;
        var next=button(">",10+left-35,height-55,35,()->{page++;rebuildWidgets();}); next.active=page+1<pages;
        if(Session.selectedLayer>=0) {
            name=addRenderableWidget(new EditBox(font,rightX,30,right-55,20,Component.literal("Layer name")));
            name.setMaxLength(64); name.setValue(layers.get(Session.selectedLayer).name());
            button("Save",rightX+right-50,30,50,()->{ message="Name saved."; rebuildWidgets(); });
        }
        button("Add",rightX,55,half,()->{var ls=new ArrayList<>(Session.project.layers());ls.add(new Project.Layer(Session.freeLayerName(),Project.neutral(),true,1,Set.of(),null,null));Session.selectedLayer=ls.size()-1;page=Session.selectedLayer/rows;message="";replace(ls);});
        var duplicate=button("Duplicate",rightX+half+5,55,right-half-5,()->{var ls=new ArrayList<>(Session.project.layers());var l=ls.get(Session.selectedLayer);ls.add(Session.selectedLayer+1,copyOf(l,Session.freeCopyName(l.name()),l.enabled()));Session.selectedLayer++;page=Session.selectedLayer/rows;message="";replace(ls);});
        var toggle=button(Session.selectedLayer>=0&&layers.get(Session.selectedLayer).enabled()?"Disable":"Enable",rightX,78,half,()->{var ls=new ArrayList<>(Session.project.layers());var l=ls.get(Session.selectedLayer);ls.set(Session.selectedLayer,copyOf(l,l.name(),!l.enabled()));message="";replace(ls);});
        var remove=button("Remove",rightX+half+5,78,right-half-5,()->{var ls=new ArrayList<>(Session.project.layers());ls.remove(Session.selectedLayer);Session.selectedLayer=Math.min(Session.selectedLayer,ls.size()-1);message="";replace(ls);});
        var up=button("Move earlier",rightX,101,half,()->move(-1,rows));
        var down=button("Move later",rightX+half+5,101,right-half-5,()->move(1,rows));
        var conditions=button("Conditions...",rightX,124,half,()->minecraft.setScreenAndShow(new LayerConditionsScreen(this,false)));
        button("Sample context...",rightX+half+5,124,right-half-5,()->minecraft.setScreenAndShow(new LayerConditionsScreen(this,true)));
        var disableAll=button("Disable all layers",rightX,147,right,()->{
            var ls=new ArrayList<Project.Layer>(); for(var l:Session.project.layers()) ls.add(l.enabled()?copyOf(l,l.name(),false):l);
            message="All layers disabled."; replace(ls);
        });
        disableAll.active=layers.stream().anyMatch(Project.Layer::enabled);
        disableAll.setTooltip(Tooltip.create(Component.literal("Layers are kept but ignored, so you can Apply or Export the base grade.")));
        duplicate.active=toggle.active=remove.active=conditions.active=Session.selectedLayer>=0;
        up.active=Session.selectedLayer>0; down.active=Session.selectedLayer>=0&&Session.selectedLayer<layers.size()-1;
        button("Edit selected grade",10,height-30,width-20,this::onClose);
    }
    private void move(int delta,int rows) {var ls=new ArrayList<>(Session.project.layers());int to=Session.selectedLayer+delta;Collections.swap(ls,Session.selectedLayer,to);Session.selectedLayer=to;page=to/rows;message="";replace(ls);}
    private static String num(double v){return v==Math.rint(v)?Long.toString((long)v):String.format(Locale.ROOT,"%.1f",v);}
    private List<String> summary() {
        var out=new ArrayList<String>();
        if(Session.selectedLayer<0) { out.add("Base grade is always applied."); return out; }
        var l=Session.project.layers().get(Session.selectedLayer);
        out.add(l.biomes().isEmpty()?"Biomes: any":"Biomes: "+String.join(", ",new TreeSet<>(l.biomes()).stream().map(b->b.replace("minecraft:","")).toList()));
        if(l.altitude()!=null) out.add("Height: Y "+num(l.altitude().min())+" to "+num(l.altitude().max())+" (feather "+num(l.altitude().feather())+")");
        if(l.time()!=null) out.add("Time: "+num(l.time().start())+" to "+num(l.time().end())+" (feather "+num(l.time().feather())+")");
        return out;
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float d){g.fill(0,0,width,height,0xFF10191E);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float d){
        super.extractRenderState(g,x,y,d);g.text(font,"GRADE LAYERS",10,10,0xFF77C9BC);
        if(width<340||height<220)return;
        g.text(font,"SAMPLE ONLY",width-90,10,0xFFD9C993);
        int left=(width-30)/2, rightX=20+left, right=width-rightX-10;
        int rows=Math.max(1,(height-115)/23), pages=Math.max(1,(Session.project.layers().size()+rows-1)/rows);
        g.text(font,"Page "+(page+1)+" / "+pages,50,height-49,0xFF9DB3BD);
        int y0=173;
        if(!message.isEmpty()) { g.text(font,font.plainSubstrByWidth(message,right),rightX,y0,0xFFE8B96B); y0+=12; }
        for(String line:summary()) {
            if(y0+9>height-34) break;
            g.text(font,font.plainSubstrByWidth(line,right),rightX,y0,0xFFD9C993); y0+=12;
        }
    }
    /** Escape always leaves; a valid pending rename is kept, an invalid one is dropped. */
    @Override public void onClose(){ flushName(); minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen(){return false;}
}
