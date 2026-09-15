package org.academy.api.server.ability.electromaster;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.AbilityDefenseEffects;
import org.academy.api.common.entitycontrol.MentalImmunity;

/** Reusable innate field effects for players, program-controlled entities and addons. */
public final class MagneticFieldEffects {
    public static final Identifier SOURCE = AcademyCraft.academy("magnetic_field");
    private MagneticFieldEffects() {}

    public static void setPassives(LivingEntity subject, boolean enabled) {
        AbilityDefenseEffects.setTrueResistance(subject, SOURCE, 2, enabled);
        MentalImmunity.set(subject, SOURCE, Component.translatable(
                "message.academy.mentalout.protected.electromagnetic_field"), enabled);
    }
}
