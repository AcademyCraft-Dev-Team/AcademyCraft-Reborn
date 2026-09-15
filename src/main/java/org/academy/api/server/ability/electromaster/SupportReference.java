package org.academy.api.server.ability.electromaster;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

/**
 * A resolved support surface: the block providing it, the closest point of its collision shape to the
 * subject, which side of the support the subject is on, and the distance between them.
 *
 * <p>{@code face} follows {@link net.minecraft.world.phys.BlockHitResult#getDirection()} semantics, so it
 * describes the support surface facing the subject: {@link Direction#UP} is ground below the subject,
 * {@link Direction#DOWN} a ceiling above, and a horizontal direction a wall beside it. Terrain-following
 * height must only ever be driven from a {@link Direction#UP} reference, which is what keeps a nearby
 * wall or ceiling from becoming an altitude anchor.</p>
 */
public record SupportReference(BlockPos pos, Vec3 closestPoint, Direction face, double distance) {
    public SupportReference {
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(closestPoint, "closestPoint");
        Objects.requireNonNull(face, "face");
    }

    /** Whether this reference is an upward-facing surface the subject is floating above. */
    public boolean isGround() {
        return face == Direction.UP;
    }

    /** Height of this surface at the closest point; ground references report their top face. */
    public double surfaceY() {
        return closestPoint.y;
    }
}
