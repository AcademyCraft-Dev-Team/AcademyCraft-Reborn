package org.academy.internal.common.ability.mentalout.control;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.academy.AcademyCraft;
import org.academy.api.common.entitycontrol.MentalControlFeedbackApi.ImmuneFeedbackProvider;
import org.academy.api.common.entitycontrol.MentalControlTags;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class MentalControlFeedbackRegistry {
    private static final ConcurrentHashMap<Identifier, ImmuneFeedbackProvider> PROVIDERS = new ConcurrentHashMap<>();
    private static final Set<Identifier> REPORTED_FAILURES = ConcurrentHashMap.newKeySet();

    private MentalControlFeedbackRegistry() {
    }

    public static void register(Identifier entityTypeId, ImmuneFeedbackProvider provider) {
        Objects.requireNonNull(entityTypeId, "entityTypeId");
        Objects.requireNonNull(provider, "provider");
        if (PROVIDERS.putIfAbsent(entityTypeId, provider) != null) {
            throw new IllegalArgumentException("Duplicate mental immunity feedback for " + entityTypeId);
        }
    }

    public static Component resolve(ServerPlayer controller, LivingEntity subject, Component fallback) {
        Objects.requireNonNull(controller, "controller");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(fallback, "fallback");
        if (!subject.getType().builtInRegistryHolder().is(MentalControlTags.IMMUNE)) return fallback.copy();
        var id = BuiltInRegistries.ENTITY_TYPE.getKey(subject.getType());
        var provider = PROVIDERS.get(id);
        if (provider != null) {
            try {
                var result = provider.feedback(controller, subject);
                if (result != null) return result.copy();
            } catch (RuntimeException exception) {
                if (REPORTED_FAILURES.add(id)) {
                    AcademyCraft.LOGGER.error("Mental immunity feedback failed for {}; using built-in text", id, exception);
                }
            }
        }
        return fallback.copy();
    }
}
