package org.academy.api.common.entitycontrol;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.academy.internal.common.ability.mentalout.control.MentalControlFeedbackRegistry;
import org.jspecify.annotations.Nullable;

/** Custom text for targets protected by {@link MentalControlTags#IMMUNE}. */
public final class MentalControlFeedbackApi {
    private MentalControlFeedbackApi() {
    }

    /**
     * Register once during mod initialization, using an entity type's registry ID.
     * Duplicate IDs are rejected; registration does not grant or remove immunity.
     * The provider runs on the server thread and may inspect the particular target.
     * Return a translatable component for localization, or null to use the built-in text.
     * Providers must not mutate game state. Registrations survive server restarts in the JVM.
     */
    public static void registerImmuneFeedback(Identifier entityTypeId, ImmuneFeedbackProvider provider) {
        MentalControlFeedbackRegistry.register(entityTypeId, provider);
    }

    /**
     * Resolves the text without sending it. For an untagged target or a missing/failing provider,
     * returns the standard protected-target message. The returned component is a copy.
     */
    public static Component immuneFeedback(ServerPlayer controller, LivingEntity subject) {
        return MentalControlFeedbackRegistry.resolve(controller, subject,
                Component.translatable("message.academy.mentalout.protected_target"));
    }

    @FunctionalInterface
    public interface ImmuneFeedbackProvider {
        @Nullable Component feedback(ServerPlayer controller, LivingEntity subject);
    }
}
