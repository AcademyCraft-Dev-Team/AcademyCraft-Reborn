package org.academy.api.server.ability.electromaster;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.AbilityDefenseEffects;
import org.academy.api.common.ability.electromaster.LevitationTuning;
import org.academy.api.common.entitycontrol.MentalImmunity;
import org.academy.api.server.vanilla.MinecraftServerContext;

/** Reusable innate field effects for players, program-controlled entities and addons. */
public final class MagneticFieldEffects {
    public static final Identifier SOURCE = AcademyCraft.academy("magnetic_field");
    private MagneticFieldEffects() {}

    public static void setPassives(LivingEntity subject, boolean enabled) {
        AbilityDefenseEffects.setTrueResistance(subject, SOURCE, 2, enabled);
        MentalImmunity.set(subject, SOURCE, Component.translatable(
                "message.academy.mentalout.protected.electromagnetic_field"), enabled);
    }

    /** Server-owned levitation tuning; the built-in defaults apply when no server config is attached. */
    public static LevitationTuning levitationTuning(Entity subject) {
        if (subject == null || !(subject.level() instanceof ServerLevel level)) return LevitationTuning.DEFAULT;
        if (!(level.getServer() instanceof MinecraftServerContext context)) return LevitationTuning.DEFAULT;
        var server = context.getAcademyCraftServer();
        if (server == null) return LevitationTuning.DEFAULT;
        var config = server.getAbilityConfig();
        return config == null || config.electromaster == null
                ? LevitationTuning.DEFAULT : config.electromaster.levitation();
    }
}
