package org.academy.internal.common.ability.program;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.program.*;
import org.academy.internal.common.ability.mentalout.precision.PrecisionGraph;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Compatibility catalog for graphs imported from Precision Operation.
 * Its executors are composed with the common algebra by the shared program action gateway.
 */
public final class PrecisionProgramNodeCatalog implements ProgramNodeLookup {
    public static final Identifier MENTALOUT = AcademyCraft.academy("mentalout");
    public static final PrecisionProgramNodeCatalog INSTANCE = new PrecisionProgramNodeCatalog();

    private final Map<Identifier, ProgramNodeType<?>> types;

    private PrecisionProgramNodeCatalog() {
        var mutable = new HashMap<Identifier, ProgramNodeType<?>>();
        mutable.put(PrecisionProgramNodeIds.ON_CAST, new EntryNodeType());
        for (var kind : PrecisionGraph.NodeKind.values()) {
            mutable.put(PrecisionProgramNodeIds.id(kind), new PrecisionNodeType(kind));
        }
        types = Map.copyOf(mutable);
    }

    @Override
    public ProgramNodeType<?> find(Identifier id) {
        return types.get(id);
    }

    public Map<Identifier, ProgramNodeType<?>> types() {
        return types;
    }

    public record PrecisionConfiguration(double parameter) {
        public static final Codec<PrecisionConfiguration> CODEC = Codec.DOUBLE
                .fieldOf("parameter")
                .xmap(PrecisionConfiguration::new, PrecisionConfiguration::parameter)
                .codec();
    }

    private enum EmptyConfiguration {
        INSTANCE
    }

    private static final class EntryNodeType implements ProgramNodeType<EmptyConfiguration> {
        @Override
        public Codec<EmptyConfiguration> configurationCodec() {
            return MapCodec.unit(EmptyConfiguration.INSTANCE).codec();
        }

        @Override
        public int schemaVersion() {
            return 1;
        }

        @Override
        public ProgramNodeSchema schema(EmptyConfiguration configuration) {
            return new ProgramNodeSchema(
                    List.of(),
                    List.of(ProgramPortDefinition.output("flow", ProgramValueTypes.FLOW))
            );
        }

        @Override
        public ProgramNodeRole role() {
            return ProgramNodeRole.ENTRY;
        }

        @Override
        public ProgramNodePurity purity() {
            return ProgramNodePurity.PURE;
        }

        @Override
        public ProgramNodeScope scope() {
            return ProgramNodeScope.category(MENTALOUT);
        }
    }

    private static final class PrecisionNodeType implements ProgramNodeType<PrecisionConfiguration> {
        private final PrecisionGraph.NodeKind kind;
        private final ProgramNodeSchema schema;

        private PrecisionNodeType(PrecisionGraph.NodeKind kind) {
            this.kind = kind;
            schema = new ProgramNodeSchema(
                    kind.inputDefinitions().stream().map(definition -> new ProgramPortDefinition(
                            definition.key(),
                            valueType(definition.type()),
                            definition.required(),
                            definition.maxConnections()
                    )).toList(),
                    kind.outputDefinitions().stream().map(definition -> new ProgramPortDefinition(
                            definition.key(),
                            valueType(definition.type()),
                            definition.required(),
                            definition.maxConnections()
                    )).toList()
            );
        }

        @Override
        public Codec<PrecisionConfiguration> configurationCodec() {
            return PrecisionConfiguration.CODEC;
        }

        @Override
        public int schemaVersion() {
            return 1;
        }

        @Override
        public ProgramNodeSchema schema(PrecisionConfiguration configuration) {
            if (!kind.isParameterValid(configuration.parameter())) {
                throw new IllegalArgumentException("Invalid legacy precision parameter");
            }
            return schema;
        }

        @Override
        public ProgramNodeRole role() {
            if (kind.isConditionalBranch()) return ProgramNodeRole.CONTROL;
            if (kind.isAction()) return ProgramNodeRole.ACTION;
            return kind.category() == PrecisionGraph.NodeCategory.SOURCE
                    ? ProgramNodeRole.QUERY
                    : ProgramNodeRole.VALUE;
        }

        @Override
        public ProgramNodePurity purity() {
            if (kind.isAction()) return ProgramNodePurity.ACTION;
            return kind.category() == PrecisionGraph.NodeCategory.COLLECTION
                    ? ProgramNodePurity.PURE
                    : ProgramNodePurity.WORLD_QUERY;
        }

        @Override
        public ProgramNodeScope scope() {
            return ProgramNodeScope.category(MENTALOUT);
        }

        private static ProgramValueType valueType(PrecisionGraph.PortType type) {
            return switch (type) {
                case ENTITY -> ProgramValueTypes.ENTITY_REFERENCE;
                case ENTITY_SET -> ProgramValueTypes.ENTITY_SET;
                case DESTINATION -> ProgramValueTypes.CONTROL_DESTINATION;
                case FLOW -> ProgramValueTypes.FLOW;
                case DIRECTION -> ProgramValueTypes.DIRECTION;
            };
        }
    }
}
