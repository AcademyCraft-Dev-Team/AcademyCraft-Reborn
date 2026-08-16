package org.academy.internal.common.ability.mentalout.precision;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.ability.program.ProgramBlockPosition;
import org.academy.api.common.ability.program.ProgramDirection;
import org.academy.api.common.ability.program.ProgramTargetResolver;
import org.academy.api.common.ability.program.ProgramWorldPosition;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Mental-out category view for common target-query nodes.
 *
 * <p>Every coordinate query is confined to the caster's current dimension and a bounded area
 * around the caster. Results are deterministically ordered and capped before entering the VM.</p>
 */
final class PrecisionProgramTargetResolver implements ProgramTargetResolver {
    static final double MAX_QUERY_RANGE = 32.0;
    static final int MAX_QUERY_RESULTS = 128;

    private final ServerPlayer player;

    PrecisionProgramTargetResolver(ServerPlayer player) {
        this.player = player;
    }

    @Override
    public Object caster() {
        return player;
    }

    @Override
    public Optional<Object> lookTarget() {
        var look = player.getViewVector(1.0f);
        if (look.lengthSqr() <= 1.0E-12) return Optional.empty();
        var eye = player.getEyePosition();
        return raycastEntity(
                new ProgramWorldPosition(
                        player.level().dimension().identifier(), eye.x, eye.y, eye.z
                ),
                new ProgramDirection(look.x, look.y, look.z),
                MAX_QUERY_RANGE
        );
    }

    @Override
    public Optional<ProgramBlockPosition> lookBlockTarget() {
        var look = player.getViewVector(1.0f);
        if (look.lengthSqr() <= 1.0E-12) return Optional.empty();
        var eye = player.getEyePosition();
        return raycastBlock(
                new ProgramWorldPosition(
                        player.level().dimension().identifier(), eye.x, eye.y, eye.z
                ),
                new ProgramDirection(look.x, look.y, look.z),
                MAX_QUERY_RANGE
        );
    }

    @Override
    public Optional<ProgramWorldPosition> positionOf(Object entityReference) {
        if (!(entityReference instanceof Entity entity)
                || entity.level() != player.level()
                || entity.isRemoved()) {
            return Optional.empty();
        }
        var position = entity.position();
        return Optional.of(new ProgramWorldPosition(
                entity.level().dimension().identifier(),
                position.x,
                position.y,
                position.z
        ));
    }

    @Override
    public Optional<ProgramDirection> lookDirectionOf(Object entityReference) {
        if (!(entityReference instanceof Entity entity)
                || entity.level() != player.level()
                || entity.isRemoved()) {
            return Optional.empty();
        }
        var direction = entity.getLookAngle();
        if (direction.lengthSqr() <= 1.0E-12) return Optional.empty();
        return Optional.of(new ProgramDirection(direction.x, direction.y, direction.z));
    }

    @Override
    public List<?> entitiesAround(ProgramWorldPosition center, double radius) {
        var origin = requireLocalOrigin(center);
        var boundedRadius = requireRange(radius);
        return player.level().getEntities(
                        player,
                        new AABB(origin, origin).inflate(boundedRadius),
                        entity -> entity.isAlive() && !entity.isRemoved() && !entity.isSpectator()
                ).stream()
                .sorted(Comparator.comparing(entity -> entity.getUUID().toString()))
                .limit(MAX_QUERY_RESULTS)
                .toList();
    }

    @Override
    public Optional<ProgramBlockPosition> raycastBlock(
            ProgramWorldPosition origin,
            ProgramDirection direction,
            double maximumDistance
    ) {
        var start = requireLocalOrigin(origin);
        var distance = requireRange(maximumDistance);
        var end = start.add(vector(direction).scale(distance));
        var hit = player.level().clip(new ClipContext(
                start,
                end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        ));
        if (hit.getType() != HitResult.Type.BLOCK) return Optional.empty();
        var block = hit.getBlockPos();
        return Optional.of(new ProgramBlockPosition(
                player.level().dimension().identifier(),
                block.getX(),
                block.getY(),
                block.getZ()
        ));
    }

    @Override
    public Optional<ProgramDirection> blockNormalFromView(
            Object entityReference,
            double maximumDistance
    ) {
        if (!(entityReference instanceof Entity entity)
                || entity.level() != player.level()
                || entity.isRemoved()) {
            return Optional.empty();
        }
        var look = entity.getViewVector(1.0f);
        if (look.lengthSqr() <= 1.0E-12) return Optional.empty();
        var start = requireLocalOrigin(new ProgramWorldPosition(
                player.level().dimension().identifier(),
                entity.getEyePosition().x,
                entity.getEyePosition().y,
                entity.getEyePosition().z
        ));
        return clipNormal(entity, start, look, requireRange(maximumDistance));
    }

    @Override
    public Optional<ProgramDirection> raycastBlockNormal(
            ProgramWorldPosition origin,
            ProgramDirection direction,
            double maximumDistance
    ) {
        return clipNormal(
                player,
                requireLocalOrigin(origin),
                vector(direction),
                requireRange(maximumDistance)
        );
    }

    @Override
    public Optional<Object> raycastEntity(
            ProgramWorldPosition origin,
            ProgramDirection direction,
            double maximumDistance
    ) {
        var start = requireLocalOrigin(origin);
        var distance = requireRange(maximumDistance);
        var fullEnd = start.add(vector(direction).scale(distance));
        var blockHit = player.level().clip(new ClipContext(
                start,
                fullEnd,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        ));
        var end = blockHit.getType() == HitResult.Type.MISS
                ? fullEnd
                : blockHit.getLocation();
        var hit = ProjectileUtil.getEntityHitResult(
                player.level(),
                player,
                start,
                end,
                new AABB(start, end).inflate(1.0),
                entity -> entity != player
                        && entity.isAlive()
                        && !entity.isRemoved()
                        && !entity.isSpectator()
                        && entity.isPickable(),
                0.3f
        );
        return hit == null ? Optional.empty() : Optional.of(hit.getEntity());
    }

    private Vec3 requireLocalOrigin(ProgramWorldPosition position) {
        if (!position.dimension().equals(player.level().dimension().identifier())) {
            throw new IllegalArgumentException("Precision query origin is in another dimension");
        }
        var origin = new Vec3(position.x(), position.y(), position.z());
        if (origin.distanceToSqr(player.position()) > MAX_QUERY_RANGE * MAX_QUERY_RANGE) {
            throw new IllegalArgumentException("Precision query origin is outside the allowed area");
        }
        return origin;
    }

    private static double requireRange(double range) {
        if (!Double.isFinite(range) || range < 0.0 || range > MAX_QUERY_RANGE) {
            throw new IllegalArgumentException("Precision query range is outside the allowed limit");
        }
        return range;
    }

    private static Vec3 vector(ProgramDirection direction) {
        return new Vec3(direction.x(), direction.y(), direction.z());
    }

    private Optional<ProgramDirection> clipNormal(
            Entity source,
            Vec3 start,
            Vec3 direction,
            double distance
    ) {
        if (direction.lengthSqr() <= 1.0E-12) return Optional.empty();
        var hit = player.level().clip(new ClipContext(
                start,
                start.add(direction.normalize().scale(distance)),
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                source
        ));
        if (hit.getType() != HitResult.Type.BLOCK) return Optional.empty();
        var face = hit.getDirection();
        return Optional.of(new ProgramDirection(face.getStepX(), face.getStepY(), face.getStepZ()));
    }
}
