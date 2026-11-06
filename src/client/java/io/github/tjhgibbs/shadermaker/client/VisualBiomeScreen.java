package io.github.tjhgibbs.shadermaker.client;

import io.github.tjhgibbs.shadermaker.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Sky-colour signal picker; vanilla biome buttons are shortcuts, never identity claims. */
final class VisualBiomeScreen extends Screen {
    private final Screen parent;
    private final Consumer<String> accept;
    private final String exactIds;
    private VisualBiomes.Group group;
    private int page,memberPage;
    private String hex,message="";
    VisualBiomeScreen(Screen parent,String selected,String exactIds,Consumer<String> accept){
        super(Component.literal("Sky colour context"));this.parent=parent;this.accept=accept;this.exactIds=exactIds;
        group=selected!=null?VisualBiomes.get(selected):VisualBiomes.GROUPS.get(3);hex=String.format(Locale.ROOT,"#%06x",group.rgb());
    }
    private Button button(String text,int x,int y,int w,Runnable r){return addRenderableWidget(Button.builder(Component.literal(text),b->r.run()).bounds(x,y,w,20).build());}
    private int rows(){return Math.max(1,(height-180)/24);}
    private int pageSize(){return rows()*2;}
    @Override protected void init(){
        var color=addRenderableWidget(new EditBox(font,10,31,width-120,20,Component.literal("Custom sky colour #RRGGBB")));color.setMaxLength(7);color.setValue(hex);color.setResponder(v->hex=v);
        button("Set custom",width-106,31,96,()->{try{String value=hex.strip().replaceFirst("^#","").toLowerCase(Locale.ROOT);if(!value.matches("[0-9a-f]{6}"))throw new IllegalArgumentException("Use #RRGGBB");group=VisualBiomes.get("custom_"+value);message="Custom signal selected. Use sky colour to keep it.";rebuildWidgets();}catch(IllegalArgumentException e){message=e.getMessage();}});
        int count=pageSize(),half=(width-24)/2;
        for(int i=page*count;i<Math.min(VisualBiomes.GROUPS.size(),(page+1)*count);i++){
            var choice=VisualBiomes.GROUPS.get(i);int slot=i-page*count;
            var b=button(String.format(Locale.ROOT,"#%06x · %s",choice.rgb(),choice.label()),10+(slot%2)*(half+4),70+(slot/2)*24,half,()->{group=choice;hex=String.format(Locale.ROOT,"#%06x",group.rgb());memberPage=0;rebuildWidgets();});
            b.setTooltip(Tooltip.create(Component.literal("Vanilla sky colour shortcut\n"+String.join("\n",choice.biomes())+"\n"+VisualBiomes.WARNING)));
        }
        int pages=(VisualBiomes.GROUPS.size()+count-1)/count;
        button("Vanilla colours "+(page+1)+" / "+pages+" >",10,height-100,half,()->{page=(page+1)%pages;rebuildWidgets();});
        button("Shared biome names >",14+half,height-100,half,()->{memberPage++;rebuildWidgets();}).active=!group.biomes().isEmpty();
        button("Cancel",10,height-28,70,this::onClose);
        var use=button((!exactIds.isBlank()?"Convert exact IDs → #":"Use sky colour #")+String.format(Locale.ROOT,"%06x",group.rgb()),84,height-28,width-94,()->{accept.accept(group.id());onClose();});
        if(!exactIds.isBlank())use.setTooltip(Tooltip.create(Component.literal("Explicit conversion: replaces these legacy exact biome IDs with a sky-colour signal: "+exactIds+". Stock matches: "+String.join(", ",group.biomes())+". Any location/datapack can provide this colour. Save conditions commits; Cancel discards.")));
    }
    private void line(GuiGraphicsExtractor g,String s,int y,int c){g.text(font,font.plainSubstrByWidth(s,width-20),10,y,c);}
    @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float d){g.fill(0,0,width,height,0xFF10191E);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float d){
        super.extractRenderState(g,x,y,d);line(g,"SKY COLOUR · #RRGGBB · DATAPACK SIGNAL",10,0xFF77C9BC);
        line(g,"Vanilla biome colours (shortcuts; hover for all shared names)",57,0xFF9DB3BD);
        var names=group.biomes();String members=names.isEmpty()?"Custom signal: set minecraft:visual/sky_color in your datapack.":"Shares: "+String.join(", ",names.subList((memberPage*2)%names.size(),Math.min(names.size(),(memberPage*2)%names.size()+2)));
        line(g,members,height-76,0xFF9DB3BD);
        line(g,"Hue match; needs visible sky. Darkness/weather/blending affect it.",height-64,0xFFFFCB83);
        line(g,!exactIds.isBlank()?"Conversion replaces legacy IDs: "+exactIds:message.isEmpty()?"Brightness is ignored; matching hue may occur anywhere.":message,height-44,0xFFFFCB83);
    }
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
