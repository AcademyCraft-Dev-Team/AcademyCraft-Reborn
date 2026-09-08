package org.academy.api.common.registries;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.api.common.ability.Skill;

/** Stable built-in content references; resolving a key does not initialize a skill or category. */
public final class AcademyKeys {
    public static final ResourceKey<AbilityCategory> LEVEL0 = category("level0");
    public static final ResourceKey<AbilityCategory> ELECTROMASTER = category("electromaster");
    public static final ResourceKey<AbilityCategory> ACCELERATOR = category("accelerator");
    public static final ResourceKey<AbilityCategory> MELTDOWNER = category("meltdowner");
    public static final ResourceKey<AbilityCategory> TELEPORT = category("teleport");
    public static final ResourceKey<AbilityCategory> MENTALOUT = category("mentalout");
    public static final ResourceKey<AbilityCategory> DARKMATTER = category("darkmatter");
    public static final ResourceKey<AbilityCategory> AEROMANIP = category("aeromanip");

    private AcademyKeys() {
    }

    public static ResourceKey<AbilityCategory> category(String path) {
        return ResourceKey.create(Registries.Keys.ABILITY_CATEGORIES, Identifier.fromNamespaceAndPath("academy", path));
    }

    public static ResourceKey<Skill> skill(String path) {
        return ResourceKey.create(Registries.Keys.SKILLS, Identifier.fromNamespaceAndPath("academy", path));
    }
}
