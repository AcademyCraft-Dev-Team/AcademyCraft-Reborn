package org.academy.internal.common.structure.space;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructureHit;
import org.academy.api.common.structure.StructureQueries;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** Player-owned rays and short-lived action receipts; no player or world state is rewound. */
public final class NativeStructurePlayerTarget {
    private NativeStructurePlayerTarget() {}

    /** Accepts a network observation once and pins the actor and the resulting current hit. */
    public static @Nullable Observation capture(ServerPlayer player, StructureHit proposed) {
        Objects.requireNonNull(proposed, "proposed");
        var actor = Actor.capture(player);
        if (actor == null) return null;
        var hit = current(player, proposed, actor);
        return hit == null ? null : new Observation(player, proposed, actor, hit);
    }

    /**
     * A supplied observation may use the bounded server-owned motion history. A null proposal
     * always uses the actor's current ordinary ray, as needed by an ongoing mining session.
     * Action eligibility and temporal action ticks remain the caller's responsibility.
     */
    public static @Nullable StructureHit current(ServerPlayer player, @Nullable StructureHit proposed) {
        var actor = Actor.capture(player);
        return actor == null ? null : current(player, proposed, actor);
    }

    private static @Nullable StructureHit current(ServerPlayer player, @Nullable StructureHit proposed, Actor actor) {
        var end = actor.eye.add(actor.look.scale(actor.range));
        double lengthSquared = actor.eye.distanceToSqr(end);
        if (!Actor.finite(end) || lengthSquared < 1.0E-12
                || lengthSquared > StructureQueries.MAX_RAY_LENGTH * StructureQueries.MAX_RAY_LENGTH) return null;
        var hit = proposed == null ? StructureQueries.raycast(actor.host, actor.eye, end).orElse(null)
                : NativeStructureQueries.validateMoving(actor.host, actor.eye, end, proposed).currentHit().orElse(null);
        if (hit == null || !actor.matches(player)) return null;
        // validateMoving has already verified this old point against a server-published pose
        // and the real historical outline. Never use an unaccepted packet point as authority.
        var originalPoint = proposed == null ? hit.worldPoint() : proposed.worldPoint();
        if (!permitted(player, actor.host, originalPoint) || !permitted(player, actor.host, hit.worldPoint())
                || !clearEntities(player, actor.host, actor.eye, end, actor.eye.distanceToSqr(originalPoint))
                || !clearEntities(player, actor.host, actor.eye, hit.worldPoint(), actor.eye.distanceToSqr(hit.worldPoint()))
                || !actor.matches(player)) return null;
        return hit;
    }

    private static boolean permitted(ServerPlayer player, ServerLevel host, Vec3 point) {
        var position = BlockPos.containing(point);
        return host.hasChunkAt(position) && host.getWorldBorder().isWithinBounds(position)
                && host.mayInteract(player, position);
    }

    private static boolean clearEntities(ServerPlayer player, ServerLevel host, Vec3 from, Vec3 to,
                                         double maximumDistanceSquared) {
        for (var entity : host.getEntities(player, new AABB(from, to).inflate(1),
                candidate -> !candidate.isSpectator() && candidate.isPickable())) {
            if (occludes(entity.getBoundingBox(), entity.getPickRadius(), from, to, maximumDistanceSquared)) return false;
        }
        return true;
    }

    /** Shared by both ray segments; an entity behind the selected face must not block use. */
    static boolean occludes(AABB bounds, double pickRadius, Vec3 from, Vec3 to, double maximumDistanceSquared) {
        if (!Double.isFinite(pickRadius) || pickRadius < 0
                || !Double.isFinite(maximumDistanceSquared) || maximumDistanceSquared < 0) return true;
        var box = bounds.inflate(pickRadius);
        if (!Double.isFinite(box.minX) || !Double.isFinite(box.minY) || !Double.isFinite(box.minZ)
                || !Double.isFinite(box.maxX) || !Double.isFinite(box.maxY) || !Double.isFinite(box.maxZ)) return true;
        // AABB.clip excludes the segment's far endpoint. A coincident entity face still
        // occludes the reprojected segment, which ends exactly at the selected block face.
        return box.contains(from)
                || from.distanceToSqr(to) <= maximumDistanceSquared && box.distanceToSqr(to) == 0
                || box.clip(from, to)
                .filter(point -> from.distanceToSqr(point) <= maximumDistanceSquared).isPresent();
    }

    public static final class Observation {
        private final ServerPlayer player;
        private final StructureHit proposed;
        private final Actor actor;
        private final StructureHit hit;

        private Observation(ServerPlayer player, StructureHit proposed, Actor actor, StructureHit hit) {
            this.player = player;
            this.proposed = proposed;
            this.actor = actor;
            this.hit = hit;
        }

        public StructureHit hit() { return hit; }

        /** A callback cannot move the actor or adopt a newer pose/content as the original action. */
        public @Nullable StructureHit revalidate() {
            actor.requireThread();
            if (!actor.matches(player)) return null;
            var current = NativeStructurePlayerTarget.current(player, proposed, actor);
            return hit.equals(current) && actor.matches(player) ? current : null;
        }
    }

    private record Actor(ServerLevel host, Vec3 position, Vec3 eye, Vec3 look, double range) {
        private static @Nullable Actor capture(ServerPlayer player) {
            Objects.requireNonNull(player, "player");
            var host = player.level();
            if (!host.getServer().isSameThread()) throw new IllegalStateException("Structure targeting requires server thread");
            if (NativeStructureDimensions.isStorageLevel(host.dimension())) return null;
            var position = player.position();
            var eye = player.getEyePosition();
            var look = player.getLookAngle();
            double range = player.blockInteractionRange();
            if (!finite(position) || !finite(eye) || !finite(look) || !Double.isFinite(range)
                    || range <= 0 || range > StructureQueries.MAX_RAY_LENGTH
                    || look.lengthSqr() < 1.0E-12) return null;
            var actor = new Actor(host, position, eye, look, range);
            return actor.matches(player) ? actor : null;
        }

        private void requireThread() {
            if (!host.getServer().isSameThread()) throw new IllegalStateException("Structure targeting requires server thread");
        }

        private boolean matches(ServerPlayer player) {
            return player.level() == host && host.getServer().getLevel(host.dimension()) == host
                    && position.equals(player.position()) && eye.equals(player.getEyePosition())
                    && look.equals(player.getLookAngle()) && range == player.blockInteractionRange();
        }

        private static boolean finite(Vec3 value) {
            return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
        }
    }
}
