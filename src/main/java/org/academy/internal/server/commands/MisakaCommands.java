package org.academy.internal.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import org.academy.api.common.misaka.MisakaNAT;
import net.minecraft.world.entity.EntitySpawnReason;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.misaka.MisakaDayTime;
import org.academy.internal.server.misaka.MisakaActiveNetworkData;
import org.academy.internal.server.misaka.MisakaComputeContribution;
import org.academy.internal.server.misaka.MisakaPlayers;
import org.academy.internal.server.world.level.storage.MisakaNetworkGovernance;
import org.academy.internal.server.world.level.storage.MisakaSisterRoster;

import java.util.UUID;

public final class MisakaCommands {
    private MisakaCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("misaka")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("rescue")
                        .executes(ctx -> rescue(ctx, 1))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 16))
                                .executes(ctx -> rescue(ctx, IntegerArgumentType.getInteger(ctx, "count")))))
                .then(Commands.literal("list")
                        .executes(MisakaCommands::list))
                .then(Commands.literal("info")
                        .then(Commands.argument("serial", IntegerArgumentType.integer(1))
                                .executes(MisakaCommands::info)))
                .then(Commands.literal("setfavor")
                        .then(Commands.argument("serial", IntegerArgumentType.integer(1))
                                .then(Commands.argument("player", StringArgumentType.string())
                                        .then(Commands.argument("value", IntegerArgumentType.integer(-20, 20))
                                                .executes(MisakaCommands::setFavor)))))
                .then(Commands.literal("setperception")
                        .then(Commands.argument("serial", IntegerArgumentType.integer(1))
                                .then(Commands.argument("value", IntegerArgumentType.integer(0, 200))
                                        .executes(MisakaCommands::setPerception))))
                .then(Commands.literal("bind")
                        .then(Commands.argument("serial", IntegerArgumentType.integer(1))
                                .then(Commands.argument("nodeName", StringArgumentType.string())
                                        .executes(MisakaCommands::bind))))
                .then(Commands.literal("network")
                        .then(Commands.argument("serial", IntegerArgumentType.integer(1))
                                .executes(MisakaCommands::network)))
                .then(Commands.literal("active")
                        .executes(MisakaCommands::activeNetwork)
                        .then(Commands.argument("player", StringArgumentType.string())
                                .executes(MisakaCommands::activeNetworkNamed)));
    }

    private static int rescue(CommandContext<CommandSourceStack> ctx, int count) throws CommandSyntaxException {
        var player = ctx.getSource().getPlayerOrException();
        var level = (ServerLevel) player.level();
        int day = MisakaDayTime.dayIndex(level);
        var roster = MisakaSisterRoster.get(level.getServer());
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            var registered = roster.tryRegisterRescued(level.getRandom(), day);
            if (registered.isEmpty()) {
                ctx.getSource().sendFailure(Component.translatable("message.academy.misaka_serial_pool_exhausted"));
                break;
            }
            var record = registered.get();
            var sister = EntityTypes.MISAKA_SISTER.get().create(level, EntitySpawnReason.MOB_SUMMONED);
            if (sister == null) {
                roster.release(record.misakaUuid);
                continue;
            }
            sister.snapTo(
                    player.getX() + level.getRandom().nextDouble() * 2.0 - 1.0,
                    player.getY(),
                    player.getZ() + level.getRandom().nextDouble() * 2.0 - 1.0,
                    level.getRandom().nextFloat() * 360.0f,
                    0.0f
            );
            sister.bindToRecord(record);
            level.addFreshEntity(sister);
            spawned++;
        }
        int finalSpawned = spawned;
        if (spawned > 0) {
            ctx.getSource().sendSuccess(
                    () -> Component.literal("Spawned " + finalSpawned + " Misaka sister(s)."),
                    true
            );
        }
        return spawned;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        var roster = MisakaSisterRoster.get(ctx.getSource().getServer());
        var lines = new StringBuilder("Misaka sisters:\n");
        for (var record : roster.all()) {
            lines.append("  #").append(record.serial)
                    .append(" uuid=").append(record.misakaUuid)
                    .append(" awakened=").append(record.awakened)
                    .append(" recon=").append(record.isReconstruction)
                    .append(" incap=").append(record.incapacitated)
                    .append(" perception=").append(record.perception)
                    .append('\n');
        }
        if (roster.all().isEmpty()) {
            lines.append("  (none)");
        }
        ctx.getSource().sendSuccess(() -> Component.literal(lines.toString()), false);
        return roster.all().size();
    }

    private static int info(CommandContext<CommandSourceStack> ctx) {
        int serial = IntegerArgumentType.getInteger(ctx, "serial");
        var record = MisakaSisterRoster.get(ctx.getSource().getServer()).findBySerial(serial).orElse(null);
        if (record == null) {
            ctx.getSource().sendFailure(Component.literal("No Misaka sister with serial " + serial));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(String.format(
                "Misaka %d: uuid=%s awakened=%s reconstruction=%s incap=%s perception=%d/%d favor=%s node=%s",
                record.serial,
                record.misakaUuid,
                record.awakened,
                record.isReconstruction,
                record.incapacitated,
                record.perception,
                record.perceptionCap,
                record.favorByPlayerName,
                record.networkNodePos
        )), false);
        return 1;
    }

    private static int setFavor(CommandContext<CommandSourceStack> ctx) {
        int serial = IntegerArgumentType.getInteger(ctx, "serial");
        String name = StringArgumentType.getString(ctx, "player");
        int value = IntegerArgumentType.getInteger(ctx, "value");
        var roster = MisakaSisterRoster.get(ctx.getSource().getServer());
        var record = roster.findBySerial(serial).orElse(null);
        if (record == null) {
            ctx.getSource().sendFailure(Component.literal("No Misaka sister with serial " + serial));
            return 0;
        }
        roster.modify(record.misakaUuid, sister -> sister.favorByPlayerName.put(name, value));
        MisakaComputeContribution.refreshCpForRecord(ctx.getSource().getServer(), record);
        ctx.getSource().sendSuccess(
                () -> Component.literal("Set favor for " + name + " on #" + serial + " to " + value),
                true
        );
        return 1;
    }

    private static int setPerception(CommandContext<CommandSourceStack> ctx) {
        int serial = IntegerArgumentType.getInteger(ctx, "serial");
        int value = IntegerArgumentType.getInteger(ctx, "value");
        var roster = MisakaSisterRoster.get(ctx.getSource().getServer());
        var record = roster.findBySerial(serial).orElse(null);
        if (record == null) {
            ctx.getSource().sendFailure(Component.literal("No Misaka sister with serial " + serial));
            return 0;
        }
        roster.modify(record.misakaUuid, sister -> {
            sister.perception = value;
            if (value >= 101) {
                sister.isReconstruction = true;
                sister.highTierUnlocked = true;
                sister.perceptionCap = Math.max(sister.perceptionCap, 200);
            }
        });
        MisakaComputeContribution.refreshCpForRecord(ctx.getSource().getServer(), record);
        ctx.getSource().sendSuccess(
                () -> Component.literal("Set perception on #" + serial + " to " + value),
                true
        );
        return 1;
    }

    private static int bind(CommandContext<CommandSourceStack> ctx) {
        int serial = IntegerArgumentType.getInteger(ctx, "serial");
        String nodeName = StringArgumentType.getString(ctx, "nodeName");
        var server = ctx.getSource().getServer();
        var roster = MisakaSisterRoster.get(server);
        var record = roster.findBySerial(serial).orElse(null);
        if (record == null) {
            ctx.getSource().sendFailure(Component.literal("No Misaka sister with serial " + serial));
            return 0;
        }
        ServerLevel level = server.overworld();
        BlockPos nodePos = MisakaNAT.get().findNode(level, nodeName).orElse(null);
        if (nodePos == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown node: " + nodeName));
            return 0;
        }
        if (!MisakaNAT.get().bindSisterToNode(level, record.misakaUuid, nodePos)) {
            ctx.getSource().sendFailure(Component.literal("Failed to bind #" + serial + " to " + nodeName));
            return 0;
        }
        ctx.getSource().sendSuccess(
                () -> Component.literal("Bound #" + serial + " to node " + nodeName),
                true
        );
        return 1;
    }

    private static int network(CommandContext<CommandSourceStack> ctx) {
        int serial = IntegerArgumentType.getInteger(ctx, "serial");
        var server = ctx.getSource().getServer();
        var record = MisakaSisterRoster.get(server).findBySerial(serial).orElse(null);
        if (record == null) {
            ctx.getSource().sendFailure(Component.literal("No Misaka sister with serial " + serial));
            return 0;
        }
        UUID networkId = MisakaNAT.get().resolveNetworkId(server.overworld(), record.networkNodePos);
        var gov = MisakaNetworkGovernance.get(server);
        boolean integrated = networkId != null && gov.hasEverIntegrated(networkId);
        var admins = gov.listAdminDisplayNames(networkId);
        String adminText = admins.isEmpty() ? "(none)" : String.join(", ", admins);
        UUID recon = networkId == null ? null : gov.reconstructionUuid(networkId).orElse(null);
        ctx.getSource().sendSuccess(() -> Component.literal(String.format(
                "Network for #%d: id=%s integrated=%s reconUuid=%s admins=[%s]",
                serial,
                networkId,
                integrated,
                recon,
                adminText
        )), false);
        return 1;
    }

    private static int activeNetwork(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var player = ctx.getSource().getPlayerOrException();
        return reportActive(ctx, player.getUUID(), player.getGameProfile().name());
    }

    private static int activeNetworkNamed(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "player");
        var player = MisakaPlayers.findOnlineByName(ctx.getSource().getServer(), name);
        if (player != null) {
            return reportActive(ctx, player.getUUID(), player.getGameProfile().name());
        }
        ctx.getSource().sendFailure(Component.literal("Online player not found: " + name));
        return 0;
    }

    private static int reportActive(CommandContext<CommandSourceStack> ctx, UUID playerUuid, String displayName) {
        var active = MisakaActiveNetworkData.get(ctx.getSource().getServer()).getActive(playerUuid);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "ActiveNetwork for " + displayName + ": " + active.map(UUID::toString).orElse("(none)")
        ), false);
        return 1;
    }
}
