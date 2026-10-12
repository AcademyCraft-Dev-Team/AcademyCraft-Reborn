package org.academy.internal.common.structure.space;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.SpaceBlockRef;
import org.academy.api.common.structure.StructureBlockData;
import org.academy.api.common.structure.StructureContexts;
import org.academy.api.common.structure.StructureHandle;
import org.academy.api.common.structure.StructureHit;
import org.academy.api.common.structure.StructurePlacementEvent;
import org.academy.internal.common.structure.BlockStructureOrientedBox;
import org.academy.internal.common.structure.NativeStructureRuntime;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.Supplier;

/** Audited single-cell vanilla placement with local geometry and real host inventory ownership. */
public final class NativeStructurePlacement {
    private NativeStructurePlacement() {}

    public static boolean canPlace(ItemStack stack) {
        return NativeStructurePlacementPolicy.canPlace(stack);
    }

    /**
     * The dispatcher supplies a trusted ray hit and a function which checks that same original
     * client observation against the actor's current authoritative ray. It is invoked again after
     * protection callbacks. No client-provided item stack or inventory image is accepted.
     */
    public static boolean place(ServerPlayer player, StructureHit trustedHit, InteractionHand hand,
                                Supplier<@Nullable StructureHit> revalidate) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(trustedHit, "trustedHit");
        Objects.requireNonNull(hand, "hand");
        Objects.requireNonNull(revalidate, "revalidate");
        var host = player.level();
        var server = host.getServer();
        if (!server.isSameThread()) throw new IllegalStateException("Structure placement requires the server thread");
        var runtime = NativeStructureRuntime.backend(server);
        var handle = runtime.resolve(trustedHit.block().structure()).orElse(null);
        if (handle == null || !handle.runtimeId().equals(trustedHit.runtimeId())
                || !runtime.isPresented(handle.reference()) || !allowedActor(player, host)) return false;
        var storage = runtime.storageLevel(handle.reference()).orElse(null);
        if (storage == null) return false;

        var outcome = new Outcome();
        boolean guarded;
        try {
            guarded = runtime.runInteraction(handle.reference(), () -> StructureContexts.run(handle, space -> {
                outcome.stage = "initial-ray-actor";
                var current = revalidate.get();
                if (!trustedHit.equals(current) || !allowedActor(player, host)) return;
                var actualStack = player.getItemInHand(hand);
                outcome.stage = "initial-prepare";
                var preview = prepare(player, host, storage, runtime, handle, current, hand, actualStack);
                if (preview == null) return;
                var expectedStack = actualStack.copy();
                int handSlot = inventorySlot(player, hand);
                boolean creative = player.hasInfiniteMaterials();
                var event = new StructurePlacementEvent(player, current, space, hand,
                        new SpaceBlockRef(handle.reference(), preview.position), preview.before,
                        preview.state, expectedStack);
                outcome.stage = "protection-event";
                if (NeoForge.EVENT_BUS.post(event).isCanceled()) return;

                outcome.stage = "after-event-ownership";
                var afterEvent = revalidate.get();
                if (!current.equals(afterEvent) || !allowedActor(player, host)
                        || creative != player.hasInfiniteMaterials() || inventorySlot(player, hand) != handSlot
                        || player.getItemInHand(hand) != actualStack || !ItemStack.matches(actualStack, expectedStack)
                        || !preview.before.equals(handle.world().read(preview.position))) return;
                outcome.stage = "after-event-prepare";
                var checked = prepare(player, host, storage, runtime, handle, afterEvent, hand, actualStack);
                if (checked == null || !checked.position.equals(preview.position) || checked.state != preview.state
                        || !checked.before.equals(preview.before)) return;
                // Shape and protection hooks may themselves call mod code. Verify the ownership
                // and version one final time before transferring a single material out of the hand.
                outcome.stage = "before-reservation-ownership";
                if (!current.equals(revalidate.get()) || !allowedActor(player, host)
                        || player.getItemInHand(hand) != actualStack || !ItemStack.matches(actualStack, expectedStack)
                        || inventorySlot(player, hand) != handSlot || creative != player.hasInfiniteMaterials()
                        || !runtime.isPresented(handle.reference())
                        || !checked.before.equals(handle.world().read(checked.position))) return;

                // The final shape callbacks may change the other hand or secondary-use state.
                // Recompute the action before adopting either as the reservation's baseline.
                var selection = NativeStructurePlacementPolicy.selectAction(player.getMainHandItem(),
                        player.getOffhandItem(), player.isSecondaryUseActive(),
                        storage.getBlockState(current.block().localPosition()));
                if (selection == null || selection.action() != NativeStructurePlacementPolicy.Action.PLACE
                        || selection.hand() != hand) return;

                var actorPosition = player.position();
                var actorEye = player.getEyePosition();
                var actorLook = player.getLookAngle();
                boolean secondaryUse = player.isSecondaryUseActive();
                var otherHand = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
                var otherStack = player.getItemInHand(otherHand);
                var expectedOtherStack = otherStack.copy();
                var escrow = creative ? actualStack.copyWithCount(1) : actualStack.split(1);
                var remaining = actualStack.copy();
                outcome.inventorySlot = creative ? -1 : handSlot;
                // This detached receipt represents the already-reserved material. It is refunded
                // only when the guarded call proves no block write happened, never after a write.
                var receipt = escrow.copyWithCount(1);
                NativeStructurePlaceContext placement = null;
                boolean ambiguousWrite = false;
                try {
                    outcome.stage = "vanilla-before-write";
                    placement = new NativeStructurePlaceContext(storage, player, hand, escrow, afterEvent);
                    placement.authorizeWrite(checked.state, () -> {
                        outcome.stage = "actual-write-authorization";
                        boolean authorized = current.equals(revalidate.get())
                            && allowedActor(player, host) && player.position().equals(actorPosition)
                            && player.getEyePosition().equals(actorEye) && player.getLookAngle().equals(actorLook)
                            && player.isSecondaryUseActive() == secondaryUse && creative == player.hasInfiniteMaterials()
                            && inventorySlot(player, hand) == handSlot && player.getItemInHand(hand) == actualStack
                            && ItemStack.matches(actualStack, remaining) && ItemStack.matches(escrow, receipt)
                            && player.getItemInHand(otherHand) == otherStack && ItemStack.matches(otherStack, expectedOtherStack)
                            && !player.getCooldowns().isOnCooldown(receipt) && runtime.isPresented(handle.reference())
                            && checked.before.equals(handle.world().read(checked.position))
                            && hostMayPlace(player, host, handle, checked.position)
                            && checked.collision.stillValid();
                        if (authorized) outcome.stage = "authorized-write";
                        return authorized;
                    });
                    var result = ((BlockItem) receipt.getItem()).place(placement);
                    if (!placement.hasAttemptedWrite() && result.consumesAction()) {
                        // The required redirect makes this impossible for the audited vanilla
                        // path. An opaque replacement must not create a block and refund its item.
                        ambiguousWrite = true;
                        throw new IllegalStateException("Native placement bypassed its authorized write");
                    }
                    if (!placement.hasWritten()) return;
                    if (!result.consumesAction()) {
                        throw new IllegalStateException("Native placement wrote blocks but reported failure");
                    }
                    outcome.stage = "post-write-effects";
                    placement.playSound();
                    player.awardStat(Stats.ITEM_USED.get(receipt.getItem()));
                    if (!creative && expectedStack.getCount() == 1) {
                        EventHooks.onPlayerDestroyItem(player, expectedStack, hand);
                    }
                    outcome.placed = true;
                    outcome.stage = "placed";
                } finally {
                    if (!creative && !ambiguousWrite && (placement == null || !placement.hasWritten())) {
                        refund(player, hand, receipt);
                    }
                }
            }));
        } finally {
            if (Boolean.getBoolean("academy.nativeStructurePlacementSmoke")) {
                AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_PLACEMENT_SERVICE stage={} placed={} hand={} target={}",
                        outcome.stage, outcome.placed, hand, trustedHit.block().localPosition());
            }
            if (outcome.inventorySlot >= 0 && player.connection != null) {
                player.connection.send(player.getInventory().createInventoryUpdatePacket(outcome.inventorySlot));
                // A refusing callback may have changed the selected hand/slot before the refund.
                // Synchronize any destination slot as well without replacing any inventory data.
                player.inventoryMenu.broadcastChanges();
            }
        }
        return guarded && outcome.placed;
    }

    private static @Nullable Candidate prepare(ServerPlayer player, ServerLevel host, ServerLevel storage,
                                               NativeStructureRuntime runtime, StructureHandle handle,
                                               StructureHit hit, InteractionHand hand, ItemStack stack) {
        if (!canPlace(stack) || player.getCooldowns().isOnCooldown(stack)
                || !stack.getItem().isEnabled(host.enabledFeatures())
                || !stack.getItem().isEnabled(storage.enabledFeatures())
                || !runtime.isPresented(handle.reference()) || !handle.runtimeId().equals(hit.runtimeId())) return null;
        var clicked = hit.block().localPosition();
        if (!localPositionLoaded(storage, clicked)) return null;
        var clickedState = storage.getBlockState(clicked);
        if (!auditedClickedState(clickedState)) return null;
        var selection = NativeStructurePlacementPolicy.selectAction(player.getMainHandItem(), player.getOffhandItem(),
                player.isSecondaryUseActive(), clickedState);
        if (selection == null || selection.action() != NativeStructurePlacementPolicy.Action.PLACE
                || selection.hand() != hand) return null;
        var context = new NativeStructurePlaceContext(storage, player, hand, stack, hit);
        var position = context.getClickedPos().immutable();
        if (!localPositionLoaded(storage, position)) return null;
        var before = handle.world().read(position);
        if (!auditedReplacement(before) || !context.canPlace()) return null;
        int cellCount = runtime.presentedCellCount(handle.reference());
        if (cellCount < 0 || cellCount + (before.state().isAir() ? 1 : 0) > NativeStructureView.MAX_CELLS) return null;
        var block = ((BlockItem) stack.getItem()).getBlock();
        if (!block.isEnabled(storage.enabledFeatures())) return null;
        var proposed = block.getStateForPlacement(context);
        if (proposed == null || !auditedPlacementBlock(proposed) || !NativeStructureView.isSupportedState(proposed)
                || !survivalCoordinatesAvailable(storage, position, proposed)
                || !proposed.canSurvive(storage, position)
                || !storage.isUnobstructed(proposed, position, CollisionContext.placementContext(player))
                || !hostMayPlace(player, host, handle, position)) return null;
        var collision = NativeStructurePlacementCollision.preflight(host, handle, storage, position, proposed);
        return collision == null ? null : new Candidate(position, before, proposed, collision);
    }

    private static boolean allowedActor(ServerPlayer player, ServerLevel host) {
        return player.level() == host && NativeStructureInteraction.canUseMenu(player)
                && !player.isUsingItem() && player.containerMenu == player.inventoryMenu;
    }

    private static boolean localPositionLoaded(ServerLevel storage, BlockPos position) {
        return NativeStructureDimensions.contains(position) && !storage.isOutsideBuildHeight(position)
                && storage.hasChunkAt(position);
    }

    private static boolean survivalCoordinatesAvailable(ServerLevel storage, BlockPos position, BlockState proposed) {
        if (!proposed.is(Blocks.LEVER)) return true;
        var support = switch (proposed.getValue(LeverBlock.FACE)) {
            case FLOOR -> position.below();
            case CEILING -> position.above();
            case WALL -> position.relative(proposed.getValue(LeverBlock.FACING).getOpposite());
        };
        // The storage boundary reads as BARRIER. It must not become an invisible attachment face.
        return localPositionLoaded(storage, support);
    }

    private static boolean auditedReplacement(StructureBlockData before) {
        return before.blockEntityData() == null && before.state().getFluidState().isEmpty()
                && (before.state().isAir() || auditedPlacementBlock(before.state()));
    }

    private static boolean auditedPlacementBlock(BlockState state) {
        return state.is(Blocks.STONE) || state.is(Blocks.COBBLESTONE) || state.is(Blocks.OAK_PLANKS)
                || state.is(Blocks.OAK_STAIRS) || state.is(Blocks.OAK_SLAB) || state.is(Blocks.LEVER);
    }

    private static boolean auditedClickedState(BlockState state) {
        if (!state.getFluidState().isEmpty()) return false;
        return auditedPlacementBlock(state) || state.is(Blocks.CHEST) || state.is(Blocks.FURNACE)
                || state.is(Blocks.BLAST_FURNACE) || state.is(Blocks.SMOKER) || state.is(Blocks.HOPPER)
                || state.is(Blocks.DISPENSER) || state.is(Blocks.DROPPER) || state.is(Blocks.REDSTONE_LAMP);
    }

    private static boolean hostMayPlace(ServerPlayer player, ServerLevel host, StructureHandle handle, BlockPos position) {
        var bounds = BlockStructureOrientedBox.transform(new AABB(position), handle.pose()).bounds();
        if (bounds.minX < -30_000_000 || bounds.maxX > 30_000_000
                || bounds.minZ < -30_000_000 || bounds.maxZ > 30_000_000) return false;
        // Protect every host cell covered by the transformed unit cell, including empty collision
        // shapes such as levers. Never load host chunks just to make a placement succeed.
        int minX = Mth.floor(bounds.minX), minY = Mth.floor(bounds.minY), minZ = Mth.floor(bounds.minZ);
        int maxX = Mth.floor(Math.nextDown(bounds.maxX));
        int maxY = Mth.floor(Math.nextDown(bounds.maxY));
        int maxZ = Mth.floor(Math.nextDown(bounds.maxZ));
        if ((long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1) > 64) return false;
        for (var cell : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)) {
            if (host.isOutsideBuildHeight(cell) || !host.hasChunkAt(cell)
                    || !host.getWorldBorder().isWithinBounds(cell) || !host.mayInteract(player, cell)) return false;
        }
        return true;
    }

    private static int inventorySlot(ServerPlayer player, InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? player.getInventory().getSelectedSlot() : Inventory.SLOT_OFFHAND;
    }

    private static void refund(ServerPlayer player, InteractionHand hand, ItemStack reserved) {
        var current = player.getItemInHand(hand);
        if (current.isEmpty()) {
            player.setItemInHand(hand, reserved);
        } else if (ItemStack.isSameItemSameComponents(current, reserved) && current.getCount() < current.getMaxStackSize()) {
            current.grow(1);
        } else {
            // A callback changed the hand. Return the one reserved material without overwriting
            // that change; vanilla inserts it or drops it in the player's actual host world.
            player.getInventory().placeItemBackInInventory(reserved);
        }
    }

    private record Candidate(BlockPos position, StructureBlockData before, BlockState state,
                             NativeStructurePlacementCollision.Guard collision) {}

    private static final class Outcome {
        private boolean placed;
        private int inventorySlot = -1;
        private String stage = "runtime-guard";
    }
}
