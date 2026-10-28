package org.academy.internal.common.ability.program;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.ability.program.*;
import org.academy.api.common.structure.StructurePhysicsControl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProgramStructureMovementTest {
    @Test void displacementsKeepTheirMagnitudeAndRejectOverflowBeforeAnyAcquisition() {
        assertEquals(new ProgramVector(0, -64, 0), new ProgramStructureMoveRequest(new ProgramVector(0, -64, 0), 200).displacement());
        assertDoesNotThrow(() -> new ProgramStructureMoveRequest(new ProgramVector(0, 0, 0), 2));
        for (var offset : List.of(new ProgramVector(64, 1, 0), new ProgramVector(Double.MAX_VALUE, 0, 0))) {
            assertThrows(IllegalArgumentException.class, () -> new ProgramStructureMoveRequest(offset, 20));
        }
        for (int ticks : List.of(0, 1, 201, Integer.MAX_VALUE)) assertThrows(IllegalArgumentException.class,
                () -> new ProgramStructureMoveRequest(new ProgramVector(1, 0, 0), ticks));
    }

    @Test void eightBodiesAcrossDifferentGroupsCannotMultiplyTheActorsForceCeiling() {
        var force = new Vec3(-3000, 4000, 0);
        var limited = ServerStructureGroupMovement.limited(force);
        assertEquals(force.normalize(), limited.normalize());
        assertEquals(StructurePhysicsControl.MAX_FORCE, limited.length() * ProgramStructureGroupControl.MAX_MEMBERS, 1e-8);
        assertEquals(Vec3.ZERO, ServerStructureGroupMovement.limited(Vec3.ZERO));
        assertEquals(new Vec3(3, -4, 0), ServerStructureGroupMovement.limited(new Vec3(3, -4, 0)));
    }

    @Test void movementConfigurationRoundTripsAndDefaultsWithoutWideningTheForceWindow() {
        var codec = ProgramStructureMovementNodes.Configuration.CODEC;
        assertEquals(20, codec.parse(JsonOps.INSTANCE, new JsonObject()).getOrThrow().request(new ProgramVector(0,0,1)).durationTicks());
        var value = new ProgramStructureMovementNodes.Configuration(.125);
        assertEquals(value, codec.parse(JsonOps.INSTANCE, codec.encodeStart(JsonOps.INSTANCE, value).getOrThrow()).getOrThrow());
        assertEquals(3, value.request(new ProgramVector(0,0,1)).durationTicks());
        for (double seconds : List.of(-1.0, 0.0, .05, 10.01, Double.POSITIVE_INFINITY, Double.NaN)) {
            var config = new JsonObject(); config.addProperty("duration_seconds", seconds);
            assertTrue(codec.parse(JsonOps.INSTANCE, config).error().isPresent());
        }
        var runtime = new ProgramStructureGroupRuntime() {};
        assertEquals(ProgramStructureOperationResult.Status.UNAVAILABLE,
                runtime.moveStructures(List.of(), new ProgramStructureMoveRequest(new ProgramVector(1,0,0), 20)).result().status());
    }
}
