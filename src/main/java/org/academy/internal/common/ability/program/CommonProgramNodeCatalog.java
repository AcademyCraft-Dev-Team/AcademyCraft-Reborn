package org.academy.internal.common.ability.program;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import org.academy.api.common.ability.program.ProgramNodePurity;
import org.academy.api.common.ability.program.ProgramEntityPositionAnchor;
import org.academy.api.common.ability.program.ProgramNodeRole;
import org.academy.api.common.ability.program.ProgramNodeSchema;
import org.academy.api.common.ability.program.ProgramNodeScope;
import org.academy.api.common.ability.program.ProgramNodeType;
import org.academy.api.common.ability.program.ProgramPortDefinition;
import org.academy.api.common.ability.program.ProgramValueType;
import org.academy.api.common.ability.program.ProgramValueTypes;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Strongly typed common algebra shared by every ability category.
 *
 * <p>Control-flow may cycle, while data edges stay acyclic. Session variables bridge successive
 * control steps, giving the graph a small imperative core without allowing accidental recursive
 * data evaluation.</p>
 */
public final class CommonProgramNodeCatalog implements ProgramNodeLookup {
    private static final Codec<Identifier> IDENTIFIER_CODEC = Codec.STRING.xmap(
            Identifier::parse,
            Identifier::toString
    );
    private static final Map<Identifier, ProgramValueType> VARIABLE_TYPES = variableTypes();
    public static final CommonProgramNodeCatalog INSTANCE = new CommonProgramNodeCatalog();

    private final Map<Identifier, ProgramNodeType<?>> types;

    private CommonProgramNodeCatalog() {
        var result = new HashMap<Identifier, ProgramNodeType<?>>();
        registerConstants(result);
        registerScalarLogic(result);
        registerControlAndState(result);
        registerSpatial(result);
        registerQueries(result);
        registerFilters(result);
        registerEquality(result);
        for (var domain : CollectionDomain.values()) registerCollection(result, domain);
        registerRandomCollectionValues(result);
        registerAdvancedValues(result);
        types = Map.copyOf(result);
    }

    @Override
    public ProgramNodeType<?> find(Identifier id) {
        return types.get(id);
    }

    public Map<Identifier, ProgramNodeType<?>> types() {
        return types;
    }

    public static ProgramValueType variableType(Identifier id) {
        var type = VARIABLE_TYPES.get(id);
        if (type == null) throw new IllegalArgumentException("Unsupported program variable type " + id);
        return type;
    }

    private static void registerConstants(Map<Identifier, ProgramNodeType<?>> result) {
        put(result, CommonProgramNodeIds.SCALAR_CONSTANT, type(
                ScalarConfiguration.CODEC,
                configuration -> outputSchema("value", configuration.kind().type()),
                ProgramNodeRole.VALUE,
                ProgramNodePurity.PURE
        ));
        put(result, CommonProgramNodeIds.BOOLEAN_CONSTANT, type(
                BooleanConfiguration.CODEC,
                _ -> outputSchema("value", ProgramValueTypes.BOOLEAN),
                ProgramNodeRole.VALUE,
                ProgramNodePurity.PURE
        ));
        put(result, CommonProgramNodeIds.INTEGER_CONSTANT, type(
                IntegerConfiguration.CODEC,
                _ -> outputSchema("value", ProgramValueTypes.INTEGER),
                ProgramNodeRole.VALUE,
                ProgramNodePurity.PURE
        ));
        put(result, CommonProgramNodeIds.BIG_INTEGER_CONSTANT, type(
                BigIntegerConfiguration.CODEC,
                _ -> outputSchema("value", ProgramValueTypes.BIG_INTEGER),
                ProgramNodeRole.VALUE,
                ProgramNodePurity.PURE
        ));
        put(result, CommonProgramNodeIds.FLOAT_CONSTANT, type(
                FloatConfiguration.CODEC,
                _ -> outputSchema("value", ProgramValueTypes.FLOAT),
                ProgramNodeRole.VALUE,
                ProgramNodePurity.PURE
        ));
    }

    private static void registerScalarLogic(Map<Identifier, ProgramNodeType<?>> result) {
        put(result, CommonProgramNodeIds.NUMERIC_ARITHMETIC, type(
                NumericArithmeticConfiguration.CODEC,
                configuration -> configuration.operator() == ArithmeticOperator.ABSOLUTE
                        ? unarySchema("value", configuration.kind().type(),
                        "result", configuration.kind().type())
                        : binarySchema(configuration.kind().type(), configuration.kind().type()),
                ProgramNodeRole.VALUE,
                ProgramNodePurity.PURE
        ));
        put(result, CommonProgramNodeIds.NUMERIC_COMPARE, type(
                NumericComparisonConfiguration.CODEC,
                configuration -> binarySchema(
                        configuration.kind().type(),
                        ProgramValueTypes.BOOLEAN
                ),
                ProgramNodeRole.VALUE,
                ProgramNodePurity.PURE
        ));
        for (var id : List.of(
                CommonProgramNodeIds.INTEGER_ADD,
                CommonProgramNodeIds.INTEGER_SUBTRACT,
                CommonProgramNodeIds.INTEGER_MULTIPLY,
                CommonProgramNodeIds.INTEGER_DIVIDE,
                CommonProgramNodeIds.INTEGER_MODULO
        )) put(result, id, unitType(binarySchema(ProgramValueTypes.INTEGER, ProgramValueTypes.INTEGER)));
        for (var id : List.of(
                CommonProgramNodeIds.INTEGER_EQUAL,
                CommonProgramNodeIds.INTEGER_LESS,
                CommonProgramNodeIds.INTEGER_LESS_EQUAL,
                CommonProgramNodeIds.INTEGER_GREATER,
                CommonProgramNodeIds.INTEGER_GREATER_EQUAL
        )) put(result, id, unitType(binarySchema(ProgramValueTypes.INTEGER, ProgramValueTypes.BOOLEAN)));

        for (var id : List.of(
                CommonProgramNodeIds.BIG_INTEGER_ADD,
                CommonProgramNodeIds.BIG_INTEGER_SUBTRACT,
                CommonProgramNodeIds.BIG_INTEGER_MULTIPLY,
                CommonProgramNodeIds.BIG_INTEGER_DIVIDE,
                CommonProgramNodeIds.BIG_INTEGER_MODULO
        )) put(result, id, unitType(binarySchema(ProgramValueTypes.BIG_INTEGER, ProgramValueTypes.BIG_INTEGER)));
        for (var id : List.of(
                CommonProgramNodeIds.BIG_INTEGER_EQUAL,
                CommonProgramNodeIds.BIG_INTEGER_LESS,
                CommonProgramNodeIds.BIG_INTEGER_LESS_EQUAL,
                CommonProgramNodeIds.BIG_INTEGER_GREATER,
                CommonProgramNodeIds.BIG_INTEGER_GREATER_EQUAL
        )) put(result, id, unitType(binarySchema(ProgramValueTypes.BIG_INTEGER, ProgramValueTypes.BOOLEAN)));

        for (var id : List.of(
                CommonProgramNodeIds.FLOAT_ADD,
                CommonProgramNodeIds.FLOAT_SUBTRACT,
                CommonProgramNodeIds.FLOAT_MULTIPLY,
                CommonProgramNodeIds.FLOAT_DIVIDE,
                CommonProgramNodeIds.FLOAT_MODULO
        )) put(result, id, unitType(binarySchema(ProgramValueTypes.FLOAT, ProgramValueTypes.FLOAT)));
        for (var id : List.of(
                CommonProgramNodeIds.FLOAT_EQUAL,
                CommonProgramNodeIds.FLOAT_LESS,
                CommonProgramNodeIds.FLOAT_LESS_EQUAL,
                CommonProgramNodeIds.FLOAT_GREATER,
                CommonProgramNodeIds.FLOAT_GREATER_EQUAL
        )) put(result, id, unitType(binarySchema(ProgramValueTypes.FLOAT, ProgramValueTypes.BOOLEAN)));

        put(result, CommonProgramNodeIds.BOOLEAN_NOT, unitType(new ProgramNodeSchema(
                List.of(ProgramPortDefinition.requiredInput("value", ProgramValueTypes.BOOLEAN)),
                List.of(ProgramPortDefinition.output("result", ProgramValueTypes.BOOLEAN))
        )));
        for (var id : List.of(
                CommonProgramNodeIds.BOOLEAN_AND,
                CommonProgramNodeIds.BOOLEAN_OR,
                CommonProgramNodeIds.BOOLEAN_XOR
        )) put(result, id, unitType(binarySchema(ProgramValueTypes.BOOLEAN, ProgramValueTypes.BOOLEAN)));
    }

    private static void registerControlAndState(Map<Identifier, ProgramNodeType<?>> result) {
        put(result, CommonProgramNodeIds.TRIGGER_HURT, type(
                unitCodec(),
                _ -> entrySchema(),
                ProgramNodeRole.ENTRY,
                ProgramNodePurity.PURE
        ));
        put(result, CommonProgramNodeIds.TRIGGER_LOOP, type(
                LoopTriggerConfiguration.CODEC,
                _ -> entrySchema(),
                ProgramNodeRole.ENTRY,
                ProgramNodePurity.PURE
        ));
        put(result, CommonProgramNodeIds.TRIGGER_MELEE, type(
                unitCodec(),
                _ -> entrySchema(),
                ProgramNodeRole.ENTRY,
                ProgramNodePurity.PURE
        ));
        put(result, CommonProgramNodeIds.TRIGGER_MOVEMENT, type(
                MovementTriggerConfiguration.CODEC,
                _ -> entrySchema(),
                ProgramNodeRole.ENTRY,
                ProgramNodePurity.PURE
        ));
        put(result, CommonProgramNodeIds.TRIGGER_HEALTH_THRESHOLD, type(
                HealthThresholdTriggerConfiguration.CODEC,
                _ -> entrySchema(),
                ProgramNodeRole.ENTRY,
                ProgramNodePurity.STATE
        ));
        put(result, CommonProgramNodeIds.BRANCH, type(
                unitCodec(),
                _ -> new ProgramNodeSchema(
                        List.of(
                                flowInput(),
                                ProgramPortDefinition.requiredInput("condition", ProgramValueTypes.BOOLEAN)
                        ),
                        List.of(flowOutput("true"), flowOutput("false"))
                ),
                ProgramNodeRole.CONTROL,
                ProgramNodePurity.PURE
        ));
        put(result, CommonProgramNodeIds.STOP, type(
                unitCodec(),
                _ -> new ProgramNodeSchema(List.of(flowInput()), List.of()),
                ProgramNodeRole.CONTROL,
                ProgramNodePurity.PURE
        ));
        put(result, CommonProgramNodeIds.VARIABLE_GET, type(
                VariableConfiguration.CODEC,
                configuration -> outputSchema("value", configuration.type()),
                ProgramNodeRole.VALUE,
                ProgramNodePurity.STATE
        ));
        put(result, CommonProgramNodeIds.VARIABLE_SET, type(
                VariableConfiguration.CODEC,
                configuration -> new ProgramNodeSchema(
                        List.of(
                                flowInput(),
                                ProgramPortDefinition.requiredInput("value", configuration.type())
                        ),
                        List.of(flowOutput("flow"))
                ),
                ProgramNodeRole.CONTROL,
                ProgramNodePurity.STATE
        ));
    }

    private static void registerSpatial(Map<Identifier, ProgramNodeType<?>> result) {
        put(result, CommonProgramNodeIds.WORLD_POSITION_CONSTANT, type(
                WorldPositionConfiguration.CODEC,
                _ -> outputSchema("position", ProgramValueTypes.WORLD_POSITION),
                ProgramNodeRole.VALUE,
                ProgramNodePurity.PURE
        ));
        put(result, CommonProgramNodeIds.WORLD_POSITION_CONSTRUCT, type(
                DimensionConfiguration.CODEC,
                _ -> constructSchema(ProgramValueTypes.FLOAT, ProgramValueTypes.WORLD_POSITION),
                ProgramNodeRole.VALUE,
                ProgramNodePurity.PURE
        ));
        put(result, CommonProgramNodeIds.WORLD_POSITION_COMPONENTS, unitType(componentSchema(
                ProgramValueTypes.WORLD_POSITION,
                ProgramValueTypes.FLOAT
        )));
        put(result, CommonProgramNodeIds.WORLD_POSITION_OFFSET, unitType(new ProgramNodeSchema(
                List.of(
                        ProgramPortDefinition.requiredInput("position", ProgramValueTypes.WORLD_POSITION),
                        ProgramPortDefinition.requiredInput("direction", ProgramValueTypes.DIRECTION),
                        ProgramPortDefinition.requiredInput("distance", ProgramValueTypes.FLOAT)
                ),
                List.of(ProgramPortDefinition.output("position", ProgramValueTypes.WORLD_POSITION))
        )));
        put(result, CommonProgramNodeIds.WORLD_POSITION_DISTANCE, unitType(binarySchema(
                ProgramValueTypes.WORLD_POSITION,
                ProgramValueTypes.FLOAT
        )));
        put(result, CommonProgramNodeIds.WORLD_POSITION_SAME_DIMENSION, unitType(binarySchema(
                ProgramValueTypes.WORLD_POSITION,
                ProgramValueTypes.BOOLEAN
        )));

        put(result, CommonProgramNodeIds.BLOCK_POSITION_CONSTANT, type(
                BlockPositionConfiguration.CODEC,
                _ -> outputSchema("position", ProgramValueTypes.BLOCK_POSITION),
                ProgramNodeRole.VALUE,
                ProgramNodePurity.PURE
        ));
        put(result, CommonProgramNodeIds.BLOCK_POSITION_CONSTRUCT, type(
                DimensionConfiguration.CODEC,
                _ -> constructSchema(ProgramValueTypes.INTEGER, ProgramValueTypes.BLOCK_POSITION),
                ProgramNodeRole.VALUE,
                ProgramNodePurity.PURE
        ));
        put(result, CommonProgramNodeIds.BLOCK_POSITION_COMPONENTS, unitType(componentSchema(
                ProgramValueTypes.BLOCK_POSITION,
                ProgramValueTypes.INTEGER
        )));
        put(result, CommonProgramNodeIds.POSITION_TO_BLOCK, unitType(unarySchema(
                "position",
                ProgramValueTypes.WORLD_POSITION,
                "block",
                ProgramValueTypes.BLOCK_POSITION
        )));
        put(result, CommonProgramNodeIds.BLOCK_TO_CENTER, unitType(unarySchema(
                "block",
                ProgramValueTypes.BLOCK_POSITION,
                "position",
                ProgramValueTypes.WORLD_POSITION
        )));

        put(result, CommonProgramNodeIds.DIRECTION_CONSTANT, type(
                DirectionConfiguration.CODEC,
                _ -> outputSchema("direction", ProgramValueTypes.DIRECTION),
                ProgramNodeRole.VALUE,
                ProgramNodePurity.PURE
        ));
        put(result, CommonProgramNodeIds.DIRECTION_CONSTRUCT, unitType(constructSchema(
                ProgramValueTypes.FLOAT,
                ProgramValueTypes.DIRECTION
        )));
        put(result, CommonProgramNodeIds.DIRECTION_COMPONENTS, unitType(componentSchema(
                ProgramValueTypes.DIRECTION,
                ProgramValueTypes.FLOAT
        )));
        put(result, CommonProgramNodeIds.DIRECTION_BETWEEN, unitType(new ProgramNodeSchema(
                List.of(
                        ProgramPortDefinition.requiredInput("from", ProgramValueTypes.WORLD_POSITION),
                        ProgramPortDefinition.requiredInput("to", ProgramValueTypes.WORLD_POSITION)
                ),
                List.of(ProgramPortDefinition.output("direction", ProgramValueTypes.DIRECTION))
        )));
        put(result, CommonProgramNodeIds.DIRECTION_OPPOSITE, unitType(unarySchema(
                "direction",
                ProgramValueTypes.DIRECTION,
                "direction",
                ProgramValueTypes.DIRECTION
        )));
        put(result, CommonProgramNodeIds.DIRECTION_DOT, unitType(binarySchema(
                ProgramValueTypes.DIRECTION,
                ProgramValueTypes.FLOAT
        )));
        put(result, CommonProgramNodeIds.VEC3_OPERATION, type(
                Vec3OperationConfiguration.CODEC,
                configuration -> binarySchema(
                        configuration.kind().type(),
                        configuration.operator() == Vec3Operator.DOT
                                ? ProgramValueTypes.FLOAT : configuration.kind().type()
                ),
                ProgramNodeRole.VALUE,
                ProgramNodePurity.PURE
        ));
    }

    private static void registerQueries(Map<Identifier, ProgramNodeType<?>> result) {
        put(result, CommonProgramNodeIds.CASTER, queryType(outputSchema(
                "entity", ProgramValueTypes.ENTITY_REFERENCE
        )));
        put(result, CommonProgramNodeIds.DAMAGE_ATTACKER, queryType(outputSchema(
                "entity", ProgramValueTypes.ENTITY_REFERENCE
        )));
        put(result, CommonProgramNodeIds.LOOK_TARGET, type(
                LookTargetConfiguration.CODEC,
                configuration -> outputSchema(
                        configuration.targetType().port(),
                        configuration.targetType().valueType()
                ),
                ProgramNodeRole.QUERY,
                ProgramNodePurity.WORLD_QUERY
        ));
        put(result, CommonProgramNodeIds.ENTITY_POSITION, type(
                EntityPositionConfiguration.CODEC,
                _ -> unarySchema(
                        "entity",
                        ProgramValueTypes.ENTITY_REFERENCE,
                        "position",
                        ProgramValueTypes.WORLD_POSITION
                ),
                ProgramNodeRole.QUERY,
                ProgramNodePurity.WORLD_QUERY
        ));
        put(result, CommonProgramNodeIds.ENTITY_LOOK_DIRECTION, queryType(unarySchema(
                "entity",
                ProgramValueTypes.ENTITY_REFERENCE,
                "direction",
                ProgramValueTypes.DIRECTION
        )));
        put(result, CommonProgramNodeIds.ENTITIES_AROUND, queryType(new ProgramNodeSchema(
                List.of(
                        ProgramPortDefinition.requiredInput("center", ProgramValueTypes.WORLD_POSITION),
                        ProgramPortDefinition.requiredInput("radius", ProgramValueTypes.FLOAT)
                ),
                List.of(ProgramPortDefinition.output("entities", ProgramValueTypes.ENTITY_SET))
        )));
        put(result, CommonProgramNodeIds.RAYCAST_BLOCK, queryType(new ProgramNodeSchema(
                List.of(
                        ProgramPortDefinition.requiredInput("origin", ProgramValueTypes.WORLD_POSITION),
                        ProgramPortDefinition.requiredInput("direction", ProgramValueTypes.DIRECTION),
                        ProgramPortDefinition.requiredInput("range", ProgramValueTypes.FLOAT)
                ),
                List.of(ProgramPortDefinition.output("block", ProgramValueTypes.BLOCK_POSITION))
        )));
        put(result, CommonProgramNodeIds.RAYCAST_ENTITY, queryType(new ProgramNodeSchema(
                List.of(
                        ProgramPortDefinition.requiredInput("origin", ProgramValueTypes.WORLD_POSITION),
                        ProgramPortDefinition.requiredInput("direction", ProgramValueTypes.DIRECTION),
                        ProgramPortDefinition.requiredInput("range", ProgramValueTypes.FLOAT)
                ),
                List.of(ProgramPortDefinition.output("entity", ProgramValueTypes.ENTITY_REFERENCE))
        )));
        put(result, CommonProgramNodeIds.BLOCK_NORMAL, type(
                BlockNormalConfiguration.CODEC,
                configuration -> new ProgramNodeSchema(
                        switch (configuration.mode()) {
                            case VIEW -> List.of(ProgramPortDefinition.requiredInput(
                                    "entity", ProgramValueTypes.ENTITY_REFERENCE));
                            case POSITION_DIRECTION -> List.of(
                                    ProgramPortDefinition.requiredInput(
                                            "origin", ProgramValueTypes.WORLD_POSITION),
                                    ProgramPortDefinition.requiredInput(
                                            "direction", ProgramValueTypes.DIRECTION)
                            );
                        },
                        List.of(ProgramPortDefinition.output(
                                "normal", ProgramValueTypes.DIRECTION))
                ),
                ProgramNodeRole.QUERY,
                ProgramNodePurity.WORLD_QUERY
        ));
        put(result, CommonProgramNodeIds.BLOCK_VOLUME, queryType(new ProgramNodeSchema(
                List.of(
                        ProgramPortDefinition.requiredInput("first", ProgramValueTypes.BLOCK_POSITION),
                        ProgramPortDefinition.requiredInput("second", ProgramValueTypes.BLOCK_POSITION)
                ),
                List.of(ProgramPortDefinition.output(
                        "blocks", ProgramValueTypes.BLOCK_POSITION_SET))
        )));
    }

    private static void registerEquality(Map<Identifier, ProgramNodeType<?>> result) {
        put(result, CommonProgramNodeIds.ENTITY_EQUAL, unitType(binarySchema(
                ProgramValueTypes.ENTITY_REFERENCE,
                ProgramValueTypes.BOOLEAN
        )));
        put(result, CommonProgramNodeIds.WORLD_POSITION_EQUAL, unitType(binarySchema(
                ProgramValueTypes.WORLD_POSITION,
                ProgramValueTypes.BOOLEAN
        )));
        put(result, CommonProgramNodeIds.BLOCK_POSITION_EQUAL, unitType(binarySchema(
                ProgramValueTypes.BLOCK_POSITION,
                ProgramValueTypes.BOOLEAN
        )));
        put(result, CommonProgramNodeIds.DIRECTION_EQUAL, unitType(binarySchema(
                ProgramValueTypes.DIRECTION,
                ProgramValueTypes.BOOLEAN
        )));
    }

    private static void registerFilters(Map<Identifier, ProgramNodeType<?>> result) {
        var entitySetFilter = unarySchema(
                "entities",
                ProgramValueTypes.ENTITY_SET,
                "entities",
                ProgramValueTypes.ENTITY_SET
        );
        put(result, CommonProgramNodeIds.FILTER_ENTITY_ALIVE, queryType(entitySetFilter));
        put(result, CommonProgramNodeIds.FILTER_ENTITY_HAS_TARGET, queryType(entitySetFilter));
        put(result, CommonProgramNodeIds.FILTER_ENTITY_DISTANCE, queryType(new ProgramNodeSchema(
                List.of(
                        ProgramPortDefinition.requiredInput("entities", ProgramValueTypes.ENTITY_SET),
                        ProgramPortDefinition.requiredInput("center", ProgramValueTypes.WORLD_POSITION),
                        ProgramPortDefinition.requiredInput("radius", ProgramValueTypes.FLOAT)
                ),
                List.of(ProgramPortDefinition.output("entities", ProgramValueTypes.ENTITY_SET))
        )));
        for (var id : List.of(
                CommonProgramNodeIds.FILTER_ENTITY_ALLIED_TO,
                CommonProgramNodeIds.FILTER_ENTITY_HOSTILE_TO,
                CommonProgramNodeIds.FILTER_ENTITY_TARGETED_BY,
                CommonProgramNodeIds.FILTER_ENTITY_LAST_DAMAGED_BY,
                CommonProgramNodeIds.FILTER_ENTITY_VISIBLE_FROM
        )) put(result, id, queryType(new ProgramNodeSchema(
                List.of(
                        ProgramPortDefinition.requiredInput("entities", ProgramValueTypes.ENTITY_SET),
                        ProgramPortDefinition.requiredInput(
                                relationPort(id), ProgramValueTypes.ENTITY_REFERENCE)
                ),
                List.of(ProgramPortDefinition.output("entities", ProgramValueTypes.ENTITY_SET))
        )));
        for (var id : List.of(
                CommonProgramNodeIds.FILTER_ENTITY_HEALTH_AT_LEAST,
                CommonProgramNodeIds.FILTER_ENTITY_HEALTH_AT_MOST,
                CommonProgramNodeIds.FILTER_ENTITY_MAX_HEALTH_AT_LEAST,
                CommonProgramNodeIds.FILTER_ENTITY_MAX_HEALTH_AT_MOST
        )) put(result, id, queryType(new ProgramNodeSchema(
                List.of(
                        ProgramPortDefinition.requiredInput("entities", ProgramValueTypes.ENTITY_SET),
                        ProgramPortDefinition.requiredInput(
                                id.equals(CommonProgramNodeIds.FILTER_ENTITY_HEALTH_AT_LEAST)
                                        || id.equals(CommonProgramNodeIds.FILTER_ENTITY_HEALTH_AT_MOST)
                                        ? "percent" : "health",
                                ProgramValueTypes.FLOAT)
                ),
                List.of(ProgramPortDefinition.output("entities", ProgramValueTypes.ENTITY_SET))
        )));
        put(result, CommonProgramNodeIds.FILTER_ENTITY_TYPE, type(
                EntityKindConfiguration.CODEC,
                _ -> entitySetFilter,
                ProgramNodeRole.QUERY,
                ProgramNodePurity.WORLD_QUERY
        ));
        put(result, CommonProgramNodeIds.FILTER_ENTITY_EXACT, type(
                ExactFilterConfiguration.CODEC,
                _ -> entitySetFilter,
                ProgramNodeRole.QUERY,
                ProgramNodePurity.WORLD_QUERY
        ));
        put(result, CommonProgramNodeIds.FILTER_BLOCK_EXACT, type(
                ExactFilterConfiguration.CODEC,
                _ -> unarySchema("blocks", ProgramValueTypes.BLOCK_POSITION_SET,
                        "blocks", ProgramValueTypes.BLOCK_POSITION_SET),
                ProgramNodeRole.QUERY,
                ProgramNodePurity.WORLD_QUERY
        ));
    }

    private static void registerAdvancedValues(Map<Identifier, ProgramNodeType<?>> result) {
        put(result, CommonProgramNodeIds.RANDOM_NUMBER, type(
                RandomNumberConfiguration.CODEC,
                configuration -> outputSchema("value", configuration.kind().type()),
                ProgramNodeRole.VALUE,
                ProgramNodePurity.STATE
        ));
        put(result, CommonProgramNodeIds.SORT_POINTS_BY_DISTANCE, type(
                DistanceSortConfiguration.CODEC,
                configuration -> new ProgramNodeSchema(
                        List.of(
                                ProgramPortDefinition.requiredInput(
                                        "values", configuration.kind().collectionType()),
                                ProgramPortDefinition.requiredInput(
                                        "origin", ProgramValueTypes.WORLD_POSITION)
                        ),
                        List.of(ProgramPortDefinition.output(
                                "values", configuration.kind().collectionType()))
                ),
                ProgramNodeRole.QUERY,
                ProgramNodePurity.WORLD_QUERY
        ));
    }

    private static void registerRandomCollectionValues(
            Map<Identifier, ProgramNodeType<?>> result
    ) {
        put(result, CommonProgramNodeIds.RANDOM_ENTITY, randomCollectionType(
                "entities", ProgramValueTypes.ENTITY_SET,
                "entity", ProgramValueTypes.ENTITY_REFERENCE));
        put(result, CommonProgramNodeIds.NEAREST_ENTITY_TO_POSITION, queryType(
                new ProgramNodeSchema(
                        List.of(
                                ProgramPortDefinition.requiredInput(
                                        "position", ProgramValueTypes.WORLD_POSITION),
                                ProgramPortDefinition.requiredInput(
                                        "entities", ProgramValueTypes.ENTITY_SET)
                        ),
                        List.of(ProgramPortDefinition.output(
                                "entity", ProgramValueTypes.ENTITY_REFERENCE))
                )
        ));
        put(result, CommonProgramNodeIds.RANDOM_WORLD_POSITION, randomCollectionType(
                "positions", ProgramValueTypes.WORLD_POSITION_SET,
                "position", ProgramValueTypes.WORLD_POSITION));
        put(result, CommonProgramNodeIds.RANDOM_BLOCK_POSITION, randomCollectionType(
                "blocks", ProgramValueTypes.BLOCK_POSITION_SET,
                "block", ProgramValueTypes.BLOCK_POSITION));
        put(result, CommonProgramNodeIds.RANDOM_DIRECTION, randomCollectionType(
                "directions", ProgramValueTypes.DIRECTION_SET,
                "direction", ProgramValueTypes.DIRECTION));
    }

    private static ProgramNodeType<?> randomCollectionType(
            String input,
            ProgramValueType collectionType,
            String output,
            ProgramValueType elementType
    ) {
        return queryType(new ProgramNodeSchema(
                List.of(ProgramPortDefinition.requiredInput(input, collectionType)),
                List.of(ProgramPortDefinition.output(output, elementType))
        ));
    }

    private static String relationPort(Identifier id) {
        if (id.equals(CommonProgramNodeIds.FILTER_ENTITY_TARGETED_BY)) return "target";
        if (id.equals(CommonProgramNodeIds.FILTER_ENTITY_LAST_DAMAGED_BY)) return "attacker";
        if (id.equals(CommonProgramNodeIds.FILTER_ENTITY_VISIBLE_FROM)) return "observer";
        return "reference";
    }

    private static void registerCollection(
            Map<Identifier, ProgramNodeType<?>> result,
            CollectionDomain domain
    ) {
        var empty = outputSchema("values", domain.collectionType);
        var singleton = unarySchema("value", domain.elementType, "values", domain.collectionType);
        var binary = new ProgramNodeSchema(
                List.of(
                        ProgramPortDefinition.requiredInput("left", domain.collectionType),
                        ProgramPortDefinition.requiredInput("right", domain.collectionType)
                ),
                List.of(ProgramPortDefinition.output("values", domain.collectionType))
        );
        var contains = new ProgramNodeSchema(
                List.of(
                        ProgramPortDefinition.requiredInput("values", domain.collectionType),
                        ProgramPortDefinition.requiredInput("value", domain.elementType)
                ),
                List.of(ProgramPortDefinition.output("result", ProgramValueTypes.BOOLEAN))
        );
        var size = unarySchema("values", domain.collectionType, "size", ProgramValueTypes.INTEGER);
        var get = new ProgramNodeSchema(
                List.of(
                        ProgramPortDefinition.requiredInput("values", domain.collectionType),
                        ProgramPortDefinition.requiredInput("index", ProgramValueTypes.INTEGER)
                ),
                List.of(ProgramPortDefinition.output("value", domain.elementType))
        );
        var foreach = new ProgramNodeSchema(
                List.of(
                        flowInput(),
                        ProgramPortDefinition.requiredInput("values", domain.collectionType)
                ),
                List.of(
                        flowOutput("body"),
                        flowOutput("done"),
                        ProgramPortDefinition.output("value", domain.elementType)
                )
        );
        put(result, domain.id("empty"), type(
                CollectionBuilderConfiguration.CODEC,
                configuration -> {
                    var inputs = java.util.stream.IntStream
                            .rangeClosed(1, configuration.inputs())
                            .mapToObj(index -> ProgramPortDefinition.requiredInput(
                                    "value_" + index, domain.elementType))
                            .toList();
                    return new ProgramNodeSchema(inputs, empty.outputs());
                },
                ProgramNodeRole.VALUE,
                ProgramNodePurity.PURE
        ));
        put(result, domain.id("singleton"), unitType(singleton));
        put(result, domain.id("union"), unitType(binary));
        put(result, domain.id("intersection"), unitType(binary));
        put(result, domain.id("difference"), unitType(binary));
        put(result, domain.id("contains"), unitType(contains));
        put(result, domain.id("size"), unitType(size));
        put(result, domain.id("get"), unitType(get));
        put(result, domain.id("foreach"), type(
                unitCodec(),
                _ -> foreach,
                ProgramNodeRole.CONTROL,
                ProgramNodePurity.STATE
        ));
    }

    private static ProgramNodeSchema binarySchema(
            ProgramValueType input,
            ProgramValueType output
    ) {
        return new ProgramNodeSchema(
                List.of(
                        ProgramPortDefinition.requiredInput("left", input),
                        ProgramPortDefinition.requiredInput("right", input)
                ),
                List.of(ProgramPortDefinition.output("result", output))
        );
    }

    private static ProgramNodeSchema entrySchema() {
        return new ProgramNodeSchema(
                List.of(),
                List.of(ProgramPortDefinition.output("flow", ProgramValueTypes.FLOW))
        );
    }

    private static ProgramNodeSchema unarySchema(
            String inputName,
            ProgramValueType input,
            String outputName,
            ProgramValueType output
    ) {
        return new ProgramNodeSchema(
                List.of(ProgramPortDefinition.requiredInput(inputName, input)),
                List.of(ProgramPortDefinition.output(outputName, output))
        );
    }

    private static ProgramNodeSchema constructSchema(
            ProgramValueType componentType,
            ProgramValueType outputType
    ) {
        return new ProgramNodeSchema(
                List.of(
                        ProgramPortDefinition.requiredInput("x", componentType),
                        ProgramPortDefinition.requiredInput("y", componentType),
                        ProgramPortDefinition.requiredInput("z", componentType)
                ),
                List.of(ProgramPortDefinition.output(
                        outputType.equals(ProgramValueTypes.DIRECTION) ? "direction" : "position",
                        outputType
                ))
        );
    }

    private static ProgramNodeSchema componentSchema(
            ProgramValueType inputType,
            ProgramValueType componentType
    ) {
        return new ProgramNodeSchema(
                List.of(ProgramPortDefinition.requiredInput(
                        inputType.equals(ProgramValueTypes.DIRECTION) ? "direction" : "position",
                        inputType
                )),
                List.of(
                        ProgramPortDefinition.output("x", componentType),
                        ProgramPortDefinition.output("y", componentType),
                        ProgramPortDefinition.output("z", componentType)
                )
        );
    }

    private static ProgramNodeSchema outputSchema(String name, ProgramValueType output) {
        return new ProgramNodeSchema(List.of(), List.of(ProgramPortDefinition.output(name, output)));
    }

    private static ProgramPortDefinition flowInput() {
        return new ProgramPortDefinition(
                "flow",
                ProgramValueTypes.FLOW,
                true,
                ProgramPortDefinition.UNBOUNDED_CONNECTIONS
        );
    }

    private static ProgramPortDefinition flowOutput(String name) {
        return new ProgramPortDefinition(name, ProgramValueTypes.FLOW, false, 1);
    }

    private static ProgramNodeType<Unit> unitType(ProgramNodeSchema schema) {
        return type(unitCodec(), _ -> schema, ProgramNodeRole.VALUE, ProgramNodePurity.PURE);
    }

    private static ProgramNodeType<Unit> queryType(ProgramNodeSchema schema) {
        return type(unitCodec(), _ -> schema, ProgramNodeRole.QUERY, ProgramNodePurity.WORLD_QUERY);
    }

    private static Codec<Unit> unitCodec() {
        return MapCodec.unit(Unit.INSTANCE).codec();
    }

    private static <C> ProgramNodeType<C> type(
            Codec<C> codec,
            Function<C, ProgramNodeSchema> schema,
            ProgramNodeRole role,
            ProgramNodePurity purity
    ) {
        return new FixedNodeType<>(codec, schema, role, purity);
    }

    private static void put(
            Map<Identifier, ProgramNodeType<?>> result,
            Identifier id,
            ProgramNodeType<?> type
    ) {
        if (result.putIfAbsent(id, type) != null) {
            throw new IllegalStateException("Duplicate common program node " + id);
        }
    }

    private static Map<Identifier, ProgramValueType> variableTypes() {
        var result = new HashMap<Identifier, ProgramValueType>();
        for (var type : List.of(
                ProgramValueTypes.BOOLEAN,
                ProgramValueTypes.INTEGER,
                ProgramValueTypes.BIG_INTEGER,
                ProgramValueTypes.FLOAT,
                ProgramValueTypes.IDENTIFIER,
                ProgramValueTypes.DURATION,
                ProgramValueTypes.DIRECTION,
                ProgramValueTypes.WORLD_POSITION,
                ProgramValueTypes.BLOCK_POSITION,
                ProgramValueTypes.ENTITY_REFERENCE,
                ProgramValueTypes.LIVING_ENTITY_REFERENCE,
                ProgramValueTypes.DIRECTION_SET,
                ProgramValueTypes.WORLD_POSITION_SET,
                ProgramValueTypes.BLOCK_POSITION_SET,
                ProgramValueTypes.ENTITY_SET,
                ProgramValueTypes.LIVING_ENTITY_SET
        )) result.put(type.id(), type);
        return Map.copyOf(result);
    }

    public enum CollectionDomain {
        ENTITY("entity", ProgramValueTypes.ENTITY_REFERENCE, ProgramValueTypes.ENTITY_SET),
        WORLD_POSITION(
                "world_position",
                ProgramValueTypes.WORLD_POSITION,
                ProgramValueTypes.WORLD_POSITION_SET
        ),
        BLOCK_POSITION(
                "block_position",
                ProgramValueTypes.BLOCK_POSITION,
                ProgramValueTypes.BLOCK_POSITION_SET
        ),
        DIRECTION("direction", ProgramValueTypes.DIRECTION, ProgramValueTypes.DIRECTION_SET);

        private final String path;
        private final ProgramValueType elementType;
        private final ProgramValueType collectionType;

        CollectionDomain(
                String path,
                ProgramValueType elementType,
                ProgramValueType collectionType
        ) {
            this.path = path;
            this.elementType = elementType;
            this.collectionType = collectionType;
        }

        public Identifier id(String operation) {
            return CommonProgramNodeIds.collection(path, operation);
        }

        public ProgramValueType elementType() {
            return elementType;
        }

        public ProgramValueType collectionType() {
            return collectionType;
        }
    }

    public record BooleanConfiguration(boolean value) {
        public static final Codec<BooleanConfiguration> CODEC = Codec.BOOL.fieldOf("value")
                .xmap(BooleanConfiguration::new, BooleanConfiguration::value).codec();
    }

    public record ScalarConfiguration(ScalarKind kind, String value) {
        public static final Codec<ScalarConfiguration> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        ScalarKind.CODEC.fieldOf("type").forGetter(ScalarConfiguration::kind),
                        Codec.STRING.fieldOf("value").forGetter(ScalarConfiguration::value)
                ).apply(instance, ScalarConfiguration::new));

        public ScalarConfiguration {
            if (kind == null || value == null) {
                throw new IllegalArgumentException("Scalar type and value are required");
            }
            kind.parse(value);
        }

        public Object parsedValue() {
            return kind.parse(value);
        }
    }

    public record NumericComparisonConfiguration(NumericKind kind, ComparisonOperator operator) {
        public static final Codec<NumericComparisonConfiguration> CODEC =
                RecordCodecBuilder.create(instance -> instance.group(
                        NumericKind.CODEC.fieldOf("type")
                                .forGetter(NumericComparisonConfiguration::kind),
                        ComparisonOperator.CODEC.fieldOf("operator")
                                .forGetter(NumericComparisonConfiguration::operator)
                ).apply(instance, NumericComparisonConfiguration::new));

        public NumericComparisonConfiguration {
            if (kind == null || operator == null) {
                throw new IllegalArgumentException("Numeric type and comparison operator are required");
            }
        }
    }

    public record NumericArithmeticConfiguration(NumericKind kind, ArithmeticOperator operator) {
        public static final Codec<NumericArithmeticConfiguration> CODEC =
                RecordCodecBuilder.create(instance -> instance.group(
                        NumericKind.CODEC.fieldOf("type")
                                .forGetter(NumericArithmeticConfiguration::kind),
                        ArithmeticOperator.CODEC.fieldOf("operator")
                                .forGetter(NumericArithmeticConfiguration::operator)
                ).apply(instance, NumericArithmeticConfiguration::new));

        public NumericArithmeticConfiguration {
            if (kind == null || operator == null) {
                throw new IllegalArgumentException("Numeric type and arithmetic operator are required");
            }
        }
    }

    public record LoopTriggerConfiguration(boolean enabled, int interval) {
        public static final Codec<LoopTriggerConfiguration> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.BOOL.optionalFieldOf("enabled", true)
                        .forGetter(LoopTriggerConfiguration::enabled),
                Codec.intRange(0, 1200).fieldOf("interval")
                        .forGetter(LoopTriggerConfiguration::interval)
        ).apply(instance, LoopTriggerConfiguration::new));
    }

    public record CollectionBuilderConfiguration(int inputs) {
        public static final Codec<CollectionBuilderConfiguration> CODEC = Codec.intRange(0, 64)
                .optionalFieldOf("inputs", 0)
                .xmap(CollectionBuilderConfiguration::new, CollectionBuilderConfiguration::inputs)
                .codec();
    }

    public record EntityPositionConfiguration(ProgramEntityPositionAnchor anchor) {
        public static final Codec<EntityPositionConfiguration> CODEC = Codec.STRING
                .optionalFieldOf("anchor", ProgramEntityPositionAnchor.FEET.wireName())
                .xmap(
                        value -> new EntityPositionConfiguration(
                                ProgramEntityPositionAnchor.byName(value)),
                        configuration -> configuration.anchor().wireName()
                ).codec();

        public EntityPositionConfiguration {
            if (anchor == null) throw new IllegalArgumentException(
                    "Entity position anchor is required");
        }
    }

    public record ExactFilterConfiguration(String selectors) {
        public static final Codec<ExactFilterConfiguration> CODEC = Codec.STRING
                .fieldOf("selectors")
                .xmap(ExactFilterConfiguration::new, ExactFilterConfiguration::selectors)
                .codec();

        public ExactFilterConfiguration {
            if (selectors == null || selectors.isBlank() || selectors.length() > 512
                    || parsedSelectors(selectors).isEmpty()) {
                throw new IllegalArgumentException("At least one exact selector is required");
            }
        }

        public List<String> values() {
            return parsedSelectors(selectors);
        }

        private static List<String> parsedSelectors(String value) {
            return java.util.Arrays.stream(value.split("[,;\\r\\n]+"))
                    .map(String::trim)
                    .filter(selector -> !selector.isEmpty())
                    .distinct()
                    .limit(64)
                    .toList();
        }
    }

    public record RandomNumberConfiguration(NumericKind kind, String lower, String upper) {
        public static final Codec<RandomNumberConfiguration> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        NumericKind.CODEC.fieldOf("type").forGetter(RandomNumberConfiguration::kind),
                        Codec.STRING.fieldOf("lower").forGetter(RandomNumberConfiguration::lower),
                        Codec.STRING.fieldOf("upper").forGetter(RandomNumberConfiguration::upper)
                ).apply(instance, RandomNumberConfiguration::new));

        public RandomNumberConfiguration {
            if (kind == null || lower == null || upper == null) {
                throw new IllegalArgumentException("Random-number bounds are required");
            }
            switch (kind) {
                case INTEGER -> {
                    if (Integer.parseInt(lower) > Integer.parseInt(upper)) invalidBounds();
                }
                case BIG_INTEGER -> {
                    if (new BigInteger(lower).compareTo(new BigInteger(upper)) > 0) invalidBounds();
                }
                case FLOAT -> {
                    var minimum = finiteBound(lower);
                    var maximum = finiteBound(upper);
                    if (minimum > maximum) invalidBounds();
                }
            }
        }

        private static double finiteBound(String value) {
            var parsed = Double.parseDouble(value);
            if (!Double.isFinite(parsed)) invalidBounds();
            return parsed;
        }

        private static void invalidBounds() {
            throw new IllegalArgumentException("Random-number lower bound exceeds upper bound");
        }
    }

    public record Vec3OperationConfiguration(Vec3Kind kind, Vec3Operator operator) {
        public static final Codec<Vec3OperationConfiguration> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        Vec3Kind.CODEC.fieldOf("type").forGetter(Vec3OperationConfiguration::kind),
                        Vec3Operator.CODEC.fieldOf("operator")
                                .forGetter(Vec3OperationConfiguration::operator)
                ).apply(instance, Vec3OperationConfiguration::new));
    }

    public record DistanceSortConfiguration(PointCollectionKind kind, SortOrder order) {
        public static final Codec<DistanceSortConfiguration> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        PointCollectionKind.CODEC.fieldOf("type")
                                .forGetter(DistanceSortConfiguration::kind),
                        SortOrder.CODEC.fieldOf("order")
                                .forGetter(DistanceSortConfiguration::order)
                ).apply(instance, DistanceSortConfiguration::new));
    }

    public record MovementTriggerConfiguration(MovementCondition condition) {
        public static final Codec<MovementTriggerConfiguration> CODEC = MovementCondition.CODEC
                .fieldOf("condition")
                .xmap(MovementTriggerConfiguration::new, MovementTriggerConfiguration::condition)
                .codec();

        public MovementTriggerConfiguration {
            if (condition == null) throw new IllegalArgumentException("Movement condition is required");
        }
    }

    public record HealthThresholdTriggerConfiguration(
            HealthThresholdMode mode,
            float threshold
    ) {
        public static final Codec<HealthThresholdTriggerConfiguration> CODEC =
                RecordCodecBuilder.create(instance -> instance.group(
                        HealthThresholdMode.CODEC.optionalFieldOf(
                                "mode", HealthThresholdMode.BELOW)
                                .forGetter(HealthThresholdTriggerConfiguration::mode),
                        Codec.floatRange(0.0f, Float.MAX_VALUE)
                                .optionalFieldOf("threshold", 10.0f)
                                .forGetter(HealthThresholdTriggerConfiguration::threshold)
                ).apply(instance, HealthThresholdTriggerConfiguration::new));

        public HealthThresholdTriggerConfiguration {
            if (mode == null || !Float.isFinite(threshold)) {
                throw new IllegalArgumentException("Health threshold configuration is invalid");
            }
        }
    }

    public enum HealthThresholdMode {
        ABOVE("above"),
        BELOW("below");

        private static final Codec<HealthThresholdMode> CODEC = Codec.STRING.xmap(
                HealthThresholdMode::byName,
                HealthThresholdMode::wireName
        );
        private final String wireName;

        HealthThresholdMode(String wireName) {
            this.wireName = wireName;
        }

        public String wireName() {
            return wireName;
        }

        private static HealthThresholdMode byName(String value) {
            for (var mode : values()) if (mode.wireName.equals(value)) return mode;
            throw new IllegalArgumentException("Unknown health threshold mode " + value);
        }
    }

    public record LookTargetConfiguration(LookTargetType targetType) {
        public static final Codec<LookTargetConfiguration> CODEC = Codec.STRING
                .optionalFieldOf("target_type", LookTargetType.ENTITY.wireName())
                .xmap(
                        value -> new LookTargetConfiguration(LookTargetType.byName(value)),
                        configuration -> configuration.targetType().wireName()
                )
                .codec();

        public LookTargetConfiguration {
            if (targetType == null) throw new IllegalArgumentException("Look target type is required");
        }
    }

    public record BlockNormalConfiguration(BlockNormalMode mode) {
        public static final Codec<BlockNormalConfiguration> CODEC = Codec.STRING
                .optionalFieldOf("mode", BlockNormalMode.VIEW.wireName())
                .xmap(
                        value -> new BlockNormalConfiguration(BlockNormalMode.byName(value)),
                        configuration -> configuration.mode().wireName()
                )
                .codec();

        public BlockNormalConfiguration {
            if (mode == null) throw new IllegalArgumentException("Block normal mode is required");
        }
    }

    public enum BlockNormalMode {
        VIEW("view"),
        POSITION_DIRECTION("position_direction");

        private final String wireName;

        BlockNormalMode(String wireName) {
            this.wireName = wireName;
        }

        public String wireName() {
            return wireName;
        }

        private static BlockNormalMode byName(String name) {
            for (var mode : values()) if (mode.wireName.equals(name)) return mode;
            throw new IllegalArgumentException("Unknown block normal mode " + name);
        }
    }

    public enum LookTargetType {
        ENTITY("entity", "entity", ProgramValueTypes.ENTITY_REFERENCE),
        BLOCK("block", "block", ProgramValueTypes.BLOCK_POSITION);

        private final String wireName;
        private final String port;
        private final ProgramValueType valueType;

        LookTargetType(String wireName, String port, ProgramValueType valueType) {
            this.wireName = wireName;
            this.port = port;
            this.valueType = valueType;
        }

        public String wireName() {
            return wireName;
        }

        public String port() {
            return port;
        }

        public ProgramValueType valueType() {
            return valueType;
        }

        private static LookTargetType byName(String name) {
            for (var type : values()) if (type.wireName.equals(name)) return type;
            throw new IllegalArgumentException("Unknown look target type " + name);
        }
    }

    public enum ScalarKind {
        BOOLEAN("boolean", ProgramValueTypes.BOOLEAN) {
            @Override
            Object parse(String value) {
                if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                    throw new IllegalArgumentException("Boolean value must be true or false");
                }
                return Boolean.parseBoolean(value);
            }
        },
        INTEGER("integer", ProgramValueTypes.INTEGER) {
            @Override
            Object parse(String value) {
                return Integer.parseInt(value);
            }
        },
        BIG_INTEGER("big_integer", ProgramValueTypes.BIG_INTEGER) {
            @Override
            Object parse(String value) {
                return new BigInteger(value);
            }
        },
        FLOAT("float", ProgramValueTypes.FLOAT) {
            @Override
            Object parse(String value) {
                var result = Double.parseDouble(value);
                if (!Double.isFinite(result)) {
                    throw new IllegalArgumentException("Float must be finite");
                }
                return result;
            }
        };

        private static final Codec<ScalarKind> CODEC = Codec.STRING.xmap(
                ScalarKind::byName,
                ScalarKind::wireName
        );
        private final String wireName;
        private final ProgramValueType type;

        ScalarKind(String wireName, ProgramValueType type) {
            this.wireName = wireName;
            this.type = type;
        }

        abstract Object parse(String value);

        public String wireName() {
            return wireName;
        }

        public ProgramValueType type() {
            return type;
        }

        private static ScalarKind byName(String name) {
            for (var kind : values()) if (kind.wireName.equals(name)) return kind;
            throw new IllegalArgumentException("Unknown scalar type " + name);
        }
    }

    public enum NumericKind {
        INTEGER("integer", ProgramValueTypes.INTEGER),
        BIG_INTEGER("big_integer", ProgramValueTypes.BIG_INTEGER),
        FLOAT("float", ProgramValueTypes.FLOAT);

        private static final Codec<NumericKind> CODEC = Codec.STRING.xmap(
                NumericKind::byName,
                NumericKind::wireName
        );
        private final String wireName;
        private final ProgramValueType type;

        NumericKind(String wireName, ProgramValueType type) {
            this.wireName = wireName;
            this.type = type;
        }

        public String wireName() {
            return wireName;
        }

        public ProgramValueType type() {
            return type;
        }

        private static NumericKind byName(String name) {
            for (var kind : values()) if (kind.wireName.equals(name)) return kind;
            throw new IllegalArgumentException("Unknown numeric type " + name);
        }
    }

    public enum ComparisonOperator {
        EQUAL("equal"),
        LESS("less"),
        LESS_EQUAL("less_equal"),
        GREATER("greater"),
        GREATER_EQUAL("greater_equal");

        private static final Codec<ComparisonOperator> CODEC = Codec.STRING.xmap(
                ComparisonOperator::byName,
                ComparisonOperator::wireName
        );
        private final String wireName;

        ComparisonOperator(String wireName) {
            this.wireName = wireName;
        }

        public String wireName() {
            return wireName;
        }

        public boolean test(int comparison) {
            return switch (this) {
                case EQUAL -> comparison == 0;
                case LESS -> comparison < 0;
                case LESS_EQUAL -> comparison <= 0;
                case GREATER -> comparison > 0;
                case GREATER_EQUAL -> comparison >= 0;
            };
        }

        private static ComparisonOperator byName(String name) {
            for (var operator : values()) if (operator.wireName.equals(name)) return operator;
            throw new IllegalArgumentException("Unknown comparison operator " + name);
        }
    }

    public enum ArithmeticOperator {
        ADD("add"),
        SUBTRACT("subtract"),
        MULTIPLY("multiply"),
        DIVIDE("divide"),
        MODULO("modulo"),
        ABSOLUTE("absolute");

        private static final Codec<ArithmeticOperator> CODEC = Codec.STRING.xmap(
                ArithmeticOperator::byName,
                ArithmeticOperator::wireName
        );
        private final String wireName;

        ArithmeticOperator(String wireName) {
            this.wireName = wireName;
        }

        public String wireName() {
            return wireName;
        }

        private static ArithmeticOperator byName(String name) {
            for (var operator : values()) if (operator.wireName.equals(name)) return operator;
            throw new IllegalArgumentException("Unknown arithmetic operator " + name);
        }
    }

    public enum Vec3Kind {
        DIRECTION("direction", ProgramValueTypes.DIRECTION),
        WORLD_POSITION("world_position", ProgramValueTypes.WORLD_POSITION);

        private static final Codec<Vec3Kind> CODEC = Codec.STRING.xmap(
                Vec3Kind::byName, Vec3Kind::wireName);
        private final String wireName;
        private final ProgramValueType type;

        Vec3Kind(String wireName, ProgramValueType type) {
            this.wireName = wireName;
            this.type = type;
        }

        public String wireName() { return wireName; }
        public ProgramValueType type() { return type; }

        private static Vec3Kind byName(String name) {
            for (var value : values()) if (value.wireName.equals(name)) return value;
            throw new IllegalArgumentException("Unknown vec3 type " + name);
        }
    }

    public enum Vec3Operator {
        DOT("dot"), CROSS("cross"), ADD("add");

        private static final Codec<Vec3Operator> CODEC = Codec.STRING.xmap(
                Vec3Operator::byName, Vec3Operator::wireName);
        private final String wireName;

        Vec3Operator(String wireName) { this.wireName = wireName; }
        public String wireName() { return wireName; }

        private static Vec3Operator byName(String name) {
            for (var value : values()) if (value.wireName.equals(name)) return value;
            throw new IllegalArgumentException("Unknown vec3 operator " + name);
        }
    }

    public enum PointCollectionKind {
        ENTITY("entity", ProgramValueTypes.ENTITY_SET),
        WORLD_POSITION("world_position", ProgramValueTypes.WORLD_POSITION_SET),
        BLOCK_POSITION("block_position", ProgramValueTypes.BLOCK_POSITION_SET);

        private static final Codec<PointCollectionKind> CODEC = Codec.STRING.xmap(
                PointCollectionKind::byName, PointCollectionKind::wireName);
        private final String wireName;
        private final ProgramValueType collectionType;

        PointCollectionKind(String wireName, ProgramValueType collectionType) {
            this.wireName = wireName;
            this.collectionType = collectionType;
        }

        public String wireName() { return wireName; }
        public ProgramValueType collectionType() { return collectionType; }

        private static PointCollectionKind byName(String name) {
            for (var value : values()) if (value.wireName.equals(name)) return value;
            throw new IllegalArgumentException("Unknown point collection type " + name);
        }
    }

    public enum SortOrder {
        ASCENDING("ascending", false), DESCENDING("descending", true);

        private static final Codec<SortOrder> CODEC = Codec.STRING.xmap(
                SortOrder::byName, SortOrder::wireName);
        private final String wireName;
        private final boolean reversed;

        SortOrder(String wireName, boolean reversed) {
            this.wireName = wireName;
            this.reversed = reversed;
        }

        public String wireName() { return wireName; }
        public boolean reversed() { return reversed; }

        private static SortOrder byName(String name) {
            for (var value : values()) if (value.wireName.equals(name)) return value;
            throw new IllegalArgumentException("Unknown sort order " + name);
        }
    }

    public enum MovementCondition {
        JUMP("jump"),
        SNEAK("sneak"),
        SPRINT("sprint"),
        ELYTRA("elytra"),
        SWIM("swim");

        private static final Codec<MovementCondition> CODEC = Codec.STRING.xmap(
                MovementCondition::byName,
                MovementCondition::wireName
        );
        private final String wireName;

        MovementCondition(String wireName) {
            this.wireName = wireName;
        }

        public String wireName() {
            return wireName;
        }

        public static MovementCondition byName(String name) {
            for (var condition : values()) if (condition.wireName.equals(name)) return condition;
            throw new IllegalArgumentException("Unknown movement condition " + name);
        }
    }

    public record IntegerConfiguration(int value) {
        public static final Codec<IntegerConfiguration> CODEC = Codec.INT.fieldOf("value")
                .xmap(IntegerConfiguration::new, IntegerConfiguration::value).codec();
    }

    public record BigIntegerConfiguration(BigInteger value) {
        public static final Codec<BigIntegerConfiguration> CODEC = Codec.STRING.fieldOf("value")
                .xmap(value -> new BigIntegerConfiguration(new BigInteger(value)),
                        configuration -> configuration.value.toString())
                .codec();

        public BigIntegerConfiguration {
            if (value == null) throw new IllegalArgumentException("Big integer cannot be null");
        }
    }

    public record FloatConfiguration(double value) {
        public static final Codec<FloatConfiguration> CODEC = Codec.DOUBLE.fieldOf("value")
                .xmap(FloatConfiguration::new, FloatConfiguration::value).codec();

        public FloatConfiguration {
            if (!Double.isFinite(value)) throw new IllegalArgumentException("Float must be finite");
        }
    }

    public record DimensionConfiguration(Identifier dimension) {
        public static final Codec<DimensionConfiguration> CODEC = IDENTIFIER_CODEC
                .fieldOf("dimension")
                .xmap(DimensionConfiguration::new, DimensionConfiguration::dimension)
                .codec();
    }

    public record DirectionConfiguration(double x, double y, double z) {
        public static final Codec<DirectionConfiguration> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        Codec.DOUBLE.fieldOf("x").forGetter(DirectionConfiguration::x),
                        Codec.DOUBLE.fieldOf("y").forGetter(DirectionConfiguration::y),
                        Codec.DOUBLE.fieldOf("z").forGetter(DirectionConfiguration::z)
                ).apply(instance, DirectionConfiguration::new));
    }

    public record WorldPositionConfiguration(
            Identifier dimension,
            double x,
            double y,
            double z
    ) {
        public static final Codec<WorldPositionConfiguration> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        IDENTIFIER_CODEC.fieldOf("dimension").forGetter(WorldPositionConfiguration::dimension),
                        Codec.DOUBLE.fieldOf("x").forGetter(WorldPositionConfiguration::x),
                        Codec.DOUBLE.fieldOf("y").forGetter(WorldPositionConfiguration::y),
                        Codec.DOUBLE.fieldOf("z").forGetter(WorldPositionConfiguration::z)
                ).apply(instance, WorldPositionConfiguration::new));
    }

    public record BlockPositionConfiguration(Identifier dimension, int x, int y, int z) {
        public static final Codec<BlockPositionConfiguration> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        IDENTIFIER_CODEC.fieldOf("dimension").forGetter(BlockPositionConfiguration::dimension),
                        Codec.INT.fieldOf("x").forGetter(BlockPositionConfiguration::x),
                        Codec.INT.fieldOf("y").forGetter(BlockPositionConfiguration::y),
                        Codec.INT.fieldOf("z").forGetter(BlockPositionConfiguration::z)
                ).apply(instance, BlockPositionConfiguration::new));
    }

    public record VariableConfiguration(String name, Identifier typeId) {
        public static final Codec<VariableConfiguration> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        Codec.STRING.fieldOf("name").forGetter(VariableConfiguration::name),
                        IDENTIFIER_CODEC.fieldOf("type").forGetter(VariableConfiguration::typeId)
                ).apply(instance, VariableConfiguration::new));

        public VariableConfiguration {
            if (name == null || name.isBlank() || name.length() > 64) {
                throw new IllegalArgumentException("Variable name must contain 1 to 64 characters");
            }
            variableType(typeId);
        }

        public ProgramValueType type() {
            return variableType(typeId);
        }
    }

    public record EntityKindConfiguration(EntityKind type) {
        public static final Codec<EntityKindConfiguration> CODEC = EntityKind.CODEC
                .fieldOf("type")
                .xmap(EntityKindConfiguration::new, EntityKindConfiguration::type)
                .codec();

        public EntityKindConfiguration {
            if (type == null) throw new IllegalArgumentException("Entity type is required");
        }
    }

    public enum EntityKind {
        ANY("any"),
        LIVING("living"),
        PLAYER("player"),
        MOB("mob"),
        HOSTILE("hostile"),
        ANIMAL("animal"),
        FRIENDLY("friendly"),
        PROJECTILE("projectile"),
        ITEM("item");

        private static final Codec<EntityKind> CODEC = Codec.STRING.xmap(
                EntityKind::byName,
                EntityKind::wireName
        );
        private final String wireName;

        EntityKind(String wireName) {
            this.wireName = wireName;
        }

        public String wireName() {
            return wireName;
        }

        private static EntityKind byName(String name) {
            for (var type : values()) if (type.wireName.equals(name)) return type;
            throw new IllegalArgumentException("Unknown entity type " + name);
        }
    }

    private record FixedNodeType<C>(
            Codec<C> configurationCodec,
            Function<C, ProgramNodeSchema> schemaFactory,
            ProgramNodeRole role,
            ProgramNodePurity purity
    ) implements ProgramNodeType<C> {
        @Override
        public int schemaVersion() {
            return 1;
        }

        @Override
        public ProgramNodeSchema schema(C configuration) {
            return schemaFactory.apply(configuration);
        }

        @Override
        public ProgramNodeScope scope() {
            return ProgramNodeScope.COMMON;
        }
    }

    private enum Unit {
        INSTANCE
    }
}
