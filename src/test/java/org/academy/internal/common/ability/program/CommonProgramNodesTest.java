package org.academy.internal.common.ability.program;

import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import org.academy.api.common.ability.program.*;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
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
    void editorOrdersPriorityTargetsCollectionsAndTriggersDeterministically() {
        var entries = AbilityProgramDefinitions.mentalout().editorCatalog().entries().stream()
                .filter(ProgramEditorNodeCatalog.Entry::visible)
                .toList();
        var targets = entries.stream()
                .filter(entry -> entry.group() == ProgramEditorNodeCatalog.Group.TARGET)
                .toList();
        assertEquals(CommonProgramNodeIds.CASTER, targets.get(0).id());
        assertEquals(CommonProgramNodeIds.LOOK_TARGET, targets.get(1).id());

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
    void raycastsAndEntityProjectionCloseAllFourTargetDomains() {
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
                        node(14, CommonProgramNodeIds.STOP)
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
                        edge(13, "flow", 14, "flow")
                )
        );
        var targetPosition = new ProgramWorldPosition(OVERWORLD, 2.0, 64.0, 0.0);
        var targetDirection = new ProgramDirection(0.0, -1.0, 0.0);
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
