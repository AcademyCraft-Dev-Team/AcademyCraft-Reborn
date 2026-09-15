package org.academy.api.common.ability;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.academy.api.common.attribute.PlayerAttributes;

/** Shared transient defenses; each source owns only its own modifier. */
public final class AbilityDefenseEffects {
    private AbilityDefenseEffects() {}

    /** Returns false when the entity type has not opted into the true-resistance attribute. */
    public static boolean setTrueResistance(LivingEntity subject, Identifier source, double points, boolean enabled) {
        if (subject.level().isClientSide()) return false;
        var attribute = subject.getAttribute(PlayerAttributes.TRUE_RESISTANCE);
        if (attribute == null) return false;
        var amount = enabled && Double.isFinite(points) ? Math.max(0, points) : 0;
        var current = attribute.getModifier(source);
        if (current != null && current.amount() == amount && amount > 0) return true;
        if (current != null) attribute.removeModifier(source);
        if (amount > 0) attribute.addTransientModifier(new AttributeModifier(source, amount, AttributeModifier.Operation.ADD_VALUE));
        return true;
    }
}
