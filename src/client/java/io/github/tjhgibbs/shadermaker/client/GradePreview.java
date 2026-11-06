package io.github.tjhgibbs.shadermaker.client;

import io.github.tjhgibbs.shadermaker.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Shared reference rendering: final, original, or selected grade in isolation. */
final class GradePreview {
    private GradePreview() {}
    private static Project seenProject;
    private static SampleImage seenImage;
    private static Atmosphere.Context seenContext;
    private static int seenView=-1, seenLayer;
    private static final int[] pixels=new int[SampleImage.WIDTH*SampleImage.HEIGHT];
    static Atmosphere.Context context(){return new Atmosphere.Context(Session.sampleTime,Session.sampleY,0,Session.sampleBiome,0,Session.sampleRain,Session.sampleThunder,Session.sampleFlash,Session.signalSamples,Session.sampleX,Session.sampleZ,Session.previewCommands);}
    static void draw(GuiGraphicsExtractor g,int x,int y,int w,int h,int view) {
        var p=Session.project;
        var context=context();
        if(seenProject!=p||seenImage!=SampleImage.current||seenView!=view||seenLayer!=Session.selectedLayer||!context.equals(seenContext)){
            render(p,context,view);
            seenProject=p;seenImage=SampleImage.current;seenView=view;seenLayer=Session.selectedLayer;seenContext=context;
        }
        int cols=SampleImage.WIDTH,rows=SampleImage.HEIGHT;
        for(int j=0;j<rows;j++)for(int i=0;i<cols;i++)
            g.fill(x+i*w/cols,y+j*h/rows,x+(i+1)*w/cols,y+(j+1)*h/rows,pixels[j*cols+i]);
        g.outline(x,y,w,h,0xFF4D696D);
    }
    private static void render(Project p,Atmosphere.Context context,int view){
        var solo=view==2?Session.grade():null;
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
            if(view==0) color=p.evaluate(color,u*2-1,v*2-1,context);
            else if(view==2) color=solo.grade().apply(color,u*2-1,v*2-1,solo.get("opacity"),solo.curves());
            int rgb=0xFF000000|((int)Math.round(color.r()*255)<<16)|((int)Math.round(color.g()*255)<<8)|(int)Math.round(color.b()*255);
            pixels[j*cols+i]=rgb;
        }
    }
}
