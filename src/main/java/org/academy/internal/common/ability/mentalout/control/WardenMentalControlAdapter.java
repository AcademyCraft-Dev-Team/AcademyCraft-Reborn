package org.academy.internal.common.ability.mentalout.control;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.warden.Warden;
import org.academy.api.common.entitycontrol.*;

public final class WardenMentalControlAdapter implements MentalControlAdapter {
    @Override
    public boolean matches(LivingEntity subject) {
        return subject instanceof Warden;
    }

    @Override
    public ControlSupport support(LivingEntity subject, ControlCapability capability) {
        if (!matches(subject)) return ControlSupport.UNSUPPORTED;
        return ControlSupport.FULL;
    }

    @Override
    public ControlBinding activate(ControlContext context, ControlDirective directive) {
        if (!(context.subject() instanceof Warden warden)) {
            throw new IllegalArgumentException("Warden adapter requires a Warden subject");
        }
        return StandardMobControlBindings.create(context, warden, directive);
    }
}
