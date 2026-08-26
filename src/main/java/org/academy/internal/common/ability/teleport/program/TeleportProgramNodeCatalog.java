package org.academy.internal.common.ability.teleport.program;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.program.*;
import org.academy.internal.common.ability.AbilityCategoryNames;
import org.academy.internal.common.ability.program.ProgramNodeLookup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Strongly typed Teleport target and action schemas.
 */
public final class TeleportProgramNodeCatalog implements ProgramNodeLookup {
    public static final Identifier TELEPORT = AcademyCraft.academy(AbilityCategoryNames.TELEPORT);
    public static final TeleportProgramNodeCatalog INSTANCE = new TeleportProgramNodeCatalog();

    private final Map<Identifier, ProgramNodeType<?>> types;

    private TeleportProgramNodeCatalog() {
        var result = new HashMap<Identifier, ProgramNodeType<?>>();
        put(result, TeleportProgramNodeIds.CASTER, unitType(
                querySchema(), ProgramNodeRole.QUERY, ProgramNodePurity.WORLD_QUERY,
                categoryScope()));
        put(result, TeleportProgramNodeIds.LOOK_TARGET, unitType(
                querySchema(), ProgramNodeRole.QUERY, ProgramNodePurity.WORLD_QUERY,
                categoryScope()));
        put(result, TeleportProgramNodeIds.SELF_TELEPORT, powerType(
                selfTeleportSchema(), TeleportProgramCapabilities.SELF_TELEPORT));
        put(result, TeleportProgramNodeIds.ENTITY_TELEPORT, new DynamicNodeType<>(
                TargetTeleportConfiguration.CODEC,
                TeleportProgramNodeCatalog::targetTeleportSchema,
                ProgramNodeRole.ACTION,
                ProgramNodePurity.ACTION,
                capabilityScope(TeleportProgramCapabilities.ENTITY_TELEPORT)
        ));
        put(result, TeleportProgramNodeIds.BLOCK_ITEM_TELEPORT, new DynamicNodeType<>(
                BlockItemTeleportConfiguration.CODEC,
                TeleportProgramNodeCatalog::blockItemTeleportSchema,
                ProgramNodeRole.ACTION,
                ProgramNodePurity.ACTION,
                capabilityScope(TeleportProgramCapabilities.SELF_TELEPORT)
        ));
        put(result, TeleportProgramNodeIds.SPACE_SAFETY, unitType(
                spaceSafetySchema(), ProgramNodeRole.QUERY, ProgramNodePurity.WORLD_QUERY,
                categoryScope()));
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

    private static ProgramNodeSchema selfTeleportSchema() {
        return new ProgramNodeSchema(
                List.of(
                        ProgramPortDefinition.requiredInput("flow", ProgramValueTypes.FLOW),
                        ProgramPortDefinition.requiredInput(
                                "destination", ProgramValueTypes.WORLD_POSITION)
                ),
                List.of(ProgramPortDefinition.output("flow", ProgramValueTypes.FLOW))
        );
    }

    private static ProgramNodeSchema entityTeleportSchema() {
        return new ProgramNodeSchema(
                List.of(
                        ProgramPortDefinition.requiredInput("flow", ProgramValueTypes.FLOW),
                        ProgramPortDefinition.requiredInput(
                                "entity", ProgramValueTypes.ENTITY_REFERENCE),
                        ProgramPortDefinition.requiredInput(
                                "destination", ProgramValueTypes.WORLD_POSITION)
                ),
                List.of(ProgramPortDefinition.output("flow", ProgramValueTypes.FLOW))
        );
    }

    private static ProgramNodeSchema targetTeleportSchema(
            TargetTeleportConfiguration configuration
    ) {
        return new ProgramNodeSchema(
                List.of(
                        ProgramPortDefinition.requiredInput("flow", ProgramValueTypes.FLOW),
                        ProgramPortDefinition.requiredInput(
                                configuration.targetType().port(),
                                configuration.targetType().valueType()),
                        ProgramPortDefinition.requiredInput(
                                "destination", ProgramValueTypes.CONTROL_DESTINATION),
                        ProgramPortDefinition.optionalInput(
                                "direction", ProgramValueTypes.DIRECTION)
                ),
                List.of(ProgramPortDefinition.output("flow", ProgramValueTypes.FLOW))
        );
    }

    private static ProgramNodeSchema spaceSafetySchema() {
        return new ProgramNodeSchema(
                List.of(
                        ProgramPortDefinition.requiredInput("entity", ProgramValueTypes.ENTITY_REFERENCE),
                        ProgramPortDefinition.requiredInput("position", ProgramValueTypes.WORLD_POSITION)
                ),
                List.of(ProgramPortDefinition.output("result", ProgramValueTypes.BOOLEAN))
        );
    }

    private static ProgramNodeSchema blockItemTeleportSchema(
            BlockItemTeleportConfiguration configuration
    ) {
        var inputs = new ArrayList<ProgramPortDefinition>();
        inputs.add(ProgramPortDefinition.requiredInput("flow", ProgramValueTypes.FLOW));
        inputs.add(ProgramPortDefinition.requiredInput(
                "position", ProgramValueTypes.BLOCK_POSITION));
        if (configuration.mode() == BlockItemTeleportMode.PLACE) {
            inputs.add(ProgramPortDefinition.optionalInput("slot", ProgramValueTypes.INTEGER));
        }
        return new ProgramNodeSchema(
                List.copyOf(inputs),
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
        return ProgramNodeScope.category(TELEPORT);
    }

    private static ProgramNodeScope capabilityScope(Identifier capability) {
        return new ProgramNodeScope(Set.of(TELEPORT), Set.of(capability));
    }

    private static void put(
            Map<Identifier, ProgramNodeType<?>> result,
            Identifier id,
            ProgramNodeType<?> type
    ) {
        if (result.putIfAbsent(id, type) != null) {
            throw new IllegalStateException("Duplicate Teleport program node " + id);
        }
    }

    public record PowerConfiguration(float power) {
        public static final Codec<PowerConfiguration> CODEC = Codec.floatRange(0.0f, 2.0f)
                .fieldOf("power")
                .xmap(PowerConfiguration::new, PowerConfiguration::power)
                .codec();
    }

    public record TargetTeleportConfiguration(float power, TargetType targetType) {
        public static final Codec<TargetTeleportConfiguration> CODEC =
                RecordCodecBuilder.create(instance -> instance.group(
                        Codec.floatRange(0.0f, 2.0f).fieldOf("power")
                                .forGetter(TargetTeleportConfiguration::power),
                        TargetType.CODEC.optionalFieldOf("target_type", TargetType.ENTITY)
                                .forGetter(TargetTeleportConfiguration::targetType)
                ).apply(instance, TargetTeleportConfiguration::new));
    }

    public record BlockItemTeleportConfiguration(BlockItemTeleportMode mode) {
        public static final Codec<BlockItemTeleportConfiguration> CODEC =
                BlockItemTeleportMode.CODEC.optionalFieldOf(
                                "mode", BlockItemTeleportMode.PLACE)
                        .xmap(BlockItemTeleportConfiguration::new,
                                BlockItemTeleportConfiguration::mode)
                        .codec();
    }

    public enum BlockItemTeleportMode {
        PLACE("place"),
        COLLECT("collect");

        private static final Codec<BlockItemTeleportMode> CODEC = Codec.STRING.xmap(
                BlockItemTeleportMode::byName, BlockItemTeleportMode::wireName);
        private final String wireName;

        BlockItemTeleportMode(String wireName) {
            this.wireName = wireName;
        }

        public String wireName() {
            return wireName;
        }

        private static BlockItemTeleportMode byName(String value) {
            for (var mode : values()) if (mode.wireName.equals(value)) return mode;
            throw new IllegalArgumentException("Unknown block-item teleport mode " + value);
        }
    }

    public enum TargetType {
        ENTITY("entity", "entity", ProgramValueTypes.ENTITY_REFERENCE),
        BLOCK("block", "block", ProgramValueTypes.BLOCK_POSITION);

        private static final Codec<TargetType> CODEC = Codec.STRING.xmap(
                TargetType::byName, TargetType::wireName);
        private final String wireName;
        private final String port;
        private final ProgramValueType valueType;

        TargetType(String wireName, String port,
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

        private static TargetType byName(String value) {
            for (var type : values()) if (type.wireName.equals(value)) return type;
            throw new IllegalArgumentException("Unknown teleport target type " + value);
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
