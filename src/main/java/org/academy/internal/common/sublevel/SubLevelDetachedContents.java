package org.academy.internal.common.sublevel;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

import java.util.ArrayList;

/** Releases unsupported decoration when mining removes the body's final collision cell. */
final class SubLevelDetachedContents {
    private SubLevelDetachedContents() {}

    /**
     * Called inside the mining output scope, so ordinary neighbor/loot callbacks and every
     * BlockDropsEvent still publish once at the structure's host-space position.
     * Protected leftovers remain in the plot and can be selected again.
     */
    static void dropIfMassless(SubLevelRuntime runtime, SubLevel subLevel, ServerPlayer actor) {
        var storage = runtime.storage();
        var level = storage.level();
        var bounds = subLevel.localBounds();
        var remaining = new ArrayList<BlockPos>();
        for (var local : BlockPos.betweenClosed(BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ),
                BlockPos.containing(Math.nextDown(bounds.maxX), Math.nextDown(bounds.maxY),
                        Math.nextDown(bounds.maxZ)))) {
            var position = storage.worldPosition(subLevel.plot(), local);
            var state = level.getBlockState(position);
            if (state.isAir()) continue;
            if (!state.getCollisionShape(level, position).isEmpty()) return;
            remaining.add(local.immutable());
        }
        for (var local : remaining) {
            var position = storage.worldPosition(subLevel.plot(), local);
            var state = level.getBlockState(position);
            if (state.isAir()) continue; // An earlier support update may already have dropped it.
            if (!state.getCollisionShape(level, position).isEmpty()) return;
            var hostPosition = BlockPos.containing(subLevel.pose().localToWorldPoint(Vec3.atCenterOf(local)));
            if (actor.level() != runtime.host() || !actor.isAlive() || actor.isRemoved()) return;
            if (!runtime.host().mayInteract(actor, hostPosition)
                    || !SubLevelMiningRules.canMine(actor, level, position, state, level.getBlockEntity(position))) continue;
            var event = new BreakBlockEvent(level, position, state, actor);
            if (NeoForge.EVENT_BUS.post(event).isCanceled()) continue;
            if (actor.level() != runtime.host() || !actor.isAlive() || actor.isRemoved()) return;
            if (level.getBlockState(position) != state || !runtime.host().mayInteract(actor, hostPosition)
                    || !SubLevelMiningRules.canMine(actor, level, position, state, level.getBlockEntity(position))) continue;
            level.destroyBlock(position, !actor.preventsBlockDrops(), actor);
        }
    }
}
