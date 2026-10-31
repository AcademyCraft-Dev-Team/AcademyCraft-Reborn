package org.academy.api.common.ability.program;

import net.minecraft.resources.Identifier;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Explicit, invocation-local value sampling for program nodes and extensions.
 *
 * <p>A snapshot copies collection membership and normalizes numeric values to immutable wrappers.
 * Entity references retain the resolver's opaque identity; structure references retain their
 * activation identity. Neither freezes a target's live properties, grants control, nor replaces
 * the authorization and validity checks of a later action. To save a target's position, sample
 * a position value rather than its reference.</p>
 *
 * <p>This API does not evaluate an upstream graph or force an already latched source to refresh.
 * Control leases, action receipts and types without a defined snapshot representation are not
 * supported. Snapshots are runtime values, not persistent or shared-variable storage.</p>
 */
public final class ProgramValueSnapshots {
    private static final List<ProgramValueType> SUPPORTED_TYPES = List.of(
            ProgramValueTypes.BOOLEAN,
            ProgramValueTypes.INTEGER,
            ProgramValueTypes.BIG_INTEGER,
            ProgramValueTypes.FLOAT,
            ProgramValueTypes.TEXT,
            ProgramValueTypes.TAG,
            ProgramValueTypes.IDENTIFIER,
            ProgramValueTypes.VECTOR,
            ProgramValueTypes.DIRECTION,
            ProgramValueTypes.WORLD_POSITION,
            ProgramValueTypes.BLOCK_POSITION,
            ProgramValueTypes.ENTITY_REFERENCE,
            ProgramValueTypes.LIVING_ENTITY_REFERENCE,
            ProgramValueTypes.STRUCTURE_REFERENCE,
            ProgramValueTypes.STRUCTURE_SET,
            ProgramValueTypes.STRUCTURE_ANCHOR,
            ProgramValueTypes.STRUCTURE_POSE,
            ProgramValueTypes.DIRECTION_SET,
            ProgramValueTypes.WORLD_POSITION_SET,
            ProgramValueTypes.BLOCK_POSITION_SET,
            ProgramValueTypes.ENTITY_SET,
            ProgramValueTypes.LIVING_ENTITY_SET
    );
    private static final Map<Identifier, ProgramValueType> TYPES_BY_ID = SUPPORTED_TYPES.stream()
            .collect(Collectors.toUnmodifiableMap(ProgramValueType::id, Function.identity()));

    private ProgramValueSnapshots() {
    }

    /** Deterministic finite choices for a snapshot node's fixed port type. */
    public static List<ProgramValueType> supportedTypes() {
        return SUPPORTED_TYPES;
    }

    public static boolean supports(ProgramValueType type) {
        return type != null && TYPES_BY_ID.containsKey(type.id());
    }

    public static ProgramValueType requireType(Identifier id) {
        var type = TYPES_BY_ID.get(Objects.requireNonNull(id, "id"));
        if (type == null) throw new IllegalArgumentException("Unsupported program snapshot type " + id);
        return type;
    }

    /** Samples a value without changing its declared type. */
    public static ProgramValue<?> snapshot(ProgramValue<?> source) {
        Objects.requireNonNull(source, "source");
        return snapshot(source, source.type());
    }

    /**
     * Samples using only the existing compatible port conversions. In particular, this does not
     * silently turn an entity or structure reference into a world position.
     *
     * @throws IllegalArgumentException for unsupported types, incompatible ports or malformed raw values
     */
    public static ProgramValue<?> snapshot(ProgramValue<?> source, ProgramValueType targetType) {
        Objects.requireNonNull(source, "source");
        requireType(source.type().id());
        requireType(targetType.id());
        if (!ProgramValueTypes.canConnect(source.type(), targetType)) {
            throw new IllegalArgumentException("Cannot sample incompatible program value");
        }
        var raw = source.value();
        if (source.type().equals(ProgramValueTypes.TAG) && targetType.equals(ProgramValueTypes.TEXT)) {
            raw = cast(raw, ProgramTag.class).toString();
        } else if (source.type().equals(ProgramValueTypes.INTEGER)
                && targetType.equals(ProgramValueTypes.FLOAT)) {
            raw = integer(raw).doubleValue();
        }
        return new ProgramValue<>(targetType, copy(targetType, raw));
    }

    private static Object copy(ProgramValueType type, Object raw) {
        if (type.equals(ProgramValueTypes.BOOLEAN)) return cast(raw, Boolean.class);
        if (type.equals(ProgramValueTypes.INTEGER)) return integer(raw);
        if (type.equals(ProgramValueTypes.BIG_INTEGER)) return bigInteger(raw);
        if (type.equals(ProgramValueTypes.FLOAT)) {
            var value = cast(raw, Number.class).doubleValue();
            if (!Double.isFinite(value)) throw new IllegalArgumentException("Sampled float must be finite");
            return value;
        }
        if (type.equals(ProgramValueTypes.TEXT)) return cast(raw, String.class);
        if (type.equals(ProgramValueTypes.TAG)) return cast(raw, ProgramTag.class);
        if (type.equals(ProgramValueTypes.IDENTIFIER)) return cast(raw, Identifier.class);
        if (type.equals(ProgramValueTypes.VECTOR)) return cast(raw, ProgramVector.class);
        if (type.equals(ProgramValueTypes.DIRECTION)) return cast(raw, ProgramDirection.class);
        if (type.equals(ProgramValueTypes.WORLD_POSITION)) return cast(raw, ProgramWorldPosition.class);
        if (type.equals(ProgramValueTypes.BLOCK_POSITION)) return cast(raw, ProgramBlockPosition.class);
        if (type.equals(ProgramValueTypes.STRUCTURE_REFERENCE)) return cast(raw, ProgramStructureReference.class);
        if (type.equals(ProgramValueTypes.STRUCTURE_ANCHOR)) return cast(raw, ProgramStructureAnchor.class);
        if (type.equals(ProgramValueTypes.STRUCTURE_POSE)) return cast(raw, ProgramStructurePose.class);
        if (type.equals(ProgramValueTypes.STRUCTURE_SET)) return copyList(raw, ProgramStructureReference.class);
        if (type.equals(ProgramValueTypes.DIRECTION_SET)) return copyList(raw, ProgramDirection.class);
        if (type.equals(ProgramValueTypes.WORLD_POSITION_SET)) return copyList(raw, ProgramWorldPosition.class);
        if (type.equals(ProgramValueTypes.BLOCK_POSITION_SET)) return copyList(raw, ProgramBlockPosition.class);
        if (type.equals(ProgramValueTypes.ENTITY_SET)
                || type.equals(ProgramValueTypes.LIVING_ENTITY_SET)) return copyList(raw, Object.class);
        // ProgramTargetResolver intentionally accepts opaque references, including extension identities.
        if (type.equals(ProgramValueTypes.ENTITY_REFERENCE)
                || type.equals(ProgramValueTypes.LIVING_ENTITY_REFERENCE)) return raw;
        throw new IllegalArgumentException("Unsupported program snapshot type " + type.id());
    }

    private static Integer integer(Object raw) {
        if (raw instanceof Integer value) return value;
        try {
            return decimal(raw).intValueExact();
        } catch (ArithmeticException | NumberFormatException failure) {
            throw new IllegalArgumentException("Sampled integer must be exact and in range", failure);
        }
    }

    private static BigInteger bigInteger(Object raw) {
        if (raw instanceof BigInteger value) return new BigInteger(value.toByteArray());
        try {
            return decimal(raw).toBigIntegerExact();
        } catch (ArithmeticException | NumberFormatException failure) {
            throw new IllegalArgumentException("Sampled big integer must be integral", failure);
        }
    }

    private static BigDecimal decimal(Object raw) {
        return new BigDecimal(cast(raw, Number.class).toString());
    }

    private static List<?> copyList(Object raw, Class<?> memberType) {
        var values = cast(raw, List.class);
        for (var value : values) cast(value, memberType);
        return List.copyOf(values);
    }

    private static <T> T cast(Object raw, Class<T> type) {
        if (!type.isInstance(raw)) {
            throw new IllegalArgumentException("Unexpected raw value for program snapshot: " + type.getSimpleName());
        }
        return type.cast(raw);
    }
}
