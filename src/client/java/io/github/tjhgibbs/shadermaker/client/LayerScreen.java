package io.github.tjhgibbs.shadermaker.client;

import io.github.tjhgibbs.shadermaker.*;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import java.util.*;

/**
 * Home screen: the whole pack at a glance. Every layer and fog row carries its own actions
 * (show/hide, rename, edit grade, conditions, copy, reorder, delete), so nothing needs a
 * select-then-find-the-button round trip. Project, preview and game actions live in fixed places.
 */
final class LayerScreen extends Screen {
    static final int BG=0xFF10191E, PANEL=0xFF15252C, ROW=0xFF1B2F36, ROW_SELECTED=0xFF2C4C50, ACCENT=0xFF77C9BC,
        TEXT=0xFFF0EEE4, DIM=0xFF9DB3BD, WARN=0xFFFFCB83, STATUS=0xFFD9C993;
    private static final int ROW_H=26, HEADER_H=18;
    private final Screen parent;
    private int previewView, input, scroll;
    private String message="";
    /** Row being renamed: layer index, or -2-fogIndex for fog; Integer.MIN_VALUE when none. */
    private int renaming=Integer.MIN_VALUE;
    private EditBox renameBox;
    private int listX, listW, listTop, listBottom, previewX, previewW;
    private boolean showPreview;
    private int contentH;
    private final List<Entry> entries=new ArrayList<>();
    private record Entry(int kind,int index,int y){} // kind 0 layer header, 1 layer, 2 base, 3 fog header, 4 fog, 5 fog empty
    private static String chosenFog=Fog.PRESETS.getFirst();
    private long lastClick; private int lastClickRow=Integer.MIN_VALUE;

    LayerScreen(Screen parent){super(Component.literal("Shader Maker"));this.parent=parent;}

    private Button button(String label,int x,int y,int w,Runnable action){
        return addRenderableWidget(Button.builder(Component.literal(label),b->{commitRename();action.run();}).bounds(x,y,w,20).build());
    }
    private Button button(String label,int x,int y,int w,String tip,Runnable action){
        var b=button(label,x,y,w,action);b.setTooltip(Tooltip.create(Component.literal(tip)));return b;
    }
    private void replace(List<Project.Layer> layers){Session.edit(Session.project.withLayers(layers));Session.commit();rebuildWidgets();}
    private void replaceFog(List<Fog> fog){Session.edit(Session.project.withFog(fog));Session.commit();rebuildWidgets();}
    static Project.Layer copy(Project.Layer l,String name,boolean enabled){return l.withName(name).withEnabled(enabled);}

    // ---- actions -------------------------------------------------------------------------
    private void addLayer(){
        var ls=new ArrayList<>(Session.project.layers());
        ls.add(new Project.Layer(Session.freeLayerName(),Project.neutral(),true,1,Set.of(),null,null));
        Session.selectedLayer=ls.size()-1;replace(ls);
        message="Added "+ls.getLast().name()+". Edit its grade, or set When… to make it conditional.";
    }
    private void duplicate(int i){
        var ls=new ArrayList<>(Session.project.layers());var l=ls.get(i);
        ls.add(i+1,copy(l,Session.freeCopyName(l.name()),l.enabled()));Session.selectedLayer=i+1;replace(ls);
    }
    private void delete(int i){
        var ls=new ArrayList<>(Session.project.layers());var removed=ls.remove(i);
        Session.commit();
        Session.selectedLayer=Math.min(i,ls.size()-1);
        replace(ls);message="Deleted \""+removed.name()+"\". Undo (Ctrl+Z) brings it back.";
    }
    private void move(int i,int delta){
        var ls=new ArrayList<>(Session.project.layers());int to=i+delta;if(to<0||to>=ls.size())return;
        Collections.swap(ls,i,to);Session.selectedLayer=to;replace(ls);
    }
    private void toggle(int i){
        var l=Session.project.layers().get(i);
        if(!l.enabled()&&!PackCompiler.layerIssues(l).isEmpty()){minecraft.setScreenAndShow(new ExportIssuesScreen(this,i));return;}
        var ls=new ArrayList<>(Session.project.layers());ls.set(i,copy(l,l.name(),!l.enabled()));replace(ls);
    }
    private void editGrade(int i){Session.selectedLayer=i;Session.commit();minecraft.setScreenAndShow(new EditorScreen(this));}
    private void editConditions(int i){Session.selectedLayer=i;Session.commit();minecraft.setScreenAndShow(new LayerConditionsScreen(this));}
    private void openFog(int i,String preset){Session.commit();minecraft.setScreenAndShow(new FogScreen(this,i,preset));}
    private void toggleFog(int i){var list=new ArrayList<>(Session.project.fog());list.set(i,list.get(i).enabled(!list.get(i).enabled()));replaceFog(list);}
    private void deleteFog(int i){var list=new ArrayList<>(Session.project.fog());var f=list.remove(i);Session.commit();replaceFog(list);message="Deleted fog \""+f.name()+"\". Undo (Ctrl+Z) brings it back.";}
    private void duplicateFog(int i){var list=new ArrayList<>(Session.project.fog());var f=list.get(i);String n=f.name();list.add(i+1,f.named(n.substring(0,Math.min(n.length(),59))+" copy"));replaceFog(list);}

    private void startRename(int row){renaming=row;rebuildWidgets();if(renameBox!=null){setFocused(renameBox);renameBox.setFocused(true);renameBox.moveCursorToEnd(false);renameBox.setHighlightPos(0);}}
    private void cancelRename(){renaming=Integer.MIN_VALUE;renameBox=null;rebuildWidgets();}
    /** Commits a pending rename; returns false (and keeps the box open) if the name is invalid. */
    private boolean commitRename(){
        if(renaming==Integer.MIN_VALUE||renameBox==null)return true;
        String value=renameBox.getValue().strip();int row=renaming;
        try{
            if(row>=0){
                var ls=new ArrayList<>(Session.project.layers());var l=ls.get(row);
                if(!value.equals(l.name())){
                    for(int i=0;i<ls.size();i++)if(i!=row&&ls.get(i).name().equals(value))throw new IllegalArgumentException("Another layer is already called \""+value+"\".");
                    ls.set(row,copy(l,value,l.enabled()));Session.edit(Session.project.withLayers(ls));Session.commit();
                }
            }else{
                var list=new ArrayList<>(Session.project.fog());int i=-2-row;
                if(!value.equals(list.get(i).name())){list.set(i,list.get(i).named(value));Session.edit(Session.project.withFog(list));Session.commit();}
            }
        }catch(IllegalArgumentException e){message=e.getMessage()!=null&&e.getMessage().startsWith("Another")?e.getMessage():"Names need 1–64 printable characters.";return false;}
        renaming=Integer.MIN_VALUE;renameBox=null;message="";return true;
    }
    private void confirmNewPack(){
        minecraft.setScreenAndShow(new ConfirmScreen(confirmed->{
            if(confirmed){
                Session.commit();Session.edit(Project.neutral());Session.selectedLayer=-1;Session.name="My look";
                Session.activation=PackCompiler.Activation.ALWAYS;Session.commit();
                Session.message="Started a new pack. Undo restores the previous pack settings.";scroll=0;message="";
            }
            minecraft.setScreenAndShow(this);
        },Component.literal("Start a new pack?"),
            Component.literal("Replace the current pack with a Neutral base grade and remove all layers and fog? Saved projects and exported files are kept. You can undo the pack settings."),
            Component.literal("New pack"),Component.literal("Cancel")));
    }
    private void history(boolean redo){commitRename();Session.commit();Session.undo(redo);rebuildWidgets();}

    // ---- layout ----------------------------------------------------------------------------
    @Override protected void init(){
        renameBox=null;entries.clear();
        if(width<340||height<220){button("Close",width/2-40,height-26,80,this::onClose);return;}
        if(Session.selectedLayer>=Session.project.layers().size())Session.selectedLayer=-1;
        showPreview=width>=560;
        listX=10;listW=showPreview?Math.max(300,(width-30)*56/100):width-20;
        previewX=listX+listW+10;previewW=width-previewX-10;
        listTop=36;listBottom=height-52;

        // Header: project name, history, project files.
        int rx=width-10;
        rx-=46;button("Save",rx,8,46,"Save this project to config/shader-maker/projects.",Session::save);
        rx-=50;button("Open…",rx,8,48,"Open a saved project.",()->{Session.commit();minecraft.setScreenAndShow(new LibraryScreen(this));});
        rx-=46;button("New…",rx,8,44,"Start a new pack with a Neutral base grade, no layers and no fog.",this::confirmNewPack);
        rx-=50;var redo=button("Redo",rx-4,8,44,"Redo (Ctrl+Y)",()->history(true));redo.active=Session.history.canRedo();
        rx-=48;var undo=button("Undo",rx-4,8,44,"Undo (Ctrl+Z)",()->history(false));undo.active=Session.history.canUndo();
        var name=addRenderableWidget(new EditBox(font,96,9,Math.max(50,Math.min(200,rx-4-96-8)),18,Component.literal("Project name")));
        name.setMaxLength(48);name.setValue(Session.name);name.setResponder(v->Session.name=v);
        name.setTooltip(Tooltip.create(Component.literal("Project name: used for Save and Export file names.")));

        buildList();

        if(showPreview){
            int third=(previewW-8)/3;String[] views={"Final","Original","Solo"};
            for(int i=0;i<3;i++){final int v=i;var b=button(views[i],previewX+i*(third+4),listTop,i==2?previewW-2*(third+4):third,
                new String[]{"Everything: base grade plus all active layers.","The scene with no grading.","Only the selected grade, ignoring its conditions."}[i],()->{previewView=v;rebuildWidgets();});b.active=previewView!=i;}
            int imgBottom=previewImageBottom();
            int half=(previewW-4)/2;
            button("Image…",previewX,imgBottom+4,half,"Choose the preview picture: bundled capture, synthetic specimen or your own screenshot.",()->{Session.commit();minecraft.setScreenAndShow(new SampleScreen(this));});
            button(new String[]{"Simulate…","Camera Y","Daylight","Sky colour","Rain","Thunder","Flash","Render inputs","Camera X, Z","Commands"}[input],previewX+half+4,imgBottom+4,previewW-half-4,
                "Cycle the simulated condition inputs used by the preview. They do not change the export.",()->{input=(input+1)%10;rebuildWidgets();});
            if(input==7)button("Set render inputs…",previewX,imgBottom+28,previewW,()->minecraft.setScreenAndShow(new RenderSignalInputsScreen(this)));
            else if(input==9)button(Session.previewCommands?"Preview: command layers ON":"Preview: command layers OFF",previewX,imgBottom+28,previewW,
                "Show command-switched layers as if /posteffect had turned them on.",()->{Session.previewCommands=!Session.previewCommands;rebuildWidgets();});
            else if(input>0)addPreviewInput(imgBottom+28);
        }

        // Footer: game and export actions.
        int y=height-26;
        boolean narrow=width<520;int aw=narrow?84:104,dw=narrow?48:56,mw=narrow?64:96,ew=narrow?78:96,mx=14+aw+dw+(narrow?4:10),ex=mx+mw+4;
        var apply=button(narrow?"Apply":"Apply to game",10,y,aw,"Build the pack and load it into this world so you can look around. Reapply after edits.",()->{Session.commit();Session.apply(true);});
        apply.active=!Session.busy&&PackCompiler.issues(Session.project).isEmpty();
        if(!PackCompiler.issues(Session.project).isEmpty())apply.setTooltip(Tooltip.create(Component.literal(String.join(" ",PackCompiler.issues(Session.project)))));
        button("Disable",14+aw,y,dw,"Remove the preview pack from the game.",()->Session.apply(false)).active=!Session.busy&&Session.active();
        boolean fogActive=FogShaders.active(Session.project);
        var mode=button(Session.activation==PackCompiler.Activation.ALWAYS?(narrow?"Auto":"Mode: Auto"):(narrow?"Command":"Mode: Command"),mx,y,mw,
            "Auto packs are always on. Command packs switch on with /posteffect (grading only).",
            ()->{Session.activation=Session.activation==PackCompiler.Activation.ALWAYS?PackCompiler.Activation.COMMAND:PackCompiler.Activation.ALWAYS;rebuildWidgets();});
        if(fogActive&&Session.activation==PackCompiler.Activation.ALWAYS){mode.active=false;mode.setTooltip(Tooltip.create(Component.literal("Command packs cannot include terrain fog: /posteffect cannot switch a core shader. Disable or delete the fog to use it.")));}
        boolean blocked=!PackCompiler.issues(Session.project).isEmpty();
        button(blocked?(narrow?"Resolve…":"Resolve export…"):(narrow?"Export":"Export ZIP"),ex,y,ew,blocked?"Some layers use conditions that cannot export. Review them.":"Write a standalone resource pack to .minecraft/shader-maker/exports.",()->{
            if(PackCompiler.issues(Session.project,Session.activation).isEmpty())Session.export();
            else if(PackCompiler.issues(Session.project).isEmpty())Session.message="Command pack cannot include terrain fog. Switch to Auto, or disable the fog.";
            else minecraft.setScreenAndShow(new ExportIssuesScreen(this,-1));
        }).active=!Session.busy;
        if(!narrow)button("Folder",ex+ew+4,y,52,"Copy the project folder path to the clipboard.",()->{try{java.nio.file.Files.createDirectories(Session.ROOT);minecraft.keyboardHandler.setClipboard(Session.ROOT.toString());Session.message="Copied project folder path";}catch(Exception e){Session.message="Could not copy path: "+e.getMessage();}});
        button("Close",width-70,y,60,"Close Shader Maker (Esc).",this::onClose);
    }

    private int previewImageBottom(){
        int top=listTop+24,maxH=Math.max(40,listBottom-top-76);
        int h=Math.min(maxH,previewW*9/16);return top+h;
    }

    private void buildList(){
        var layers=Session.project.layers();var fog=Session.project.fog();
        var rows=new ArrayList<int[]>(); // {kind,index}
        rows.add(new int[]{0,0});
        for(int i=layers.size()-1;i>=0;i--)rows.add(new int[]{1,i});
        rows.add(new int[]{2,-1});
        rows.add(new int[]{3,0});
        if(fog.isEmpty())rows.add(new int[]{5,0});
        for(int i=0;i<fog.size();i++)rows.add(new int[]{4,i});
        int total=0;for(var r:rows)total+=height(r[0]);contentH=total;
        int visible=listBottom-listTop;
        scroll=Math.max(0,Math.min(scroll,Math.max(0,total-visible)));
        int y=listTop-scroll;
        for(var r:rows){
            int h=height(r[0]);
            if(y>=listTop-1&&y+h<=listBottom+1){entries.add(new Entry(r[0],r[1],y));addRowWidgets(r[0],r[1],y);}
            y+=h;
        }
    }
    private static int height(int kind){return kind==0||kind==3?HEADER_H+4:kind==5?20:ROW_H+2;}

    private void addRowWidgets(int kind,int index,int y){
        int right=listX+listW;
        switch(kind){
            case 0->button("+ Layer",right-64,y,64,"Add a grade layer on top. Layers blend over everything below them.",this::addLayer).setHeight(16);
            case 3->{
                int w=Math.min(110,listW/3);
                var presets=addRenderableWidget(CycleButton.<String>builder(Component::literal,chosenFog).withValues(Fog.PRESETS)
                    .withTooltip(p->Tooltip.create(Component.literal(Fog.presetHelp(p)+"\nClick to cycle presets, then Add.")))
                    .create(right-w-56,y,w,16,Component.literal("Preset"),(b,v)->chosenFog=v));
                button("Add",right-52,y,52,"Add this fog preset around where you are standing.",()->openFog(-1,presets.getValue())).setHeight(16);
            }
            case 1->{
                var l=Session.project.layers().get(index);int ry=y+3;
                button(l.enabled()?"On":"Off",listX+3,ry,26,"Show / hide this layer in the preview and the pack.",()->toggle(index));
                int x=right-3;
                x-=18;button("✕",x,ry,18,"Delete layer (Undo restores it)",()->delete(index));
                x-=16;var down=button("▼",x,ry,15,"Move down: blend earlier",()->move(index,-1));down.active=index>0;
                x-=16;var up=button("▲",x,ry,15,"Move up: blend later, over more layers",()->move(index,1));up.active=index<Session.project.layers().size()-1;
                x-=20;button("⧉",x,ry,18,"Duplicate layer",()->duplicate(index));
                x-=20;button("✎",x,ry,18,"Rename (or double-click the name)",()->startRename(index));
                x-=48;button(conditionCount(l)>0?"When ●":"When…",x,ry,46,"Conditions: when and where this layer applies, and how strongly.\n"+LayerScreen.conditions(l),()->editConditions(index));
                x-=48;button("Grade",x,ry,46,"Edit this layer's colour grade (Enter)",()->editGrade(index));
                if(renaming==index)addRename(l.name(),listX+33,ry,x-listX-37);
            }
            case 2->button("Grade",right-3-46,y+3,46,"Edit the base grade (Enter)",()->editGrade(-1));
            case 4->{
                var f=Session.project.fog().get(index);int ry=y+3;
                button(f.enabled()?"On":"Off",listX+3,ry,26,"Show / hide this fog.",()->toggleFog(index));
                int x=right-3;
                x-=18;button("✕",x,ry,18,"Delete fog (Undo restores it)",()->deleteFog(index));
                x-=20;button("⧉",x,ry,18,"Duplicate fog",()->duplicateFog(index));
                x-=20;button("✎",x,ry,18,"Rename (or double-click the name)",()->startRename(-2-index));
                x-=48;button("Edit",x,ry,46,"Edit shape, density and placement",()->openFog(index,null));
                if(renaming==-2-index)addRename(f.name(),listX+33,ry,x-listX-37);
            }
            default->{}
        }
    }
    private static List<String> withFirst(String first,List<String> rest){var l=new ArrayList<String>();l.add(first);l.addAll(rest);return l;}
    private void addRename(String value,int x,int y,int w){
        renameBox=addRenderableWidget(new EditBox(font,x,y,Math.max(40,w),20,Component.literal("Name")));
        renameBox.setMaxLength(64);renameBox.setValue(value);
    }
    private static int conditionCount(Project.Layer l){
        int n=0;if(l.altitude()!=null)n++;if(l.options().area()!=null)n++;if(l.options().command())n++;if(l.visualBiomeGroup()!=null)n++;if(l.contextControls().active())n++;if(l.time()!=null)n++;if(!l.biomes().isEmpty())n++;if(l.weather()!=null)n++;if(l.lightning()!=null)n++;if(l.strength()<1)n++;return n;
    }

    private void addPreviewInput(int y){
        if(input==8){
            var f=addRenderableWidget(new EditBox(font,previewX,y,previewW,20,Component.literal("Preview camera X, Z")));
            f.setMaxLength(48);f.setValue(num(Session.sampleX)+", "+num(Session.sampleZ));
            f.setResponder(v->{try{var parts=v.split(",");if(parts.length!=2)throw new IllegalArgumentException();double x=Double.parseDouble(parts[0].strip()),z=Double.parseDouble(parts[1].strip());
                if(!Double.isFinite(x)||!Double.isFinite(z)||Math.abs(x)>3e7||Math.abs(z)>3e7)throw new IllegalArgumentException();Session.sampleX=x;Session.sampleZ=z;message="";}
                catch(RuntimeException e){message="Preview input: enter X, Z such as 120, -45.";}});
            return;
        }
        String value=switch(input){case 1->num(Session.sampleY);case 2->num(Session.sampleTime);case 3->Session.sampleBiome;case 4->num(Session.sampleRain);case 5->num(Session.sampleThunder);default->num(Session.sampleFlash);};
        var f=addRenderableWidget(new EditBox(font,previewX,y,previewW,20,Component.literal(new String[]{"","Preview camera Y","Preview daylight tick","Preview sky #RRGGBB or vanilla biome ID","Preview rain 0–1","Preview thunder 0–1","Preview flash 0–1"}[input])));
        f.setMaxLength(input==3?256:32);f.setValue(value);
        f.setResponder(v->{try{if(input==3){String id=v.strip();if(id.matches("#[0-9a-fA-F]{6}")){Session.sampleBiome=id;return;}if(!id.contains(":"))id="minecraft:"+id;if(!id.matches("[a-z0-9_.-]+:[a-z0-9/._-]+"))throw new IllegalArgumentException();Session.sampleBiome=id;}
            else {double n=Double.parseDouble(v);if(!Double.isFinite(n)||Math.abs(n)>Float.MAX_VALUE)throw new IllegalArgumentException();if(input==2&&(n<0||n>=24000)||input>=4&&(n<0||n>1))throw new IllegalArgumentException();switch(input){case 1->Session.sampleY=n;case 2->Session.sampleTime=n;case 4->Session.sampleRain=n;case 5->Session.sampleThunder=n;default->Session.sampleFlash=n;}}message="";
        }catch(IllegalArgumentException e){message="Preview input: "+(input==3?"enter #RRGGBB or a vanilla biome ID.":input==2?"use ticks 0–23999.":input>=4?"use 0–1.":"enter a finite height.");}});
    }

    // ---- text helpers ------------------------------------------------------------------------
    static String num(double v){return v==Math.rint(v)?Long.toString((long)v):String.format(Locale.ROOT,"%.1f",v);}
    static String conditions(Project.Layer l){
        var s=new ArrayList<String>();
        if(l.options().command())s.add("Command: "+CommandEffects.NAMESPACE+":"+l.commandId());
        if(l.altitude()!=null)s.add("Y "+num(l.altitude().min())+" to "+num(l.altitude().max()));
        var a=l.options().area();if(a!=null)s.add("X "+num(a.minX())+" to "+num(a.maxX())+", Z "+num(a.minZ())+" to "+num(a.maxZ()));
        if(l.visualBiomeGroup()!=null)s.add("Sky colour: "+VisualBiomes.get(l.visualBiomeGroup()).label());
        if(l.contextControls().active()){s.add(l.contextControls().blocksOnly()?"Blocks only":"Render context");l.contextControls().conditions().forEach(c->s.add(c.label()));}
        if(l.time()!=null)s.add("Time "+num(l.time().start())+"–"+num(l.time().end()));
        if(!l.biomes().isEmpty())s.add(String.join(" / ",new TreeSet<>(l.biomes())).replace("minecraft:",""));
        if(l.weather()!=null)s.add("Rain "+num(l.weather().rain().min())+"–"+num(l.weather().rain().max())+", thunder "+num(l.weather().thunder().min())+"–"+num(l.weather().thunder().max()));
        if(l.lightning()!=null)s.add("Flash "+num(l.lightning().min())+"–"+num(l.lightning().max()));
        return s.isEmpty()?"Always":String.join(" + ",s);
    }
    private String status(Project.Layer l){double weight=l.weight(GradePreview.context());return (!l.enabled()?"Hidden":weight==0?"Inactive in preview":Math.round(weight*100)+"% in preview")+(l.time()!=null||!l.biomes().isEmpty()||l.weather()!=null||l.lightning()!=null?" · "+(l.enabled()?"blocks export":"not exported"):"");}
    private void text(GuiGraphicsExtractor g,String s,int x,int y,int w,int color){g.text(font,font.width(s)<=w?s:font.plainSubstrByWidth(s,Math.max(0,w-6))+"…",x,y,color);}

    // ---- rendering ---------------------------------------------------------------------------
    @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float d){g.fill(0,0,width,height,BG);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float d){
        if(width>=340&&height>=220){
            g.fill(listX-4,listTop-4,listX+listW+4,listBottom+4,PANEL);
            g.enableScissor(listX-4,listTop-2,listX+listW+4,listBottom+2);
            var layers=Session.project.layers();
            for(var e:entries){
                int y=e.y(),right=listX+listW;
                switch(e.kind()){
                    case 0->{text(g,"GRADE LAYERS",listX+2,y+4,listW,ACCENT);text(g,"top blends last",listX+4+font.width("GRADE LAYERS  "),y+4,listW-160,DIM);}
                    case 3->{g.fill(listX,y-3,right,y-2,0xFF2A3E45);text(g,"FOG",listX+2,y+4,listW,ACCENT);text(g,"terrain only",listX+4+font.width("FOG  "),y+4,listW-260,DIM);}
                    case 5->text(g,"No fog yet. Choose a preset above and press Add.",listX+6,y+5,listW-12,DIM);
                    case 1,2,4->{
                        boolean selected=e.kind()==1&&Session.selectedLayer==e.index()||e.kind()==2&&Session.selectedLayer<0;
                        boolean hover=mx>=listX&&mx<right&&my>=y&&my<y+ROW_H;
                        g.fill(listX,y,right,y+ROW_H,selected?ROW_SELECTED:hover?0xFF22393F:ROW);
                        if(selected)g.fill(listX,y,listX+2,y+ROW_H,ACCENT);
                        int textX=e.kind()==2?listX+8:listX+34,textW=textRight(e.kind())-textX-4;
                        if(renaming==(e.kind()==4?-2-e.index():e.index())&&e.kind()!=2)break;
                        if(e.kind()==1){var l=layers.get(e.index());int c=l.enabled()?TEXT:0xFF7D8C91;
                            text(g,l.name(),textX,y+4,textW,c);text(g,conditions(l)+" · "+status(l),textX,y+15,textW,l.enabled()?DIM:0xFF5F6E73);}
                        else if(e.kind()==2){text(g,"Base grade",textX,y+4,textW,TEXT);text(g,"Always applied first, to the whole scene",textX,y+15,textW,DIM);}
                        else{var f=Session.project.fog().get(e.index());
                            text(g,f.name(),textX,y+4,textW,f.enabled()?TEXT:0xFF7D8C91);text(g,f.summary(),textX,y+15,textW,f.enabled()?DIM:0xFF5F6E73);}
                    }
                    default->{}
                }
            }
            g.disableScissor();
            boolean more=contentH-scroll>listBottom-listTop;
            if(scroll>0||more){String hint=(scroll>0?"▲ ":"")+"scroll"+(more?" ▼":"");int hw=font.width(hint)+6;
                g.fill(listX+listW-hw-70,listTop-4,listX+listW-68,listTop+9,PANEL);text(g,hint,listX+listW-hw-67,listTop,hw,ACCENT);}
            if(showPreview){
                g.fill(previewX-4,listTop-4,width-6,listBottom+4,0xFF192A30);
                int top=listTop+24,bottom=previewImageBottom();
                int ph=bottom-top,pw=Math.min(previewW,ph*16/9);
                GradePreview.draw(g,previewX+(previewW-pw)/2,top,pw,ph,previewView);
                int ty=bottom+(input>0?52:28);
                String sel=Session.selectedLayer<0?"Selected: Base grade":"Selected: "+layers.get(Session.selectedLayer).name();
                if(ty+10<listBottom)text(g,sel+(previewView==2?" (solo)":""),previewX,ty,previewW,ACCENT);
                if(ty+22<listBottom)text(g,(SampleImage.current==null?"Synthetic specimen":SampleImage.current.name)+(input>0?" · simulated inputs":"")+" · preview only",previewX,ty+12,previewW,DIM);
                if(ty+34<listBottom&&Session.active())text(g,"Preview pack is loaded in the game.",previewX,ty+24,previewW,0xFF8FD18F);
            }
        }
        super.extractRenderState(g,mx,my,d);
        text(g,"SHADER MAKER",10,14,84,ACCENT);
        if(width<340||height<220){text(g,"Use a smaller GUI scale",10,40,width-20,TEXT);return;}
        String issue=PackCompiler.issues(Session.project).stream().findFirst().orElse("");
        String status=!message.isEmpty()?message:!Session.visibleMessage().isEmpty()?Session.visibleMessage():!issue.isEmpty()?"Export blocked: "+issue:
            "Click a row to select · double-click a name to rename · Del deletes · Enter edits the grade";
        text(g,status,10,height-40,width-20,issue.isEmpty()||!message.isEmpty()?STATUS:WARN);
    }
    private int textRight(int kind){int right=listX+listW-3;return switch(kind){case 1->right-18-16-16-20-20-48-48;case 2->right-49;default->right-18-20-20-48;};}

    // ---- input -------------------------------------------------------------------------------
    @Override public boolean mouseClicked(MouseButtonEvent e,boolean doubleClick){
        if(super.mouseClicked(e,doubleClick))return true;
        if(e.button()!=InputConstants.MOUSE_BUTTON_LEFT)return false;
        for(var entry:entries){
            if(entry.kind()!=1&&entry.kind()!=2&&entry.kind()!=4)continue;
            if(e.x()<listX||e.x()>=listX+listW||e.y()<entry.y()||e.y()>=entry.y()+ROW_H)continue;
            if(!commitRename())return true;
            int row=entry.kind()==4?-2-entry.index():entry.index();
            long now=System.currentTimeMillis();boolean dbl=doubleClick||(row==lastClickRow&&now-lastClick<400);
            lastClick=now;lastClickRow=row;
            if(entry.kind()==4){if(dbl)startRename(row);return true;}
            Session.selectedLayer=entry.index();message="";
            if(dbl&&entry.kind()==1)startRename(row);else rebuildWidgets();
            return true;
        }
        if(!commitRename())return true;
        if(renaming!=Integer.MIN_VALUE)rebuildWidgets();
        return false;
    }
    @Override public boolean mouseScrolled(double x,double y,double sx,double sy){
        if(x>=listX&&x<listX+listW&&y>=listTop&&y<listBottom){commitRename();scroll-=(int)Math.signum(sy)*(ROW_H+2);rebuildWidgets();return true;}
        return super.mouseScrolled(x,y,sx,sy);
    }
    @Override public boolean keyPressed(KeyEvent event){
        int key=event.key();
        if(renameBox!=null){
            if(key==InputConstants.KEY_RETURN||key==InputConstants.KEY_NUMPADENTER){if(commitRename())rebuildWidgets();return true;}
            if(key==InputConstants.KEY_ESCAPE){cancelRename();return true;}
            return super.keyPressed(event);
        }
        boolean text=getFocused() instanceof EditBox;
        if(!text&&event.hasControlDown()&&!event.hasAltDown()&&(key==InputConstants.KEY_Z||key==InputConstants.KEY_Y)){history(key==InputConstants.KEY_Y||event.hasShiftDown());return true;}
        if(!text&&!event.hasControlDown()&&!event.hasAltDown()){
            int sel=Session.selectedLayer;
            if(key==InputConstants.KEY_DELETE&&sel>=0){delete(sel);return true;}
            if(key==InputConstants.KEY_F2&&sel>=0){startRename(sel);return true;}
            if(key==InputConstants.KEY_RETURN||key==InputConstants.KEY_NUMPADENTER){editGrade(sel);return true;}
            if(key==InputConstants.KEY_UP||key==InputConstants.KEY_DOWN){
                int n=Session.project.layers().size();
                // Display order: top layer first, base last (index -1).
                int display=sel<0?n:n-1-sel;display=Math.max(0,Math.min(n,display+(key==InputConstants.KEY_DOWN?1:-1)));
                Session.selectedLayer=display==n?-1:n-1-display;rebuildWidgets();return true;
            }
        }
        return super.keyPressed(event);
    }
    @Override public void resize(int w,int h){commitRename();super.resize(w,h);}
    @Override public void onClose(){if(!commitRename()){cancelRename();}Session.commit();minecraft.gui.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
