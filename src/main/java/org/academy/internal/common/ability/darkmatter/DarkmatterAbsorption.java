package org.academy.internal.common.ability.darkmatter;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.academy.AcademyCraft;

/** Makes temporary dark-matter absorption compatible with the MAX_ABSORPTION attribute. */
public final class DarkmatterAbsorption {
    private static final Identifier CAPACITY_ID = AcademyCraft.academy("darkmatter_absorption_capacity");

    private DarkmatterAbsorption() { }

    public static void grantAtLeast(ServerPlayer player, float amount) {
        if (player == null || !Float.isFinite(amount) || amount <= 0.0f) return;
        var attribute = player.getAttribute(Attributes.MAX_ABSORPTION);
        if (attribute == null) return;
        var desiredCapacity = Math.max(amount, player.getMaxAbsorption());
        var previous = attribute.getModifier(CAPACITY_ID);
        if (previous != null) attribute.removeModifier(CAPACITY_ID);
        var required = Math.max(0.0, desiredCapacity - attribute.getValue());
        if (required > 0.0) {
            attribute.addTransientModifier(new AttributeModifier(
                    CAPACITY_ID, required, AttributeModifier.Operation.ADD_VALUE));
        }
        player.setAbsorptionAmount(Math.max(player.getAbsorptionAmount(), amount));
    }
}
