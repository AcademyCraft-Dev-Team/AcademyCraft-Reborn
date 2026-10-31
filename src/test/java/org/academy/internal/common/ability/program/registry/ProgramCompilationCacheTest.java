package org.academy.internal.common.ability.program.registry;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import org.academy.api.common.ability.program.*;
import org.academy.api.common.profiler.AcademyProfiler;
import org.academy.internal.client.validation.PrecisionPerformanceGraphs;
import org.academy.internal.common.ability.program.compile.ProgramCompileResult;
import org.academy.internal.common.ability.program.compile.ProgramCompiler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ProgramCompilationCacheTest {
    private static final Identifier CATEGORY = Identifier.parse("academy:accelerator");
    private static final Identifier CUSTOM = Identifier.parse("test:custom_control");
    private static final AbilityProgramDefinition DEFINITION = AbilityProgramDefinitions.require(CATEGORY);

    @AfterEach
    void clearGlobalTestEntries() {
        AcademyProfiler.stopZoneCapture();
        ProgramCompileCacheLifecycle.clearAll();
    }

    @Test
    void semanticReuseIgnoresEnvelopeIdentityAndLayoutButNotConfigurationOrCapabilities() {
        var original = PrecisionPerformanceGraphs.editorGraph(2);
        var capabilities = PrecisionPerformanceGraphs.capabilities(DEFINITION);
        DEFINITION.clearCompilationCache();
        var first = DEFINITION.compile(original, capabilities);
        assertTrue(first.valid());
        var renamed = new AbilityProgram(original.schemaVersion(), UUID.randomUUID(), "Different name", CATEGORY,
                original.graph(), new ProgramEditorLayout(Map.of(0, new ProgramEditorLayout.NodePosition(900, 400))));
        assertSame(first, DEFINITION.compile(renamed, capabilities));
        var changedNodes = original.graph().nodes().stream().map(node -> {
            if (node.id() == 0) return node;
            var configuration = node.configuration().getAsJsonObject();
            configuration.addProperty("duration_seconds", 2.0);
            return new ProgramGraph.Node(node.id(), node.type(), node.schemaVersion(), configuration);
        }).toList();
        var changed = new ProgramGraph(changedNodes, original.graph().edges());
        assertNotSame(first, DEFINITION.compile(changed, capabilities));
        var mutableCapabilities = new HashSet<>(capabilities);
        assertSame(first, DEFINITION.compile(original, mutableCapabilities));
        mutableCapabilities.clear();
        var denied = DEFINITION.compile(original, mutableCapabilities);
        assertFalse(denied.valid());
        assertTrue(denied.diagnostics().stream().anyMatch(value -> value.code() == ProgramDiagnosticCode.CAPABILITY_MISSING));
        mutableCapabilities.addAll(capabilities);
        assertSame(first, DEFINITION.compile(original, mutableCapabilities));
    }

    @Test
    void warmedCacheStillRunsMigrationAndRejectsWrongEnvelopeOrNodeVersions() {
        var original = PrecisionPerformanceGraphs.editorGraph(2);
        var capabilities = PrecisionPerformanceGraphs.capabilities(DEFINITION);
        DEFINITION.clearCompilationCache();
        var first = DEFINITION.compile(original, capabilities);
        AcademyProfiler.startZoneCapture();
        try { assertSame(first, DEFINITION.compile(original, capabilities)); }
        finally { AcademyProfiler.stopZoneCapture(); }
        var thread = AcademyProfiler.snapshot().getZones().get(Thread.currentThread().getName());
        long migrationRequests = thread.topSlices(100, true).stream()
                .mapToLong(slice -> slice.counters().getOrDefault("program.compile.migration_requests", 0L)).sum();
        assertEquals(1, migrationRequests);
        assertFalse(DEFINITION.compile(new AbilityProgram(AbilityProgram.CURRENT_SCHEMA_VERSION + 1,
                original.id(), original.name(), CATEGORY, original.graph(), original.editorLayout()), capabilities).valid());
        assertFalse(DEFINITION.compile(new AbilityProgram(original.schemaVersion(), original.id(), original.name(),
                Identifier.parse("academy:teleport"), original.graph(), original.editorLayout()), capabilities).valid());
        var invalidNodes = new ArrayList<>(original.graph().nodes());
        var node = invalidNodes.getLast();
        invalidNodes.set(invalidNodes.size() - 1, new ProgramGraph.Node(node.id(), node.type(),
                node.schemaVersion() + 1, node.configuration()));
        assertFalse(DEFINITION.compile(new ProgramGraph(invalidNodes, original.graph().edges()), capabilities).valid());
    }

    @Test
    void parsedNumericEqualityCollisionCannotReturnAnotherConfiguration() {
        var firstNumber = JsonParser.parseString("9007199254740992");
        var secondNumber = JsonParser.parseString("9007199254740993");
        assertEquals(firstNumber, secondNumber, "Gson 2.14 uses double equality for parsed numbers");
        assertEquals(firstNumber.hashCode(), secondNumber.hashCode());
        assertNotEquals(firstNumber.getAsInt(), secondNumber.getAsInt());
        var firstConfig = new JsonObject(); firstConfig.add("value", firstNumber);
        var secondConfig = new JsonObject(); secondConfig.add("value", secondNumber);
        var cache = new ProgramCompilationCache();
        var calls = new AtomicInteger();
        var first = compile(cache, graph(CommonProgramNodeIds.INTEGER_CONSTANT, firstConfig), Set.of(), calls);
        var second = compile(cache, graph(CommonProgramNodeIds.INTEGER_CONSTANT, secondConfig), Set.of(), calls);
        assertEquals(0, ((CommonProgramNodeCatalog.IntegerConfiguration) first.program().nodes().get(1).configuration()).value());
        assertEquals(1, ((CommonProgramNodeCatalog.IntegerConfiguration) second.program().nodes().get(1).configuration()).value());
        assertEquals(2, calls.get());
        assertEquals(2, cache.stats().entries());
    }

    @Test
    void exactNumberTextAndTypeRemainDistinctWhileObjectKeyOrderCanReuse() {
        var cache = new ProgramCompilationCache();
        var calls = new AtomicInteger();
        var positive = JsonParser.parseString("{\"value\":0.0}").getAsJsonObject();
        var negative = JsonParser.parseString("{\"value\":-0.0}").getAsJsonObject();
        var first = compile(cache, graph(CommonProgramNodeIds.FLOAT_CONSTANT, positive), Set.of(), calls);
        var second = compile(cache, graph(CommonProgramNodeIds.FLOAT_CONSTANT, negative), Set.of(), calls);
        assertNotSame(first, second);
        assertNotEquals(Double.doubleToRawLongBits(((CommonProgramNodeCatalog.FloatConfiguration)
                        first.program().nodes().get(1).configuration()).value()),
                Double.doubleToRawLongBits(((CommonProgramNodeCatalog.FloatConfiguration)
                        second.program().nodes().get(1).configuration()).value()));
        var ordered = new JsonObject(); ordered.addProperty("value", 7); ordered.addProperty("unused", "safe");
        var reversed = new JsonObject(); reversed.addProperty("unused", "safe"); reversed.addProperty("value", 7);
        var canonical = compile(cache, graph(CommonProgramNodeIds.INTEGER_CONSTANT, ordered), Set.of(), calls);
        assertSame(canonical, compile(cache, graph(CommonProgramNodeIds.INTEGER_CONSTANT, reversed), Set.of(), calls));
        assertEquals(3, calls.get());
    }

    @Test
    void mutableRawNumbersAreNeverRetainedAsSemanticKeys() {
        var value = new AtomicInteger(1);
        var configuration = new JsonObject(); configuration.addProperty("value", value);
        var graph = graph(CommonProgramNodeIds.INTEGER_CONSTANT, configuration);
        var cache = new ProgramCompilationCache();
        var calls = new AtomicInteger();
        var first = compile(cache, graph, Set.of(), calls);
        value.set(2);
        var second = compile(cache, graph, Set.of(), calls);
        assertEquals(1, ((CommonProgramNodeCatalog.IntegerConfiguration) first.program().nodes().get(1).configuration()).value());
        assertEquals(2, ((CommonProgramNodeCatalog.IntegerConfiguration) second.program().nodes().get(1).configuration()).value());
        assertEquals(2, calls.get());
        assertEquals(0, cache.stats().entries());
        assertEquals(0, cache.stats().identityGraphs());
    }

    @Test
    void unknownMutableDecodedConfigurationsAndExtensionsAlwaysBypass() {
        var cache = new ProgramCompilationCache();
        var calls = new AtomicInteger();
        var type = new MutableType();
        var configuration = new JsonObject(); configuration.addProperty("value", "original");
        var graph = graph(CUSTOM, configuration);
        var types = Map.<Identifier, ProgramNodeType<?>>of(
                BaseAbilityProgramDefinition.entryId(CATEGORY), DEFINITION.categoryNodeTypes().get(BaseAbilityProgramDefinition.entryId(CATEGORY)),
                CUSTOM, type);
        var context = new ProgramCompileContext(CATEGORY, Set.of(), ProgramLimits.DEFAULT);
        var lookup = ProgramNodeLookup.firstOf(types::get, CommonProgramNodeCatalog.INSTANCE);
        var first = cache.compile(graph, context, types, () -> {
            calls.incrementAndGet(); return ProgramCompiler.compile(graph, context, lookup);
        });
        assertTrue(first.valid());
        ((MutableConfiguration) first.program().nodes().get(1).configuration()).values().add("modified");
        var second = cache.compile(graph, context, types, () -> {
            calls.incrementAndGet(); return ProgramCompiler.compile(graph, context, lookup);
        });
        assertEquals(List.of("original"), ((MutableConfiguration) second.program().nodes().get(1).configuration()).values());
        assertEquals(2, calls.get());
        assertEquals(0, cache.stats().entries());

        var extension = new ExtensionType();
        var extensionTypes = Map.<Identifier, ProgramNodeType<?>>of(
                BaseAbilityProgramDefinition.entryId(CATEGORY), types.get(BaseAbilityProgramDefinition.entryId(CATEGORY)), CUSTOM, extension);
        var extensionLookup = ProgramNodeLookup.firstOf(extensionTypes::get, CommonProgramNodeCatalog.INSTANCE);
        var extensionConfiguration = new JsonObject(); extensionConfiguration.addProperty("value", 1);
        var extensionGraph = graph(CUSTOM, extensionConfiguration);
        var extensionCalls = new AtomicInteger();
        for (int i = 0; i < 2; i++) assertTrue(cache.compile(extensionGraph, context, extensionTypes, () -> {
            extensionCalls.incrementAndGet();
            return ProgramCompiler.compile(extensionGraph, context, extensionLookup);
        }).valid());
        assertEquals(2, extensionCalls.get(), "Even an audited immutable payload cannot opt an extension into caching");
        assertEquals(0, cache.stats().entries());
        assertFalse(ProgramCompilationCache.immutableConfiguration(new UnknownRecord(1), 0));
        assertFalse(ProgramCompilationCache.immutableConfiguration(MutableEnum.VALUE, 0));
        assertFalse(ProgramCompilationCache.immutableConfiguration(new JsonObject(), 0));
    }

    @Test
    void everyCurrentNativeDefaultHasAnExplicitlyAuditedConfigurationShape() {
        int checked = 0;
        var unsupported = new java.util.TreeSet<String>();
        for (var definition : AbilityProgramDefinitions.all()) {
            for (var entry : definition.editorCatalog().entries()) {
                if (entry.type() instanceof ProgramNodeExtension<?>) continue;
                var decoded = decodedDefault(entry.type(), entry.defaultConfiguration());
                if (!ProgramCompilationCache.immutableConfiguration(decoded, 0)) {
                    unsupported.add(entry.id() + " -> " + decoded.getClass().getName());
                }
                checked++;
            }
        }
        assertEquals(1476 + 7, checked, "Original inventory plus one explicitly audited common sample node in seven categories");
        assertEquals(Set.of(), unsupported, "All unsupported native defaults are reported together for explicit audit");
    }

    @Test
    void lruEntryAndWeightBudgetsAlsoBoundTheIdentityIndex() {
        var cache = new ProgramCompilationCache(2, ProgramCompilationCache.MAX_WEIGHT);
        var calls = new AtomicInteger();
        var first = integerGraph(1);
        var second = integerGraph(2);
        var third = integerGraph(3);
        compile(cache, first, Set.of(), calls);
        compile(cache, second, Set.of(), calls);
        compile(cache, first, Set.of(), calls);
        compile(cache, third, Set.of(), calls);
        compile(cache, second, Set.of(), calls);
        assertEquals(4, calls.get(), "Only the least recently used second graph should be recompiled");
        assertEquals(2, cache.stats().entries());
        assertEquals(2, cache.stats().identityGraphs());
        long singleWeight = cache.stats().weight() / 2;
        var weightBound = new ProgramCompilationCache(64, singleWeight * 2 - 1);
        compile(weightBound, first, Set.of(), calls);
        compile(weightBound, second, Set.of(), calls);
        assertEquals(1, weightBound.stats().entries());
        assertTrue(weightBound.stats().weight() <= weightBound.stats().maxWeight());
        weightBound.clear();
        assertEquals(0, weightBound.stats().weight());
        assertEquals(0, weightBound.stats().identityGraphs());
    }

    @Test
    void oversizedConfigurationAndCapabilityPayloadsCannotEnterTheBudget() {
        var cache = new ProgramCompilationCache(64, 8192);
        var calls = new AtomicInteger();
        var configuration = new JsonObject(); configuration.addProperty("value", 1);
        configuration.addProperty("unused", "x".repeat(8192));
        assertTrue(compile(cache, graph(CommonProgramNodeIds.INTEGER_CONSTANT, configuration), Set.of(), calls).valid());
        var longCapability = Identifier.parse("test:" + "a".repeat(4096));
        assertTrue(compile(cache, integerGraph(1), Set.of(longCapability), calls).valid());
        assertEquals(0, cache.stats().entries());
        assertEquals(0, cache.stats().weight());
        assertEquals(2, calls.get());
    }

    @Test
    void sameKeyConcurrentRequestsBuildOneImmutableResult() throws Exception {
        var cache = new ProgramCompilationCache();
        var graph = integerGraph(4);
        var calls = new AtomicInteger();
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(8)) {
            var futures = new ArrayList<java.util.concurrent.Future<ProgramCompileResult>>();
            for (int i = 0; i < 32; i++) futures.add(executor.submit(() -> {
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return compile(cache, graph, Set.of(), calls);
            }));
            start.countDown();
            var first = futures.getFirst().get(10, TimeUnit.SECONDS);
            for (var future : futures) assertSame(first, future.get(10, TimeUnit.SECONDS));
        }
        assertEquals(1, calls.get());
        assertEquals(1, cache.stats().entries());
    }

    @Test
    void invalidResultsLimitsAndRealLifecycleHandlersCannotReuseStaleEntries() {
        var graph = PrecisionPerformanceGraphs.editorGraph(2);
        var capabilities = PrecisionPerformanceGraphs.capabilities(DEFINITION);
        var custom = BaseAbilityProgramDefinition.create(CATEGORY);
        var customFirst = custom.compile(integerGraph(1), Set.of());
        assertTrue(customFirst.valid());
        assertNotSame(customFirst, custom.compile(integerGraph(1), Set.of()), "Custom definitions always bypass");
        var limited = new AbilityProgramDefinition(CATEGORY, custom.categoryNodeTypes(), _ -> null,
                custom.editorCatalog(), new ProgramLimits(1, 1));
        var rejectedByLimit = limited.compile(graph.graph(), capabilities);
        assertFalse(rejectedByLimit.valid());
        assertTrue(rejectedByLimit.diagnostics().stream().anyMatch(value -> value.code() == ProgramDiagnosticCode.TOO_MANY_NODES));
        var cache = new ProgramCompilationCache();
        var calls = new AtomicInteger();
        var bad = new ProgramGraph(integerGraph(1).nodes(), List.of(new ProgramGraph.Edge(
                new ProgramGraph.Endpoint(99, "missing"), new ProgramGraph.Endpoint(1, "missing"))));
        assertFalse(compile(cache, bad, Set.of(), calls).valid());
        assertFalse(compile(cache, bad, Set.of(), calls).valid());
        assertEquals(2, calls.get());
        assertEquals(0, cache.stats().entries());
        for (var clear : List.<Runnable>of(
                () -> ProgramCompileCacheLifecycle.serverStopped(null),
                () -> ProgramCompileCacheLifecycle.Client.loggedOut(null),
                () -> ProgramCompileCacheLifecycle.Client.stopped(null))) {
            assertTrue(DEFINITION.compile(graph, capabilities).valid());
            assertTrue(DEFINITION.compilationCacheStats().entries() > 0);
            clear.run();
            assertEquals(0, DEFINITION.compilationCacheStats().entries());
            assertEquals(0, DEFINITION.compilationCacheStats().weight());
            assertEquals(0, DEFINITION.compilationCacheStats().identityGraphs());
        }
    }

    private static ProgramCompileResult compile(ProgramCompilationCache cache, ProgramGraph graph,
                                                Set<Identifier> capabilities, AtomicInteger calls) {
        var context = new ProgramCompileContext(CATEGORY, capabilities, ProgramLimits.DEFAULT);
        return cache.compile(graph, context, DEFINITION.categoryNodeTypes(), () -> {
            calls.incrementAndGet();
            return ProgramCompiler.compile(graph, context, DEFINITION.nodeLookup());
        });
    }

    private static ProgramGraph integerGraph(int value) {
        var configuration = new JsonObject(); configuration.addProperty("value", value);
        return graph(CommonProgramNodeIds.INTEGER_CONSTANT, configuration);
    }

    private static ProgramGraph graph(Identifier type, JsonObject configuration) {
        var entry = BaseAbilityProgramDefinition.entryId(CATEGORY);
        var known = DEFINITION.nodeLookup().find(type);
        boolean flow = known == null || known.role().requiresFlow();
        return new ProgramGraph(List.of(new ProgramGraph.Node(0, entry, 1, new JsonObject()),
                new ProgramGraph.Node(1, type, 1, configuration)), flow ? List.of(new ProgramGraph.Edge(
                new ProgramGraph.Endpoint(0, "flow"), new ProgramGraph.Endpoint(1, "flow"))) : List.of());
    }

    private static <C> C decodedDefault(ProgramNodeType<C> type, com.google.gson.JsonElement encoded) {
        return type.configurationCodec().parse(JsonOps.INSTANCE, encoded).getOrThrow();
    }

    private record UnknownRecord(int value) {}
    private record MutableConfiguration(List<String> values) {}
    private enum MutableEnum { VALUE { private final List<String> state = new ArrayList<>(); } }

    private static class MutableType implements ProgramNodeType<MutableConfiguration> {
        @Override public Codec<MutableConfiguration> configurationCodec() {
            return Codec.STRING.fieldOf("value").codec().xmap(value -> new MutableConfiguration(new ArrayList<>(List.of(value))),
                    value -> value.values().getFirst());
        }
        @Override public int schemaVersion() { return 1; }
        @Override public ProgramNodeSchema schema(MutableConfiguration configuration) {
            return new ProgramNodeSchema(List.of(ProgramPortDefinition.requiredInput("flow", ProgramValueTypes.FLOW)),
                    List.of(ProgramPortDefinition.output("flow", ProgramValueTypes.FLOW)));
        }
        @Override public ProgramNodeRole role() { return ProgramNodeRole.CONTROL; }
        @Override public ProgramNodePurity purity() { return ProgramNodePurity.PURE; }
        @Override public ProgramNodeScope scope() { return ProgramNodeScope.category(CATEGORY); }
    }

    private static final class ExtensionType implements ProgramNodeExtension<CommonProgramNodeCatalog.IntegerConfiguration> {
        @Override public Codec<CommonProgramNodeCatalog.IntegerConfiguration> configurationCodec() {
            return CommonProgramNodeCatalog.IntegerConfiguration.CODEC;
        }
        @Override public int schemaVersion() { return 1; }
        @Override public ProgramNodeSchema schema(CommonProgramNodeCatalog.IntegerConfiguration configuration) {
            return new ProgramNodeSchema(List.of(ProgramPortDefinition.requiredInput("flow", ProgramValueTypes.FLOW)),
                    List.of(ProgramPortDefinition.output("flow", ProgramValueTypes.FLOW)));
        }
        @Override public ProgramNodeRole role() { return ProgramNodeRole.CONTROL; }
        @Override public ProgramNodePurity purity() { return ProgramNodePurity.PURE; }
        @Override public ProgramNodeScope scope() { return ProgramNodeScope.category(CATEGORY); }
        @Override public ProgramNodeExecution<CommonProgramNodeCatalog.IntegerConfiguration> execution() {
            return (_, _, _) -> ProgramNodeStep.data(Map.of());
        }
        @Override public ProgramNodeEditorMetadata editorMetadata() {
            var value = new JsonObject(); value.addProperty("value", 1);
            return new ProgramNodeEditorMetadata(value, ProgramNodeEditorMetadata.Group.FLOW, "test.custom", "test.port.");
        }
    }
}
