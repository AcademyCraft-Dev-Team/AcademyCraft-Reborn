package org.academy.api.common.structure;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Planned shared client/server movement contract. AABBs select candidates and never define
 * rotated contact surfaces: empty space inside those bounds remains traversable. Only an
 * actual shape intersection or confirmed support may change entity movement.
 */
public interface StructureContactResolver {
    record Limits(int candidateStructures, int shapePairs, int iterations, double maxCorrection) {}

    /** Local cuboids remain oriented by the two poses; sweptBounds is broadphase-only. */
    record Candidate(StructureRef reference, long geometryVersion, long poseVersion,
                     StructurePose previousPose, StructurePose currentPose,
                     List<AABB> localCollisionBoxes, AABB sweptBounds) {}

    record Contact(StructureRef reference, long geometryVersion, long poseVersion,
                   Vec3 localAnchor, Vec3 worldPoint, Vec3 worldNormal,
                   double penetration, double timeOfImpact) {}

    /** resolvedMovement corrects requested movement/penetration; supportMovement is separately applied at most once. */
    record ContactFrame(UUID entityId, long simulationTick, List<Contact> contacts,
                        Vec3 resolvedMovement, Vec3 supportMovement,
                        Optional<Contact> support, boolean budgetExhausted) {}

    record CarryResult(Vec3 appliedMovement, boolean grounded, boolean rejected) {}

    /** CONTACT-01: Select bounded candidates in the entity's actual level using its swept AABB. */
    List<Candidate> broadPhase(Level host, Entity entity, Vec3 requestedMovement, Limits limits);

    /** CONTACT-02: Resolve movement/penetration against oriented shapes with identical client/server rules; preserve gaps. */
    ContactFrame narrowPhase(Level host, Entity entity, Vec3 requestedMovement,
                             List<Candidate> candidates, Limits limits);

    /** CONTACT-03: Revalidate frame identity and support before bounded authoritative carry, including rotation. */
    CarryResult applyCarry(ServerLevel host, Entity entity, ContactFrame frame, Limits limits);

    /** CONTACT-04: Release transient support history on removal, dimension change or session shutdown. */
    void clear(Level host, UUID entityId);
}
