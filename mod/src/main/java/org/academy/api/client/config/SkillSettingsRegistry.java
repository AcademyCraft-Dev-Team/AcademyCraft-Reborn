package org.academy.api.client.config;

import net.minecraft.resources.Identifier;
import org.academy.api.common.ability.Skill;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.*;

public final class SkillSettingsRegistry {
    private static final Map<Identifier, Map<String, Module>> MODULES_BY_SKILL = new LinkedHashMap<>();

    private SkillSettingsRegistry() {
    }

    public static void register(Skill skill, Module module) {
        register(skill.getKey(), module);
    }

    public static void register(Identifier skillId, Module module) {
        var modules = MODULES_BY_SKILL.computeIfAbsent(skillId, ignored -> new LinkedHashMap<>());
        if (modules.putIfAbsent(module.id(), module) != null) {
            throw new IllegalStateException(
                    "Skill settings module '" + module.id() + "' is already registered for " + skillId);
        }
    }

    public static void unregister(Identifier skillId, String moduleId) {
        var modules = MODULES_BY_SKILL.get(skillId);
        if (modules == null) return;
        modules.remove(moduleId);
        if (modules.isEmpty()) MODULES_BY_SKILL.remove(skillId);
    }

    public static List<Module> getModules(Skill skill) {
        var modules = MODULES_BY_SKILL.get(skill.getKey());
        return modules == null ? List.of() : List.copyOf(modules.values());
    }

    public record Module(String id, String titleKey, List<Entry> entries) {
        public Module {
            if (id.isBlank()) {
                throw new IllegalArgumentException("Skill settings module id cannot be blank");
            }
            var entryIds = new HashSet<String>();
            for (var entry : entries) {
                if (!entryIds.add(entry.id())) {
                    throw new IllegalArgumentException(
                            "Skill settings module '" + id + "' contains duplicate entry ids");
                }
            }
        }
    }

    public sealed interface Entry permits Toggle, IntegerRange, Choice, FloatRange, Action {
        String id();

        String labelKey();
    }

    public record Toggle(String id, String labelKey, BooleanSupplier getter,
                         Consumer<Boolean> setter) implements Entry {
    }

    public record IntegerRange(String id, String labelKey, int min, int max, int step, IntSupplier getter,
                               IntConsumer setter) implements Entry {
        public IntegerRange {
            if (min > max) {
                throw new IllegalArgumentException("Integer setting '" + id + "' has an invalid range");
            }
            if (step <= 0) {
                throw new IllegalArgumentException("Integer setting '" + id + "' must use a positive step");
            }
        }
    }

    public record Choice(String id, String labelKey, List<String> optionKeys, IntSupplier getter, IntConsumer setter,
                         BooleanSupplier available, String unavailableKey) implements Entry {
        public Choice {
            if (optionKeys.isEmpty()) {
                throw new IllegalArgumentException("Choice setting '" + id + "' must have at least one option");
            }
            if (optionKeys.stream().anyMatch(String::isBlank)) {
                throw new IllegalArgumentException(
                        "Choice setting '" + id + "' contains an empty option translation key");
            }
        }

        public int clampIndex(int index) {
            return Math.clamp(index, 0, optionKeys.size() - 1);
        }
    }

    @FunctionalInterface
    public interface FloatSupplier {
        float getAsFloat();
    }

    public record FloatRange(String id, String labelKey, float min, float max, float step, FloatSupplier getter,
                             Consumer<Float> setter, Runnable commit,
                             @Nullable Function<Float, String> formatter) implements Entry {
        public FloatRange(
                String id,
                String labelKey,
                float min,
                float max,
                float step,
                FloatSupplier getter,
                Consumer<Float> setter,
                Runnable commit
        ) {
            this(id, labelKey, min, max, step, getter, setter, commit, null);
        }

        public FloatRange {
            if (!Float.isFinite(min) || !Float.isFinite(max) || min > max) {
                throw new IllegalArgumentException("Float setting '" + id + "' has an invalid range");
            }
            if (!Float.isFinite(step) || step <= 0f) {
                throw new IllegalArgumentException("Float setting '" + id + "' must use a positive finite step");
            }
        }

        public String formatValue(float value) {
            if (formatter != null) return formatter.apply(value);
            var range = max - min;
            var normalized = range > 0f ? Math.clamp((value - min) / range, 0f, 1f) : 0f;
            return Math.round(normalized * 100f) + "%";
        }

        public float quantize(float value) {
            if (!Float.isFinite(value)) return min;
            var index = Math.round((value - min) / step);
            return Math.clamp(min + index * step, min, max);
        }
    }

    public record Action(String id, String labelKey, String buttonKey, Runnable action) implements Entry {
    }
}
