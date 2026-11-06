package io.github.tjhgibbs.shadermaker.client;

import io.github.tjhgibbs.shadermaker.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Explicit reference inputs, never inferred from screenshot RGB or a mod-only camera probe. */
final class RenderSignalInputsScreen extends Screen {
    private final Screen parent;
    private WorldSignals.Source source=WorldSignals.Source.FOG_COLOR;
    private String value="#C0D8FF",message="";
    private boolean terrain=Session.signalSamples.terrainVisible(),pixel=Session.signalSamples.terrainPixel();
    private final EnumMap<WorldSignals.Source,WorldSignals.Sample> values=new EnumMap<>(WorldSignals.Source.class);
    RenderSignalInputsScreen(Screen parent){super(Component.literal("Simulated render inputs"));this.parent=parent;values.putAll(Session.signalSamples.values());load();}
    private Button button(String text,int y,Runnable action){return addRenderableWidget(Button.builder(Component.literal(text),b->action.run()).bounds(10,y,width-20,20).build());}
    private void load(){var sample=values.get(source);value=sample==null?(source.colour()?"#C0D8FF":"1024"):source.colour()?String.format(Locale.ROOT,"#%02X%02X%02X",Math.round(sample.r()*255),Math.round(sample.g()*255),Math.round(sample.b()*255)):Double.toString(sample.r());}
    @Override protected void init(){
        if(width<340||height<250){button("Cancel",height-28,this::onClose);return;}
        addRenderableWidget(CycleButton.<WorldSignals.Source>builder(s->Component.literal(s.label),source).withValues(WorldSignals.Source.values()).displayOnlyValue().create(10,30,width-20,20,Component.literal("Input"),(b,s)->{source=s;load();message="";rebuildWidgets();}));
        var field=addRenderableWidget(new EditBox(font,10,56,width-20,20,Component.literal("Simulated colour / distance")));field.setMaxLength(64);field.setValue(value);field.setResponder(v->value=v);
        button("Set this input",82,()->{try{WorldSignals.Sample sample;if(source.colour()){if(!value.strip().matches("#[0-9a-fA-F]{6}"))throw new IllegalArgumentException("Use #RRGGBB.");sample=WorldSignals.Sample.colour(Integer.parseInt(value.strip().substring(1),16));}else sample=WorldSignals.Sample.distance(Double.parseDouble(value.strip()));values.put(source,sample);message="Input set in draft.";}catch(IllegalArgumentException e){message="Enter a finite distance or colour #RRGGBB.";}});
        button("Make this input unavailable",106,()->{values.remove(source);message="Unavailable input: matching conditions will be inactive.";});
        button(terrain?"Terrain signal: present (3+ samples)":"Terrain signal: absent",130,()->{terrain=!terrain;rebuildWidgets();});
        button(pixel?"Treat preview image as: marked blocks":"Treat preview image as: other scene",154,()->{pixel=!pixel;rebuildWidgets();}).setTooltip(Tooltip.create(Component.literal("Only affects the Blocks only scope. A photograph has no terrain mask; the whole specimen uses this assumption.")));
        button("Copy all inputs from where I'm standing",178,()->{int n=0;for(var src:WorldSignals.Source.values()){var v=LiveSignals.read(src);if(v.isPresent()){values.put(src,v.get());n++;}}terrain=true;load();message=n==0?"Join a world to read live values.":"Copied "+n+" live values. Save to use them.";rebuildWidgets();});
        button("Save simulated inputs",height-28,()->{Session.signalSamples=new WorldSignals.Samples(values,terrain,pixel);onClose();});
    }
    private void line(GuiGraphicsExtractor g,String s,int y){g.text(font,font.plainSubstrByWidth(s,width-20),10,y,0xFFFFCB83);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float d){super.extractRenderState(g,x,y,d);line(g,"SIMULATED RENDER INPUTS · reference preview only",10);if(width<340||height<250)return;line(g,"Set inputs explicitly; missing inputs never match.",206);if(height>=280)line(g,"Image has no block mask: the whole specimen uses your scope assumption.",220);line(g,message,height-43);}
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
