package org.academy.internal.common.ability.mentalout.control;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.squid.Squid;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.monster.warden.Warden;
import org.academy.api.common.entitycontrol.ControlBinding;
import org.academy.api.common.entitycontrol.ControlCapability;
import org.academy.api.common.entitycontrol.ControlContext;
import org.academy.api.common.entitycontrol.ControlDirective;
import org.academy.api.common.entitycontrol.ControlSupport;
import org.academy.api.common.entitycontrol.MentalControlAdapter;

public final class VanillaMobMentalControlAdapter implements MentalControlAdapter {
    @Override
    public boolean matches(LivingEntity subject) {
        return subject instanceof Mob
                && !(subject instanceof Warden)
                && !(subject instanceof WitherBoss)
                && !(subject instanceof EnderDragon);
    }

    @Override
    public ControlSupport support(LivingEntity subject, ControlCapability capability) {
        if (!matches(subject)) return ControlSupport.UNSUPPORTED;
        if ((capability == ControlCapability.PATH_CONTROL
                || capability == ControlCapability.GUARD_CONTROL)
                && (subject instanceof Shulker || subject instanceof Bat || subject instanceof Squid)) {
            return ControlSupport.UNSUPPORTED;
        }
        return ControlSupport.BEST_EFFORT;
    }

    @Override
    public ControlBinding activate(ControlContext context, ControlDirective directive) {
        if (!(context.subject() instanceof Mob mob)) {
            throw new IllegalArgumentException("Vanilla Mob adapter requires a Mob subject");
        }
        return StandardMobControlBindings.create(context, mob, directive);
    }
}
