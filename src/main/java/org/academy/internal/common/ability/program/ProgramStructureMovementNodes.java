package org.academy.internal.common.ability.program;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import org.academy.api.common.ability.program.*;

import java.util.List;

/** Bounded displacement action shared by the three structure-controlling categories. */
public final class ProgramStructureMovementNodes {
    private ProgramStructureMovementNodes() {}

    public record Configuration(double durationSeconds) {
        public static final Codec<Configuration> CODEC = Codec.DOUBLE.comapFlatMap(value ->
                Double.isFinite(value) && value >= .1 && value <= 10
                        ? DataResult.success(value) : DataResult.error(() -> "Movement duration must be in [0.1, 10] seconds"),
                value -> value).optionalFieldOf("duration_seconds", 1.0).codec().xmap(Configuration::new, Configuration::durationSeconds);
        public Configuration {
            if (!Double.isFinite(durationSeconds) || durationSeconds < .1 || durationSeconds > 10) {
                throw new IllegalArgumentException("Invalid movement duration");
            }
        }
        public ProgramStructureMoveRequest request(ProgramVector displacement) {
            return new ProgramStructureMoveRequest(displacement, (int) Math.ceil(durationSeconds * 20));
        }
        public ProgramStructureMoveRequest request(ProgramVector displacement, ProgramInputView inputs) {
            var seconds = inputs.first("seconds").isEmpty() ? durationSeconds
                    : ((Number) inputs.requireCompatible("seconds", ProgramValueTypes.FLOAT).value()).doubleValue();
            return new Configuration(seconds).request(displacement);
        }
    }
    public static JsonObject defaults() {
        var config = new JsonObject(); config.addProperty("duration_seconds", 1.0); return config;
    }
    public static ProgramNodeType<Configuration> type(ProgramNodeScope scope) { return new Type(scope); }
    private record Type(ProgramNodeScope scope) implements ProgramNodeType<Configuration> {
        @Override public Codec<Configuration> configurationCodec() { return Configuration.CODEC; }
        @Override public int schemaVersion() { return 1; }
        @Override public ProgramNodeRole role() { return ProgramNodeRole.ACTION; }
        @Override public ProgramNodePurity purity() { return ProgramNodePurity.ACTION; }
        @Override public ProgramNodeSchema schema(Configuration config) {
            return new ProgramNodeSchema(List.of(ProgramPortDefinition.requiredInput("flow", ProgramValueTypes.FLOW),
                    ProgramPortDefinition.optionalInput("structures", ProgramValueTypes.STRUCTURE_SET),
                    ProgramPortDefinition.optionalInput("structure", ProgramValueTypes.STRUCTURE_REFERENCE),
                    ProgramPortDefinition.optionalInput("displacement", ProgramValueTypes.VECTOR),
                    ProgramPortDefinition.optionalInput("seconds", ProgramValueTypes.FLOAT)),
                    List.of(ProgramPortDefinition.output("structures", ProgramValueTypes.STRUCTURE_SET),
                            ProgramPortDefinition.output("result", ProgramValueTypes.STRUCTURE_OPERATION_RESULT),
                            ProgramPortDefinition.output("flow", ProgramValueTypes.FLOW)));
        }
    }
}
