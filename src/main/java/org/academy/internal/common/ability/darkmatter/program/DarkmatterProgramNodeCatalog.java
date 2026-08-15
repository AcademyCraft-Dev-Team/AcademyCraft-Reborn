package org.academy.internal.common.ability.darkmatter.program;

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

/** Strongly typed Darkmatter target and action schemas. */
public final class DarkmatterProgramNodeCatalog implements ProgramNodeLookup {
    public static final Identifier DARKMATTER =
            AcademyCraft.academy(AbilityCategoryNames.DARKMATTER);
    public static final DarkmatterProgramNodeCatalog INSTANCE =
            new DarkmatterProgramNodeCatalog();

    private final Map<Identifier, ProgramNodeType<?>> types;

    private DarkmatterProgramNodeCatalog() {
        var result = new HashMap<Identifier, ProgramNodeType<?>>();
        put(result, DarkmatterProgramNodeIds.CASTER, unitType(
                querySchema(), ProgramNodeRole.QUERY, ProgramNodePurity.WORLD_QUERY,
                categoryScope()));
        put(result, DarkmatterProgramNodeIds.LOOK_TARGET, unitType(
                querySchema(), ProgramNodeRole.QUERY, ProgramNodePurity.WORLD_QUERY,
                categoryScope()));
        put(result, DarkmatterProgramNodeIds.DISASSEMBLE_BLOCK, powerType(
                disassembleBlockSchema(), DarkmatterProgramCapabilities.DISASSEMBLE_BLOCK));
        put(result, DarkmatterProgramNodeIds.DISASSEMBLE_ENTITY, powerType(
                disassembleEntitySchema(), DarkmatterProgramCapabilities.DISASSEMBLE_ENTITY));
        put(result, DarkmatterProgramNodeIds.DARKMATTER_CUT, powerType(
                darkmatterCutSchema(), DarkmatterProgramCapabilities.DARKMATTER_CUT));
        put(result, DarkmatterProgramNodeIds.CREATE_BEETLE, powerType(
                createBeetleSchema(), DarkmatterProgramCapabilities.CREATE_BEETLE));
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

    private static ProgramNodeSchema disassembleBlockSchema() {
        return new ProgramNodeSchema(
                List.of(
                        ProgramPortDefinition.requiredInput("flow", ProgramValueTypes.FLOW),
                        ProgramPortDefinition.requiredInput(
                                "block", ProgramValueTypes.BLOCK_POSITION)
                ),
                List.of(ProgramPortDefinition.output("flow", ProgramValueTypes.FLOW))
        );
    }

    private static ProgramNodeSchema darkmatterCutSchema() {
        return new ProgramNodeSchema(
                List.of(
                        ProgramPortDefinition.requiredInput("flow", ProgramValueTypes.FLOW),
                        ProgramPortDefinition.requiredInput(
                                "direction", ProgramValueTypes.DIRECTION)
                ),
                List.of(ProgramPortDefinition.output("flow", ProgramValueTypes.FLOW))
        );
    }

    private static ProgramNodeSchema disassembleEntitySchema() {
        return new ProgramNodeSchema(
                List.of(
                        ProgramPortDefinition.requiredInput("flow", ProgramValueTypes.FLOW),
                        ProgramPortDefinition.requiredInput(
                                "entity", ProgramValueTypes.ENTITY_REFERENCE)
                ),
                List.of(ProgramPortDefinition.output("flow", ProgramValueTypes.FLOW))
        );
    }

    private static ProgramNodeSchema createBeetleSchema() {
        return new ProgramNodeSchema(
                List.of(
                        ProgramPortDefinition.requiredInput("flow", ProgramValueTypes.FLOW),
                        ProgramPortDefinition.requiredInput(
                                "position", ProgramValueTypes.WORLD_POSITION)
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
        return ProgramNodeScope.category(DARKMATTER);
    }

    private static ProgramNodeScope capabilityScope(Identifier capability) {
        return new ProgramNodeScope(Set.of(DARKMATTER), Set.of(capability));
    }

    private static void put(
            Map<Identifier, ProgramNodeType<?>> result,
            Identifier id,
            ProgramNodeType<?> type
    ) {
        if (result.putIfAbsent(id, type) != null) {
            throw new IllegalStateException("Duplicate Darkmatter program node " + id);
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
