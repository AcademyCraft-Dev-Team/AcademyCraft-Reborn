package org.academy.internal.common.ability.program;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.academy.api.common.ability.program.ProgramValue;
import org.academy.api.common.ability.program.ProgramValueType;
import org.academy.api.common.ability.program.ProgramValueTypes;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.server.world.level.storage.Player;

import java.math.BigInteger;
import java.util.Map;
import java.util.Optional;

/** Persistent, category-scoped values shared by a player's program slots. */
public final class ProgramSharedVariables {
    public static final int MAX_VARIABLES = 64;
    public static final int MAX_VALUE_LENGTH = 256;

    private ProgramSharedVariables() {
    }

    public static Identifier category(ServerPlayer player) {
        var category = AbilitySystemServer.getSystem(player)
                .getPlayerAbilityCategory(player.getUUID());
        if (category == null) throw new IllegalStateException("Program owner has no ability category");
        return category.getKey();
    }

    public static Optional<ProgramValue<?>> read(ServerPlayer player, Identifier category, String name) {
        validateName(name);
        var saved = data(player).getProgramSharedVariables(category.toString()).get(name);
        if (saved == null) return Optional.empty();
        try {
            return Optional.of(decode(saved));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    public static Map<String, SavedValue> snapshot(ServerPlayer player, Identifier category) {
        return data(player).getProgramSharedVariables(category.toString());
    }

    public static void write(ServerPlayer player, Identifier category, String name, ProgramValue<?> value) {
        validateName(name);
        var saved = encode(value);
        var data = data(player);
        var existing = data.getProgramSharedVariables(category.toString());
        if (!existing.containsKey(name) && existing.size() >= MAX_VARIABLES) {
            throw new IllegalStateException("Program shared variable limit exceeded");
        }
        data.setProgramSharedVariable(category.toString(), name, saved);
    }

    public static void validateValue(ProgramValue<?> value) {
        encode(value);
    }

    public static void restore(ServerPlayer player, Identifier category, String name, SavedValue value) {
        data(player).setProgramSharedVariable(category.toString(), name, value);
    }

    public static void remove(ServerPlayer player, Identifier category, String name) {
        validateName(name);
        data(player).removeProgramSharedVariable(category.toString(), name);
    }

    public static void clear(ServerPlayer player, Identifier category) {
        data(player).clearProgramSharedVariables(category.toString());
    }

    public static ProgramValue<?> defaultValue(ProgramValueType type) {
        if (type.equals(ProgramValueTypes.BOOLEAN)) return new ProgramValue<>(type, false);
        if (type.equals(ProgramValueTypes.INTEGER)) return new ProgramValue<>(type, 0);
        if (type.equals(ProgramValueTypes.BIG_INTEGER)) return new ProgramValue<>(type, BigInteger.ZERO);
        if (type.equals(ProgramValueTypes.FLOAT)) return new ProgramValue<>(type, 0.0);
        if (type.equals(ProgramValueTypes.TEXT)) return new ProgramValue<>(type, "");
        throw new IllegalArgumentException("Unsupported shared variable type " + type.id());
    }

    public static boolean supported(ProgramValueType type) {
        return type.equals(ProgramValueTypes.BOOLEAN)
                || type.equals(ProgramValueTypes.INTEGER)
                || type.equals(ProgramValueTypes.BIG_INTEGER)
                || type.equals(ProgramValueTypes.FLOAT)
                || type.equals(ProgramValueTypes.TEXT);
    }

    public static void validateName(String name) {
        if (name == null || name.isBlank() || !name.equals(name.strip())
                || name.codePointCount(0, name.length()) > 64
                || name.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid shared variable name");
        }
    }

    private static Player data(ServerPlayer player) {
        var data = AbilitySystemServer.getSystem(player).getPlayerData(player.getUUID());
        if (data == null) throw new IllegalStateException("Program owner data is unavailable");
        return data;
    }

    static SavedValue encode(ProgramValue<?> value) {
        if (value == null || !supported(value.type())) {
            throw new IllegalArgumentException("Unsupported shared variable type");
        }
        var raw = value.value();
        String encoded;
        if (value.type().equals(ProgramValueTypes.BOOLEAN)) encoded = Boolean.toString((Boolean) raw);
        else if (value.type().equals(ProgramValueTypes.INTEGER)) encoded = Integer.toString((Integer) raw);
        else if (value.type().equals(ProgramValueTypes.BIG_INTEGER)) encoded = ((BigInteger) raw).toString();
        else if (value.type().equals(ProgramValueTypes.FLOAT)) {
            var number = ((Number) raw).doubleValue();
            if (!Double.isFinite(number)) throw new IllegalArgumentException("Shared variable must be finite");
            encoded = Double.toString(number);
        } else encoded = (String) raw;
        if (encoded.codePointCount(0, encoded.length()) > MAX_VALUE_LENGTH) {
            throw new IllegalArgumentException("Shared variable value is too long");
        }
        return new SavedValue(value.type().id().toString(), encoded);
    }

    static ProgramValue<?> decode(SavedValue saved) {
        if (saved == null || saved.type() == null || saved.value() == null
                || saved.value().codePointCount(0, saved.value().length()) > MAX_VALUE_LENGTH) {
            throw new IllegalArgumentException("Invalid saved shared variable");
        }
        var id = Identifier.parse(saved.type());
        var value = saved.value();
        if (id.equals(ProgramValueTypes.BOOLEAN.id())) {
            if (!value.equals("true") && !value.equals("false")) throw new IllegalArgumentException();
            return new ProgramValue<>(ProgramValueTypes.BOOLEAN, Boolean.parseBoolean(value));
        }
        if (id.equals(ProgramValueTypes.INTEGER.id())) {
            return new ProgramValue<>(ProgramValueTypes.INTEGER, Integer.parseInt(value));
        }
        if (id.equals(ProgramValueTypes.BIG_INTEGER.id())) {
            return new ProgramValue<>(ProgramValueTypes.BIG_INTEGER, new BigInteger(value));
        }
        if (id.equals(ProgramValueTypes.FLOAT.id())) {
            var number = Double.parseDouble(value);
            if (!Double.isFinite(number)) throw new IllegalArgumentException();
            return new ProgramValue<>(ProgramValueTypes.FLOAT, number);
        }
        if (id.equals(ProgramValueTypes.TEXT.id())) {
            return new ProgramValue<>(ProgramValueTypes.TEXT, value);
        }
        throw new IllegalArgumentException("Unsupported shared variable type");
    }

    public record SavedValue(String type, String value) {
    }
}
