package org.academy.internal.client.sublevel;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.phys.Vec3;
import org.academy.internal.client.ability.mentalout.MentalIntrusionClientState;
import org.academy.internal.client.ability.mentalout.PlayerControlClientState;
import org.academy.internal.common.network.SubLevelMiningPacket;
import org.academy.internal.common.network.SubLevelInteractPacket;
import org.jspecify.annotations.Nullable;

/**
 * Client input for exact SubLevel snapshots. Mining uses a bounded request id and a heartbeat;
 * the server owns progress, tool wear and drops. Use/place keep their original routing.
 */
public final class SubLevelClientInteraction {
    private static final double REACH_PADDING = 0.5;
    private static final long HEARTBEAT_TICKS = 4L;
    private static long nextRequestId = 1;
    private static @Nullable Session session;
    private static long lastSentTick;

    private SubLevelClientInteraction() {
    }

    public static boolean use(Minecraft minecraft) {
        var hit = hit(minecraft);
        if (hit == null) return false;
        var stack = minecraft.player.getMainHandItem();
        var action = stack.getItem() instanceof BlockItem && !minecraft.player.isSecondaryUseActive()
                ? SubLevelInteractPacket.Action.PLACE : SubLevelInteractPacket.Action.USE;
        SubLevelInteractPacket.send(new SubLevelInteractPacket(hit.id(), hit.localPosition(),
                hit.face(), hit.worldPoint(), InteractionHand.MAIN_HAND, action));
        return true;
    }

    /** Starts or renews a server-authoritative mining session for the picked structure. */
    public static boolean attack(Minecraft minecraft) {
        var hit = hit(minecraft);
        if (hit == null) {
            abort(minecraft);
            return false;
        }
        if (session != null && session.matches(hit)) {
            heartbeat(minecraft, hit, SubLevelMiningPacket.Action.CONTINUE);
            return true;
        }
        abort(minecraft);
        // A structure hit always occludes the ordinary block path, even when this actor cannot
        // mine yet; otherwise vanilla could break a host block behind the structure.
        if (!allowed(minecraft)) return true;
        if (nextRequestId == Long.MAX_VALUE) return true;
        session = new Session(nextRequestId++, hit);
        send(minecraft, SubLevelMiningPacket.Action.START);
        lastSentTick = gameTime(minecraft);
        return true;
    }

    /** Held-attack heartbeat; returns false so the vanilla path may run when no session is active. */
    public static boolean continueAttack(Minecraft minecraft, boolean down) {
        if (!down) {
            abort(minecraft);
            return false;
        }
        var hit = hit(minecraft);
        if (hit == null) {
            abort(minecraft);
            return false;
        }
        if (session == null || !session.matches(hit)) {
            abort(minecraft);
            attack(minecraft);
            return true;
        }
        if (!allowed(minecraft)) return true;
        if (gameTime(minecraft) - lastSentTick < HEARTBEAT_TICKS) return true;
        heartbeat(minecraft, hit, SubLevelMiningPacket.Action.CONTINUE);
        return true;
    }

    public static boolean isMining() {
        return session != null;
    }

    /** Client tick guard: a menu, death, disconnect or target loss ends the session promptly. */
    public static void tick(Minecraft minecraft) {
        if (session == null) return;
        if (!allowed(minecraft) || hit(minecraft) == null) abort(minecraft);
    }

    private static void heartbeat(Minecraft minecraft, SubLevelClientState.Hit hit,
                                  SubLevelMiningPacket.Action action) {
        if (session == null) return;
        session.face = hit.face();
        session.point = hit.worldPoint();
        session.local = hit.localPosition();
        send(minecraft, action);
        lastSentTick = gameTime(minecraft);
    }

    private static void send(Minecraft minecraft, SubLevelMiningPacket.Action action) {
        if (session == null) return;
        if (minecraft.player == null || minecraft.level == null) return;
        SubLevelMiningPacket.send(new SubLevelMiningPacket(action, session.requestId, session.id, session.local,
                session.face, session.point, InteractionHand.MAIN_HAND, session.inside));
    }

    public static void abort(Minecraft minecraft) {
        var previous = session;
        session = null;
        if (previous == null || minecraft == null || minecraft.player == null || minecraft.level == null) return;
        SubLevelMiningPacket.send(new SubLevelMiningPacket(SubLevelMiningPacket.Action.ABORT, previous.requestId,
                previous.id, previous.local, previous.face, previous.point, InteractionHand.MAIN_HAND, previous.inside));
    }

    private static boolean allowed(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null || minecraft.gameMode == null
                || minecraft.gui.screen() != null || minecraft.getCameraEntity() != minecraft.player
                || !minecraft.player.isAlive() || minecraft.player.isSpectator() || minecraft.player.isPassenger()
                || !minecraft.player.getAbilities().mayBuild || minecraft.player.isHandsBusy()
                || minecraft.player.isUsingItem() || MentalIntrusionClientState.blocksWorldInteraction()
                || PlayerControlClientState.blocksWorldInteraction(false)) {
            return false;
        }
        var held = minecraft.player.getMainHandItem();
        return held.isItemEnabled(minecraft.level.enabledFeatures())
                && !minecraft.player.cannotAttackWithItem(held, 0);
    }

    private static long gameTime(Minecraft minecraft) {
        return minecraft.level == null ? 0L : minecraft.level.getGameTime();
    }

    private static SubLevelClientState.@Nullable Hit hit(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null) return null;
        var from = minecraft.player.getEyePosition();
        var to = from.add(minecraft.player.getViewVector(1.0F)
                .scale(minecraft.player.blockInteractionRange() + REACH_PADDING));
        return SubLevelClientState.raycast(minecraft.level, from, to);
    }

    private static final class Session {
        private final long requestId;
        private final org.academy.internal.common.sublevel.SubLevelId id;
        private BlockPos local;
        private Direction face;
        private Vec3 point;
        private boolean inside;

        private Session(long requestId, SubLevelClientState.Hit hit) {
            this.requestId = requestId;
            this.id = hit.id();
            this.local = hit.localPosition();
            this.face = hit.face();
            this.point = hit.worldPoint();
        }

        private boolean matches(SubLevelClientState.Hit hit) {
            return id.equals(hit.id()) && local.equals(hit.localPosition());
        }
    }
}
