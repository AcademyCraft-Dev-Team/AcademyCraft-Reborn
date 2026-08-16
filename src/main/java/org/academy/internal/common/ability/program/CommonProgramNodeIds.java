package org.academy.internal.common.ability.program;

import net.minecraft.resources.Identifier;
import org.academy.AcademyCraft;

/** Stable identifiers for the shared, ability-category-independent node algebra. */
public final class CommonProgramNodeIds {
    public static final Identifier SCALAR_CONSTANT = id("value/scalar");
    public static final Identifier BOOLEAN_CONSTANT = id("value/boolean");
    public static final Identifier INTEGER_CONSTANT = id("value/integer");
    public static final Identifier FLOAT_CONSTANT = id("value/float");

    public static final Identifier INTEGER_ADD = id("logic/integer/add");
    public static final Identifier INTEGER_SUBTRACT = id("logic/integer/subtract");
    public static final Identifier INTEGER_MULTIPLY = id("logic/integer/multiply");
    public static final Identifier INTEGER_DIVIDE = id("logic/integer/divide");
    public static final Identifier INTEGER_MODULO = id("logic/integer/modulo");
    public static final Identifier INTEGER_EQUAL = id("logic/integer/equal");
    public static final Identifier INTEGER_LESS = id("logic/integer/less");
    public static final Identifier INTEGER_LESS_EQUAL = id("logic/integer/less_equal");
    public static final Identifier INTEGER_GREATER = id("logic/integer/greater");
    public static final Identifier INTEGER_GREATER_EQUAL = id("logic/integer/greater_equal");

    public static final Identifier BIG_INTEGER_CONSTANT = id("value/big_integer");
    public static final Identifier BIG_INTEGER_ADD = id("logic/big_integer/add");
    public static final Identifier BIG_INTEGER_SUBTRACT = id("logic/big_integer/subtract");
    public static final Identifier BIG_INTEGER_MULTIPLY = id("logic/big_integer/multiply");
    public static final Identifier BIG_INTEGER_DIVIDE = id("logic/big_integer/divide");
    public static final Identifier BIG_INTEGER_MODULO = id("logic/big_integer/modulo");
    public static final Identifier BIG_INTEGER_EQUAL = id("logic/big_integer/equal");
    public static final Identifier BIG_INTEGER_LESS = id("logic/big_integer/less");
    public static final Identifier BIG_INTEGER_LESS_EQUAL = id("logic/big_integer/less_equal");
    public static final Identifier BIG_INTEGER_GREATER = id("logic/big_integer/greater");
    public static final Identifier BIG_INTEGER_GREATER_EQUAL = id("logic/big_integer/greater_equal");

    public static final Identifier FLOAT_ADD = id("logic/float/add");
    public static final Identifier FLOAT_SUBTRACT = id("logic/float/subtract");
    public static final Identifier FLOAT_MULTIPLY = id("logic/float/multiply");
    public static final Identifier FLOAT_DIVIDE = id("logic/float/divide");
    public static final Identifier FLOAT_MODULO = id("logic/float/modulo");
    public static final Identifier FLOAT_EQUAL = id("logic/float/equal");
    public static final Identifier FLOAT_LESS = id("logic/float/less");
    public static final Identifier FLOAT_LESS_EQUAL = id("logic/float/less_equal");
    public static final Identifier FLOAT_GREATER = id("logic/float/greater");
    public static final Identifier FLOAT_GREATER_EQUAL = id("logic/float/greater_equal");
    public static final Identifier NUMERIC_ARITHMETIC = id("logic/numeric/arithmetic");
    public static final Identifier NUMERIC_COMPARE = id("logic/numeric/compare");

    public static final Identifier BOOLEAN_NOT = id("logic/boolean/not");
    public static final Identifier BOOLEAN_AND = id("logic/boolean/and");
    public static final Identifier BOOLEAN_OR = id("logic/boolean/or");
    public static final Identifier BOOLEAN_XOR = id("logic/boolean/xor");
    public static final Identifier BRANCH = id("flow/branch");
    public static final Identifier STOP = id("flow/stop");
    public static final Identifier TRIGGER_HURT = id("flow/trigger/hurt");
    public static final Identifier TRIGGER_LOOP = id("flow/trigger/loop");
    public static final Identifier TRIGGER_MELEE = id("flow/trigger/melee");
    public static final Identifier TRIGGER_MOVEMENT = id("flow/trigger/movement");
    public static final Identifier TRIGGER_HEALTH_THRESHOLD =
            id("flow/trigger/health_threshold");
    public static final Identifier VARIABLE_GET = id("state/variable_get");
    public static final Identifier VARIABLE_SET = id("state/variable_set");

    public static final Identifier WORLD_POSITION_CONSTANT = id("spatial/world_position");
    public static final Identifier WORLD_POSITION_CONSTRUCT = id("spatial/world_position_construct");
    public static final Identifier WORLD_POSITION_COMPONENTS = id("spatial/world_position_components");
    public static final Identifier WORLD_POSITION_OFFSET = id("spatial/world_position_offset");
    public static final Identifier WORLD_POSITION_DISTANCE = id("spatial/world_position_distance");
    public static final Identifier WORLD_POSITION_SAME_DIMENSION = id("spatial/world_position_same_dimension");
    public static final Identifier BLOCK_POSITION_CONSTANT = id("spatial/block_position");
    public static final Identifier BLOCK_POSITION_CONSTRUCT = id("spatial/block_position_construct");
    public static final Identifier BLOCK_POSITION_COMPONENTS = id("spatial/block_position_components");
    public static final Identifier POSITION_TO_BLOCK = id("spatial/position_to_block");
    public static final Identifier BLOCK_TO_CENTER = id("spatial/block_to_center");
    public static final Identifier DIRECTION_CONSTANT = id("spatial/direction");
    public static final Identifier DIRECTION_CONSTRUCT = id("spatial/direction_construct");
    public static final Identifier DIRECTION_COMPONENTS = id("spatial/direction_components");
    public static final Identifier DIRECTION_BETWEEN = id("spatial/direction_between");
    public static final Identifier DIRECTION_OPPOSITE = id("spatial/direction_opposite");
    public static final Identifier DIRECTION_DOT = id("spatial/direction_dot");

    public static final Identifier ENTITY_POSITION = id("query/entity_position");
    public static final Identifier ENTITY_LOOK_DIRECTION = id("query/entity_look_direction");
    public static final Identifier CASTER = id("query/caster");
    public static final Identifier LOOK_TARGET = id("query/look_target");
    public static final Identifier ENTITIES_AROUND = id("query/entities_around");
    public static final Identifier RAYCAST_BLOCK = id("query/raycast_block");
    public static final Identifier RAYCAST_ENTITY = id("query/raycast_entity");
    public static final Identifier BLOCK_NORMAL = id("query/block_normal");

    public static final Identifier FILTER_ENTITY_ALIVE = id("filter/entity/alive");
    public static final Identifier FILTER_ENTITY_DISTANCE = id("filter/entity/distance");
    public static final Identifier FILTER_ENTITY_ALLIED_TO = id("filter/entity/allied_to");
    public static final Identifier FILTER_ENTITY_HOSTILE_TO = id("filter/entity/hostile_to");
    public static final Identifier FILTER_ENTITY_TARGETED_BY = id("filter/entity/targeted_by");
    public static final Identifier FILTER_ENTITY_LAST_DAMAGED_BY =
            id("filter/entity/last_damaged_by");
    public static final Identifier FILTER_ENTITY_TYPE = id("filter/entity/type");
    public static final Identifier FILTER_ENTITY_HEALTH_AT_LEAST =
            id("filter/entity/health_at_least");
    public static final Identifier FILTER_ENTITY_HEALTH_AT_MOST =
            id("filter/entity/health_at_most");
    public static final Identifier FILTER_ENTITY_MAX_HEALTH_AT_LEAST =
            id("filter/entity/max_health_at_least");
    public static final Identifier FILTER_ENTITY_MAX_HEALTH_AT_MOST =
            id("filter/entity/max_health_at_most");
    public static final Identifier FILTER_ENTITY_HAS_TARGET = id("filter/entity/has_target");
    public static final Identifier FILTER_ENTITY_VISIBLE_FROM = id("filter/entity/visible_from");

    public static final Identifier ENTITY_EQUAL = id("logic/entity/equal");
    public static final Identifier WORLD_POSITION_EQUAL = id("logic/world_position/equal");
    public static final Identifier BLOCK_POSITION_EQUAL = id("logic/block_position/equal");
    public static final Identifier DIRECTION_EQUAL = id("logic/direction/equal");
    public static final Identifier RANDOM_ENTITY = id("collection/entity/random");
    public static final Identifier NEAREST_ENTITY_TO_POSITION =
            id("collection/entity/nearest_to_position");
    public static final Identifier RANDOM_WORLD_POSITION =
            id("collection/world_position/random");
    public static final Identifier RANDOM_BLOCK_POSITION =
            id("collection/block_position/random");
    public static final Identifier RANDOM_DIRECTION = id("collection/direction/random");

    private CommonProgramNodeIds() {
    }

    public static Identifier collection(String domain, String operation) {
        if (!switch (domain) {
            case "entity", "world_position", "block_position", "direction" -> true;
            default -> false;
        }) {
            throw new IllegalArgumentException("Unknown collection domain " + domain);
        }
        if (!switch (operation) {
            case "empty", "singleton", "union", "intersection", "difference", "contains",
                 "size", "get", "foreach" -> true;
            default -> false;
        }) {
            throw new IllegalArgumentException("Unknown collection operation " + operation);
        }
        return id("collection/" + domain + "/" + operation);
    }

    private static Identifier id(String path) {
        return AcademyCraft.academy("program/core/" + path);
    }
}
