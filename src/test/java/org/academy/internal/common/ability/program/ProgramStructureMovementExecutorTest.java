package org.academy.internal.common.ability.program;

import org.academy.api.common.ability.program.*;
import net.minecraft.resources.Identifier;
import org.academy.api.common.structure.StructureRef;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ProgramStructureMovementExecutorTest {
    private static final ProgramStructureControl CONTROL = new ProgramStructureControl(new StructureRef(UUID.randomUUID(),
            Identifier.parse("minecraft:overworld"), Identifier.parse("academy:test"), 1),
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    @Test void waitsForTheForceWindowSubmitsOnceAndDoesNotRereadTargetsOrChargeOnPoll() {
        var clock = new AtomicLong(100);
        var starts = new AtomicInteger(); var stops = new AtomicInteger();
        var state = new HashMap<String,Object>();
        var runtime = new ProgramStructureControlRuntime() {
            @Override public ProgramStructureOperationResult structureControlStatus(ProgramStructureControl control) {
                assertEquals(CONTROL,control); return ProgramStructureOperationResult.completed("active");
            }
            @Override public ProgramStructureOperationResult finishStructureMovement(ProgramStructureControl control) {
                stops.incrementAndGet(); return ProgramStructureOperationResult.completed("stopped");
            }
        };
        var context = new ProgramVmContext(100,new HashMap<>(),state,
                new ProgramExecutionFrame(new ProgramActionTransaction(),runtime,null,clock::get));
        context.enterNode(2,Identifier.parse("academy:program/accelerator/action/structure/move"));
        var step=ProgramStructureMovementExecutors.run(context,runtime,
                () -> new ProgramStructureMovementExecutors.Request(CONTROL,null,null,1),
                request -> { starts.incrementAndGet(); return ProgramStructureOperationResult.completed("accepted"); });
        assertEquals(ProgramNodeStep.Directive.RETRY,step.directive()); assertTrue(step.outputs().isEmpty());
        clock.set(119);
        assertEquals(ProgramNodeStep.Directive.RETRY,ProgramStructureMovementExecutors.run(context,runtime,
                () -> { throw new AssertionError("re-read input"); }, request -> { throw new AssertionError("charged twice"); }).directive());
        clock.set(120);
        var done=ProgramStructureMovementExecutors.run(context,runtime,
                () -> { throw new AssertionError("re-read input"); }, request -> { throw new AssertionError("charged twice"); });
        assertEquals(ProgramNodeStep.Directive.CONTINUE,done.directive());
        assertEquals(CONTROL,done.outputs().get("control").value());
        assertEquals(1,starts.get()); assertEquals(1,stops.get()); assertTrue(state.isEmpty());
    }
    @Test void lostControlRejectsWithoutFinishingOrResubmittingAndInvalidDurationNeverStarts() {
        var state = new HashMap<String,Object>(); var lost = new java.util.concurrent.atomic.AtomicBoolean();
        var runtime = new ProgramStructureControlRuntime() {
            @Override public ProgramStructureOperationResult structureControlStatus(ProgramStructureControl control) {
                return lost.get()?ProgramStructureOperationResult.rejected("control_lease_lost"):ProgramStructureOperationResult.completed("active");
            }
            @Override public ProgramStructureOperationResult finishStructureMovement(ProgramStructureControl control) {
                throw new AssertionError("lost handle stopped successor");
            }
        };
        var context=new ProgramVmContext(0,new HashMap<>(),state,new ProgramExecutionFrame(new ProgramActionTransaction(),runtime));
        context.enterNode(2,Identifier.parse("academy:test"));
        assertThrows(IllegalArgumentException.class,() -> new ProgramStructureMovementExecutors.Request(CONTROL,null,null,0));
        ProgramStructureMovementExecutors.run(context,runtime,() -> new ProgramStructureMovementExecutors.Request(CONTROL,null,null,.1),
                request -> ProgramStructureOperationResult.completed("accepted"));
        lost.set(true);
        assertThrows(ProgramActionFailure.class,() -> ProgramStructureMovementExecutors.run(context,runtime,
                () -> { throw new AssertionError(); }, request -> { throw new AssertionError(); }));
        assertTrue(state.isEmpty());
    }
}
