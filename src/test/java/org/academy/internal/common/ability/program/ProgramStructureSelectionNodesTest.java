package org.academy.internal.common.ability.program;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import org.academy.api.common.ability.program.*;
import org.academy.api.common.structure.StructureRef;
import org.academy.internal.common.ability.program.compile.ProgramBookCodec;
import org.academy.internal.common.ability.program.registry.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ProgramStructureSelectionNodesTest {
    private static final Identifier WORLD = Identifier.parse("minecraft:overworld");
    private static final ProgramWorldPosition POINT = new ProgramWorldPosition(WORLD, 0, 64, 0);
    private static final ProgramStructureReference NEAR = reference(1), FAR = reference(2), STALE = reference(3);
    private static final CommonProgramNodeCatalog.CollectionDomain DOMAIN = CommonProgramNodeCatalog.CollectionDomain.STRUCTURE;

    @Test void nearestAndNearbyUseForwardedWholeStructureQueries() {
        ForwardingProgramTargetResolver resolver = Resolver::new;
        var inputs = inputs("center", POINT, "radius", 8.0);
        assertEquals(NEAR, execute(CommonProgramNodeIds.NEAREST_STRUCTURE, inputs, resolver).outputs().get("structure").value());
        assertEquals(List.of(NEAR, FAR), execute(CommonProgramNodeIds.STRUCTURES_AROUND, inputs, resolver).outputs().get("structures").value());
        assertFalse(ProgramValueTypes.canConnect(ProgramValueTypes.STRUCTURE_SET, ProgramValueTypes.ENTITY_SET));
        assertFalse(ProgramValueTypes.canConnect(ProgramValueTypes.STRUCTURE_SET, ProgramValueTypes.STRUCTURE_REFERENCE));
    }

    @Test void nearestInSetRechecksStaleReferencesAndFilterRemovesDuplicates() {
        var resolver = new Resolver();
        assertEquals(NEAR, execute(CommonProgramNodeIds.NEAREST_STRUCTURE_TO_POSITION,
                inputs("position", POINT, "structures", List.of(STALE, FAR, NEAR)), resolver).outputs().get("structure").value());
        assertEquals(List.of(NEAR), execute(CommonProgramNodeIds.FILTER_STRUCTURE_DISTANCE,
                inputs("center", POINT, "radius", 2.0, "structures", List.of(STALE, FAR, NEAR, NEAR)), resolver)
                .outputs().get("structures").value());
    }

    @Test void emptyAndEntirelyStaleSelectionsHaveNoSingleTarget() {
        var resolver = new Resolver(); resolver.targets = List.of();
        assertTrue(execute(CommonProgramNodeIds.NEAREST_STRUCTURE, inputs("center", POINT, "radius", 8.0), resolver).outputs().isEmpty());
        assertEquals(List.of(), execute(CommonProgramNodeIds.STRUCTURES_AROUND, inputs("center", POINT, "radius", 8.0), resolver)
                .outputs().get("structures").value());
        assertTrue(execute(CommonProgramNodeIds.NEAREST_STRUCTURE_TO_POSITION,
                inputs("position", POINT, "structures", List.of(STALE)), resolver).outputs().isEmpty());
    }

    @Test void equalDistancesKeepInputOrderAndForeignCollectionMembersAreRejected() {
        var resolver = new Resolver(); resolver.distances = Map.of(NEAR, 2.0, FAR, 2.0);
        assertEquals(FAR, execute(CommonProgramNodeIds.NEAREST_STRUCTURE_TO_POSITION,
                inputs("position", POINT, "structures", List.of(FAR, NEAR)), resolver).outputs().get("structure").value());
        assertThrows(IllegalArgumentException.class, () -> execute(CommonProgramNodeIds.NEAREST_STRUCTURE_TO_POSITION,
                inputs("position", POINT, "structures", List.of("entity")), resolver));
        for (double radius : new double[]{-1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(RuntimeException.class, () -> execute(CommonProgramNodeIds.STRUCTURES_AROUND,
                    inputs("center", POINT, "radius", radius), resolver));
        }
    }

    @Test void collectionOperationsDeduplicateByFullActivationIdentity() {
        var newActivation = new ProgramStructureReference(NEAR.reference(), UUID.randomUUID());
        var union = execute(DOMAIN.id("union"), inputs("left", List.of(NEAR, FAR), "right", List.of(NEAR, newActivation)), null);
        assertEquals(List.of(NEAR, FAR, newActivation), union.outputs().get("values").value());
        assertEquals(List.of(FAR), execute(DOMAIN.id("difference"), inputs("left", List.of(NEAR, FAR), "right", List.of(NEAR)), null)
                .outputs().get("values").value());
    }

    @Test void allThreeCategoryGraphsRoundTripStoreSetsAndIterateEachStructureOnce() {
        for (var category : List.of("accelerator", "electromaster", "aeromanip")) {
            var definition = AbilityProgramDefinitions.require(Identifier.parse("academy:" + category));
            var point = new JsonObject(); point.addProperty("dimension", WORLD.toString());
            point.addProperty("x", 0); point.addProperty("y", 64); point.addProperty("z", 0);
            var radius = new JsonObject(); radius.addProperty("value", 8);
            var graph = new ProgramGraph(List.of(
                    node(0, BaseAbilityProgramDefinition.entryId(definition.category()), new JsonObject()),
                    node(1, CommonProgramNodeIds.WORLD_POSITION_CONSTANT, point),
                    node(2, CommonProgramNodeIds.FLOAT_CONSTANT, radius),
                    node(3, CommonProgramNodeIds.STRUCTURES_AROUND, new JsonObject()),
                    variable(4, CommonProgramNodeIds.VARIABLE_SET, "targets", ProgramValueTypes.STRUCTURE_SET),
                    variable(5, CommonProgramNodeIds.VARIABLE_GET, "targets", ProgramValueTypes.STRUCTURE_SET),
                    node(6, DOMAIN.id("foreach"), new JsonObject()),
                    variable(7, CommonProgramNodeIds.VARIABLE_SET, "last", ProgramValueTypes.STRUCTURE_REFERENCE),
                    variable(8, CommonProgramNodeIds.VARIABLE_SET, "count", ProgramValueTypes.INTEGER)), List.of(
                    edge(0, "flow", 4, "flow"), edge(1, "position", 3, "center"), edge(2, "value", 3, "radius"),
                    edge(3, "structures", 4, "value"), edge(4, "flow", 6, "flow"), edge(5, "value", 6, "values"),
                    edge(6, "body", 7, "flow"), edge(6, "value", 7, "value"), edge(7, "flow", 8, "flow"),
                    edge(6, "index", 8, "value")));
            var program = new AbilityProgram(1, UUID.randomUUID(), "structure selection", definition.category(), graph, ProgramEditorLayout.EMPTY);
            var decoded = ProgramBookCodec.decodeProgram(ProgramBookCodec.encodeProgram(program));
            assertTrue(decoded.valid()); assertEquals(program, decoded.program());
            var compiled = definition.compile(decoded.program().graph(), Set.of());
            assertTrue(compiled.valid(), () -> compiled.diagnostics().toString());
            var session = new ProgramVm.Session(compiled.program());
            var result = session.run(0, 1000, definition.executors(), (ForwardingProgramTargetResolver) Resolver::new);
            assertEquals(ProgramVmResult.Status.COMPLETED, result.status(), result::toString);
            assertEquals(List.of(NEAR, FAR), session.variables().get("targets").value());
            assertEquals(FAR, session.variables().get("last").value());
            assertEquals(2, session.variables().get("count").value());
            for (var id : List.of(CommonProgramNodeIds.STRUCTURES_AROUND, CommonProgramNodeIds.NEAREST_STRUCTURE,
                    CommonProgramNodeIds.NEAREST_STRUCTURE_TO_POSITION, CommonProgramNodeIds.FILTER_STRUCTURE_DISTANCE, DOMAIN.id("foreach"))) {
                assertTrue(definition.editorCatalog().entry(id).visible());
            }
            var control = Identifier.parse("academy:program/" + category + "/action/structure/control");
            assertEquals(ProgramValueTypes.STRUCTURE_REFERENCE, definition.editorCatalog().schema(control, new JsonObject())
                    .input("structure").orElseThrow().type());
        }
    }

    private static ProgramNodeStep execute(Identifier id, ProgramInputView inputs, Object resolver) {
        var type = CommonProgramNodeCatalog.INSTANCE.find(id);
        var configuration = type.configurationCodec().parse(JsonOps.INSTANCE, new JsonObject()).getOrThrow();
        @SuppressWarnings("unchecked") var executor = (ProgramNodeExecutor<Object>) CommonProgramExecutors.INSTANCE.find(id);
        return executor.execute(new ProgramVmContext(0, new HashMap<>(), new HashMap<>(), resolver), configuration, inputs);
    }
    private static ProgramInputView inputs(Object... entries) {
        var values = new HashMap<String, List<ProgramValue<?>>>();
        for (int i = 0; i < entries.length; i += 2) {
            var value = entries[i + 1];
            var type = value instanceof List<?> ? ProgramValueTypes.STRUCTURE_SET
                    : value instanceof ProgramWorldPosition ? ProgramValueTypes.WORLD_POSITION : ProgramValueTypes.FLOAT;
            values.put((String) entries[i], List.of(new ProgramValue<>(type, value)));
        }
        return new ProgramInputView(values);
    }
    private static ProgramStructureReference reference(int id) {
        return new ProgramStructureReference(new StructureRef(new UUID(0, id), WORLD, Identifier.parse("academy:native_sublevel"), 1), new UUID(1, id));
    }
    private static ProgramGraph.Node node(int id, Identifier type, JsonObject configuration) { return new ProgramGraph.Node(id, type, 1, configuration); }
    private static ProgramGraph.Node variable(int id, Identifier node, String name, ProgramValueType type) {
        var config = new JsonObject(); config.addProperty("name", name); config.addProperty("type", type.id().toString());
        return node(id, node, config);
    }
    private static ProgramGraph.Edge edge(int from, String output, int to, String input) {
        return new ProgramGraph.Edge(new ProgramGraph.Endpoint(from, output), new ProgramGraph.Endpoint(to, input));
    }
    private static final class Resolver implements ProgramTargetResolver {
        List<ProgramStructureReference> targets = List.of(NEAR, FAR);
        Map<ProgramStructureReference, Double> distances = Map.of(NEAR, 2.0, FAR, 6.0);
        @Override public List<ProgramStructureReference> structuresAround(ProgramWorldPosition center, double radius) { return targets; }
        @Override public OptionalDouble structureDistanceTo(ProgramStructureReference target, ProgramWorldPosition point) {
            return distances.containsKey(target) ? OptionalDouble.of(distances.get(target)) : OptionalDouble.empty();
        }
        @Override public Optional<ProgramWorldPosition> positionOf(Object target) { return Optional.empty(); }
        @Override public Optional<ProgramDirection> lookDirectionOf(Object target) { return Optional.empty(); }
        @Override public List<?> entitiesAround(ProgramWorldPosition center, double radius) { return List.of(); }
        @Override public Optional<ProgramBlockPosition> raycastBlock(ProgramWorldPosition origin, ProgramDirection direction, double range) { return Optional.empty(); }
    }
}
