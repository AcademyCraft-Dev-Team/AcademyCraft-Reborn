package org.academy.internal.server.misaka;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.academy.AcademyCraft;
import org.academy.api.common.misaka.MisakaComputeAccess;
import org.academy.internal.common.misaka.MisakaNetworkPermission;
import org.academy.internal.common.network.misaka.MisakaOrbitalChargePacket;
import org.academy.internal.server.world.level.storage.MisakaNetworkGovernance;
import org.academy.internal.server.world.level.storage.MisakaRelayRegistry;
import org.jspecify.annotations.Nullable;
import org.misaka.MisakaNetworkServer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hold-to-charge orbital strike for the laser designator.
 * All network compute is spent only through {@link MisakaComputeAccess#consumeRate}
 * (registered HELD+ACTUAL device); charge accumulates solely from that return value.
 */
public final class MisakaOrbitalCharge {
    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
    private static final Identifier DESIGNATOR_ID = AcademyCraft.academy("laser_designator");
    private static final float GRANT_EPS = 1.0e-4f;

    public static void clearRuntime() {
        SESSIONS.clear();
    }

    private MisakaOrbitalCharge() {
    }

    public static float chargeNeed(MinecraftServer server) {
        var academy = server == null ? null : server.getAcademyCraftServer();
        if (academy == null) {
            return 1000.0f;
        }
        return Math.max(1.0f, academy.getGenericConfig().misakaOrbitalStrikeChargeNeedMsk);
    }

    public static float chargeRate(MinecraftServer server) {
        var academy = server == null ? null : server.getAcademyCraftServer();
        if (academy == null) {
            return 200.0f;
        }
        float rate = academy.getGenericConfig().misakaOrbitalStrikeChargeRateMskPerSec;
        if (rate <= 0.0f) {
            rate = academy.getGenericConfig().misakaOrbitalStrikeMskCost;
        }
        return Math.max(0.0f, rate);
    }

    public static void clear(UUID playerUuid) {
        if (playerUuid != null) {
            SESSIONS.remove(playerUuid);
        }
    }

    public static void clear(ServerPlayer player) {
        if (player == null) {
            return;
        }
        clear(player.getUUID());
        sync(player, false, 0.0f, chargeNeed(player.level().getServer()), 0.0f, "");
    }

    /**
     * One server tick while the designator is held.
     * @return true if a strike was started (caller should stop using the item)
     */
    public static boolean tickCharge(
            ServerPlayer player,
            UUID satelliteId,
            InteractionContext aim
    ) {
        var server = player.level().getServer();
        if (server == null || satelliteId == null) {
            clear(player);
            return false;
        }

        float need = chargeNeed(server);
        float rateCap = chargeRate(server);
        var session = session(player);
        boolean firstTick = session.lastTick == Integer.MIN_VALUE;

        var gate = MisakaOrbitalStrikeSupport.validateBegin(server, satelliteId, player);
        if (gate != MisakaOrbitalStrikeSupport.BeginResult.OK) {
            // Soft-block: keep hold HUD alive. Hard clear() every tick was wiping the action bar.
            softBlock(player, session, need, MisakaOrbitalStrikeSupport.messageKey(gate));
            return false;
        }

        var entry = MisakaRelayRegistry.get(server).get(satelliteId);
        if (entry == null) {
            softBlock(player, session, need, "message.academy.laser_designator_sat_missing");
            return false;
        }

        boolean switched = ensureActiveNetwork(player, entry.networkId, server.overworld().getGameTime());
        if (!switched && !isActiveNetwork(player, entry.networkId)) {
            softBlock(player, session, need, "message.academy.laser_designator_wrong_network");
            return false;
        }

        var access = ServerMisakaComputeAccess.INSTANCE;
        // Refresh device budget on first hold tick / ActiveNetwork switch / empty budget so
        // consumeRate can draw without waiting for the next settle second.
        if (firstTick || switched || !(access.playerBudgetMskPerSecond(player) > GRANT_EPS)) {
            MisakaComputeContribution.syncPlayerDeviceBudget(player, entry.networkId);
        }

        float hudRate = hudRate(access, player, rateCap);
        float granted = MisakaComputeAccess.consumeRateStatic(player, DESIGNATOR_ID, rateCap);
        if (!(granted > GRANT_EPS)) {
            String reason;
            if (access.effectivePercent(player, DESIGNATOR_ID) <= 0) {
                reason = "message.academy.laser_designator_no_alloc";
            } else if (!(access.playerBudgetMskPerSecond(player) > GRANT_EPS)) {
                reason = "message.academy.laser_designator_no_compute";
            } else {
                reason = "message.academy.laser_designator_no_compute";
            }
            softBlock(player, session, need, reason);
            return false;
        }

        session.charge += granted;
        session.touch(server.getTickCount());
        sync(player, true, session.charge, need, hudRate, "");

        if (session.charge + 1.0e-3f < need) {
            return false;
        }

        StrikeTarget target = aim.resolveTarget(player);
        if (target == null) {
            softBlock(player, session, need, "message.academy.laser_designator_no_target");
            return false;
        }

        var result = MisakaRelayRegistry.get(server)
                .beginOrbitalStrike(server, satelliteId, target.impact(), player);
        if (result == MisakaOrbitalStrikeSupport.BeginResult.OK) {
            MisakaRelayRegistry.get(server).get(satelliteId).strikeTerrainTarget = target.terrain();
        }
        clear(player);
        player.sendOverlayMessage(Component.translatable(MisakaOrbitalStrikeSupport.messageKey(result)));
        return result == MisakaOrbitalStrikeSupport.BeginResult.OK;
    }

    /**
     * End-of-tick hook from device window flush. Charge is applied from
     * {@link MisakaComputeAccess#consumeRate}'s return value inside {@link #tickCharge}.
     */
    public static void finalizeTickGrant(ServerPlayer player) {
        // no-op — hold-use applies grants synchronously from consumeRate.
    }

    public static void onReleased(ServerPlayer player) {
        var session = SESSIONS.remove(player.getUUID());
        sync(player, false, 0.0f, chargeNeed(player.level().getServer()), 0.0f, "");
        if (session != null && session.charge > 1.0e-3f) {
            player.sendOverlayMessage(Component.translatable("message.academy.laser_designator_charge_cancel"));
        }
    }

    /**
     * Keep the hold session + client HUD active, and pin a status lang key so the client
     * does not replace it with an empty clear packet every tick.
     */
    private static void softBlock(ServerPlayer player, Session session, float need, String statusKey) {
        var server = player.level().getServer();
        if (server != null) {
            session.touch(server.getTickCount());
        }
        sync(player, true, session.charge, need, 0.0f, statusKey);
    }

    /**
     * Ensure ActiveNetwork matches the satellite network (auto-switch, no cooldown),
     * when the player has satellite-use permission.
     *
     * @return true if the active network was changed this call
     */
    private static boolean ensureActiveNetwork(ServerPlayer player, UUID networkId, long gameTime) {
        if (isActiveNetwork(player, networkId)) {
            return false;
        }
        var governance = MisakaNetworkGovernance.get(player.level().getServer());
        if (!governance.hasPermissionOrOpen(
                player,
                networkId,
                MisakaNetworkPermission.SATELLITE_USE,
                MisakaNetworkPermission.SATELLITE_MANAGE,
                MisakaNetworkPermission.ACCESS,
                MisakaNetworkPermission.ADMIN
        )) {
            return false;
        }
        MisakaActiveNetworkData.get(player.level().getServer())
                .forceSetActive(player.getUUID(), networkId, gameTime);
        return true;
    }

    private static boolean isActiveNetwork(ServerPlayer player, UUID networkId) {
        var active = MisakaActiveNetworkData.get(player.level().getServer()).getActive(player).orElse(null);
        return networkId.equals(active);
    }

    private static float hudRate(MisakaComputeAccess access, ServerPlayer player, float rateCap) {
        float budget = Math.max(0.0f, access.playerBudgetMskPerSecond(player));
        int percent = Math.max(0, access.effectivePercent(player, DESIGNATOR_ID));
        float share = budget * (percent / 100.0f);
        if (!(share > 0.0f)) {
            return 0.0f;
        }
        return Math.min(rateCap, share);
    }

    private static void sync(
            ServerPlayer player,
            boolean active,
            float charge,
            float need,
            float rate,
            String statusKey
    ) {
        MisakaNetworkServer.send(player, new MisakaOrbitalChargePacket(active, charge, need, rate, statusKey));
    }

    private static Session session(ServerPlayer player) {
        return SESSIONS.computeIfAbsent(player.getUUID(), ignored -> new Session());
    }

    @FunctionalInterface
    public interface InteractionContext {
        @Nullable StrikeTarget resolveTarget(ServerPlayer player);
    }

    private static final class Session {
        float charge;
        int lastTick = Integer.MIN_VALUE;

        void touch(int tick) {
            lastTick = tick;
        }
    }

    public record StrikeTarget(BlockPos impact, boolean terrain) {}

    public static @Nullable StrikeTarget clipTarget(ServerPlayer player, double range) {
        var eye = player.getEyePosition();
        var look = player.getViewVector(1.0f);
        var end = eye.add(look.scale(range));
        var target = org.academy.api.server.ability.WorldBlockTargets.clip(
                player, eye, end, ClipContext.Block.OUTLINE);
        if (!target.hit()) return null;
        boolean structure = target.source() == org.academy.api.server.ability.WorldBlockTargets.Source.STRUCTURE;
        // Step just inside the hit surface. Structure points remain world-space effects,
        // never addresses for editing the host terrain beneath a moving structure.
        return new StrikeTarget(BlockPos.containing(target.position().add(look.scale(1.0E-4))), !structure);
    }
}
