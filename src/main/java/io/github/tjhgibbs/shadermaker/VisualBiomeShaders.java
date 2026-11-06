package io.github.tjhgibbs.shadermaker;

/** Vanilla resources only. Experimental transport from visible sky to the full-screen pass. */
public final class VisualBiomeShaders {
    private VisualBiomeShaders() {}
    public static String sky() {return sky(null);}
    public static String sky(Project project) {
        return """
            #version 330
            #extension GL_ARB_separate_shader_objects : require
            #include <minecraft:fog.glsl>
            #include <minecraft:dynamictransforms.glsl>
            layout(location = 0) in float sphericalVertexDistance;
            layout(location = 1) in float cylindricalVertexDistance;
            layout(location = 0) out vec4 fragColor;
            """+VisualBiomes.classifierGlsl(project==null?VisualBiomes.GROUPS:VisualBiomes.palette(project))+"""
            void main() {
                // Preserve vanilla visible RGB, including fog and the lower dark disc.
                fragColor = apply_fog(ColorModulator, sphericalVertexDistance, cylindricalVertexDistance,
                    0.0, FogSkyEnd, FogSkyEnd, FogSkyEnd, FogColor);
                float code = visualBiomeCode(ColorModulator.rgb);
                if (code > 0.0 && ColorModulator.a == 1.0) fragColor.a = code / 255.0;
            }
            """;
    }
    /** Complementary nibbles make unmarked opaque alpha (255) and empty alpha (0) invalid. */
    public static String decodeGlsl() {
        return """
            int skyMarker(float alpha) {
                float scaled = alpha * 255.0;
                int code = int(round(scaled));
                if (abs(scaled - float(code)) > 0.1 || code < 15 || code > 240 || code % 15 != 0) return -1;
                return code / 15 - 1;
            }
            bool skyDepth(float d) { return d <= 0.0000001 || d >= 0.9999999; }
            """;
    }
    public static String detect() {
        return """
            #version 330
            #extension GL_ARB_separate_shader_objects : require
            uniform sampler2D InSampler;
            uniform sampler2D DepthSampler;
            layout(location = 0) in vec2 texCoord;
            layout(location = 0) out vec4 fragColor;
            """+decodeGlsl()+"""
            void main() {
                int counts[16];
                for (int i=0; i<16; i++) counts[i]=0;
                ivec2 size = textureSize(InSampler,0);
                // One 1x1 reduction per frame; hold_sky keeps memory across frames. No CPU input or scene RGB changes.
                for (int y=0; y<32; y++) for (int x=0; x<32; x++) {
                    ivec2 p = min(ivec2((vec2(x,y)+0.5)*vec2(size)/32.0),size-1);
                    if (!skyDepth(texelFetch(DepthSampler,p,0).r)) continue;
                    int id = skyMarker(texelFetch(InSampler,p,0).a);
                    if (id >= 0) counts[id]++;
                }
                int winner=-1; int largest=2; bool tied=false;
                for (int i=0; i<16; i++) {
                    if (counts[i]>largest) {winner=i; largest=counts[i]; tied=false;}
                    else if (counts[i]==largest) tied=true;
                }
                float code = winner<0 || tied ? 0.0 : float(15+15*winner)/255.0;
                fragColor = vec4(code,0.0,0.0,1.0);
            }
            """;
    }
}
