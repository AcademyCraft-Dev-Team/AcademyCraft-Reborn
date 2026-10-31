package org.academy.internal.common.ability.program;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.resources.Identifier;
import org.academy.api.common.ability.program.*;

import java.util.List;
import java.util.Map;

/** A flow-executed value sample; the VM's existing control-output latch owns its lifetime. */
public final class ProgramSamplingNodes {
    private static final ProgramNodeType<Configuration> TYPE = new Type();

    private ProgramSamplingNodes() {
    }

    public static ProgramNodeType<Configuration> type() {
        return TYPE;
    }

    public static JsonObject defaults() {
        var result = new JsonObject();
        result.addProperty("type", ProgramValueTypes.ENTITY_SET.id().toString());
        return result;
    }

    public static ProgramNodeStep sample(Configuration configuration, ProgramInputView inputs) {
        var value = ProgramValueSnapshots.snapshot(
                inputs.requireCompatible("value", configuration.type()), configuration.type());
        return ProgramNodeStep.next("flow", Map.of("value", value));
    }

    public record Configuration(Identifier typeId) {
        private static final Codec<Identifier> TYPE_CODEC = Codec.STRING.comapFlatMap(value -> {
            try {
                var id = Identifier.tryParse(value);
                if (id == null) return DataResult.error(() -> "Invalid program snapshot type " + value);
                ProgramValueSnapshots.requireType(id);
                return DataResult.success(id);
            } catch (IllegalArgumentException failure) {
                return DataResult.error(() -> "Unsupported program snapshot type " + value);
            }
        }, Identifier::toString);
        public static final Codec<Configuration> CODEC = TYPE_CODEC.fieldOf("type")
                .xmap(Configuration::new, Configuration::typeId).codec();

        public Configuration {
            ProgramValueSnapshots.requireType(typeId);
        }

        public ProgramValueType type() {
            return ProgramValueSnapshots.requireType(typeId);
        }
    }

    private static final class Type implements ProgramNodeType<Configuration> {
        @Override public Codec<Configuration> configurationCodec() { return Configuration.CODEC; }
        @Override public int schemaVersion() { return 1; }
        @Override public ProgramNodeRole role() { return ProgramNodeRole.CONTROL; }
        @Override public ProgramNodePurity purity() { return ProgramNodePurity.STATE; }
        @Override public ProgramNodeScope scope() { return ProgramNodeScope.COMMON; }

        @Override
        public List<ProgramConfigurationPort> configurationPorts(Configuration configuration) {
            // A runtime input must not change the graph's fixed value-port type.
            return List.of();
        }

        @Override
        public ProgramNodeSchema schema(Configuration configuration) {
            return new ProgramNodeSchema(List.of(
                    new ProgramPortDefinition("flow", ProgramValueTypes.FLOW, true,
                            ProgramPortDefinition.UNBOUNDED_CONNECTIONS),
                    ProgramPortDefinition.requiredInput("value", configuration.type())
            ), List.of(
                    ProgramPortDefinition.output("value", configuration.type()),
                    new ProgramPortDefinition("flow", ProgramValueTypes.FLOW, false, 1)
            ));
        }
    }
}
