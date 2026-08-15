package org.academy.internal.common.ability.meltdowner.program;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.program.ProgramNodePurity;
import org.academy.api.common.ability.program.ProgramNodeRole;
import org.academy.api.common.ability.program.ProgramNodeSchema;
import org.academy.api.common.ability.program.ProgramNodeScope;
import org.academy.api.common.ability.program.ProgramNodeType;
import org.academy.api.common.ability.program.ProgramPortDefinition;
import org.academy.api.common.ability.program.ProgramValueTypes;
import org.academy.internal.common.ability.AbilityCategoryNames;
import org.academy.internal.common.ability.program.ProgramNodeLookup;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Strongly typed Meltdowner target and action schemas. */
public final class MeltdownerProgramNodeCatalog implements ProgramNodeLookup {
    public static final Identifier MELTDOWNER =
            AcademyCraft.academy(AbilityCategoryNames.MELTDOWNER);
    public static final MeltdownerProgramNodeCatalog INSTANCE =
            new MeltdownerProgramNodeCatalog();

    private final Map<Identifier, ProgramNodeType<?>> types;

    private MeltdownerProgramNodeCatalog() {
        var result = new HashMap<Identifier, ProgramNodeType<?>>();
        put(result, MeltdownerProgramNodeIds.CASTER, unitType(
                querySchema(), ProgramNodeRole.QUERY, ProgramNodePurity.WORLD_QUERY,
                categoryScope()));
        put(result, MeltdownerProgramNodeIds.LOOK_TARGET, unitType(
                querySchema(), ProgramNodeRole.QUERY, ProgramNodePurity.WORLD_QUERY,
                categoryScope()));
        put(result, MeltdownerProgramNodeIds.ELECTRON_BEAM, beamType(
                BeamConfiguration.CODEC, MeltdownerProgramNodeCatalog::electronBeamSchema,
                MeltdownerProgramCapabilities.ELECTRON_BEAM));
        put(result, MeltdownerProgramNodeIds.MINING_BEAM, beamType(
                MiningBeamConfiguration.CODEC, MeltdownerProgramNodeCatalog::miningBeamSchema,
                MeltdownerProgramCapabilities.MINING_BEAM));
        put(result, MeltdownerProgramNodeIds.ATOMIC_JET, beamType(
                AtomicJetConfiguration.CODEC, _ -> atomicJetSchema(),
                MeltdownerProgramCapabilities.ATOMIC_JET));
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

    private static ProgramNodeSchema electronBeamSchema(BeamConfiguration configuration) {
        var aim = configuration.aimMode() == AimMode.DIRECTION
                ? ProgramPortDefinition.requiredInput("direction", ProgramValueTypes.DIRECTION)
                : ProgramPortDefinition.requiredInput(
                        "target_position", ProgramValueTypes.WORLD_POSITION);
        return new ProgramNodeSchema(
                List.of(
                        ProgramPortDefinition.requiredInput("flow", ProgramValueTypes.FLOW),
                        ProgramPortDefinition.optionalInput("origin", ProgramValueTypes.WORLD_POSITION),
                        aim
                ),
                List.of(ProgramPortDefinition.output("flow", ProgramValueTypes.FLOW))
        );
    }

    private static ProgramNodeSchema miningBeamSchema(MiningBeamConfiguration configuration) {
        var inputs = new java.util.ArrayList<ProgramPortDefinition>();
        inputs.add(ProgramPortDefinition.requiredInput("flow", ProgramValueTypes.FLOW));
        inputs.add(ProgramPortDefinition.optionalInput("origin", ProgramValueTypes.WORLD_POSITION));
        if (configuration.aimMode() == AimMode.DIRECTION) {
            inputs.add(ProgramPortDefinition.optionalInput("direction", ProgramValueTypes.DIRECTION));
            // Legacy targeted-mining programs are decoded through this optional compatibility port.
            inputs.add(ProgramPortDefinition.optionalInput("block", ProgramValueTypes.BLOCK_POSITION));
        } else {
            inputs.add(ProgramPortDefinition.requiredInput(
                    "target_position", ProgramValueTypes.WORLD_POSITION));
        }
        return new ProgramNodeSchema(
                List.copyOf(inputs),
                List.of(ProgramPortDefinition.output("flow", ProgramValueTypes.FLOW))
        );
    }

    private static ProgramNodeSchema atomicJetSchema() {
        return new ProgramNodeSchema(
                List.of(
                        ProgramPortDefinition.requiredInput("flow", ProgramValueTypes.FLOW),
                        ProgramPortDefinition.requiredInput("entity", ProgramValueTypes.ENTITY_REFERENCE),
                        ProgramPortDefinition.requiredInput("direction", ProgramValueTypes.DIRECTION)
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
                _ -> schema,
                ProgramNodeRole.ACTION,
                ProgramNodePurity.ACTION,
                capabilityScope(capability)
        );
    }

    private static <C> ProgramNodeType<C> beamType(
            Codec<C> codec,
            java.util.function.Function<C, ProgramNodeSchema> schema,
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

    private static ProgramNodeType<EmptyConfiguration> unitType(
            ProgramNodeSchema schema,
            ProgramNodeRole role,
            ProgramNodePurity purity,
            ProgramNodeScope scope
    ) {
        return new FixedNodeType<>(
                MapCodec.unit(EmptyConfiguration.INSTANCE).codec(),
                _ -> schema,
                role,
                purity,
                scope
        );
    }

    private static ProgramNodeScope categoryScope() {
        return ProgramNodeScope.category(MELTDOWNER);
    }

    private static ProgramNodeScope capabilityScope(Identifier capability) {
        return new ProgramNodeScope(Set.of(MELTDOWNER), Set.of(capability));
    }

    private static void put(
            Map<Identifier, ProgramNodeType<?>> result,
            Identifier id,
            ProgramNodeType<?> type
    ) {
        if (result.putIfAbsent(id, type) != null) {
            throw new IllegalStateException("Duplicate Meltdowner program node " + id);
        }
    }

    public record PowerConfiguration(float power) {
        public static final Codec<PowerConfiguration> CODEC = Codec.floatRange(0.0f, 2.0f)
                .fieldOf("power")
                .xmap(PowerConfiguration::new, PowerConfiguration::power)
                .codec();
    }

    public record BeamConfiguration(float power, AimMode aimMode, boolean destroyBlocks) {
        public static final Codec<BeamConfiguration> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        Codec.floatRange(0.0f, 2.0f).fieldOf("power")
                                .forGetter(BeamConfiguration::power),
                        AimMode.CODEC.optionalFieldOf("aim_mode", AimMode.DIRECTION)
                                .forGetter(BeamConfiguration::aimMode),
                        Codec.BOOL.optionalFieldOf("destroy_blocks", true)
                                .forGetter(BeamConfiguration::destroyBlocks)
                ).apply(instance, BeamConfiguration::new));
    }

    public record MiningBeamConfiguration(float power, AimMode aimMode) {
        public static final Codec<MiningBeamConfiguration> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        Codec.floatRange(0.0f, 2.0f).fieldOf("power")
                                .forGetter(MiningBeamConfiguration::power),
                        AimMode.CODEC.optionalFieldOf("aim_mode", AimMode.DIRECTION)
                                .forGetter(MiningBeamConfiguration::aimMode)
                ).apply(instance, MiningBeamConfiguration::new));
    }

    public record AtomicJetConfiguration(float power, boolean destroyBlocks) {
        public static final Codec<AtomicJetConfiguration> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        Codec.floatRange(0.0f, 2.0f).fieldOf("power")
                                .forGetter(AtomicJetConfiguration::power),
                        Codec.BOOL.optionalFieldOf("destroy_blocks", true)
                                .forGetter(AtomicJetConfiguration::destroyBlocks)
                ).apply(instance, AtomicJetConfiguration::new));
    }

    public enum AimMode {
        DIRECTION("direction"),
        TARGET("target");

        private static final Codec<AimMode> CODEC = Codec.STRING.xmap(
                AimMode::byName, AimMode::wireName);
        private final String wireName;

        AimMode(String wireName) {
            this.wireName = wireName;
        }

        public String wireName() {
            return wireName;
        }

        private static AimMode byName(String value) {
            for (var mode : values()) if (mode.wireName.equals(value)) return mode;
            throw new IllegalArgumentException("Unknown beam aim mode " + value);
        }
    }

    private enum EmptyConfiguration {
        INSTANCE
    }

    private record FixedNodeType<C>(
            Codec<C> configurationCodec,
            java.util.function.Function<C, ProgramNodeSchema> schemaFactory,
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
