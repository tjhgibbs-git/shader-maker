package io.github.tjhgibbs.shadermaker.client;

import io.github.tjhgibbs.shadermaker.*;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

/** In-game look development. The reference specimen is immediate; Apply reloads the real game effect. */
public final class EditorScreen extends Screen {
    private final Screen parent;
    private int tab, page;
    // 0: composite, 1: original, 2: selected grade without conditions.
    private int sampleView;
    private Button undo, redo, apply, disable;
    private int leftWidth, contentBottom;
    private static final String[] TABS = {"Light", "Levels", "Balance", "Curves"};
    private static final List<List<String>> CONTROL_GROUPS = List.of(
        List.of("opacity", "exposure", "contrast", "saturation", "vibrance", "shoulder", "vignette"),
        List.of("black", "white", "gamma"), List.of("red", "green", "blue"));
    public EditorScreen(Screen parent) { super(Component.literal("Shader Maker")); this.parent=parent; Session.selectedLayer=-1; }
    private Button button(String label,int x,int y,int w,Runnable action) {
        return addRenderableWidget(Button.builder(Component.literal(label),b -> action.run()).bounds(x,y,w,20).build());
    }
    @Override protected void init() {
        if (width<340 || height<220) { button("Done",width/2-40,height-26,80,this::onClose); return; }
        leftWidth=Math.min(270,(width-30)/2); contentBottom=height-85;
        EditBox name = new EditBox(font,width-156,10,146,20,Component.literal("Project name"));
        name.setMaxLength(48); name.setValue(Session.name); name.setResponder(value -> Session.name=value); addRenderableWidget(name);
        int x=10,tabWidth=Math.min(57,(width-120)/4-3);
        for(int i=0;i<TABS.length;i++) { final int t=i; var b=button(TABS[i],x,40,tabWidth,() -> { Session.commit();tab=t;page=0;rebuildWidgets(); }); b.active=tab!=i;x+=tabWidth+3; }
        undo=button("Undo",width-100,40,43,() -> {Session.commit();Session.undo(false);rebuildWidgets();});
        redo=button("Redo",width-54,40,44,() -> {Session.undo(true);rebuildWidgets();});
        if(tab<3) {
            List<Project.Control> controls = CONTROL_GROUPS.get(tab).stream()
                .map(key -> Project.CONTROLS.stream().filter(c -> c.key().equals(key)).findFirst().orElseThrow())
                .toList();
            int rows=Math.max(1,(contentBottom-88)/22);
            int pages=(controls.size()+rows-1)/rows; page=Math.min(page,pages-1);
            var choices = new ArrayList<>(List.of("Choose…")); choices.addAll(Project.PRESETS);
            presetChoice=matchingPreset();
            presetButton=addRenderableWidget(CycleButton.<String>builder(Component::literal,presetChoice)
                .withValues(choices).withTooltip(value -> Tooltip.create(Component.literal("Replace the current look with a preset")))
                .create(leftWidth+20,68,width-leftWidth-30,20,Component.literal("Preset"),(b,value) -> {
                    presetChoice=value; if (!value.equals("Choose…")) { Session.editGrade(Project.preset(value));Session.commit();rebuildWidgets(); }
                }));
            int y=68;
            for (int i=page*rows;i<Math.min(controls.size(),(page+1)*rows);i++) {
                addRenderableWidget(new GradeSlider(10,y,leftWidth,controls.get(i))); y+=22;
            }
            if(pages>1) {
                button("<",10,contentBottom-20,24,() -> {page=(page+pages-1)%pages;rebuildWidgets();});
                button((page+1)+" / "+pages+"  >",38,contentBottom-20,leftWidth-28,() -> {page=(page+1)%pages;rebuildWidgets();});
            }
        } else {
            button("Edit curves…",10,66,leftWidth,() -> minecraft.setScreenAndShow(new CurveScreen(this)));
            button("Gentle S curve",10,90,leftWidth,() -> curvePreset(false));
            button("Lifted shadows",10,114,leftWidth,() -> curvePreset(true));
            if(contentBottom>175) button("Reset all curves",10,150,leftWidth,() -> {Session.editGrade(Session.grade().withCurves(Curves.identity()));Session.commit();});
        }
        int previewX=leftWidth+20, previewWidth=width-previewX-10;
        int sampleButton=(previewWidth-8)/3;
        var view=button(new String[]{"All", "Original", "Solo"}[sampleView],previewX,contentBottom-20,sampleButton,() -> {sampleView=(sampleView+1)%3;rebuildWidgets();});
        view.setTooltip(Tooltip.create(Component.literal("Cycle sample: all layers, original, selected grade alone. Choose a screenshot under Context → Sample image.")));
        button("Context",previewX+sampleButton+4,contentBottom-20,sampleButton,() -> {Session.commit();minecraft.setScreenAndShow(new LayerConditionsScreen(this,true));});
        button("Layers…",previewX+2*(sampleButton+4),contentBottom-20,previewWidth-2*(sampleButton+4),() -> {Session.commit();minecraft.setScreenAndShow(new LayerScreen(this));});
        int y=height-77; int gap=4; int fileW=(width-20-3*gap)/4;
        button("Save project",10,y,fileW,Session::save);
        button("Load project",10+fileW+gap,y,fileW,() -> {Session.commit();minecraft.setScreenAndShow(new LibraryScreen(this));});
        button(Session.activation==PackCompiler.Activation.ALWAYS?"Auto pack":"Command pack",10+2*(fileW+gap),y,fileW,() -> { Session.activation=Session.activation==PackCompiler.Activation.ALWAYS?PackCompiler.Activation.COMMAND:PackCompiler.Activation.ALWAYS;rebuildWidgets();});
        var export=button("Export ZIP",10+3*(fileW+gap),y,fileW,Session::export);
        y=height-53;
        int applyW=Math.max(100,fileW);
        apply=button("Apply to game",10,y,applyW,() -> {Session.commit();Session.apply(true);});
        if (Session.project.layers().stream().anyMatch(Project.Layer::enabled)) {
            var unsupported=Tooltip.create(Component.literal("Enabled layers work in the sample only. Disable them to apply or export."));
            apply.setTooltip(unsupported); export.setTooltip(unsupported);
        }
        disable=button("Disable",14+applyW,y,65,() -> Session.apply(false));
        button("Done",width-70,y,60,this::onClose);
        button("Path",width-120,y,46,() -> {try {java.nio.file.Files.createDirectories(Session.ROOT);minecraft.keyboardHandler.setClipboard(Session.ROOT.toString());Session.message="Copied project folder path";} catch(Exception e){Session.message="Could not open folder: "+e.getMessage();}});
    }
    private String presetChoice="Choose…";
    private CycleButton<String> presetButton; private Project presetSeen;
    /** The preset the selected grade exactly equals, else "Choose…", so the label never claims a stale preset. */
    private String matchingPreset() {
        var grade=Session.grade().withLayers(List.of());
        for(String preset:Project.PRESETS) if(Project.preset(preset).withLayers(List.of()).equals(grade)) return preset;
        return "Choose…";
    }
    private void curvePreset(boolean lifted) {
        var points=lifted?List.of(new Curves.Point(0,.08),new Curves.Point(.5,.54),new Curves.Point(1,1)):
            List.of(new Curves.Point(0,0),new Curves.Point(.25,.2),new Curves.Point(.75,.8),new Curves.Point(1,1));
        Session.editGrade(Session.grade().withCurve(0,new Curves.Curve(points)));Session.commit();
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float delta) { g.fill(0,0,width,height,0xFF10191E); }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta) {
        super.extractRenderState(g,mx,my,delta);
        g.text(font,"SHADER MAKER",10,10,0xFFF0EEE4);
        if(width<340 || height<220) {
            g.text(font,"Use a smaller GUI scale",10,40,0xFFF0EEE4);
            g.text(font,"to open the editor.",10,54,0xFF9DB3BD);return;
        }
        g.text(font,fit(Session.selectedLayer<0?"EDITING BASE GRADE":"EDITING "+Session.project.layers().get(Session.selectedLayer).name(),width-20),10,25,0xFF77C9BC);
        if(presetButton!=null&&presetSeen!=Session.project&&tab<3){presetSeen=Session.project;String m=matchingPreset();if(!m.equals(presetButton.getValue())){presetChoice=m;presetButton.setValue(m);}}
        undo.active=Session.history.canUndo();redo.active=Session.history.canRedo();
        apply.active=!Session.busy;disable.active=!Session.busy&&Session.active();
        int px=leftWidth+20, pw=width-px-10;
        g.text(font,fit(new String[]{"ALL", "ORIGINAL", "SOLO"}[sampleView]+" · "+(SampleImage.current==null?"Synthetic":SampleImage.current.name),pw),px,tab<3?94:70,0xFF77C9BC);
        int sampleY=tab<3?106:84;
        int sampleH=Math.max(1,Math.min(80,contentBottom-sampleY-26));
        drawSpecimen(g,px,sampleY,pw,sampleH);
        int infoY=sampleY+sampleH+7;
        if(infoY+21<contentBottom-22) {
            g.text(font,fit("Sample: "+Session.sampleBiome.replace("minecraft:","")+" Y "+Math.round(Session.sampleY)+" t "+Math.round(Session.sampleTime),pw),px,infoY,0xFF9DB3BD);
            g.text(font,fit(layerStatus(),pw),px,infoY+12,0xFFD9C993);
        }
        g.text(font,fit(Session.visibleMessage(),width-20),10,height-24,0xFFD9C993);
        if(height>260) g.text(font,fit("Exports: .minecraft/shader-maker/exports",width-20),10,height-13,0xFF758B94);
    }
    private String fit(String text,int w) { return font.width(text)<=w?text:font.plainSubstrByWidth(text,Math.max(0,w-10))+"…"; }
    private String layerStatus() {
        if (Session.selectedLayer<0) return "Base grade · sample only";
        var layer=Session.project.layers().get(Session.selectedLayer);
        if (sampleView==2) return "Solo ignores layer conditions";
        if (!layer.enabled()) return "Inactive: layer disabled";
        if (!layer.biomes().isEmpty() && !layer.biomes().contains(Session.sampleBiome)) return "Inactive: biome";
        double altitude=layer.altitude()==null?1:layer.altitude().weight(Session.sampleY);
        if (altitude==0) return "Inactive: height";
        double time=layer.time()==null?1:layer.time().weight(Session.sampleTime);
        if (time==0) return "Inactive: time";
        return "Layer weight: "+Math.round(100*layer.strength()*altitude*time)+"%";
    }
    private void drawSpecimen(GuiGraphicsExtractor g,int x,int y,int w,int h) {
        var p=Session.project;
        var context=new Atmosphere.Context(Session.sampleTime,Session.sampleY,0,Session.sampleBiome,0);
        var solo=sampleView==2?Session.grade():null;
        int cols=SampleImage.WIDTH, rows=SampleImage.HEIGHT;
        for(int j=0;j<rows;j++) for(int i=0;i<cols;i++) {
            double u=i/(double)(cols-1),v=j/(double)(rows-1);
            double r=.32+.42*v, green=.47+.19*v,b=.61-.15*v;
            if(v>.49+Math.sin(u*11)*.09) {r=.28;green=.42;b=.37;}
            if(v>.67+Math.sin(u*8+1)*.08) {r=.16;green=.29;b=.25;}
            if(Math.hypot(u-.76,v-.24)<.08) {r=.98;green=.89;b=.67;}
            if(SampleImage.current!=null) {
                int pixel=SampleImage.current.pixels[j*cols+i];
                r=((pixel>>16)&255)/255.0; green=((pixel>>8)&255)/255.0; b=(pixel&255)/255.0;
            }
            // Keep the calibration ramp as a secondary strip for every reference.
            if(j>=rows-3) {r=green=b=u;}
            var color=new Atmosphere.Color(r,green,b,1);
            if(sampleView==0) color=p.evaluate(color,u*2-1,v*2-1,context);
            else if(sampleView==2) color=solo.grade().apply(color,u*2-1,v*2-1,solo.get("opacity"),solo.curves());
            int rgb=0xFF000000|((int)Math.round(color.r()*255)<<16)|((int)Math.round(color.g()*255)<<8)|(int)Math.round(color.b()*255);
            g.fill(x+i*w/cols,y+j*h/rows,x+(i+1)*w/cols,y+(j+1)*h/rows,rgb);
        }
        g.outline(x,y,w,h,0xFF4D696D);
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (!(getFocused() instanceof EditBox) && event.hasControlDown() && !event.hasAltDown()) {
            if (event.key()==InputConstants.KEY_Z || event.key()==InputConstants.KEY_Y) {
                Session.commit(); Session.undo(event.key()==InputConstants.KEY_Y || event.hasShiftDown()); rebuildWidgets(); return true;
            }
        }
        return super.keyPressed(event);
    }
    @Override public void onClose() {Session.commit();minecraft.gui.setScreen(parent);}
    @Override public boolean isPauseScreen() {return false;}
}
