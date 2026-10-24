package org.academy.internal.common.ability.program;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.academy.api.common.ability.program.*;
import org.academy.api.common.structure.upgrade.StructureOrientation;

import java.util.List;
import java.util.Locale;

/** Shared schemas and stable configuration encodings for the new attitude actions. */
public final class ProgramStructureOrientationNodes {
    private static final Codec<Double> SECONDS = finiteRange(0.05, 60.0, false);
    private static final Codec<Double> ANGULAR_SPEED = finiteRange(
            -StructureOrientation.MAX_ANGULAR_SPEED, StructureOrientation.MAX_ANGULAR_SPEED, true);

    private ProgramStructureOrientationNodes() {
    }

    public static ProgramNodeType<ResetConfiguration> resetType(ProgramNodeScope scope) {
        return new OrientationNodeType<>(ResetConfiguration.CODEC, schema(false), scope);
    }

    public static ProgramNodeType<SpinConfiguration> spinType(ProgramNodeScope scope) {
        return new OrientationNodeType<>(SpinConfiguration.CODEC, schema(true), scope);
    }

    public static JsonObject resetDefaults() {
        var result = new JsonObject();
        result.addProperty("reset_mode", "world_up_keep_yaw");
        result.addProperty("missing_baseline", "reject");
        result.addProperty("timeout_seconds", 5.0);
        return result;
    }

    public static JsonObject spinDefaults() {
        var result = new JsonObject();
        result.addProperty("frame", "world");
        result.addProperty("angular_speed", 1.0);
        result.addProperty("duration_seconds", 1.0);
        result.addProperty("finish_mode", "brake");
        return result;
    }

    private static ProgramNodeSchema schema(boolean spin) {
        var inputs = new java.util.ArrayList<>(List.of(
                ProgramPortDefinition.requiredInput("flow", ProgramValueTypes.FLOW),
                ProgramPortDefinition.requiredInput("control", ProgramValueTypes.STRUCTURE_CONTROL)));
        if (spin) inputs.add(ProgramPortDefinition.requiredInput("axis", ProgramValueTypes.DIRECTION));
        return new ProgramNodeSchema(inputs, List.of(
                ProgramPortDefinition.output("result", ProgramValueTypes.STRUCTURE_OPERATION_RESULT),
                ProgramPortDefinition.output("flow", ProgramValueTypes.FLOW)));
    }

    public record ResetConfiguration(StructureOrientation.ResetMode resetMode,
                                     StructureOrientation.MissingBaselinePolicy missingBaseline,
                                     double timeoutSeconds) {
        public static final Codec<ResetConfiguration> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                enumCodec(StructureOrientation.ResetMode.class)
                        .optionalFieldOf("reset_mode", StructureOrientation.ResetMode.WORLD_UP_KEEP_YAW)
                        .forGetter(ResetConfiguration::resetMode),
                enumCodec(StructureOrientation.MissingBaselinePolicy.class)
                        .optionalFieldOf("missing_baseline", StructureOrientation.MissingBaselinePolicy.REJECT)
                        .forGetter(ResetConfiguration::missingBaseline),
                SECONDS.optionalFieldOf("timeout_seconds", 5.0).forGetter(ResetConfiguration::timeoutSeconds)
        ).apply(instance, ResetConfiguration::new));

        public ResetConfiguration {
            java.util.Objects.requireNonNull(resetMode, "resetMode");
            java.util.Objects.requireNonNull(missingBaseline, "missingBaseline");
            requireSeconds(timeoutSeconds);
        }

        public ProgramStructureOrientationRuntime.ResetRequest request() {
            return new ProgramStructureOrientationRuntime.ResetRequest(resetMode, missingBaseline,
                    durationTicks(timeoutSeconds));
        }
    }

    public record SpinConfiguration(StructureOrientation.AxisFrame frame, double angularSpeed,
                                    double durationSeconds, StructureOrientation.StopMode finishMode) {
        public static final Codec<SpinConfiguration> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                enumCodec(StructureOrientation.AxisFrame.class).optionalFieldOf("frame", StructureOrientation.AxisFrame.WORLD)
                        .forGetter(SpinConfiguration::frame),
                ANGULAR_SPEED.optionalFieldOf("angular_speed", 1.0).forGetter(SpinConfiguration::angularSpeed),
                SECONDS.optionalFieldOf("duration_seconds", 1.0).forGetter(SpinConfiguration::durationSeconds),
                enumCodec(StructureOrientation.StopMode.class).optionalFieldOf("finish_mode", StructureOrientation.StopMode.BRAKE)
                        .forGetter(SpinConfiguration::finishMode)
        ).apply(instance, SpinConfiguration::new));

        public SpinConfiguration {
            java.util.Objects.requireNonNull(frame, "frame");
            java.util.Objects.requireNonNull(finishMode, "finishMode");
            requireSeconds(durationSeconds);
            if (!Double.isFinite(angularSpeed) || angularSpeed == 0.0
                    || Math.abs(angularSpeed) > StructureOrientation.MAX_ANGULAR_SPEED) {
                throw new IllegalArgumentException("Spin speed is outside the angular budget");
            }
        }

        public ProgramStructureOrientationRuntime.SpinRequest request(ProgramDirection axis) {
            return new ProgramStructureOrientationRuntime.SpinRequest(axis, frame, angularSpeed,
                    durationTicks(durationSeconds), finishMode);
        }
    }

    private record OrientationNodeType<C>(Codec<C> configurationCodec, ProgramNodeSchema ports,
                                           ProgramNodeScope scope) implements ProgramNodeType<C> {
        @Override public int schemaVersion() { return 1; }
        @Override public ProgramNodeSchema schema(C configuration) { return ports; }
        @Override public ProgramNodeRole role() { return ProgramNodeRole.ACTION; }
        @Override public ProgramNodePurity purity() { return ProgramNodePurity.ACTION; }
    }

    private static <E extends Enum<E>> Codec<E> enumCodec(Class<E> type) {
        return Codec.STRING.comapFlatMap(value -> {
            for (var candidate : type.getEnumConstants()) {
                if (candidate.name().toLowerCase(Locale.ROOT).equals(value)) return DataResult.success(candidate);
            }
            return DataResult.error(() -> "Unknown " + type.getSimpleName() + ": " + value);
        }, value -> value.name().toLowerCase(Locale.ROOT));
    }

    private static Codec<Double> finiteRange(double minimum, double maximum, boolean nonzero) {
        return Codec.DOUBLE.comapFlatMap(value -> Double.isFinite(value) && value >= minimum && value <= maximum
                        && (!nonzero || value != 0.0)
                ? DataResult.success(value) : DataResult.error(() -> "Value outside finite orientation budget"),
                value -> value);
    }

    private static int durationTicks(double seconds) {
        requireSeconds(seconds);
        return (int) Math.ceil(seconds * 20.0);
    }

    private static void requireSeconds(double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0.05 || seconds > 60.0) {
            throw new IllegalArgumentException("Orientation duration must be in [0.05, 60] seconds");
        }
    }
}
