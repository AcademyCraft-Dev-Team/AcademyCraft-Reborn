package org.academy.internal.coremod.client;

import net.minecraft.core.Holder;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.chat.ChatAbilities;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.stats.StatsCounter;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Input;
import org.academy.api.common.ability.ImagineBreakerHealthAccess;
import org.academy.internal.client.ability.VectorReflectionClientRuntime;

import java.lang.invoke.VarHandle;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Field-free bytecode template for generated local-player dispatch subclasses. */
public class VrLocalPlayerTemplate extends LocalPlayer implements ImagineBreakerHealthAccess {
    private static Map<UUID, Integer> academy$a;
    private static VarHandle academy$b;
    private static VarHandle academy$c;
    private static int academy$d;
    private static int academy$e;

    public VrLocalPlayerTemplate(Minecraft minecraft, ClientLevel level, ClientPacketListener connection,
                                 StatsCounter stats, ClientRecipeBook recipeBook, Input lastSentInput,
                                 boolean wasSprinting, ChatAbilities chatAbilities) {
        super(minecraft, level, connection, stats, recipeBook, lastSentInput, wasSprinting, chatAbilities);
    }

    private boolean academy$protected() {
        return VectorReflectionClientRuntime.isProtected(this);
    }

    private boolean academy$reflects(MobEffectInstance effect) {
        return academy$protected()
                && VectorReflectionClientRuntime.shouldReflectEffect(this, effect);
    }

    private void academy$discardReflectedEffect(MobEffectInstance effect) {
        var removed = super.removeEffectNoUpdate(effect.getEffect());
        if (removed != null) super.onEffectsRemoved(List.of(removed));
    }

    @Override
    public float getHealth() {
        var original = super.getHealth();
        if (!academy$protected()) return original;

        var uuid = getUUID();
        var encoded = academy$a.get(uuid);
        var initial = Float.isFinite(original) ? Math.max(0.0f, original) : 0.0f;
        float cached;
        if (encoded == null) {
            cached = initial;
            academy$a.put(uuid, Float.floatToRawIntBits(cached) ^ academy$e);
        } else {
            cached = Float.intBitsToFloat(encoded ^ academy$e);
            if (!Float.isFinite(cached)) {
                cached = initial;
                academy$a.put(uuid, Float.floatToRawIntBits(cached) ^ academy$e);
            } else {
                cached = Math.max(0.0f, cached);
            }
        }
        var maximum = super.getMaxHealth();
        if (Float.isFinite(original) && Float.isFinite(maximum)
                && original > cached && original <= maximum) {
            cached = original;
            academy$a.put(uuid, Float.floatToRawIntBits(cached) ^ academy$e);
        }
        return Math.max(1.0f, cached);
    }

    @Override
    public void imaginebreaker(float amount) {
        if (!academy$protected() || !Float.isFinite(amount) || !(amount > 0.0f)) return;

        var original = super.getHealth();
        var uuid = getUUID();
        var encoded = academy$a.get(uuid);
        var initial = Float.isFinite(original) ? Math.max(0.0f, original) : 0.0f;
        float cached;
        if (encoded == null) {
            cached = initial;
        } else {
            cached = Float.intBitsToFloat(encoded ^ academy$e);
            if (!Float.isFinite(cached)) cached = initial;
            else cached = Math.max(0.0f, cached);
        }
        cached = Math.max(0.0f, cached - amount);
        academy$a.put(uuid, Float.floatToRawIntBits(cached) ^ academy$e);
        var items = (Object[]) academy$b.get(entityData);
        academy$c.set(items[academy$d], Float.valueOf(cached));
    }

    @Override
    public void setHealth(float amount) {
        if (!academy$protected()) {
            super.setHealth(amount);
            return;
        }
        var current = getHealth();
        var maximum = super.getMaxHealth();
        if (Float.isFinite(amount) && Float.isFinite(maximum)
                && amount > current && amount <= maximum) {
            super.setHealth(amount);
        }
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        if (!academy$protected() || accessor == null || accessor.id() != academy$d) {
            super.onSyncedDataUpdated(accessor);
            return;
        }

        var items = (Object[]) academy$b.get(entityData);
        var rawValue = academy$c.get(items[academy$d]);
        var reported = rawValue instanceof Float value ? value : Float.NaN;
        var original = Float.isFinite(reported) ? Math.max(0.0f, reported) : 0.0f;
        var uuid = getUUID();
        var encoded = academy$a.get(uuid);
        float cached;
        if (encoded == null) {
            cached = original;
        } else {
            cached = Float.intBitsToFloat(encoded ^ academy$e);
            if (!Float.isFinite(cached)) cached = original;
            else cached = Math.max(0.0f, cached);
        }
        var maximum = super.getMaxHealth();
        if (Float.isFinite(maximum) && original > cached && original <= maximum) {
            cached = original;
        }
        academy$a.put(uuid, Float.floatToRawIntBits(cached) ^ academy$e);
        if (!Float.isFinite(reported) || reported != cached) {
            academy$c.set(items[academy$d], Float.valueOf(cached));
        }
        super.onSyncedDataUpdated(accessor);
    }

    @Override
    public void tick() {
        super.tick();
        var uuid = getUUID();
        if (!academy$protected()) {
            academy$a.remove(uuid);
            return;
        }

        var original = super.getHealth();
        var encoded = academy$a.get(uuid);
        var initial = Float.isFinite(original) ? Math.max(0.0f, original) : 0.0f;
        float cached;
        if (encoded == null) {
            cached = initial;
        } else {
            cached = Float.intBitsToFloat(encoded ^ academy$e);
            if (!Float.isFinite(cached)) cached = initial;
            else cached = Math.max(0.0f, cached);
        }
        var maximum = super.getMaxHealth();
        if (Float.isFinite(original) && Float.isFinite(maximum)
                && original > cached && original <= maximum) {
            cached = original;
        }
        academy$a.put(uuid, Float.floatToRawIntBits(cached) ^ academy$e);
        var items = (Object[]) academy$b.get(entityData);
        academy$c.set(items[academy$d], Float.valueOf(cached));
        hurtTime = 0;
        hurtDuration = 0;
        hurtMarked = false;
        deathTime = 0;
        lastHurt = 0.0f;
        invulnerableTime = 0;
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
                : instance != null && !VectorReflectionClientRuntime.shouldReflectEffect(this, instance);
    }

    @Override
    public MobEffectInstance getEffect(Holder<MobEffect> effect) {
        var instance = super.getEffect(effect);
        return academy$protected()
                && VectorReflectionClientRuntime.shouldReflectEffect(this, instance) ? null : instance;
    }

    /*
     * addEffect(MobEffectInstance) and removeEffectNoUpdate are final in 26.2. The guarded
     * two-argument overload receives the former's virtual dispatch. Removal APIs stay available so
     * server synchronization and the local ghost-effect purge can clear stale effects.
     */

    @Override
    public boolean hurtClient(DamageSource source) {
        if (!academy$protected()) return super.hurtClient(source);
        VectorReflectionClientRuntime.sanitize(this);
        return false;
    }

    @Override
    protected void markHurt() {
        if (!academy$protected()) super.markHurt();
    }

    @Override
    public void animateHurt(float direction) {
        if (!academy$protected()) super.animateHurt(direction);
    }

    @Override
    public void indicateDamage(double x, double z) {
        if (!academy$protected()) super.indicateDamage(x, z);
    }

    @Override
    public void handleDamageEvent(DamageSource source) {
        if (!academy$protected()) {
            super.handleDamageEvent(source);
            return;
        }
        hurtTime = 0;
        hurtDuration = 0;
        hurtMarked = false;
        deathTime = 0;
        lastHurt = 0.0f;
        invulnerableTime = 0;
        VectorReflectionClientRuntime.sanitize(this);
    }

    @Override
    public void handleEntityEvent(byte state) {
        if (academy$protected() && (state == 2 || state == 3)) {
            VectorReflectionClientRuntime.sanitize(this);
            return;
        }
        super.handleEntityEvent(state);
    }

    @Override
    public void die(DamageSource source) {
        if (!academy$protected()) super.die(source);
        else VectorReflectionClientRuntime.sanitize(this);
    }

    @Override
    public void kill(ServerLevel level) {
        if (!academy$protected()) super.kill(level);
        else VectorReflectionClientRuntime.sanitize(this);
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
        } else {
            VectorReflectionClientRuntime.sanitize(this);
        }
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
