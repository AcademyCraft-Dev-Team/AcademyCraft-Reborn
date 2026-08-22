package org.academy.api.common.ability.darkmatter;

import net.minecraft.resources.Identifier;

import java.util.Objects;

/** Public descriptor for an independently budgeted creature AI/utility module. */
public record DarkmatterCreatureModuleType(
        Identifier id,
        int budgetCost,
        ModuleHandler handler
) {
    public DarkmatterCreatureModuleType {
        Objects.requireNonNull(id, "id");
        if (budgetCost < 0) throw new IllegalArgumentException("budgetCost must be non-negative");
        handler = handler == null ? ModuleHandler.NONE : handler;
    }

    /** Stable client localization key; extension mods can provide the same key in their lang files. */
    public String translationKey() {
        return "darkmatter_creature.module." + id.getNamespace() + "." + id.getPath();
    }

    public String descriptionTranslationKey() {
        return translationKey() + ".desc";
    }

    @FunctionalInterface
    public interface ModuleHandler {
        ModuleHandler NONE = (_, _, _) -> { };

        void tick(net.minecraft.world.entity.Mob creature,
                  net.minecraft.server.level.ServerPlayer owner,
                  float valueMultiplier);
    }
}
