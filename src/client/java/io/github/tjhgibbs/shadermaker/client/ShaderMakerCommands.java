package io.github.tjhgibbs.shadermaker.client;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.network.chat.Component;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/** Read-only diagnostics for the clock/rendering spike. No live-render capability is implied. */
final class ShaderMakerCommands {
    static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(
            literal("shadermaker")
                .then(literal("context").executes(command -> {
                    var source=command.getSource();
                    source.sendFeedback(Component.literal(describe(WorldContext.capture(source.getClient()))));
                    return 1;
                }))
                .then(literal("capabilities").executes(command -> {
                    command.getSource().sendFeedback(Component.literal(
                        "Vanilla pack output: base grade, ordered layers and camera-height conditions, plus Sky colour conditions with vanilla shortcuts/custom datapack signals (visible sky required), and authored terrain distance/height/box/sphere fog using native fog colour. Exact biome IDs, daylight, rain/thunder and visible flash: standalone export not implemented; existing settings can be saved, reviewed or cleared. Terrain fog: Auto mode only; entities/sky/clouds retain native fog. Animated noise and lit fog are deliberately excluded. Camera-height output requires clean-client graphics validation."));
                    return 1;
                }))));
    }
    static String describe(java.util.Optional<WorldContext> snapshot) {
        if(snapshot.isEmpty()) return "World context unavailable (no initialized camera in current level).";
        var c=snapshot.get();
        return "dimension="+c.dimension()+" biome="+c.biome().orElse("unavailable")
            +" cameraY="+c.cameraY()+" yaw="+c.yaw()+" clockTicks="
            +(c.dayTime().isPresent()?Double.toString(c.dayTime().getAsDouble()):"unavailable")
            +" (raw default clock; first server update and /time synchronization unverified)";
    }
}
