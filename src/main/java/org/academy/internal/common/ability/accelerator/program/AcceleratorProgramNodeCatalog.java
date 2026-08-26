package org.academy.internal.common.ability.accelerator.program;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.program.*;
import org.academy.internal.common.ability.AbilityCategoryNames;
import org.academy.internal.common.ability.accelerator.skills.lv2.KineticEnergyApplied;
import org.academy.internal.common.ability.program.ProgramNodeLookup;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Strongly typed vector-manipulation program node schemas.
 */
public final class AcceleratorProgramNodeCatalog implements ProgramNodeLookup {
    public static final Identifier ACCELERATOR =
            AcademyCraft.academy(AbilityCategoryNames.ACCELERATOR);
    public static final AcceleratorProgramNodeCatalog INSTANCE =
            new AcceleratorProgramNodeCatalog();

    private final Map<Identifier, ProgramNodeType<?>> types;

    private AcceleratorProgramNodeCatalog() {
        var result = new HashMap<Identifier, ProgramNodeType<?>>();
        put(result, AcceleratorProgramNodeIds.CASTER, unitType(
                querySchema("entity"), ProgramNodeRole.QUERY, ProgramNodePurity.WORLD_QUERY,
                categoryScope()));
        put(result, AcceleratorProgramNodeIds.LOOK_TARGET, unitType(
                querySchema("entity"), ProgramNodeRole.QUERY, ProgramNodePurity.WORLD_QUERY,
                categoryScope()));
        put(result, AcceleratorProgramNodeIds.INCOMING_PROJECTILES, unitType(
                new ProgramNodeSchema(
                        List.of(),
                        List.of(ProgramPortDefinition.output(
                                "entities", ProgramValueTypes.ENTITY_SET))
                ),
                ProgramNodeRole.QUERY,
                ProgramNodePurity.WORLD_QUERY,
                categoryScope()
        ));
        put(result, AcceleratorProgramNodeIds.APPLY_VECTOR, strengthType(
                actionSchema("entity"), AcceleratorProgramCapabilities.APPLY_VECTOR));
        put(result, AcceleratorProgramNodeIds.KINETIC_IMPACT, strengthType(
                actionSchema("entity"), AcceleratorProgramCapabilities.KINETIC_IMPACT));
        put(result, AcceleratorProgramNodeIds.KINETIC_SHOCKWAVE, shockwaveType());
        put(result, AcceleratorProgramNodeIds.REDIRECT_PROJECTILE,
                unitType(
                        actionSchema("projectile"),
                        ProgramNodeRole.ACTION,
                        ProgramNodePurity.ACTION,
                        capabilityScope(AcceleratorProgramCapabilities.REDIRECT_PROJECTILE)
                ));
        put(result, AcceleratorProgramNodeIds.DISPLACE_ENTITY, strengthType(
                entityDisplacementSchema(), AcceleratorProgramCapabilities.DISPLACE_ENTITY));
        put(result, AcceleratorProgramNodeIds.DISPLACE_BLOCK, strengthType(
                blockDisplacementSchema(), AcceleratorProgramCapabilities.DISPLACE_BLOCK));
        types = Map.copyOf(result);
    }

    @Override
    public ProgramNodeType<?> find(Identifier id) {
        return types.get(id);
    }

    public Map<Identifier, ProgramNodeType<?>> types() {
        return types;
    }

    private static ProgramNodeSchema querySchema(String output) {
        return new ProgramNodeSchema(
                List.of(),
                List.of(ProgramPortDefinition.output(output, ProgramValueTypes.ENTITY_REFERENCE))
        );
    }

    private static ProgramNodeSchema actionSchema(String entityPort) {
        return new ProgramNodeSchema(
                List.of(
                        ProgramPortDefinition.requiredInput("flow", ProgramValueTypes.FLOW),
                        ProgramPortDefinition.requiredInput(
                                entityPort, ProgramValueTypes.ENTITY_REFERENCE),
                        ProgramPortDefinition.requiredInput(
                                "direction", ProgramValueTypes.DIRECTION)
                ),
                List.of(ProgramPortDefinition.output("flow", ProgramValueTypes.FLOW))
        );
    }

    private static ProgramNodeSchema shockwaveSchema() {
        return new ProgramNodeSchema(
                List.of(
                        ProgramPortDefinition.requiredInput("flow", ProgramValueTypes.FLOW),
                        ProgramPortDefinition.requiredInput(
                                "position", ProgramValueTypes.WORLD_POSITION),
                        ProgramPortDefinition.requiredInput(
                                "direction", ProgramValueTypes.DIRECTION)
                ),
                List.of(ProgramPortDefinition.output("flow", ProgramValueTypes.FLOW))
        );
    }

    private static ProgramNodeSchema entityDisplacementSchema() {
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

    private static ProgramNodeSchema blockDisplacementSchema() {
        return new ProgramNodeSchema(
                List.of(
                        ProgramPortDefinition.requiredInput("flow", ProgramValueTypes.FLOW),
                        ProgramPortDefinition.requiredInput(
                                "block", ProgramValueTypes.BLOCK_POSITION),
                        ProgramPortDefinition.requiredInput(
                                "destination", ProgramValueTypes.BLOCK_POSITION)
                ),
                List.of(ProgramPortDefinition.output("flow", ProgramValueTypes.FLOW))
        );
    }

    private static ProgramNodeType<StrengthConfiguration> strengthType(
            ProgramNodeSchema schema,
            Identifier capability
    ) {
        return new FixedNodeType<>(
                StrengthConfiguration.CODEC,
                schema,
                ProgramNodeRole.ACTION,
                ProgramNodePurity.ACTION,
                capabilityScope(capability)
        );
    }

    private static ProgramNodeType<ShockwaveConfiguration> shockwaveType() {
        return new FixedNodeType<>(
                ShockwaveConfiguration.CODEC,
                shockwaveSchema(),
                ProgramNodeRole.ACTION,
                ProgramNodePurity.ACTION,
                capabilityScope(AcceleratorProgramCapabilities.KINETIC_SHOCKWAVE)
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
        return ProgramNodeScope.category(ACCELERATOR);
    }

    private static ProgramNodeScope capabilityScope(Identifier capability) {
        return new ProgramNodeScope(Set.of(ACCELERATOR), Set.of(capability));
    }

    private static void put(
            Map<Identifier, ProgramNodeType<?>> result,
            Identifier id,
            ProgramNodeType<?> type
    ) {
        if (result.putIfAbsent(id, type) != null) {
            throw new IllegalStateException("Duplicate accelerator program node " + id);
        }
    }

    public record StrengthConfiguration(int strength) {
        public static final Codec<StrengthConfiguration> CODEC = Codec.intRange(0, 2)
                .fieldOf("strength")
                .xmap(StrengthConfiguration::new, StrengthConfiguration::strength)
                .codec();

        public AcceleratorProgramStrength tier() {
            return AcceleratorProgramStrength.byWireId(strength);
        }
    }

    /**
     * Continuous custom shockwave controls with a decoder for legacy saved graphs.
     */
    public record ShockwaveConfiguration(
            float power,
            boolean destroyBlocks,
            int radius
    ) {
        private static final Codec<ShockwaveConfiguration> CURRENT_CODEC =
                RecordCodecBuilder.create(instance ->
                        instance.group(
                                Codec.floatRange(0.0f, 2.0f).fieldOf("power")
                                        .forGetter(ShockwaveConfiguration::power),
                                Codec.BOOL.optionalFieldOf("destroy_blocks", false)
                                        .forGetter(ShockwaveConfiguration::destroyBlocks),
                                Codec.intRange(
                                                KineticEnergyApplied.MIN_PROGRAM_RADIUS,
                                                KineticEnergyApplied.MAX_PROGRAM_RADIUS)
                                        .fieldOf("radius")
                                        .forGetter(ShockwaveConfiguration::radius)
                        ).apply(instance, ShockwaveConfiguration::new));

        public static final Codec<ShockwaveConfiguration> CODEC = Codec.either(
                CURRENT_CODEC,
                LegacyShockwaveConfiguration.CODEC
        ).xmap(
                value -> value.map(configuration -> configuration,
                        LegacyShockwaveConfiguration::migrate),
                Either::left
        );
    }

    private record LegacyShockwaveConfiguration(
            int strength,
            boolean destroyBlocks,
            float damage,
            int blockRadius
    ) {
        private static final Codec<LegacyShockwaveConfiguration> CODEC =
                RecordCodecBuilder.create(instance -> instance.group(
                        Codec.intRange(0, 2)
                                .fieldOf("strength")
                                .forGetter(LegacyShockwaveConfiguration::strength),
                        Codec.BOOL.optionalFieldOf("destroy_blocks", false)
                                .forGetter(LegacyShockwaveConfiguration::destroyBlocks),
                        Codec.floatRange(1.0f, 100.0f)
                                .optionalFieldOf("damage", KineticEnergyApplied.BASE_PROGRAM_DAMAGE)
                                .forGetter(LegacyShockwaveConfiguration::damage),
                        Codec.intRange(1, 32)
                                .optionalFieldOf("block_radius", KineticEnergyApplied.DEFAULT_PROGRAM_RADIUS)
                                .forGetter(LegacyShockwaveConfiguration::blockRadius)
                ).apply(instance, LegacyShockwaveConfiguration::new));

        private ShockwaveConfiguration migrate() {
            var migratedPower = Math.clamp(
                    damage / KineticEnergyApplied.BASE_PROGRAM_DAMAGE,
                    0.0f,
                    2.0f
            );
            return new ShockwaveConfiguration(migratedPower, destroyBlocks, blockRadius);
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
}
