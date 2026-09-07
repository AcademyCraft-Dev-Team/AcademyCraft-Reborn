package org.academy.api.common.ability.mentalout;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.academy.api.common.attribute.PlayerAttributes;

/** Reusable mental self-regulation effects for skills, precision programs and non-player actors. */
public final class MentaloutDefenseEffects {
    private MentaloutDefenseEffects() {}

    /** Owns only the supplied transient modifier; entities without the attribute are left unchanged. */
    public static void setTrueResistance(LivingEntity subject, Identifier source, double points, boolean enabled) {
        if (subject == null || subject.level().isClientSide()) return;
        var attribute = subject.getAttribute(PlayerAttributes.TRUE_RESISTANCE);
        if (attribute == null) return;
        var amount = enabled && Double.isFinite(points) ? Math.max(0.0, points) : 0.0;
        var current = attribute.getModifier(source);
        if (current != null && current.amount() == amount && amount > 0.0) return;
        if (current != null) attribute.removeModifier(source);
        if (amount > 0.0) attribute.addTransientModifier(new AttributeModifier(
                source, amount, AttributeModifier.Operation.ADD_VALUE));
    }

    /** Uses final damage after defenses; the threshold is exclusive and the cap applies per hit. */
    public static float damageCpCost(float finalDamage, float threshold, float maximumCost) {
        return Float.isFinite(finalDamage) && finalDamage > threshold
                ? Math.min(finalDamage, maximumCost) : 0.0f;
    }
}
