package io.github.tjhgibbs.shadermaker.client;

import io.github.tjhgibbs.shadermaker.WorldSignals;
import net.minecraft.client.Minecraft;
import net.minecraft.world.attribute.EnvironmentAttributes;
import java.util.*;

/**
 * Authoring aid only: reads the values the game resolved for the last frame at the camera, so authors can
 * match "here" instead of guessing colours. Exported packs never depend on this.
 */
final class LiveSignals {
    private LiveSignals() {}
    static Optional<WorldSignals.Sample> read(WorldSignals.Source source) {
        var mc=Minecraft.getInstance();
        if(mc.level==null)return Optional.empty();
        try {
            var camera=mc.gameRenderer.mainCamera();
            if(!camera.isInitialized())return Optional.empty();
            if(source.lightmap){
                var attribute=switch(source){case AMBIENT_COLOR->EnvironmentAttributes.AMBIENT_LIGHT_COLOR;case BLOCK_LIGHT_TINT->EnvironmentAttributes.BLOCK_LIGHT_TINT;
                    case SKY_LIGHT_COLOR->EnvironmentAttributes.SKY_LIGHT_COLOR;default->EnvironmentAttributes.NIGHT_VISION_COLOR;};
                var v=camera.attributeProbe().getValue(attribute,1f);
                return Optional.of(new WorldSignals.Sample(v.x(),v.y(),v.z()));
            }
            var fog=mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState.fogData;
            if(fog==null)return Optional.empty();
            return switch(source){
                case FOG_COLOR->Optional.of(new WorldSignals.Sample(fog.color.x(),fog.color.y(),fog.color.z()));
                case FOG_START->Optional.of(WorldSignals.Sample.distance(fog.environmentalStart));
                case FOG_END->Optional.of(WorldSignals.Sample.distance(fog.environmentalEnd));
                case SKY_FOG_END->Optional.of(WorldSignals.Sample.distance(fog.skyEnd));
                case CLOUD_FOG_END->Optional.of(WorldSignals.Sample.distance(fog.cloudEnd));
                default->Optional.empty(); // Surface tint varies per block; there is no single camera value.
            };
        } catch(RuntimeException e) { return Optional.empty(); }
    }
    static int rgb(WorldSignals.Sample s){
        return (int)Math.round(Math.clamp(s.r(),0,1)*255)<<16|(int)Math.round(Math.clamp(s.g(),0,1)*255)<<8|(int)Math.round(Math.clamp(s.b(),0,1)*255);
    }
}
