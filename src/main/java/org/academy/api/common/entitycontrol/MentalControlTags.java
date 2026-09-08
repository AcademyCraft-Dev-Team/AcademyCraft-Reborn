package org.academy.api.common.entitycontrol;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

/** Entity-type tags that addons can extend through data packs with replace: false. */
public final class MentalControlTags {
    public static final TagKey<EntityType<?>> IMMUNE = tag("mental_control_immune");
    public static final TagKey<EntityType<?>> RESISTANCE = tag("mental_control_resistance");
    public static final TagKey<EntityType<?>> BOSS_COST = tag("mental_control_boss_cost");

    private MentalControlTags() {
    }

    private static TagKey<EntityType<?>> tag(String path) {
        return TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath("academy", path));
    }
}
