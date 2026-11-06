package io.github.tjhgibbs.shadermaker.client;

import io.github.tjhgibbs.shadermaker.*;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Terrain fog workspace: fog list and presets on the left, settings in the middle, simulated preview on the right.
 * Field edits apply immediately when valid; invalid text stays visible with an explanation and never clamps silently.
 */
final class FogScreen extends Screen {
    private final Screen parent;
    private int selected,tab,page,view;
    private static String chosenPreset=Fog.PRESETS.getFirst();
    private static Fog.Shape template=Fog.Shape.HEIGHT_BAND;
    private final Map<String,String> draft=new HashMap<>();
    private String message="";
    private boolean invalid;
    private double cameraY=70;
    private final List<int[]> labelPos=new ArrayList<>();
    private final List<String> labels=new ArrayList<>();
    private int listW,settingsX,settingsW,previewX,previewW,listTop=36;
    private boolean sidePreview;
    FogScreen(Screen parent){this(parent,0,null);}
    FogScreen(Screen parent,int index,String preset){
        super(Component.literal("Fog"));this.parent=parent;
        var mc=net.minecraft.client.Minecraft.getInstance();
        if(mc.player!=null)cameraY=Math.round(mc.player.getEyeY());
        if(preset!=null)addPreset(preset);
        else selected=Math.max(0,Math.min(index,Session.project.fog().size()-1));
        loadDraft();
    }
    private Fog current(){return selected<Session.project.fog().size()?Session.project.fog().get(selected):null;}
    private Button button(String text,int x,int y,int w,Runnable r){return addRenderableWidget(Button.builder(Component.literal(text),b->r.run()).bounds(x,y,Math.max(16,w),20).build());}
    private Button button(String text,int x,int y,int w,String tip,Runnable r){var b=button(text,x,y,w,r);b.setTooltip(Tooltip.create(Component.literal(tip)));return b;}
    private void replace(Fog f){var list=new ArrayList<>(Session.project.fog());list.set(selected,f);Session.edit(Session.project.withFog(list));}
    private void loadDraft(){draft.clear();invalid=false;var f=current();if(f==null)return;draft.put("name",f.name());f.values().forEach((k,v)->draft.put(k,LayerScreen.num(v)));var a=f.altitude();draft.put("minY",a==null?"":LayerScreen.num(a.min()));draft.put("maxY",a==null?"":LayerScreen.num(a.max()));draft.put("featherY",a==null?"":LayerScreen.num(a.feather()));}
    /** Applies the draft to the project without a history entry. Returns false and explains when invalid. */
    private boolean flush(){
        var f=current();if(f==null)return true;
        try{var values=new HashMap<String,Double>();for(String key:Fog.DEFAULTS.keySet())values.put(key,Double.parseDouble(draft.get(key).strip()));
            boolean has=List.of("minY","maxY","featherY").stream().anyMatch(k->!draft.get(k).isBlank());
            var a=has?new Atmosphere.Range(Double.parseDouble(draft.get("minY")),Double.parseDouble(draft.get("maxY")),draft.get("featherY").isBlank()?0:Double.parseDouble(draft.get("featherY"))):null;
            var next=new Fog(draft.get("name"),f.enabled(),f.shape(),f.falloff(),values,a,f.samples());if(!next.equals(f))replace(next);
            if(invalid)message="";invalid=false;return true;
        }catch(IllegalArgumentException e){invalid=true;message="Not applied: "+(e instanceof NumberFormatException?"enter numbers; a camera condition needs both minimum and maximum.":e.getMessage());return false;}
    }
    /** Structural change: requires a valid draft, then records one undo step. */
    private void change(Runnable r){if(flush()){Session.commit();r.run();Session.commit();loadDraft();rebuildWidgets();}}
    private void addPreset(String preset){
        var mc=net.minecraft.client.Minecraft.getInstance();var p=mc.player;
        double gy=64;
        if(p!=null){ // top of the first solid block below the player: right when flying and indoors
            gy=p.getBlockY();var pos=p.blockPosition().mutable();
            for(int i=0;i<96&&pos.getY()>mc.level.getMinY();i++,pos.move(0,-1,0))
                if(!mc.level.getBlockState(pos).getCollisionShape(mc.level,pos).isEmpty()){gy=pos.getY()+1;break;}
        }
        double x=p==null?0:p.getX(),y=p==null?64:p.getY(),z=p==null?0:p.getZ();
        var list=new ArrayList<>(Session.project.fog());var added=Fog.preset(preset,gy,x,y,z);list.addAll(added);
        Session.commit();Session.edit(Session.project.withFog(list));Session.commit();selected=list.size()-added.size();
        message="Added "+preset+(p!=null&&preset.matches("Ground mist|Low cloud|Interior haze|Misty morning")?" around you (Y "+(int)gy+"). Apply to game to see it.":". Apply to game to see it.");
    }
    private void field(String key,String label,int y,double min,double max){
        labels.add(label);labelPos.add(new int[]{settingsX,y});int numberW=Math.min(80,settingsW/3);
        var field=addRenderableWidget(new EditBox(font,settingsX+settingsW-numberW,y+11,numberW,20,Component.literal(label)));
        field.setMaxLength(32);field.setValue(draft.get(key));field.setResponder(v->{draft.put(key,v);flush();});
        if(Double.isFinite(min)){
            double value;try{value=Double.parseDouble(draft.get(key));}catch(Exception e){value=min;}
            final double initial=Math.clamp((value-min)/(max-min),0,1);
            addRenderableWidget(new AbstractSliderButton(settingsX,y+11,settingsW-numberW-4,20,Component.empty(),initial){
                {updateMessage();}
                @Override protected void updateMessage(){setMessage(Component.literal(LayerScreen.num(Math.round((min+value*(max-min))*10)/10d)));}
                @Override protected void applyValue(){double n=Math.round((min+value*(max-min))*10)/10d;field.setValue(LayerScreen.num(n));}
                @Override public void onRelease(MouseButtonEvent e){super.onRelease(e);Session.commit();}
            });
        }else if(Fog.DEFAULTS.containsKey(key)||key.equals("minY")||key.equals("maxY")){
            var mc=net.minecraft.client.Minecraft.getInstance();
            boolean here=mc.player!=null&&(key.equals("x")||key.equals("y")||key.equals("z")||key.equals("lower")||key.equals("upper")||key.equals("minY")||key.equals("maxY"));
            if(here)button("Use my position",settingsX,y+11,settingsW-numberW-4,"Set from where you are standing ("+(key.equals("x")?"X":key.equals("z")?"Z":"feet Y")+").",()->{var p=mc.player;double v=key.equals("x")?Math.floor(p.getX())+.5:key.equals("z")?Math.floor(p.getZ())+.5:p.getBlockY();field.setValue(LayerScreen.num(v));Session.commit();});
            else button("Default",settingsX,y+11,settingsW-numberW-4,()->field.setValue(LayerScreen.num(Fog.DEFAULTS.getOrDefault(key,0d))));
        }
    }
    private List<String> keys(){var f=current();if(f==null)return List.of();return switch(tab){
        case 0->f.falloff()==Fog.Falloff.LINEAR?List.of("strength","start","end"):List.of("strength","visibility","start");
        case 1->switch(f.shape()){case EVERYWHERE->List.of();case BELOW_HEIGHT->List.of("upper","fade");case HEIGHT_BAND->List.of("lower","upper","fade");case BOX->List.of("x","y","z","width","height","depth","fade");case SPHERE->List.of("x","y","z","radius","fade");};
        case 2->List.of("minY","maxY","featherY");default->List.of();};}
    private String label(String key){return switch(key){case "strength"->"Density (0–1)";case "start"->current().falloff()==Fog.Falloff.LINEAR?"Fog starts at (blocks)":"Clear zone around camera (blocks)";case "end"->"Full fog at (blocks)";case "visibility"->"Half the light lost every (blocks) · lower = thicker";case "lower"->"Bottom at world Y";case "upper"->"Top at world Y";case "fade"->"Soft edge (blocks)";case "minY"->"Only when camera Y ≥";case "maxY"->"…and camera Y ≤";case "featherY"->"Fade in over (blocks)";default->key.length()==1?"Centre "+key.toUpperCase(Locale.ROOT):Character.toUpperCase(key.charAt(0))+key.substring(1)+" (blocks)";};}

    @Override protected void init(){
        labels.clear();labelPos.clear();
        if(width<340||height<220){button("Back",10,height-28,80,this::onClose);return;}
        listW=Math.max(150,Math.min(230,width*26/100));
        settingsX=listW+20;sidePreview=width>=700;
        settingsW=sidePreview?Math.min(320,(width-settingsX-20)/2):width-settingsX-10;
        previewX=settingsX+settingsW+10;previewW=width-previewX-10;
        button("< Layers",10,8,64,"Back to the layer list (Esc)",this::onClose);
        int rx=width-10;
        rx-=48;var redo=button("Redo",rx,8,44,"Redo (Ctrl+Y)",()->history(true));redo.active=Session.history.canRedo();
        rx-=48;var undo=button("Undo",rx,8,44,"Undo (Ctrl+Z)",()->history(false));undo.active=Session.history.canUndo();

        // Left: fog objects, then the add panel.
        var fog=Session.project.fog();
        int y=listTop+14,addTop=height-52-80;
        for(int i=0;i<fog.size()&&y+22<=addTop;i++){
            final int index=i;var f=fog.get(i);
            button(f.enabled()?"On":"Off",10,y,26,"Show / hide this fog",()->change(()->{var list=new ArrayList<>(Session.project.fog());list.set(index,list.get(index).enabled(!list.get(index).enabled()));Session.edit(Session.project.withFog(list));}));
            button("✕",10+listW-18,y,18,"Delete (Undo restores it)",()->change(()->{var list=new ArrayList<>(Session.project.fog());list.remove(index);Session.edit(Session.project.withFog(list));selected=Math.max(0,Math.min(selected,list.size()-1));}));
            button("⧉",10+listW-38,y,18,"Duplicate",()->change(()->{var list=new ArrayList<>(Session.project.fog());var old=list.get(index);String n=old.name();list.add(index+1,old.named(n.substring(0,Math.min(n.length(),59))+" copy"));Session.edit(Session.project.withFog(list));selected=index+1;}));
            y+=24;
        }
        var preset=addRenderableWidget(CycleButton.<String>builder(Component::literal,chosenPreset).withValues(Fog.PRESETS)
            .withTooltip(p->Tooltip.create(Component.literal(Fog.presetHelp(p)))).create(10,addTop+14,listW-50,20,Component.literal("Preset"),(b,v)->chosenPreset=v));
        button("Add",10+listW-46,addTop+14,46,"Add this preset. Heights are placed around where you stand.",()->{if(flush()){addPreset(preset.getValue());loadDraft();rebuildWidgets();}});
        button(shapeName(template),10,addTop+52,listW-50,"Cycle the shape for a blank fog object",()->{template=Fog.Shape.values()[(template.ordinal()+1)%Fog.Shape.values().length];rebuildWidgets();});
        button("Add",10+listW-46,addTop+52,46,"Add a blank fog object of this shape",()->change(()->{var list=new ArrayList<>(Session.project.fog());list.add(Fog.create(template));Session.edit(Session.project.withFog(list));selected=list.size()-1;page=0;}));

        // Middle: settings of the selected fog.
        var f=current();
        if(f!=null){
            var name=addRenderableWidget(new EditBox(font,settingsX,listTop,settingsW,20,Component.literal("Fog name")));name.setMaxLength(64);name.setValue(draft.get("name"));name.setResponder(v->{draft.put("name",v);flush();});
            String[] tabs={"Look","Shape","Camera","Quality"};int tw=(settingsW-12)/4;
            for(int i=0;i<tabs.length;i++){final int t=i;button(tabs[i],settingsX+i*(tw+4),listTop+26,i==3?settingsW-3*(tw+4):tw,
                new String[]{"Density and how quickly fog builds up","Where the fog is: everywhere, below a height, a band, a box or a sphere","Only show this fog when the camera is within a height range","Edge smoothness for soft boxes and spheres"}[i],()->change(()->{tab=t;page=0;})).active=tab!=i;}
            int fy=listTop+52;
            if(tab==0){var b=button(f.falloff()==Fog.Falloff.NATURAL?"Falloff: Natural":"Falloff: Linear ramp",settingsX,fy,settingsW,
                "Natural fog thickens smoothly with distance. Linear ramps from a start to a full-fog distance (Everywhere shape only).",()->change(()->replace(current().falloff(current().falloff()==Fog.Falloff.NATURAL?Fog.Falloff.LINEAR:Fog.Falloff.NATURAL))));b.active=f.shape()==Fog.Shape.EVERYWHERE;fy+=24;}
            if(tab==1){var b=button("Shape: "+shapeName(f.shape()),settingsX,fy,settingsW,"Shape is fixed per fog object; add a new one to use another shape.",()->{});b.active=false;fy+=24;}
            if(tab==2){button("Always (clear camera condition)",settingsX,fy,settingsW,()->{draft.put("minY","");draft.put("maxY","");draft.put("featherY","");change(()->{});});fy+=24;}
            var keys=keys();int fit=Math.max(1,(height-60-fy)/34),rows=keys.size()>fit?Math.max(1,(height-86-fy)/34):fit,pages=Math.max(1,(keys.size()+rows-1)/rows);page=Math.min(page,pages-1);
            for(int i=page*rows;i<Math.min(keys.size(),(page+1)*rows);i++){String k=keys.get(i);double min=Double.NaN,max=0;
                if(k.equals("strength")){min=0;max=1;}else if(List.of("start","end","visibility","fade","width","height","depth","radius").contains(k)){min=k.equals("start")||k.equals("fade")?0:1;max=k.equals("fade")?16:256;}
                field(k,label(k),fy+(i-page*rows)*34,min,max);
            }
            if(pages>1)button("More fields "+(page+1)+" / "+pages+" >",settingsX,fy+rows*34+4,settingsW,()->change(()->page=(page+1)%pages));
            if(tab==3&&f.sampled())button(f.samples()+" samples per pixel",settingsX,fy,settingsW,"More samples give smoother soft edges and cost more GPU time.",()->change(()->replace(current().samples(current().samples()==8?16:current().samples()==16?32:8))));
        }
        // Right: simulated preview controls.
        if(sidePreview&&previewW>=120){int half=(previewW-4)/2;
            button(new String[]{"View: Final","View: No fog","View: Density"}[view],previewX,listTop,half,()->{view=(view+1)%3;rebuildWidgets();});
            button("Camera Y "+(int)cameraY,previewX+half+4,listTop,previewW-half-4,"Simulated camera height for the preview",()->{cameraY=cameraY>=150?40:cameraY+8;rebuildWidgets();});}
        // Footer.
        int fy=height-26;
        var apply=button("Apply to game",10,fy,104,"Load the pack into this world to see the fog for real.",()->{if(flush()){Session.commit();Session.apply(true);}});
        apply.active=!Session.busy&&PackCompiler.issues(Session.project).isEmpty();
        button("Disable",118,fy,56,"Remove the preview pack from the game.",()->Session.apply(false)).active=!Session.busy&&Session.active();
        button("Done",width-70,fy,60,"Back to the layer list",this::onClose);
    }
    private static String shapeName(Fog.Shape s){return switch(s){case EVERYWHERE->"Everywhere";case BELOW_HEIGHT->"Below a height";case HEIGHT_BAND->"Height band";case BOX->"Box";case SPHERE->"Sphere";};}
    private void history(boolean redo){flush();Session.commit();Session.undo(redo);selected=Math.min(selected,Math.max(0,Session.project.fog().size()-1));loadDraft();rebuildWidgets();}
    private void line(GuiGraphicsExtractor g,String text,int x,int y,int w,int color){g.text(font,font.width(text)<=w?text:font.plainSubstrByWidth(text,Math.max(1,w-6))+"…",x,y,color);}
    @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float d){g.fill(0,0,width,height,LayerScreen.BG);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float d){
        if(width>=340&&height>=220){
            g.fill(6,listTop-4,listW+14,height-48,LayerScreen.PANEL);
            var fog=Session.project.fog();int y=listTop+14,addTop=height-52-80;
            for(int i=0;i<fog.size()&&y+22<=addTop;i++){
                if(i==selected){g.fill(8,y-2,listW+12,y+22,LayerScreen.ROW_SELECTED);g.fill(8,y-2,10,y+22,LayerScreen.ACCENT);}
                line(g,fog.get(i).name(),40,y+6,listW-80,fog.get(i).enabled()?LayerScreen.TEXT:0xFF7D8C91);y+=24;
            }
            line(g,"FOG OBJECTS · densities add",10,listTop,listW,LayerScreen.ACCENT);
            if(fog.isEmpty())line(g,"None yet. Add a preset below.",10,listTop+18,listW,LayerScreen.DIM);
            g.fill(8,addTop,listW+12,addTop+1,0xFF2A3E45);
            line(g,"ADD A PRESET",10,addTop+4,listW,LayerScreen.ACCENT);
            line(g,"OR A BLANK SHAPE",10,addTop+42,listW,LayerScreen.ACCENT);
            if(sidePreview&&previewW>=120){
                g.fill(previewX-4,listTop-4,width-6,height-48,0xFF192A30);
                int ph=Math.max(20,height-48-(listTop+24)-30),charts=ph>=150?46:0;
                FogPreview.draw(g,previewX,listTop+24,previewW,ph-charts,cameraY,view);if(charts>0)FogPreview.graphs(g,previewX,listTop+24+ph-charts,previewW,charts,cameraY);
                line(g,"SIMULATED · pillars at 16/32/64/128/256 blocks",previewX,listTop+28+ph,previewW,LayerScreen.DIM);
                line(g,"Real fog takes the world's fog colour",previewX,listTop+40+ph,previewW,LayerScreen.DIM);
            }
        }
        super.extractRenderState(g,mx,my,d);
        line(g,"FOG · terrain only",82,14,Math.max(0,width-82-110),LayerScreen.ACCENT);
        if(width<340||height<220)return;
        var f=current();
        if(f==null)line(g,"Pick a preset on the left and press Add.",settingsX,listTop+6,settingsW,LayerScreen.STATUS);
        else{
            for(int i=0;i<labels.size();i++)line(g,labels.get(i),labelPos.get(i)[0],labelPos.get(i)[1],settingsW,LayerScreen.DIM);
            int hy=height-62;
            String help=switch(tab){case 0->"Colour follows the world's own fog colour.";case 1->f.shape()==Fog.Shape.EVERYWHERE?"Everywhere: no placement needed.":"World coordinates · Use my position to place it here.";case 2->"Leave blank for always.";default->f.sampled()?"Soft edges use sampled integration.":"Exact integration; no quality settings needed.";};
            line(g,help,settingsX,hy,settingsW,LayerScreen.DIM);
        }
        String status=!message.isEmpty()?message:Session.visibleMessage().isEmpty()?"Changes apply as you type · Apply to game to see them in the world · entities, sky and clouds keep vanilla fog":Session.visibleMessage();
        line(g,status,10,height-40,width-20,invalid?LayerScreen.WARN:LayerScreen.STATUS);
    }
    @Override public boolean mouseClicked(MouseButtonEvent e,boolean doubleClick){
        if(super.mouseClicked(e,doubleClick))return true;
        int y=listTop+14,addTop=height-52-80;
        for(int i=0;i<Session.project.fog().size()&&y+22<=addTop;i++){
            if(e.x()>=8&&e.x()<listW+12&&e.y()>=y-2&&e.y()<y+22){final int index=i;change(()->{selected=index;page=0;});return true;}
            y+=24;
        }
        return false;
    }
    @Override public boolean keyPressed(KeyEvent event){
        if(!(getFocused() instanceof EditBox)&&event.hasControlDown()&&!event.hasAltDown()&&(event.key()==InputConstants.KEY_Z||event.key()==InputConstants.KEY_Y)){history(event.key()==InputConstants.KEY_Y||event.hasShiftDown());return true;}
        return super.keyPressed(event);
    }
    @Override public void onClose(){
        if(!flush()){return;}
        Session.commit();minecraft.gui.setScreen(parent);
    }
    @Override public boolean isPauseScreen(){return false;}
}
