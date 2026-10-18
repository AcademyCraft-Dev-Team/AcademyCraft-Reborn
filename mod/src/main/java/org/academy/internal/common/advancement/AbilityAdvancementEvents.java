package org.academy.internal.common.advancement;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.academy.api.common.ability.event.AbilityCategoryChangedEvent;
import org.academy.api.common.ability.event.AbilityLevelChangedEvent;
import org.academy.api.common.ability.event.SkillLearnedEvent;
import org.academy.api.common.ability.event.SkillRemovedEvent;

@EventBusSubscriber
public final class AbilityAdvancementEvents {
    private AbilityAdvancementEvents() {
    }

    @SubscribeEvent
    public static void onSkillLearned(SkillLearnedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        AbilityAdvancements.onSkillLearned(player, event.getCategory(), event.getSkillKey());
    }

    @SubscribeEvent
    public static void onSkillRemoved(SkillRemovedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        AbilityAdvancements.onSkillRemoved(player, event.getSkillKey());
    }

    @SubscribeEvent
    public static void onLevelChanged(AbilityLevelChangedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        AbilityAdvancements.onLevelChanged(player, event.getCategory(), event.getLevel());
    }

    @SubscribeEvent
    public static void onCategoryChanged(AbilityCategoryChangedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.isSkillsCleared()) AbilityAdvancements.revokeAll(player);
        else AbilityAdvancements.onCategoryChanged(player, event.getCategory());
    }
}
