package org.academy.internal.common.ability.program;

import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.program.*;
import org.academy.internal.common.ability.program.compile.ProgramCompiler;
import org.academy.internal.common.ability.program.migrate.ProgramNumericUpgrades;
import org.academy.internal.common.ability.program.registry.AbilityProgramDefinitions;
import org.academy.internal.common.ability.program.registry.BaseAbilityProgramDefinition;
import org.academy.internal.common.ability.program.registry.CommonProgramNodeIds;
import org.academy.internal.common.ability.program.registry.ProgramExecutorLookup;
import org.academy.internal.common.ability.program.registry.ProgramNodeExecutor;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.*;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.*;

/** Independent expected answers and VM-level contracts, not a direct comparison of shared math helpers. */
class ProgramNumericUpgradeEquivalenceTest {
    private static final Identifier CATEGORY = AcademyCraft.academy("accelerator");
    private record Operation(String kind, String operator, boolean comparison) {
        Identifier legacy() { return AcademyCraft.academy("program/core/logic/" + kind + "/" + operator); }
        ProgramValueType output() { return comparison ? ProgramValueTypes.BOOLEAN : type(kind); }
    }
    private static List<Operation> operations() {
        var result = new ArrayList<Operation>();
        for (String kind : List.of("integer", "big_integer", "float")) {
            for (String operator : List.of("add", "subtract", "multiply", "divide", "modulo")) {
                if (!(kind.equals("integer") && operator.equals("divide"))) result.add(new Operation(kind, operator, false));
            }
            for (String operator : List.of("equal", "less", "less_equal", "greater", "greater_equal")) result.add(new Operation(kind, operator, true));
        }
        return result;
    }

    @Test void allTwentyNineMappingsProduceIndependentGoldenAnswers() {
        assertEquals(29, operations().size());
        for (var operation : operations()) {
            var old = simple(operation, "3", "2"); var replacement = upgraded(old);
            Object expected;
            if (operation.comparison) expected = operation.operator.equals("greater") || operation.operator.equals("greater_equal");
            else {
                String number = switch (operation.operator) { case "add" -> "5"; case "subtract", "modulo" -> "1";
                    case "multiply" -> "6"; case "divide" -> operation.kind.equals("float") ? "1.5" : "1"; default -> throw new AssertionError(); };
                expected = switch (operation.kind) { case "integer" -> Integer.valueOf(number); case "big_integer" -> new BigInteger(number);
                    case "float" -> Double.valueOf(number); default -> throw new AssertionError(); };
            }
            for (var program : List.of(old, replacement)) {
                var run = run(program, null, null, 0);
                assertEquals(ProgramVmResult.Status.COMPLETED, run.result.status(), operation.toString());
                assertEquals(new ProgramValue<>(operation.output(), expected), run.session.variables().get("answer"), operation.toString());
                assertEquals(0, run.transaction.size(), "A numeric upgrade must not introduce a world action or charge");
            }
        }
    }

    @Test void overflowDivisionAndNonfiniteFailuresKeepTheirSourceAndDiagnostic() {
        record Failure(Operation operation, String left, String right) {}
        for (var failure : List.of(
                new Failure(new Operation("integer", "add", false), "2147483647", "1"),
                new Failure(new Operation("integer", "subtract", false), "-2147483648", "1"),
                new Failure(new Operation("integer", "multiply", false), "2147483647", "2"),
                new Failure(new Operation("integer", "modulo", false), "7", "0"),
                new Failure(new Operation("big_integer", "divide", false), "7", "0"),
                new Failure(new Operation("big_integer", "modulo", false), "7", "0"),
                new Failure(new Operation("float", "divide", false), "1", "0"),
                new Failure(new Operation("float", "modulo", false), "1", "0"),
                new Failure(new Operation("float", "multiply", false), "1.7976931348623157E308", "2")
        )) {
            var old = simple(failure.operation, failure.left, failure.right);
            var first = run(old, null, null, 0); var second = run(upgraded(old), null, null, 0);
            assertEquals(ProgramVmResult.Status.FAILED, first.result.status(), failure.toString());
            assertEquals(3, first.result.nodeId(), "Failure must identify the original numeric source node");
            assertEquals(first.result, second.result, "status, source id, diagnostic, port and detail must agree");
            assertTrue(first.session.variables().isEmpty()); assertTrue(second.session.variables().isEmpty());
            assertEquals(0, first.transaction.size()); assertEquals(0, second.transaction.size());
        }
    }

    @Test void signedZeroLargeIntegersAndNegativeRemaindersKeepTheirDocumentedMeaning() {
        assertAnswer(new Operation("float", "equal", true), "-0.0", "0.0", false);
        assertAnswer(new Operation("float", "less", true), "-0.0", "0.0", true);
        assertAnswer(new Operation("integer", "modulo", false), "-7", "3", -1);
        assertAnswer(new Operation("big_integer", "modulo", false), "-7", "3", BigInteger.valueOf(-1));
        assertAnswer(new Operation("big_integer", "greater", true), "1000000000000000000000000000001", "1000000000000000000000000000000", true);
        assertAnswer(new Operation("float", "divide", false), "-7", "2", -3.5);
    }

    @Test void excludedIntegerDivisionRetainsItsActualOverflowCounterexample() {
        var operation = new Operation("integer", "divide", false); var old = simple(operation, "-2147483648", "-1");
        var plan = ProgramNumericUpgrades.preview(old, AbilityProgramDefinitions.require(CATEGORY), Set.of());
        assertFalse(plan.canApply()); assertEquals(old, plan.candidate());
        var oldRun = run(old, null, null, 0);
        assertEquals(Integer.MIN_VALUE, oldRun.session.variables().get("answer").value());
        var checked = replaceOperation(old, operation); var newRun = run(checked, null, null, 0);
        assertEquals(ProgramVmResult.Status.FAILED, newRun.result.status());
        assertEquals(3, newRun.result.nodeId());
    }

    @Test void liveClockQueriesStillReevaluateOncePerActionAcrossWait() {
        var old = waiting(false); var replacement = upgraded(old);
        List<String> previous = null;
        for (var program : List.of(old, replacement)) {
            var log = new ArrayList<String>(); var clock = new long[]{100};
            LongSupplier live = () -> { long value = clock[0]; log.add("time=" + value); clock[0] += 100; return value; };
            var run = run(program, live, null, 0);
            assertEquals(ProgramVmResult.Status.SUSPENDED, run.result.status());
            assertEquals(BigInteger.valueOf(102), run.session.variables().get("before").value());
            assertEquals(ProgramVmResult.Status.SUSPENDED, run.session.run(1, 1024, executors(), run.frame).status());
            assertEquals(List.of("time=100"), log, "Waiting must not poll or resample the data branch");
            assertEquals(ProgramVmResult.Status.COMPLETED, run.session.run(2, 1024, executors(), run.frame).status());
            assertEquals(BigInteger.valueOf(202), run.session.variables().get("after").value());
            assertEquals(List.of("time=100", "time=200"), log);
            if (previous != null) assertEquals(previous, log); previous = List.copyOf(log);
        }
    }

    @Test void actualRandomSourceKeepsPerNodeLatchAndEvaluationOrderAcrossWait() {
        var old = waiting(true); var replacement = upgraded(old);
        List<Integer> previous = null;
        for (var program : List.of(old, replacement)) {
            var log = new ArrayList<Integer>();
            ProgramExecutorLookup counted = id -> recording(executors().find(id), id, log);
            var run = run(program, null, counted, 0);
            assertEquals(ProgramVmResult.Status.SUSPENDED, run.result.status());
            assertEquals(ProgramVmResult.Status.COMPLETED, run.session.run(2, 1024, counted, run.frame).status());
            var before = (BigInteger) run.session.variables().get("before").value();
            assertEquals(before, run.session.variables().get("after").value(), "The same real random node must retain its invocation draw");
            assertTrue(before.compareTo(BigInteger.valueOf(12)) >= 0 && before.compareTo(BigInteger.valueOf(22)) <= 0);
            assertEquals(List.of(1, 1), log, "Each action resolves the same node; its original executor retains the sample");
            if (previous != null) assertEquals(previous, log); previous = List.copyOf(log);
        }
    }

    private static <C> ProgramNodeExecutor<C> recording(ProgramNodeExecutor<C> executor, Identifier id, List<Integer> log) {
        return (context, config, inputs) -> {
            if (id.equals(CommonProgramNodeIds.RANDOM_NUMBER)) log.add(context.nodeId());
            return executor.execute(context, config, inputs);
        };
    }
    private static void assertAnswer(Operation operation, String left, String right, Object expected) {
        var old = simple(operation, left, right);
        for (var program : List.of(old, upgraded(old))) {
            var result = run(program, null, null, 0);
            assertEquals(ProgramVmResult.Status.COMPLETED, result.result.status());
            assertEquals(expected, result.session.variables().get("answer").value());
        }
    }
    private static AbilityProgram simple(Operation operation, String left, String right) {
        return program(new ProgramGraph(List.of(entry(), scalar(1, operation.kind, left), scalar(2, operation.kind, right),
                node(3, operation.legacy(), new JsonObject()), variable(4, "answer", operation.output()), node(5, CommonProgramNodeIds.STOP, new JsonObject())),
                List.of(edge(0, "flow", 4, "flow"), edge(4, "flow", 5, "flow"), edge(1, "value", 3, "left"),
                        edge(2, "value", 3, "right"), edge(3, "result", 4, "value"))));
    }
    private static AbilityProgram waiting(boolean random) {
        var config = new JsonObject(); config.addProperty("type", "big_integer"); config.addProperty("lower", "10"); config.addProperty("upper", "20");
        var source = node(1, random ? CommonProgramNodeIds.RANDOM_NUMBER : CommonProgramNodeIds.GAME_TIME, random ? config : new JsonObject());
        return program(new ProgramGraph(List.of(entry(), source, scalar(2, "big_integer", "2"),
                node(3, CommonProgramNodeIds.BIG_INTEGER_ADD, new JsonObject()), variable(4, "before", ProgramValueTypes.BIG_INTEGER),
                node(5, CommonProgramNodeIds.WAIT, new JsonObject()), scalar(6, "integer", "2"),
                variable(7, "after", ProgramValueTypes.BIG_INTEGER), node(8, CommonProgramNodeIds.STOP, new JsonObject())),
                List.of(edge(0, "flow", 4, "flow"), edge(4, "flow", 5, "flow"), edge(5, "flow", 7, "flow"), edge(7, "flow", 8, "flow"),
                        edge(1, random ? "value" : "time", 3, "left"), edge(2, "value", 3, "right"),
                        edge(3, "result", 4, "value"), edge(3, "result", 7, "value"), edge(6, "value", 5, "ticks"))));
    }
    private record Run(ProgramVm.Session session, ProgramVmResult result, ProgramExecutionFrame frame, ProgramActionTransaction transaction) {}
    private static Run run(AbilityProgram program, LongSupplier clock, ProgramExecutorLookup lookup, long tick) {
        var definition = AbilityProgramDefinitions.require(CATEGORY);
        var compiled = ProgramCompiler.compile(program.graph(), new ProgramCompileContext(CATEGORY, Set.of(), ProgramLimits.DEFAULT), definition.nodeLookup());
        assertTrue(compiled.valid(), () -> compiled.diagnostics().toString());
        var transaction = ProgramActionTransaction.sequential(); var frame = new ProgramExecutionFrame(transaction, null, null, clock);
        var session = new ProgramVm.Session(Objects.requireNonNull(compiled.program()));
        return new Run(session, session.run(tick, 1024, lookup == null ? executors() : lookup, frame), frame, transaction);
    }
    private static AbilityProgram upgraded(AbilityProgram old) {
        var plan = ProgramNumericUpgrades.preview(old, AbilityProgramDefinitions.require(CATEGORY), Set.of());
        assertTrue(plan.canApply(), () -> plan.diagnostics().toString()); return plan.candidate();
    }
    private static AbilityProgram replaceOperation(AbilityProgram old, Operation operation) {
        var config = new JsonObject(); config.addProperty("type", operation.kind); config.addProperty("operator", operation.operator);
        return new AbilityProgram(1, old.id(), old.name(), old.category(), new ProgramGraph(old.graph().nodes().stream()
                .map(node -> node.id() == 3 ? node(3, CommonProgramNodeIds.NUMERIC_ARITHMETIC, config) : node).toList(), old.graph().edges()), old.editorLayout());
    }
    private static ProgramExecutorLookup executors() { return AbilityProgramDefinitions.require(CATEGORY).executors(); }
    private static AbilityProgram program(ProgramGraph graph) { return new AbilityProgram(1, UUID.randomUUID(), "numeric contract", CATEGORY, graph, ProgramEditorLayout.EMPTY); }
    private static ProgramGraph.Node entry() { return node(0, BaseAbilityProgramDefinition.entryId(CATEGORY), new JsonObject()); }
    private static ProgramGraph.Node scalar(int id, String kind, String value) {
        var config = new JsonObject(); config.addProperty("type", kind); config.addProperty("value", value); return node(id, CommonProgramNodeIds.SCALAR_CONSTANT, config);
    }
    private static ProgramGraph.Node variable(int id, String name, ProgramValueType type) {
        var config = new JsonObject(); config.addProperty("name", name); config.addProperty("type", type.id().toString()); return node(id, CommonProgramNodeIds.VARIABLE_SET, config);
    }
    private static ProgramGraph.Node node(int id, Identifier type, JsonObject config) { return new ProgramGraph.Node(id, type, 1, config); }
    private static ProgramGraph.Edge edge(int from, String output, int to, String input) { return new ProgramGraph.Edge(new ProgramGraph.Endpoint(from, output), new ProgramGraph.Endpoint(to, input)); }
    private static ProgramValueType type(String kind) { return switch (kind) { case "integer" -> ProgramValueTypes.INTEGER;
        case "big_integer" -> ProgramValueTypes.BIG_INTEGER; case "float" -> ProgramValueTypes.FLOAT; default -> throw new IllegalArgumentException(); }; }
}
