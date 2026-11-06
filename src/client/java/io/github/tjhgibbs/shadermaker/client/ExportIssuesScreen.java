package io.github.tjhgibbs.shadermaker.client;

import io.github.tjhgibbs.shadermaker.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Review every affected layer and make explicit, undoable repairs. Saving never needs repair. */
final class ExportIssuesScreen extends Screen {
    private final Screen parent;
    private int index, issuePage;
    ExportIssuesScreen(Screen parent,int index) {
        super(Component.literal("Export availability"));this.parent=parent;this.index=index;
    }
    private Button button(String label,int x,int y,int w,Runnable action) {
        return addRenderableWidget(Button.builder(Component.literal(label),b->action.run()).bounds(x,y,w,20).build());
    }
    private List<Integer> affected() {
        var result=new ArrayList<Integer>();
        for(int i=0;i<Session.project.layers().size();i++)if(!PackCompiler.layerIssues(Session.project.layers().get(i)).isEmpty())result.add(i);
        return result;
    }
    @Override protected void init() {
        var affected=affected();
        if(!affected.contains(index))index=affected.isEmpty()?-1:affected.getFirst();
        if(index>=0) {
            int half=(width-24)/2;
            button("Next reason",width-100,28,90,()->{issuePage++;rebuildWidgets();});
            button("< Previous layer",10,height-100,half,()->step(-1));
            button("Next layer >",14+half,height-100,half,()->step(1));
            button("Review / clear conditions…",10,height-76,half,()->{Session.selectedLayer=index;minecraft.setScreenAndShow(new LayerConditionsScreen(this));});
            var layer=Session.project.layers().get(index);
            button(layer.enabled()?"Disable layer (keep settings)":"Layer disabled · settings kept",14+half,height-76,half,()->{
                var layers=new ArrayList<>(Session.project.layers());var l=layers.get(index);
                layers.set(index,l.withEnabled(false));
                Session.edit(Session.project.withLayers(layers));Session.commit();rebuildWidgets();
            }).active=layer.enabled();
        }
        button("Save project",10,height-28,(width-24)/2,Session::save);
        button("Back",14+(width-24)/2,height-28,(width-24)/2,this::onClose);
    }
    private void step(int delta){var a=affected();index=a.get(Math.floorMod(a.indexOf(index)+delta,a.size()));issuePage=0;rebuildWidgets();}
    @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float d){g.fill(0,0,width,height,0xFF10191E);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float d) {
        super.extractRenderState(g,x,y,d);
        g.text(font,"STANDALONE PACK · EXPORT AVAILABILITY",10,10,0xFF77C9BC);
        int yPos=48;
        var lines=new ArrayList<String>();
        if(index<0){lines.addAll(PackCompiler.issues(Session.project,Session.activation));lines.add("Projects can always be saved, including unavailable settings.");}
        if(index>=0){var l=Session.project.layers().get(index);g.text(font,font.plainSubstrByWidth(l.name()+" · "+(l.enabled()?"blocks export":"disabled"),Math.max(1,width-120)),10,33,0xFFD9C993);var reasons=PackCompiler.layerIssues(l);lines.add("Reason "+(issuePage%reasons.size()+1)+" / "+reasons.size());lines.add(reasons.get(issuePage%reasons.size()));}
        if(PackCompiler.issues(Session.project,Session.activation).isEmpty())lines.add("Ready to export. Return to the editor and choose Export ZIP.");
        for(String line:lines){
            while(!line.isEmpty()&&yPos<height-112){
                String part=font.plainSubstrByWidth(line,Math.max(1,width-20));if(part.isEmpty())break;
                if(part.length()<line.length()){int cut=part.lastIndexOf(' ');if(cut>0)part=part.substring(0,cut+1);}
                g.text(font,part,10,yPos,0xFFD9C993);line=line.substring(part.length());yPos+=11;
            }
            yPos+=4;
        }
        g.text(font,font.plainSubstrByWidth(Session.visibleMessage(),width-20),10,height-42,0xFF77C9BC);
    }
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
