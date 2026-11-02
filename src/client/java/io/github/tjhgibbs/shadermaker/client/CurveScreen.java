package io.github.tjhgibbs.shadermaker.client;

import io.github.tjhgibbs.shadermaker.*;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.*;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Direct curve editing: pointer, precise 0–255 fields, and keyboard nudges. */
final class CurveScreen extends Screen {
    private final Screen parent;
    private int channel,selected,gx=12,gy=65,size;
    private boolean dragging;
    private EditBox input,output;
    private String error="Left or right click to add · drag to move · arrows to nudge";
    private static final List<String> CHANNELS=List.of("Master RGB","Red","Green","Blue");
    CurveScreen(Screen parent) {super(Component.literal("Curves"));this.parent=parent;}
    private Curves.Curve curve() {return List.of(Session.grade().curves().master(),Session.grade().curves().red(),Session.grade().curves().green(),Session.grade().curves().blue()).get(channel);}
    private Button button(String text,int x,int y,int w,Runnable action) {return addRenderableWidget(Button.builder(Component.literal(text),b -> action.run()).bounds(x,y,w,20).build());}
    @Override protected void init() {
        if(width<340||height<220){button("Done",width/2-40,height-26,80,this::onClose);return;}
        size=Math.max(40,Math.min(height-114,width-182));
        selected=Math.min(selected,curve().points().size()-1);
        addRenderableWidget(CycleButton.<String>builder(Component::literal,CHANNELS.get(channel)).withValues(CHANNELS)
            .create(12,35,size,20,Component.literal("Channel"),(b,value) -> {channel=CHANNELS.indexOf(value);selected=0;rebuildWidgets();}));
        int x=gx+size+16,w=width-x-12;
        input=addRenderableWidget(new EditBox(font,x,80,w,20,Component.literal("Input 0–255")));
        output=addRenderableWidget(new EditBox(font,x,117,w,20,Component.literal("Output 0–255")));
        input.setMaxLength(6);output.setMaxLength(6);syncFields();
        button("Set point",x,143,w,() -> {
            try {
                double a=Double.parseDouble(input.getValue())/255,b=Double.parseDouble(output.getValue())/255;
                List<Curves.Point> points=new ArrayList<>(curve().points());
                if ((selected==0&&a!=0)||(selected==points.size()-1&&a!=1)) throw new IllegalArgumentException("Endpoint input is anchored");
                points.set(selected,new Curves.Point(a,b));
                Session.editGrade(Session.grade().withCurve(channel,new Curves.Curve(points)));Session.commit();error="Point updated.";
            } catch(Exception e) {error="Invalid point: check range and point order.";}
        });
        button("Delete point",x,167,w,() -> {removePoint();syncFields();});
        button("Reset channel",12,height-30,110,() -> {Session.editGrade(Session.grade().withCurve(channel,Curves.linear()));selected=0;Session.commit();syncFields();});
        button("Done",width-72,height-30,60,this::onClose);
    }
    private void syncFields() {
        if(input==null)return;var p=curve().points().get(selected);
        input.setValue(String.format(Locale.ROOT,"%.1f",p.input()*255));output.setValue(String.format(Locale.ROOT,"%.1f",p.output()*255));
    }
    private boolean inside(double x,double y) {return x>=gx&&x<=gx+size&&y>=gy&&y<=gy+size;}
    @Override public boolean mouseClicked(MouseButtonEvent e,boolean doubleClick) {
        if((e.button()!=InputConstants.MOUSE_BUTTON_LEFT&&e.button()!=InputConstants.MOUSE_BUTTON_RIGHT)||!inside(e.x(),e.y()))return super.mouseClicked(e,doubleClick);
        setFocused(null);error="Left or right click to add · drag to move · arrows to nudge";
        double x=(e.x()-gx)/size,y=1-(e.y()-gy)/size;
        List<Curves.Point> points=new ArrayList<>(curve().points());
        int nearest=-1;double distance=9;
        for(int i=0;i<points.size();i++) {var p=points.get(i);double d=Math.hypot((p.input()-x)*size,(p.output()-y)*size);if(d<distance){distance=d;nearest=i;}}
        if(nearest>=0)selected=nearest;
        else {
            if(points.size()>=16){error="Maximum 16 points per channel.";return true;}
            int i=0;while(i<points.size()&&points.get(i).input()<x)i++;
            if(i==0||i==points.size()||x-points.get(i-1).input()<1.0/255||points.get(i).input()-x<1.0/255){error="Points need at least one input step between them.";return true;}
            points.add(i,new Curves.Point(x,y));selected=i;
            Session.editGrade(Session.grade().withCurve(channel,new Curves.Curve(points)));
        }
        dragging=true;syncFields();return true;
    }
    private void move(double x,double y) {
        var points=new ArrayList<>(curve().points());
        x=selected==0?0:selected==points.size()-1?1:Math.max(points.get(selected-1).input()+1.0/255,Math.min(points.get(selected+1).input()-1.0/255,x));
        points.set(selected,new Curves.Point(x,Math.max(0,Math.min(1,y))));
        Session.editGrade(Session.grade().withCurve(channel,new Curves.Curve(points)));syncFields();
    }
    @Override public boolean mouseDragged(MouseButtonEvent e,double dx,double dy) {if(!dragging)return super.mouseDragged(e,dx,dy);move((e.x()-gx)/size,1-(e.y()-gy)/size);return true;}
    @Override public boolean mouseReleased(MouseButtonEvent e) {if(dragging){dragging=false;Session.commit();return true;}return super.mouseReleased(e);}
    private void removePoint() {
        var points=new ArrayList<>(curve().points());
        if(selected==0||selected==points.size()-1){error="Endpoints cannot be deleted.";return;}
        points.remove(selected);selected=Math.min(selected,points.size()-1);Session.editGrade(Session.grade().withCurve(channel,new Curves.Curve(points)));Session.commit();
    }
    @Override public boolean keyPressed(KeyEvent e) {
        if(input==null||output==null||input.isFocused()||output.isFocused())return super.keyPressed(e);
        if(e.key()==InputConstants.KEY_DELETE||e.key()==InputConstants.KEY_BACKSPACE){removePoint();syncFields();return true;}
        if(e.isLeft()||e.isRight()||e.isUp()||e.isDown()) {
            var p=curve().points().get(selected);double step=e.hasShiftDown()?10.0/255:1.0/255;
            move(p.input()+(e.isRight()?step:e.isLeft()?-step:0),p.output()+(e.isUp()?step:e.isDown()?-step:0));Session.commit();return true;
        }
        return super.keyPressed(e);
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float d) {g.fill(0,0,width,height,0xFF10191E);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float d) {
        super.extractRenderState(g,mx,my,d);g.text(font,"CURVES / "+CHANNELS.get(channel).toUpperCase(Locale.ROOT),12,12,0xFF77C9BC);
        if(width<340||height<220){g.text(font,"Use a smaller GUI scale",12,40,0xFFF0EEE4);return;}
        int x=gx+size+16;g.text(font,"Input · 0–255",x,67,0xFF9DB3BD);g.text(font,"Output · 0–255",x,104,0xFF9DB3BD);
        g.fill(gx,gy,gx+size,gy+size,0xFF17272E);
        for(int i=0;i<=4;i++){int v=i*size/4;g.fill(gx+v,gy,gx+v+1,gy+size,0xFF294149);g.fill(gx,gy+v,gx+size,gy+v+1,0xFF294149);}
        for(int i=0;i<=size;i++) {int y=gy+size-(int)Math.round(curve().sample(i/(double)size)*size);g.fill(gx+i,y,gx+i+2,y+2,0xFF77C9BC);}
        for(int i=0;i<curve().points().size();i++){var p=curve().points().get(i);int px=gx+(int)(p.input()*size),py=gy+size-(int)(p.output()*size);g.fill(px-2,py-2,px+3,py+3,i==selected?0xFFFFD28B:0xFFF0EEE4);}
        g.text(font,font.plainSubstrByWidth(error,width-24),12,height-43,0xFFD9C993);
    }
    @Override public void onClose(){Session.commit();minecraft.gui.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
