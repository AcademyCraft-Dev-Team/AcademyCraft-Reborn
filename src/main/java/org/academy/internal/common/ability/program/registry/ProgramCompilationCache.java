package org.academy.internal.common.ability.program.registry;

import com.google.gson.JsonElement;
import com.google.gson.internal.LazilyParsedNumber;
import net.minecraft.resources.Identifier;
import org.academy.api.common.ability.program.*;
import org.academy.api.common.profiler.AcademyProfiler;
import org.academy.internal.common.ability.accelerator.program.AcceleratorProgramNodeCatalog;
import org.academy.internal.common.ability.aeromanip.program.AeromanipProgramNodeCatalog;
import org.academy.internal.common.ability.darkmatter.program.DarkmatterProgramNodeCatalog;
import org.academy.internal.common.ability.electromaster.program.ElectromasterProgramNodeCatalog;
import org.academy.internal.common.ability.meltdowner.program.MeltdownerProgramNodeCatalog;
import org.academy.internal.common.ability.program.ProgramStructureMovementNodes;
import org.academy.internal.common.ability.program.ProgramStructureOrientationNodes;
import org.academy.internal.common.ability.program.compile.ProgramCompileResult;
import org.academy.internal.common.ability.teleport.program.TeleportProgramNodeCatalog;

import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;

/** Per-definition successful compilations only. Weight is a conservative budget, not measured heap. */
final class ProgramCompilationCache {
    static final int MAX_ENTRIES = 64;
    static final long MAX_WEIGHT = 4L * 1024 * 1024;
    private static final Set<Class<?>> IMMUTABLE_LEAVES = Set.of(
            String.class, Boolean.class, Byte.class, Short.class, Integer.class, Long.class,
            Float.class, Double.class, Character.class, BigInteger.class, BigDecimal.class,
            Identifier.class, ProgramTag.class, ProgramValueType.class);
    // Audited concrete configuration records. New records and all extension objects default to bypass.
    private static final Set<Class<?>> CONFIGURATIONS = Set.of(
            CommonProgramNodeCatalog.BooleanConfiguration.class,
            CommonProgramNodeCatalog.ScalarConfiguration.class,
            CommonProgramNodeCatalog.NumericComparisonConfiguration.class,
            CommonProgramNodeCatalog.NumericConversionConfiguration.class,
            CommonProgramNodeCatalog.NumericArithmeticConfiguration.class,
            CommonProgramNodeCatalog.LoopTriggerConfiguration.class,
            CommonProgramNodeCatalog.ValueTypeConfiguration.class,
            CommonProgramNodeCatalog.CollectionBuilderConfiguration.class,
            CommonProgramNodeCatalog.EntityPositionConfiguration.class,
            CommonProgramNodeCatalog.ExactFilterConfiguration.class,
            CommonProgramNodeCatalog.RandomNumberConfiguration.class,
            CommonProgramNodeCatalog.Vec3OperationConfiguration.class,
            CommonProgramNodeCatalog.EntityDataConfiguration.class,
            CommonProgramNodeCatalog.DebugOutputConfiguration.class,
            CommonProgramNodeCatalog.DistanceSortConfiguration.class,
            CommonProgramNodeCatalog.MovementTriggerConfiguration.class,
            CommonProgramNodeCatalog.HealthThresholdTriggerConfiguration.class,
            CommonProgramNodeCatalog.LookTargetConfiguration.class,
            CommonProgramNodeCatalog.BlockNormalConfiguration.class,
            CommonProgramNodeCatalog.IntegerConfiguration.class,
            CommonProgramNodeCatalog.BigIntegerConfiguration.class,
            CommonProgramNodeCatalog.FloatConfiguration.class,
            CommonProgramNodeCatalog.TextConfiguration.class,
            CommonProgramNodeCatalog.TagConfiguration.class,
            CommonProgramNodeCatalog.DimensionConfiguration.class,
            CommonProgramNodeCatalog.DirectionConfiguration.class,
            CommonProgramNodeCatalog.WorldPositionConfiguration.class,
            CommonProgramNodeCatalog.BlockPositionConfiguration.class,
            CommonProgramNodeCatalog.VariableConfiguration.class,
            CommonProgramNodeCatalog.SharedVariableConfiguration.class,
            CommonProgramNodeCatalog.SharedVariableNameConfiguration.class,
            CommonProgramNodeCatalog.ChatReadConfiguration.class,
            CommonProgramNodeCatalog.ChatTriggerConfiguration.class,
            CommonProgramNodeCatalog.TextSplitConfiguration.class,
            CommonProgramNodeCatalog.TextToVec3Configuration.class,
            CommonProgramNodeCatalog.TextComparisonConfiguration.class,
            CommonProgramNodeCatalog.EntityKindConfiguration.class,
            ProgramStructureFilters.Blocks.class,
            ProgramStructureFilters.Source.class,
            PrecisionProgramNodeCatalog.PrecisionConfiguration.class,
            AcceleratorProgramNodeCatalog.StrengthConfiguration.class,
            AcceleratorProgramNodeCatalog.PowerConfiguration.class,
            AcceleratorProgramNodeCatalog.ExtractStructureConfiguration.class,
            AcceleratorProgramNodeCatalog.MoveStructureConfiguration.class,
            AcceleratorProgramNodeCatalog.ShockwaveConfiguration.class,
            AeromanipProgramNodeCatalog.PowerConfiguration.class,
            AeromanipProgramNodeCatalog.LaminarCutConfiguration.class,
            AeromanipProgramNodeCatalog.ConvergingAirflowConfiguration.class,
            AeromanipProgramNodeCatalog.TemporaryNozzleConfiguration.class,
            AeromanipProgramNodeCatalog.JetActivationConfiguration.class,
            AeromanipProgramNodeCatalog.BlockStructureLaunchConfiguration.class,
            AeromanipProgramNodeCatalog.ExtractStructureConfiguration.class,
            AeromanipProgramNodeCatalog.MoveStructureConfiguration.class,
            AeromanipProgramNodeCatalog.LaunchStructureConfiguration.class,
            ElectromasterProgramNodeCatalog.PowerConfiguration.class,
            ElectromasterProgramNodeCatalog.ExtractStructureConfiguration.class,
            ElectromasterProgramNodeCatalog.MoveStructureConfiguration.class,
            ElectromasterProgramNodeCatalog.EnergyDetectionConfiguration.class,
            ElectromasterProgramNodeCatalog.EnergyLevelConfiguration.class,
            ElectromasterProgramNodeCatalog.ChainConfiguration.class,
            ElectromasterProgramNodeCatalog.RedstoneDetectionConfiguration.class,
            ElectromasterProgramNodeCatalog.CurrentRechargeConfiguration.class,
            ElectromasterProgramNodeCatalog.MagneticConfiguration.class,
            MeltdownerProgramNodeCatalog.PowerConfiguration.class,
            MeltdownerProgramNodeCatalog.BeamConfiguration.class,
            MeltdownerProgramNodeCatalog.MiningBeamConfiguration.class,
            MeltdownerProgramNodeCatalog.ElectronFanConfiguration.class,
            MeltdownerProgramNodeCatalog.AtomicJetConfiguration.class,
            DarkmatterProgramNodeCatalog.PowerConfiguration.class,
            DarkmatterProgramNodeCatalog.FieldConfiguration.class,
            TeleportProgramNodeCatalog.PowerConfiguration.class,
            TeleportProgramNodeCatalog.TargetTeleportConfiguration.class,
            TeleportProgramNodeCatalog.BlockItemTeleportConfiguration.class,
            ProgramStructureMovementNodes.Configuration.class,
            ProgramStructureOrientationNodes.ResetConfiguration.class,
            ProgramStructureOrientationNodes.SpinConfiguration.class);

    private final int maxEntries;
    private final long maxWeight;
    private final LinkedHashMap<Key, Entry> entries = new LinkedHashMap<>(16, 0.75f, true);
    // Only original graphs of retained entries are indexed; equivalent callers never add aliases.
    private final IdentityHashMap<ProgramGraph, GraphUse> identities = new IdentityHashMap<>();
    private long weight;

    ProgramCompilationCache() { this(MAX_ENTRIES, MAX_WEIGHT); }

    ProgramCompilationCache(int maxEntries, long maxWeight) {
        if (maxEntries < 1 || maxWeight < 1) throw new IllegalArgumentException("Cache budgets must be positive");
        this.maxEntries = maxEntries;
        this.maxWeight = maxWeight;
    }

    ProgramCompileResult compile(ProgramGraph graph, ProgramCompileContext context,
                                 Map<Identifier, ProgramNodeType<?>> categoryTypes,
                                 Supplier<ProgramCompileResult> compiler) {
        if (graph.nodes().isEmpty() || graph.nodes().size() > context.limits().maxNodes()
                || graph.edges().size() > context.limits().maxEdges() || !knownNodes(graph, categoryTypes)) {
            AcademyProfiler.incrementCounter("program.compile.cache_bypass");
            return compiler.get();
        }
        synchronized (this) {
            long capabilityWeight = 0;
            for (var capability : context.capabilities()) {
                capabilityWeight += 256L + 4L * (capability.getNamespace().length() + capability.getPath().length() + 1);
                if (capabilityWeight > maxWeight) {
                    AcademyProfiler.incrementCounter("program.compile.cache_bypass");
                    AcademyProfiler.incrementCounter("program.compile.cache_oversized_capabilities");
                    return compiler.get();
                }
            }
            var known = identities.get(graph);
            var inspected = known == null ? inspectGraph(graph) : known;
            if (inspected == null) {
                AcademyProfiler.incrementCounter("program.compile.cache_bypass");
                AcademyProfiler.incrementCounter("program.compile.cache_budget_or_mutable_graph");
                return compiler.get();
            }
            var key = new Key(inspected.structure, context.capabilities());
            var cached = entries.get(key);
            if (cached != null) {
                AcademyProfiler.incrementCounter("program.compile.cache_hit");
                return cached.result;
            }
            AcademyProfiler.incrementCounter("program.compile.cache_miss");
            var result = compiler.get();
            if (!result.valid() || result.program().nodes().values().stream()
                    .anyMatch(node -> !immutableConfiguration(node.configuration(), 0))) {
                AcademyProfiler.incrementCounter("program.compile.cache_bypass");
                AcademyProfiler.incrementCounter("program.compile.cache_result_not_admitted");
                return result;
            }
            long entryWeight = inspected.weight * 2 + graph.nodes().size() * 1024L
                    + graph.edges().size() * 256L + capabilityWeight + 512;
            if (entryWeight > maxWeight) {
                AcademyProfiler.incrementCounter("program.compile.cache_bypass");
                AcademyProfiler.incrementCounter("program.compile.cache_oversized_result");
                return result;
            }
            while (entries.size() >= maxEntries || weight + entryWeight > maxWeight) evict();
            entries.put(key, new Entry(result, entryWeight, graph));
            weight += entryWeight;
            var use = identities.computeIfAbsent(graph, _ -> inspected);
            use.references++;
            return result;
        }
    }

    private static boolean knownNodes(ProgramGraph graph, Map<Identifier, ProgramNodeType<?>> categoryTypes) {
        for (var node : graph.nodes()) {
            var type = categoryTypes.get(node.type());
            if (type == null) type = CommonProgramNodeCatalog.INSTANCE.find(node.type());
            if (type == null || type instanceof ProgramNodeExtension<?>) return false;
        }
        return true;
    }

    private GraphUse inspectGraph(ProgramGraph graph) {
        long result = 128L + graph.nodes().size() * 128L + graph.edges().size() * 128L;
        var nodes = new ArrayList<NodeKey>();
        for (var node : graph.nodes()) {
            var text = new StringBuilder();
            long json = jsonWeight(node.configuration(), 0, maxWeight - result, text);
            if (json < 0) return null;
            result += json;
            if (result > maxWeight) return null;
            nodes.add(new NodeKey(node.id(), node.type(), node.schemaVersion(), text.toString()));
        }
        return new GraphUse(result, new Structure(List.copyOf(nodes), graph.edges()));
    }

    /** Exact tagged JSON content; Gson numeric equals/hashCode can collapse distinct large values. */
    private static long jsonWeight(JsonElement value, int depth, long remaining, StringBuilder text) {
        if (depth > 32 || remaining < 16) return -1;
        if (value.isJsonNull()) { text.append('z'); return 16; }
        if (value.isJsonPrimitive()) {
            var primitive = value.getAsJsonPrimitive();
            var contents = primitive.getAsString();
            if (primitive.isNumber()) {
                var type = primitive.getAsNumber().getClass();
                if (!IMMUTABLE_LEAVES.contains(type) && type != LazilyParsedNumber.class) return -1;
                contents = type.getName() + ":" + contents;
            }
            long weight = 64L + contents.length() * 4L;
            if (weight > remaining) return -1;
            text.append(primitive.isNumber() ? 'n' : primitive.isString() ? 's' : 'b');
            appendText(text, contents);
            return weight;
        }
        long result = 64;
        if (value.isJsonArray()) {
            text.append('a').append(value.getAsJsonArray().size()).append('[');
            for (var child : value.getAsJsonArray()) {
                long weight = jsonWeight(child, depth + 1, remaining - result, text);
                if (weight < 0) return -1;
                result += weight + 32;
                if (result > remaining) return -1;
            }
            text.append(']');
        } else {
            if (value.getAsJsonObject().size() * 64L > remaining) return -1;
            text.append('o').append(value.getAsJsonObject().size()).append('{');
            for (var child : new TreeMap<>(value.getAsJsonObject().asMap()).entrySet()) {
                result += child.getKey().length() * 4L + 64;
                if (result > remaining) return -1;
                appendText(text, child.getKey());
                long weight = jsonWeight(child.getValue(), depth + 1, remaining - result, text);
                if (weight < 0) return -1;
                result += weight;
                if (result > remaining) return -1;
            }
            text.append('}');
        }
        return result <= remaining ? result : -1;
    }

    private static void appendText(StringBuilder output, String value) {
        output.append(value.length()).append(':').append(value);
    }

    static boolean immutableConfiguration(Object value, int depth) {
        if (value == null || depth > 8) return false;
        var type = value.getClass();
        if (IMMUTABLE_LEAVES.contains(type)) return true;
        try {
            if (value instanceof Enum<?> enumeration) {
                for (Class<?> current = type; current != null && current != Enum.class; current = current.getSuperclass()) {
                    for (var field : current.getDeclaredFields()) {
                        if (Modifier.isStatic(field.getModifiers())) continue;
                        if (!Modifier.isFinal(field.getModifiers()) || !field.trySetAccessible()
                                || !immutableConfiguration(field.get(value), depth + 1)) return false;
                    }
                    if (current == enumeration.getDeclaringClass()) return true;
                }
                return false;
            }
            if (!CONFIGURATIONS.contains(type)) return false;
            for (var component : type.getRecordComponents()) {
                var accessor = component.getAccessor();
                if (!accessor.trySetAccessible()
                        || !immutableConfiguration(accessor.invoke(value), depth + 1)) return false;
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException unsupported) {
            return false;
        }
    }

    private void evict() {
        var iterator = entries.entrySet().iterator();
        var removed = iterator.next();
        iterator.remove();
        weight -= removed.getValue().weight;
        var graph = removed.getValue().identityGraph;
        var use = identities.get(graph);
        if (--use.references == 0) identities.remove(graph);
        AcademyProfiler.incrementCounter("program.compile.cache_evict");
    }

    synchronized void clear() {
        entries.clear();
        identities.clear();
        weight = 0;
        AcademyProfiler.incrementCounter("program.compile.cache_clear");
    }

    synchronized Stats stats() { return new Stats(entries.size(), weight, identities.size(), maxEntries, maxWeight); }

    record Stats(int entries, long weight, int identityGraphs, int maxEntries, long maxWeight) {}
    private record Key(Structure graph, Set<Identifier> capabilities) {}
    private record Structure(List<NodeKey> nodes, List<ProgramGraph.Edge> edges) {}
    private record NodeKey(int id, Identifier type, int version, String configuration) {}
    private record Entry(ProgramCompileResult result, long weight, ProgramGraph identityGraph) {}
    private static final class GraphUse {
        private final long weight;
        private final Structure structure;
        private int references;
        private GraphUse(long weight, Structure structure) { this.weight = weight; this.structure = structure; }
    }
}
