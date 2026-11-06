package io.github.tjhgibbs.shadermaker.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class ShaderMakerClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        ShaderMakerCommands.register();
        try { SampleImage.bundled(); }
        catch (java.io.IOException e) { System.getLogger("shader-maker").log(System.Logger.Level.WARNING,"Sample unavailable",e); }
        var category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("shader_maker","main"));
        var key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.shader_maker.open",InputConstants.Type.KEYBOARD,InputConstants.KEY_N,category));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (key.consumeClick()) {
                // Only open in a world; the current UI owns its input, including typed N.
                var screen=mc.gui.screen();
                if(mc.level!=null && (screen==null || screen instanceof PauseScreen))
                    mc.setScreenAndShow(new LayerScreen(screen));
            }
        });
        ScreenEvents.AFTER_INIT.register((mc,screen,width,height) -> {
            if (mc.level!=null && screen instanceof PauseScreen)
                Screens.getWidgets(screen).add(Button.builder(Component.literal("Shader Maker"),b -> mc.setScreenAndShow(new LayerScreen(screen)))
                    .bounds(6,6,106,20).build());
        });
    }
}
