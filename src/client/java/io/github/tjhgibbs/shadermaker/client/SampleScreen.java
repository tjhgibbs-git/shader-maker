package io.github.tjhgibbs.shadermaker.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.nio.file.Path;

final class SampleScreen extends Screen {
    private final Screen parent;
    private String path="", status="PNG/JPEG path; use an ungraded screenshot for comparison.";
    SampleScreen(Screen parent) { super(Component.literal("Sample image")); this.parent=parent; }
    private void button(String text,int y,Runnable action) {
        addRenderableWidget(Button.builder(Component.literal(text),b->action.run()).bounds(10,y,Math.max(100,width-20),20).build());
    }
    @Override protected void init() {
        button("Bundled day capture",32,()->{try{SampleImage.bundled();onClose();}catch(Exception e){status=e.getMessage();}});
        button("Synthetic specimen",56,()->{SampleImage.current=null;onClose();});
        var field=addRenderableWidget(new EditBox(font,10,86,width-20,20,Component.literal("Screenshot path")));
        field.setMaxLength(4096);field.setValue(path);field.setResponder(v->path=v);
        button("Load screenshot",110,()->{try{SampleImage.load(Path.of(path.strip()));onClose();}catch(Exception e){status="Load failed: "+e.getMessage();}});
        button("Done",height-30,this::onClose);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float d) {
        super.extractRenderState(g,x,y,d);
        g.text(font,"SAMPLE IMAGE",10,10,0xFF77C9BC);
        g.text(font,font.plainSubstrByWidth(status,width-20),10,140,0xFFD9C993);
        g.text(font,"Sample context is simulated, including for screenshots.",10,154,0xFF9DB3BD);
    }
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
