package org.academy.internal.server.misaka;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.academy.internal.common.network.misaka.MisakaOrbitalChargePacket;
import org.academy.internal.server.world.level.storage.MisakaRelayRegistry;
import org.jspecify.annotations.Nullable;
import org.misaka.MisakaNetworkServer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hold-to-charge orbital strike for the laser designator.
 * Draws from the player's Misaka-allocated compute (as available CP), up to
 * {@code chargeRate} MSk/s, until {@code chargeNeed} is reached. Releasing early discards progress.
 */
public final class MisakaOrbitalCharge {
    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

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
        sync(player, false, 0.0f, chargeNeed(player.level().getServer()), 0.0f);
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

        var gate = MisakaOrbitalStrikeSupport.validateBegin(server, satelliteId, player);
        if (gate != MisakaOrbitalStrikeSupport.BeginResult.OK) {
            player.sendOverlayMessage(Component.translatable(MisakaOrbitalStrikeSupport.messageKey(gate)));
            clear(player);
            return false;
        }

        var entry = MisakaRelayRegistry.get(server).get(satelliteId);
        if (entry == null) {
            clear(player);
            return false;
        }

        var active = MisakaActiveNetworkData.get(server).getActive(player).orElse(null);
        if (active == null || !active.equals(entry.networkId)) {
            player.sendOverlayMessage(Component.translatable("message.academy.laser_designator_wrong_network"));
            clear(player);
            return false;
        }

        float need = chargeNeed(server);
        float rateCap = chargeRate(server);
        String name = player.getGameProfile().name();
        // Claimable share of network supply (friendly sisters), not last-settle demand fill.
        float claimable = MisakaComputeContribution.claimableMskPerSecond(server, name, entry.networkId);
        float rate = Math.min(rateCap, Math.max(0.0f, claimable));
        var session = session(player);
        if (!(rate > 0.0f)) {
            player.sendOverlayMessage(Component.translatable("message.academy.laser_designator_no_alloc"));
            session.touch(server.getTickCount());
            sync(player, true, session.charge, need, 0.0f);
            return false;
        }

        int settleEpoch = server.getTickCount() / MisakaComputeContribution.SETTLE_INTERVAL_TICKS;
        session.rollWindow(settleEpoch);

        float want = Math.min(rate / MisakaComputeContribution.SETTLE_INTERVAL_TICKS, need - session.charge);
        float room = rate - session.drawnThisWindow;
        float take = Math.min(want, Math.max(0.0f, room));
        if (!(take > 1.0e-4f)) {
            sync(player, true, session.charge, need, rate);
            return false;
        }

        // Spend from the player's claimable Misaka budget this settle window (demand ledger),
        // not from last-second CP fill — idle friendly supply must still be usable.
        MisakaComputeUsageTracker.addChargeUsage(player.getUUID(), take);
        session.charge += take;
        session.drawnThisWindow += take;
        session.touch(server.getTickCount());
        sync(player, true, session.charge, need, rate);

        if (session.charge + 1.0e-3f < need) {
            return false;
        }

        BlockPos target = aim.resolveTarget(player);
        if (target == null) {
            player.sendOverlayMessage(Component.translatable("message.academy.laser_designator_no_target"));
            clear(player);
            return false;
        }

        var result = MisakaRelayRegistry.get(server)
                .beginOrbitalStrike(server, satelliteId, target, player);
        clear(player);
        player.sendOverlayMessage(Component.translatable(MisakaOrbitalStrikeSupport.messageKey(result)));
        return result == MisakaOrbitalStrikeSupport.BeginResult.OK;
    }

    public static void onReleased(ServerPlayer player) {
        var session = SESSIONS.remove(player.getUUID());
        sync(player, false, 0.0f, chargeNeed(player.level().getServer()), 0.0f);
        if (session != null && session.charge > 1.0e-3f) {
            player.sendOverlayMessage(Component.translatable("message.academy.laser_designator_charge_cancel"));
        }
    }

    private static void sync(ServerPlayer player, boolean active, float charge, float need, float rate) {
        MisakaNetworkServer.send(player, new MisakaOrbitalChargePacket(active, charge, need, rate));
    }

    private static Session session(ServerPlayer player) {
        return SESSIONS.computeIfAbsent(player.getUUID(), ignored -> new Session());
    }

    @FunctionalInterface
    public interface InteractionContext {
        @Nullable BlockPos resolveTarget(ServerPlayer player);
    }

    private static final class Session {
        float charge;
        float drawnThisWindow;
        int settleEpoch = Integer.MIN_VALUE;
        int lastTick = Integer.MIN_VALUE;

        void rollWindow(int epoch) {
            if (epoch != settleEpoch) {
                settleEpoch = epoch;
                drawnThisWindow = 0.0f;
            }
        }

        void touch(int tick) {
            lastTick = tick;
        }
    }

    public static BlockPos clipTarget(ServerPlayer player, double range) {
        var eye = player.getEyePosition();
        var look = player.getViewVector(1.0f);
        var end = eye.add(look.scale(range));
        BlockHitResult hit = player.level().clip(new ClipContext(
                eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player
        ));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        return hit.getBlockPos();
    }
}
