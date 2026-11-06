package io.github.tjhgibbs.shadermaker;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Pinned 26.3 opaque terrain / lightmap alpha bridge. Never changes translucent or OIT alpha. */
public final class WorldSignalShaders {
    private WorldSignalShaders() {}
    public static String decode(){return """
        // Marker = alpha 15*(1+mask) AND RGB low bits odd/even/odd. Every 8-bit alpha is an integer, so
        // the alpha code alone is mimicked by 1 in 15 blended pixels (e.g. OIT-composited water coverage).
        int smTerrainMask(vec4 texel, float depth) {
            if (depth <= 0.0000001 || depth >= 0.9999999) return -1;
            ivec3 low = ivec3(floor(texel.rgb * 255.0 + 0.5)) & 1;
            if (low != ivec3(1, 0, 1)) return -1;
            float scaled = texel.a * 255.0;
            int code = int(round(scaled));
            if (abs(scaled-float(code)) > 0.1 || code < 15 || code > 240 || code % 15 != 0) return -1;
            return code/15-1;
        }
        """;}
    private static String predicate(List<WorldSignals.Condition> conditions,boolean lightmap){return conditions.stream().filter(c->c.source().lightmap==lightmap).map(WorldSignals.Condition::glsl).reduce((a,b)->a+" && "+b).orElse("true");}
    public static String vertex(Project p){
        String s=FogShaders.active(p)?FogShaders.vertex():FogShaders.nativeVertex();
        String inputs="layout(location = 6) flat out int smLightMask;\nlayout(location = 7) out vec3 smSurfaceTint;\n";
        String sample=WorldSignals.lighting(p)?"float smAlpha = texelFetch(Sampler2,ivec2(0),0).a * 255.0;\n    int smCode = int(round(smAlpha));\n    smLightMask = (abs(smAlpha-float(smCode)) <= 0.1 && smCode >= 15 && smCode <= 240 && smCode % 15 == 0) ? smCode/15-1 : 0;\n":"smLightMask = 15;\n";
        return s.replace("void main() {", inputs+"void main() {\n    smSurfaceTint=Color.rgb;\n    smLightMask=0;\n    #ifndef OIT_ALPHA_ONLY\n    "+sample+"    #endif\n");
    }
    public static String fragment(Project p){
        String s=FogShaders.active(p)?FogShaders.fragment(p):FogShaders.nativeFragment();
        var fn=new StringBuilder("layout(location = 6) flat in int smLightMask;\nlayout(location = 7) in vec3 smSurfaceTint;\n"+WorldSignals.COLOUR_GLSL+"\nint smContextMask() {\n    int mask=0;\n");
        var palette=WorldSignals.palette(p);
        for(int i=0;i<palette.size();i++) {
            var conditions=palette.get(i);String light=conditions.stream().anyMatch(c->c.source().lightmap)?"(smLightMask & "+(1<<i)+") != 0 && ":"";
            fn.append("    if (").append(light).append(predicate(conditions,false)).append(") mask |= ").append(1<<i).append(";\n");
        }
        fn.append("    return mask;\n}\n");
        // Native 26.3: solid has no cutout define; cutout uses 0.5, sorted translucent uses 0.1.
        // A runtime comparison is intentional: GLSL preprocessor expressions cannot compare float defines.
        return s.replace("vec4 calculateFinalColor",fn+"\nvec4 calculateFinalColor").replace("fragColor = calculateFinalColor(color);", """
            fragColor = calculateFinalColor(color);
            #ifndef OIT_ACCUMULATE
            #ifndef ALPHA_CUTOUT
                bool smOpaquePipeline = true;
            #else
                bool smOpaquePipeline = ALPHA_CUTOUT >= 0.5;
            #endif
            // Filtered/mipmapped opaque texels can land a hair below 1.0; treat them as opaque.
            if (smOpaquePipeline && fragColor.a >= 254.5/255.0) {
                fragColor.a = float(15*(1+smContextMask()))/255.0;
                // Checksum in the RGB low bits (odd, even, odd): at most 1/255 change per channel.
                vec3 smQ = floor(clamp(fragColor.rgb, 0.0, 1.0) * 255.0 + 0.5);
                fragColor.rgb = (smQ - mod(smQ, 2.0) + vec3(1.0, 0.0, 1.0)) / 255.0;
            }
            #endif
            """);
    }
    public static String lightmap(Project p){
        String source;
        try(var stream=WorldSignalShaders.class.getResourceAsStream("/assets/shader_maker/templates/lightmap.fsh")) {
            if(stream==null)throw new IllegalStateException("Missing pinned lightmap template");
            source=new String(stream.readAllBytes(),StandardCharsets.UTF_8);
        } catch(IOException e){throw new UncheckedIOException(e);}
        var fn=new StringBuilder(WorldSignals.COLOUR_GLSL+"\nfloat smLightmapMarker() {\n    int mask=0;\n");
        var palette=WorldSignals.palette(p);
        for(int i=0;i<palette.size();i++)fn.append("    if (").append(predicate(palette.get(i),true)).append(") mask |= ").append(1<<i).append(";\n");
        fn.append("    return float(15*(1+mask))/255.0;\n}\n");
        return source.replace("void main() {",fn+"\nvoid main() {").replace("fragColor = vec4(color, 1.0);","fragColor = vec4(color, smLightmapMarker());");
    }
    /** Every pinned vanilla lightmap consumer uses this helper; alpha must never affect surface opacity. */
    public static String sampleLightmap(){return """
        #ifndef MINECRAFT_SAMPLE_LIGHTMAP_GLSL
        #define MINECRAFT_SAMPLE_LIGHTMAP_GLSL
        vec4 sample_lightmap(sampler2D lightMap, ivec2 uv) {
            return vec4(texture(lightMap, clamp((uv / 256.0) + 0.5 / 16.0, vec2(0.5 / 16.0), vec2(15.5 / 16.0))).rgb, 1.0);
        }
        #endif
        """;}
    public static String detect(){return """
        #version 330
        #extension GL_ARB_separate_shader_objects : require
        uniform sampler2D InSampler;
        uniform sampler2D DepthSampler;
        layout(location = 0) in vec2 texCoord;
        layout(location = 0) out vec4 fragColor;
        """+decode()+"""
        void main() {
            ivec4 counts=ivec4(0);
            int valid=0;
            ivec2 size=textureSize(InSampler,0);
            for(int y=0;y<32;y++) for(int x=0;x<32;x++) {
                ivec2 p=min(ivec2((vec2(x,y)+0.5)*vec2(size)/32.0),size-1);
                int mask=smTerrainMask(texelFetch(InSampler,p,0),texelFetch(DepthSampler,p,0).r);
                if(mask<0)continue;
                valid++;
                for(int i=0;i<4;i++)if((mask & (1<<i))!=0)counts[i]++;
            }
            // No marked terrain this frame: 0.5 in every channel tells the hold pass to keep its memory.
            // A set is on when at least 3 samples and 1/16 of all marked terrain samples carry it.
            fragColor=valid>=3?vec4(greaterThanEqual(counts,ivec4(max(3,valid/16)))):vec4(0.5);
        }
        """;}
}
