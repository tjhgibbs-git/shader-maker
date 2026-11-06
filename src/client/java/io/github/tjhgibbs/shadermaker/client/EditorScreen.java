package io.github.tjhgibbs.shadermaker.client;

import io.github.tjhgibbs.shadermaker.*;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Grade editor for the selected layer (or the base). Opened from the Layers home; Done, Enter and Esc return there. */
public final class EditorScreen extends Screen {
    private final Screen parent;
    private static int tab;
    private int page;
    // 0: composite, 1: original, 2: selected grade without conditions.
    private int sampleView;
    private Button undo, redo, apply, disable;
    private int leftWidth, contentBottom, previewX, previewW;
    private static final String[] TABS = {"Light", "Levels", "Balance", "Curves"};
    private static final List<List<String>> CONTROL_GROUPS = List.of(
        List.of("opacity", "exposure", "contrast", "saturation", "vibrance", "shoulder", "vignette"),
        List.of("black", "white", "gamma"), List.of("red", "green", "blue"));
    public EditorScreen(Screen parent) { super(Component.literal("Shader Maker grade")); this.parent=parent; }
    private Button button(String label,int x,int y,int w,Runnable action) {
        return addRenderableWidget(Button.builder(Component.literal(label),b -> action.run()).bounds(x,y,w,20).build());
    }
    private Button button(String label,int x,int y,int w,String tip,Runnable action) {
        var b=button(label,x,y,w,action); b.setTooltip(Tooltip.create(Component.literal(tip))); return b;
    }
    @Override protected void init() {
        if (width<340 || height<220) { button("Done",width/2-40,height-26,80,this::finishEditing); return; }
        leftWidth=Math.min(340,(width-30)*45/100); contentBottom=height-52;
        previewX=leftWidth+20; previewW=width-previewX-10;
        button("< Layers",10,8,64,"Back to the layer list (Esc / Enter)",this::finishEditing);
        int rx=width-10;
        rx-=86; button("Reset grade…",rx,8,86,"Restore this grade to Neutral, including all curves.",this::confirmResetGrade);
        rx-=48; redo=button("Redo",rx,8,44,"Redo (Ctrl+Y)",() -> {Session.undo(true);rebuildWidgets();});
        rx-=48; undo=button("Undo",rx,8,44,"Undo (Ctrl+Z)",() -> {Session.commit();Session.undo(false);rebuildWidgets();});
        int x=10,tabWidth=(leftWidth-9)/4;
        for(int i=0;i<TABS.length;i++) { final int t=i; var b=button(TABS[i],x,36,tabWidth,() -> { Session.commit();tab=t;page=0;rebuildWidgets(); }); b.active=tab!=i;x+=tabWidth+3; }
        if(tab<3) {
            List<Project.Control> controls = CONTROL_GROUPS.get(tab).stream()
                .map(key -> Project.CONTROLS.stream().filter(c -> c.key().equals(key)).findFirst().orElseThrow())
                .toList();
            int rows=Math.max(1,(contentBottom-88)/24);
            int pages=(controls.size()+rows-1)/rows; page=Math.min(page,pages-1);
            int y=62;
            for (int i=page*rows;i<Math.min(controls.size(),(page+1)*rows);i++) {
                addRenderableWidget(new GradeSlider(10,y,leftWidth,controls.get(i))); y+=24;
            }
            if(pages>1) {
                button("<",10,contentBottom-20,24,() -> {page=(page+pages-1)%pages;rebuildWidgets();});
                button((page+1)+" / "+pages+"  >",38,contentBottom-20,leftWidth-28,() -> {page=(page+1)%pages;rebuildWidgets();});
            }
        } else {
            button("Edit curves…",10,62,leftWidth,"Open the curve editor: master and per-channel curves.",() -> {Session.commit();minecraft.setScreenAndShow(new CurveScreen(this));});
            button("Gentle S curve",10,86,leftWidth,() -> curvePreset(false));
            button("Lifted shadows",10,110,leftWidth,() -> curvePreset(true));
            button("Reset all curves",10,134,leftWidth,() -> {Session.editGrade(Session.grade().withCurves(Curves.identity()));Session.commit();});
        }
        // Preview column: preset, picture, view switches.
        var choices = new ArrayList<>(List.of("Choose…")); choices.addAll(Project.PRESETS);
        presetChoice=matchingPreset();
        presetButton=addRenderableWidget(CycleButton.<String>builder(Component::literal,presetChoice)
            .withValues(choices).withTooltip(value -> Tooltip.create(Component.literal("Replace this grade with a preset look (Undo restores it)")))
            .create(previewX,36,previewW,20,Component.literal("Preset"),(b,value) -> {
                presetChoice=value; if (!value.equals("Choose…")) { Session.editGrade(Project.preset(value));Session.commit();rebuildWidgets(); }
            }));
        int imageBottom=imageBottom();
        int third=(previewW-8)/3;
        String[] views={"Final","Original","Solo"};
        for(int i=0;i<3;i++){final int v=i;var b=button(views[i],previewX+i*(third+4),imageBottom+4,i==2?previewW-2*(third+4):third,
            new String[]{"Everything: base grade plus all active layers.","The scene with no grading.","Only this grade, ignoring its conditions."}[i],() -> {sampleView=v;rebuildWidgets();});b.active=sampleView!=i;}
        // Footer.
        int y=height-26;
        apply=button("Apply to game",10,y,104,"Load the pack into this world so you can see the grade for real. Reapply after edits.",() -> {Session.commit();Session.apply(true);});
        if (!PackCompiler.issues(Session.project).isEmpty()) apply.setTooltip(Tooltip.create(Component.literal(String.join(" ",PackCompiler.issues(Session.project)))));
        disable=button("Disable",118,y,56,"Remove the preview pack from the game.",() -> Session.apply(false));
        button("Done",width-70,y,60,"Back to the layer list (Enter)",this::finishEditing);
    }
    private int imageBottom(){int top=62,maxH=Math.max(30,contentBottom-top-60);return top+Math.min(maxH,previewW*9/16);}
    private String presetChoice="Choose…";
    private CycleButton<String> presetButton; private Project presetSeen;
    private void confirmResetGrade() {
        String target=Session.selectedLayer<0?"the base grade":"the grade of \""+Session.project.layers().get(Session.selectedLayer).name()+"\"";
        minecraft.setScreenAndShow(new ConfirmScreen(confirmed -> {
            if(confirmed) {
                Session.commit();
                Session.editGrade(Project.neutral());
                Session.commit();
                Session.message="Grade reset to Neutral. Undo restores the previous grade.";
            }
            minecraft.setScreenAndShow(this);
        },Component.literal("Reset grade to Neutral?"),
            Component.literal("Reset "+target+" to basic values and straight curves? Layer names, conditions, blend settings, other layers and fog are kept. You can undo this."),
            Component.literal("Reset grade"),Component.literal("Cancel")));
    }
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
    @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float delta) { g.fill(0,0,width,height,LayerScreen.BG); }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta) {
        if(width>=340&&height>=220) g.fill(previewX-4,58,width-6,contentBottom+2,0xFF192A30);
        super.extractRenderState(g,mx,my,delta);
        if(width<340 || height<220) {
            g.text(font,"Use a smaller GUI scale",10,40,0xFFF0EEE4);
            g.text(font,"to open the editor.",10,54,0xFF9DB3BD);return;
        }
        String target=Session.selectedLayer<0?"Base grade":Session.project.layers().get(Session.selectedLayer).name();
        g.text(font,fit("GRADE · "+target,width-80-260),82,14,LayerScreen.ACCENT);
        if(presetButton!=null&&presetSeen!=Session.project){presetSeen=Session.project;String m=matchingPreset();if(!m.equals(presetButton.getValue())){presetChoice=m;presetButton.setValue(m);}}
        undo.active=Session.history.canUndo();redo.active=Session.history.canRedo();
        apply.active=!Session.busy&&PackCompiler.issues(Session.project).isEmpty();disable.active=!Session.busy&&Session.active();
        int top=62,bottom=imageBottom(),ph=bottom-top,pw=Math.min(previewW,ph*16/9);
        GradePreview.draw(g,previewX+(previewW-pw)/2,top,pw,ph,sampleView);
        int infoY=bottom+30;
        if(infoY+10<contentBottom) g.text(font,fit((SampleImage.current==null?"Synthetic specimen":SampleImage.current.name)+" · "+Session.sampleSource+": "+Math.round(Session.sampleY),previewW),previewX,infoY,0xFF9DB3BD);
        if(infoY+22<contentBottom) g.text(font,fit(layerStatus(),previewW),previewX,infoY+12,0xFFD9C993);
        String status=Session.visibleMessage();
        if(status.isEmpty()&&!PackCompiler.issues(Session.project).isEmpty())status="Export unavailable · open Resolve export on the Layers page for details.";
        if(status.isEmpty())status="Drag sliders or type values · Enter or Done returns to the layer list";
        g.text(font,fit(status,width-20),10,height-40,0xFFD9C993);
    }
    private String fit(String text,int w) { return font.width(text)<=w?text:font.plainSubstrByWidth(text,Math.max(0,w-10))+"…"; }
    private String layerStatus() {
        if (Session.selectedLayer<0) return "Base grade · always applied";
        var layer=Session.project.layers().get(Session.selectedLayer);
        if (sampleView==2) return "Solo ignores layer conditions";
        if (!layer.enabled()) return "Hidden in preview and export";
        if(layer.contextControls().active()&&!layer.contextControls().matches(Session.signalSamples))return "Inactive: render signals (simulated)";
        if(layer.visualBiomeGroup()!=null&&!VisualBiomes.previewMatch(layer.visualBiomeGroup(),Session.sampleBiome))return "Inactive: sky colour (simulated)";
        if (!layer.biomes().isEmpty() && !layer.biomes().contains(Session.sampleBiome)) return "Inactive: legacy exact biome ID";
        if (layer.altitude()!=null&&layer.altitude().weight(Session.sampleY)==0) return "Inactive: height";
        if (layer.time()!=null&&layer.time().weight(Session.sampleTime)==0) return "Inactive: time";
        if (layer.weather()!=null&&layer.weather().weight(GradePreview.context())==0) return "Inactive: weather";
        if (layer.lightning()!=null&&Atmosphere.unitWeight(layer.lightning(),Session.sampleFlash)==0) return "Inactive: lightning flash";
        return "Layer weight: "+Math.round(100*layer.weight(GradePreview.context()))+"% · use Solo to judge the grade alone";
    }
    @Override public boolean keyPressed(KeyEvent event) {
        // Handle screen shortcuts before focused buttons/sliders can act on Enter.
        if ((event.key()==InputConstants.KEY_RETURN || event.key()==InputConstants.KEY_NUMPADENTER)
                && !event.hasControlDown() && !event.hasAltDown() && !event.hasShiftDown()) {
            finishEditing();return true;
        }
        if (!(getFocused() instanceof EditBox) && event.hasControlDown() && !event.hasAltDown()) {
            if (event.key()==InputConstants.KEY_Z || event.key()==InputConstants.KEY_Y) {
                Session.commit(); Session.undo(event.key()==InputConstants.KEY_Y || event.hasShiftDown()); rebuildWidgets(); return true;
            }
        }
        return super.keyPressed(event);
    }
    private void finishEditing() {
        Session.commit();
        if(parent instanceof LayerScreen) minecraft.setScreenAndShow(parent);
        else minecraft.setScreenAndShow(new LayerScreen(parent));
    }
    @Override public void onClose() {finishEditing();}
    @Override public boolean isPauseScreen() {return false;}
}
