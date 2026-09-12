package org.academy.api.common.ability.program;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;

import java.lang.reflect.RecordComponent;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/** Shared discovery and resolution logic for optional node-configuration inputs. */
public final class ProgramConfigurationPorts {
    private static final List<String> STRUCTURAL_FIELDS = List.of("inputs");

    private ProgramConfigurationPorts() {
    }

    /**
     * Discovers scalar record components that can be safely replaced without changing the node's
     * configured port types or control-flow shape. Entry configuration is evaluated outside the
     * VM and is therefore intentionally excluded.
     */
    public static <C> List<ProgramConfigurationPort> infer(
            Codec<C> codec,
            C configuration,
            ProgramNodeRole role
    ) {
        if (configuration == null || role == ProgramNodeRole.ENTRY
                || !configuration.getClass().isRecord()) return List.of();
        var encoded = codec.encodeStart(JsonOps.INSTANCE, configuration).result().orElse(null);
        if (!(encoded instanceof JsonObject object)) return List.of();
        var result = new ArrayList<ProgramConfigurationPort>();
        for (var component : configuration.getClass().getRecordComponents()) {
            var field = snakeCase(component.getName());
            if (STRUCTURAL_FIELDS.contains(field)) continue;
            // A component such as typeId commonly maps to a structural wire field named "type".
            // Do not invent an unusable alias when its inferred wire name is absent.
            if (component.getName().endsWith("Id") && !object.has(field)) continue;
            var type = valueType(configuration, component);
            if (type != null) result.add(new ProgramConfigurationPort(field, type));
        }
        return List.copyOf(result);
    }

    /** Appends every configuration port that is not already present in the base schema. */
    public static ProgramNodeSchema attach(
            ProgramNodeSchema schema,
            List<ProgramConfigurationPort> ports
    ) {
        if (ports.isEmpty()) return schema;
        var inputs = new ArrayList<>(schema.inputs());
        for (var port : ports) {
            if (schema.input(port.field()).isPresent()) continue;
            inputs.add(ProgramPortDefinition.optionalInput(port.field(), port.type()));
        }
        return new ProgramNodeSchema(inputs, schema.outputs());
    }

    /** Resolves connected overrides through the node codec so all normal bounds checks remain. */
    public static <C> C resolve(
            Codec<C> codec,
            C configuration,
            List<ProgramConfigurationPort> ports,
            ProgramInputView inputs
    ) {
        if (ports.isEmpty()) return configuration;
        var encoded = codec.encodeStart(JsonOps.INSTANCE, configuration).getOrThrow();
        if (!(encoded instanceof JsonObject object)) return configuration;
        JsonObject changed = null;
        for (var port : ports) {
            var supplied = inputs.first(port.field()).orElse(null);
            if (supplied == null) continue;
            if (!ProgramValueTypes.canConnect(supplied.type(), port.type())) {
                throw new IllegalArgumentException(
                        "Incompatible program configuration input " + port.field());
            }
            var existing = object.get(port.field());
            if (existing != null && !existing.isJsonPrimitive()) continue;
            if (changed == null) changed = object.deepCopy();
            changed.add(port.field(), encodedValue(port.type(), supplied.value(), existing));
        }
        return changed == null
                ? configuration
                : codec.parse(JsonOps.INSTANCE, changed).getOrThrow();
    }

    private static ProgramValueType valueType(Object configuration, RecordComponent component) {
        var javaType = component.getType();
        if (javaType == byte.class || javaType == Byte.class
                || javaType == short.class || javaType == Short.class
                || javaType == int.class || javaType == Integer.class) return ProgramValueTypes.INTEGER;
        if (javaType == long.class || javaType == Long.class
                || javaType == BigInteger.class) return ProgramValueTypes.BIG_INTEGER;
        if (javaType == float.class || javaType == Float.class
                || javaType == double.class || javaType == Double.class) return ProgramValueTypes.FLOAT;
        if (javaType == ProgramTag.class) return ProgramValueTypes.TAG;
        if (javaType != String.class) return null;
        var numeric = numericKind(configuration);
        return numeric == null ? ProgramValueTypes.TEXT : numeric;
    }

    private static ProgramValueType numericKind(Object configuration) {
        for (var component : configuration.getClass().getRecordComponents()) {
            if (!component.getName().equals("kind")) continue;
            try {
                var accessor = component.getAccessor();
                accessor.trySetAccessible();
                var candidate = accessor.invoke(configuration);
                if (candidate == null) continue;
                var method = candidate.getClass().getMethod("type");
                var result = method.invoke(candidate);
                if (result instanceof ProgramValueType type
                        && (type.equals(ProgramValueTypes.INTEGER)
                        || type.equals(ProgramValueTypes.BIG_INTEGER)
                        || type.equals(ProgramValueTypes.FLOAT))) return type;
            } catch (NoSuchMethodException ignored) {
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Cannot inspect numeric program configuration", exception);
            }
        }
        return null;
    }

    private static JsonElement encodedValue(
            ProgramValueType targetType,
            Object value,
            JsonElement existing
    ) {
        if (targetType.equals(ProgramValueTypes.BOOLEAN)) {
            return new JsonPrimitive((Boolean) value);
        }
        if (targetType.equals(ProgramValueTypes.INTEGER)) {
            var number = ((Number) value).intValue();
            return existing != null && existing.getAsJsonPrimitive().isString()
                    ? new JsonPrimitive(Integer.toString(number)) : new JsonPrimitive(number);
        }
        if (targetType.equals(ProgramValueTypes.BIG_INTEGER)) {
            var number = value instanceof BigInteger bigInteger
                    ? bigInteger : BigInteger.valueOf(((Number) value).longValue());
            return existing != null && existing.getAsJsonPrimitive().isString()
                    ? new JsonPrimitive(number.toString()) : new JsonPrimitive(number);
        }
        if (targetType.equals(ProgramValueTypes.FLOAT)) {
            var number = ((Number) value).doubleValue();
            return existing != null && existing.getAsJsonPrimitive().isString()
                    ? new JsonPrimitive(Double.toString(number)) : new JsonPrimitive(number);
        }
        if (targetType.equals(ProgramValueTypes.TEXT)) {
            return new JsonPrimitive(value.toString());
        }
        if (targetType.equals(ProgramValueTypes.IDENTIFIER)) {
            return new JsonPrimitive(((Identifier) value).toString());
        }
        if (targetType.equals(ProgramValueTypes.TAG)) {
            return new JsonPrimitive(((ProgramTag) value).id().toString());
        }
        throw new IllegalArgumentException("Unsupported program configuration input type " + targetType);
    }

    private static String snakeCase(String value) {
        var result = new StringBuilder(value.length() + 4);
        for (var index = 0; index < value.length(); index++) {
            var character = value.charAt(index);
            if (Character.isUpperCase(character)) {
                result.append('_').append(Character.toLowerCase(character));
            } else {
                result.append(character);
            }
        }
        return result.toString();
    }
}
