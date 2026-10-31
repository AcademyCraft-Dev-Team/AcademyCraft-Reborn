package org.academy.internal.common.ability.program;

import org.academy.api.common.ability.program.ProgramNodeStep;
import org.academy.api.common.ability.program.ProgramRunStatus;
import org.academy.internal.common.ability.program.registry.AbilityProgramDefinitions;
import org.academy.internal.common.ability.program.registry.CommonProgramNodeIds;
import org.academy.internal.common.ability.program.registry.ProgramExecutorLookup;
import org.academy.internal.common.ability.program.registry.ProgramNodeExecutor;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ProgramVmObservationTest {
    @Test void yieldKeepsItsSourceNodeWhileTheContinuationPointsDownstreamAndUsesLogicalTime() {
        var definition = AbilityProgramDefinitions.require(ProgramAutomaticBenchmarkGameTests.program(0, 4).category());
        var compiled = definition.compile(ProgramAutomaticBenchmarkGameTests.program(0, 4), Set.of());
        assertTrue(compiled.valid());
        var vm = new ProgramVm.Session(compiled.program()); var lookups = new AtomicInteger();
        ProgramExecutorLookup executors = id -> { if (id.equals(CommonProgramNodeIds.WAIT)) lookups.incrementAndGet(); return definition.executors().find(id); };
        var frame = new ProgramExecutionFrame(ProgramActionTransaction.sequential(), null, null, () -> 100000);
        assertEquals(ProgramVmResult.Status.SUSPENDED, vm.run(500, 1000, executors, frame).status());
        assertEquals(6, vm.currentNodeId());
        var view = vm.observationView();
        assertEquals(2, view.sourceNodeId()); assertEquals(CommonProgramNodeIds.WAIT, view.nodeType());
        assertEquals(500, view.logicalTick()); assertEquals(504, view.wakeAt()); assertEquals(ProgramRunStatus.WaitKind.WAIT, view.kind());
        for (int i = 0; i < 100; i++) assertEquals(view, vm.observationView());
        vm.run(501, 1000, executors, frame);
        assertEquals(1, lookups.get(), "reading observations or waiting cannot re-execute the wait action");
        assertEquals(504, vm.observationView().wakeAt());
    }

    @Test void retryKeepsTheSameNodeAndReadOnlyObservationCannotSubmitTheOperationAgain() {
        var program = ProgramAutomaticBenchmarkGameTests.program(0, 4);
        var definition = AbilityProgramDefinitions.require(program.category());
        var vm = new ProgramVm.Session(definition.compile(program, Set.of()).program());
        var attempts = new AtomicInteger();
        ProgramNodeExecutor<Object> retry = (_, _, _) -> { attempts.incrementAndGet(); return ProgramNodeStep.retry(3); };
        ProgramExecutorLookup executors = id -> id.equals(CommonProgramNodeIds.WAIT) ? retry : definition.executors().find(id);
        vm.run(100, 1000, executors, null);
        assertEquals(2, vm.currentNodeId()); assertEquals(2, vm.observationView().sourceNodeId());
        assertEquals(ProgramRunStatus.WaitKind.RETRY, vm.observationView().kind()); assertEquals(103, vm.observationView().wakeAt());
        for (int i = 0; i < 100; i++) vm.observationView();
        vm.run(102, 1000, executors, null); assertEquals(1, attempts.get());
        vm.run(103, 1000, executors, null); assertEquals(2, attempts.get());
    }

    @Test void fuelWaitDoesNotInventASleepDeadline() {
        var program = ProgramAutomaticBenchmarkGameTests.program(0, 4);
        var definition = AbilityProgramDefinitions.require(program.category());
        var vm = new ProgramVm.Session(definition.compile(program, Set.of()).program());
        assertEquals(ProgramVmResult.Status.FUEL_EXHAUSTED, vm.run(1, 1, definition.executors(), null).status());
        assertEquals(ProgramRunStatus.WaitKind.FUEL, vm.observationView().kind()); assertEquals(-1, vm.observationView().wakeAt());
    }
}
