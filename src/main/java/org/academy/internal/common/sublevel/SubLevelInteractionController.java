package org.academy.internal.common.sublevel;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.SpaceBlockRef;
import org.academy.api.common.structure.StructureBlockData;
import org.academy.api.common.structure.StructureContexts;
import org.academy.api.common.structure.StructureHandle;
import org.academy.api.common.structure.StructureHit;
import org.academy.api.common.structure.StructureInteractionController;
import org.academy.api.common.structure.StructureMiningEvent;
import org.academy.api.common.structure.StructurePlacementEvent;
import org.academy.api.common.structure.StructureRef;
import org.academy.internal.common.structure.space.NativeStructureBreakDrops;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Server-authoritative interaction sessions for plot-backed structures. Mining is clocked only by
 * server ticks; progress, tool wear, loot and experience are owned here. Placement and use are
 * mapped into the shared plot space, while every receipt and effect stays in the visible host.
 */
public final class SubLevelInteractionController implements StructureInteractionController {
    private static final long LEASE_TICKS = 10L;
    private static final long PROGRESS_HEARTBEAT_TICKS = 20L;
    private static final double OBSERVER_RANGE_SQUARED = 64.0 * 64.0;
    private static final int MAX_OBSERVERS = 24;
    private static final int MAX_HOST_CELLS = 64;

    private final Map<MinecraftServer, ServerSessions> servers = new WeakHashMap<>();
    private long generatedRequestId;

    /** Server-assigned id for legacy one-shot requests that carry no monotonic request number. */
    public synchronized long nextRequestId() {
        return generatedRequestId = Math.max(generatedRequestId + 1, 1);
    }

    @Override
    public MiningReceipt mine(ServerPlayer actor, MiningRequest request) {
        requireActor(actor);
        if (request == null || request.requestId() <= 0) {
            return rejected(0, "invalid_request");
        }
        var server = actor.level().getServer();
        var state = servers(server);
        var current = state.active.get(actor);
        if (request.action() == MiningAction.ABORT) {
            if (current == null || current.requestId != request.requestId()) {
                return new MiningReceipt(request.requestId(), 0, null, MiningStatus.ABORTED, 0, -1, "no_session");
            }
            end(current, MiningStatus.ABORTED, null);
            return terminalReceipt(current, MiningStatus.ABORTED);
        }
        if (request.action() == MiningAction.CONTINUE) {
            if (current == null || current.requestId != request.requestId()) {
                return new MiningReceipt(request.requestId(), 0, null, MiningStatus.ABORTED, 0, -1, "no_session");
            }
            if (!current.maintainable(actor)) {
                end(current, MiningStatus.ABORTED, null);
                return terminalReceipt(current, MiningStatus.ABORTED);
            }
            current.hold(server.getTickCount());
            if (request.observation() != null) current.observation = request.observation();
            return receipt(current, current.progress > 0 ? MiningStatus.PROGRESS : MiningStatus.STARTED,
                    current.stage);
        }
        long highest = state.highest.getOrDefault(actor, 0L);
        if (request.requestId() <= highest) {
            return new MiningReceipt(request.requestId(), 0, null, MiningStatus.REJECTED, 0, -1, "stale_request");
        }
        state.highest.put(actor, request.requestId());
        if (current != null) end(current, MiningStatus.ABORTED, null);
        if (!SubLevelMiningRules.canUseMenu(actor)) {
            return new MiningReceipt(request.requestId(), 0, null, MiningStatus.REJECTED, 0, -1, "actor_busy");
        }
        var hit = validateTarget(actor, request.observation());
        if (hit == null) {
            return new MiningReceipt(request.requestId(), 0, null, MiningStatus.REJECTED, 0, -1, "target_invalid");
        }
        var runtime = SubLevelServer.runtime(actor.level()).orElse(null);
        var id = SubLevelStructureRefs.id(hit.block().structure()).orElse(null);
        var subLevel = runtime == null || id == null ? null : runtime.get(id).orElse(null);
        StructureHandle handle = SubLevelQueries.resolve(server, hit.block().structure()).orElse(null);
        if (runtime == null || subLevel == null || handle == null) {
            return new MiningReceipt(request.requestId(), 0, null, MiningStatus.REJECTED, 0, -1, "structure_unavailable");
        }
        var plot = subLevel.plot();
        var local = hit.block().localPosition();
        var plotPosition = runtime.storage().worldPosition(plot, local);
        var blockEntity = runtime.storage().blockEntity(plot, local);
        var blockState = runtime.storage().blockState(plot, local);
        if (!SubLevelMiningRules.canMine(actor, runtime.storage().level(), plotPosition, blockState, blockEntity)) {
            return new MiningReceipt(request.requestId(), 0, null, MiningStatus.REJECTED, 0, -1, "not_mineable");
        }
        StructureBlockData before;
        try {
            before = handle.world().read(local);
        } catch (IllegalArgumentException | IllegalStateException unavailable) {
            return new MiningReceipt(request.requestId(), 0, null, MiningStatus.REJECTED, 0, -1, "content_unavailable");
        }
        boolean[] allowed = {true};
        StructureContexts.run(handle, context -> {
            var event = new StructureMiningEvent(actor, hit, context, StructureMiningEvent.Phase.START,
                    before, actor.getMainHandItem());
            if (NeoForge.EVENT_BUS.post(event).isCanceled()) allowed[0] = false;
        });
        if (!allowed[0]) {
            return new MiningReceipt(request.requestId(), 0, null, MiningStatus.REJECTED, 0, -1, "protection");
        }
        var session = new Session(actor, runtime, subLevel, hit, request.requestId(), before, blockEntity,
                actor.getMainHandItem(), actor.getInventory().getSelectedSlot(),
                actor.gameMode.getGameModeForPlayer(), server.getTickCount());
        state.active.put(actor, session);
        return receipt(session, MiningStatus.STARTED, 0);
    }

    @Override
    public void tick(MinecraftServer server, Feedback feedback) {
        Objects.requireNonNull(server, "server");
        if (!server.isSameThread()) throw new IllegalStateException("SubLevel interactions require the server thread");
        var state = servers.get(server);
        if (state == null || state.active.isEmpty()) return;
        long now = server.getTickCount();
        for (var session : new ArrayList<>(state.active.values())) {
            var actor = session.actor;
            if (actor.hasDisconnected() || actor.isRemoved() || !actor.isAlive()
                    || actor.level() != session.host || actor.containerMenu != actor.inventoryMenu
                    || actor.isUsingItem() || !actor.connection.isAcceptingMessages()) {
                end(session, MiningStatus.ABORTED, feedback);
                continue;
            }
            if (now - session.lastHoldTick > LEASE_TICKS) {
                end(session, MiningStatus.ABORTED, feedback);
                continue;
            }
            if (!ItemStack.matches(actor.getMainHandItem(), session.tool)
                    || actor.getInventory().getSelectedSlot() != session.slot
                    || actor.gameMode.getGameModeForPlayer() != session.gameType) {
                end(session, MiningStatus.ABORTED, feedback);
                continue;
            }
            var validated = validateTarget(actor, session.observation);
            var runtime = SubLevelServer.runtime(session.host).orElse(null);
            if (validated == null || runtime == null || !session.matches(actor, validated, runtime)) {
                end(session, MiningStatus.ABORTED, feedback);
                continue;
            }
            session.observation = validated;
            if (runtime == null) {
                end(session, MiningStatus.ABORTED, feedback);
                continue;
            }
            var plotPosition = runtime.storage().worldPosition(session.subLevel.plot(), session.local);
            var blockState = runtime.storage().blockState(session.subLevel.plot(), session.local);
            float hardness = blockState.getDestroySpeed(runtime.storage().level(), plotPosition);
            boolean harvest = blockState.canHarvestBlock(runtime.storage().level(), plotPosition, actor);
            double delta = session.gameType == GameType.CREATIVE ? 1.0
                    : SubLevelMiningRules.increment(hardness, actor.getDestroySpeed(blockState, null), harvest);
            double previousProgress = session.progress;
            session.progress = Math.min(1.0, session.progress + Math.max(0.0, delta));
            int stage = Math.min(9, (int) (session.progress * 10.0));
            boolean changed = stage != session.stage;
            session.stage = stage;
            if (session.progress >= 1.0) {
                var terminal = finishMining(actor, session.requestId);
                end(session, terminal.status(), feedback);
            } else if (changed || now - session.lastPublished >= PROGRESS_HEARTBEAT_TICKS) {
                publish(session, session.progress > previousProgress ? MiningStatus.PROGRESS : MiningStatus.STARTED,
                        feedback);
            }
        }
    }

    @Override
    public ActionReceipt place(ServerPlayer actor, ActionRequest request) {
        requireActor(actor);
        if (request == null) return actionRejected(0, "invalid_request");
        if (!SubLevelMiningRules.canUseMenu(actor)) return actionRejected(request.requestId(), "actor_busy");
        var hit = validateTarget(actor, request.observation());
        if (hit == null) return actionRejected(request.requestId(), "target_invalid");
        var server = actor.level().getServer();
        var runtime = SubLevelServer.runtime(actor.level()).orElse(null);
        var id = SubLevelStructureRefs.id(hit.block().structure()).orElse(null);
        var subLevel = runtime == null || id == null ? null : runtime.get(id).orElse(null);
        StructureHandle handle = SubLevelQueries.resolve(server, hit.block().structure()).orElse(null);
        if (runtime == null || subLevel == null || handle == null) return actionRejected(request.requestId(), "structure_unavailable");
        var stack = actor.getItemInHand(request.hand());
        if (!(stack.getItem() instanceof BlockItem blockItem)) return actionRejected(request.requestId(), "not_a_block");
        if (!stack.getItem().isEnabled(actor.level().enabledFeatures())) return actionRejected(request.requestId(), "block_disabled");
        var plot = subLevel.plot();
        var storage = runtime.storage();
        var origin = storage.worldPosition(plot, BlockPos.ZERO);
        var plotPoint = new Vec3(origin.getX() + hit.localPoint().x, hit.localPoint().y, origin.getZ() + hit.localPoint().z);
        var plotPosition = storage.worldPosition(plot, hit.block().localPosition());
        var plotHit = new BlockHitResult(plotPoint, hit.localFace(), plotPosition, hit.inside());
        var changed = new ArrayList<SpaceBlockRef>();
        String[] detail = {"rejected"};
        boolean[] applied = {false};
        try {
            StructureContexts.run(handle, context -> {
                var placement = new SubLevelPlaceContext(storage.level(), actor, request.hand(), stack, plotHit,
                        hit.pose(), origin);
                var targetWorld = placement.getClickedPos();
                BlockPos targetLocal;
                try {
                    targetLocal = storage.localPosition(plot, targetWorld);
                } catch (IllegalArgumentException outside) {
                    detail[0] = "outside_bounds";
                    return;
                }
                if (!subLevel.localBounds().contains(targetLocal.getX() + 0.5, targetLocal.getY() + 0.5,
                        targetLocal.getZ() + 0.5)) {
                    detail[0] = "outside_bounds";
                    return;
                }
                var before = storage.blockState(plot, targetLocal);
                if (!before.canBeReplaced(placement)) {
                    detail[0] = "not_replaceable";
                    return;
                }
                var proposed = blockItem.getBlock().getStateForPlacement(placement);
                if (proposed == null || !proposed.canSurvive(storage.level(), targetWorld)) {
                    detail[0] = "unsupported";
                    return;
                }
                if (!hostAllows(actor, hit.pose(), targetLocal)) {
                    detail[0] = "host_protected";
                    return;
                }
                if (entityBlocksPlacement(actor, hit.pose(), targetLocal)) {
                    detail[0] = "entity_obstructed";
                    return;
                }
                var target = new SpaceBlockRef(hit.block().structure(), targetLocal);
                var event = new StructurePlacementEvent(actor, hit, context, request.hand(), target,
                        new StructureBlockData(before), proposed, stack.copyWithCount(1));
                if (NeoForge.EVENT_BUS.post(event).isCanceled()) {
                    detail[0] = "protection";
                    return;
                }
                var result = blockItem.place(placement);
                if (!result.consumesAction()) {
                    detail[0] = "vanilla_rejected";
                    return;
                }
                actor.awardStat(Stats.ITEM_USED.get(blockItem));
                runtime.refreshGeometry(subLevel.id());
                changed.add(target);
                applied[0] = true;
                detail[0] = "placed";
            });
        } catch (RuntimeException | Error failure) {
            AcademyCraft.LOGGER.error("SubLevel placement failed for {}", hit.block(), failure);
            return new ActionReceipt(request.requestId(), target(hit), ActionStatus.REJECTED, List.of(),
                    false, true, "exception");
        }
        return new ActionReceipt(request.requestId(), target(hit), applied[0] ? ActionStatus.APPLIED : ActionStatus.REJECTED,
                List.copyOf(changed), false, !applied[0], detail[0]);
    }

    @Override
    public ActionReceipt use(ServerPlayer actor, ActionRequest request) {
        requireActor(actor);
        if (request == null) return actionRejected(0, "invalid_request");
        if (!SubLevelMiningRules.canUseMenu(actor)) return actionRejected(request.requestId(), "actor_busy");
        var hit = validateTarget(actor, request.observation());
        if (hit == null) return actionRejected(request.requestId(), "target_invalid");
        var id = SubLevelStructureRefs.id(hit.block().structure()).orElse(null);
        var runtime = SubLevelServer.runtime(actor.level()).orElse(null);
        var subLevel = runtime == null || id == null ? null : runtime.get(id).orElse(null);
        if (runtime == null || subLevel == null) return actionRejected(request.requestId(), "structure_unavailable");
        var result = SubLevelInteractionService.useBlock(actor.level().getServer(), id, hit.block().localPosition(),
                hit.localFace(), hit.worldPoint(), actor, request.hand());
        if (result == InteractionResult.FAIL) return actionRejected(request.requestId(), "use_failed");
        if (!result.consumesAction()) {
            return new ActionReceipt(request.requestId(), target(hit), ActionStatus.PASS, List.of(), false, false, "pass");
        }
        rememberMenu(actor, id, hit.block().localPosition());
        runtime.refreshGeometry(subLevel.id());
        return new ActionReceipt(request.requestId(), target(hit), ActionStatus.APPLIED,
                List.of(new SpaceBlockRef(hit.block().structure(), hit.block().localPosition())),
                false, false, "used");
    }

    @Override
    public void cancelActor(ServerPlayer actor, CancelReason reason, Feedback feedback) {
        if (actor == null) return;
        var server = actor.level().getServer();
        var state = servers.get(server);
        if (state == null) return;
        var session = state.active.get(actor);
        if (session != null) end(session, statusFor(reason), feedback);
        closeMenu(actor, state);
        // Do not retain a disconnected player object through this server's lifetime.
        state.highest.remove(actor);
        state.menus.remove(actor);
    }

    @Override
    public void cancelStructure(MinecraftServer server, StructureRef reference, UUID runtimeId,
                                CancelReason reason, Feedback feedback) {
        if (server == null || reference == null) return;
        var state = servers.get(server);
        if (state == null) return;
        for (var session : new ArrayList<>(state.active.values())) {
            if (session.reference.equals(reference) && session.runtimeId.equals(runtimeId)) {
                end(session, statusFor(reason), feedback);
            }
        }
        SubLevelStructureRefs.id(reference).ifPresent(id -> closeMenusFor(server, id, null));
    }

    @Override
    public void close(MinecraftServer server, Feedback feedback) {
        if (server == null) return;
        var state = servers.remove(server);
        if (state == null) return;
        for (var session : new ArrayList<>(state.active.values())) {
            end(session, MiningStatus.ABORTED, feedback);
        }
        for (var actor : List.copyOf(state.menus.keySet())) {
            if (!actor.hasDisconnected()) closeMenu(actor, state);
        }
        state.active.clear();
        state.highest.clear();
        state.menus.clear();
    }

    /** Used by the packet handler to send the synchronous receipt through the shared feedback. */
    public static void publishSync(Feedback feedback, ServerPlayer actor, MiningReceipt receipt) {
        if (feedback != null && receipt != null) feedback.mining(actor, actor.getUUID(), receipt);
    }

    /**
     * Legacy one-shot BREAK mapping: a repeated hit on the same cell renews the existing session
     * instead of restarting its progress. It never grants an instant break.
     */
    public MiningReceipt continueLegacy(ServerPlayer actor, StructureHit observation) {
        requireActor(actor);
        if (observation == null) return rejected(0, "target_invalid");
        var state = servers(actor.level().getServer());
        var session = state.active.get(actor);
        if (session != null && session.reference.equals(observation.block().structure())
                && session.local.equals(observation.block().localPosition())) {
            return mine(actor, new MiningRequest(session.requestId, MiningAction.CONTINUE, observation));
        }
        return mine(actor, new MiningRequest(nextRequestId(), MiningAction.START, observation));
    }

    private MiningReceipt finishMining(ServerPlayer actor, long requestId) {
        var state = servers(actor.level().getServer());
        var session = state.active.get(actor);
        if (session == null || session.requestId != requestId) return rejected(requestId, "no_session");
        if (session.progress < 1.0) return receipt(session, MiningStatus.PROGRESS, session.stage);
        var runtime = SubLevelServer.runtime(session.host).orElse(null);
        if (runtime == null || !session.maintainable(actor)) {
            return terminalReceipt(session, MiningStatus.REJECTED);
        }
        var validated = validateTarget(actor, session.observation);
        if (validated == null || !session.matches(actor, validated, runtime)) {
            return terminalReceipt(session, MiningStatus.REJECTED);
        }
        var plot = session.subLevel.plot();
        var storage = runtime.storage();
        var plotPosition = storage.worldPosition(plot, session.local);
        var blockEntity = storage.blockEntity(plot, session.local);
        var blockState = storage.blockState(plot, session.local);
        if (!session.before.state().equals(blockState)) {
            // The cell changed since START; never destroy a different block than the authorized one.
            return terminalReceipt(session, MiningStatus.REJECTED);
        }
        var handle = SubLevelQueries.resolve(actor.level().getServer(), validated.block().structure()).orElse(null);
        if (handle == null || !SubLevelMiningRules.canMine(actor, storage.level(), plotPosition, blockState, blockEntity)) {
            return terminalReceipt(session, MiningStatus.REJECTED);
        }
        boolean[] allowed = {true};
        StructureContexts.run(handle, context -> {
            var event = new StructureMiningEvent(actor, validated, context, StructureMiningEvent.Phase.FINISH,
                    session.before, actor.getMainHandItem());
            if (NeoForge.EVENT_BUS.post(event).isCanceled()) allowed[0] = false;
        });
        if (!allowed[0]) return terminalReceipt(session, MiningStatus.REJECTED);
        var breakEvent = new BreakBlockEvent(storage.level(), plotPosition, blockState, actor);
        NeoForge.EVENT_BUS.post(breakEvent);
        if (breakEvent.isCanceled()) return terminalReceipt(session, MiningStatus.REJECTED);
        // Protection callbacks may change the cell, the actor or the tool; never commit a
        // different block than the one the session authorized.
        if (storage.blockState(plot, session.local) != blockState
                || storage.blockEntity(plot, session.local) != blockEntity
                || !session.maintainable(actor) || validateTarget(actor, session.observation) == null) {
            return terminalReceipt(session, MiningStatus.REJECTED);
        }
        boolean[] completed = {false};
        try {
            StructureContexts.run(handle, context ->
                    completed[0] = destroyBlock(session, validated, blockState, blockEntity));
        } catch (RuntimeException | Error failure) {
            AcademyCraft.LOGGER.error("SubLevel mining commit failed for {} at {}", session.reference,
                    session.local, failure);
        }
        if (completed[0] || storage.blockState(plot, session.local) != blockState) {
            closeMenusFor(session.server, session.subLevel.id(), session.local);
            runtime.refreshGeometry(session.subLevel.id());
        }
        if (actor.connection != null) {
            actor.connection.send(actor.getInventory().createInventoryUpdatePacket(
                    actor.getInventory().getSelectedSlot()));
            actor.inventoryMenu.broadcastChanges();
        }
        return terminalReceipt(session, completed[0] ? MiningStatus.COMPLETED : MiningStatus.REJECTED);
    }

    private boolean destroyBlock(Session session, StructureHit hit, BlockState state,
                                 @Nullable BlockEntity blockEntity) {
        var actor = session.actor;
        var storage = session.runtime.storage();
        var level = storage.level();
        var position = storage.worldPosition(session.subLevel.plot(), session.local);
        var origin = storage.worldPosition(session.subLevel.plot(), BlockPos.ZERO);
        int side = storage.layout().sideChunks() * 16;
        var bounds = new AABB(origin.getX(), level.getMinY(), origin.getZ(),
                origin.getX() + side, level.getMaxY() + 1.0, origin.getZ() + side);
        var tool = actor.getMainHandItem();
        var toolBefore = tool.copy();
        // Keep vanilla callbacks, global loot modifiers and BlockDropsEvent in one scoped path.
        // Container contents and XP created by callbacks are captured too, then moved exactly once.
        var drops = NativeStructureBreakDrops.open(session.host, level, hit, session.before,
                blockEntity, origin, bounds);
        try (drops) {
            var block = state.getBlock();
            var adjusted = block.playerWillDestroy(level, position, state, actor);
            if (level.getBlockState(position) != state || level.getBlockEntity(position) != blockEntity
                    || actor.getMainHandItem() != tool || !session.maintainable(actor)
                    || validateTarget(actor, session.observation) == null) {
                throw new IllegalStateException("SubLevel mining target changed during will-destroy callbacks");
            }
            boolean harvest = !actor.preventsBlockDrops() && adjusted.canHarvestBlock(level, position, actor);
            if (level.getBlockState(position) != state || level.getBlockEntity(position) != blockEntity
                    || actor.getMainHandItem() != tool || !session.maintainable(actor)
                    || validateTarget(actor, session.observation) == null) {
                throw new IllegalStateException("SubLevel mining target changed during harvest callbacks");
            }
            if (!actor.hasInfiniteMaterials()) tool.mineBlock(level, adjusted, position, actor);
            var expectedWear = toolBefore.copy();
            // setDamageValue also adds a DAMAGE component to seeds/blocks. Such items never
            // wear, and adding that component makes an unchanged held stack fail equality.
            if (expectedWear.isDamageableItem()) expectedWear.setDamageValue(tool.getDamageValue());
            if (level.getBlockState(position) != state || level.getBlockEntity(position) != blockEntity
                    || actor.level() != session.host || actor.isRemoved() || !actor.isAlive()
                    || actor.gameMode.getGameModeForPlayer() != session.gameType
                    || actor.getInventory().getSelectedSlot() != session.slot
                    || actor.getMainHandItem() != tool || !tool.isEmpty() && !ItemStack.matches(tool, expectedWear)
                    || !hostAllows(actor, hit.pose(), session.local)
                    || validateTarget(actor, session.observation) == null) {
                throw new IllegalStateException("SubLevel mining target changed during tool callbacks");
            }
            if (!adjusted.onDestroyedByPlayer(level, position, actor, toolBefore, harvest,
                    level.getFluidState(position))) {
                throw new IllegalStateException("SubLevel mining removal was refused");
            }
            var replacement = level.getBlockState(position);
            if (!replacement.isAir() && !(replacement.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock)) {
                throw new IllegalStateException("SubLevel mining callback did not remove the solid block");
            }
            block.destroy(level, position, adjusted);
            if (harvest) block.playerDestroy(level, actor, position, adjusted, blockEntity, toolBefore);
            SubLevelDetachedContents.dropIfMassless(session.runtime, session.subLevel, actor);
            if (!actor.hasInfiniteMaterials() && tool.isEmpty() && !toolBefore.isEmpty()) {
                EventHooks.onPlayerDestroyItem(actor, toolBefore, InteractionHand.MAIN_HAND);
            }
            drops.publish();
            var center = hit.pose().localToWorldPoint(Vec3.atCenterOf(session.local));
            var sound = adjusted.getSoundType(level, position, actor);
            session.host.playSound(null, center.x, center.y, center.z, sound.getBreakSound(),
                    net.minecraft.sounds.SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F,
                    sound.getPitch() * 0.8F);
            session.host.sendParticles(new net.minecraft.core.particles.BlockParticleOption(
                            net.minecraft.core.particles.ParticleTypes.BLOCK, adjusted),
                    center.x, center.y, center.z, 24, 0.25, 0.25, 0.25, 0.05);
            return true;
        } catch (RuntimeException | Error failure) {
            SubLevelMiningRecovery.retain(session.server, drops, failure);
            throw failure;
        }
    }

    private @Nullable StructureHit validateTarget(ServerPlayer actor, StructureHit observation) {
        if (actor == null || observation == null) return null;
        var server = actor.level().getServer();
        if (server == null || !server.isSameThread()) return null;
        var host = actor.level();
        var reference = observation.block().structure();
        if (!SubLevelStructureRefs.isSubLevel(reference)
                || !reference.dimension().equals(host.dimension().identifier())) return null;
        var runtime = SubLevelServer.runtime(host).orElse(null);
        var id = SubLevelStructureRefs.id(reference).orElse(null);
        var subLevel = runtime == null || id == null ? null : runtime.get(id).orElse(null);
        var activation = id == null ? null : SubLevelActivationRegistry.find(id).orElse(null);
        if (runtime == null || subLevel == null || activation == null
                || !activation.runtimeId().equals(observation.runtimeId())
                || activation.generation() != reference.generation()
                || subLevel.geometryRevision() != observation.geometryRevision()
                || (subLevel.state() != SubLevelState.ACTIVE && subLevel.state() != SubLevelState.SLEEPING)) {
            return null;
        }
        var local = observation.block().localPosition();
        if (!subLevel.localBounds().contains(local.getX() + 0.5, local.getY() + 0.5, local.getZ() + 0.5)) return null;
        var state = runtime.storage().blockState(subLevel.plot(), local);
        if (state.isAir()) return null;
        var pose = subLevel.pose();
        var center = pose.localToWorldPoint(Vec3.atCenterOf(local));
        var eye = actor.getEyePosition();
        double reach = actor.blockInteractionRange() + 1.25;
        if (!Double.isFinite(reach) || reach <= 0 || eye.distanceToSqr(center) > reach * reach) return null;
        var clip = host.clip(new ClipContext(eye, center, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, actor));
        if (clip.getType() == HitResult.Type.BLOCK
                && eye.distanceToSqr(clip.getLocation()) < eye.distanceToSqr(center) - 0.05) return null;
        if (!hostAllows(actor, pose, local)) return null;
        // The structure's own cells occlude a hidden target; host blocks were already clipped above.
        var localEye = pose.worldToLocalPoint(eye);
        var structureClip = runtime.view(id).clip(new ClipContext(localEye, Vec3.atCenterOf(local),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE,
                net.minecraft.world.phys.shapes.CollisionContext.empty()));
        if (structureClip.getType() == HitResult.Type.BLOCK && !structureClip.getBlockPos().equals(local)) {
            return null;
        }
        var localPoint = observation.localPoint();
        if (!new AABB(local).inflate(0.75).contains(localPoint)) return null;
        var worldPoint = pose.localToWorldPoint(localPoint);
        return new StructureHit(new SpaceBlockRef(reference, local), observation.runtimeId(),
                subLevel.geometryRevision(), pose, observation.localFace(), localPoint, worldPoint, observation.inside());
    }

    private boolean hostAllows(ServerPlayer actor, org.academy.api.common.structure.StructurePose pose, BlockPos local) {
        var bounds = org.academy.internal.common.structure.BlockStructureOrientedBox.transform(
                new AABB(local), pose).bounds();
        if (bounds.minX < -30_000_000 || bounds.maxX > 30_000_000
                || bounds.minZ < -30_000_000 || bounds.maxZ > 30_000_000) return false;
        var host = actor.level();
        int minX = (int) Math.floor(bounds.minX), minY = (int) Math.floor(bounds.minY), minZ = (int) Math.floor(bounds.minZ);
        int maxX = (int) Math.floor(Math.nextDown(bounds.maxX));
        int maxY = (int) Math.floor(Math.nextDown(bounds.maxY));
        int maxZ = (int) Math.floor(Math.nextDown(bounds.maxZ));
        if ((long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1) > MAX_HOST_CELLS) return false;
        for (var cell : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)) {
            if (host.isOutsideBuildHeight(cell) || !host.hasChunkAt(cell)
                    || !host.getWorldBorder().isWithinBounds(cell) || !host.mayInteract(actor, cell)) return false;
        }
        return true;
    }

    private boolean entityBlocksPlacement(ServerPlayer actor, org.academy.api.common.structure.StructurePose pose, BlockPos local) {
        var bounds = org.academy.internal.common.structure.BlockStructureOrientedBox.transform(
                new AABB(local), pose).bounds().inflate(1.0E-4);
        for (var entity : actor.level().getEntities(actor, bounds)) {
            if (entity.isSpectator() || entity.isRemoved()) continue;
            if (entity.getBoundingBox().intersects(bounds)) return true;
        }
        return false;
    }

    private void publish(Session session, MiningStatus status, @Nullable Feedback feedback) {
        long sequence = session.nextSequence();
        var receipt = new MiningReceipt(session.requestId, sequence, session.target(), status,
                (float) Math.clamp(session.progress, 0.0, 1.0), status == MiningStatus.PROGRESS
                || status == MiningStatus.STARTED ? session.stage : -1, session.detail);
        if (feedback == null) return;
        var recipients = recipients(session);
        for (var recipient : recipients) feedback.mining(recipient, session.actor.getUUID(), receipt);
        if (status != MiningStatus.STARTED && status != MiningStatus.PROGRESS) session.observers.clear();
    }

    private Set<ServerPlayer> recipients(Session session) {
        var result = Collections.newSetFromMap(new IdentityHashMap<ServerPlayer, Boolean>());
        result.add(session.actor);
        var center = session.subLevel.pose().localToWorldPoint(Vec3.atCenterOf(session.local));
        int count = 0;
        for (var player : session.host.players()) {
            if (player == session.actor) continue;
            if (count >= MAX_OBSERVERS) break;
            if (player.position().distanceToSqr(center) <= OBSERVER_RANGE_SQUARED) {
                session.observers.add(player);
                result.add(player);
                count++;
            }
        }
        for (var observer : session.observers) {
            if (result.size() > MAX_OBSERVERS) break;
            if (observer.connection != null && observer.connection.isAcceptingMessages()
                    && observer.level() == session.host) {
                result.add(observer);
            }
        }
        return result;
    }

    private void end(Session session, MiningStatus status, @Nullable Feedback feedback) {
        var state = servers.get(session.server);
        if (state == null || state.active.get(session.actor) != session) return;
        state.active.remove(session.actor);
        session.finished = true;
        session.detail = status.name().toLowerCase(java.util.Locale.ROOT);
        publish(session, status, feedback);
        closeMenu(session.actor, state);
    }

    /** Closes every menu bound to one removed cell (null local = the whole structure). */
    private void closeMenusFor(MinecraftServer server, SubLevelId id, @Nullable BlockPos local) {
        var state = servers.get(server);
        if (state == null) return;
        for (var entry : new ArrayList<>(state.menus.entrySet())) {
            var open = entry.getValue();
            if (!open.id().equals(id) || local != null && !open.local().equals(local)) continue;
            state.menus.remove(entry.getKey());
            if (entry.getKey().containerMenu == open.menu()) entry.getKey().closeContainer();
        }
    }

    private void rememberMenu(ServerPlayer actor, SubLevelId id, BlockPos local) {
        var state = servers(actor.level().getServer());
        if (actor.containerMenu == actor.inventoryMenu) {
            state.menus.remove(actor);
            return;
        }
        state.menus.put(actor, new OpenMenu(id, local, actor.containerMenu));
    }

    private void closeMenu(ServerPlayer actor, ServerSessions state) {
        var open = state.menus.remove(actor);
        if (open == null) return;
        if (actor.containerMenu == open.menu && actor.containerMenu != actor.inventoryMenu) actor.closeContainer();
    }

    private MiningReceipt receipt(Session session, MiningStatus status, int stage) {
        return new MiningReceipt(session.requestId, session.nextSequence(), session.target(), status,
                (float) Math.clamp(session.progress, 0.0, 1.0), stage, session.detail);
    }

    private MiningReceipt terminalReceipt(Session session, MiningStatus status) {
        return new MiningReceipt(session.requestId, session.nextSequence(), session.target(), status,
                (float) Math.clamp(session.progress, 0.0, 1.0), -1, status.name().toLowerCase(java.util.Locale.ROOT));
    }

    private static MiningReceipt rejected(long requestId, String detail) {
        return new MiningReceipt(Math.max(0, requestId), 0, null, MiningStatus.REJECTED, 0, -1, detail);
    }

    private static ActionReceipt actionRejected(long requestId, String detail) {
        return new ActionReceipt(Math.max(0, requestId), null, ActionStatus.REJECTED, List.of(), false, false, detail);
    }

    private static Target target(StructureHit hit) {
        return new Target(hit.block(), hit.runtimeId(), hit.geometryRevision());
    }

    private static MiningStatus statusFor(CancelReason reason) {
        return MiningStatus.ABORTED;
    }

    private ServerSessions servers(MinecraftServer server) {
        return servers.computeIfAbsent(server, ignored -> new ServerSessions());
    }

    private static void requireActor(ServerPlayer actor) {
        Objects.requireNonNull(actor, "actor");
        var server = actor.level().getServer();
        if (server == null || !server.isSameThread()) {
            throw new IllegalStateException("SubLevel interactions require the owning server thread");
        }
    }

    /** Server-owned session state; never persisted and dropped with its server. */
    private static final class ServerSessions {
        private final Map<ServerPlayer, Session> active = new IdentityHashMap<>();
        private final Map<ServerPlayer, Long> highest = new IdentityHashMap<>();
        private final Map<ServerPlayer, OpenMenu> menus = new IdentityHashMap<>();
    }

    private record OpenMenu(SubLevelId id, BlockPos local, AbstractContainerMenu menu) {
    }

    private static final class Session {
        private final ServerPlayer actor;
        private final MinecraftServer server;
        private final ServerLevel host;
        private final SubLevelRuntime runtime;
        private final SubLevel subLevel;
        private final SubLevelId id;
        private final StructureRef reference;
        private final UUID runtimeId;
        private final BlockPos local;
        private final long requestId;
        private final StructureBlockData before;
        private final @Nullable BlockEntity blockEntity;
        private final ItemStack tool;
        private final int slot;
        private final GameType gameType;
        private final Set<ServerPlayer> observers = Collections.newSetFromMap(new IdentityHashMap<>());
        private StructureHit observation;
        private double progress;
        private int stage;
        private long lastHoldTick;
        private long lastPublished;
        private long sequence;
        private boolean finished;
        private String detail = "";

        private Session(ServerPlayer actor, SubLevelRuntime runtime, SubLevel subLevel, StructureHit observation,
                        long requestId, StructureBlockData before, @Nullable BlockEntity blockEntity,
                        ItemStack tool, int slot, GameType gameType, long now) {
            this.actor = actor;
            this.server = actor.level().getServer();
            this.host = actor.level();
            this.runtime = runtime;
            this.subLevel = subLevel;
            this.id = subLevel.id();
            this.reference = observation.block().structure();
            this.runtimeId = observation.runtimeId();
            this.local = observation.block().localPosition();
            this.requestId = requestId;
            this.before = before;
            this.blockEntity = blockEntity;
            this.tool = tool.copy();
            this.slot = slot;
            this.gameType = gameType;
            this.observation = observation;
            this.lastHoldTick = now;
            this.lastPublished = now;
        }

        private void hold(long tick) {
            lastHoldTick = tick;
        }

        private long nextSequence() {
            return ++sequence;
        }

        private Target target() {
            return new Target(new SpaceBlockRef(reference, local), runtimeId, observation.geometryRevision());
        }

        private boolean maintainable(ServerPlayer actor) {
            if (finished || actor != this.actor || actor.level() != host || actor.isRemoved() || actor.hasDisconnected()
                    || actor.containerMenu != actor.inventoryMenu || actor.isUsingItem()
                    || actor.gameMode.getGameModeForPlayer() != gameType
                    || actor.getInventory().getSelectedSlot() != slot
                    || !ItemStack.matches(actor.getMainHandItem(), tool)) {
                return false;
            }
            var entry = SubLevelServer.runtime(host).orElse(null);
            if (entry == null) return false;
            var storage = entry.storage();
            var plotPosition = storage.worldPosition(subLevel.plot(), local);
            if (!storage.level().hasChunkAt(plotPosition)) return false;
            var state = storage.blockState(subLevel.plot(), local);
            var entity = storage.blockEntity(subLevel.plot(), local);
            return (entity == null) == (blockEntity == null) && entity == blockEntity
                    && SubLevelMiningRules.canMine(actor, storage.level(), plotPosition, state, entity)
                    && SubLevelMiningRules.sameTool(actor.getMainHandItem(), tool);
        }

        private boolean matches(ServerPlayer actor, StructureHit validated, @Nullable SubLevelRuntime current) {
            if (current == null || validated == null) return false;
            if (!validated.block().equals(observation.block())
                    || !validated.runtimeId().equals(observation.runtimeId())
                    || !validated.block().structure().equals(reference)) return false;
            var storage = current.storage();
            var plotPosition = storage.worldPosition(subLevel.plot(), local);
            var entity = storage.blockEntity(subLevel.plot(), local);
            var state = storage.blockState(subLevel.plot(), local);
            return entity == blockEntity && SubLevelMiningRules.canMine(actor, storage.level(), plotPosition, state, entity);
        }
    }
}
