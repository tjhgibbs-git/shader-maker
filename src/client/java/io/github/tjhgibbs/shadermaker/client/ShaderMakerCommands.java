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
                        "Static grading: preview/export. Conditional layers: CPU sample only. Live uniforms, synchronized clock, pulses and depth fog: unavailable/unverified."));
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
