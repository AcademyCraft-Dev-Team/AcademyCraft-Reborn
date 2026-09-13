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
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.misaka.MisakaNAT;
import net.minecraft.world.entity.EntitySpawnReason;
import org.academy.internal.common.ability.teleport.TeleportSync;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.misaka.MisakaDayTime;
import org.academy.internal.server.misaka.MisakaActiveNetworkData;
import org.academy.internal.server.misaka.MisakaComputeContribution;
import org.academy.internal.server.misaka.MisakaLoadedSisterIndex;
import org.academy.internal.server.misaka.MisakaNetworkCoverage;
import org.academy.internal.server.misaka.MisakaPlayers;
import org.academy.internal.server.misaka.MisakaRosterWorldReconcile;
import org.academy.internal.server.misaka.MisakaSisterMaterializer;
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
                .then(Commands.literal("recover")
                        .then(Commands.argument("serial", IntegerArgumentType.integer(1))
                                .executes(MisakaCommands::recover)))
                .then(Commands.literal("respawn")
                        .then(Commands.argument("serial", IntegerArgumentType.integer(1))
                                .executes(MisakaCommands::respawn)))
                .then(Commands.literal("tp")
                        .then(Commands.argument("serial", IntegerArgumentType.integer(1))
                                .executes(MisakaCommands::tp)))
                .then(Commands.literal("verify")
                        .executes(ctx -> verify(ctx, false)))
                .then(Commands.literal("prune-absent")
                        .executes(ctx -> verify(ctx, true)))
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

    private static int recover(CommandContext<CommandSourceStack> ctx) {
        int serial = IntegerArgumentType.getInteger(ctx, "serial");
        var server = ctx.getSource().getServer();
        var roster = MisakaSisterRoster.get(server);
        var record = roster.findBySerial(serial).orElse(null);
        if (record == null) {
            ctx.getSource().sendFailure(Component.literal("No Misaka sister with serial " + serial));
            return 0;
        }
        roster.modify(record.misakaUuid, sister -> sister.incapacitated = false);
        record.incapacitated = false;
        MisakaComputeContribution.refreshCpForRecord(server, record);
        var loaded = org.academy.internal.common.world.entity.misaka.MisakaSisterRosterSync
                .findLoadedSister(server, record.misakaUuid);
        if (loaded.isPresent()) {
            var sister = loaded.get();
            sister.setHealth(Math.min(sister.getMaxHealth(), Math.max(sister.getHealth(), sister.getMaxHealth() * 0.5f)));
            org.academy.internal.common.world.entity.misaka.MisakaSisterRosterSync.syncFromRecord(sister, record);
            sister.clearIncapacitatedHold();
        }
        boolean found = loaded.isPresent();
        ctx.getSource().sendSuccess(
                () -> Component.literal(
                        "Recovered #" + serial + " (incap=false)"
                                + (found
                                ? ", synced loaded entity"
                                : ", no loaded entity — run /academy misaka verify "
                                + "(or /academy misaka respawn " + serial + " here)")
                ),
                true
        );
        return 1;
    }

    /**
     * Materialize a roster row that has no living entity at the issuer's feet.
     * Bulk recovery is automatic via {@link MisakaRosterWorldReconcile}; this is for force-here.
     */
    private static int respawn(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var player = ctx.getSource().getPlayerOrException();
        int serial = IntegerArgumentType.getInteger(ctx, "serial");
        var server = ctx.getSource().getServer();
        var roster = MisakaSisterRoster.get(server);
        var record = roster.findBySerial(serial).orElse(null);
        if (record == null) {
            ctx.getSource().sendFailure(Component.literal("No Misaka sister with serial " + serial));
            return 0;
        }

        var existing = MisakaNetworkCoverage.findLoadedSister(server, record.misakaUuid, serial);
        if (existing != null) {
            ctx.getSource().sendFailure(Component.literal(
                    "#" + serial + " is already loaded at " + formatPos(existing.position())
                            + ". Use /academy misaka tp " + serial
            ));
            return 0;
        }

        var level = (ServerLevel) player.level();
        var sister = MisakaSisterMaterializer.materializeAt(
                level, record, player.getX(), player.getY(), player.getZ(), player.getYRot()
        );
        if (sister == null) {
            ctx.getSource().sendFailure(Component.literal("Failed to respawn Misaka sister #" + serial));
            return 0;
        }

        boolean incap = record.incapacitated;
        ctx.getSource().sendSuccess(
                () -> Component.literal(
                        "Respawned #" + serial + " (" + record.misakaUuid + ") at "
                                + formatPos(sister.position())
                                + (incap ? " [still incapacitated — /academy misaka recover " + serial + "]" : "")
                ),
                true
        );
        return 1;
    }

    private static int tp(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var player = ctx.getSource().getPlayerOrException();
        int serial = IntegerArgumentType.getInteger(ctx, "serial");
        var server = ctx.getSource().getServer();
        var record = MisakaSisterRoster.get(server).findBySerial(serial).orElse(null);
        if (record == null) {
            ctx.getSource().sendFailure(Component.literal("No Misaka sister with serial " + serial));
            return 0;
        }

        var loaded = MisakaNetworkCoverage.findLoadedSister(server, record.misakaUuid, serial);
        if (loaded != null) {
            // Uuid/serial drift: roster row is authoritative; rebind + mirror status.
            org.academy.internal.common.world.entity.misaka.MisakaSisterRosterSync
                    .ensureRegistered(loaded);
            loaded = MisakaNetworkCoverage.findLoadedSister(server, record.misakaUuid, serial);
            if (loaded == null) {
                ctx.getSource().sendFailure(Component.literal(
                        "#" + serial + " vanished during roster reconcile. " + describeLoadedSisters()
                ));
                return 0;
            }
            if (!record.misakaUuid.equals(loaded.getMisakaUuid()) || loaded.getSerial() != serial) {
                org.academy.internal.common.world.entity.misaka.MisakaSisterRosterSync
                        .bindToRecord(loaded, record);
            }
            var level = (ServerLevel) loaded.level();
            var pos = loaded.position();
            if (!TeleportSync.teleportInstantly(player, level, pos)) {
                ctx.getSource().sendFailure(Component.literal("Teleport to #" + serial + " failed"));
                return 0;
            }
            var dim = level.dimension().identifier();
            ctx.getSource().sendSuccess(
                    () -> Component.literal(
                            "Teleported to #" + serial
                                    + " at " + formatPos(pos)
                                    + " (" + dim + ")"
                    ),
                    true
            );
            return 1;
        }

        ChunkPos chunk = record.lastKnownChunk;
        BlockPos knownPos = record.lastKnownBlockPos;
        if (chunk == null && knownPos == null) {
            ctx.getSource().sendFailure(Component.literal(
                    "#" + serial + " is not loaded and has no last-known position. "
                            + describeLoadedSisters()
            ));
            return 0;
        }
        var level = server.getLevel(record.lastKnownDimension);
        if (level == null) {
            level = server.overworld();
        }
        if (chunk != null) {
            level.getChunk(chunk.x(), chunk.z());
        } else if (knownPos != null) {
            level.getChunk(knownPos);
        }
        loaded = MisakaNetworkCoverage.findLoadedSister(server, record.misakaUuid, serial);
        if (loaded != null) {
            if (!record.misakaUuid.equals(loaded.getMisakaUuid())) {
                org.academy.internal.common.world.entity.misaka.MisakaSisterRosterSync
                        .bindToRecord(loaded, record);
            }
            var destLevel = (ServerLevel) loaded.level();
            var pos = loaded.position();
            if (!TeleportSync.teleportInstantly(player, destLevel, pos)) {
                ctx.getSource().sendFailure(Component.literal("Teleport to #" + serial + " failed"));
                return 0;
            }
            var dim = destLevel.dimension().identifier();
            ctx.getSource().sendSuccess(
                    () -> Component.literal(
                            "Teleported to #" + serial
                                    + " at " + formatPos(pos)
                                    + " (" + dim + ")"
                    ),
                    true
            );
            return 1;
        }

        final Vec3 dest;
        if (knownPos != null) {
            dest = new Vec3(knownPos.getX() + 0.5, knownPos.getY(), knownPos.getZ() + 0.5);
        } else {
            int x = chunk.getMiddleBlockX();
            int z = chunk.getMiddleBlockZ();
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            dest = new Vec3(x + 0.5, y, z + 0.5);
        }
        final ServerLevel destLevel = level;
        if (!TeleportSync.teleportInstantly(player, destLevel, dest)) {
            ctx.getSource().sendFailure(Component.literal(
                    "Teleport to last-known position of #" + serial + " failed"
            ));
            return 0;
        }
        var dim = destLevel.dimension().identifier();
        ctx.getSource().sendSuccess(
                () -> Component.literal(
                        "Entity #" + serial + " not loaded; teleported to last-known "
                                + formatPos(dest)
                                + " (" + dim + "). "
                                + describeLoadedSisters()
                                + " — run /academy misaka verify to rematerialize missing entities "
                                + "in loaded last-known chunks (or /academy misaka respawn " + serial + ")"
                ),
                true
        );
        return 1;
    }

    private static String describeLoadedSisters() {
        var parts = new StringBuilder("Loaded now:");
        int count = 0;
        for (var sister : MisakaLoadedSisterIndex.all()) {
            if (sister.isRemoved()) {
                continue;
            }
            if (count++ > 0) {
                parts.append(',');
            }
            parts.append(" #").append(sister.getSerial())
                    .append('@').append(formatPos(sister.position()));
        }
        if (count == 0) {
            parts.append(" (none)");
        }
        return parts.toString();
    }

    private static String formatPos(Vec3 pos) {
        return String.format("%.1f %.1f %.1f", pos.x, pos.y, pos.z);
    }

    private static int verify(CommandContext<CommandSourceStack> ctx, boolean pruneAbsent) {
        var server = ctx.getSource().getServer();
        int rematerialized = 0;
        int rebound = 0;
        int pruned = 0;
        int entityFixed = 0;
        MisakaRosterWorldReconcile.Report last;
        // Drain ghosts in loaded last-known chunks (large rosters need multiple budgeted passes).
        int passes = 0;
        do {
            last = MisakaRosterWorldReconcile.reconcile(
                    server,
                    pruneAbsent,
                    passes == 0,
                    MisakaSisterMaterializer.COMMAND_BUDGET,
                    !pruneAbsent
            );
            rematerialized += last.rematerialized();
            rebound += last.rebound();
            pruned += last.prunedAbsent();
            entityFixed += last.entitiesWithoutRosterFixed();
            passes++;
        } while (!pruneAbsent
                && last.rematerialized() > 0
                && last.pendingMaterialize() > 0
                && passes < 64);

        int matched = last.loadedMatched();
        int notLoaded = last.notCurrentlyLoaded();
        int pending = last.pendingMaterialize();
        int finalRematerialized = rematerialized;
        int finalRebound = rebound;
        int finalPruned = pruned;
        int finalEntityFixed = entityFixed;
        int finalPasses = passes;
        ctx.getSource().sendSuccess(
                () -> Component.literal(String.format(
                        "Misaka reconcile%s: matched=%d rebound=%d rematerialized=%d notLoaded=%d pending=%d pruned=%d entityFixed=%d (passes=%d)",
                        pruneAbsent ? " (prune)" : "",
                        matched,
                        finalRebound,
                        finalRematerialized,
                        notLoaded,
                        pending,
                        finalPruned,
                        finalEntityFixed,
                        finalPasses
                )),
                true
        );
        return finalRebound + finalRematerialized + finalPruned + finalEntityFixed;
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
