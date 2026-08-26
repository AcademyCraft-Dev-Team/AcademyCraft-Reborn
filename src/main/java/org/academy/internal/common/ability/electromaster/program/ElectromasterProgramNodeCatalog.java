package org.academy.internal.common.ability.electromaster.program;

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
 * Strongly typed Electromaster target and action schemas.
 */
public final class ElectromasterProgramNodeCatalog implements ProgramNodeLookup {
    public static final Identifier ELECTROMASTER =
            AcademyCraft.academy(AbilityCategoryNames.ELECTROMASTER);
    public static final ElectromasterProgramNodeCatalog INSTANCE =
            new ElectromasterProgramNodeCatalog();

    private final Map<Identifier, ProgramNodeType<?>> types;

    private ElectromasterProgramNodeCatalog() {
        var result = new HashMap<Identifier, ProgramNodeType<?>>();
        put(result, ElectromasterProgramNodeIds.CASTER, unitType(
                querySchema(), ProgramNodeRole.QUERY, ProgramNodePurity.WORLD_QUERY,
                categoryScope()));
        put(result, ElectromasterProgramNodeIds.LOOK_TARGET, unitType(
                querySchema(), ProgramNodeRole.QUERY, ProgramNodePurity.WORLD_QUERY,
                categoryScope()));
        put(result, ElectromasterProgramNodeIds.CHARGEABLE_BLOCKS, unitType(
                chargeableBlocksSchema(), ProgramNodeRole.QUERY, ProgramNodePurity.WORLD_QUERY,
                categoryScope()));
        put(result, ElectromasterProgramNodeIds.ENERGY_DETECTION, dynamicType(
                EnergyDetectionConfiguration.CODEC,
                configuration -> energyDetectionSchema(configuration.targetType()),
                ProgramNodeRole.QUERY, ProgramNodePurity.WORLD_QUERY, categoryScope()));
        put(result, ElectromasterProgramNodeIds.REDSTONE_DETECTION, dynamicType(
                RedstoneDetectionConfiguration.CODEC,
                _ -> redstoneDetectionSchema(),
                ProgramNodeRole.QUERY, ProgramNodePurity.WORLD_QUERY, categoryScope()));
        put(result, ElectromasterProgramNodeIds.ARC_DISCHARGE, powerType(
                arcSchema(), ElectromasterProgramCapabilities.ARC_DISCHARGE));
        put(result, ElectromasterProgramNodeIds.MAGNETIC_MOVE, dynamicType(
                MagneticConfiguration.CODEC,
                ElectromasterProgramNodeCatalog::magneticMoveSchema,
                ProgramNodeRole.ACTION, ProgramNodePurity.ACTION,
                capabilityScope(ElectromasterProgramCapabilities.MAGNETIC_MOVE)));
        put(result, ElectromasterProgramNodeIds.CURRENT_RECHARGE, dynamicType(
                CurrentRechargeConfiguration.CODEC,
                configuration -> currentRechargeSchema(configuration.targetType()),
                ProgramNodeRole.ACTION, ProgramNodePurity.ACTION,
                capabilityScope(ElectromasterProgramCapabilities.CURRENT_RECHARGE)));
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

    private static ProgramNodeSchema arcSchema() {
        return new ProgramNodeSchema(
                List.of(
                        ProgramPortDefinition.requiredInput("flow", ProgramValueTypes.FLOW),
                        ProgramPortDefinition.requiredInput(
                                "entity", ProgramValueTypes.ENTITY_REFERENCE)
                ),
                List.of(ProgramPortDefinition.output("flow", ProgramValueTypes.FLOW))
        );
    }

    private static ProgramNodeSchema chargeableBlocksSchema() {
        return new ProgramNodeSchema(
                List.of(
                        ProgramPortDefinition.requiredInput("center", ProgramValueTypes.WORLD_POSITION),
                        ProgramPortDefinition.requiredInput("radius", ProgramValueTypes.FLOAT)
                ),
                List.of(ProgramPortDefinition.output("blocks", ProgramValueTypes.BLOCK_POSITION_SET))
        );
    }

    private static ProgramNodeSchema energyDetectionSchema(EnergyTargetType targetType) {
        return new ProgramNodeSchema(
                List.of(ProgramPortDefinition.requiredInput(
                        targetType.port(), targetType.valueType())),
                List.of(ProgramPortDefinition.output("result", ProgramValueTypes.BOOLEAN))
        );
    }

    private static ProgramNodeSchema redstoneDetectionSchema() {
        return new ProgramNodeSchema(
                List.of(ProgramPortDefinition.requiredInput("block", ProgramValueTypes.BLOCK_POSITION)),
                List.of(ProgramPortDefinition.output("result", ProgramValueTypes.BOOLEAN))
        );
    }

    private static ProgramNodeSchema currentRechargeSchema(EnergyTargetType targetType) {
        return new ProgramNodeSchema(
                List.of(
                        ProgramPortDefinition.requiredInput("flow", ProgramValueTypes.FLOW),
                        ProgramPortDefinition.requiredInput(targetType.port(), targetType.valueType())
                ),
                List.of(ProgramPortDefinition.output("flow", ProgramValueTypes.FLOW))
        );
    }

    private static ProgramNodeSchema magneticMoveSchema(MagneticConfiguration configuration) {
        return new ProgramNodeSchema(
                List.of(
                        ProgramPortDefinition.requiredInput("flow", ProgramValueTypes.FLOW),
                        ProgramPortDefinition.requiredInput(
                                configuration.targetType().port(),
                                configuration.targetType().valueType()),
                        ProgramPortDefinition.requiredInput(
                                "destination", ProgramValueTypes.WORLD_POSITION)
                ),
                List.of(ProgramPortDefinition.output("flow", ProgramValueTypes.FLOW))
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
        return ProgramNodeScope.category(ELECTROMASTER);
    }

    private static ProgramNodeScope capabilityScope(Identifier capability) {
        return new ProgramNodeScope(Set.of(ELECTROMASTER), Set.of(capability));
    }

    private static void put(
            Map<Identifier, ProgramNodeType<?>> result,
            Identifier id,
            ProgramNodeType<?> type
    ) {
        if (result.putIfAbsent(id, type) != null) {
            throw new IllegalStateException("Duplicate Electromaster program node " + id);
        }
    }

    public record PowerConfiguration(float power) {
        public static final Codec<PowerConfiguration> CODEC = Codec.floatRange(0.0f, 2.0f)
                .fieldOf("power")
                .xmap(PowerConfiguration::new, PowerConfiguration::power)
                .codec();
    }

    public record EnergyDetectionConfiguration(
            EnergyTargetType targetType,
            ComparisonMode mode,
            float percent
    ) {
        public static final Codec<EnergyDetectionConfiguration> CODEC =
                RecordCodecBuilder.create(instance -> instance.group(
                        EnergyTargetType.CODEC.optionalFieldOf(
                                        "target_type", EnergyTargetType.ENTITY)
                                .forGetter(EnergyDetectionConfiguration::targetType),
                        ComparisonMode.CODEC.optionalFieldOf("mode", ComparisonMode.BELOW)
                                .forGetter(EnergyDetectionConfiguration::mode),
                        Codec.floatRange(0.0f, 100.0f).optionalFieldOf("percent", 50.0f)
                                .forGetter(EnergyDetectionConfiguration::percent)
                ).apply(instance, EnergyDetectionConfiguration::new));
    }

    public record RedstoneDetectionConfiguration(ComparisonMode mode, int level) {
        public static final Codec<RedstoneDetectionConfiguration> CODEC =
                RecordCodecBuilder.create(instance -> instance.group(
                        ComparisonMode.CODEC.optionalFieldOf("mode", ComparisonMode.BELOW)
                                .forGetter(RedstoneDetectionConfiguration::mode),
                        Codec.intRange(0, 15).optionalFieldOf("level", 8)
                                .forGetter(RedstoneDetectionConfiguration::level)
                ).apply(instance, RedstoneDetectionConfiguration::new));
    }

    public record CurrentRechargeConfiguration(EnergyTargetType targetType) {
        public static final Codec<CurrentRechargeConfiguration> CODEC = EnergyTargetType.CODEC
                .optionalFieldOf("target_type", EnergyTargetType.ENTITY)
                .xmap(CurrentRechargeConfiguration::new,
                        CurrentRechargeConfiguration::targetType)
                .codec();
    }

    public record MagneticConfiguration(
            float power,
            EnergyTargetType targetType,
            MagneticMode mode
    ) {
        public static final Codec<MagneticConfiguration> CODEC =
                RecordCodecBuilder.create(instance -> instance.group(
                        Codec.floatRange(0.0f, 2.0f).fieldOf("power")
                                .forGetter(MagneticConfiguration::power),
                        EnergyTargetType.CODEC.optionalFieldOf(
                                        "target_type", EnergyTargetType.ENTITY)
                                .forGetter(MagneticConfiguration::targetType),
                        MagneticMode.CODEC.optionalFieldOf("mode", MagneticMode.PULL)
                                .forGetter(MagneticConfiguration::mode)
                ).apply(instance, MagneticConfiguration::new));
    }

    public enum EnergyTargetType {
        ENTITY("entity", "entity", ProgramValueTypes.ENTITY_REFERENCE),
        BLOCK("block", "block", ProgramValueTypes.BLOCK_POSITION);

        private static final Codec<EnergyTargetType> CODEC = Codec.STRING.xmap(
                EnergyTargetType::byName, EnergyTargetType::wireName);
        private final String wireName;
        private final String port;
        private final ProgramValueType valueType;

        EnergyTargetType(String wireName, String port,
                         ProgramValueType valueType) {
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

        private static EnergyTargetType byName(String value) {
            for (var type : values()) if (type.wireName.equals(value)) return type;
            throw new IllegalArgumentException("Unknown energy target type " + value);
        }
    }

    public enum ComparisonMode {
        BELOW("below"), ABOVE("above");
        private static final Codec<ComparisonMode> CODEC = Codec.STRING.xmap(
                ComparisonMode::byName, ComparisonMode::wireName);
        private final String wireName;

        ComparisonMode(String wireName) {
            this.wireName = wireName;
        }

        public String wireName() {
            return wireName;
        }

        private static ComparisonMode byName(String value) {
            for (var mode : values()) if (mode.wireName.equals(value)) return mode;
            throw new IllegalArgumentException("Unknown comparison mode " + value);
        }
    }

    public enum MagneticMode {
        PULL("pull"), LAUNCH("launch");
        private static final Codec<MagneticMode> CODEC = Codec.STRING.xmap(
                MagneticMode::byName, MagneticMode::wireName);
        private final String wireName;

        MagneticMode(String wireName) {
            this.wireName = wireName;
        }

        public String wireName() {
            return wireName;
        }

        private static MagneticMode byName(String value) {
            for (var mode : values()) if (mode.wireName.equals(value)) return mode;
            throw new IllegalArgumentException("Unknown magnetic mode " + value);
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
