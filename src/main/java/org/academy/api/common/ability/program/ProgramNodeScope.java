package org.academy.api.common.ability.program;

import net.minecraft.resources.Identifier;

import java.util.Set;

/**
 * Ability-category and entitlement restrictions attached to a registered node type.
 */
public record ProgramNodeScope(
        Set<Identifier> allowedCategories,
        Set<Identifier> requiredCapabilities
) {
    public static final ProgramNodeScope COMMON = new ProgramNodeScope(Set.of(), Set.of());

    public ProgramNodeScope {
        allowedCategories = allowedCategories == null ? Set.of() : Set.copyOf(allowedCategories);
        requiredCapabilities = requiredCapabilities == null ? Set.of() : Set.copyOf(requiredCapabilities);
    }

    public static ProgramNodeScope category(Identifier category) {
        return new ProgramNodeScope(Set.of(category), Set.of());
    }

    public boolean allowsCategory(Identifier category) {
        return allowedCategories.isEmpty() || allowedCategories.contains(category);
    }
}
