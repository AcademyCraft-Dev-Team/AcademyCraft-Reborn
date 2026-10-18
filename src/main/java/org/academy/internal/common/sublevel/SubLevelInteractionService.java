package org.academy.internal.common.sublevel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.academy.AcademyCraft;

/**
 * Phase 3 server-side interaction bridge. It uses the real plot Level for vanilla block use,
 * placement and destruction; client raycast/packet routing is added in Phase 4.
 */
public final class SubLevelInteractionService {
    private SubLevelInteractionService() {
    }

    /** Server-side line-of-sight validation for a client interaction packet. */
    public static boolean validateRay(MinecraftServer server, SubLevelId id, BlockPos local,
                                      Vec3 point, ServerPlayer player, boolean usePoint) {
        var runtime = SubLevelServer.runtime(server).orElse(null);
        if (runtime == null) return false;
        var subLevel = runtime.get(id).orElse(null);
        if (subLevel == null) return false;
        var eye = player.getEyePosition();
        double reach = player.blockInteractionRange() + 1.0;
        if (eye.distanceToSqr(point) > reach * reach) return false;
        var pose = subLevel.pose();
        var localPoint = pose.worldToLocalPoint(point);
        if (!new AABB(local).inflate(0.05).contains(localPoint)) return false;
        var localEye = pose.worldToLocalPoint(eye);
        var rayTarget = usePoint ? localPoint : Vec3.atCenterOf(local);
        var hit = runtime.view(id).clip(new ClipContext(localEye, rayTarget,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, CollisionContext.empty()));
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(local);
    }

    public static boolean breakBlock(MinecraftServer server, SubLevelId id, BlockPos local,
                                     ServerPlayer player) {
        var runtime = SubLevelServer.runtime(server).orElse(null);
        if (runtime == null) return false;
        var subLevel = runtime.get(id).orElse(null);
        if (subLevel == null) return false;
        var level = runtime.storage().level();
        var world = runtime.storage().worldPosition(subLevel.plot(), local);
        if (!level.hasChunkAt(world) || level.getBlockState(world).isAir()) return false;
        if (player != null && !level.mayInteract(player, world)) return false;
        if (!level.destroyBlock(world, player != null, player)) return false;
        runtime.refreshGeometry(id);
        return true;
    }

    public static InteractionResult useBlock(MinecraftServer server, SubLevelId id, BlockPos local,
                                             Direction face, Vec3 hit, ServerPlayer player,
                                             InteractionHand hand) {
        var runtime = SubLevelServer.runtime(server).orElse(null);
        if (runtime == null) return InteractionResult.FAIL;
        var subLevel = runtime.get(id).orElse(null);
        if (subLevel == null) return InteractionResult.FAIL;
        var level = runtime.storage().level();
        var world = runtime.storage().worldPosition(subLevel.plot(), local);
        if (!level.hasChunkAt(world) || level.getBlockState(world).isAir()) return InteractionResult.FAIL;
        if (!level.mayInteract(player, world)) return InteractionResult.FAIL;
        var result = new BlockHitResult(hit, face, world, false);
        var stack = player.getItemInHand(hand);
        var before = level.getBlockState(world);
        var interaction = before.useItemOn(stack, level, player, hand, result);
        if (!interaction.consumesAction()) {
            interaction = before.useWithoutItem(level, player, result);
        }
        var after = level.getBlockState(world);
        AcademyCraft.LOGGER.debug("SubLevel use {}: {} -> {} result={}", world, before, after, interaction);
        if (interaction.consumesAction()) runtime.refreshGeometry(id);
        return interaction;
    }

    public static InteractionResult placeBlock(MinecraftServer server, SubLevelId id, BlockPos local,
                                               Direction face, Vec3 hit, ServerPlayer player,
                                               InteractionHand hand) {
        var runtime = SubLevelServer.runtime(server).orElse(null);
        if (runtime == null) return InteractionResult.FAIL;
        var subLevel = runtime.get(id).orElse(null);
        if (subLevel == null) return InteractionResult.FAIL;
        var level = runtime.storage().level();
        var world = runtime.storage().worldPosition(subLevel.plot(), local);
        if (!level.hasChunkAt(world) || !level.mayInteract(player, world)) return InteractionResult.FAIL;
        var stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof BlockItem)) return InteractionResult.FAIL;
        var hitResult = new BlockHitResult(hit, face, world, false);
        var context = new BlockPlaceContext(level, player, hand, stack, hitResult);
        var result = ((BlockItem) stack.getItem()).place(context);
        if (result.consumesAction()) runtime.refreshGeometry(id);
        return result;
    }
}
