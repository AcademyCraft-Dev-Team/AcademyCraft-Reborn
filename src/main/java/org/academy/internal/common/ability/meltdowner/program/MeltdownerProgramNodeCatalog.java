package org.academy.internal.common.ability.meltdowner.program;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
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
        put(result, MeltdownerProgramNodeIds.ELECTRON_BEAM, powerType(
                electronBeamSchema(), MeltdownerProgramCapabilities.ELECTRON_BEAM));
        put(result, MeltdownerProgramNodeIds.MINING_BEAM, powerType(
                miningBeamSchema(), MeltdownerProgramCapabilities.MINING_BEAM));
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

    private static ProgramNodeSchema electronBeamSchema() {
        return new ProgramNodeSchema(
                List.of(
                        ProgramPortDefinition.requiredInput("flow", ProgramValueTypes.FLOW),
                        ProgramPortDefinition.requiredInput(
                                "direction", ProgramValueTypes.DIRECTION)
                ),
                List.of(ProgramPortDefinition.output("flow", ProgramValueTypes.FLOW))
        );
    }

    private static ProgramNodeSchema miningBeamSchema() {
        return new ProgramNodeSchema(
                List.of(
                        ProgramPortDefinition.requiredInput("flow", ProgramValueTypes.FLOW),
                        ProgramPortDefinition.requiredInput(
                                "block", ProgramValueTypes.BLOCK_POSITION)
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
