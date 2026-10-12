package org.academy.internal.common.structure.space;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.phys.AABB;
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
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;

/** Explicit vanilla adapters; never lend a host player to a foreign Level. */
public final class NativeStructureInteraction {
    public enum Result { APPLIED, DENIED, STALE, UNSUPPORTED, BUSY }
    private static final WeakHashMap<ServerPlayer, Integer> LAST_USE = new WeakHashMap<>();
    private static final Set<StructureHandle> ACTING = Collections.newSetFromMap(new IdentityHashMap<>());

    private NativeStructureInteraction() {}

    public static Result useLever(ServerPlayer player, StructureHit proposed) {
        return use(player, proposed, true);
    }

    public static Result useBlock(ServerPlayer player, StructureHit proposed) {
        return use(player, proposed, false);
    }

    private static Result use(ServerPlayer player, StructureHit proposed, boolean leverOnly) {
        var server = player.level().getServer();
        if (!server.isSameThread()) throw new IllegalStateException("Structure interaction requires server thread");
        if (!allowed(player)) return Result.DENIED;
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
            var initial = valid(player, proposed).orElse(null);
            if (initial == null) return Result.STALE;
            var storage = runtime.storageLevel(handle.reference()).orElseThrow();
            var initialState = storage.getBlockState(initial.block().localPosition());
            var action = initialState.is(Blocks.LEVER) ? StructureInteractionEvent.Action.USE_LEVER
                    : !leverOnly && initialState.is(Blocks.CHEST) ? StructureInteractionEvent.Action.OPEN_CONTAINER : null;
            if (action == null) return Result.UNSUPPORTED;
            boolean denied = StructureContexts.call(handle, context ->
                    NeoForge.EVENT_BUS.post(new StructureInteractionEvent(player, initial, context, action)).isCanceled());
            if (denied || !allowed(player)) return Result.DENIED;
            // Protection listeners may move the actor/structure or change blocks synchronously.
            // The original version remains the expectation: a listener cannot replace the target.
            if (!runtime.isPresented(handle.reference())) return Result.STALE;
            var current = valid(player, proposed).orElse(null);
            if (current == null) return Result.STALE;
            var position = current.block().localPosition();
            var state = storage.getBlockState(position);
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

    private static boolean allowed(ServerPlayer player) {
        return canUseMenu(player) && player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty();
    }

    /** Persistent menu validity excludes per-action time scaling and hand contents. */
    public static boolean canMaintainMenu(ServerPlayer player) {
        // Custom payloads bypass the vanilla packet injections. Keep the same control,
        // paralysis and temporal action-tick gates as MixinServerGamePacketListenerImpl.
        // Vanilla also refuses block use before client loading/teleport acknowledgement.
        if (!player.isAlive() || player.isRemoved() || player.isSpectator() || !player.getAbilities().mayBuild
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

    private static Optional<StructureHit> valid(ServerPlayer player, StructureHit proposed) {
        double range = player.blockInteractionRange();
        if (!Double.isFinite(range) || range <= 0 || range > StructureQueries.MAX_RAY_LENGTH) return Optional.empty();
        var from = player.getEyePosition();
        var to = from.add(player.getLookAngle().scale(range));
        var host = player.level();
        var hit = StructureQueries.validate(host, from, to, proposed).currentHit().orElse(null);
        if (hit == null) return Optional.empty();
        if (!host.mayInteract(player, BlockPos.containing(hit.worldPoint()))
                || !host.getWorldBorder().isWithinBounds(BlockPos.containing(hit.worldPoint()))) return Optional.empty();
        double distance = from.distanceToSqr(hit.worldPoint());
        for (var entity : host.getEntities(player, new AABB(from, to).inflate(1),
                candidate -> !candidate.isSpectator() && candidate.isPickable())) {
            var box = entity.getBoundingBox().inflate(entity.getPickRadius());
            if (box.contains(from) || box.clip(from, to).filter(point -> from.distanceToSqr(point) <= distance).isPresent()) {
                return Optional.empty();
            }
        }
        return Optional.of(hit);
    }
}
