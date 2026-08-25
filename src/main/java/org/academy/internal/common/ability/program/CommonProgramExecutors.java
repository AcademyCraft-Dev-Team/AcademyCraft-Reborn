package org.academy.internal.common.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import org.academy.api.common.ability.program.ProgramBlockPosition;
import org.academy.api.common.ability.program.ProgramDirection;
import org.academy.api.common.ability.program.ProgramTargetResolver;
import org.academy.api.common.ability.program.ProgramValue;
import org.academy.api.common.ability.program.ProgramValueType;
import org.academy.api.common.ability.program.ProgramValueTypes;
import org.academy.api.common.ability.program.ProgramWorldPosition;
import org.academy.internal.common.ability.darkmatter.DarkmatterTargeting;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Comparator;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiFunction;
import java.util.function.DoubleBinaryOperator;
import java.util.function.IntBinaryOperator;
import java.util.function.Predicate;

/** Runtime implementations of the common node algebra. */
public final class CommonProgramExecutors implements ProgramExecutorLookup {
    public static final CommonProgramExecutors INSTANCE = new CommonProgramExecutors();

    private final Map<Identifier, ProgramNodeExecutor<?>> executors;

    private CommonProgramExecutors() {
        var result = new HashMap<Identifier, ProgramNodeExecutor<?>>();
        registerConstants(result);
        registerScalarLogic(result);
        registerTriggerEntries(result);
        registerControlAndState(result);
        registerSpatial(result);
        registerQueries(result);
        registerFilters(result);
        registerEquality(result);
        for (var domain : CommonProgramNodeCatalog.CollectionDomain.values()) {
            registerCollection(result, domain);
        }
        registerRandomCollectionValues(result);
        registerAdvancedValues(result);
        executors = Map.copyOf(result);
    }

    @Override
    public @Nullable ProgramNodeExecutor<?> find(Identifier nodeType) {
        return executors.get(nodeType);
    }

    public Map<Identifier, ProgramNodeExecutor<?>> executors() {
        return executors;
    }

    private static void registerConstants(Map<Identifier, ProgramNodeExecutor<?>> result) {
        put(result, CommonProgramNodeIds.SCALAR_CONSTANT,
                (ProgramVmContext _, CommonProgramNodeCatalog.ScalarConfiguration configuration,
                 ProgramInputView _) -> data(
                        "value",
                        configuration.kind().type(),
                        configuration.parsedValue()
                ));
        put(result, CommonProgramNodeIds.BOOLEAN_CONSTANT,
                (ProgramVmContext _, CommonProgramNodeCatalog.BooleanConfiguration configuration,
                 ProgramInputView _) -> data("value", ProgramValueTypes.BOOLEAN, configuration.value()));
        put(result, CommonProgramNodeIds.INTEGER_CONSTANT,
                (ProgramVmContext _, CommonProgramNodeCatalog.IntegerConfiguration configuration,
                 ProgramInputView _) -> data("value", ProgramValueTypes.INTEGER, configuration.value()));
        put(result, CommonProgramNodeIds.BIG_INTEGER_CONSTANT,
                (ProgramVmContext _, CommonProgramNodeCatalog.BigIntegerConfiguration configuration,
                 ProgramInputView _) -> data("value", ProgramValueTypes.BIG_INTEGER, configuration.value()));
        put(result, CommonProgramNodeIds.FLOAT_CONSTANT,
                (ProgramVmContext _, CommonProgramNodeCatalog.FloatConfiguration configuration,
                 ProgramInputView _) -> data("value", ProgramValueTypes.FLOAT, configuration.value()));
    }

    private static void registerScalarLogic(Map<Identifier, ProgramNodeExecutor<?>> result) {
        put(result, CommonProgramNodeIds.NUMERIC_ARITHMETIC,
                (ProgramVmContext _,
                 CommonProgramNodeCatalog.NumericArithmeticConfiguration configuration,
                 ProgramInputView inputs) -> numericArithmetic(inputs, configuration));
        put(result, CommonProgramNodeIds.NUMERIC_COMPARE,
                (ProgramVmContext _,
                 CommonProgramNodeCatalog.NumericComparisonConfiguration configuration,
                 ProgramInputView inputs) -> data(
                        "result",
                        ProgramValueTypes.BOOLEAN,
                        configuration.operator().test(compareNumeric(inputs, configuration.kind()))
                ));
        integerBinary(result, CommonProgramNodeIds.INTEGER_ADD, Math::addExact);
        integerBinary(result, CommonProgramNodeIds.INTEGER_SUBTRACT, Math::subtractExact);
        integerBinary(result, CommonProgramNodeIds.INTEGER_MULTIPLY, Math::multiplyExact);
        integerBinary(result, CommonProgramNodeIds.INTEGER_DIVIDE, (left, right) -> left / right);
        integerBinary(result, CommonProgramNodeIds.INTEGER_MODULO, (left, right) -> left % right);
        integerComparison(result, CommonProgramNodeIds.INTEGER_EQUAL, value -> value == 0);
        integerComparison(result, CommonProgramNodeIds.INTEGER_LESS, value -> value < 0);
        integerComparison(result, CommonProgramNodeIds.INTEGER_LESS_EQUAL, value -> value <= 0);
        integerComparison(result, CommonProgramNodeIds.INTEGER_GREATER, value -> value > 0);
        integerComparison(result, CommonProgramNodeIds.INTEGER_GREATER_EQUAL, value -> value >= 0);

        bigIntegerBinary(result, CommonProgramNodeIds.BIG_INTEGER_ADD, BigInteger::add);
        bigIntegerBinary(result, CommonProgramNodeIds.BIG_INTEGER_SUBTRACT, BigInteger::subtract);
        bigIntegerBinary(result, CommonProgramNodeIds.BIG_INTEGER_MULTIPLY, BigInteger::multiply);
        bigIntegerBinary(result, CommonProgramNodeIds.BIG_INTEGER_DIVIDE, BigInteger::divide);
        bigIntegerBinary(result, CommonProgramNodeIds.BIG_INTEGER_MODULO, BigInteger::remainder);
        bigIntegerComparison(result, CommonProgramNodeIds.BIG_INTEGER_EQUAL, value -> value == 0);
        bigIntegerComparison(result, CommonProgramNodeIds.BIG_INTEGER_LESS, value -> value < 0);
        bigIntegerComparison(result, CommonProgramNodeIds.BIG_INTEGER_LESS_EQUAL, value -> value <= 0);
        bigIntegerComparison(result, CommonProgramNodeIds.BIG_INTEGER_GREATER, value -> value > 0);
        bigIntegerComparison(result, CommonProgramNodeIds.BIG_INTEGER_GREATER_EQUAL, value -> value >= 0);

        floatBinary(result, CommonProgramNodeIds.FLOAT_ADD, (left, right) -> left + right);
        floatBinary(result, CommonProgramNodeIds.FLOAT_SUBTRACT, (left, right) -> left - right);
        floatBinary(result, CommonProgramNodeIds.FLOAT_MULTIPLY, (left, right) -> left * right);
        floatBinary(result, CommonProgramNodeIds.FLOAT_DIVIDE, (left, right) -> left / right);
        floatBinary(result, CommonProgramNodeIds.FLOAT_MODULO, (left, right) -> left % right);
        floatComparison(result, CommonProgramNodeIds.FLOAT_EQUAL, value -> value == 0);
        floatComparison(result, CommonProgramNodeIds.FLOAT_LESS, value -> value < 0);
        floatComparison(result, CommonProgramNodeIds.FLOAT_LESS_EQUAL, value -> value <= 0);
        floatComparison(result, CommonProgramNodeIds.FLOAT_GREATER, value -> value > 0);
        floatComparison(result, CommonProgramNodeIds.FLOAT_GREATER_EQUAL, value -> value >= 0);

        put(result, CommonProgramNodeIds.BOOLEAN_NOT, (_, _, inputs) -> data(
                "result",
                ProgramValueTypes.BOOLEAN,
                !booleanValue(inputs, "value")
        ));
        booleanBinary(result, CommonProgramNodeIds.BOOLEAN_AND, (left, right) -> left && right);
        booleanBinary(result, CommonProgramNodeIds.BOOLEAN_OR, (left, right) -> left || right);
        booleanBinary(result, CommonProgramNodeIds.BOOLEAN_XOR, (left, right) -> left ^ right);
    }

    private static void registerControlAndState(
            Map<Identifier, ProgramNodeExecutor<?>> result
    ) {
        put(result, CommonProgramNodeIds.BRANCH, (_, _, inputs) -> ProgramNodeStep.next(
                booleanValue(inputs, "condition") ? "true" : "false"
        ));
        put(result, CommonProgramNodeIds.STOP, (_, _, _) -> ProgramNodeStep.stop());
        put(result, CommonProgramNodeIds.VARIABLE_GET,
                (ProgramVmContext context,
                 CommonProgramNodeCatalog.VariableConfiguration configuration,
                 ProgramInputView _) -> {
                    var value = context.variable(configuration.name()).orElseThrow(() ->
                            new IllegalStateException("Program variable is not initialized"));
                    return data("value", coerce(value, configuration.type()));
                });
        put(result, CommonProgramNodeIds.VARIABLE_SET,
                (ProgramVmContext context,
                 CommonProgramNodeCatalog.VariableConfiguration configuration,
                 ProgramInputView inputs) -> {
                    context.setVariable(
                            configuration.name(),
                            coerce(inputs.requireCompatible("value", configuration.type()), configuration.type())
                    );
                    return ProgramNodeStep.next("flow");
                });
    }

    private static void registerTriggerEntries(
            Map<Identifier, ProgramNodeExecutor<?>> result
    ) {
        for (var id : List.of(
                CommonProgramNodeIds.TRIGGER_HURT,
                CommonProgramNodeIds.TRIGGER_LOOP,
                CommonProgramNodeIds.TRIGGER_MELEE,
                CommonProgramNodeIds.TRIGGER_MOVEMENT,
                CommonProgramNodeIds.TRIGGER_HEALTH_THRESHOLD
        )) {
            put(result, id, (_, _, _) -> ProgramNodeStep.next("flow"));
        }
    }

    private static void registerSpatial(Map<Identifier, ProgramNodeExecutor<?>> result) {
        put(result, CommonProgramNodeIds.WORLD_POSITION_CONSTANT,
                (ProgramVmContext _,
                 CommonProgramNodeCatalog.WorldPositionConfiguration configuration,
                 ProgramInputView _) -> data(
                        "position",
                        ProgramValueTypes.WORLD_POSITION,
                        new ProgramWorldPosition(
                                configuration.dimension(),
                                configuration.x(),
                                configuration.y(),
                                configuration.z()
                        )
                ));
        put(result, CommonProgramNodeIds.WORLD_POSITION_CONSTRUCT,
                (ProgramVmContext _,
                 CommonProgramNodeCatalog.DimensionConfiguration configuration,
                 ProgramInputView inputs) -> data(
                        "position",
                        ProgramValueTypes.WORLD_POSITION,
                        new ProgramWorldPosition(
                                configuration.dimension(),
                                floatValue(inputs, "x"),
                                floatValue(inputs, "y"),
                                floatValue(inputs, "z")
                        )
                ));
        put(result, CommonProgramNodeIds.WORLD_POSITION_COMPONENTS, (_, _, inputs) -> {
            var position = worldPosition(inputs, "position");
            return ProgramNodeStep.data(Map.of(
                    "x", value(ProgramValueTypes.FLOAT, position.x()),
                    "y", value(ProgramValueTypes.FLOAT, position.y()),
                    "z", value(ProgramValueTypes.FLOAT, position.z())
            ));
        });
        put(result, CommonProgramNodeIds.WORLD_POSITION_OFFSET, (_, _, inputs) -> data(
                "position",
                ProgramValueTypes.WORLD_POSITION,
                worldPosition(inputs, "position").offset(
                        direction(inputs, "direction"),
                        floatValue(inputs, "distance")
                )
        ));
        put(result, CommonProgramNodeIds.WORLD_POSITION_DISTANCE, (_, _, inputs) -> data(
                "result",
                ProgramValueTypes.FLOAT,
                worldPosition(inputs, "left").distanceTo(worldPosition(inputs, "right"))
        ));
        put(result, CommonProgramNodeIds.WORLD_POSITION_SAME_DIMENSION, (_, _, inputs) -> data(
                "result",
                ProgramValueTypes.BOOLEAN,
                worldPosition(inputs, "left").dimension().equals(
                        worldPosition(inputs, "right").dimension()
                )
        ));

        put(result, CommonProgramNodeIds.BLOCK_POSITION_CONSTANT,
                (ProgramVmContext _,
                 CommonProgramNodeCatalog.BlockPositionConfiguration configuration,
                 ProgramInputView _) -> data(
                        "position",
                        ProgramValueTypes.BLOCK_POSITION,
                        new ProgramBlockPosition(
                                configuration.dimension(),
                                configuration.x(),
                                configuration.y(),
                                configuration.z()
                        )
                ));
        put(result, CommonProgramNodeIds.BLOCK_POSITION_CONSTRUCT,
                (ProgramVmContext _,
                 CommonProgramNodeCatalog.DimensionConfiguration configuration,
                 ProgramInputView inputs) -> data(
                        "position",
                        ProgramValueTypes.BLOCK_POSITION,
                        new ProgramBlockPosition(
                                configuration.dimension(),
                                integer(inputs, "x"),
                                integer(inputs, "y"),
                                integer(inputs, "z")
                        )
                ));
        put(result, CommonProgramNodeIds.BLOCK_POSITION_COMPONENTS, (_, _, inputs) -> {
            var position = blockPosition(inputs, "position");
            return ProgramNodeStep.data(Map.of(
                    "x", value(ProgramValueTypes.INTEGER, position.x()),
                    "y", value(ProgramValueTypes.INTEGER, position.y()),
                    "z", value(ProgramValueTypes.INTEGER, position.z())
            ));
        });
        put(result, CommonProgramNodeIds.POSITION_TO_BLOCK, (_, _, inputs) -> data(
                "block",
                ProgramValueTypes.BLOCK_POSITION,
                ProgramBlockPosition.containing(worldPosition(inputs, "position"))
        ));
        put(result, CommonProgramNodeIds.BLOCK_TO_CENTER, (_, _, inputs) -> data(
                "position",
                ProgramValueTypes.WORLD_POSITION,
                blockPosition(inputs, "block").center()
        ));

        put(result, CommonProgramNodeIds.DIRECTION_CONSTANT,
                (ProgramVmContext _,
                 CommonProgramNodeCatalog.DirectionConfiguration configuration,
                 ProgramInputView _) -> data(
                        "direction",
                        ProgramValueTypes.DIRECTION,
                        new ProgramDirection(configuration.x(), configuration.y(), configuration.z())
                ));
        put(result, CommonProgramNodeIds.DIRECTION_CONSTRUCT, (_, _, inputs) -> data(
                "direction",
                ProgramValueTypes.DIRECTION,
                new ProgramDirection(
                        floatValue(inputs, "x"),
                        floatValue(inputs, "y"),
                        floatValue(inputs, "z")
                )
        ));
        put(result, CommonProgramNodeIds.DIRECTION_COMPONENTS, (_, _, inputs) -> {
            var direction = direction(inputs, "direction");
            return ProgramNodeStep.data(Map.of(
                    "x", value(ProgramValueTypes.FLOAT, direction.x()),
                    "y", value(ProgramValueTypes.FLOAT, direction.y()),
                    "z", value(ProgramValueTypes.FLOAT, direction.z())
            ));
        });
        put(result, CommonProgramNodeIds.DIRECTION_BETWEEN, (_, _, inputs) -> data(
                "direction",
                ProgramValueTypes.DIRECTION,
                ProgramDirection.between(
                        worldPosition(inputs, "from"),
                        worldPosition(inputs, "to")
                )
        ));
        put(result, CommonProgramNodeIds.DIRECTION_OPPOSITE, (_, _, inputs) -> data(
                "direction",
                ProgramValueTypes.DIRECTION,
                direction(inputs, "direction").opposite()
        ));
        put(result, CommonProgramNodeIds.DIRECTION_DOT, (_, _, inputs) -> data(
                "result",
                ProgramValueTypes.FLOAT,
                direction(inputs, "left").dot(direction(inputs, "right"))
        ));
        put(result, CommonProgramNodeIds.VEC3_OPERATION,
                (ProgramVmContext _, CommonProgramNodeCatalog.Vec3OperationConfiguration configuration,
                 ProgramInputView inputs) -> vec3Operation(inputs, configuration));
    }

    private static void registerQueries(Map<Identifier, ProgramNodeExecutor<?>> result) {
        put(result, CommonProgramNodeIds.CASTER, (context, _, _) -> data(
                "entity",
                ProgramValueTypes.ENTITY_REFERENCE,
                resolver(context).caster()
        ));
        put(result, CommonProgramNodeIds.DAMAGE_ATTACKER, (context, _, _) ->
                AbilityProgramTriggerRuntime.currentDamageAttacker(resolver(context).caster())
                        .map(entity -> data(
                                "entity",
                                ProgramValueTypes.ENTITY_REFERENCE,
                                entity
                        )).orElseGet(CommonProgramExecutors::emptyData));
        put(result, CommonProgramNodeIds.LOOK_TARGET,
                (ProgramVmContext context,
                 CommonProgramNodeCatalog.LookTargetConfiguration configuration,
                 ProgramInputView _) -> switch (configuration.targetType()) {
                    case ENTITY -> resolver(context).lookTarget()
                            .map(entity -> data(
                                    "entity",
                                    ProgramValueTypes.ENTITY_REFERENCE,
                                    entity
                            )).orElseGet(CommonProgramExecutors::emptyData);
                    case BLOCK -> resolver(context).lookBlockTarget()
                            .map(block -> data(
                                    "block",
                                    ProgramValueTypes.BLOCK_POSITION,
                                    block
                            )).orElseGet(CommonProgramExecutors::emptyData);
                });
        put(result, CommonProgramNodeIds.ENTITY_POSITION,
                (ProgramVmContext context,
                 CommonProgramNodeCatalog.EntityPositionConfiguration configuration,
                 ProgramInputView inputs) ->
                resolver(context).positionOf(
                                raw(inputs, "entity", ProgramValueTypes.ENTITY_REFERENCE),
                                configuration.anchor())
                        .map(position -> data(
                                "position",
                                ProgramValueTypes.WORLD_POSITION,
                                position
                        )).orElseGet(CommonProgramExecutors::emptyData));
        put(result, CommonProgramNodeIds.ENTITY_LOOK_DIRECTION, (context, _, inputs) ->
                resolver(context).lookDirectionOf(raw(
                                inputs,
                                "entity",
                                ProgramValueTypes.ENTITY_REFERENCE
                        )).map(direction -> data(
                                "direction",
                                ProgramValueTypes.DIRECTION,
                                direction
                        )).orElseGet(CommonProgramExecutors::emptyData));
        put(result, CommonProgramNodeIds.ENTITIES_AROUND, (context, _, inputs) -> {
            var radius = nonNegative(floatValue(inputs, "radius"), "radius");
            var entities = resolver(context).entitiesAround(worldPosition(inputs, "center"), radius);
            return data(
                    "entities",
                    ProgramValueTypes.ENTITY_SET,
                    canonical(Objects.requireNonNull(entities, "Resolved entity set"))
            );
        });
        put(result, CommonProgramNodeIds.RAYCAST_BLOCK, (context, _, inputs) -> {
            var range = nonNegative(floatValue(inputs, "range"), "range");
            return resolver(context).raycastBlock(
                            worldPosition(inputs, "origin"),
                            direction(inputs, "direction"),
                            range
                    ).map(block -> data("block", ProgramValueTypes.BLOCK_POSITION, block))
                    .orElseGet(CommonProgramExecutors::emptyData);
        });
        put(result, CommonProgramNodeIds.RAYCAST_ENTITY, (context, _, inputs) -> {
            var range = nonNegative(floatValue(inputs, "range"), "range");
            return resolver(context).raycastEntity(
                            worldPosition(inputs, "origin"),
                            direction(inputs, "direction"),
                            range
                    ).map(entity -> data("entity", ProgramValueTypes.ENTITY_REFERENCE, entity))
                    .orElseGet(CommonProgramExecutors::emptyData);
        });
        put(result, CommonProgramNodeIds.BLOCK_NORMAL,
                (ProgramVmContext context,
                 CommonProgramNodeCatalog.BlockNormalConfiguration configuration,
                 ProgramInputView inputs) -> {
                    var normal = switch (configuration.mode()) {
                        case VIEW -> resolver(context).blockNormalFromView(
                                raw(inputs, "entity", ProgramValueTypes.ENTITY_REFERENCE),
                                32.0
                        );
                        case POSITION_DIRECTION -> resolver(context).raycastBlockNormal(
                                worldPosition(inputs, "origin"),
                                direction(inputs, "direction"),
                                32.0
                        );
                    };
                    return normal.map(value -> data(
                                    "normal", ProgramValueTypes.DIRECTION, value))
                            .orElseGet(CommonProgramExecutors::emptyData);
                });
        put(result, CommonProgramNodeIds.BLOCK_VOLUME, (_, _, inputs) -> blockVolume(inputs));
    }

    private static void registerEquality(Map<Identifier, ProgramNodeExecutor<?>> result) {
        equality(result, CommonProgramNodeIds.ENTITY_EQUAL, ProgramValueTypes.ENTITY_REFERENCE);
        equality(result, CommonProgramNodeIds.WORLD_POSITION_EQUAL, ProgramValueTypes.WORLD_POSITION);
        equality(result, CommonProgramNodeIds.BLOCK_POSITION_EQUAL, ProgramValueTypes.BLOCK_POSITION);
        equality(result, CommonProgramNodeIds.DIRECTION_EQUAL, ProgramValueTypes.DIRECTION);
    }

    private static void registerFilters(Map<Identifier, ProgramNodeExecutor<?>> result) {
        put(result, CommonProgramNodeIds.FILTER_ENTITY_ALIVE, (_, _, inputs) -> filterEntities(
                inputs,
                entity -> !(entity instanceof Entity value)
                        || value.isAlive() && !value.isRemoved()
        ));
        put(result, CommonProgramNodeIds.FILTER_ENTITY_DISTANCE, (context, _, inputs) -> {
            var center = worldPosition(inputs, "center");
            var radius = nonNegative(floatValue(inputs, "radius"), "radius");
            return filterEntities(inputs, entity -> resolver(context).positionOf(entity)
                    .filter(position -> position.dimension().equals(center.dimension()))
                    .map(position -> position.distanceTo(center) <= radius)
                    .orElse(false));
        });
        put(result, CommonProgramNodeIds.FILTER_ENTITY_ALLIED_TO, (_, _, inputs) -> {
            var reference = raw(inputs, "reference", ProgramValueTypes.ENTITY_REFERENCE);
            return filterEntities(inputs, entity -> entity instanceof Entity value
                    && reference instanceof Entity target
                    && DarkmatterTargeting.areAllied(value, target));
        });
        put(result, CommonProgramNodeIds.FILTER_ENTITY_HOSTILE_TO, (context, _, inputs) -> {
            var reference = raw(inputs, "reference", ProgramValueTypes.ENTITY_REFERENCE);
            var damageAttacker = AbilityProgramTriggerRuntime
                    .currentDamageAttacker(reference).orElse(null);
            return filterEntities(inputs, entity -> entity instanceof LivingEntity value
                    && reference instanceof LivingEntity target
                    && isHostileTo(value, target, damageAttacker));
        });
        put(result, CommonProgramNodeIds.FILTER_ENTITY_TARGETED_BY, (_, _, inputs) -> {
            var target = raw(inputs, "target", ProgramValueTypes.ENTITY_REFERENCE);
            return filterEntities(inputs, entity -> entity instanceof Mob mob
                    && mob.getTarget() == target);
        });
        put(result, CommonProgramNodeIds.FILTER_ENTITY_LAST_DAMAGED_BY, (_, _, inputs) -> {
            var attacker = raw(inputs, "attacker", ProgramValueTypes.ENTITY_REFERENCE);
            return filterEntities(inputs, entity -> entity instanceof LivingEntity living
                    && living.getLastHurtByMob() == attacker);
        });
        put(result, CommonProgramNodeIds.FILTER_ENTITY_TYPE,
                (ProgramVmContext _, CommonProgramNodeCatalog.EntityKindConfiguration configuration,
                 ProgramInputView inputs) -> filterEntities(
                        inputs,
                        entity -> matchesEntityKind(entity, configuration.type())
                ));
        put(result, CommonProgramNodeIds.FILTER_ENTITY_EXACT,
                (ProgramVmContext _, CommonProgramNodeCatalog.ExactFilterConfiguration configuration,
                 ProgramInputView inputs) -> filterEntities(
                        inputs, entity -> matchesEntitySelector(entity, configuration.values())));
        put(result, CommonProgramNodeIds.FILTER_ENTITY_HEALTH_AT_LEAST, (_, _, inputs) -> {
            var percent = healthPercentThreshold(inputs);
            return filterEntities(inputs, entity -> healthPercent(entity) >= percent);
        });
        put(result, CommonProgramNodeIds.FILTER_ENTITY_HEALTH_AT_MOST, (_, _, inputs) -> {
            var percent = healthPercentThreshold(inputs);
            return filterEntities(inputs, entity -> healthPercent(entity) <= percent);
        });
        put(result, CommonProgramNodeIds.FILTER_ENTITY_MAX_HEALTH_AT_LEAST, (_, _, inputs) -> {
            var health = nonNegative(floatValue(inputs, "health"), "health");
            return filterEntities(inputs, entity -> entity instanceof LivingEntity living
                    && living.getMaxHealth() >= health);
        });
        put(result, CommonProgramNodeIds.FILTER_ENTITY_MAX_HEALTH_AT_MOST, (_, _, inputs) -> {
            var health = nonNegative(floatValue(inputs, "health"), "health");
            return filterEntities(inputs, entity -> entity instanceof LivingEntity living
                    && living.getMaxHealth() <= health);
        });
        put(result, CommonProgramNodeIds.FILTER_ENTITY_HAS_TARGET, (_, _, inputs) ->
                filterEntities(inputs, entity -> entity instanceof Mob mob
                        && mob.getTarget() != null));
        put(result, CommonProgramNodeIds.FILTER_ENTITY_VISIBLE_FROM, (_, _, inputs) -> {
            var observer = raw(inputs, "observer", ProgramValueTypes.ENTITY_REFERENCE);
            return filterEntities(inputs, entity -> observer instanceof LivingEntity living
                    && entity instanceof Entity target
                    && living.level() == target.level()
                    && living.hasLineOfSight(target));
        });
        put(result, CommonProgramNodeIds.FILTER_BLOCK_EXACT,
                (ProgramVmContext context,
                 CommonProgramNodeCatalog.ExactFilterConfiguration configuration,
                 ProgramInputView inputs) -> exactBlockFilter(context, inputs, configuration));
    }

    private static ProgramNodeStep filterEntities(
            ProgramInputView inputs,
            Predicate<Object> predicate
    ) {
        return data(
                "entities",
                ProgramValueTypes.ENTITY_SET,
                canonical(collection(
                        inputs,
                        "entities",
                        CommonProgramNodeCatalog.CollectionDomain.ENTITY
                ).stream().filter(predicate).toList())
        );
    }

    private static boolean isHostileTo(
            LivingEntity entity,
            LivingEntity target,
            Object damageAttacker
    ) {
        return entity != target
                && !DarkmatterTargeting.areAllied(entity, target)
                && (entity == damageAttacker
                || entity instanceof Mob mob && (mob.getTarget() == target || mob.canAttack(target))
                || entity.getLastHurtByMob() == target);
    }

    private static boolean matchesEntityKind(
            Object entity,
            CommonProgramNodeCatalog.EntityKind type
    ) {
        return switch (type) {
            case ANY -> entity instanceof Entity;
            case LIVING -> entity instanceof LivingEntity;
            case PLAYER -> entity instanceof Player;
            case MOB -> entity instanceof Mob;
            case HOSTILE -> entity instanceof Enemy;
            case ANIMAL -> entity instanceof Animal;
            case FRIENDLY -> entity instanceof Mob
                    && !(entity instanceof Enemy)
                    && !(entity instanceof Animal);
            case PROJECTILE -> entity instanceof Projectile;
            case ITEM -> entity instanceof ItemEntity;
        };
    }

    private static void registerRandomCollectionValues(
            Map<Identifier, ProgramNodeExecutor<?>> result
    ) {
        randomCollectionValue(result, CommonProgramNodeIds.RANDOM_ENTITY,
                CommonProgramNodeCatalog.CollectionDomain.ENTITY, "entities", "entity");
        put(result, CommonProgramNodeIds.NEAREST_ENTITY_TO_POSITION, (context, _, inputs) -> {
            var origin = worldPosition(inputs, "position");
            Object nearest = null;
            var nearestDistance = Double.POSITIVE_INFINITY;
            for (var entity : collection(
                    inputs, "entities", CommonProgramNodeCatalog.CollectionDomain.ENTITY)) {
                var position = resolver(context).positionOf(entity).orElse(null);
                if (position == null || !origin.dimension().equals(position.dimension())) continue;
                var distance = squaredDistance(origin, position);
                if (distance < nearestDistance) {
                    nearest = entity;
                    nearestDistance = distance;
                }
            }
            return nearest == null
                    ? emptyData()
                    : data("entity", ProgramValueTypes.ENTITY_REFERENCE, nearest);
        });
        randomCollectionValue(result, CommonProgramNodeIds.RANDOM_WORLD_POSITION,
                CommonProgramNodeCatalog.CollectionDomain.WORLD_POSITION, "positions", "position");
        randomCollectionValue(result, CommonProgramNodeIds.RANDOM_BLOCK_POSITION,
                CommonProgramNodeCatalog.CollectionDomain.BLOCK_POSITION, "blocks", "block");
        randomCollectionValue(result, CommonProgramNodeIds.RANDOM_DIRECTION,
                CommonProgramNodeCatalog.CollectionDomain.DIRECTION, "directions", "direction");
    }

    private static void randomCollectionValue(
            Map<Identifier, ProgramNodeExecutor<?>> result,
            Identifier id,
            CommonProgramNodeCatalog.CollectionDomain domain,
            String input,
            String output
    ) {
        put(result, id, (_, _, inputs) -> {
            var values = collection(inputs, input, domain);
            if (values.isEmpty()) return emptyData();
            return data(output, domain.elementType(),
                    values.get(ThreadLocalRandom.current().nextInt(values.size())));
        });
    }

    private static void registerAdvancedValues(
            Map<Identifier, ProgramNodeExecutor<?>> result
    ) {
        put(result, CommonProgramNodeIds.RANDOM_NUMBER,
                (ProgramVmContext _, CommonProgramNodeCatalog.RandomNumberConfiguration configuration,
                 ProgramInputView _) -> data(
                        "value", configuration.kind().type(), randomNumber(configuration)));
        put(result, CommonProgramNodeIds.SORT_POINTS_BY_DISTANCE,
                (ProgramVmContext context,
                 CommonProgramNodeCatalog.DistanceSortConfiguration configuration,
                 ProgramInputView inputs) -> {
                    var origin = worldPosition(inputs, "origin");
                    var domain = switch (configuration.kind()) {
                        case ENTITY -> CommonProgramNodeCatalog.CollectionDomain.ENTITY;
                        case WORLD_POSITION -> CommonProgramNodeCatalog.CollectionDomain.WORLD_POSITION;
                        case BLOCK_POSITION -> CommonProgramNodeCatalog.CollectionDomain.BLOCK_POSITION;
                    };
                    var values = new java.util.ArrayList<>(collection(inputs, "values", domain));
                    Comparator<Object> comparator = Comparator.comparingDouble(
                            value -> pointDistanceSquared(context, origin, value));
                    if (configuration.order().reversed()) comparator = comparator.reversed();
                    values.sort(comparator);
                    return data("values", configuration.kind().collectionType(), List.copyOf(values));
                });
    }

    private static ProgramNodeStep blockVolume(ProgramInputView inputs) {
        var first = blockPosition(inputs, "first");
        var second = blockPosition(inputs, "second");
        if (!first.dimension().equals(second.dimension())) {
            throw new IllegalArgumentException("Block-volume corners are in different dimensions");
        }
        var minX = Math.min(first.x(), second.x());
        var minY = Math.min(first.y(), second.y());
        var minZ = Math.min(first.z(), second.z());
        var maxX = Math.max(first.x(), second.x());
        var maxY = Math.max(first.y(), second.y());
        var maxZ = Math.max(first.z(), second.z());
        var sizeX = (long) maxX - minX + 1L;
        var sizeY = (long) maxY - minY + 1L;
        var sizeZ = (long) maxZ - minZ + 1L;
        if (sizeX > 32_768L || sizeY > 32_768L || sizeZ > 32_768L
                || sizeX * sizeY > 32_768L || sizeX * sizeY * sizeZ > 32_768L) {
            throw new IllegalArgumentException("Block volume exceeds 32768 positions");
        }
        var blocks = new java.util.ArrayList<ProgramBlockPosition>((int) (sizeX * sizeY * sizeZ));
        for (long x = minX; x <= maxX; x++) {
            for (long y = minY; y <= maxY; y++) {
                for (long z = minZ; z <= maxZ; z++) {
                    blocks.add(new ProgramBlockPosition(
                            first.dimension(), (int) x, (int) y, (int) z));
                }
            }
        }
        return data("blocks", ProgramValueTypes.BLOCK_POSITION_SET, List.copyOf(blocks));
    }

    private static ProgramNodeStep exactBlockFilter(
            ProgramVmContext context,
            ProgramInputView inputs,
            CommonProgramNodeCatalog.ExactFilterConfiguration configuration
    ) {
        var caster = resolver(context).caster();
        if (!(caster instanceof ServerPlayer player)) {
            throw new IllegalStateException("Exact block filtering requires a server player");
        }
        var blocks = collection(inputs, "blocks",
                CommonProgramNodeCatalog.CollectionDomain.BLOCK_POSITION).stream()
                .map(ProgramBlockPosition.class::cast)
                .filter(position -> matchesBlockSelector(player, position, configuration.values()))
                .toList();
        return data("blocks", ProgramValueTypes.BLOCK_POSITION_SET, blocks);
    }

    private static ProgramNodeStep vec3Operation(
            ProgramInputView inputs,
            CommonProgramNodeCatalog.Vec3OperationConfiguration configuration
    ) {
        if (configuration.kind() == CommonProgramNodeCatalog.Vec3Kind.DIRECTION) {
            var left = direction(inputs, "left");
            var right = direction(inputs, "right");
            return switch (configuration.operator()) {
                case DOT -> data("result", ProgramValueTypes.FLOAT, left.dot(right));
                case CROSS -> data("result", ProgramValueTypes.DIRECTION, new ProgramDirection(
                        left.y() * right.z() - left.z() * right.y(),
                        left.z() * right.x() - left.x() * right.z(),
                        left.x() * right.y() - left.y() * right.x()));
                case ADD -> data("result", ProgramValueTypes.DIRECTION, new ProgramDirection(
                        left.x() + right.x(), left.y() + right.y(), left.z() + right.z()));
            };
        }
        var left = worldPosition(inputs, "left");
        var right = worldPosition(inputs, "right");
        if (!left.dimension().equals(right.dimension())) {
            throw new IllegalArgumentException("Vec3 positions are in different dimensions");
        }
        return switch (configuration.operator()) {
            case DOT -> data("result", ProgramValueTypes.FLOAT,
                    left.x() * right.x() + left.y() * right.y() + left.z() * right.z());
            case CROSS -> data("result", ProgramValueTypes.WORLD_POSITION,
                    new ProgramWorldPosition(left.dimension(),
                            left.y() * right.z() - left.z() * right.y(),
                            left.z() * right.x() - left.x() * right.z(),
                            left.x() * right.y() - left.y() * right.x()));
            case ADD -> data("result", ProgramValueTypes.WORLD_POSITION,
                    new ProgramWorldPosition(left.dimension(),
                            left.x() + right.x(), left.y() + right.y(), left.z() + right.z()));
        };
    }

    private static Object randomNumber(
            CommonProgramNodeCatalog.RandomNumberConfiguration configuration
    ) {
        return switch (configuration.kind()) {
            case INTEGER -> {
                var lower = Integer.parseInt(configuration.lower());
                var upper = Integer.parseInt(configuration.upper());
                yield lower == upper ? lower
                        : (int) ThreadLocalRandom.current().nextLong(lower, (long) upper + 1L);
            }
            case BIG_INTEGER -> randomBigInteger(
                    new BigInteger(configuration.lower()), new BigInteger(configuration.upper()));
            case FLOAT -> {
                var lower = Double.parseDouble(configuration.lower());
                var upper = Double.parseDouble(configuration.upper());
                yield lower == upper ? lower : ThreadLocalRandom.current().nextDouble(lower, upper);
            }
        };
    }

    private static BigInteger randomBigInteger(BigInteger lower, BigInteger upper) {
        var range = upper.subtract(lower).add(BigInteger.ONE);
        if (range.equals(BigInteger.ONE)) return lower;
        BigInteger offset;
        do {
            offset = new BigInteger(range.bitLength(), ThreadLocalRandom.current());
        } while (offset.compareTo(range) >= 0);
        return lower.add(offset);
    }

    private static double pointDistanceSquared(
            ProgramVmContext context,
            ProgramWorldPosition origin,
            Object value
    ) {
        ProgramWorldPosition position;
        if (value instanceof ProgramWorldPosition world) {
            position = world;
        } else if (value instanceof ProgramBlockPosition block) {
            position = block.center();
        } else {
            position = resolver(context).positionOf(value).orElseThrow(() ->
                    new IllegalArgumentException("Entity position is unavailable"));
        }
        if (!origin.dimension().equals(position.dimension())) {
            throw new IllegalArgumentException("Distance-sort point is in another dimension");
        }
        return squaredDistance(origin, position);
    }

    private static boolean matchesEntitySelector(Object value, List<String> selectors) {
        if (!(value instanceof Entity entity)) return false;
        var typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        for (var selector : selectors) {
            if (selector.startsWith("#")) {
                var id = Identifier.tryParse(selector.substring(1));
                if (id != null && BuiltInRegistries.ENTITY_TYPE
                        .wrapAsHolder(entity.getType())
                        .is(TagKey.create(Registries.ENTITY_TYPE, id))) return true;
                continue;
            }
            var id = Identifier.tryParse(selector);
            if (id != null && id.equals(typeId)) return true;
            if (selector.equals(entity.getName().getString())) return true;
        }
        return false;
    }

    private static boolean matchesBlockSelector(
            ServerPlayer player,
            ProgramBlockPosition position,
            List<String> selectors
    ) {
        if (!position.dimension().equals(player.level().dimension().identifier())) return false;
        var blockPosition = new BlockPos(position.x(), position.y(), position.z());
        if (!player.level().hasChunkAt(blockPosition)) return false;
        var state = player.level().getBlockState(blockPosition);
        var blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        for (var selector : selectors) {
            if (selector.startsWith("#")) {
                var id = Identifier.tryParse(selector.substring(1));
                if (id != null && state.is(TagKey.create(Registries.BLOCK, id))) return true;
                continue;
            }
            var id = Identifier.tryParse(selector);
            if (id != null && id.equals(blockId)) return true;
            if (selector.equals(state.getBlock().getName().getString())) return true;
        }
        return false;
    }

    private static double healthPercentThreshold(ProgramInputView inputs) {
        var percent = floatValue(inputs, "percent");
        if (percent < 0.0 || percent > 100.0) {
            throw new IllegalArgumentException("Health percent must be between 0 and 100");
        }
        return percent;
    }

    private static double healthPercent(Object entity) {
        if (!(entity instanceof LivingEntity living) || living.getMaxHealth() <= 0.0f) {
            return Double.NaN;
        }
        return living.getHealth() / living.getMaxHealth() * 100.0;
    }

    private static void registerCollection(
            Map<Identifier, ProgramNodeExecutor<?>> result,
            CommonProgramNodeCatalog.CollectionDomain domain
    ) {
        put(result, domain.id("empty"),
                (ProgramVmContext _,
                 CommonProgramNodeCatalog.CollectionBuilderConfiguration configuration,
                 ProgramInputView inputs) -> {
                    var values = new ArrayList<Object>(configuration.inputs());
                    for (var index = 1; index <= configuration.inputs(); index++) {
                        values.add(raw(inputs, "value_" + index, domain.elementType()));
                    }
                    return data("values", domain.collectionType(), canonical(values));
                });
        put(result, domain.id("singleton"), (_, _, inputs) -> data(
                "values",
                domain.collectionType(),
                List.of(raw(inputs, "value", domain.elementType()))
        ));
        put(result, domain.id("union"), (_, _, inputs) -> {
            var values = new LinkedHashSet<Object>();
            values.addAll(collection(inputs, "left", domain));
            values.addAll(collection(inputs, "right", domain));
            return data("values", domain.collectionType(), List.copyOf(values));
        });
        put(result, domain.id("intersection"), (_, _, inputs) -> {
            var right = new HashSet<>(collection(inputs, "right", domain));
            var values = collection(inputs, "left", domain).stream()
                    .filter(right::contains)
                    .toList();
            return data("values", domain.collectionType(), canonical(values));
        });
        put(result, domain.id("difference"), (_, _, inputs) -> {
            var right = new HashSet<>(collection(inputs, "right", domain));
            var values = collection(inputs, "left", domain).stream()
                    .filter(value -> !right.contains(value))
                    .toList();
            return data("values", domain.collectionType(), canonical(values));
        });
        put(result, domain.id("contains"), (_, _, inputs) -> data(
                "result",
                ProgramValueTypes.BOOLEAN,
                collection(inputs, "values", domain).contains(raw(
                        inputs,
                        "value",
                        domain.elementType()
                ))
        ));
        put(result, domain.id("size"), (_, _, inputs) -> data(
                "size",
                ProgramValueTypes.INTEGER,
                collection(inputs, "values", domain).size()
        ));
        put(result, domain.id("get"), (_, _, inputs) -> {
            var values = collection(inputs, "values", domain);
            var index = integer(inputs, "index") - 1;
            if (index < 0 || index >= values.size()) {
                throw new IllegalArgumentException("Collection position is out of bounds");
            }
            return data("value", domain.elementType(), values.get(index));
        });
        put(result, domain.id("foreach"), (context, _, inputs) -> {
            var key = "foreach:" + context.nodeId();
            var state = context.executorState(key)
                    .map(IterationState.class::cast)
                    .orElseGet(() -> new IterationState(
                            collection(inputs, "values", domain),
                            0
                    ));
            if (state.index >= state.values.size()) {
                context.removeExecutorState(key);
                return ProgramNodeStep.next("done");
            }
            context.setExecutorState(key, new IterationState(state.values, state.index + 1));
            return ProgramNodeStep.call("body", Map.of(
                    "value",
                    value(domain.elementType(), state.values.get(state.index))
            ));
        });
    }

    private static void integerBinary(
            Map<Identifier, ProgramNodeExecutor<?>> result,
            Identifier id,
            IntBinaryOperator operation
    ) {
        put(result, id, (_, _, inputs) -> data(
                "result",
                ProgramValueTypes.INTEGER,
                operation.applyAsInt(integer(inputs, "left"), integer(inputs, "right"))
        ));
    }

    private static void integerComparison(
            Map<Identifier, ProgramNodeExecutor<?>> result,
            Identifier id,
            IntPredicate predicate
    ) {
        put(result, id, (_, _, inputs) -> data(
                "result",
                ProgramValueTypes.BOOLEAN,
                predicate.test(Integer.compare(integer(inputs, "left"), integer(inputs, "right")))
        ));
    }

    private static void bigIntegerBinary(
            Map<Identifier, ProgramNodeExecutor<?>> result,
            Identifier id,
            BiFunction<BigInteger, BigInteger, BigInteger> operation
    ) {
        put(result, id, (_, _, inputs) -> data(
                "result",
                ProgramValueTypes.BIG_INTEGER,
                operation.apply(bigInteger(inputs, "left"), bigInteger(inputs, "right"))
        ));
    }

    private static void bigIntegerComparison(
            Map<Identifier, ProgramNodeExecutor<?>> result,
            Identifier id,
            IntPredicate predicate
    ) {
        put(result, id, (_, _, inputs) -> data(
                "result",
                ProgramValueTypes.BOOLEAN,
                predicate.test(bigInteger(inputs, "left").compareTo(bigInteger(inputs, "right")))
        ));
    }

    private static void floatBinary(
            Map<Identifier, ProgramNodeExecutor<?>> result,
            Identifier id,
            DoubleBinaryOperator operation
    ) {
        put(result, id, (_, _, inputs) -> data(
                "result",
                ProgramValueTypes.FLOAT,
                finite(operation.applyAsDouble(
                        floatValue(inputs, "left"),
                        floatValue(inputs, "right")
                ))
        ));
    }

    private static void floatComparison(
            Map<Identifier, ProgramNodeExecutor<?>> result,
            Identifier id,
            IntPredicate predicate
    ) {
        put(result, id, (_, _, inputs) -> data(
                "result",
                ProgramValueTypes.BOOLEAN,
                predicate.test(Double.compare(
                        floatValue(inputs, "left"),
                        floatValue(inputs, "right")
                ))
        ));
    }

    private static void booleanBinary(
            Map<Identifier, ProgramNodeExecutor<?>> result,
            Identifier id,
            BooleanBinaryOperator operation
    ) {
        put(result, id, (_, _, inputs) -> data(
                "result",
                ProgramValueTypes.BOOLEAN,
                operation.apply(
                        booleanValue(inputs, "left"),
                        booleanValue(inputs, "right")
                )
        ));
    }

    private static void equality(
            Map<Identifier, ProgramNodeExecutor<?>> result,
            Identifier id,
            ProgramValueType type
    ) {
        put(result, id, (_, _, inputs) -> data(
                "result",
                ProgramValueTypes.BOOLEAN,
                Objects.equals(raw(inputs, "left", type), raw(inputs, "right", type))
        ));
    }

    private static ProgramTargetResolver resolver(ProgramVmContext context) {
        var direct = context.attachment(ProgramTargetResolver.class);
        if (direct.isPresent()) return direct.get();
        return context.attachment(ProgramExecutionFrame.class)
                .flatMap(frame -> frame.environment(ProgramTargetResolver.class))
                .orElseThrow(() -> new IllegalStateException("No program target resolver is available"));
    }

    private static boolean booleanValue(ProgramInputView inputs, String port) {
        return (Boolean) inputs.require(port, ProgramValueTypes.BOOLEAN).value();
    }

    private static int compareNumeric(
            ProgramInputView inputs,
            CommonProgramNodeCatalog.NumericKind kind
    ) {
        return switch (kind) {
            case INTEGER -> Integer.compare(integer(inputs, "left"), integer(inputs, "right"));
            case BIG_INTEGER -> bigInteger(inputs, "left").compareTo(bigInteger(inputs, "right"));
            case FLOAT -> Double.compare(floatValue(inputs, "left"), floatValue(inputs, "right"));
        };
    }

    private static ProgramNodeStep numericArithmetic(
            ProgramInputView inputs,
            CommonProgramNodeCatalog.NumericArithmeticConfiguration configuration
    ) {
        return switch (configuration.kind()) {
            case INTEGER -> data(
                    "result",
                    ProgramValueTypes.INTEGER,
                    switch (configuration.operator()) {
                        case ADD -> Math.addExact(integer(inputs, "left"), integer(inputs, "right"));
                        case SUBTRACT -> Math.subtractExact(
                                integer(inputs, "left"), integer(inputs, "right"));
                        case MULTIPLY -> Math.multiplyExact(
                                integer(inputs, "left"), integer(inputs, "right"));
                        case DIVIDE -> integer(inputs, "left") / integer(inputs, "right");
                        case MODULO -> integer(inputs, "left") % integer(inputs, "right");
                        case ABSOLUTE -> {
                            var value = integer(inputs, "value");
                            if (value == Integer.MIN_VALUE) {
                                throw new ArithmeticException("Integer absolute overflow");
                            }
                            yield Math.abs(value);
                        }
                    }
            );
            case BIG_INTEGER -> data(
                    "result",
                    ProgramValueTypes.BIG_INTEGER,
                    switch (configuration.operator()) {
                        case ADD -> bigInteger(inputs, "left").add(bigInteger(inputs, "right"));
                        case SUBTRACT -> bigInteger(inputs, "left").subtract(bigInteger(inputs, "right"));
                        case MULTIPLY -> bigInteger(inputs, "left").multiply(bigInteger(inputs, "right"));
                        case DIVIDE -> bigInteger(inputs, "left").divide(bigInteger(inputs, "right"));
                        case MODULO -> bigInteger(inputs, "left").remainder(bigInteger(inputs, "right"));
                        case ABSOLUTE -> bigInteger(inputs, "value").abs();
                    }
            );
            case FLOAT -> data(
                    "result",
                    ProgramValueTypes.FLOAT,
                    finite(switch (configuration.operator()) {
                        case ADD -> floatValue(inputs, "left") + floatValue(inputs, "right");
                        case SUBTRACT -> floatValue(inputs, "left") - floatValue(inputs, "right");
                        case MULTIPLY -> floatValue(inputs, "left") * floatValue(inputs, "right");
                        case DIVIDE -> floatValue(inputs, "left") / floatValue(inputs, "right");
                        case MODULO -> floatValue(inputs, "left") % floatValue(inputs, "right");
                        case ABSOLUTE -> Math.abs(floatValue(inputs, "value"));
                    })
            );
        };
    }

    private static int integer(ProgramInputView inputs, String port) {
        return (Integer) inputs.require(port, ProgramValueTypes.INTEGER).value();
    }

    private static BigInteger bigInteger(ProgramInputView inputs, String port) {
        return (BigInteger) inputs.require(port, ProgramValueTypes.BIG_INTEGER).value();
    }

    private static double floatValue(ProgramInputView inputs, String port) {
        var value = inputs.requireCompatible(port, ProgramValueTypes.FLOAT);
        if (!(value.value() instanceof Number number)) {
            throw new IllegalArgumentException("Float-compatible input is not numeric");
        }
        return finite(number.doubleValue());
    }

    private static ProgramWorldPosition worldPosition(ProgramInputView inputs, String port) {
        return (ProgramWorldPosition) inputs.require(port, ProgramValueTypes.WORLD_POSITION).value();
    }

    private static ProgramBlockPosition blockPosition(ProgramInputView inputs, String port) {
        return (ProgramBlockPosition) inputs.require(port, ProgramValueTypes.BLOCK_POSITION).value();
    }

    private static ProgramDirection direction(ProgramInputView inputs, String port) {
        return (ProgramDirection) inputs.require(port, ProgramValueTypes.DIRECTION).value();
    }

    private static Object raw(
            ProgramInputView inputs,
            String port,
            ProgramValueType type
    ) {
        return inputs.requireCompatible(port, type).value();
    }

    private static List<?> collection(
            ProgramInputView inputs,
            String port,
            CommonProgramNodeCatalog.CollectionDomain domain
    ) {
        var raw = inputs.requireCompatible(port, domain.collectionType()).value();
        if (!(raw instanceof List<?> list)) {
            throw new IllegalArgumentException("Program collection value is not a list");
        }
        var canonical = canonical(list);
        for (var value : canonical) validateElement(domain, value);
        return canonical;
    }

    private static void validateElement(
            CommonProgramNodeCatalog.CollectionDomain domain,
            Object value
    ) {
        var valid = switch (domain) {
            case ENTITY -> true;
            case WORLD_POSITION -> value instanceof ProgramWorldPosition;
            case BLOCK_POSITION -> value instanceof ProgramBlockPosition;
            case DIRECTION -> value instanceof ProgramDirection;
        };
        if (!valid) throw new IllegalArgumentException("Program collection contains a foreign value");
    }

    private static List<?> canonical(List<?> values) {
        return List.copyOf(new LinkedHashSet<>(values));
    }

    private static ProgramValue<?> coerce(
            ProgramValue<?> source,
            ProgramValueType target
    ) {
        if (!ProgramValueTypes.canConnect(source.type(), target)) {
            throw new IllegalArgumentException("Cannot store incompatible program value");
        }
        Object raw = source.value();
        if (source.type().equals(ProgramValueTypes.INTEGER) && target.equals(ProgramValueTypes.FLOAT)) {
            raw = ((Integer) raw).doubleValue();
        }
        if (raw instanceof List<?> list) raw = List.copyOf(list);
        return value(target, raw);
    }

    private static ProgramNodeStep data(
            String port,
            ProgramValueType type,
            Object raw
    ) {
        return data(port, value(type, raw));
    }

    private static ProgramNodeStep data(String port, ProgramValue<?> value) {
        return ProgramNodeStep.data(Map.of(port, value));
    }

    private static ProgramNodeStep emptyData() {
        return ProgramNodeStep.data(Map.of());
    }

    private static ProgramValue<?> value(ProgramValueType type, Object raw) {
        return new ProgramValue<>(type, raw);
    }

    private static double finite(double value) {
        if (!Double.isFinite(value)) throw new ArithmeticException("Program float result is not finite");
        return value;
    }

    private static double squaredDistance(
            ProgramWorldPosition left,
            ProgramWorldPosition right
    ) {
        var x = left.x() - right.x();
        var y = left.y() - right.y();
        var z = left.z() - right.z();
        return x * x + y * y + z * z;
    }

    private static double nonNegative(double value, String name) {
        if (value < 0.0) throw new IllegalArgumentException(name + " cannot be negative");
        return value;
    }

    private static <C> void put(
            Map<Identifier, ProgramNodeExecutor<?>> result,
            Identifier id,
            ProgramNodeExecutor<C> executor
    ) {
        if (result.putIfAbsent(id, executor) != null) {
            throw new IllegalStateException("Duplicate common program executor " + id);
        }
    }

    private record IterationState(List<?> values, int index) {
        private IterationState {
            values = List.copyOf(values);
        }
    }

    @FunctionalInterface
    private interface IntPredicate {
        boolean test(int value);
    }

    @FunctionalInterface
    private interface BooleanBinaryOperator {
        boolean apply(boolean left, boolean right);
    }
}
