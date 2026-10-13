package org.academy.internal.common.structure.space;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import org.academy.api.common.structure.StructureBlockData;
import org.academy.api.common.structure.StructureContexts;
import org.academy.api.common.structure.StructureHandle;
import org.academy.api.common.structure.StructureHit;
import org.academy.api.common.structure.StructureMiningEvent;
import org.academy.api.common.structure.StructureQueries;
import org.academy.internal.common.structure.BlockStructureOrientedBox;
import org.academy.internal.common.structure.NativeStructureRuntime;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.Supplier;

/** Completes one already-authorized mining session; the player always remains in the host level. */
public final class NativeStructureMining {
    private NativeStructureMining() {}

    public static boolean destroy(ServerPlayer player, StructureHit hit,
                                  Supplier<@Nullable StructureHit> revalidate) {
        return destroy(player, hit, revalidate, () -> strictTarget(player, hit));
    }

    /**
     * The session supplies a pinned, player-bound target receipt separately from its unchanged-tool
     * guard. The receipt remains valid to consult after vanilla has legitimately worn the tool.
     */
    public static boolean destroy(ServerPlayer player, StructureHit hit,
                                  Supplier<@Nullable StructureHit> revalidate,
                                  Supplier<@Nullable StructureHit> targetReceipt) {
        Objects.requireNonNull(player);
        Objects.requireNonNull(hit);
        Objects.requireNonNull(revalidate);
        Objects.requireNonNull(targetReceipt);
        var host = player.level();
        if (!host.getServer().isSameThread()) throw new IllegalStateException("Native mining requires the server thread");
        var runtime = NativeStructureRuntime.backend(host.getServer());
        var handle = runtime.resolve(hit.block().structure()).orElse(null);
        if (handle == null || !handle.runtimeId().equals(hit.runtimeId()) || !runtime.isPresented(handle.reference())
                || !allowedActor(player, host)) return false;
        var storage = runtime.storageLevel(handle.reference()).orElse(null);
        var position = hit.block().localPosition();
        if (storage == null || !NativeStructureDimensions.contains(position) || storage.isOutsideBuildHeight(position)
                || !storage.hasChunkAt(position)) return false;
        var outcome = new Outcome();
        boolean guarded;
        try {
            guarded = runtime.runInteraction(handle.reference(), () -> StructureContexts.run(handle, context -> {
                if (!hit.equals(revalidate.get()) || !hit.equals(targetReceipt.get()) || !allowedActor(player, host)) return;
                var before = handle.world().read(position);
                var blockEntity = storage.getBlockEntity(position);
                var tool = player.getMainHandItem();
                if (!NativeStructureMiningPolicy.canMine(before.state(), blockEntity)
                        || !NativeStructureMiningPolicy.canUseTool(tool)) return;
                var initial = new Ownership(player, before, blockEntity, tool);
                outcome.toolBefore = initial.toolBefore;
                outcome.tool = tool;
                outcome.storage = storage;
                var event = new StructureMiningEvent(player, hit, context, StructureMiningEvent.Phase.FINISH, before, tool);
                if (NeoForge.EVENT_BUS.post(event).isCanceled()) return;
                if (!authorized(player, host, storage, runtime, handle, hit, initial, true, targetReceipt)
                        || !hit.equals(revalidate.get())) return;

                // Construct the actual 26.2 event without CommonHooks' local-coordinate
                // ClientboundBlockUpdatePacket path. Projection/session sync owns correction.
                var breakEvent = new BreakBlockEvent(storage, position, before.state(), player);
                breakEvent.setCanceled(!tool.isEmpty() && !tool.canDestroyBlock(before.state(), storage, position, player)
                        || player.blockActionRestricted(storage, position, initial.gameType));
                NeoForge.EVENT_BUS.post(breakEvent);
                if (breakEvent.isCanceled() || !authorized(player, host, storage, runtime, handle, hit, initial, true, targetReceipt)
                        || !hit.equals(revalidate.get())) return;

                // Closing returns carried items through the real player's inventory exactly once.
                // Close callbacks run before final validation and before any inventory is split.
                if (blockEntity != null) NativeStructureChestSessions.closeBlock(host.getServer(), handle.reference(),
                        handle.runtimeId(), position);
                if (!authorized(player, host, storage, runtime, handle, hit, initial, true, targetReceipt)
                        || !hit.equals(revalidate.get())) return;

                try (var drops = NativeStructureBreakDrops.open(host, storage, hit, before, blockEntity)) {
                    outcome.drops = drops;
                    drops.recoveryEvidence();
                    var block = before.state().getBlock();
                    var adjusted = block.playerWillDestroy(storage, position, before.state(), player);
                    if (adjusted != before.state() || !authorized(player, host, storage, runtime, handle, hit, initial, true, targetReceipt)
                            || !hit.equals(revalidate.get())) {
                        throw new IllegalStateException("Native mining changed during will-destroy callbacks");
                    }
                    boolean harvest = !player.preventsBlockDrops() && adjusted.canHarvestBlock(storage, position, player);
                    if (!authorized(player, host, storage, runtime, handle, hit, initial, true, targetReceipt)
                            || !hit.equals(revalidate.get())) {
                        throw new IllegalStateException("Native mining changed during harvest callbacks");
                    }
                    if (!player.preventsBlockDrops()) tool.mineBlock(storage, adjusted, position, player);
                    // Session validation includes the original tool components. Durability has now
                    // legitimately changed. The independent pinned target receipt still verifies
                    // actor, motion history and current visibility without rejecting that wear.
                    if (!authorized(player, host, storage, runtime, handle, hit, initial, false, targetReceipt)) {
                        throw new IllegalStateException("Native mining changed during tool callbacks");
                    }
                    outcome.writeAttempted = true;
                    boolean removed = adjusted.onDestroyedByPlayer(storage, position, player,
                            initial.toolBefore.copy(), harvest, storage.getFluidState(position));
                    if (!removed || !storage.getBlockState(position).isAir()) {
                        throw new IllegalStateException("Native mining removal did not produce its expected empty cell");
                    }
                    outcome.removed = true;
                    block.destroy(storage, position, adjusted);
                    if (!player.preventsBlockDrops() && harvest) {
                        block.playerDestroy(storage, player, position, adjusted, blockEntity, initial.toolBefore.copy());
                    }
                    if (tool.isEmpty() && !initial.toolBefore.isEmpty() && !player.preventsBlockDrops()) {
                        EventHooks.onPlayerDestroyItem(player, initial.toolBefore.copy(), InteractionHand.MAIN_HAND);
                    }
                    drops.publish();
                    var center = hit.pose().localToWorldPoint(Vec3.atCenterOf(position));
                    var sound = adjusted.getSoundType(storage, position, player);
                    host.playSound(null, center.x, center.y, center.z, sound.getBreakSound(), SoundSource.BLOCKS,
                            (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
                    host.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, adjusted),
                            center.x, center.y, center.z, 24, 0.25, 0.25, 0.25, 0.05);
                    outcome.completed = true;
                    // Empty structures keep their permanent owner slot. The normal view cache
                    // changes to EMPTY and removes only the projection, never the stored identity.
                }
            }), outcome::evidence);
        } finally {
            if (player.connection != null) {
                player.connection.send(player.getInventory().createInventoryUpdatePacket(player.getInventory().getSelectedSlot()));
                player.inventoryMenu.broadcastChanges();
            }
        }
        return guarded && outcome.completed;
    }

    private static boolean authorized(ServerPlayer player, ServerLevel host, ServerLevel storage,
                                      NativeStructureRuntime runtime, StructureHandle handle, StructureHit hit,
                                      Ownership initial, boolean unchangedTool,
                                      Supplier<@Nullable StructureHit> targetReceipt) {
        if (!owns(player, host, initial, unchangedTool) || !runtime.isPresented(handle.reference())
                || storage.getBlockEntity(hit.block().localPosition()) != initial.blockEntity
                || !initial.before.equals(handle.world().read(hit.block().localPosition()))
                || player.blockActionRestricted(storage, hit.block().localPosition(), initial.gameType)
                || !hostMayBreak(player, host, handle, hit.block().localPosition())) return false;
        if (!hit.equals(targetReceipt.get())) return false;
        var from = player.getEyePosition();
        // A historical view ray selected a local face which has since moved. Current obstacles
        // are checked along the authorized face's current world segment, not the old look line.
        var to = hit.worldPoint();
        double targetDistance = from.distanceToSqr(hit.worldPoint());
        int visited = 0;
        for (var entity : host.getEntities(player, new AABB(from, to).inflate(1),
                candidate -> !candidate.isSpectator() && candidate.isPickable())) {
            if (++visited > 256) return false;
            if (NativeStructurePlayerTarget.occludes(entity.getBoundingBox(), entity.getPickRadius(),
                    from, to, targetDistance)) return false;
        }
        // Shapes and entity predicates may call mod code. Recheck host protection before
        // the final receipt and plain ownership/version checks. Never adopt a different hit.
        return hostMayBreak(player, host, handle, hit.block().localPosition())
                && hit.equals(targetReceipt.get())
                && runtime.isPresented(handle.reference())
                && storage.getBlockEntity(hit.block().localPosition()) == initial.blockEntity
                && initial.before.equals(handle.world().read(hit.block().localPosition()))
                && owns(player, host, initial, unchangedTool);
    }

    private static @Nullable StructureHit strictTarget(ServerPlayer player, StructureHit hit) {
        double distance = player.blockInteractionRange();
        if (!Double.isFinite(distance) || distance <= 0 || distance > StructureQueries.MAX_RAY_LENGTH) return null;
        var from = player.getEyePosition();
        var to = from.add(player.getLookAngle().scale(distance));
        return StructureQueries.validate(player.level(), from, to, hit).currentHit().orElse(null);
    }

    private static boolean owns(ServerPlayer player, ServerLevel host, Ownership initial, boolean unchangedTool) {
        return allowedActor(player, host) && player.gameMode.getGameModeForPlayer() == initial.gameType
                && player.hasInfiniteMaterials() == initial.creative && player.preventsBlockDrops() == initial.preventDrops
                && player.getInventory().getSelectedSlot() == initial.slot && player.getMainHandItem() == initial.tool
                && (unchangedTool ? ItemStack.matches(initial.tool, initial.toolBefore) : expectedToolWear(initial))
                && NativeStructureMiningPolicy.canUseTool(initial.tool)
                && initial.tool.isItemEnabled(host.enabledFeatures()) && !player.getCooldowns().isOnCooldown(initial.tool)
                && player.getOffhandItem() == initial.otherTool && ItemStack.matches(initial.otherTool, initial.otherBefore)
                && player.position().equals(initial.position) && player.getEyePosition().equals(initial.eye)
                && player.getLookAngle().equals(initial.look);
    }

    private static boolean expectedToolWear(Ownership initial) {
        if (initial.toolBefore.isEmpty()) return initial.tool.isEmpty();
        if (initial.tool.isEmpty()) return true;
        var expected = initial.toolBefore.copy();
        expected.setDamageValue(initial.tool.getDamageValue());
        return ItemStack.matches(initial.tool, expected);
    }

    private static boolean allowedActor(ServerPlayer player, ServerLevel host) {
        return player.level() == host && NativeStructureInteraction.canUseMenu(player)
                && player.connection.getPlayer() == player && !player.isUsingItem() && player.containerMenu == player.inventoryMenu;
    }

    private static boolean hostMayBreak(ServerPlayer player, ServerLevel host, StructureHandle handle, BlockPos position) {
        var bounds = BlockStructureOrientedBox.transform(new AABB(position), handle.pose()).bounds();
        if (bounds.minX < -30_000_000 || bounds.maxX > 30_000_000
                || bounds.minZ < -30_000_000 || bounds.maxZ > 30_000_000
                || bounds.minY < host.getMinY() || bounds.maxY > host.getMaxY() + 1.0) return false;
        var min = BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ);
        var max = BlockPos.containing(Math.nextDown(bounds.maxX), Math.nextDown(bounds.maxY), Math.nextDown(bounds.maxZ));
        if ((long) (max.getX() - min.getX() + 1) * (max.getY() - min.getY() + 1)
                * (max.getZ() - min.getZ() + 1) > 64) return false;
        for (var cell : BlockPos.betweenClosed(min, max)) {
            if (host.isOutsideBuildHeight(cell) || !host.hasChunkAt(cell)
                    || !host.getWorldBorder().isWithinBounds(cell) || !host.mayInteract(player, cell)) return false;
        }
        return true;
    }

    private static final class Ownership {
        private final StructureBlockData before;
        private final @Nullable BlockEntity blockEntity;
        private final ItemStack tool;
        private final ItemStack toolBefore;
        private final ItemStack otherTool;
        private final ItemStack otherBefore;
        private final int slot;
        private final GameType gameType;
        private final boolean creative;
        private final boolean preventDrops;
        private final Vec3 position;
        private final Vec3 eye;
        private final Vec3 look;

        private Ownership(ServerPlayer player, StructureBlockData before, @Nullable BlockEntity blockEntity, ItemStack tool) {
            this.before = before; this.blockEntity = blockEntity; this.tool = tool; toolBefore = tool.copy();
            otherTool = player.getOffhandItem(); otherBefore = otherTool.copy();
            slot = player.getInventory().getSelectedSlot(); gameType = player.gameMode.getGameModeForPlayer();
            creative = player.hasInfiniteMaterials(); preventDrops = player.preventsBlockDrops();
            position = player.position(); eye = player.getEyePosition(); look = player.getLookAngle();
        }
    }

    private static final class Outcome {
        private NativeStructureBreakDrops.@Nullable Scope drops;
        private @Nullable ServerLevel storage;
        private ItemStack toolBefore = ItemStack.EMPTY;
        private ItemStack tool = ItemStack.EMPTY;
        private boolean writeAttempted;
        private boolean removed;
        private boolean completed;

        private CompoundTag evidence() {
            var result = drops == null ? new CompoundTag() : drops.recoveryEvidence();
            result.putBoolean("write_attempted", writeAttempted);
            result.putBoolean("block_removed", removed);
            if (storage != null) {
                var reporter = new ProblemReporter.Collector();
                var output = TagValueOutput.createWithContext(reporter, storage.registryAccess());
                output.store("before_observation_do_not_restore", ItemStack.OPTIONAL_CODEC, toolBefore);
                output.store("current", ItemStack.OPTIONAL_CODEC, tool);
                if (!reporter.isEmpty()) throw new IllegalStateException("Cannot encode native mining tool: " + reporter.getReport());
                result.put("tool", output.buildResult());
            }
            return result;
        }
    }
}
