package org.academy.internal.common.ability.program;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import org.academy.api.common.ability.program.*;
import org.academy.api.common.structure.StructureBlockSummary;
import org.academy.api.common.structure.StructureRef;
import org.academy.api.common.structure.upgrade.StructureProvenance;
import org.academy.internal.common.ability.program.compile.ProgramBookCodec;
import org.academy.internal.common.ability.program.registry.*;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ProgramStructureFilterNodesTest {
    private static final Identifier WORLD = Identifier.parse("minecraft:overworld");
    private static final Identifier SKILL = Identifier.parse("academy:kinetic_throw");
    private static final ProgramStructureReference OWN = reference(1), OTHER = reference(2), UNMARKED = reference(3), UNKNOWN = reference(4);
    private static final StructureProvenance.Metadata METADATA = StructureProvenance.SkillCreation
            .resting(new UUID(0, 1), SKILL).capture(new UUID(0, 2), 0);

    @Test void sourceModesDistinguishKnownUnmarkedFromUnavailableAndPreserveOrder() {
        ForwardingProgramTargetResolver resolver = Resolver::new;
        var values = List.of(OTHER, UNKNOWN, OWN, UNMARKED, OWN);
        assertEquals(List.of(OTHER, OWN), filter("marked", values, resolver));
        assertEquals(List.of(UNMARKED), filter("unmarked", values, resolver));
        assertEquals(List.of(OWN), filter("owned_by_caster", values, resolver));
        assertEquals(List.of(OTHER, OWN), filter("skill", values, resolver));
        assertEquals(List.of(), filter("fragment", values, resolver));
        assertEquals(List.of(), filter("unmarked", List.of(UNKNOWN), resolver));
        assertEquals(List.of(), filter("marked", List.of(), resolver));
        assertEquals(true, execute(CommonProgramNodeIds.STRUCTURE_VALID, new JsonObject(),
                inputs("structure", OWN), resolver).outputs().get("result").value());
        assertEquals(false, execute(CommonProgramNodeIds.STRUCTURE_VALID, new JsonObject(),
                inputs("structure", UNKNOWN), resolver).outputs().get("result").value());
        assertEquals(List.of(OTHER, OWN, UNMARKED), execute(CommonProgramNodeIds.FILTER_STRUCTURE_VALID, new JsonObject(),
                inputs("structures", values), resolver).outputs().get("structures").value());
    }

    @Test void fragmentAndAttributionAreMetadataPredicatesRatherThanControlGrants() {
        var fragment = new StructureProvenance.Metadata(StructureProvenance.CreationKind.SKILL_FRAGMENT,
                UUID.randomUUID(), METADATA.rootLineageId(), Optional.of(OWN.reference()), METADATA.ownerId(), SKILL,
                METADATA.sourceGridRotation(), METADATA.restorePolicy(), 1);
        var source = new ProgramStructureSource(Optional.of(fragment), false);
        assertTrue(new ProgramStructureFilters.Source("fragment", SKILL.toString()).matches(source));
        assertTrue(new ProgramStructureFilters.Source("skill", SKILL.toString()).matches(source));
        assertFalse(new ProgramStructureFilters.Source("owned_by_caster", SKILL.toString()).matches(source));
        assertFalse(new ProgramStructureFilters.Source("skill", "academy:high_speed_jet").matches(source));
        assertThrows(IllegalArgumentException.class, () -> new ProgramStructureSource(Optional.empty(), true));
    }

    @Test void malformedConfigurationsFailAndEmptyContentNeverMatchesAll() {
        for (var selectors : List.of("", " ", "#", "Bad ID", "x".repeat(513))) {
            assertThrows(RuntimeException.class, () -> new ProgramStructureFilters.Blocks(selectors, "any"));
        }
        assertThrows(IllegalArgumentException.class, () -> new ProgramStructureFilters.Blocks("minecraft:iron_block", "some"));
        assertThrows(IllegalArgumentException.class, () -> new ProgramStructureFilters.Source("unknown", SKILL.toString()));
        assertFalse(new ProgramStructureFilters.Blocks("minecraft:iron_block,#minecraft:logs", "all")
                .matches(new StructureBlockSummary(Map.of(), 0)));
        assertFalse(new ProgramStructureFilters.Blocks("minecraft:iron_block", "any").matches(new StructureBlockSummary(Map.of(), 1)));
        var config = new JsonObject(); config.addProperty("selectors", "minecraft:iron_block");
        assertEquals(List.of(), execute(CommonProgramNodeIds.FILTER_STRUCTURE_BLOCKS, config,
                inputs("structures", List.of(UNKNOWN)), (ForwardingProgramTargetResolver) Resolver::new)
                .outputs().get("structures").value());
    }

    @Test void connectedConfigurationInputsUseTheSameWireNamesAndValidation() {
        @SuppressWarnings("unchecked") var type = (ProgramNodeType<ProgramStructureFilters.Source>)
                CommonProgramNodeCatalog.INSTANCE.find(CommonProgramNodeIds.FILTER_STRUCTURE_SOURCE);
        var stored = new ProgramStructureFilters.Source("owned_by_caster", SKILL.toString());
        assertTrue(type.resolvedSchema(stored).input("source_mode").isPresent());
        var inputs = new ProgramInputView(Map.of("source_mode", List.of(new ProgramValue<>(ProgramValueTypes.TEXT, "unmarked"))));
        assertEquals(new ProgramStructureFilters.Source("unmarked", SKILL.toString()), type.resolveConfiguration(stored, inputs));
        var invalid = new ProgramInputView(Map.of("source_mode", List.of(new ProgramValue<>(ProgramValueTypes.TEXT, "invalid"))));
        assertThrows(RuntimeException.class, () -> type.resolveConfiguration(stored, invalid));
    }

    @Test void allCategoriesRoundTripFiltersAndEmptyLoopsWithoutBypassingRejectedControl() throws Exception {
        for (String category : List.of("accelerator", "electromaster", "aeromanip")) {
            var definition = AbilityProgramDefinitions.require(Identifier.parse("academy:" + category));
            var runtimeClass = Class.forName("org.academy.internal.common.ability." + category + ".program."
                    + switch (category) { case "accelerator" -> "Accelerator"; case "electromaster" -> "Electromaster"; default -> "Aeromanip"; } + "ProgramRuntime");
            var calls = new ArrayList<ProgramStructureReference>();
            var resolver = new Resolver();
            var runtime = Proxy.newProxyInstance(runtimeClass.getClassLoader(), new Class<?>[]{runtimeClass}, (proxy, method, args) -> {
                if (method.getName().equals("controlStructure")) {
                    calls.add((ProgramStructureReference) args[0]);
                    return method.getReturnType().getMethod("rejected", String.class).invoke(null, "test_permission_denied");
                }
                return ProgramTargetResolver.class.getMethod(method.getName(), method.getParameterTypes()).invoke(resolver, args);
            });
            for (var mode : List.of("fragment", "owned_by_caster")) {
                calls.clear();
                var point = new JsonObject(); point.addProperty("dimension", WORLD.toString());
                point.addProperty("x", 0); point.addProperty("y", 64); point.addProperty("z", 0);
                var radius = new JsonObject(); radius.addProperty("value", 8);
                var graph = new ProgramGraph(List.of(
                        node(0, BaseAbilityProgramDefinition.entryId(definition.category()), new JsonObject()),
                        node(1, CommonProgramNodeIds.WORLD_POSITION_CONSTANT, point),
                        node(2, CommonProgramNodeIds.FLOAT_CONSTANT, radius),
                        node(3, CommonProgramNodeIds.STRUCTURES_AROUND, new JsonObject()),
                        node(4, CommonProgramNodeIds.FILTER_STRUCTURE_SOURCE, sourceConfig(mode)),
                        node(5, CommonProgramNodeCatalog.CollectionDomain.STRUCTURE.id("foreach"), new JsonObject()),
                        node(6, Identifier.parse("academy:program/" + category + "/action/structure/control"), new JsonObject())), List.of(
                        edge(0, "flow", 5, "flow"), edge(1, "position", 3, "center"), edge(2, "value", 3, "radius"),
                        edge(3, "structures", 4, "structures"), edge(4, "structures", 5, "values"),
                        edge(5, "body", 6, "flow"), edge(5, "value", 6, "structure")));
                var program = new AbilityProgram(1, UUID.randomUUID(), "filters", definition.category(), graph, ProgramEditorLayout.EMPTY);
                var decoded = ProgramBookCodec.decodeProgram(ProgramBookCodec.encodeProgram(program));
                assertTrue(decoded.valid()); assertEquals(program, decoded.program());
                var capability = Identifier.parse("academy:" + switch (category) {
                    case "accelerator" -> "kinetic_throw"; case "electromaster" -> "magnet_manipulation"; default -> "high_speed_jet";
                });
                var compiled = definition.compile(decoded.program().graph(), Set.of(capability));
                assertTrue(compiled.valid(), () -> compiled.diagnostics().toString());
                var result = new ProgramVm.Session(compiled.program()).run(0, 1000, definition.executors(),
                        new ProgramExecutionFrame(new ProgramActionTransaction(), runtime));
                assertEquals(mode.equals("fragment") ? ProgramVmResult.Status.COMPLETED : ProgramVmResult.Status.FAILED,
                        result.status(), result::toString);
                assertEquals(mode.equals("fragment") ? List.of() : List.of(OWN), calls);
                if (!mode.equals("fragment")) {
                    assertEquals(6, result.nodeId());
                    assertEquals("rejected:test_permission_denied", result.detail());
                }
            }
            for (var id : List.of(CommonProgramNodeIds.FILTER_STRUCTURE_BLOCKS, CommonProgramNodeIds.FILTER_STRUCTURE_SOURCE,
                    CommonProgramNodeIds.FILTER_STRUCTURE_VALID, CommonProgramNodeIds.STRUCTURE_VALID)) {
                var entry = definition.editorCatalog().entry(id);
                assertTrue(entry.visible());
                assertNotNull(definition.editorCatalog().schema(id, entry.defaultConfiguration()));
            }
        }
    }

    private static Object filter(String mode, List<ProgramStructureReference> values, Object resolver) {
        return execute(CommonProgramNodeIds.FILTER_STRUCTURE_SOURCE, sourceConfig(mode), inputs("structures", values), resolver)
                .outputs().get("structures").value();
    }
    private static JsonObject sourceConfig(String mode) { var config = new JsonObject(); config.addProperty("source_mode", mode); return config; }
    private static ProgramNodeStep execute(Identifier id, JsonObject config, ProgramInputView inputs, Object resolver) {
        var type = CommonProgramNodeCatalog.INSTANCE.find(id);
        var parsed = type.configurationCodec().parse(JsonOps.INSTANCE, config).getOrThrow();
        @SuppressWarnings("unchecked") var executor = (ProgramNodeExecutor<Object>) CommonProgramExecutors.INSTANCE.find(id);
        return executor.execute(new ProgramVmContext(0, new HashMap<>(), new HashMap<>(), resolver), parsed, inputs);
    }
    private static ProgramInputView inputs(String name, Object value) {
        return new ProgramInputView(Map.of(name, List.of(new ProgramValue<>(value instanceof List<?> ? ProgramValueTypes.STRUCTURE_SET
                : ProgramValueTypes.STRUCTURE_REFERENCE, value))));
    }
    private static ProgramStructureReference reference(int id) {
        return new ProgramStructureReference(new StructureRef(new UUID(0, id), WORLD, Identifier.parse("academy:native_sublevel"), 1), new UUID(1, id));
    }
    private static ProgramGraph.Node node(int id, Identifier type, JsonObject config) { return new ProgramGraph.Node(id, type, 1, config); }
    private static ProgramGraph.Edge edge(int from, String output, int to, String input) {
        return new ProgramGraph.Edge(new ProgramGraph.Endpoint(from, output), new ProgramGraph.Endpoint(to, input));
    }
    private static final class Resolver implements ProgramTargetResolver {
        @Override public boolean isStructureValid(ProgramStructureReference reference) { return !reference.equals(UNKNOWN); }
        @Override public Optional<ProgramStructureSource> structureSource(ProgramStructureReference reference) {
            if (reference.equals(UNKNOWN)) return Optional.empty();
            return Optional.of(new ProgramStructureSource(reference.equals(UNMARKED) ? Optional.empty() : Optional.of(METADATA), reference.equals(OWN)));
        }
        @Override public List<ProgramStructureReference> structuresAround(ProgramWorldPosition center, double radius) { return List.of(OTHER, UNKNOWN, OWN, UNMARKED); }
        @Override public Optional<ProgramWorldPosition> positionOf(Object target) { return Optional.empty(); }
        @Override public Optional<ProgramDirection> lookDirectionOf(Object target) { return Optional.empty(); }
        @Override public List<?> entitiesAround(ProgramWorldPosition center, double radius) { return List.of(); }
        @Override public Optional<ProgramBlockPosition> raycastBlock(ProgramWorldPosition origin, ProgramDirection direction, double range) { return Optional.empty(); }
    }
}
