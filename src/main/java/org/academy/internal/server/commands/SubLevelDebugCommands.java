package org.academy.internal.server.commands;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.academy.internal.common.sublevel.SubLevelServer;
import org.academy.internal.common.structure.upgrade.lifecycle.LocalStructureRestorationBatch;

/** Phase 1 diagnostics for the shared plot and unified physics universe. */
final class SubLevelDebugCommands {
    private SubLevelDebugCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("sublevel")
                .then(Commands.literal("restore_all")
                        .executes(context -> LocalStructureRestorationBatch.start(context.getSource()))
                        .then(Commands.literal("status").executes(context -> LocalStructureRestorationBatch.report(context.getSource())))
                        .then(Commands.literal("cancel").executes(context -> LocalStructureRestorationBatch.cancel(context.getSource()))))
                .then(Commands.literal("status").executes(SubLevelDebugCommands::status))
                .then(Commands.literal("probe").executes(SubLevelDebugCommands::probe))
                .then(Commands.literal("capture").executes(SubLevelDebugCommands::capture))
                .then(Commands.literal("remove").executes(SubLevelDebugCommands::remove))
                .then(Commands.literal("shift")
                        .then(Commands.argument("dx", DoubleArgumentType.doubleArg(-16, 16))
                                .then(Commands.argument("dy", DoubleArgumentType.doubleArg(-16, 16))
                                        .then(Commands.argument("dz", DoubleArgumentType.doubleArg(-16, 16))
                                                .executes(SubLevelDebugCommands::shift)))))
                .then(Commands.literal("rotate")
                        .then(Commands.argument("yaw", DoubleArgumentType.doubleArg(-360, 360))
                                .executes(SubLevelDebugCommands::rotate)))
                .then(Commands.literal("launch")
                        .then(Commands.argument("dx", DoubleArgumentType.doubleArg(-1, 1))
                                .then(Commands.argument("dy", DoubleArgumentType.doubleArg(-1, 1))
                                        .then(Commands.argument("dz", DoubleArgumentType.doubleArg(-1, 1))
                                                .then(Commands.argument("speed", DoubleArgumentType.doubleArg(0.01, 64))
                                                        .executes(SubLevelDebugCommands::launch))))));
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

    private static int shift(CommandContext<CommandSourceStack> context) {
        var result = SubLevelServer.shiftFirst(context.getSource().getServer(),
                DoubleArgumentType.getDouble(context, "dx"), DoubleArgumentType.getDouble(context, "dy"),
                DoubleArgumentType.getDouble(context, "dz"));
        context.getSource().sendSuccess(() -> Component.literal("[AC SubLevel] " + result), true);
        return result.startsWith("shifted ") ? 1 : 0;
    }

    private static int rotate(CommandContext<CommandSourceStack> context) {
        var result = SubLevelServer.rotateFirst(context.getSource().getServer(),
                DoubleArgumentType.getDouble(context, "yaw"));
        context.getSource().sendSuccess(() -> Component.literal("[AC SubLevel] " + result), true);
        return result.startsWith("rotated ") ? 1 : 0;
    }

    private static int launch(CommandContext<CommandSourceStack> context) {
        var result = SubLevelServer.launchFirst(context.getSource().getServer(),
                new net.minecraft.world.phys.Vec3(DoubleArgumentType.getDouble(context, "dx"),
                        DoubleArgumentType.getDouble(context, "dy"),
                        DoubleArgumentType.getDouble(context, "dz")),
                DoubleArgumentType.getDouble(context, "speed"));
        context.getSource().sendSuccess(() -> Component.literal("[AC SubLevel] " + result), true);
        return result.startsWith("launched ") ? 1 : 0;
    }

    private static int remove(CommandContext<CommandSourceStack> context) {
        var result = SubLevelServer.removeProbe(context.getSource().getServer());
        context.getSource().sendSuccess(() -> Component.literal("[AC SubLevel] " + result), true);
        return result.startsWith("removed ") ? 1 : 0;
    }
}
