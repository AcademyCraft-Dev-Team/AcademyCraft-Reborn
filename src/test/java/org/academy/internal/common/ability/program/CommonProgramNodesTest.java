package org.academy.internal.common.ability.program;

import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import org.academy.api.common.ability.program.ProgramBlockPosition;
import org.academy.api.common.ability.program.ProgramCompileContext;
import org.academy.api.common.ability.program.ProgramDirection;
import org.academy.api.common.ability.program.ProgramEntityPositionAnchor;
import org.academy.api.common.ability.program.ProgramGraph;
import org.academy.api.common.ability.program.ProgramLimits;
import org.academy.api.common.ability.program.ProgramTargetResolver;
import org.academy.api.common.ability.program.ProgramValueTypes;
import org.academy.api.common.ability.program.ProgramVector;
import org.academy.api.common.ability.program.ProgramWorldPosition;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CommonProgramNodesTest {
    private static final Identifier CATEGORY = PrecisionProgramNodeCatalog.MENTALOUT;
    private static final Identifier OVERWORLD = Identifier.parse("minecraft:overworld");

    @Test
    void everyCommonNodeHasAnExecutorAndEveryTargetDomainHasCollectionAlgebra() {
        var catalog = CommonProgramNodeCatalog.INSTANCE;
        var executors = CommonProgramExecutors.INSTANCE;

        assertEquals(catalog.types().keySet(), executors.executors().keySet());
        for (var domain : CommonProgramNodeCatalog.CollectionDomain.values()) {
            for (var operation : List.of(
                    "empty",
                    "singleton",
                    "union",
                    "intersection",
                    "difference",
                    "contains",
                    "size",
                    "get",
                    "foreach"
            )) {
                assertNotNull(catalog.find(domain.id(operation)), domain + "/" + operation);
                assertNotNull(executors.find(domain.id(operation)), domain + "/" + operation);
            }
        }
        for (var id : List.of(
                CommonProgramNodeIds.RANDOM_ENTITY,
                CommonProgramNodeIds.NEAREST_ENTITY_TO_POSITION,
                CommonProgramNodeIds.RANDOM_WORLD_POSITION,
                CommonProgramNodeIds.RANDOM_BLOCK_POSITION,
                CommonProgramNodeIds.RANDOM_DIRECTION,
                CommonProgramNodeIds.FILTER_ENTITY_MAX_HEALTH_AT_LEAST,
                CommonProgramNodeIds.FILTER_ENTITY_MAX_HEALTH_AT_MOST,
                CommonProgramNodeIds.TRIGGER_HEALTH_THRESHOLD,
                CommonProgramNodeIds.RANDOM_NUMBER,
                CommonProgramNodeIds.VEC3_OPERATION,
                CommonProgramNodeIds.VECTOR_CONSTRUCT,
                CommonProgramNodeIds.VECTOR_COMPONENTS,
                CommonProgramNodeIds.ENTITY_DATA,
                CommonProgramNodeIds.ENTITY_MOTION,
                CommonProgramNodeIds.ENTITY_HEIGHT,
                CommonProgramNodeIds.DAMAGE_AMOUNT,
                CommonProgramNodeIds.GAME_TIME,
                CommonProgramNodeIds.LOOP_INDEX,
                CommonProgramNodeIds.MELEE_TARGET,
                CommonProgramNodeIds.DEBUG_OUTPUT,
                CommonProgramNodeIds.SELECT_VALUE,
                CommonProgramNodeIds.BREAK_LOOP,
                CommonProgramNodeIds.CONTINUE_LOOP,
                CommonProgramNodeIds.WAIT,
                CommonProgramNodeIds.BLOCK_VOLUME,
                CommonProgramNodeIds.FILTER_ENTITY_EXACT,
                CommonProgramNodeIds.FILTER_BLOCK_EXACT,
                CommonProgramNodeIds.SORT_POINTS_BY_DISTANCE
        )) {
            assertNotNull(catalog.find(id), id.toString());
            assertNotNull(executors.find(id), id.toString());
        }
    }

    @Test
    void entityDataAndDebugOutputExposeConfiguredTypedPorts() {
        var catalog = AbilityProgramDefinitions.mentalout().editorCatalog();
        var entityData = new JsonObject();
        entityData.addProperty("data", "cp");
        var entityDataSchema = catalog.schema(CommonProgramNodeIds.ENTITY_DATA, entityData);
        assertNotNull(entityDataSchema);
        assertEquals(ProgramValueTypes.ENTITY_REFERENCE,
                entityDataSchema.inputs().getFirst().type());
        assertEquals(ProgramValueTypes.FLOAT,
                entityDataSchema.outputs().getFirst().type());

        var debug = new JsonObject();
        debug.addProperty("value_type", "world_position_list");
        debug.addProperty("text", "targets={value}");
        debug.addProperty("audience", "all");
        var debugSchema = catalog.schema(CommonProgramNodeIds.DEBUG_OUTPUT, debug);
        assertNotNull(debugSchema);
        assertEquals(ProgramValueTypes.WORLD_POSITION_SET,
                debugSchema.inputs().get(1).type());
        assertEquals(ProgramValueTypes.FLOW,
                debugSchema.inputs().getFirst().type());
        assertEquals(ProgramValueTypes.FLOW,
                debugSchema.outputs().getFirst().type());

        var vectorScale = new JsonObject();
        vectorScale.addProperty("type", "vector");
        vectorScale.addProperty("operator", "scale");
        var scaleSchema = catalog.schema(CommonProgramNodeIds.VEC3_OPERATION, vectorScale);
        assertNotNull(scaleSchema);
        assertEquals(List.of(ProgramValueTypes.VECTOR, ProgramValueTypes.FLOAT),
                scaleSchema.inputs().stream().map(port -> port.type()).toList());
        assertEquals(ProgramValueTypes.VECTOR, scaleSchema.outputs().getFirst().type());

        var normalize = new JsonObject();
        normalize.addProperty("type", "world_position");
        normalize.addProperty("operator", "normalize");
        var normalizeSchema = catalog.schema(CommonProgramNodeIds.VEC3_OPERATION, normalize);
        assertNotNull(normalizeSchema);
        assertEquals(1, normalizeSchema.inputs().size());
        assertEquals(ProgramValueTypes.DIRECTION, normalizeSchema.outputs().getFirst().type());
    }

    @Test
    void editorOrdersPriorityTargetsCollectionsAndTriggersDeterministically() {
        var entries = AbilityProgramDefinitions.mentalout().editorCatalog().entries().stream()
                .filter(ProgramEditorNodeCatalog.Entry::visible)
                .toList();
        var targets = entries.stream()
                .filter(entry -> entry.group() == ProgramEditorNodeCatalog.Group.TARGET)
                .toList();
        assertEquals(CommonProgramNodeIds.CASTER, targets.get(0).id());
        assertEquals(CommonProgramNodeIds.DAMAGE_ATTACKER, targets.get(1).id());
        assertEquals(CommonProgramNodeIds.LOOK_TARGET, targets.get(2).id());

        var collections = entries.stream()
                .filter(entry -> entry.group() == ProgramEditorNodeCatalog.Group.COLLECTION)
                .toList();
        var firstNonEntity = 0;
        while (firstNonEntity < collections.size()
                && collections.get(firstNonEntity).id().getPath()
                .contains("/collection/entity/")) firstNonEntity++;
        assertTrue(firstNonEntity > 0);
        assertTrue(collections.subList(firstNonEntity, collections.size()).stream()
                .noneMatch(entry -> entry.id().getPath().contains("/collection/entity/")));

        var flows = entries.stream()
                .filter(entry -> entry.group() == ProgramEditorNodeCatalog.Group.FLOW)
                .map(ProgramEditorNodeCatalog.Entry::id)
                .toList();
        assertEquals(List.of(
                CommonProgramNodeIds.TRIGGER_HURT,
                CommonProgramNodeIds.TRIGGER_LOOP,
                CommonProgramNodeIds.TRIGGER_MELEE,
                CommonProgramNodeIds.TRIGGER_MOVEMENT,
                PrecisionProgramNodeIds.ON_CAST,
                CommonProgramNodeIds.TRIGGER_HEALTH_THRESHOLD
        ), flows.subList(0, 6));
    }

    @Test
    void mergedScalarArithmeticAndComparisonNodesDriveDynamicPortTypes() {
        var catalog = AbilityProgramDefinitions.mentalout().editorCatalog();
        assertTrue(catalog.entry(CommonProgramNodeIds.SCALAR_CONSTANT).visible());
        assertTrue(catalog.entry(CommonProgramNodeIds.NUMERIC_ARITHMETIC).visible());
        assertTrue(catalog.entry(CommonProgramNodeIds.NUMERIC_COMPARE).visible());
        assertFalse(catalog.entry(CommonProgramNodeIds.INTEGER_CONSTANT).visible());
        assertFalse(catalog.entry(CommonProgramNodeIds.BIG_INTEGER_ADD).visible());
        assertFalse(catalog.entry(CommonProgramNodeIds.FLOAT_GREATER).visible());
        var floatConfiguration = new JsonObject();
        floatConfiguration.addProperty("type", "float");
        floatConfiguration.addProperty("value", "1.5");
        var floatSchema = catalog.schema(CommonProgramNodeIds.SCALAR_CONSTANT, floatConfiguration);
        assertNotNull(floatSchema);
        assertEquals(ProgramValueTypes.FLOAT, floatSchema.outputs().getFirst().type());

        var comparisonConfiguration = new JsonObject();
        comparisonConfiguration.addProperty("type", "big_integer");
        comparisonConfiguration.addProperty("operator", "greater_equal");
        var comparisonSchema = catalog.schema(
                CommonProgramNodeIds.NUMERIC_COMPARE,
                comparisonConfiguration
        );
        assertNotNull(comparisonSchema);
        assertTrue(comparisonSchema.inputs().stream()
                .allMatch(port -> port.type().equals(ProgramValueTypes.BIG_INTEGER)));

        var arithmeticConfiguration = new JsonObject();
        arithmeticConfiguration.addProperty("type", "float");
        arithmeticConfiguration.addProperty("operator", "multiply");
        var arithmeticSchema = catalog.schema(
                CommonProgramNodeIds.NUMERIC_ARITHMETIC,
                arithmeticConfiguration
        );
        assertNotNull(arithmeticSchema);
        assertTrue(arithmeticSchema.inputs().stream()
                .allMatch(port -> port.type().equals(ProgramValueTypes.FLOAT)));
        assertEquals(ProgramValueTypes.FLOAT, arithmeticSchema.outputs().getFirst().type());

        var graph = new ProgramGraph(
                List.of(
                        node(1, PrecisionProgramNodeIds.ON_CAST),
                        scalarNode(2, "integer", "7"),
                        scalarNode(3, "integer", "5"),
                        numericComparisonNode(4, "integer", "greater"),
                        variableNode(5, CommonProgramNodeIds.VARIABLE_SET, "accepted",
                                ProgramValueTypes.BOOLEAN.id()),
                        node(6, CommonProgramNodeIds.STOP)
                ),
                List.of(
                        edge(1, "flow", 5, "flow"),
                        edge(2, "value", 4, "left"),
                        edge(3, "value", 4, "right"),
                        edge(4, "result", 5, "value"),
                        edge(5, "flow", 6, "flow")
                )
        );

        assertEquals(true, run(graph, null).variables().get("accepted").value());
    }

    @Test
    void mergedNumericArithmeticExecutesTheSelectedOperation() {
        var graph = new ProgramGraph(
                List.of(
                        node(1, PrecisionProgramNodeIds.ON_CAST),
                        scalarNode(2, "integer", "7"),
                        scalarNode(3, "integer", "5"),
                        numericArithmeticNode(4, "integer", "multiply"),
                        variableNode(5, CommonProgramNodeIds.VARIABLE_SET, "result",
                                ProgramValueTypes.INTEGER.id()),
                        node(6, CommonProgramNodeIds.STOP)
                ),
                List.of(
                        edge(1, "flow", 5, "flow"),
                        edge(2, "value", 4, "left"),
                        edge(3, "value", 4, "right"),
                        edge(4, "result", 5, "value"),
                        edge(5, "flow", 6, "flow")
                )
        );

        assertEquals(35, run(graph, null).variables().get("result").value());
    }

    @Test
    void conditionalValueSelectUsesTheConfiguredType() {
        var selectConfiguration = new JsonObject();
        selectConfiguration.addProperty("type", ProgramValueTypes.INTEGER.id().toString());
        var graph = new ProgramGraph(
                List.of(
                        node(1, PrecisionProgramNodeIds.ON_CAST),
                        new ProgramGraph.Node(
                                2, CommonProgramNodeIds.BOOLEAN_CONSTANT, 1,
                                configuration("value", true)),
                        integerNode(3, 7),
                        integerNode(4, 5),
                        new ProgramGraph.Node(
                                5, CommonProgramNodeIds.SELECT_VALUE, 1,
                                selectConfiguration),
                        variableNode(6, CommonProgramNodeIds.VARIABLE_SET, "selected",
                                ProgramValueTypes.INTEGER.id()),
                        node(7, CommonProgramNodeIds.STOP)
                ),
                List.of(
                        edge(1, "flow", 6, "flow"),
                        edge(2, "value", 5, "condition"),
                        edge(3, "value", 5, "when_true"),
                        edge(4, "value", 5, "when_false"),
                        edge(5, "value", 6, "value"),
                        edge(6, "flow", 7, "flow")
                )
        );

        assertEquals(7, run(graph, null).variables().get("selected").value());
    }

    @Test
    void waitSuspendsUntilItsConfiguredTickAndThenContinues() {
        var graph = new ProgramGraph(
                List.of(
                        node(1, PrecisionProgramNodeIds.ON_CAST),
                        integerNode(2, 3),
                        node(3, CommonProgramNodeIds.WAIT),
                        node(4, CommonProgramNodeIds.STOP)
                ),
                List.of(
                        edge(1, "flow", 3, "flow"),
                        edge(2, "value", 3, "ticks"),
                        edge(3, "flow", 4, "flow")
                )
        );
        var compiled = ProgramCompiler.compile(
                graph,
                new ProgramCompileContext(CATEGORY, Set.of(), ProgramLimits.DEFAULT),
                id -> {
                    var common = CommonProgramNodeCatalog.INSTANCE.find(id);
                    return common != null ? common : PrecisionProgramNodeCatalog.INSTANCE.find(id);
                }
        );
        assertTrue(compiled.valid(), () -> compiled.diagnostics().toString());
        var session = new ProgramVm.Session(compiled.program());

        assertEquals(ProgramVmResult.Status.SUSPENDED,
                session.run(10, 1_000, CommonProgramExecutors.INSTANCE, null).status());
        assertEquals(13L, session.wakeAt());
        assertEquals(ProgramVmResult.Status.SUSPENDED,
                session.run(12, 1_000, CommonProgramExecutors.INSTANCE, null).status());
        assertEquals(ProgramVmResult.Status.COMPLETED,
                session.run(13, 1_000, CommonProgramExecutors.INSTANCE, null).status());
    }

    @Test
    void loopIndexAndWorldClockComeFromThePersistentInvocationFrame() {
        var graph = new ProgramGraph(
                List.of(
                        node(1, PrecisionProgramNodeIds.ON_CAST),
                        node(2, CommonProgramNodeIds.LOOP_INDEX),
                        variableNode(3, CommonProgramNodeIds.VARIABLE_SET, "index",
                                ProgramValueTypes.BIG_INTEGER.id()),
                        node(4, CommonProgramNodeIds.GAME_TIME),
                        variableNode(5, CommonProgramNodeIds.VARIABLE_SET, "time",
                                ProgramValueTypes.BIG_INTEGER.id()),
                        node(6, CommonProgramNodeIds.STOP)
                ),
                List.of(
                        edge(1, "flow", 3, "flow"),
                        edge(2, "loop_index", 3, "value"),
                        edge(3, "flow", 5, "flow"),
                        edge(4, "time", 5, "value"),
                        edge(5, "flow", 6, "flow")
                )
        );
        var invocation = new ProgramInvocationContext(
                java.util.UUID.randomUUID(),
                2,
                ProgramTriggers.Type.LOOP,
                null,
                41L,
                null,
                null,
                null
        );
        var frame = new ProgramExecutionFrame(
                new ProgramActionTransaction(), null, invocation, () -> 12_345L);
        var variables = run(graph, frame).variables();

        assertEquals(BigInteger.valueOf(41L), variables.get("index").value());
        assertEquals(BigInteger.valueOf(12_345L), variables.get("time").value());
    }

    @Test
    void collectionBuilderCreatesDynamicTypedInputsAndKeepsLegacyEmptyBehavior() {
        var catalog = AbilityProgramDefinitions.mentalout().editorCatalog();
        var builderId = CommonProgramNodeCatalog.CollectionDomain.DIRECTION.id("empty");
        var legacySchema = catalog.schema(builderId, new JsonObject());
        assertNotNull(legacySchema);
        assertTrue(legacySchema.inputs().isEmpty());

        var builderConfiguration = new JsonObject();
        builderConfiguration.addProperty("inputs", 2);
        var builderSchema = catalog.schema(builderId, builderConfiguration);
        assertNotNull(builderSchema);
        assertEquals(List.of("value_1", "value_2"), builderSchema.inputs().stream()
                .map(port -> port.name()).toList());
        assertTrue(builderSchema.inputs().stream()
                .allMatch(port -> port.type().equals(ProgramValueTypes.DIRECTION)));

        var graph = new ProgramGraph(
                List.of(
                        node(1, PrecisionProgramNodeIds.ON_CAST),
                        directionNode(2, 1.0, 0.0, 0.0),
                        directionNode(3, 0.0, 1.0, 0.0),
                        collectionBuilderNode(4, builderId, 2),
                        variableNode(5, CommonProgramNodeIds.VARIABLE_SET, "directions",
                                ProgramValueTypes.DIRECTION_SET.id()),
                        node(6, CommonProgramNodeIds.STOP)
                ),
                List.of(
                        edge(1, "flow", 5, "flow"),
                        edge(2, "direction", 4, "value_1"),
                        edge(3, "direction", 4, "value_2"),
                        edge(4, "values", 5, "value"),
                        edge(5, "flow", 6, "flow")
                )
        );

        assertEquals(List.of(
                new ProgramDirection(1.0, 0.0, 0.0),
                new ProgramDirection(0.0, 1.0, 0.0)
        ), run(graph, null).variables().get("directions").value());
    }

    @Test
    void entityPositionPassesTheSelectedAnchorToTheTargetResolver() {
        var expected = new ProgramWorldPosition(OVERWORLD, 4.0, 65.0, 8.0);
        var seenAnchor = new ProgramEntityPositionAnchor[1];
        ProgramTargetResolver resolver = new ProgramTargetResolver() {
            @Override
            public Object caster() {
                return "caster";
            }

            @Override
            public Optional<ProgramWorldPosition> positionOf(Object entityReference) {
                return Optional.empty();
            }

            @Override
            public Optional<ProgramWorldPosition> positionOf(
                    Object entityReference,
                    ProgramEntityPositionAnchor anchor
            ) {
                seenAnchor[0] = anchor;
                return Optional.of(expected);
            }

            @Override
            public Optional<ProgramDirection> lookDirectionOf(Object entityReference) {
                return Optional.empty();
            }

            @Override
            public List<?> entitiesAround(ProgramWorldPosition center, double radius) {
                return List.of();
            }

            @Override
            public Optional<ProgramBlockPosition> raycastBlock(
                    ProgramWorldPosition origin,
                    ProgramDirection direction,
                    double maximumDistance
            ) {
                return Optional.empty();
            }
        };
        var graph = new ProgramGraph(
                List.of(
                        node(1, PrecisionProgramNodeIds.ON_CAST),
                        node(2, CommonProgramNodeIds.CASTER),
                        entityPositionNode(3, "center"),
                        variableNode(4, CommonProgramNodeIds.VARIABLE_SET, "position",
                                ProgramValueTypes.WORLD_POSITION.id()),
                        node(5, CommonProgramNodeIds.STOP)
                ),
                List.of(
                        edge(1, "flow", 4, "flow"),
                        edge(2, "entity", 3, "entity"),
                        edge(3, "position", 4, "value"),
                        edge(4, "flow", 5, "flow")
                )
        );

        assertEquals(expected, run(graph, resolver).variables().get("position").value());
        assertEquals(ProgramEntityPositionAnchor.CENTER, seenAnchor[0]);
    }

    @Test
    void advancedCommonNodesExecuteTypedBoundsVolumesAndDistanceSorting() {
        var graph = new ProgramGraph(
                List.of(
                        node(1, PrecisionProgramNodeIds.ON_CAST),
                        scalarNode(2, "integer", "-7"),
                        numericArithmeticNode(3, "integer", "absolute"),
                        variableNode(4, CommonProgramNodeIds.VARIABLE_SET, "absolute",
                                ProgramValueTypes.INTEGER.id()),
                        blockPositionNode(5, OVERWORLD, 0, 64, 0),
                        blockPositionNode(6, OVERWORLD, 1, 65, 1),
                        node(7, CommonProgramNodeIds.BLOCK_VOLUME),
                        variableNode(8, CommonProgramNodeIds.VARIABLE_SET, "volume",
                                ProgramValueTypes.BLOCK_POSITION_SET.id()),
                        randomNumberNode(9, "integer", "4", "4"),
                        variableNode(10, CommonProgramNodeIds.VARIABLE_SET, "random",
                                ProgramValueTypes.INTEGER.id()),
                        directionNode(20, 1.0, 0.0, 0.0),
                        directionNode(21, 0.0, 1.0, 0.0),
                        vec3OperationNode(22, "direction", "cross"),
                        variableNode(23, CommonProgramNodeIds.VARIABLE_SET, "cross",
                                ProgramValueTypes.DIRECTION.id()),
                        worldPositionNode(11, OVERWORLD, 10.0, 64.0, 0.0),
                        worldPositionNode(12, OVERWORLD, 2.0, 64.0, 0.0),
                        node(13, CommonProgramNodeCatalog.CollectionDomain.WORLD_POSITION.id("singleton")),
                        node(14, CommonProgramNodeCatalog.CollectionDomain.WORLD_POSITION.id("singleton")),
                        node(15, CommonProgramNodeCatalog.CollectionDomain.WORLD_POSITION.id("union")),
                        worldPositionNode(16, OVERWORLD, 0.0, 64.0, 0.0),
                        distanceSortNode(17, "world_position", "ascending"),
                        variableNode(18, CommonProgramNodeIds.VARIABLE_SET, "sorted",
                                ProgramValueTypes.WORLD_POSITION_SET.id()),
                        node(19, CommonProgramNodeIds.STOP)
                ),
                List.of(
                        edge(1, "flow", 4, "flow"),
                        edge(2, "value", 3, "value"),
                        edge(3, "result", 4, "value"),
                        edge(4, "flow", 8, "flow"),
                        edge(5, "position", 7, "first"),
                        edge(6, "position", 7, "second"),
                        edge(7, "blocks", 8, "value"),
                        edge(8, "flow", 10, "flow"),
                        edge(9, "value", 10, "value"),
                        edge(10, "flow", 23, "flow"),
                        edge(20, "direction", 22, "left"),
                        edge(21, "direction", 22, "right"),
                        edge(22, "result", 23, "value"),
                        edge(23, "flow", 18, "flow"),
                        edge(11, "position", 13, "value"),
                        edge(12, "position", 14, "value"),
                        edge(13, "values", 15, "left"),
                        edge(14, "values", 15, "right"),
                        edge(15, "values", 17, "values"),
                        edge(16, "position", 17, "origin"),
                        edge(17, "values", 18, "value"),
                        edge(18, "flow", 19, "flow")
                )
        );

        var variables = run(graph, null).variables();
        assertEquals(7, variables.get("absolute").value());
        assertEquals(8, ((List<?>) variables.get("volume").value()).size());
        assertEquals(4, variables.get("random").value());
        assertEquals(new ProgramDirection(0.0, 0.0, 1.0),
                variables.get("cross").value());
        assertEquals(List.of(
                new ProgramWorldPosition(OVERWORLD, 2.0, 64.0, 0.0),
                new ProgramWorldPosition(OVERWORLD, 10.0, 64.0, 0.0)
        ), variables.get("sorted").value());
    }

    @Test
    void vectorConstructionScalingLengthAndComponentsCompose() {
        var graph = new ProgramGraph(
                List.of(
                        node(1, PrecisionProgramNodeIds.ON_CAST),
                        floatNode(2, 3.0),
                        floatNode(3, 4.0),
                        floatNode(4, 0.0),
                        node(5, CommonProgramNodeIds.VECTOR_CONSTRUCT),
                        vec3OperationNode(6, "vector", "length"),
                        variableNode(7, CommonProgramNodeIds.VARIABLE_SET, "length",
                                ProgramValueTypes.FLOAT.id()),
                        floatNode(8, 2.0),
                        vec3OperationNode(9, "vector", "scale"),
                        variableNode(10, CommonProgramNodeIds.VARIABLE_SET, "scaled",
                                ProgramValueTypes.VECTOR.id()),
                        node(11, CommonProgramNodeIds.VECTOR_COMPONENTS),
                        variableNode(12, CommonProgramNodeIds.VARIABLE_SET, "scaled_x",
                                ProgramValueTypes.FLOAT.id()),
                        node(13, CommonProgramNodeIds.STOP)
                ),
                List.of(
                        edge(1, "flow", 7, "flow"),
                        edge(2, "value", 5, "x"),
                        edge(3, "value", 5, "y"),
                        edge(4, "value", 5, "z"),
                        edge(5, "vector", 6, "value"),
                        edge(6, "result", 7, "value"),
                        edge(7, "flow", 10, "flow"),
                        edge(5, "vector", 9, "value"),
                        edge(8, "value", 9, "scalar"),
                        edge(9, "result", 10, "value"),
                        edge(10, "flow", 12, "flow"),
                        edge(9, "result", 11, "value"),
                        edge(11, "x", 12, "value"),
                        edge(12, "flow", 13, "flow")
                )
        );

        var variables = run(graph, null).variables();

        assertEquals(5.0, (Double) variables.get("length").value(), 1.0E-9);
        assertEquals(new ProgramVector(6.0, 8.0, 0.0), variables.get("scaled").value());
        assertEquals(6.0, (Double) variables.get("scaled_x").value(), 1.0E-9);
    }

    @Test
    void gameTimeQueryExposesTheExecutionTickAsABigInteger() {
        var graph = new ProgramGraph(
                List.of(
                        node(1, PrecisionProgramNodeIds.ON_CAST),
                        node(2, CommonProgramNodeIds.GAME_TIME),
                        variableNode(3, CommonProgramNodeIds.VARIABLE_SET, "time",
                                ProgramValueTypes.BIG_INTEGER.id()),
                        node(4, CommonProgramNodeIds.STOP)
                ),
                List.of(
                        edge(1, "flow", 3, "flow"),
                        edge(2, "time", 3, "value"),
                        edge(3, "flow", 4, "flow")
                )
        );

        assertEquals(BigInteger.ZERO, run(graph, null).variables().get("time").value());
    }

    @Test
    void blockVolumeHandlesMaximumIntegerCoordinatesWithoutOverflow() {
        var graph = new ProgramGraph(
                List.of(
                        node(1, PrecisionProgramNodeIds.ON_CAST),
                        blockPositionNode(2, OVERWORLD, Integer.MAX_VALUE - 1, 64, 0),
                        blockPositionNode(3, OVERWORLD, Integer.MAX_VALUE, 64, 0),
                        node(4, CommonProgramNodeIds.BLOCK_VOLUME),
                        variableNode(5, CommonProgramNodeIds.VARIABLE_SET, "volume",
                                ProgramValueTypes.BLOCK_POSITION_SET.id()),
                        node(6, CommonProgramNodeIds.STOP)
                ),
                List.of(
                        edge(1, "flow", 5, "flow"),
                        edge(2, "position", 4, "first"),
                        edge(3, "position", 4, "second"),
                        edge(4, "blocks", 5, "value"),
                        edge(5, "flow", 6, "flow")
                )
        );

        assertEquals(List.of(
                new ProgramBlockPosition(OVERWORLD, Integer.MAX_VALUE - 1, 64, 0),
                new ProgramBlockPosition(OVERWORLD, Integer.MAX_VALUE, 64, 0)
        ), run(graph, null).variables().get("volume").value());
    }

    @Test
    void commonCasterLookTargetAndDistanceFilterComposeAcrossCategories() {
        var center = new ProgramWorldPosition(OVERWORLD, 0.0, 64.0, 0.0);
        var resolver = new ProgramTargetResolver() {
            @Override
            public Object caster() {
                return "caster";
            }

            @Override
            public Optional<Object> lookTarget() {
                return Optional.of("near");
            }

            @Override
            public Optional<ProgramWorldPosition> positionOf(Object entityReference) {
                return switch (entityReference.toString()) {
                    case "caster" -> Optional.of(center);
                    case "near" -> Optional.of(new ProgramWorldPosition(
                            OVERWORLD, 3.0, 64.0, 0.0));
                    case "far" -> Optional.of(new ProgramWorldPosition(
                            OVERWORLD, 12.0, 64.0, 0.0));
                    default -> Optional.empty();
                };
            }

            @Override
            public Optional<ProgramDirection> lookDirectionOf(Object entityReference) {
                return Optional.empty();
            }

            @Override
            public List<?> entitiesAround(ProgramWorldPosition position, double radius) {
                return List.of("near", "far");
            }

            @Override
            public Optional<ProgramBlockPosition> raycastBlock(
                    ProgramWorldPosition origin,
                    ProgramDirection direction,
                    double maximumDistance
            ) {
                return Optional.empty();
            }
        };
        var graph = new ProgramGraph(
                List.of(
                        node(1, PrecisionProgramNodeIds.ON_CAST),
                        node(2, CommonProgramNodeIds.CASTER),
                        variableNode(3, CommonProgramNodeIds.VARIABLE_SET, "caster",
                                ProgramValueTypes.ENTITY_REFERENCE.id()),
                        node(4, CommonProgramNodeIds.LOOK_TARGET),
                        variableNode(5, CommonProgramNodeIds.VARIABLE_SET, "look_target",
                                ProgramValueTypes.ENTITY_REFERENCE.id()),
                        worldPositionNode(6, OVERWORLD, 0.0, 64.0, 0.0),
                        floatNode(7, 32.0),
                        node(8, CommonProgramNodeIds.ENTITIES_AROUND),
                        floatNode(9, 5.0),
                        node(10, CommonProgramNodeIds.FILTER_ENTITY_DISTANCE),
                        variableNode(11, CommonProgramNodeIds.VARIABLE_SET, "nearby",
                                ProgramValueTypes.ENTITY_SET.id()),
                        node(12, CommonProgramNodeIds.STOP)
                ),
                List.of(
                        edge(1, "flow", 3, "flow"),
                        edge(2, "entity", 3, "value"),
                        edge(3, "flow", 5, "flow"),
                        edge(4, "entity", 5, "value"),
                        edge(5, "flow", 11, "flow"),
                        edge(6, "position", 8, "center"),
                        edge(7, "value", 8, "radius"),
                        edge(8, "entities", 10, "entities"),
                        edge(6, "position", 10, "center"),
                        edge(9, "value", 10, "radius"),
                        edge(10, "entities", 11, "value"),
                        edge(11, "flow", 12, "flow")
                )
        );

        var variables = run(graph, resolver).variables();
        assertEquals("caster", variables.get("caster").value());
        assertEquals("near", variables.get("look_target").value());
        assertEquals(List.of("near"), variables.get("nearby").value());
    }

    @Test
    void casterLookTargetCanSwitchToBlockPositionOutput() {
        var block = new ProgramBlockPosition(OVERWORLD, 4, 70, -3);
        var resolver = new ProgramTargetResolver() {
            @Override
            public Optional<ProgramBlockPosition> lookBlockTarget() {
                return Optional.of(block);
            }

            @Override
            public Optional<ProgramWorldPosition> positionOf(Object entityReference) {
                return Optional.empty();
            }

            @Override
            public Optional<ProgramDirection> lookDirectionOf(Object entityReference) {
                return Optional.empty();
            }

            @Override
            public List<?> entitiesAround(ProgramWorldPosition center, double radius) {
                return List.of();
            }

            @Override
            public Optional<ProgramBlockPosition> raycastBlock(
                    ProgramWorldPosition origin,
                    ProgramDirection direction,
                    double maximumDistance
            ) {
                return Optional.empty();
            }
        };
        var graph = new ProgramGraph(
                List.of(
                        node(1, PrecisionProgramNodeIds.ON_CAST),
                        lookTargetNode(2, "block"),
                        variableNode(3, CommonProgramNodeIds.VARIABLE_SET, "look_block",
                                ProgramValueTypes.BLOCK_POSITION.id()),
                        node(4, CommonProgramNodeIds.STOP)
                ),
                List.of(
                        edge(1, "flow", 3, "flow"),
                        edge(2, "block", 3, "value"),
                        edge(3, "flow", 4, "flow")
                )
        );

        assertEquals(block, run(graph, resolver).variables().get("look_block").value());
    }

    @Test
    void randomEntitySelectsTheOnlyEntityInASet() {
        var resolver = new ProgramTargetResolver() {
            @Override
            public Object caster() {
                return "only";
            }

            @Override
            public Optional<ProgramWorldPosition> positionOf(Object entityReference) {
                return Optional.empty();
            }

            @Override
            public Optional<ProgramDirection> lookDirectionOf(Object entityReference) {
                return Optional.empty();
            }

            @Override
            public List<?> entitiesAround(ProgramWorldPosition center, double radius) {
                return List.of();
            }

            @Override
            public Optional<ProgramBlockPosition> raycastBlock(
                    ProgramWorldPosition origin,
                    ProgramDirection direction,
                    double maximumDistance
            ) {
                return Optional.empty();
            }
        };
        var graph = new ProgramGraph(
                List.of(
                        node(1, PrecisionProgramNodeIds.ON_CAST),
                        node(2, CommonProgramNodeIds.CASTER),
                        node(3, CommonProgramNodeCatalog.CollectionDomain.ENTITY.id("singleton")),
                        node(4, CommonProgramNodeIds.RANDOM_ENTITY),
                        variableNode(5, CommonProgramNodeIds.VARIABLE_SET, "selected",
                                ProgramValueTypes.ENTITY_REFERENCE.id()),
                        node(6, CommonProgramNodeIds.STOP)
                ),
                List.of(
                        edge(1, "flow", 5, "flow"),
                        edge(2, "entity", 3, "value"),
                        edge(3, "values", 4, "entities"),
                        edge(4, "entity", 5, "value"),
                        edge(5, "flow", 6, "flow")
                )
        );

        assertEquals("only", run(graph, resolver).variables().get("selected").value());
    }

    @Test
    void nearestEntityToPositionSelectsTheClosestValidEntity() {
        var resolver = new ProgramTargetResolver() {
            @Override
            public Object caster() {
                return "far";
            }

            @Override
            public Optional<Object> lookTarget() {
                return Optional.of("near");
            }

            @Override
            public Optional<ProgramWorldPosition> positionOf(Object entityReference) {
                return switch (entityReference.toString()) {
                    case "near" -> Optional.of(new ProgramWorldPosition(
                            OVERWORLD, 2.0, 64.0, 0.0));
                    case "far" -> Optional.of(new ProgramWorldPosition(
                            OVERWORLD, 9.0, 64.0, 0.0));
                    default -> Optional.empty();
                };
            }

            @Override
            public Optional<ProgramDirection> lookDirectionOf(Object entityReference) {
                return Optional.empty();
            }

            @Override
            public List<?> entitiesAround(ProgramWorldPosition center, double radius) {
                return List.of();
            }

            @Override
            public Optional<ProgramBlockPosition> raycastBlock(
                    ProgramWorldPosition origin,
                    ProgramDirection direction,
                    double maximumDistance
            ) {
                return Optional.empty();
            }
        };
        var entityDomain = CommonProgramNodeCatalog.CollectionDomain.ENTITY;
        var graph = new ProgramGraph(
                List.of(
                        node(1, PrecisionProgramNodeIds.ON_CAST),
                        node(2, CommonProgramNodeIds.CASTER),
                        node(3, CommonProgramNodeIds.LOOK_TARGET),
                        node(4, entityDomain.id("singleton")),
                        node(5, entityDomain.id("singleton")),
                        node(6, entityDomain.id("union")),
                        worldPositionNode(7, OVERWORLD, 0.0, 64.0, 0.0),
                        node(8, CommonProgramNodeIds.NEAREST_ENTITY_TO_POSITION),
                        variableNode(9, CommonProgramNodeIds.VARIABLE_SET, "nearest",
                                ProgramValueTypes.ENTITY_REFERENCE.id()),
                        node(10, CommonProgramNodeIds.STOP)
                ),
                List.of(
                        edge(1, "flow", 9, "flow"),
                        edge(2, "entity", 4, "value"),
                        edge(3, "entity", 5, "value"),
                        edge(4, "values", 6, "left"),
                        edge(5, "values", 6, "right"),
                        edge(7, "position", 8, "position"),
                        edge(6, "values", 8, "entities"),
                        edge(8, "entity", 9, "value"),
                        edge(9, "flow", 10, "flow")
                )
        );

        assertEquals("near", run(graph, resolver).variables().get("nearest").value());
    }

    @Test
    void randomSpatialNodesSelectTheOnlyValueInEachSet() {
        var world = new ProgramWorldPosition(OVERWORLD, 1.25, 70.0, -2.5);
        assertEquals(world, randomSingle(
                worldPositionNode(2, OVERWORLD, world.x(), world.y(), world.z()),
                CommonProgramNodeCatalog.CollectionDomain.WORLD_POSITION.id("singleton"),
                CommonProgramNodeIds.RANDOM_WORLD_POSITION,
                "position", "positions", "position",
                ProgramValueTypes.WORLD_POSITION.id()));

        var block = new ProgramBlockPosition(OVERWORLD, 4, 71, -3);
        assertEquals(block, randomSingle(
                blockPositionNode(2, OVERWORLD, block.x(), block.y(), block.z()),
                CommonProgramNodeCatalog.CollectionDomain.BLOCK_POSITION.id("singleton"),
                CommonProgramNodeIds.RANDOM_BLOCK_POSITION,
                "position", "blocks", "block",
                ProgramValueTypes.BLOCK_POSITION.id()));

        var direction = new ProgramDirection(0.0, 1.0, 0.0);
        assertEquals(direction, randomSingle(
                directionNode(2, direction.x(), direction.y(), direction.z()),
                CommonProgramNodeCatalog.CollectionDomain.DIRECTION.id("singleton"),
                CommonProgramNodeIds.RANDOM_DIRECTION,
                "direction", "directions", "direction",
                ProgramValueTypes.DIRECTION.id()));
    }

    @Test
    void arbitraryPrecisionCounterRunsThroughVariableAndCyclicBranch() {
        var initial = BigInteger.ONE.shiftLeft(200);
        var limit = initial.add(BigInteger.valueOf(5));
        var graph = new ProgramGraph(
                List.of(
                        node(1, PrecisionProgramNodeIds.ON_CAST),
                        bigIntegerNode(2, initial),
                        variableNode(3, CommonProgramNodeIds.VARIABLE_SET, "counter",
                                ProgramValueTypes.BIG_INTEGER.id()),
                        bigIntegerNode(4, BigInteger.ONE),
                        bigIntegerNode(5, limit),
                        variableNode(6, CommonProgramNodeIds.VARIABLE_GET, "counter",
                                ProgramValueTypes.BIG_INTEGER.id()),
                        node(7, CommonProgramNodeIds.BIG_INTEGER_LESS),
                        node(8, CommonProgramNodeIds.BRANCH),
                        node(9, CommonProgramNodeIds.BIG_INTEGER_ADD),
                        variableNode(10, CommonProgramNodeIds.VARIABLE_SET, "counter",
                                ProgramValueTypes.BIG_INTEGER.id()),
                        node(11, CommonProgramNodeIds.STOP)
                ),
                List.of(
                        edge(1, "flow", 3, "flow"),
                        edge(2, "value", 3, "value"),
                        edge(3, "flow", 8, "flow"),
                        edge(6, "value", 7, "left"),
                        edge(5, "value", 7, "right"),
                        edge(7, "result", 8, "condition"),
                        edge(8, "true", 10, "flow"),
                        edge(6, "value", 9, "left"),
                        edge(4, "value", 9, "right"),
                        edge(9, "result", 10, "value"),
                        edge(10, "flow", 8, "flow"),
                        edge(8, "false", 11, "flow")
                )
        );

        var session = run(graph, null);

        assertEquals(limit, session.variables().get("counter").value());
    }

    @Test
    void spatialLoopSelectsAnOrderedSetOfBlocks() {
        var graph = new ProgramGraph(
                List.of(
                        node(1, PrecisionProgramNodeIds.ON_CAST),
                        node(2, CommonProgramNodeCatalog.CollectionDomain.BLOCK_POSITION.id("empty")),
                        variableNode(3, CommonProgramNodeIds.VARIABLE_SET, "blocks",
                                ProgramValueTypes.BLOCK_POSITION_SET.id()),
                        integerNode(4, 0),
                        variableNode(5, CommonProgramNodeIds.VARIABLE_SET, "index",
                                ProgramValueTypes.INTEGER.id()),
                        variableNode(6, CommonProgramNodeIds.VARIABLE_GET, "index",
                                ProgramValueTypes.INTEGER.id()),
                        integerNode(7, 3),
                        node(8, CommonProgramNodeIds.INTEGER_LESS),
                        node(9, CommonProgramNodeIds.BRANCH),
                        worldPositionNode(10, OVERWORLD, 0.0, 64.0, 0.0),
                        directionNode(11, 1.0, 0.0, 0.0),
                        node(12, CommonProgramNodeIds.WORLD_POSITION_OFFSET),
                        node(13, CommonProgramNodeIds.POSITION_TO_BLOCK),
                        node(14, CommonProgramNodeCatalog.CollectionDomain.BLOCK_POSITION.id("singleton")),
                        variableNode(15, CommonProgramNodeIds.VARIABLE_GET, "blocks",
                                ProgramValueTypes.BLOCK_POSITION_SET.id()),
                        node(16, CommonProgramNodeCatalog.CollectionDomain.BLOCK_POSITION.id("union")),
                        variableNode(17, CommonProgramNodeIds.VARIABLE_SET, "blocks",
                                ProgramValueTypes.BLOCK_POSITION_SET.id()),
                        integerNode(18, 1),
                        node(19, CommonProgramNodeIds.INTEGER_ADD),
                        variableNode(20, CommonProgramNodeIds.VARIABLE_SET, "index",
                                ProgramValueTypes.INTEGER.id()),
                        node(21, CommonProgramNodeIds.STOP)
                ),
                List.of(
                        edge(1, "flow", 3, "flow"),
                        edge(2, "values", 3, "value"),
                        edge(3, "flow", 5, "flow"),
                        edge(4, "value", 5, "value"),
                        edge(5, "flow", 9, "flow"),
                        edge(6, "value", 8, "left"),
                        edge(7, "value", 8, "right"),
                        edge(8, "result", 9, "condition"),
                        edge(9, "true", 17, "flow"),
                        edge(10, "position", 12, "position"),
                        edge(11, "direction", 12, "direction"),
                        edge(6, "value", 12, "distance"),
                        edge(12, "position", 13, "position"),
                        edge(13, "block", 14, "value"),
                        edge(15, "value", 16, "left"),
                        edge(14, "values", 16, "right"),
                        edge(16, "values", 17, "value"),
                        edge(17, "flow", 20, "flow"),
                        edge(6, "value", 19, "left"),
                        edge(18, "value", 19, "right"),
                        edge(19, "result", 20, "value"),
                        edge(20, "flow", 9, "flow"),
                        edge(9, "false", 21, "flow")
                )
        );

        var session = run(graph, null);

        assertEquals(List.of(
                new ProgramBlockPosition(OVERWORLD, 0, 64, 0),
                new ProgramBlockPosition(OVERWORLD, 1, 64, 0),
                new ProgramBlockPosition(OVERWORLD, 2, 64, 0)
        ), session.variables().get("blocks").value());
    }

    @Test
    void foreachBranchAndAccumulatorComposeAnEntityFilter() {
        var graph = new ProgramGraph(
                List.of(
                        node(1, PrecisionProgramNodeIds.ON_CAST),
                        node(2, CommonProgramNodeCatalog.CollectionDomain.ENTITY.id("empty")),
                        variableNode(3, CommonProgramNodeIds.VARIABLE_SET, "accepted",
                                ProgramValueTypes.ENTITY_SET.id()),
                        worldPositionNode(4, OVERWORLD, 0.0, 64.0, 0.0),
                        floatNode(5, 8.0),
                        node(6, CommonProgramNodeIds.ENTITIES_AROUND),
                        node(7, CommonProgramNodeCatalog.CollectionDomain.ENTITY.id("foreach")),
                        integerNode(8, 2),
                        node(9, CommonProgramNodeCatalog.CollectionDomain.ENTITY.id("get")),
                        node(10, CommonProgramNodeIds.ENTITY_EQUAL),
                        node(11, CommonProgramNodeIds.BRANCH),
                        node(12, CommonProgramNodeCatalog.CollectionDomain.ENTITY.id("singleton")),
                        variableNode(13, CommonProgramNodeIds.VARIABLE_GET, "accepted",
                                ProgramValueTypes.ENTITY_SET.id()),
                        node(14, CommonProgramNodeCatalog.CollectionDomain.ENTITY.id("union")),
                        variableNode(15, CommonProgramNodeIds.VARIABLE_SET, "accepted",
                                ProgramValueTypes.ENTITY_SET.id()),
                        node(16, CommonProgramNodeIds.STOP)
                ),
                List.of(
                        edge(1, "flow", 3, "flow"),
                        edge(2, "values", 3, "value"),
                        edge(3, "flow", 7, "flow"),
                        edge(4, "position", 6, "center"),
                        edge(5, "value", 6, "radius"),
                        edge(6, "entities", 7, "values"),
                        edge(7, "body", 11, "flow"),
                        edge(7, "value", 10, "left"),
                        edge(6, "entities", 9, "values"),
                        edge(8, "value", 9, "index"),
                        edge(9, "value", 10, "right"),
                        edge(10, "result", 11, "condition"),
                        edge(11, "true", 7, "flow"),
                        edge(11, "false", 15, "flow"),
                        edge(7, "value", 12, "value"),
                        edge(13, "value", 14, "left"),
                        edge(12, "values", 14, "right"),
                        edge(14, "values", 15, "value"),
                        edge(15, "flow", 7, "flow"),
                        edge(7, "done", 16, "flow")
                )
        );
        var resolver = new ProgramTargetResolver() {
            @Override
            public Optional<ProgramWorldPosition> positionOf(Object entityReference) {
                return Optional.empty();
            }

            @Override
            public Optional<ProgramDirection> lookDirectionOf(Object entityReference) {
                return Optional.empty();
            }

            @Override
            public List<?> entitiesAround(ProgramWorldPosition center, double radius) {
                return List.of("alpha", "excluded", "omega");
            }

            @Override
            public Optional<ProgramBlockPosition> raycastBlock(
                    ProgramWorldPosition origin,
                    ProgramDirection direction,
                    double maximumDistance
            ) {
                return Optional.empty();
            }
        };

        var session = run(graph, resolver);

        assertEquals(List.of("alpha", "omega"), session.variables().get("accepted").value());
    }

    @Test
    void foreachExposesOneBasedIterationIndex() {
        var graph = new ProgramGraph(
                List.of(
                        node(1, PrecisionProgramNodeIds.ON_CAST),
                        worldPositionNode(2, OVERWORLD, 0.0, 64.0, 0.0),
                        worldPositionNode(3, OVERWORLD, 1.0, 64.0, 0.0),
                        worldPositionNode(4, OVERWORLD, 2.0, 64.0, 0.0),
                        collectionBuilderNode(
                                5,
                                CommonProgramNodeCatalog.CollectionDomain.WORLD_POSITION.id("empty"),
                                3
                        ),
                        node(6, CommonProgramNodeCatalog.CollectionDomain.WORLD_POSITION.id("foreach")),
                        variableNode(7, CommonProgramNodeIds.VARIABLE_SET, "last_index",
                                ProgramValueTypes.INTEGER.id()),
                        node(8, CommonProgramNodeIds.STOP)
                ),
                List.of(
                        edge(1, "flow", 6, "flow"),
                        edge(2, "position", 5, "value_1"),
                        edge(3, "position", 5, "value_2"),
                        edge(4, "position", 5, "value_3"),
                        edge(5, "values", 6, "values"),
                        edge(6, "body", 7, "flow"),
                        edge(6, "index", 7, "value"),
                        edge(7, "flow", 6, "flow"),
                        edge(6, "done", 8, "flow")
                )
        );

        assertEquals(3, run(graph, null).variables().get("last_index").value());
    }

    @Test
    void raycastsAndEntityProjectionExposePositionLookAndMovement() {
        var graph = new ProgramGraph(
                List.of(
                        node(1, PrecisionProgramNodeIds.ON_CAST),
                        worldPositionNode(2, OVERWORLD, 0.0, 64.0, 0.0),
                        directionNode(3, 1.0, 0.0, 0.0),
                        floatNode(4, 16.0),
                        node(5, CommonProgramNodeIds.RAYCAST_ENTITY),
                        variableNode(6, CommonProgramNodeIds.VARIABLE_SET, "target",
                                ProgramValueTypes.ENTITY_REFERENCE.id()),
                        variableNode(7, CommonProgramNodeIds.VARIABLE_GET, "target",
                                ProgramValueTypes.ENTITY_REFERENCE.id()),
                        node(8, CommonProgramNodeIds.ENTITY_POSITION),
                        variableNode(9, CommonProgramNodeIds.VARIABLE_SET, "target_position",
                                ProgramValueTypes.WORLD_POSITION.id()),
                        node(10, CommonProgramNodeIds.ENTITY_LOOK_DIRECTION),
                        variableNode(11, CommonProgramNodeIds.VARIABLE_SET, "target_direction",
                                ProgramValueTypes.DIRECTION.id()),
                        node(12, CommonProgramNodeIds.RAYCAST_BLOCK),
                        variableNode(13, CommonProgramNodeIds.VARIABLE_SET, "target_block",
                                ProgramValueTypes.BLOCK_POSITION.id()),
                        node(14, CommonProgramNodeIds.ENTITY_MOVEMENT_DIRECTION),
                        variableNode(15, CommonProgramNodeIds.VARIABLE_SET, "target_movement",
                                ProgramValueTypes.DIRECTION.id()),
                        node(17, CommonProgramNodeIds.ENTITY_MOTION),
                        variableNode(18, CommonProgramNodeIds.VARIABLE_SET, "target_motion",
                                ProgramValueTypes.VECTOR.id()),
                        variableNode(19, CommonProgramNodeIds.VARIABLE_SET, "target_speed",
                                ProgramValueTypes.FLOAT.id()),
                        node(20, CommonProgramNodeIds.ENTITY_HEIGHT),
                        variableNode(21, CommonProgramNodeIds.VARIABLE_SET, "target_height",
                                ProgramValueTypes.FLOAT.id()),
                        node(16, CommonProgramNodeIds.STOP)
                ),
                List.of(
                        edge(1, "flow", 6, "flow"),
                        edge(2, "position", 5, "origin"),
                        edge(3, "direction", 5, "direction"),
                        edge(4, "value", 5, "range"),
                        edge(5, "entity", 6, "value"),
                        edge(6, "flow", 9, "flow"),
                        edge(7, "value", 8, "entity"),
                        edge(8, "position", 9, "value"),
                        edge(9, "flow", 11, "flow"),
                        edge(7, "value", 10, "entity"),
                        edge(10, "direction", 11, "value"),
                        edge(11, "flow", 13, "flow"),
                        edge(8, "position", 12, "origin"),
                        edge(10, "direction", 12, "direction"),
                        edge(4, "value", 12, "range"),
                        edge(12, "block", 13, "value"),
                        edge(13, "flow", 15, "flow"),
                        edge(7, "value", 14, "entity"),
                        edge(14, "direction", 15, "value"),
                        edge(15, "flow", 18, "flow"),
                        edge(7, "value", 17, "entity"),
                        edge(17, "vector", 18, "value"),
                        edge(18, "flow", 19, "flow"),
                        edge(17, "speed", 19, "value"),
                        edge(19, "flow", 21, "flow"),
                        edge(7, "value", 20, "entity"),
                        edge(20, "height", 21, "value"),
                        edge(21, "flow", 16, "flow")
                )
        );
        var targetPosition = new ProgramWorldPosition(OVERWORLD, 2.0, 64.0, 0.0);
        var targetDirection = new ProgramDirection(0.0, -1.0, 0.0);
        var targetMovement = new ProgramDirection(1.0, 1.0, 0.0);
        var targetMotion = new ProgramVector(0.25, -0.5, 0.75);
        var targetBlock = new ProgramBlockPosition(OVERWORLD, 2, 63, 0);
        var resolver = new ProgramTargetResolver() {
            @Override
            public Optional<ProgramWorldPosition> positionOf(Object entityReference) {
                return entityReference.equals("target")
                        ? Optional.of(targetPosition)
                        : Optional.empty();
            }

            @Override
            public Optional<ProgramDirection> lookDirectionOf(Object entityReference) {
                return entityReference.equals("target")
                        ? Optional.of(targetDirection)
                        : Optional.empty();
            }

            @Override
            public Optional<ProgramDirection> movementDirectionOf(Object entityReference) {
                return entityReference.equals("target")
                        ? Optional.of(targetMovement)
                        : Optional.empty();
            }

            @Override
            public Optional<ProgramVector> motionOf(Object entityReference) {
                return entityReference.equals("target")
                        ? Optional.of(targetMotion)
                        : Optional.empty();
            }

            @Override
            public OptionalDouble heightOf(Object entityReference) {
                return entityReference.equals("target")
                        ? OptionalDouble.of(1.8)
                        : OptionalDouble.empty();
            }

            @Override
            public List<?> entitiesAround(ProgramWorldPosition center, double radius) {
                return List.of();
            }

            @Override
            public Optional<ProgramBlockPosition> raycastBlock(
                    ProgramWorldPosition origin,
                    ProgramDirection direction,
                    double maximumDistance
            ) {
                return Optional.of(targetBlock);
            }

            @Override
            public Optional<Object> raycastEntity(
                    ProgramWorldPosition origin,
                    ProgramDirection direction,
                    double maximumDistance
            ) {
                return Optional.of("target");
            }
        };

        var session = run(graph, resolver);

        assertEquals("target", session.variables().get("target").value());
        assertEquals(targetPosition, session.variables().get("target_position").value());
        assertEquals(targetDirection, session.variables().get("target_direction").value());
        assertEquals(targetMovement, session.variables().get("target_movement").value());
        assertEquals(targetMotion, session.variables().get("target_motion").value());
        assertEquals(targetMotion.length(),
                (Double) session.variables().get("target_speed").value(), 1.0E-9);
        assertEquals(1.8, (Double) session.variables().get("target_height").value(), 1.0E-9);
        assertEquals(targetBlock, session.variables().get("target_block").value());
    }

    private static ProgramVm.Session run(ProgramGraph graph, Object attachment) {
        var compiled = ProgramCompiler.compile(
                graph,
                new ProgramCompileContext(CATEGORY, Set.of(), ProgramLimits.DEFAULT),
                id -> {
                    var common = CommonProgramNodeCatalog.INSTANCE.find(id);
                    return common != null ? common : PrecisionProgramNodeCatalog.INSTANCE.find(id);
                }
        );
        assertTrue(compiled.valid(), () -> compiled.diagnostics().toString());
        var session = new ProgramVm.Session(compiled.program());
        var result = session.run(0, 1_000, CommonProgramExecutors.INSTANCE, attachment);
        assertEquals(ProgramVmResult.Status.COMPLETED, result.status(), () -> result.toString());
        return session;
    }

    private static ProgramGraph.Node node(int id, Identifier type) {
        return new ProgramGraph.Node(id, type, 1, new JsonObject());
    }

    private static JsonObject configuration(String name, boolean value) {
        var configuration = new JsonObject();
        configuration.addProperty(name, value);
        return configuration;
    }

    private static ProgramGraph.Node integerNode(int id, int value) {
        var configuration = new JsonObject();
        configuration.addProperty("value", value);
        return new ProgramGraph.Node(id, CommonProgramNodeIds.INTEGER_CONSTANT, 1, configuration);
    }

    private static ProgramGraph.Node bigIntegerNode(int id, BigInteger value) {
        var configuration = new JsonObject();
        configuration.addProperty("value", value.toString());
        return new ProgramGraph.Node(id, CommonProgramNodeIds.BIG_INTEGER_CONSTANT, 1, configuration);
    }

    private static ProgramGraph.Node floatNode(int id, double value) {
        var configuration = new JsonObject();
        configuration.addProperty("value", value);
        return new ProgramGraph.Node(id, CommonProgramNodeIds.FLOAT_CONSTANT, 1, configuration);
    }

    private static ProgramGraph.Node scalarNode(int id, String type, String value) {
        var configuration = new JsonObject();
        configuration.addProperty("type", type);
        configuration.addProperty("value", value);
        return new ProgramGraph.Node(id, CommonProgramNodeIds.SCALAR_CONSTANT, 1, configuration);
    }

    private static ProgramGraph.Node numericComparisonNode(
            int id,
            String type,
            String operator
    ) {
        var configuration = new JsonObject();
        configuration.addProperty("type", type);
        configuration.addProperty("operator", operator);
        return new ProgramGraph.Node(id, CommonProgramNodeIds.NUMERIC_COMPARE, 1, configuration);
    }

    private static ProgramGraph.Node numericArithmeticNode(
            int id,
            String type,
            String operator
    ) {
        var configuration = new JsonObject();
        configuration.addProperty("type", type);
        configuration.addProperty("operator", operator);
        return new ProgramGraph.Node(id, CommonProgramNodeIds.NUMERIC_ARITHMETIC, 1, configuration);
    }

    private static ProgramGraph.Node variableNode(
            int id,
            Identifier nodeType,
            String name,
            Identifier valueType
    ) {
        var configuration = new JsonObject();
        configuration.addProperty("name", name);
        configuration.addProperty("type", valueType.toString());
        return new ProgramGraph.Node(id, nodeType, 1, configuration);
    }

    private static ProgramGraph.Node worldPositionNode(
            int id,
            Identifier dimension,
            double x,
            double y,
            double z
    ) {
        var configuration = new JsonObject();
        configuration.addProperty("dimension", dimension.toString());
        configuration.addProperty("x", x);
        configuration.addProperty("y", y);
        configuration.addProperty("z", z);
        return new ProgramGraph.Node(id, CommonProgramNodeIds.WORLD_POSITION_CONSTANT, 1, configuration);
    }

    private static ProgramGraph.Node blockPositionNode(
            int id,
            Identifier dimension,
            int x,
            int y,
            int z
    ) {
        var configuration = new JsonObject();
        configuration.addProperty("dimension", dimension.toString());
        configuration.addProperty("x", x);
        configuration.addProperty("y", y);
        configuration.addProperty("z", z);
        return new ProgramGraph.Node(id, CommonProgramNodeIds.BLOCK_POSITION_CONSTANT, 1,
                configuration);
    }

    private static ProgramGraph.Node randomNumberNode(
            int id,
            String type,
            String lower,
            String upper
    ) {
        var configuration = new JsonObject();
        configuration.addProperty("type", type);
        configuration.addProperty("lower", lower);
        configuration.addProperty("upper", upper);
        return new ProgramGraph.Node(
                id, CommonProgramNodeIds.RANDOM_NUMBER, 1, configuration);
    }

    private static ProgramGraph.Node distanceSortNode(
            int id,
            String type,
            String order
    ) {
        var configuration = new JsonObject();
        configuration.addProperty("type", type);
        configuration.addProperty("order", order);
        return new ProgramGraph.Node(
                id, CommonProgramNodeIds.SORT_POINTS_BY_DISTANCE, 1, configuration);
    }

    private static ProgramGraph.Node vec3OperationNode(
            int id,
            String type,
            String operator
    ) {
        var configuration = new JsonObject();
        configuration.addProperty("type", type);
        configuration.addProperty("operator", operator);
        return new ProgramGraph.Node(
                id, CommonProgramNodeIds.VEC3_OPERATION, 1, configuration);
    }

    private static Object randomSingle(
            ProgramGraph.Node valueNode,
            Identifier singletonType,
            Identifier randomType,
            String valueOutput,
            String randomInput,
            String randomOutput,
            Identifier valueType
    ) {
        var graph = new ProgramGraph(
                List.of(
                        node(1, PrecisionProgramNodeIds.ON_CAST),
                        valueNode,
                        node(3, singletonType),
                        node(4, randomType),
                        variableNode(5, CommonProgramNodeIds.VARIABLE_SET,
                                "selected", valueType),
                        node(6, CommonProgramNodeIds.STOP)
                ),
                List.of(
                        edge(1, "flow", 5, "flow"),
                        edge(2, valueOutput, 3, "value"),
                        edge(3, "values", 4, randomInput),
                        edge(4, randomOutput, 5, "value"),
                        edge(5, "flow", 6, "flow")
                )
        );
        return run(graph, null).variables().get("selected").value();
    }

    private static ProgramGraph.Node directionNode(int id, double x, double y, double z) {
        var configuration = new JsonObject();
        configuration.addProperty("x", x);
        configuration.addProperty("y", y);
        configuration.addProperty("z", z);
        return new ProgramGraph.Node(id, CommonProgramNodeIds.DIRECTION_CONSTANT, 1, configuration);
    }

    private static ProgramGraph.Node collectionBuilderNode(
            int id,
            Identifier type,
            int inputs
    ) {
        var configuration = new JsonObject();
        configuration.addProperty("inputs", inputs);
        return new ProgramGraph.Node(id, type, 1, configuration);
    }

    private static ProgramGraph.Node entityPositionNode(int id, String anchor) {
        var configuration = new JsonObject();
        configuration.addProperty("anchor", anchor);
        return new ProgramGraph.Node(id, CommonProgramNodeIds.ENTITY_POSITION, 1, configuration);
    }

    private static ProgramGraph.Node lookTargetNode(int id, String targetType) {
        var configuration = new JsonObject();
        configuration.addProperty("target_type", targetType);
        return new ProgramGraph.Node(id, CommonProgramNodeIds.LOOK_TARGET, 1, configuration);
    }

    private static ProgramGraph.Edge edge(int from, String output, int to, String input) {
        return new ProgramGraph.Edge(
                new ProgramGraph.Endpoint(from, output),
                new ProgramGraph.Endpoint(to, input)
        );
    }
}
