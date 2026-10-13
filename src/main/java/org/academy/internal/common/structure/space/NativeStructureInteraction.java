package org.academy.internal.common.structure.space;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.neoforged.neoforge.common.NeoForge;
import org.academy.api.common.damage.AbilityHitEffects;
import org.academy.api.common.structure.*;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.mentalout.control.MentalControlRuntime;
import org.academy.internal.common.ability.mentalout.control.PlayerControlSessionManager;
import org.academy.internal.common.structure.NativeStructureRuntime;
import org.academy.internal.server.time.TemporalRuntime;
import org.academy.mixin.common.MixinServerGamePacketListenerNativeStructureAccess;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.WeakHashMap;

/** Explicit vanilla adapters; the real actor's level always remains the host world. */
public final class NativeStructureInteraction {
    public enum Result { APPLIED, DENIED, STALE, UNSUPPORTED, BUSY }
    private static final WeakHashMap<ServerPlayer, Integer> LAST_USE = new WeakHashMap<>();
    private static final Set<StructureHandle> ACTING = Collections.newSetFromMap(new IdentityHashMap<>());

    private NativeStructureInteraction() {}

    public static Result useLever(ServerPlayer player, StructureHit proposed) {
        return use(player, proposed, InteractionHand.MAIN_HAND, true);
    }

    public static Result useBlock(ServerPlayer player, StructureHit proposed) {
        return useBlock(player, proposed, InteractionHand.MAIN_HAND);
    }

    public static Result useBlock(ServerPlayer player, StructureHit proposed, InteractionHand hand) {
        return use(player, proposed, hand, false);
    }

    private static Result use(ServerPlayer player, StructureHit proposed, InteractionHand hand, boolean leverOnly) {
        var server = player.level().getServer();
        if (!server.isSameThread()) throw new IllegalStateException("Structure interaction requires server thread");
        if (!canUseMenu(player) || leverOnly && !emptyHands(player)) return Result.DENIED;
        synchronized (LAST_USE) {
            int tick = server.getTickCount();
            var last = LAST_USE.put(player, tick);
            if (last != null && tick - last < 4) return Result.BUSY;
        }
        var runtime = NativeStructureRuntime.backend(server);
        var handle = runtime.resolve(proposed.block().structure()).orElse(null);
        if (handle == null || !runtime.isPresented(handle.reference())) return Result.STALE;
        synchronized (ACTING) { if (!ACTING.add(handle)) return Result.BUSY; }
        try {
            var observation = NativeStructurePlayerTarget.capture(player, proposed);
            if (observation == null) return Result.STALE;
            var initial = observation.hit();
            var storage = runtime.storageLevel(handle.reference()).orElseThrow();
            var initialState = storage.getBlockState(initial.block().localPosition());
            var selection = NativeStructurePlacementPolicy.selectAction(player.getMainHandItem(),
                    player.getOffhandItem(), player.isSecondaryUseActive(), initialState);
            if (selection == null || selection.hand() != hand) return Result.UNSUPPORTED;
            if (!leverOnly && selection.action() == NativeStructurePlacementPolicy.Action.PLACE) {
                return NativeStructurePlacement.place(player, initial, hand,
                        observation::revalidate) ? Result.APPLIED : Result.DENIED;
            }
            var action = initialState.is(Blocks.LEVER) ? StructureInteractionEvent.Action.USE_LEVER
                    : !leverOnly && initialState.is(Blocks.CHEST) ? StructureInteractionEvent.Action.OPEN_CONTAINER : null;
            if (action == null) return Result.UNSUPPORTED;
            boolean denied = StructureContexts.call(handle, context ->
                    NeoForge.EVENT_BUS.post(new StructureInteractionEvent(player, initial, context, action)).isCanceled());
            if (denied || !canUseMenu(player) || leverOnly && !emptyHands(player)) return Result.DENIED;
            // Protection listeners may move the actor/structure or change blocks synchronously.
            // The accepted current hit remains pinned: a listener cannot adopt another pose or target.
            if (!runtime.isPresented(handle.reference())) return Result.STALE;
            var current = observation.revalidate();
            if (current == null) return Result.STALE;
            var position = current.block().localPosition();
            var state = storage.getBlockState(position);
            // Protection callbacks may change the held item or secondary-use flag without a block update.
            if (!selection.equals(NativeStructurePlacementPolicy.selectAction(player.getMainHandItem(),
                    player.getOffhandItem(), player.isSecondaryUseActive(), state))) return Result.DENIED;
            if (action == StructureInteractionEvent.Action.OPEN_CONTAINER) {
                boolean[] opened = { false };
                boolean ran = runtime.runMenuInteraction(handle.reference(), () -> StructureContexts.run(handle,
                        context -> opened[0] = NativeStructureChestSessions.open(player, current)));
                return ran && opened[0] ? Result.APPLIED : Result.DENIED;
            }
            if (!state.is(Blocks.LEVER)) return Result.UNSUPPORTED;
            var host = player.level();
            if (!runtime.runInteraction(current.block().structure(), () -> StructureContexts.run(handle,
                    context -> ((LeverBlock) Blocks.LEVER).pull(state, storage, position, null)))) return Result.DENIED;
            var point = current.worldPoint();
            host.playSound(null, point.x, point.y, point.z, SoundEvents.LEVER_CLICK,
                    SoundSource.BLOCKS, 0.3F, state.getValue(LeverBlock.POWERED) ? 0.5F : 0.6F);
            return Result.APPLIED;
        } finally {
            synchronized (ACTING) { ACTING.remove(handle); }
        }
    }

    private static boolean emptyHands(ServerPlayer player) {
        return player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty();
    }

    /** Persistent menu validity excludes per-action time scaling and hand contents. */
    public static boolean canMaintainMenu(ServerPlayer player) {
        // Custom payloads bypass the vanilla packet injections. Keep the same control,
        // paralysis and temporal action-tick gates as MixinServerGamePacketListenerImpl.
        // Vanilla also refuses block use before client loading/teleport acknowledgement.
        if (!player.isAlive() || player.isRemoved() || player.isSpectator() || player.isPassenger() || !player.getAbilities().mayBuild
                || player.connection == null || !player.connection.hasClientLoaded()
                || ((MixinServerGamePacketListenerNativeStructureAccess) player.connection)
                        .academy$awaitingPositionFromClient() != null
                || PlayerControlSessionManager.blocksUntrustedWorldAction(player)
                || MentalControlRuntime.isFrozen(player) || AbilityHitEffects.isParalyzed(player)) return false;
        return true;
    }

    public static boolean canUseMenu(ServerPlayer player) {
        if (!canMaintainMenu(player)) return false;
        var context = (MinecraftServerContext) player.level().getServer();
        return !context.hasAcademyCraftServer() || ((TemporalRuntime) context.getAcademyCraftServer()
                .getTemporalService()).isPlayerActionTick(player);
    }
}
