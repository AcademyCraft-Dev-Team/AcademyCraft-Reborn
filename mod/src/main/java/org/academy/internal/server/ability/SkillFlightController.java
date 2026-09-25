package org.academy.internal.server.ability;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.neoforged.neoforge.common.NeoForgeMod;

/**
 * Manages independent creative-flight leases through NeoForge's public boolean attribute.
 */
public final class SkillFlightController {
    private SkillFlightController() {
    }

    public static void setSource(ServerPlayer player, Identifier source, boolean enabled) {
        var attribute = player.getAttribute(NeoForgeMod.CREATIVE_FLIGHT);
        if (attribute == null) return;

        var current = attribute.getModifier(source);
        if (enabled && current == null) {
            attribute.addTransientModifier(new AttributeModifier(
                    source,
                    1,
                    AttributeModifier.Operation.ADD_VALUE
            ));
            player.onUpdateAbilities();
            return;
        }
        if (!enabled && current != null) {
            attribute.removeModifier(source);
            if (!player.mayFly()) {
                player.getAbilities().flying = false;
            }
            player.onUpdateAbilities();
        }
    }
}
