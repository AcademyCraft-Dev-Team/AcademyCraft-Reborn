package org.academy.internal.common.sublevel.pipeline;

import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructureDynamics;
import org.academy.api.common.structure.StructurePipeline;

import java.util.Set;

/**
 * PIPE-05 draft lift lifecycle. The source world center is fixed by the request; arrival is
 * latched once, the hover deadline is arrival + hoverTicks, and a separate lift deadline yields
 * BLOCKED instead of silently extending the hover window.
 */
public final class PipelineLiftController {
    public static final double HEIGHT_TOLERANCE = 0.1;
    public static final double HORIZONTAL_TOLERANCE = 0.1;
    public static final int DEFAULT_HOVER_TICKS = 200;
    private static final Set<Double> TIERS = Set.of(3.0, 5.0, 7.0);

    public StructurePipeline.LiftState advance(StructurePipeline.LiftRequest request,
                                               StructurePipeline.LiftState previous,
                                               StructureDynamics.State actual, long serverTick) {
        if (request == null) throw new IllegalArgumentException("Lift request is required");
        if (request.epoch() == null || request.epoch().reference() == null || request.epoch().runtimeId() == null) {
            throw new IllegalArgumentException("Lift request epoch is invalid");
        }
        if (!finite(request.sourceCenter())) throw new IllegalArgumentException("Lift source center must be finite");
        if (!Double.isFinite(request.rise()) || !TIERS.contains(request.rise())) {
            throw new IllegalArgumentException("Lift rise must be one of 3/5/7 blocks");
        }
        if (request.hoverTicks() < 0 || request.hoverTicks() > 1_200) {
            throw new IllegalArgumentException("Lift hover ticks must be in [0, 1200]");
        }
        if (request.liftDeadlineTick() < 0) throw new IllegalArgumentException("Lift deadline cannot be negative");
        if (actual == null) throw new IllegalArgumentException("Lift observation is required");
        if (serverTick < 0) throw new IllegalArgumentException("Server tick cannot be negative");

        if (previous != null && !previous.epoch().equals(request.epoch())) previous = null;
        if (previous != null && terminal(previous.phase())) return previous;

        var actualCenter = actual.pose().localToWorldPoint(actual.localCenterOfMass());
        if (!finite(actualCenter)) throw new IllegalStateException("Lift observation produced a non-finite center");
        double rise = actualCenter.y - request.sourceCenter().y;
        double horizontal = Math.hypot(actualCenter.x - request.sourceCenter().x, actualCenter.z - request.sourceCenter().z);
        boolean arrived = rise >= request.rise() - HEIGHT_TOLERANCE && horizontal <= HORIZONTAL_TOLERANCE;

        if (previous == null) {
            if (serverTick > request.liftDeadlineTick()) {
                return new StructurePipeline.LiftState(request.epoch(), StructurePipeline.LiftPhase.BLOCKED,
                        -1, -1, "lift_deadline");
            }
            if (arrived) {
                long deadline = Math.addExact(serverTick, hoverTicks(request));
                return new StructurePipeline.LiftState(request.epoch(), StructurePipeline.LiftPhase.HOVERING,
                        serverTick, deadline, "arrived");
            }
            return new StructurePipeline.LiftState(request.epoch(), StructurePipeline.LiftPhase.LIFTING,
                    -1, -1, "lifting");
        }

        if (previous.phase() == StructurePipeline.LiftPhase.HOVERING && previous.arrivalTick() >= 0) {
            if (serverTick > previous.hoverDeadlineTick()) {
                return new StructurePipeline.LiftState(request.epoch(), StructurePipeline.LiftPhase.EXPIRED,
                        previous.arrivalTick(), previous.hoverDeadlineTick(), "hover_expired");
            }
            return previous;
        }

        // LIFTING from a previous slice: the separate lift timeout wins over a late arrival.
        if (serverTick > request.liftDeadlineTick()) {
            return new StructurePipeline.LiftState(request.epoch(), StructurePipeline.LiftPhase.BLOCKED,
                    -1, -1, "lift_deadline");
        }
        if (arrived) {
            long deadline = Math.addExact(serverTick, hoverTicks(request));
            return new StructurePipeline.LiftState(request.epoch(), StructurePipeline.LiftPhase.HOVERING,
                    serverTick, deadline, "arrived");
        }
        return new StructurePipeline.LiftState(request.epoch(), StructurePipeline.LiftPhase.LIFTING,
                -1, -1, "lifting");
    }

    private static int hoverTicks(StructurePipeline.LiftRequest request) {
        return request.hoverTicks() == 0 ? DEFAULT_HOVER_TICKS : request.hoverTicks();
    }

    private static boolean terminal(StructurePipeline.LiftPhase phase) {
        return phase == StructurePipeline.LiftPhase.LAUNCHED
                || phase == StructurePipeline.LiftPhase.BLOCKED
                || phase == StructurePipeline.LiftPhase.EXPIRED
                || phase == StructurePipeline.LiftPhase.CANCELLED;
    }

    private static boolean finite(Vec3 value) {
        return value != null && Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }
}
