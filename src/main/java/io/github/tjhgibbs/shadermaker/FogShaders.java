package io.github.tjhgibbs.shadermaker;

import java.util.*;

/** Pinned 26.3 terrain shaders; preserves native sampling, alpha, OIT and chunk fades. */
public final class FogShaders {
    private FogShaders() {}
    private static final String VERTEX = """
        #version 330
        #extension GL_ARB_separate_shader_objects : require
        
        #include <minecraft:fog.glsl>
        #include <minecraft:globals.glsl>
        #include <minecraft:projection.glsl>
        #include <minecraft:sample_lightmap.glsl>
        #include <minecraft:terrainglobals.glsl>
        #ifndef MULTIDRAW_TERRAIN
            #include <minecraft:chunksection.glsl>
        #endif
        
        layout(location = 0) in vec3 Position;
        layout(location = 1) in vec4 Color;
        layout(location = 2) in vec2 UV0;
        layout(location = 3) in ivec2 UV2;
        #ifdef MULTIDRAW_TERRAIN
        layout(location = 4) in ivec3 ChunkPosition;
        layout(location = 5) in float ChunkVisibility;
        #endif
        
        #ifndef OIT_ALPHA_ONLY
        uniform sampler2D Sampler2;
        #endif
        
        layout(location = 0) out float sphericalVertexDistance;
        layout(location = 1) out float cylindricalVertexDistance;
        layout(location = 2) out vec4 vertexColor;
        layout(location = 3) out vec2 texCoord0;
        layout(location = 4) out float chunkVisibility;
        
        void main() {
            vec3 pos = Position + (ChunkPosition - CameraBlockPos) + CameraOffset;
            gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);
        
            sphericalVertexDistance = fog_spherical_distance(pos);
            cylindricalVertexDistance = fog_cylindrical_distance(pos);
            #ifndef OIT_ALPHA_ONLY
            vertexColor = Color * sample_lightmap(Sampler2, UV2);
            #else
            vertexColor = Color;
            #endif
            texCoord0 = UV0;
        
            const float chunkFullyVisibleRange = 16.0;
            float dist = length(pos);
            chunkVisibility = mix(1.0, ChunkVisibility, clamp((dist - chunkFullyVisibleRange) / chunkFullyVisibleRange, 0.0, 1.0));
        }
        """;
    private static final String FRAGMENT = """
        #version 330
        #extension GL_ARB_separate_shader_objects : require
        
        #include <minecraft:fog.glsl>
        #include <minecraft:globals.glsl>
        #include <minecraft:texture_sampling.glsl>
        #include <minecraft:oit.glsl>
        #include <minecraft:terrainglobals.glsl>
        #ifndef MULTIDRAW_TERRAIN
            #include <minecraft:chunksection.glsl>
        #endif
        
        uniform sampler2D Sampler0;
        
        layout(location = 0) in float sphericalVertexDistance;
        layout(location = 1) in float cylindricalVertexDistance;
        layout(location = 2) in vec4 vertexColor;
        layout(location = 3) in vec2 texCoord0;
        layout(location = 4) in float chunkVisibility;
        
        #ifndef OIT_ALPHA_ONLY
        layout(location = 0) out vec4 fragColor;
        #endif
        
        vec4 calculateFinalColor(vec4 color) {
            #ifdef OIT_ACCUMULATE
            color = sampleColorForAccumulation(color);
            vec4 fogColor = vec4(FogColor.rgb * color.a, FogColor.a);
            #else
            vec4 fogColor = FogColor;
            #endif
            return apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, fogColor);
        }
        
        void main() {
            vec4 color = (UseRgss == 1 ? sampleRGSS(Sampler0, texCoord0, 1.0f / TextureSize) : sampleNearest(Sampler0, texCoord0, 1.0f / TextureSize)) * vertexColor;
            #ifndef OIT_ALPHA_ONLY
            color = mix(FogColor * vec4(1, 1, 1, color.a), color, chunkVisibility);
            #endif
            #ifdef ALPHA_CUTOUT
            if (color.a < ALPHA_CUTOUT) {
                discard;
            }
            #endif
        
            #ifdef OIT_ALPHA_ONLY
            executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
            #else
            fragColor = calculateFinalColor(color);
            #endif
        }
        """;
    public static boolean active(Project p){return p.fog().stream().anyMatch(f->f.enabled()&&f.get("strength")>0);}
    public static String nativeVertex(){return VERTEX;}
    public static String nativeFragment(){return FRAGMENT;}
    public static String vertex(){return VERTEX.replace("void main() {","layout(location = 5) out vec3 smFogPosition;\nvoid main() {").replace("gl_Position = ProjMat", "smFogPosition = pos;\n    gl_Position = ProjMat");}
    public static String fragment(Project p){return FRAGMENT.replace("vec4 calculateFinalColor", "layout(location = 5) in vec3 smFogPosition;\n"+functions(p)+"\nvec4 calculateFinalColor")
        .replace("return apply_fog(color, sphericalVertexDistance", "float smT = smFogTransmission(smFogPosition);\n    color.rgb = mix(fogColor.rgb, color.rgb, smT);\n    return apply_fog(color, sphericalVertexDistance");}
    // Subtract integer camera origin before float conversion, preserving far-world fractions.
    private static String relative(double n,int axis){long whole=(long)Math.floor(n);String c=new String[]{"x","y","z"}[axis];return "(float("+whole+" - CameraBlockPos."+c+") + "+(n-whole)+" + CameraOffset."+c+")";}
    private static String vec(double x,double y,double z){return "vec3("+x+", "+y+", "+z+")";}
    public static String functions(Project p){
        StringBuilder s=new StringBuilder(COMMON);
        s.append("float smFogTransmission(vec3 v) {\n    float tau = 0.0;\n    float cameraY = float(CameraBlockPos.y) - CameraOffset.y;\n");
        for(var fog:p.fog())if(fog.enabled()&&fog.get("strength")>0){
            String weight=Double.toString(fog.get("strength"));
            if(fog.altitude()!=null){var a=fog.altitude();weight+=" * (cameraY < "+a.min()+" || cameraY > "+a.max()+" ? 0.0 : "+(a.feather()==0?"1.0":"smoothstep(0.0, 1.0, min((cameraY - "+a.min()+") / "+a.feather()+", ("+a.max()+" - cameraY) / "+a.feather()+"))")+")";}
            if(fog.falloff()==Fog.Falloff.LINEAR)s.append("    tau += -log(max(1e-9, 1.0 - clamp((length(v) - "+fog.get("start")+") / "+(fog.get("end")-fog.get("start"))+", 0.0, 1.0) * ("+weight+")));\n");
            else s.append("    tau += 0.6931471805599453 / "+fog.get("visibility")+" * ("+weight+") * smFogLength(v, "+fog.shape().ordinal()+", "+fog.get("start")+", "+relative(fog.get("lower"),1)+", "+relative(fog.get("upper"),1)+", vec3("+relative(fog.get("x"),0)+", "+relative(fog.get("y"),1)+", "+relative(fog.get("z"),2)+"), "+vec(fog.get("width")/2,fog.get("height")/2,fog.get("depth")/2)+", "+fog.get("radius")+", "+fog.get("fade")+", "+fog.samples()+");\n");
        }
        return s.append("    return exp(-min(tau, 80.0));\n}\n").toString();
    }
    private static final String COMMON = """
        bool smFogClip(float o, float d, float lo, float hi, inout vec2 t) {
            if (abs(d) < 1e-10) return o >= lo && o <= hi;
            vec2 hit = vec2(lo-o, hi-o)/d;
            t = vec2(max(t.x, min(hit.x,hit.y)), min(t.y, max(hit.x,hit.y)));
            return t.y > t.x;
        }
        float smFogPrimitive(float x, float f) {
            if (x <= 0.0) return 0.0;
            if (x >= f) return x-f*0.5;
            float t=x/f; return f*(t*t*t-0.5*t*t*t*t);
        }
        float smFogLength(vec3 v, int shape, float start, float lowerY, float upperY,
                          vec3 center, vec3 halfSize, float radius, float fade, int samples) {
            float len=length(v); if (len < 1e-10) return 0.0;
            vec3 r=v/len; vec2 t=vec2(max(0.0,start),len);
            if (t.x >= t.y) return 0.0;
            vec3 o=-center;
            if (shape == 1 || shape == 2) {
                if (!smFogClip(0.0,r.y,shape==1 ? -1e30 : lowerY,upperY,t)) return 0.0;
            } else if (shape == 3) {
                if (!smFogClip(o.x,r.x,-halfSize.x,halfSize.x,t) ||
                    !smFogClip(o.y,r.y,-halfSize.y,halfSize.y,t) ||
                    !smFogClip(o.z,r.z,-halfSize.z,halfSize.z,t)) return 0.0;
            } else if (shape == 4) {
                // Closest approach avoids b*b-c cancellation for small far-away spheres.
                float b=dot(o,r); vec3 closest=o-r*b;
                float disc=radius*radius-dot(closest,closest);
                if (disc <= 0.0) return 0.0;
                float q=sqrt(disc); t=vec2(max(t.x,-b-q),min(t.y,-b+q));
            }
            float span=t.y-t.x; if (span <= 0.0) return 0.0;
            if (fade == 0.0 || shape == 0) return span;
            if (shape == 1 || shape == 2) {
                if (abs(r.y) < 1e-7) {
                    float y=(t.x+t.y)*0.5*r.y;
                    float density=smoothstep(0.0,fade,upperY-y);
                    if (shape==2) density=min(density,smoothstep(0.0,fade,y-lowerY));
                    return span*density;
                }
                float y0=t.x*r.y-upperY, y1=t.y*r.y-upperY;
                float top=(smFogPrimitive(y1+fade,fade)-smFogPrimitive(y0+fade,fade))/r.y;
                if (shape==1) return clamp(span-top,0.0,span);
                float thickness=upperY-lowerY;
                return clamp((smFogPrimitive(y1+thickness,fade)-smFogPrimitive(y0+thickness,fade))/r.y-top,0.0,span);
            }
            float sum=0.0;
            for (int i=0; i<samples; i++) {
                vec3 p=o+(t.x+(float(i)+0.5)*span/float(samples))*r;
                vec3 margin=halfSize-abs(p);
                float m=shape==4 ? radius-length(p) : min(margin.x,min(margin.y,margin.z));
                sum+=smoothstep(0.0,fade,m);
            }
            return span*sum/float(samples);
        }
        """;
}
