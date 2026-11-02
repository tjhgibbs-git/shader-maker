package io.github.tjhgibbs.shadermaker.client;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.nio.file.*;
import java.util.*;
final class LibraryScreen extends Screen {
    private final Screen parent;
    private List<Path> files=List.of();
    private int page;
    LibraryScreen(Screen parent){super(Component.literal("Saved projects"));this.parent=parent;}
    private void button(String label,int x,int y,int w,Runnable action){addRenderableWidget(Button.builder(Component.literal(label),b -> action.run()).bounds(x,y,w,20).build());}
    @Override protected void init(){
        try {
            Files.createDirectories(Session.projects());
            try(var paths=Files.list(Session.projects())){files=paths.filter(p -> Files.isRegularFile(p)&&p.toString().endsWith(".json")).sorted().toList();}
        } catch(Exception e){Session.message="Could not list projects: "+e.getMessage();}
        int rows=Math.max(1,(height-105)/24),pages=Math.max(1,(files.size()+rows-1)/rows);page=Math.min(page,pages-1);
        for(int i=page*rows;i<Math.min(files.size(),(page+1)*rows);i++){
            Path file=files.get(i);String label=font.plainSubstrByWidth(file.getFileName().toString(),width-55);
            button(label,12,54+(i-page*rows)*24,width-24,() -> {Session.load(file);minecraft.gui.setScreen(parent);});
        }
        button("<",12,height-30,30,() -> {page=(page+pages-1)%pages;rebuildWidgets();});
        button((page+1)+" / "+pages+"  >",46,height-30,70,() -> {page=(page+1)%pages;rebuildWidgets();});
        button("Done",width-72,height-30,60,this::onClose);
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float d){g.fill(0,0,width,height,0xFF10191E);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float d){super.extractRenderState(g,x,y,d);g.text(font,"SAVED PROJECTS",12,12,0xFF77C9BC);g.text(font,"Import JSON into config/shader-maker/projects",12,30,0xFF9DB3BD);if(files.isEmpty())g.text(font,"No saved projects yet. Save a look in the editor.",12,60,0xFFD9C993);}
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
