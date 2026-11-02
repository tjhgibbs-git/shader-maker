package io.github.tjhgibbs.shadermaker.client;

import io.github.tjhgibbs.shadermaker.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Drafts are kept across pages and applied together only after validation. */
final class LayerConditionsScreen extends Screen {
    private final Screen parent; private final boolean sample;
    private final List<EditBox> fields = new ArrayList<>();
    private final String[][] drafts;
    private int page; private String error = "";
    LayerConditionsScreen(Screen parent, boolean sample) {
        super(Component.literal("Layer conditions")); this.parent=parent; this.sample=sample;
        if(sample) drafts=new String[][] {{Session.sampleBiome, Double.toString(Session.sampleY), Double.toString(Session.sampleTime)}};
        else {
            var layer=Session.project.layers().get(Session.selectedLayer); var a=layer.altitude(); var t=layer.time();
            drafts=new String[][] {{String.join(",",new TreeSet<>(layer.biomes())),Double.toString(layer.strength())},
                a==null?new String[]{"","",""}:new String[]{""+a.min(),""+a.max(),""+a.feather()},
                t==null?new String[]{"","",""}:new String[]{""+t.start(),""+t.end(),""+t.feather()}};
        }
    }
    private Button button(String text,int x,int y,int w,Runnable action){return addRenderableWidget(Button.builder(Component.literal(text),b->action.run()).bounds(x,y,w,20).build());}
    private String[] labels(){return sample?new String[]{"Biome ID","Camera Y","Time (0–23999)"}:switch(page){case 0->new String[]{"Biome IDs (comma separated)","Strength (0–1)"};case 1->new String[]{"Minimum Y","Maximum Y","Feather"};default->new String[]{"Start tick","End tick","Feather ticks"};};}
    @Override protected void init(){
        fields.clear(); if(width<340||height<220){button("Done",width/2-40,height-26,80,this::onClose);return;}
        if(sample)button("Sample image…",10,32,150,()->minecraft.setScreenAndShow(new SampleScreen(this)));
        if(!sample){String[] tabs={"Biome & strength","Altitude","Time"};int tw=(width-30)/3;
            for(int i=0;i<tabs.length;i++){final int target=i;Button tab=button(tabs[i],10+i*(tw+5),32,tw,()->{page=target;error="";rebuildWidgets();});tab.active=i!=page;}}
        String[] names=labels();int gap=5,w=(width-20-gap*(names.length-1))/names.length;
        for(int i=0;i<names.length;i++){final int index=i,draftPage=page;EditBox f=addRenderableWidget(new EditBox(font,10+i*(w+gap),82,w,20,Component.literal(names[i])));f.setMaxLength(512);f.setValue(drafts[page][i]);f.setResponder(value->{drafts[draftPage][index]=value;error="";discardArmed=false;});fields.add(f);}
        button("Apply all",10,110,100,this::apply);
        if(sample){button("Reset sample",115,110,105,()->setFields("minecraft:plains","64","6000"));
            button("Use world",225,110,105,()->{
                var context=WorldContext.capture(minecraft);
                if(context.isEmpty()||context.get().biome().isEmpty()||context.get().dayTime().isEmpty()) {error="World biome/time unavailable; draft unchanged.";return;}
                var c=context.get();setFields(c.biome().orElseThrow(),Double.toString(c.cameraY()),Double.toString(c.dayTime().orElseThrow()));
                error="Copied world snapshot; clock synchronization is unverified.";
            });}
        else if(page==2){button("Day",115,110,70,()->setFields("0","12000","0"));button("Night",190,110,70,()->setFields("12000","0","0"));button("Clear",265,110,65,()->setFields("","",""));}
        button("Cancel",width-155,height-30,70,()->minecraft.gui.setScreen(parent));
        button("Done",width-80,height-30,70,()->{ if(apply()) minecraft.gui.setScreen(parent); });
    }
    private void setFields(String... values){for(int i=0;i<values.length;i++)fields.get(i).setValue(values[i]);error="";}
    private double number(int p,int i,String name){try{double v=Double.parseDouble(drafts[p][i].strip());if(Double.isFinite(v))return v;}catch(NumberFormatException ignored){}throw new IllegalArgumentException(name+" must be a number");}
    private boolean allBlank(int p){return Arrays.stream(drafts[p]).allMatch(String::isBlank);}
    private boolean apply(){int validating=0;try{
        if(sample){String biome=drafts[0][0].strip();if(!biome.matches("[a-z0-9_.-]+:[a-z0-9/._-]+"))throw new IllegalArgumentException("Use a biome ID such as minecraft:plains");double y=number(0,1,"Camera Y"),time=number(0,2,"Time");if(time<0||time>=24000)throw new IllegalArgumentException("Time must be 0–23999");Session.sampleBiome=biome;Session.sampleY=y;Session.sampleTime=time;}
        else{
            var layers=new ArrayList<>(Session.project.layers());var old=layers.get(Session.selectedLayer);var biomes=new HashSet<String>();String text=drafts[0][0].strip();
            if(!text.isEmpty())for(String id:text.split(",",-1)){String value=id.strip();if(!value.matches("[a-z0-9_.-]+:[a-z0-9/._-]+"))throw new IllegalArgumentException("Use biome IDs such as minecraft:plains");if(!biomes.add(value))throw new IllegalArgumentException("Duplicate biome ID: "+value);}
            double strength=number(0,1,"Strength");if(strength<0||strength>1)throw new IllegalArgumentException("Strength must be between 0 and 1");
            validating=1;Atmosphere.Range altitude=null;
            if(!allBlank(1)){double min=number(1,0,"Minimum Y"),max=number(1,1,"Maximum Y"),feather=number(1,2,"Feather");if(min>max)throw new IllegalArgumentException("Minimum Y must not exceed maximum Y");if(feather<0)throw new IllegalArgumentException("Feather must be at least 0");altitude=new Atmosphere.Range(min,max,feather);}
            validating=2;Atmosphere.CircularRange time=null;
            if(!allBlank(2)){double start=number(2,0,"Start tick"),end=number(2,1,"End tick"),feather=number(2,2,"Feather");if(start<0||start>=24000||end<0||end>=24000)throw new IllegalArgumentException("Start and end must be 0–23999");if(feather<0||feather>12000)throw new IllegalArgumentException("Feather must be 0–12000 ticks");time=new Atmosphere.CircularRange(start,end,24000,feather);}
            layers.set(Session.selectedLayer,new Project.Layer(old.name(),old.project(),old.enabled(),strength,biomes,altitude,time));Session.edit(Session.project.withLayers(layers));Session.commit();
        }
        error="Applied.";return true;
    }catch(IllegalArgumentException e){if(!sample&&page!=validating){page=validating;rebuildWidgets();}error=e.getMessage();return false;}}
    @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float d){g.fill(0,0,width,height,0xFF10191E);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float d){
        super.extractRenderState(g,x,y,d);g.text(font,sample?"SIMULATED SAMPLE CONTEXT":"LAYER CONDITIONS",10,10,0xFF77C9BC);if(width<340||height<220)return;
        String[] names=labels();int gap=5,w=(width-20-gap*(names.length-1))/names.length;for(int i=0;i<names.length;i++)g.text(font,font.plainSubstrByWidth(names[i],w),10+i*(w+gap),69,0xFF9DB3BD);
        String help=sample?"Preview sample only; it does not change the world.":switch(page){case 0->"Empty = every biome. IDs are not checked against the game; spelling must match. Conditions combine.";case 1->"Leave all fields blank to disable. Feather softens the range edges.";default->"Blank disables. If End is before Start the range wraps past tick 24000 (sunrise); feather softens edges.";};
        g.text(font,font.plainSubstrByWidth(help,width-20),10,140,0xFF9DB3BD);if(!sample)g.text(font,font.plainSubstrByWidth(page==2?"0: sunrise  •  6000: noon  •  12000: sunset  •  18000: midnight":"Done applies all pages. Cancel discards.",width-20),10,155,0xFF9DB3BD);g.text(font,font.plainSubstrByWidth(error,width-20),10,172,0xFFD9C993);
    }
    private boolean discardArmed;
    /** Escape applies valid input. If it is invalid, the first press explains and a second discards, so it is never a trap. */
    @Override public void onClose(){
        if(apply()||discardArmed){minecraft.gui.setScreen(parent);return;}
        discardArmed=true;error=error+" (Esc again or Cancel discards)";
    }
    @Override public boolean isPauseScreen(){return false;}
}
