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
import org.academy.internal.common.ability.accelerator.reflection.VectorReflectionRuntime;
import org.academy.internal.common.ability.accelerator.skills.lv4.ReflectionFilter;
import org.academy.internal.common.ability.accelerator.skills.lv4.VectorReflection;

/** Field-free bytecode template for generated server-player dispatch subclasses. */
public class VrServerPlayerTemplate extends ServerPlayer {
    public VrServerPlayerTemplate(MinecraftServer server, ServerLevel level, GameProfile profile,
                                  ClientInformation clientInformation) {
        super(server, level, profile, clientInformation);
    }

    private boolean academy$protected() {
        return VectorReflection.Server.isActive(this);
    }

    @Override
    public float getHealth() {
        var max = super.getMaxHealth();
        var original = super.getHealth();
        return academy$protected() ? VectorReflection.Server.protectHealthRead(this, max) : original;
    }

    @Override
    public boolean isAlive() {
        return academy$protected() || super.isAlive();
    }

    @Override
    public boolean isDeadOrDying() {
        return academy$protected() ? false : super.isDeadOrDying();
    }

    @Override
    public void die(DamageSource source) {
        if (!academy$protected() || VectorReflection.Server.isLegitimateHealthMutation(this)) {
            super.die(source);
            return;
        }
        VectorReflectionRuntime.requestObserverRebuild(this);
        VectorReflection.Server.maintainProtection(this);
    }

    @Override
    public void kill(ServerLevel level) {
        if (!academy$protected() || VectorReflection.Server.isLegitimateHealthMutation(this)) {
            super.kill(level);
            return;
        }
        VectorReflectionRuntime.requestObserverRebuild(this);
        VectorReflection.Server.maintainProtection(this);
    }

    @Override
    protected void actuallyHurt(ServerLevel level, DamageSource source, float damage) {
        if (academy$protected() && !VectorReflection.Server.isLegitimateHealthMutation(this)) {
            VectorReflectionRuntime.requestObserverRebuild(this);
            VectorReflection.Server.maintainProtection(this);
            return;
        }
        super.actuallyHurt(level, source, damage);
    }

    @Override
    public boolean addEffect(MobEffectInstance effect, Entity source) {
        if (academy$protected() && effect != null && ReflectionFilter.shouldReflectEffect(this, effect)) return false;
        return super.addEffect(effect, source);
    }

    @Override
    public void forceAddEffect(MobEffectInstance effect, Entity source) {
        if (academy$protected() && effect != null && ReflectionFilter.shouldReflectEffect(this, effect)) return;
        super.forceAddEffect(effect, source);
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
        VectorReflectionRuntime.requestObserverRebuild(this);
        VectorReflection.Server.maintainProtection(this);
    }

    @Override
    public boolean isInvisible() {
        return academy$protected() ? false : super.isInvisible();
    }

    @Override
    public void setInvisible(boolean invisible) {
        if (!academy$protected()) super.setInvisible(invisible);
    }
}
