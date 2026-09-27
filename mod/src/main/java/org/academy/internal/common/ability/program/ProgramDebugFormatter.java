package org.academy.internal.common.ability.program;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import org.academy.api.common.ability.program.ProgramBlockPosition;
import org.academy.api.common.ability.program.ProgramDirection;
import org.academy.api.common.ability.program.ProgramVector;
import org.academy.api.common.ability.program.ProgramWorldPosition;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;

/**
 * Deterministic, bounded chat formatting for precision-operation diagnostics.
 */
public final class ProgramDebugFormatter {
    public static final int MAX_LIST_ITEMS = 32;
    public static final int MAX_OUTPUT_LENGTH = 1024;

    private ProgramDebugFormatter() {
    }

    public static String render(String text, Object value) {
        var formatted = formatValue(value);
        var template = text == null ? "" : text.replace('\r', ' ').replace('\n', ' ');
        var result = template.contains("{value}")
                ? template.replace("{value}", formatted)
                : template.isBlank() ? formatted : template + " " + formatted;
        result = result.strip();
        return result.length() <= MAX_OUTPUT_LENGTH
                ? result : result.substring(0, MAX_OUTPUT_LENGTH - 1) + "…";
    }

    public static String formatValue(Object value) {
        if (value instanceof Entity entity) {
            return entity.getDisplayName().getString()
                    + " [" + BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType())
                    + ", " + entity.getStringUUID() + "]";
        }
        if (value instanceof ProgramWorldPosition position) {
            return position.dimension() + " @ " + vector(position.x(), position.y(), position.z());
        }
        if (value instanceof ProgramBlockPosition position) {
            return position.dimension() + " @ (" + position.x() + ", "
                    + position.y() + ", " + position.z() + ")";
        }
        if (value instanceof ProgramDirection direction) {
            return vector(direction.x(), direction.y(), direction.z());
        }
        if (value instanceof ProgramVector direction) {
            return vector(direction.x(), direction.y(), direction.z());
        }
        if (value instanceof Collection<?> collection) {
            return formatCollection(collection);
        }
        if (value instanceof Iterable<?> iterable) {
            var copied = new ArrayList<>();
            for (var entry : iterable) {
                if (copied.size() >= MAX_LIST_ITEMS + 1) break;
                copied.add(entry);
            }
            return formatCollection(copied);
        }
        if (value instanceof Double number) return number(number);
        if (value instanceof Float number) return number(number.doubleValue());
        return String.valueOf(value);
    }

    private static String formatCollection(Collection<?> values) {
        var formatted = new ArrayList<String>();
        var index = 0;
        for (var value : values) {
            if (index++ >= MAX_LIST_ITEMS) break;
            formatted.add(formatValue(value));
        }
        if (values.size() > MAX_LIST_ITEMS) {
            formatted.add(String.format(Locale.ROOT, "… +%d", values.size() - MAX_LIST_ITEMS));
        }
        return "[" + String.join(", ", formatted) + "]";
    }

    private static String vector(double x, double y, double z) {
        return "(" + number(x) + ", " + number(y) + ", " + number(z) + ")";
    }

    private static String number(double value) {
        if (!Double.isFinite(value)) return Double.toString(value);
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }
}
