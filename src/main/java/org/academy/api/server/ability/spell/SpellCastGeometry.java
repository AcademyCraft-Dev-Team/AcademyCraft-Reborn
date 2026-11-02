package org.academy.api.server.ability.spell;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

import static org.academy.api.server.ability.spell.SpellGeometryException.Reason.*;

/** Shared normalization for player, NPC and precision-operation native-spell adapters. */
public final class SpellCastGeometry {
    public static final double MAX_ORIGIN_DISTANCE = 48;
    public static final double MAX_AIM_DISTANCE = 64;
    private final SpatialCastRequest request;
    private final Vec3 origin;
    private final Vec3 direction;

    private SpellCastGeometry(SpatialCastRequest request, Vec3 origin, Vec3 direction) {
        this.request = request;
        this.origin = origin;
        this.direction = direction;
    }

    /** Validates currently loaded server geometry, without loading chunks or settling resources. */
    public static SpellCastGeometry resolve(LivingEntity caster, SpatialCastRequest request) {
        Objects.requireNonNull(caster, "caster");
        if (!(caster.level() instanceof ServerLevel level) || !level.getServer().isSameThread())
            throw new IllegalStateException("Spell geometry requires the server thread");
        if (!caster.isAlive() || caster.isRemoved()) throw new SpellGeometryException(UNAVAILABLE, "Caster unavailable");
        var target = request.target();
        if (target != null && (target.level() != level || !target.isAlive() || target.isRemoved()
                || level.getEntity(target.getUUID()) != target)) throw new SpellGeometryException(TARGET_UNREACHABLE, "Target unavailable");
        var resolved = resolve(request, caster.getEyePosition(), caster.getLookAngle());
        if (request.origin() != null && !level.hasChunkAt(BlockPos.containing(resolved.origin)))
            throw new SpellGeometryException(INVALID_INPUT, "Origin chunk is not loaded");
        if (request.endpoint() != null && !level.hasChunkAt(BlockPos.containing(request.endpoint())))
            throw new SpellGeometryException(INVALID_INPUT, "Endpoint chunk is not loaded");
        return resolved;
    }

    /** Pure geometry normalization; callers executing a spell must also use the actor overload. */
    public static SpellCastGeometry resolve(SpatialCastRequest request, Vec3 nativeOrigin, Vec3 nativeDirection) {
        Objects.requireNonNull(request, "request");
        finite(nativeOrigin, "native origin");
        var origin = request.origin() == null ? nativeOrigin : finite(request.origin(), "origin");
        if (origin.distanceToSqr(nativeOrigin) > MAX_ORIGIN_DISTANCE * MAX_ORIGIN_DISTANCE)
            throw new SpellGeometryException(INVALID_INPUT, "Origin is out of range");
        Vec3 aim = request.endpoint();
        if (aim != null) finite(aim, "endpoint");
        if (request.target() != null) {
            var center = finite(request.target().getBoundingBox().getCenter(), "target");
            if (center.distanceToSqr(origin) > MAX_AIM_DISTANCE * MAX_AIM_DISTANCE)
                throw new SpellGeometryException(TARGET_UNREACHABLE, "Target is out of range");
            if (aim == null && request.direction() == null) aim = center;
        }
        Vec3 derived = null;
        if (aim != null) {
            var delta = aim.subtract(origin);
            if (delta.lengthSqr() > MAX_AIM_DISTANCE * MAX_AIM_DISTANCE)
                throw new SpellGeometryException(INVALID_INPUT, "Endpoint is out of range");
            derived = unit(delta, "origin and endpoint must differ");
        }
        var explicit = request.direction() == null ? null : unit(request.direction(), "direction");
        if (explicit != null && derived != null && explicit.dot(derived) < 1 - 1.0e-6)
            throw new SpellGeometryException(CONFLICTING_INPUTS, "Direction conflicts with endpoint");
        var direction = explicit != null ? explicit : derived != null ? derived : unit(nativeDirection, "native direction");
        if (request.target() != null && (request.endpoint() != null || request.direction() != null)) {
            double distance = request.endpoint() == null ? MAX_AIM_DISTANCE : request.endpoint().distanceTo(origin);
            var box = request.target().getBoundingBox().inflate(.1);
            if (!box.contains(origin) && box.clip(origin, origin.add(direction.scale(distance))).isEmpty())
                throw new SpellGeometryException(CONFLICTING_INPUTS, "Direction or endpoint does not aim at the requested target");
        }
        return new SpellCastGeometry(request, origin, direction);
    }

    public SpatialCastRequest request() { return request; }
    public Vec3 origin() { return origin; }
    public Vec3 direction() { return direction; }
    public @Nullable Vec3 endpoint() { return request.endpoint(); }
    public @Nullable LivingEntity target() { return request.target(); }
    public boolean isNative() { return request.isNative(); }
    public boolean hasOrigin() { return request.origin() != null; }
    public boolean hasEndpoint() { return request.endpoint() != null; }
    public boolean hasDirection() { return request.direction() != null; }
    public boolean hasTarget() { return request.target() != null; }

    /** Preserve moving native anchors during charge/channel ticks when the origin is unspecified. */
    public Vec3 originFor(LivingEntity caster) { return hasOrigin() ? origin : caster.getEyePosition(); }

    public Vec3 directionFor(LivingEntity caster) {
        if (hasDirection()) return direction;
        if (hasEndpoint()) return unit(request.endpoint().subtract(originFor(caster)), "endpoint");
        if (hasTarget()) return unit(request.target().getBoundingBox().getCenter().subtract(originFor(caster)), "target");
        return unit(caster.getLookAngle(), "native direction");
    }

    /** Native ray length is always an upper bound, including explicit endpoint/target requests. */
    public double rayLength(LivingEntity caster, double nativeMaximum) {
        if (!Double.isFinite(nativeMaximum) || nativeMaximum < 0) throw new IllegalArgumentException("Invalid native range");
        return hasEndpoint() ? Math.min(nativeMaximum, request.endpoint().distanceTo(originFor(caster))) : nativeMaximum;
    }

    private static Vec3 finite(Vec3 value, String label) {
        if (value == null || !Double.isFinite(value.x) || !Double.isFinite(value.y) || !Double.isFinite(value.z)
                || !Double.isFinite(value.lengthSqr())) throw new SpellGeometryException(INVALID_INPUT, "Non-finite " + label);
        return value;
    }
    private static Vec3 unit(Vec3 value, String label) {
        finite(value, label);
        if (value.lengthSqr() < 1.0e-12) throw new SpellGeometryException(INVALID_INPUT, "Zero " + label);
        return value.scale(1.0 / Math.sqrt(value.lengthSqr()));
    }
}
