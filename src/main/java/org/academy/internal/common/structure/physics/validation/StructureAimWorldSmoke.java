package org.academy.internal.common.structure.physics.validation;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructureHandle;
import org.academy.api.common.structure.StructureQueries;
import org.academy.api.server.ability.WorldBlockTargets;
import org.academy.internal.common.ability.accelerator.skills.lv5.PlasmaGeneration;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.skill.Plasma;

/** Real world/query/projectile checks shared by native and mixed-mod isolated fixtures. */
public final class StructureAimWorldSmoke {
    private StructureAimWorldSmoke() {}

    public static void checkShapes(ServerLevel host, StructureHandle handle, BlockPos solid, BlockPos slab,
                                   StringBuilder evidence) {
        var pose = handle.pose();
        for (var shape : new ClipContext.Block[]{ClipContext.Block.OUTLINE, ClipContext.Block.COLLIDER}) {
            var local = Vec3.atCenterOf(solid);
            var from = pose.localToWorldPoint(local.add(0, 0, 4));
            var to = pose.localToWorldPoint(local);
            var hit = StructureQueries.raycast(host, from, to, shape).orElseThrow();
            var target = WorldBlockTargets.clip(host, null, from, to, shape);
            require(target.hit() && target.position().distanceTo(hit.worldPoint()) < 1.0E-6,
                    "Rotated structure targeting used a local point");
            var topGap = Vec3.atLowerCornerOf(slab).add(0.5, 0.75, 0.5);
            var above = WorldBlockTargets.clip(host, null, pose.localToWorldPoint(topGap.add(0, 0, 2)),
                    pose.localToWorldPoint(topGap), shape);
            require(!above.hit(), "Partial shape was replaced by its full cell or structure bounds");
            var lower = Vec3.atLowerCornerOf(slab).add(0.5, 0.25, 0.5);
            require(WorldBlockTargets.clip(host, null, pose.localToWorldPoint(lower.add(0, 0, 2)),
                    pose.localToWorldPoint(lower), shape).hit(), "Bottom half slab was not targetable");
        }
        evidence.append("rotated_structure_skill_target_and_partial_shapes=").append(handle.reference().backend()).append('\n');
    }

    public static void check(ServerPlayer player, StructureHandle handle, StringBuilder evidence) {
        var host = (ServerLevel) player.level();
        var pose = handle.pose();
        // Aim above the separately pickable nozzle on the bottom of the jet fixture.
        var from = pose.localToWorldPoint(new Vec3(0.5, 0.9, 5));
        var to = pose.localToWorldPoint(new Vec3(0.5, 0.9, -1));
        var originalPosition = player.position();
        float yaw = player.getYRot(), pitch = player.getXRot();
        var plasma = new Plasma(EntityTypes.PLASMA.get(), host);
        var zombie = new Zombie(net.minecraft.world.entity.EntityTypes.ZOMBIE, host);
        try {
            var direction = to.subtract(from).normalize();
            player.setPos(from.subtract(0, player.getEyeHeight(), 0));
            player.setYRot((float) Math.toDegrees(Math.atan2(-direction.x, direction.z)));
            player.setXRot((float) -Math.toDegrees(Math.asin(direction.y)));
            var rayEnd = from.add(player.getLookAngle().scale(32));
            var hit = StructureQueries.raycast(host, from, rayEnd).orElseThrow();
            require(hit.block().structure().equals(handle.reference()), "Aim fixture hit another structure");
            var aim = PlasmaGeneration.Server.findTarget(player);
            require(aim.distanceTo(hit.worldPoint()) < 1.0E-4,
                    "Plasma target " + aim + " differs from the world surface " + hit.worldPoint());
            zombie.setNoAi(true);
            zombie.setNoGravity(true);
            zombie.setPos(pose.localToWorldPoint(new Vec3(0.5, 0, -2)));
            require(host.addFreshEntity(zombie), "Cannot add aim occlusion fixture");
            require(PlasmaGeneration.Server.findTarget(player).distanceTo(aim) < 1.0E-4,
                    "Entity behind a physical structure stole the plasma target");
            zombie.setPos(pose.localToWorldPoint(new Vec3(0.5, 0, 3)));
            require(PlasmaGeneration.Server.findTarget(player).distanceTo(zombie.getBoundingBox().getCenter()) < 1.0E-4,
                    "Visible entity in front of a structure was no longer targetable");
            zombie.discard();
            require(WorldBlockTargets.clip(player, from, to, ClipContext.Block.COLLIDER)
                    .position().distanceTo(hit.worldPoint()) < 1.0E-4, "Collider target missed the physical block");
            plasma.setPos(from);
            // Zero damage isolates targeting/flight from unrelated terrain destruction policy.
            plasma.launch(player.getUUID(), aim, 1, 0, 0, 0, false, 0);
            for (int tick = 0; tick < 20 && !plasma.isRemoved(); tick++) plasma.tick();
            require(plasma.isRemoved() && plasma.position().distanceTo(aim) < 1.0E-6,
                    "Real plasma projectile did not impact the locked world surface");
            evidence.append("physical_structure_plasma_world_target_and_impact=")
                    .append(handle.reference().backend()).append('\n');
            evidence.append("physical_structure_plasma_entity_occlusion=true\n");
        } finally {
            zombie.discard();
            plasma.discard();
            player.setPos(originalPosition);
            player.setYRot(yaw);
            player.setXRot(pitch);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
