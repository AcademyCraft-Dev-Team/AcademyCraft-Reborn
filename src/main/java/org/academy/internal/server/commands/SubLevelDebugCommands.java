package org.academy.internal.server.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.academy.internal.common.sublevel.SubLevelServer;

/** Phase 1 diagnostics for the shared plot and unified physics universe. */
final class SubLevelDebugCommands {
    private SubLevelDebugCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("sublevel")
                .then(Commands.literal("status").executes(SubLevelDebugCommands::status))
                .then(Commands.literal("probe").executes(SubLevelDebugCommands::probe))
                .then(Commands.literal("capture").executes(SubLevelDebugCommands::capture))
                .then(Commands.literal("remove").executes(SubLevelDebugCommands::remove));
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        var status = SubLevelServer.status(context.getSource().getServer());
        context.getSource().sendSuccess(() -> Component.literal(
                "[AC SubLevel] ready=" + status.ready() + " detail=" + status.detail()
                        + " plots=" + status.allocated() + " sublevels=" + status.subLevels()
                        + " bodies=" + status.bodies()
                        + " terrain_sections=" + status.terrainSections()
                        + " terrain_boxes=" + status.terrainBoxes()), false);
        return status.ready() ? 1 : 0;
    }

    private static int probe(CommandContext<CommandSourceStack> context) {
        var result = SubLevelServer.probe(context.getSource().getServer());
        context.getSource().sendSuccess(() -> Component.literal("[AC SubLevel] " + result), true);
        return result.startsWith("created ") ? 1 : 0;
    }

    private static int capture(CommandContext<CommandSourceStack> context) {
        var player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(Component.literal("[AC SubLevel] capture requires a player"));
            return 0;
        }
        var result = SubLevelServer.captureProbe(context.getSource().getServer(), player.level(),
                player.blockPosition().above(5));
        context.getSource().sendSuccess(() -> Component.literal("[AC SubLevel] " + result), true);
        return result.startsWith("captured ") ? 1 : 0;
    }

    private static int remove(CommandContext<CommandSourceStack> context) {
        var result = SubLevelServer.removeProbe(context.getSource().getServer());
        context.getSource().sendSuccess(() -> Component.literal("[AC SubLevel] " + result), true);
        return result.startsWith("removed ") ? 1 : 0;
    }
}
