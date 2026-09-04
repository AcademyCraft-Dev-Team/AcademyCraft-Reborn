package org.academy.internal.common.ability.aeromanip.program;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.program.*;
import org.academy.internal.common.ability.AbilityCategoryNames;
import org.academy.internal.common.ability.program.ProgramNodeLookup;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Strongly typed Aeromanip target and action schemas.
 */
public final class AeromanipProgramNodeCatalog implements ProgramNodeLookup {
    public static final Identifier AEROMANIP = AcademyCraft.academy(AbilityCategoryNames.AEROMANIP);
    public static final AeromanipProgramNodeCatalog INSTANCE = new AeromanipProgramNodeCatalog();

    private final Map<Identifier, ProgramNodeType<?>> types;

    private AeromanipProgramNodeCatalog() {
        var result = new HashMap<Identifier, ProgramNodeType<?>>();
        put(result, AeromanipProgramNodeIds.CASTER, unitType(
                querySchema(), ProgramNodeRole.QUERY, ProgramNodePurity.WORLD_QUERY,
                categoryScope()));
        put(result, AeromanipProgramNodeIds.LOOK_TARGET, unitType(
                querySchema(), ProgramNodeRole.QUERY, ProgramNodePurity.WORLD_QUERY,
                categoryScope()));
        put(result, AeromanipProgramNodeIds.AIRFLOW_PUSH, powerType(
                airflowPushSchema(), AeromanipProgramCapabilities.AIRFLOW_PUSH));
        put(result, AeromanipProgramNodeIds.CONVERGING_AIRFLOW, fixedType(
                ConvergingAirflowConfiguration.CODEC,
                convergingAirflowSchema(), AeromanipProgramCapabilities.CONVERGING_AIRFLOW));
        put(result, AeromanipProgramNodeIds.LAMINAR_CUT, dynamicType(
                LaminarCutConfiguration.CODEC,
                configuration -> laminarCutSchema(configuration.planeMode()),
                ProgramNodeRole.ACTION,
                ProgramNodePurity.ACTION,
                capabilityScope(AeromanipProgramCapabilities.LAMINAR_CUT)));
        put(result, AeromanipProgramNodeIds.PLACE_TEMPORARY_JET_NOZZLE, dynamicType(
                TemporaryNozzleConfiguration.CODEC,
                configuration -> temporaryNozzleSchema(configuration.targetType()),
                ProgramNodeRole.ACTION,
                ProgramNodePurity.ACTION,
                capabilityScope(AeromanipProgramCapabilities.HIGH_SPEED_JET)));
        put(result, AeromanipProgramNodeIds.FIRE_JETS, fixedType(
                JetActivationConfiguration.CODEC,
                fireJetsSchema(), AeromanipProgramCapabilities.HIGH_SPEED_JET));
        put(result, AeromanipProgramNodeIds.OWNED_JET_NOZZLE_COUNT, unitType(
                ownedJetNozzleCountSchema(),
                ProgramNodeRole.QUERY,
                ProgramNodePurity.WORLD_QUERY,
                capabilityScope(AeromanipProgramCapabilities.HIGH_SPEED_JET)));
        put(result, AeromanipProgramNodeIds.LAUNCH_BLOCK_STRUCTURE, fixedType(
                BlockStructureLaunchConfiguration.CODEC,
                launchBlockStructureSchema(),
                AeromanipProgramCapabilities.HIGH_SPEED_JET));
        types = Map.copyOf(result);
    }

    @Override
    public ProgramNodeType<?> find(Identifier id) {
        return types.get(id);
    }

    public Map<Identifier, ProgramNodeType<?>> types() {
        return types;
    }

    private static ProgramNodeSchema querySchema() {
        return new ProgramNodeSchema(
                List.of(),
                List.of(ProgramPortDefinition.output(
                        "entity", ProgramValueTypes.ENTITY_REFERENCE))
        );
    }

    private static ProgramNodeSchema airflowPushSchema() {
        return new ProgramNodeSchema(
                List.of(
                        ProgramPortDefinition.requiredInput("flow", ProgramValueTypes.FLOW),
                        ProgramPortDefinition.requiredInput(
                                "entity", ProgramValueTypes.ENTITY_REFERENCE),
                        ProgramPortDefinition.requiredInput(
                                "direction", ProgramValueTypes.DIRECTION)
                ),
                List.of(ProgramPortDefinition.output("flow", ProgramValueTypes.FLOW))
        );
    }

    private static ProgramNodeSchema laminarCutSchema(BladePlaneMode planeMode) {
        var inputs = new java.util.ArrayList<ProgramPortDefinition>();
        inputs.add(ProgramPortDefinition.requiredInput("flow", ProgramValueTypes.FLOW));
        inputs.add(ProgramPortDefinition.optionalInput(
                "origin", ProgramValueTypes.WORLD_POSITION));
        inputs.add(ProgramPortDefinition.requiredInput(
                "direction", ProgramValueTypes.DIRECTION));
        if (planeMode == BladePlaneMode.DIRECTION) {
            inputs.add(ProgramPortDefinition.requiredInput(
                    "plane_direction", ProgramValueTypes.DIRECTION));
        }
        return new ProgramNodeSchema(
                List.copyOf(inputs),
                List.of(ProgramPortDefinition.output("flow", ProgramValueTypes.FLOW))
        );
    }

    private static ProgramNodeSchema convergingAirflowSchema() {
        return new ProgramNodeSchema(
                List.of(
                        ProgramPortDefinition.requiredInput("flow", ProgramValueTypes.FLOW),
                        ProgramPortDefinition.requiredInput("entities", ProgramValueTypes.ENTITY_SET),
                        ProgramPortDefinition.requiredInput("center", ProgramValueTypes.WORLD_POSITION)
                ),
                List.of(ProgramPortDefinition.output("flow", ProgramValueTypes.FLOW))
        );
    }

    private static ProgramNodeSchema temporaryNozzleSchema(NozzleTargetType targetType) {
        return new ProgramNodeSchema(
                List.of(
                        ProgramPortDefinition.requiredInput("flow", ProgramValueTypes.FLOW),
                        ProgramPortDefinition.requiredInput(
                                targetType.port(), targetType.valueType()),
                        ProgramPortDefinition.requiredInput(
                                "direction", ProgramValueTypes.DIRECTION)
                ),
                List.of(ProgramPortDefinition.output("flow", ProgramValueTypes.FLOW))
        );
    }

    private static ProgramNodeSchema fireJetsSchema() {
        return new ProgramNodeSchema(
                List.of(ProgramPortDefinition.requiredInput("flow", ProgramValueTypes.FLOW)),
                List.of(ProgramPortDefinition.output("flow", ProgramValueTypes.FLOW))
        );
    }

    private static ProgramNodeSchema ownedJetNozzleCountSchema() {
        return new ProgramNodeSchema(
                List.of(
                        ProgramPortDefinition.requiredInput(
                                "center", ProgramValueTypes.WORLD_POSITION),
                        ProgramPortDefinition.requiredInput(
                                "radius", ProgramValueTypes.FLOAT),
                        ProgramPortDefinition.optionalInput(
                                "direction", ProgramValueTypes.DIRECTION)
                ),
                List.of(ProgramPortDefinition.output(
                        "count", ProgramValueTypes.INTEGER))
        );
    }

    private static ProgramNodeSchema launchBlockStructureSchema() {
        return new ProgramNodeSchema(
                List.of(
                        ProgramPortDefinition.requiredInput("flow", ProgramValueTypes.FLOW),
                        ProgramPortDefinition.requiredInput(
                                "block", ProgramValueTypes.BLOCK_POSITION),
                        ProgramPortDefinition.requiredInput(
                                "direction", ProgramValueTypes.DIRECTION)
                ),
                List.of(ProgramPortDefinition.output("flow", ProgramValueTypes.FLOW))
        );
    }

    private static ProgramNodeType<PowerConfiguration> powerType(
            ProgramNodeSchema schema,
            Identifier capability
    ) {
        return new FixedNodeType<>(
                PowerConfiguration.CODEC,
                schema,
                ProgramNodeRole.ACTION,
                ProgramNodePurity.ACTION,
                capabilityScope(capability)
        );
    }

    private static <C> ProgramNodeType<C> fixedType(
            Codec<C> codec,
            ProgramNodeSchema schema,
            Identifier capability
    ) {
        return new FixedNodeType<>(
                codec,
                schema,
                ProgramNodeRole.ACTION,
                ProgramNodePurity.ACTION,
                capabilityScope(capability)
        );
    }

    private static <C> ProgramNodeType<C> dynamicType(
            Codec<C> codec,
            Function<C, ProgramNodeSchema> schema,
            ProgramNodeRole role,
            ProgramNodePurity purity,
            ProgramNodeScope scope
    ) {
        return new DynamicNodeType<>(codec, schema, role, purity, scope);
    }

    private static ProgramNodeType<EmptyConfiguration> unitType(
            ProgramNodeSchema schema,
            ProgramNodeRole role,
            ProgramNodePurity purity,
            ProgramNodeScope scope
    ) {
        return new FixedNodeType<>(
                MapCodec.unit(EmptyConfiguration.INSTANCE).codec(),
                schema,
                role,
                purity,
                scope
        );
    }

    private static ProgramNodeScope categoryScope() {
        return ProgramNodeScope.category(AEROMANIP);
    }

    private static ProgramNodeScope capabilityScope(Identifier capability) {
        return new ProgramNodeScope(Set.of(AEROMANIP), Set.of(capability));
    }

    private static void put(
            Map<Identifier, ProgramNodeType<?>> result,
            Identifier id,
            ProgramNodeType<?> type
    ) {
        if (result.putIfAbsent(id, type) != null) {
            throw new IllegalStateException("Duplicate Aeromanip program node " + id);
        }
    }

    public record PowerConfiguration(float power) {
        public static final Codec<PowerConfiguration> CODEC = Codec.floatRange(0.0f, 2.0f)
                .fieldOf("power")
                .xmap(PowerConfiguration::new, PowerConfiguration::power)
                .codec();
    }

    public record LaminarCutConfiguration(
            float power,
            ChargeTier chargeTier,
            ChargeAcceleration chargeAcceleration,
            BladePlaneMode planeMode
    ) {
        public static final Codec<LaminarCutConfiguration> CODEC =
                RecordCodecBuilder.create(instance -> instance.group(
                        Codec.floatRange(0.0f, 2.0f).fieldOf("power")
                                .forGetter(LaminarCutConfiguration::power),
                        ChargeTier.CODEC.optionalFieldOf("charge_tier", ChargeTier.INSTANT)
                                .forGetter(LaminarCutConfiguration::chargeTier),
                        ChargeAcceleration.CODEC.optionalFieldOf(
                                        "charge_acceleration", ChargeAcceleration.STANDARD)
                                .forGetter(LaminarCutConfiguration::chargeAcceleration),
                        BladePlaneMode.CODEC.optionalFieldOf(
                                        "plane_mode", BladePlaneMode.DISABLED)
                                .forGetter(LaminarCutConfiguration::planeMode)
                ).apply(instance, LaminarCutConfiguration::new));
    }

    public record ConvergingAirflowConfiguration(float power, int maximumTargets) {
        public static final Codec<ConvergingAirflowConfiguration> CODEC =
                RecordCodecBuilder.create(instance -> instance.group(
                        Codec.floatRange(0.0f, 2.0f).fieldOf("power")
                                .forGetter(ConvergingAirflowConfiguration::power),
                        Codec.intRange(1, 16).optionalFieldOf("maximum_targets", 6)
                                .forGetter(ConvergingAirflowConfiguration::maximumTargets)
                ).apply(instance, ConvergingAirflowConfiguration::new));
    }

    public record TemporaryNozzleConfiguration(NozzleTargetType targetType) {
        public static final Codec<TemporaryNozzleConfiguration> CODEC = NozzleTargetType.CODEC
                .optionalFieldOf("target_type", NozzleTargetType.ENTITY)
                .xmap(TemporaryNozzleConfiguration::new,
                        TemporaryNozzleConfiguration::targetType)
                .codec();
    }

    public record JetActivationConfiguration(int duration) {
        public static final Codec<JetActivationConfiguration> CODEC = Codec.intRange(1, 60)
                .optionalFieldOf("duration", 8)
                .xmap(JetActivationConfiguration::new, JetActivationConfiguration::duration)
                .codec();
    }

    public record BlockStructureLaunchConfiguration(
            float power,
            int structureRadius,
            int duration,
            boolean restoreWhenSettled
    ) {
        public static final Codec<BlockStructureLaunchConfiguration> CODEC =
                RecordCodecBuilder.create(instance -> instance.group(
                        Codec.floatRange(0.0f, 2.0f).optionalFieldOf("power", 1.0f)
                                .forGetter(BlockStructureLaunchConfiguration::power),
                        Codec.intRange(2, 8).optionalFieldOf("radius", 2)
                                .forGetter(BlockStructureLaunchConfiguration::structureRadius),
                        Codec.intRange(1, 60).optionalFieldOf("duration", 8)
                                .forGetter(BlockStructureLaunchConfiguration::duration),
                        Codec.BOOL.optionalFieldOf("restore_when_settled", true)
                                .forGetter(BlockStructureLaunchConfiguration::restoreWhenSettled)
                ).apply(instance, BlockStructureLaunchConfiguration::new));
    }

    public enum ChargeTier {
        INSTANT("instant"), HALF("half"), FULL("full");

        private static final Codec<ChargeTier> CODEC = Codec.STRING.xmap(
                ChargeTier::byName, ChargeTier::wireName);
        private final String wireName;

        ChargeTier(String wireName) {
            this.wireName = wireName;
        }

        public String wireName() {
            return wireName;
        }

        private static ChargeTier byName(String value) {
            for (var tier : values()) if (tier.wireName.equals(value)) return tier;
            throw new IllegalArgumentException("Unknown Laminar Cut charge tier " + value);
        }
    }

    public enum ChargeAcceleration {
        STANDARD("standard", 1.0f),
        ACCELERATED("accelerated", 1.5f),
        INSTANT("instant", 2.0f);

        private static final Codec<ChargeAcceleration> CODEC = Codec.STRING.xmap(
                ChargeAcceleration::byName, ChargeAcceleration::wireName);
        private final String wireName;
        private final float costMultiplier;

        ChargeAcceleration(String wireName, float costMultiplier) {
            this.wireName = wireName;
            this.costMultiplier = costMultiplier;
        }

        public String wireName() {
            return wireName;
        }

        public float costMultiplier() {
            return costMultiplier;
        }

        private static ChargeAcceleration byName(String value) {
            for (var acceleration : values()) {
                if (acceleration.wireName.equals(value)) return acceleration;
            }
            throw new IllegalArgumentException(
                    "Unknown Laminar Cut charge acceleration " + value);
        }
    }

    public enum BladePlaneMode {
        DISABLED("disabled"),
        DIRECTION("direction"),
        RANDOM("random");

        private static final Codec<BladePlaneMode> CODEC = Codec.STRING.xmap(
                BladePlaneMode::byName, BladePlaneMode::wireName);
        private final String wireName;

        BladePlaneMode(String wireName) {
            this.wireName = wireName;
        }

        public String wireName() {
            return wireName;
        }

        private static BladePlaneMode byName(String value) {
            for (var mode : values()) if (mode.wireName.equals(value)) return mode;
            throw new IllegalArgumentException("Unknown Laminar Cut blade plane mode " + value);
        }
    }

    public enum NozzleTargetType {
        ENTITY("entity", "entity", ProgramValueTypes.ENTITY_REFERENCE),
        BLOCK("block", "block", ProgramValueTypes.BLOCK_POSITION);

        private static final Codec<NozzleTargetType> CODEC = Codec.STRING.xmap(
                NozzleTargetType::byName, NozzleTargetType::wireName);
        private final String wireName;
        private final String port;
        private final ProgramValueType valueType;

        NozzleTargetType(String wireName, String port, ProgramValueType valueType) {
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

        private static NozzleTargetType byName(String value) {
            for (var type : values()) if (type.wireName.equals(value)) return type;
            throw new IllegalArgumentException("Unknown nozzle target type " + value);
        }
    }

    private enum EmptyConfiguration {
        INSTANCE
    }

    private record FixedNodeType<C>(
            Codec<C> configurationCodec,
            ProgramNodeSchema schema,
            ProgramNodeRole role,
            ProgramNodePurity purity,
            ProgramNodeScope scope
    ) implements ProgramNodeType<C> {
        @Override
        public int schemaVersion() {
            return 1;
        }

        @Override
        public ProgramNodeSchema schema(C configuration) {
            return schema;
        }
    }

    private record DynamicNodeType<C>(
            Codec<C> configurationCodec,
            Function<C, ProgramNodeSchema> schemaFactory,
            ProgramNodeRole role,
            ProgramNodePurity purity,
            ProgramNodeScope scope
    ) implements ProgramNodeType<C> {
        @Override
        public int schemaVersion() {
            return 1;
        }

        @Override
        public ProgramNodeSchema schema(C configuration) {
            return schemaFactory.apply(configuration);
        }
    }
}
