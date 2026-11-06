package io.github.tjhgibbs.shadermaker.client;

import io.github.tjhgibbs.shadermaker.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Deterministic authoring specimen, never a substitute for the exported resources. */
final class FogPreview {
    private FogPreview(){}
    private static Project seen;
    private static double seenY;
    private static int seenMode;
    private static final int W=64,H=36;
    private static final int[] pixels=new int[W*H];
    static void draw(GuiGraphicsExtractor g,int x,int y,int w,int h,double cameraY,int mode){
        var p=Session.project;
        if(seen!=p||seenY!=cameraY||seenMode!=mode){render(p,cameraY,mode);seen=p;seenY=cameraY;seenMode=mode;}
        for(int j=0;j<H;j++)for(int i=0;i<W;i++)g.fill(x+i*w/W,y+j*h/H,x+(i+1)*w/W,y+(j+1)*h/H,pixels[j*W+i]);
        g.outline(x,y,w,h,0xFF4D696D);
    }
    static void graphs(GuiGraphicsExtractor g,int x,int y,int w,int h,double cameraY){
        int half=w/2;g.fill(x,y,x+w,y+h,0xFF17272D);
        // Left: transmission along the specimen's forward ray, 0..256 blocks.
        for(int i=0;i<half;i++){
            double t=Fog.transmittance(Session.project.fog(),0,cameraY,-32,0,0,i*256d/Math.max(1,half-1));
            int py=y+h-3-(int)Math.round(t*(h-6));g.fill(x+i,py,x+i+1,py+2,0xFF77C9BC);
        }
        // Right: a vertical cross section through the specimen at world X=0, Z=0.
        // Each strip measures a horizontal 32-block ray at that altitude, not an endpoint tint.
        for(int j=0;j<h;j++){
            double worldY=100-j*60d/Math.max(1,h-1);
            double opacity=1-Fog.transmittance(Session.project.fog(),0,worldY,-16,0,0,32);
            int shade=(int)Math.round(opacity*160);
            g.fill(x+half,y+j,x+w,y+j+1,0xFF000000|((20+shade)<<16)|((35+shade)<<8)|(40+shade));
        }
        int cy=y+(int)Math.round((100-cameraY)/60*(h-1));
        if(cy>=y&&cy<y+h)g.fill(x+half,cy,x+w,cy+1,0xFFFFCB83);
        g.outline(x,y,w,h,0xFF4D696D);
    }
    private static double hitBox(double cx,double cy,double cz,double dx,double dy,double dz,double x,double z,double half,double top){
        double a=0,b=1000;double[] o={cx-x,cy-60,cz-z},r={dx,dy,dz},lo={-half,0,-half},hi={half,top-60,half};
        for(int i=0;i<3;i++){if(Math.abs(r[i])<1e-10){if(o[i]<lo[i]||o[i]>hi[i])return 1000;}else{double l=(lo[i]-o[i])/r[i],h=(hi[i]-o[i])/r[i];a=Math.max(a,Math.min(l,h));b=Math.min(b,Math.max(l,h));if(b<a)return 1000;}}
        return a;
    }
    private static void render(Project p,double cy,int mode){
        double cx=0,cz=-32;
        for(int j=0;j<H;j++)for(int i=0;i<W;i++){
            double dx=(i+.5-W/2d)/W*1.8,dy=(H/2d-j-.5)/W*1.8-.08,dz=1,n=Math.sqrt(dx*dx+dy*dy+1);dx/=n;dy/=n;dz/=n;
            double distance=dy<0?(60-cy)/dy:1000;if(distance<0)distance=1000;boolean pillar=false;
            for(int k=0;k<5;k++){double z=new double[]{16,32,64,128,256}[k]-32;double hit=hitBox(cx,cy,cz,dx,dy,dz,(k-2)*9,z,2,76);if(hit<distance){distance=hit;pillar=true;}}
            double r=.45,g=.65,b=.83;
            if(distance<700){double px=cx+dx*distance,pz=cz+dz*distance;
                double checker=((int)Math.floor(px/8)+(int)Math.floor(pz/8))%2==0?.38:.28;
                r=pillar?.62:checker;g=pillar?.53:checker+.08;b=pillar?.39:checker+.02;
                double t=mode==1?1:Fog.transmittance(p.fog(),cx,cy,cz,dx*distance,dy*distance,dz*distance);
                if(mode==2)r=g=b=1-t;
                else {r=r*t+.68*(1-t);g=g*t+.76*(1-t);b=b*t+.82*(1-t);var c=p.evaluate(new Atmosphere.Color(r,g,b,1),i/(double)W*2-1,j/(double)H*2-1,GradePreview.context());r=c.r();g=c.g();b=c.b();}
            }else if(mode==2)r=g=b=0;
            pixels[j*W+i]=0xFF000000|((int)Math.round(r*255)<<16)|((int)Math.round(g*255)<<8)|(int)Math.round(b*255);
        }
    }
}
