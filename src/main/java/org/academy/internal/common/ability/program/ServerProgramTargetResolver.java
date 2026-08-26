package org.academy.internal.common.ability.program;

import net.minecraft.server.level.ServerLevel;
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
import java.util.Objects;
import java.util.Optional;

/**
 * Shared bounded world-query implementation for server-owned ability program runtimes.
 */
public final class ServerProgramTargetResolver implements ProgramTargetResolver {
    private final ServerPlayer player;
    private final double maximumRange;
    private final int maximumResults;

    public ServerProgramTargetResolver(
            ServerPlayer player,
            double maximumRange,
            int maximumResults
    ) {
        this.player = Objects.requireNonNull(player, "player");
        if (!Double.isFinite(maximumRange) || maximumRange <= 0.0 || maximumResults <= 0) {
            throw new IllegalArgumentException("Program target limits must be positive");
        }
        this.maximumRange = maximumRange;
        this.maximumResults = maximumResults;
    }

    @Override
    public Object caster() {
        return player;
    }

    @Override
    public Optional<Object> lookTarget() {
        var look = player.getViewVector(1.0f);
        if (!finiteNonZero(look)) return Optional.empty();
        return raycastEntity(
                worldPosition(player.getEyePosition()),
                new ProgramDirection(look.x, look.y, look.z),
                maximumRange
        );
    }

    @Override
    public Optional<ProgramBlockPosition> lookBlockTarget() {
        var look = player.getViewVector(1.0f);
        if (!finiteNonZero(look)) return Optional.empty();
        return raycastBlock(
                worldPosition(player.getEyePosition()),
                new ProgramDirection(look.x, look.y, look.z),
                maximumRange
        );
    }

    @Override
    public Optional<ProgramWorldPosition> positionOf(Object entityReference) {
        if (!(entityReference instanceof Entity entity) || !sameUsableLevel(entity)) {
            return Optional.empty();
        }
        return Optional.of(worldPosition(entity.position()));
    }

    @Override
    public Optional<ProgramDirection> lookDirectionOf(Object entityReference) {
        if (!(entityReference instanceof Entity entity) || !sameUsableLevel(entity)) {
            return Optional.empty();
        }
        var look = entity.getLookAngle();
        if (!finiteNonZero(look)) return Optional.empty();
        return Optional.of(new ProgramDirection(look.x, look.y, look.z));
    }

    @Override
    public List<?> entitiesAround(ProgramWorldPosition center, double radius) {
        var origin = requireLocalPosition(center);
        var boundedRadius = requireRange(radius);
        return level().getEntities(
                        player,
                        new AABB(origin, origin).inflate(boundedRadius),
                        entity -> entity.isAlive()
                                && !entity.isRemoved()
                                && !entity.isSpectator()
                                && isWithinRadius(origin, entity.position(), boundedRadius)
                ).stream()
                .sorted(Comparator.comparing(entity -> entity.getUUID().toString()))
                .limit(maximumResults)
                .toList();
    }

    @Override
    public Optional<ProgramBlockPosition> raycastBlock(
            ProgramWorldPosition origin,
            ProgramDirection direction,
            double maximumDistance
    ) {
        var start = requireLocalPosition(origin);
        var distance = requireRange(maximumDistance);
        var end = start.add(normalized(direction).scale(distance));
        var hit = level().clip(new ClipContext(
                start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) return Optional.empty();
        var block = hit.getBlockPos();
        return Optional.of(new ProgramBlockPosition(
                level().dimension().identifier(), block.getX(), block.getY(), block.getZ()));
    }

    @Override
    public Optional<ProgramDirection> blockNormalFromView(
            Object entityReference,
            double maximumDistance
    ) {
        if (!(entityReference instanceof Entity entity) || !sameUsableLevel(entity)) {
            return Optional.empty();
        }
        var look = entity.getViewVector(1.0f);
        if (!finiteNonZero(look)) return Optional.empty();
        var distance = requireRange(maximumDistance);
        return clipNormal(entity, entity.getEyePosition(), look, distance);
    }

    @Override
    public Optional<ProgramDirection> raycastBlockNormal(
            ProgramWorldPosition origin,
            ProgramDirection direction,
            double maximumDistance
    ) {
        return clipNormal(
                player,
                requireLocalPosition(origin),
                normalized(direction),
                requireRange(maximumDistance)
        );
    }

    @Override
    public Optional<Object> raycastEntity(
            ProgramWorldPosition origin,
            ProgramDirection direction,
            double maximumDistance
    ) {
        var start = requireLocalPosition(origin);
        var distance = requireRange(maximumDistance);
        var fullEnd = start.add(normalized(direction).scale(distance));
        var blockHit = level().clip(new ClipContext(
                start, fullEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        var end = blockHit.getType() == HitResult.Type.MISS
                ? fullEnd : blockHit.getLocation();
        var hit = ProjectileUtil.getEntityHitResult(
                level(),
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

    public Vec3 requireLocalPosition(ProgramWorldPosition position) {
        Objects.requireNonNull(position, "position");
        if (!position.dimension().equals(level().dimension().identifier())) {
            throw new IllegalArgumentException("Position is in another dimension");
        }
        var value = new Vec3(position.x(), position.y(), position.z());
        if (!finite(value)) throw new IllegalArgumentException("Position is not finite");
        return value;
    }

    public boolean sameUsableLevel(Entity entity) {
        return entity.level() == level() && entity.isAlive() && !entity.isRemoved();
    }

    public ServerLevel level() {
        return player.level();
    }

    /**
     * Uses the same spherical distance rule as range-limited program actions.
     */
    public static boolean isWithinRadius(Vec3 center, Vec3 position, double radius) {
        return center != null
                && position != null
                && Double.isFinite(radius)
                && radius >= 0.0
                && finite(center)
                && finite(position)
                && position.distanceToSqr(center) <= radius * radius;
    }

    private ProgramWorldPosition worldPosition(Vec3 position) {
        return new ProgramWorldPosition(
                level().dimension().identifier(), position.x, position.y, position.z);
    }

    private double requireRange(double range) {
        if (!Double.isFinite(range) || range < 0.0 || range > maximumRange) {
            throw new IllegalArgumentException("Query range is outside the allowed limit");
        }
        return range;
    }

    private static Vec3 normalized(ProgramDirection direction) {
        Objects.requireNonNull(direction, "direction");
        var value = new Vec3(direction.x(), direction.y(), direction.z());
        if (!finiteNonZero(value)) throw new IllegalArgumentException("Direction is invalid");
        return value.normalize();
    }

    private Optional<ProgramDirection> clipNormal(
            Entity source,
            Vec3 start,
            Vec3 direction,
            double distance
    ) {
        var hit = level().clip(new ClipContext(
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

    private static boolean finite(Vec3 value) {
        return value != null
                && Double.isFinite(value.x)
                && Double.isFinite(value.y)
                && Double.isFinite(value.z);
    }

    private static boolean finiteNonZero(Vec3 value) {
        return finite(value) && value.lengthSqr() > 1.0E-12;
    }
}
