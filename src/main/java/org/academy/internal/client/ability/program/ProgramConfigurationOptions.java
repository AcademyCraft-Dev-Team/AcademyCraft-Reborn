package org.academy.internal.client.ability.program;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import net.minecraft.network.chat.Component;
import org.academy.AcademyCraft;
import org.academy.internal.common.ability.mentalout.precision.PrecisionGraph;
import org.academy.internal.common.ability.program.CommonProgramNodeIds;
import org.academy.internal.common.ability.program.ProgramEditorNodeCatalog;
import org.academy.internal.common.ability.meltdowner.program.MeltdownerProgramNodeIds;
import org.academy.internal.common.ability.electromaster.program.ElectromasterProgramNodeIds;
import org.academy.internal.common.ability.teleport.program.TeleportProgramNodeIds;

import java.util.List;
import java.util.stream.IntStream;

/** Finite node-configuration choices rendered as inspector step buttons. */
public final class ProgramConfigurationOptions {
    private static final List<String> VARIABLE_TYPE_PATHS = List.of(
            "boolean",
            "integer",
            "big_integer",
            "float",
            "identifier",
            "duration",
            "direction",
            "world_position",
            "block_position",
            "entity_reference",
            "living_entity_reference",
            "direction_set",
            "world_position_set",
            "block_position_set",
            "entity_set",
            "living_entity_set"
    );

    private ProgramConfigurationOptions() {
    }

    public static List<Option> options(
            ProgramEditorNodeCatalog.Entry entry,
            String field,
            JsonElement currentValue
    ) {
        if (currentValue != null
                && currentValue.isJsonPrimitive()
                && currentValue.getAsJsonPrimitive().isBoolean()) {
            if (field.equals("enabled")) {
                return List.of(
                        option(false, "screen.academy.program.configuration.enabled.off"),
                        option(true, "screen.academy.program.configuration.enabled.on")
                );
            }
            return List.of(
                    option(false, "screen.academy.program.configuration.boolean.false"),
                    option(true, "screen.academy.program.configuration.boolean.true")
            );
        }
        var id = entry.id();
        if (id.equals(CommonProgramNodeIds.SCALAR_CONSTANT) && field.equals("type")) {
            return scalarTypes(true);
        }
        if (id.equals(CommonProgramNodeIds.NUMERIC_ARITHMETIC)) {
            if (field.equals("type")) return scalarTypes(false);
            if (field.equals("operator")) {
                return stringOptions(
                        "screen.academy.program.configuration.arithmetic.",
                        "add", "subtract", "multiply", "divide", "modulo", "absolute"
                );
            }
        }
        if (id.equals(CommonProgramNodeIds.RANDOM_NUMBER) && field.equals("type")) {
            return scalarTypes(false);
        }
        if (id.equals(CommonProgramNodeIds.VEC3_OPERATION)) {
            if (field.equals("type")) {
                return stringOptions(
                        "screen.academy.program.configuration.vec3_type.",
                        "direction", "world_position");
            }
            if (field.equals("operator")) {
                return stringOptions(
                        "screen.academy.program.configuration.vec3_operator.",
                        "dot", "cross", "add");
            }
        }
        if (id.equals(CommonProgramNodeIds.SORT_POINTS_BY_DISTANCE)) {
            if (field.equals("type")) {
                return stringOptions(
                        "screen.academy.program.configuration.point_type.",
                        "entity", "world_position", "block_position");
            }
            if (field.equals("order")) {
                return stringOptions(
                        "screen.academy.program.configuration.sort_order.",
                        "ascending", "descending");
            }
        }
        if (id.equals(CommonProgramNodeIds.NUMERIC_COMPARE)) {
            if (field.equals("type")) return scalarTypes(false);
            if (field.equals("operator")) {
                return stringOptions(
                        "screen.academy.program.configuration.comparison.",
                        "equal", "less", "less_equal", "greater", "greater_equal"
                );
            }
        }
        if (id.equals(CommonProgramNodeIds.FILTER_ENTITY_TYPE) && field.equals("type")) {
            return stringOptions(
                    "screen.academy.program.configuration.entity_type.",
                    "any", "living", "player", "mob", "hostile", "animal", "friendly",
                    "projectile", "item"
            );
        }
        if (id.equals(CommonProgramNodeIds.LOOK_TARGET) && field.equals("target_type")) {
            return stringOptions(
                    "screen.academy.program.configuration.look_target.",
                    "entity", "block"
            );
        }
        if (id.equals(CommonProgramNodeIds.ENTITY_POSITION) && field.equals("anchor")) {
            return stringOptions(
                    "screen.academy.program.configuration.entity_position_anchor.",
                    "feet", "center", "eyes"
            );
        }
        if (id.equals(CommonProgramNodeIds.BLOCK_NORMAL) && field.equals("mode")) {
            return stringOptions(
                    "screen.academy.program.configuration.block_normal.",
                    "view", "position_direction"
            );
        }
        if (id.equals(CommonProgramNodeIds.TRIGGER_MOVEMENT) && field.equals("condition")) {
            return stringOptions(
                    "screen.academy.program.configuration.movement.",
                    "jump", "sneak", "sprint", "elytra", "swim"
            );
        }
        if (id.equals(CommonProgramNodeIds.TRIGGER_HEALTH_THRESHOLD) && field.equals("mode")) {
            return stringOptions(
                    "screen.academy.program.configuration.health_threshold.",
                    "below", "above"
            );
        }
        if ((id.equals(MeltdownerProgramNodeIds.ELECTRON_BEAM)
                || id.equals(MeltdownerProgramNodeIds.MINING_BEAM))
                && field.equals("aim_mode")) {
            return stringOptions(
                    "screen.academy.program.configuration.aim_mode.",
                    "direction", "target"
            );
        }
        if ((id.equals(ElectromasterProgramNodeIds.ENERGY_DETECTION)
                || id.equals(ElectromasterProgramNodeIds.CURRENT_RECHARGE)
                || id.equals(ElectromasterProgramNodeIds.MAGNETIC_MOVE))
                && field.equals("target_type")) {
            return stringOptions(
                    "screen.academy.program.configuration.energy_target.",
                    "entity", "block"
            );
        }
        if ((id.equals(ElectromasterProgramNodeIds.ENERGY_DETECTION)
                || id.equals(ElectromasterProgramNodeIds.REDSTONE_DETECTION))
                && field.equals("mode")) {
            return stringOptions(
                    "screen.academy.program.configuration.detection_mode.",
                    "below", "above"
            );
        }
        if (id.equals(ElectromasterProgramNodeIds.MAGNETIC_MOVE) && field.equals("mode")) {
            return stringOptions(
                    "screen.academy.program.configuration.magnetic_mode.",
                    "pull", "launch"
            );
        }
        if (id.equals(TeleportProgramNodeIds.ENTITY_TELEPORT)
                && field.equals("target_type")) {
            return stringOptions(
                    "screen.academy.program.configuration.teleport_target.",
                    "entity", "block"
            );
        }
        if (id.equals(TeleportProgramNodeIds.BLOCK_ITEM_TELEPORT)
                && field.equals("mode")) {
            return stringOptions(
                    "screen.academy.program.configuration.block_item_teleport.",
                    "place", "collect"
            );
        }
        if ((id.equals(CommonProgramNodeIds.VARIABLE_GET)
                || id.equals(CommonProgramNodeIds.VARIABLE_SET)) && field.equals("type")) {
            return VARIABLE_TYPE_PATHS.stream().map(path -> option(
                    AcademyCraft.academy("program_type/" + path).toString(),
                    "screen.academy.program.configuration.value_type." + path
            )).toList();
        }
        if (field.equals("strength")
                && currentValue != null && currentValue.isJsonPrimitive()
                && currentValue.getAsJsonPrimitive().isNumber()) {
            return List.of(
                    option(0, "screen.academy.program.configuration.power.controlled"),
                    option(1, "screen.academy.program.configuration.power.standard"),
                    option(2, "screen.academy.program.configuration.power.maximum")
            );
        }
        if (field.equals("parameter")) {
            var kind = entry.metadata(PrecisionGraph.NodeKind.class).orElse(null);
            if (kind != null) return precisionOptions(kind.parameterKind());
        }
        return List.of();
    }

    public static boolean isPowerSlider(String field, JsonElement currentValue) {
        return field.equals("power")
                && currentValue != null
                && currentValue.isJsonPrimitive()
                && currentValue.getAsJsonPrimitive().isNumber();
    }

    public static boolean isToggle(String field, JsonElement currentValue) {
        return field.equals("enabled")
                && currentValue != null
                && currentValue.isJsonPrimitive()
                && currentValue.getAsJsonPrimitive().isBoolean();
    }

    /** Returns the adjacent option, wrapping in both directions. */
    public static Option step(List<Option> options, JsonElement currentValue, int direction) {
        if (options.isEmpty()) throw new IllegalArgumentException("Options must not be empty");
        var currentIndex = 0;
        for (var index = 0; index < options.size(); index++) {
            if (sameValue(options.get(index).value(), currentValue)) {
                currentIndex = index;
                break;
            }
        }
        return options.get(Math.floorMod(currentIndex + Integer.signum(direction), options.size()));
    }

    public static Option selected(List<Option> options, JsonElement currentValue) {
        return options.stream()
                .filter(option -> sameValue(option.value(), currentValue))
                .findFirst()
                .orElseGet(options::getFirst);
    }

    private static List<Option> scalarTypes(boolean includeBoolean) {
        var options = stringOptions(
                "screen.academy.program.configuration.scalar_type.",
                "boolean", "integer", "big_integer", "float"
        );
        return includeBoolean ? options : options.subList(1, options.size());
    }

    private static List<Option> precisionOptions(PrecisionGraph.ParameterKind kind) {
        return switch (kind) {
            case CAPABILITY -> indexedOptions(
                    7, "screen.academy.precision_operation.value.capability."
            );
            case SORT_DIRECTION -> List.of(
                    option(0.0, "screen.academy.precision_operation.value.near_first"),
                    option(1.0, "screen.academy.precision_operation.value.far_first")
            );
            case ENTITY_TYPE -> indexedOptions(
                    8, "screen.academy.precision_operation.value.entity_type."
            );
            default -> List.of();
        };
    }

    private static List<Option> indexedOptions(int count, String translationPrefix) {
        return IntStream.range(0, count)
                .mapToObj(index -> option((double) index, translationPrefix + index))
                .toList();
    }

    private static List<Option> stringOptions(String translationPrefix, String... values) {
        return java.util.Arrays.stream(values)
                .map(value -> option(value, translationPrefix + value))
                .toList();
    }

    private static Option option(boolean value, String translationKey) {
        return new Option(new JsonPrimitive(value), translationKey);
    }

    private static Option option(int value, String translationKey) {
        return new Option(new JsonPrimitive(value), translationKey);
    }

    private static Option option(double value, String translationKey) {
        return new Option(new JsonPrimitive(value), translationKey);
    }

    private static Option option(String value, String translationKey) {
        return new Option(new JsonPrimitive(value), translationKey);
    }

    private static boolean sameValue(JsonPrimitive option, JsonElement currentValue) {
        return currentValue != null && currentValue.isJsonPrimitive()
                && option.equals(currentValue.getAsJsonPrimitive());
    }

    public record Option(JsonPrimitive value, String translationKey) {
        public Component label() {
            return Component.translatable(translationKey);
        }
    }
}
