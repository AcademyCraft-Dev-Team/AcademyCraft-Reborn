package org.academy.internal.client.structure;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.StructureHit;
import org.academy.internal.client.ability.mentalout.MentalIntrusionClientState;
import org.academy.internal.client.ability.mentalout.PlayerControlClientState;
import org.academy.internal.common.network.NativeStructureMinePacket;
import org.academy.internal.common.network.NativeStructureMiningProgressPacket;
import org.academy.internal.common.network.PlayerLeftClickSwingPacket;
import org.academy.internal.common.structure.space.NativeStructureMiningPolicy;
import org.academy.internal.common.world.entity.structure.NativeStructureProjectionEntity;
import org.jspecify.annotations.Nullable;
import org.misaka.MisakaNetworkClient;

import java.util.HashMap;
import java.util.Map;

/** Main-hand intent and server-authored cracks. Never predicts a block removal in either world. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class NativeStructureMiningClient {
    private static final boolean SMOKE = Boolean.getBoolean("academy.nativeStructureMiningSmoke");
    private static final NativeStructureMiningUpdates UPDATES = new NativeStructureMiningUpdates();
    private static @Nullable ClientLevel level;
    private static @Nullable Session session;
    private static long nextRequestId = 1, ticks, lastInputTick = -1, cooldownUntil;
    private static long lastStartedRequest;
    private static @Nullable StructureHit lastStartedHit;
    private static NativeStructureMiningProgressPacket.@Nullable Status lastStatus;
    private static int lastStage = -1;

    private NativeStructureMiningClient() {}

    /** True consumes Minecraft.startAttack; its boolean return must remain false for a held action. */
    public static boolean startAttack(Minecraft mc, int missTime) {
        synchronizeLevel(mc);
        var picked = pick(mc);
        if (picked == null) { abort(mc); return false; }
        mc.gameMode.stopDestroyBlock();
        if (!allowed(mc, missTime) || !eligible(mc, picked)) { abort(mc); return true; }
        if (ticks < cooldownUntil || lastInputTick == ticks) return true;
        lastInputTick = ticks;
        if (!click(mc)) { abort(mc); return true; }
        if (!allowed(mc, missTime) || !eligible(mc, picked)) { abort(mc); return true; }
        if (session != null && matches(mc, picked)) return true;
        begin(mc, picked);
        return true;
    }

    /** True occludes ordinary world/entity attacks even when this structure cannot be mined. */
    public static boolean continueAttack(Minecraft mc, boolean down, int missTime) {
        synchronizeLevel(mc);
        if (!down) { abort(mc); return false; }
        var picked = pick(mc);
        if (picked == null) { abort(mc); return false; }
        mc.gameMode.stopDestroyBlock();
        if (!allowed(mc, missTime) || !eligible(mc, picked)) { abort(mc); return true; }
        if (session != null && !matches(mc, picked)) abort(mc);
        if (ticks < cooldownUntil || lastInputTick == ticks) return true;
        lastInputTick = ticks;
        if (!click(mc)) { abort(mc); return true; }
        if (!allowed(mc, missTime) || !eligible(mc, picked)) { abort(mc); return true; }
        if (session == null) {
            begin(mc, picked);
        } else if (!session.stopSent) {
            if (session.ready) {
                send(mc, NativeStructureMinePacket.Action.STOP, session.requestId, null);
                session.stopSent = true;
            } else {
                send(mc, NativeStructureMinePacket.Action.HOLD, session.requestId, null);
            }
        }
        return true;
    }

    private static void begin(Minecraft mc, NativeStructureRaycast.Hit picked) {
        abort(mc);
        if (nextRequestId == Long.MAX_VALUE) return;
        // A previously targeted host block must receive its ordinary ABORT first.
        mc.gameMode.stopDestroyBlock();
        var hit = picked.structureHit();
        session = new Session(nextRequestId++, hit, mc.player.getInventory().getSelectedSlot(),
                mc.player.getMainHandItem().copy(), Block.getId(picked.projection().view().getBlockState(picked.localPosition())), ticks);
        lastStartedRequest = session.requestId;
        lastStartedHit = hit;
        lastStatus = null;
        lastStage = -1;
        UPDATES.clearOwnVisual();
        send(mc, NativeStructureMinePacket.Action.START, session.requestId, hit);
    }

    private static boolean click(Minecraft mc) {
        var event = ClientHooks.onClickInput(0, mc.options.keyAttack, InteractionHand.MAIN_HAND);
        if (event.shouldSwingHand()) {
            mc.player.swing(InteractionHand.MAIN_HAND);
            // The existing Minecraft swing injection cannot observe a call made by this helper.
            MisakaNetworkClient.send(PlayerLeftClickSwingPacket.INSTANCE);
        }
        return !event.isCanceled();
    }

    private static void send(Minecraft mc, NativeStructureMinePacket.Action action, long requestId, @Nullable StructureHit hit) {
        if (action != NativeStructureMinePacket.Action.ABORT) {
            var player = mc.player;
            player.connection.send(new ServerboundSetCarriedItemPacket(player.getInventory().getSelectedSlot()));
            // Key processing precedes LocalPlayer.sendPosition; publish this frame's actual view.
            player.connection.send(new ServerboundMovePlayerPacket.Rot(player.getYRot(), player.getXRot(),
                    player.onGround(), player.horizontalCollision));
        }
        MisakaNetworkClient.send(new NativeStructureMinePacket(action, requestId, hit));
        if (SMOKE) AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_MINING_CLIENT_INPUT action={} request={} tick={}", action, requestId, ticks);
    }

    public static void abort(Minecraft mc) {
        var previous = session;
        session = null;
        if (previous != null && mc.player != null && mc.level == level && mc.player.connection != null) {
            send(mc, NativeStructureMinePacket.Action.ABORT, previous.requestId, null);
            UPDATES.clearOwnVisual();
            lastStatus = NativeStructureMiningProgressPacket.Status.ABORTED;
            lastStage = -1;
        }
    }

    public static boolean isActive() { return session != null; }

    @SubscribeEvent
    public static void beginTick(ClientTickEvent.Pre event) {
        synchronizeLevel(Minecraft.getInstance());
        ticks++;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        synchronizeLevel(mc);
        UPDATES.active(ticks);
        if (session == null) return;
        if (mc.player == null || !allowed(mc, 0) || !mc.options.keyAttack.isDown()
                || !mc.mouseHandler.isMouseGrabbed() || ticks - session.lastUpdateTick > 100) {
            abort(mc);
            return;
        }
        var picked = pick(mc);
        if (picked == null || !matches(mc, picked)) abort(mc);
    }

    public static void accept(NativeStructureMiningProgressPacket packet) {
        var mc = Minecraft.getInstance();
        synchronizeLevel(mc);
        if (mc.level == null || mc.player == null
                || !packet.reference().dimension().equals(mc.level.dimension().identifier())
                || Block.getId(Block.stateById(packet.stateId())) != packet.stateId()) return;
        boolean own = packet.breakerEntityId() == mc.player.getId();
        boolean lastRequest = own && packet.requestId() == lastStartedRequest && lastStartedHit != null
                && packet.reference().equals(lastStartedHit.block().structure())
                && packet.runtimeId().equals(lastStartedHit.runtimeId())
                && packet.localPosition().equals(lastStartedHit.block().localPosition());
        boolean current = lastRequest && session != null && packet.requestId() == session.requestId;
        if (!own && mc.level.getEntity(packet.breakerEntityId()) == null) return;
        if (!(own ? UPDATES.acceptOwn(packet, ticks, current) : UPDATES.accept(packet, ticks, true))) return;
        // Vanilla view updates can arrive before this ACK and locally cancel an already-finished
        // target. Its terminal acknowledgement still owns the cooldown, but cannot revive it.
        boolean terminal = packet.status() == NativeStructureMiningProgressPacket.Status.COMPLETED
                || packet.status() == NativeStructureMiningProgressPacket.Status.ABORTED
                || packet.status() == NativeStructureMiningProgressPacket.Status.REJECTED;
        if (!current && !(lastRequest && terminal)) return;
        if (session != null) session.lastUpdateTick = ticks;
        lastStatus = packet.status();
        lastStage = packet.stage();
        switch (packet.status()) {
            case STARTED, PROGRESS -> { }
            case READY -> session.ready = true;
            case COMPLETED, ABORTED, REJECTED -> {
                session = null;
                cooldownUntil = ticks + 5;
            }
        }
    }

    /** At most one greatest server-authored crack stage per local cell; no host-coordinate map. */
    public static Map<BlockPos, Integer> cracks(NativeStructureProjectionEntity projection) {
        var result = new HashMap<BlockPos, Integer>();
        for (var packet : UPDATES.active(ticks)) {
            if (!packet.reference().equals(projection.viewState().reference())
                    || !packet.runtimeId().equals(projection.viewState().runtimeId())
                    || Block.getId(projection.view().getBlockState(packet.localPosition())) != packet.stateId()) continue;
            result.merge(packet.localPosition(), packet.stage(), Math::max);
        }
        return result;
    }

    private static NativeStructureRaycast.@Nullable Hit pick(Minecraft mc) {
        return mc.level == null || mc.player == null || mc.gameMode == null ? null
                : NativeStructureRaycast.pick(mc.player, mc.player.blockInteractionRange(), 1).orElse(null);
    }

    private static boolean eligible(Minecraft mc, NativeStructureRaycast.Hit picked) {
        return NativeStructureMiningPolicy.canMine(mc.player.getMainHandItem(),
                picked.projection().view().getBlockState(picked.localPosition()));
    }

    private static boolean allowed(Minecraft mc, int missTime) {
        if (mc.player == null || mc.level == null || mc.gameMode == null || mc.gui.screen() != null
                || missTime > 0 || mc.getCameraEntity() != mc.player || !mc.player.isAlive()
                || mc.player.isSpectator() || !mc.player.getAbilities().mayBuild || mc.player.isHandsBusy()
                || mc.player.isUsingItem() || MentalIntrusionClientState.blocksWorldInteraction()
                || PlayerControlClientState.blocksWorldInteraction(false)) return false;
        var held = mc.player.getMainHandItem();
        return held.isItemEnabled(mc.level.enabledFeatures()) && !held.has(DataComponents.PIERCING_WEAPON)
                && !mc.player.cannotAttackWithItem(held, 0);
    }

    private static boolean matches(Minecraft mc, NativeStructureRaycast.Hit picked) {
        return session != null && session.hit.block().structure().equals(picked.reference())
                && session.hit.runtimeId().equals(picked.runtimeId())
                && session.hit.block().localPosition().equals(picked.localPosition())
                && session.stateId == Block.getId(picked.projection().view().getBlockState(picked.localPosition()))
                && session.slot == mc.player.getInventory().getSelectedSlot()
                && ItemStack.matches(session.tool, mc.player.getMainHandItem());
    }

    private static void synchronizeLevel(Minecraft mc) {
        if (level == mc.level) return;
        level = mc.level;
        session = null;
        UPDATES.clear();
        lastInputTick = -1;
        cooldownUntil = ticks;
        lastStatus = null;
        lastStage = -1;
        lastStartedRequest = 0;
        lastStartedHit = null;
    }

    public static long currentRequestId() { return SMOKE && session != null ? session.requestId : 0; }
    public static NativeStructureMiningProgressPacket.@Nullable Status currentStatus() { return SMOKE ? lastStatus : null; }
    public static int currentStage() { return SMOKE ? lastStage : -1; }
    public static @Nullable BlockPos activeLocalPos() { return SMOKE && session != null ? session.hit.block().localPosition() : null; }

    private static final class Session {
        final long requestId;
        final StructureHit hit;
        final int slot, stateId;
        final ItemStack tool;
        long lastUpdateTick;
        boolean ready, stopSent;

        Session(long requestId, StructureHit hit, int slot, ItemStack tool, int stateId, long tick) {
            this.requestId = requestId;
            this.hit = hit;
            this.slot = slot;
            this.tool = tool;
            this.stateId = stateId;
            lastUpdateTick = tick;
        }
    }
}
