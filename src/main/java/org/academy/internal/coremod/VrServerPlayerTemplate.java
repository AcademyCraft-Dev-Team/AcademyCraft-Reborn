package org.academy.internal.coremod;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import org.academy.api.common.ability.ImagineBreakerHealthAccess;
import org.academy.internal.common.ability.accelerator.skills.lv4.ReflectionFilter;
import org.academy.internal.common.ability.accelerator.skills.lv4.VectorReflection;

import java.util.List;

/** Field-free bytecode template for generated server-player dispatch subclasses. */
public class VrServerPlayerTemplate extends ServerPlayer implements ImagineBreakerHealthAccess {
    public VrServerPlayerTemplate(MinecraftServer server, ServerLevel level, GameProfile profile,
                                  ClientInformation clientInformation) {
        super(server, level, profile, clientInformation);
    }

    private boolean academy$protected() {
        return VectorReflection.Server.isActive(this);
    }

    private boolean academy$reflects(MobEffectInstance effect) {
        return academy$protected() && effect != null
                && ReflectionFilter.shouldReflectEffect(this, effect);
    }

    private void academy$discardReflectedEffect(MobEffectInstance effect) {
        var removed = super.removeEffectNoUpdate(effect.getEffect());
        if (removed != null) super.onEffectsRemoved(List.of(removed));
    }

    @Override
    public float getHealth() {
        if (!academy$protected() || VectorReflection.Server.isLegitimateHealthMutation(this)) {
            return super.getHealth();
        }
        return VectorReflection.Server.protectHealthRead(this, super.getHealth());
    }

    @Override
    public void imaginebreaker(float amount) {
        VectorReflection.Server.imaginebreaker(this, amount);
    }

    @Override
    public boolean isAlive() {
        return academy$protected() || super.isAlive();
    }

    @Override
    public boolean isDeadOrDying() {
        return !academy$protected() && super.isDeadOrDying();
    }

    @Override
    public void die(DamageSource source) {
        if (!academy$protected() || VectorReflection.Server.isLegitimateHealthMutation(this)) {
            super.die(source);
            return;
        }
        VectorReflection.Server.maintainProtection(this);
    }

    @Override
    public void kill(ServerLevel level) {
        if (!academy$protected() || VectorReflection.Server.isLegitimateHealthMutation(this)) {
            super.kill(level);
            return;
        }
        VectorReflection.Server.maintainProtection(this);
    }

    @Override
    protected void actuallyHurt(ServerLevel level, DamageSource source, float damage) {
        if (academy$protected() && !VectorReflection.Server.isLegitimateHealthMutation(this)) {
            VectorReflection.Server.maintainProtection(this);
            return;
        }
        super.actuallyHurt(level, source, damage);
    }

    @Override
    public boolean addEffect(MobEffectInstance effect, Entity source) {
        if (academy$reflects(effect)) return false;
        return super.addEffect(effect, source);
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        return !academy$reflects(effect) && super.canBeAffected(effect);
    }

    @Override
    public void forceAddEffect(MobEffectInstance effect, Entity source) {
        if (academy$reflects(effect)) return;
        super.forceAddEffect(effect, source);
    }

    @Override
    protected void onEffectAdded(MobEffectInstance effect, Entity source) {
        if (academy$reflects(effect)) {
            academy$discardReflectedEffect(effect);
            return;
        }
        super.onEffectAdded(effect, source);
    }

    @Override
    protected void onEffectUpdated(MobEffectInstance effect, boolean refreshAttributes, Entity source) {
        if (academy$reflects(effect)) {
            academy$discardReflectedEffect(effect);
            return;
        }
        super.onEffectUpdated(effect, refreshAttributes, source);
    }

    @Override
    public void sendEffectToPassengers(MobEffectInstance effect) {
        if (!academy$reflects(effect)) super.sendEffectToPassengers(effect);
    }

    @Override
    public boolean hasEffect(Holder<MobEffect> effect) {
        var instance = super.getEffect(effect);
        return !academy$protected()
                ? super.hasEffect(effect)
                : instance != null && !ReflectionFilter.shouldReflectEffect(this, instance);
    }

    @Override
    public MobEffectInstance getEffect(Holder<MobEffect> effect) {
        var instance = super.getEffect(effect);
        return academy$protected() && instance != null && ReflectionFilter.shouldReflectEffect(this, instance)
                ? null : instance;
    }

    /*
     * addEffect(MobEffectInstance) and removeEffectNoUpdate are final in 26.2. The former
     * dispatches to the guarded two-argument overload above. Effect-removal APIs remain inherited:
     * removing an existing effect is not an incoming effect and is also required by the reflection
     * filter's own purge path.
     */

    @Override
    public void knockback(double power, double x, double z, DamageSource source,
                          float damage, boolean comesFromEffect) {
        if (!academy$protected()) super.knockback(power, x, z, source, damage, comesFromEffect);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!academy$protected() || reason == RemovalReason.CHANGED_DIMENSION
                || reason == RemovalReason.UNLOADED_WITH_PLAYER) {
            super.remove(reason);
            return;
        }
        VectorReflection.Server.maintainProtection(this);
    }

    @Override
    public boolean isInvisible() {
        return !academy$protected() && super.isInvisible();
    }

    @Override
    public void setInvisible(boolean invisible) {
        if (!academy$protected()) super.setInvisible(invisible);
    }
}
