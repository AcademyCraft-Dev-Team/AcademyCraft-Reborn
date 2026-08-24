package org.academy.internal.coremod;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import org.academy.api.common.ability.ImagineBreakerHealthAccess;
import org.academy.internal.common.ability.accelerator.skills.lv4.ReflectionFilter;
import org.academy.internal.common.ability.accelerator.skills.lv4.VectorReflection;

import java.lang.invoke.VarHandle;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Field-free bytecode template for generated server-player dispatch subclasses. */
public class VrServerPlayerTemplate extends ServerPlayer implements ImagineBreakerHealthAccess {
    /*
     * These static placeholders are renamed to per-process random identifiers by
     * DispatchSubclassFactory.  The generated overrides deliberately inline every ledger access;
     * there is no callable read/write bridge for another mod to discover and invoke.
     */
    private static Map<UUID, Long> academy$a;
    private static VarHandle academy$b;
    private static VarHandle academy$c;
    private static int academy$d;
    private static long academy$e;

    public VrServerPlayerTemplate(MinecraftServer server, ServerLevel level, GameProfile profile,
                                  ClientInformation clientInformation) {
        super(server, level, profile, clientInformation);
    }

    private boolean academy$protected() {
        return VectorReflection.Server.usesFullInstanceProtection(this);
    }

    private boolean academy$reflects(MobEffectInstance effect) {
        return VectorReflection.Server.isActive(this) && effect != null
                && ReflectionFilter.shouldReflectEffect(this, effect);
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
        var state = ProtectedHealthCache.reconcile(
                encoded == null ? 0L : encoded ^ academy$e,
                encoded != null,
                original,
                super.getMaxHealth()
        );
        academy$a.put(uuid, state ^ academy$e);
        return Math.max(1.0f, ProtectedHealthCache.health(state));
    }

    @Override
    public void imaginebreaker(float amount) {
        if (!academy$protected() || !Float.isFinite(amount) || !(amount > 0.0f)) return;

        var original = super.getHealth();
        var uuid = getUUID();
        var encoded = academy$a.get(uuid);
        var state = ProtectedHealthCache.reconcile(
                encoded == null ? 0L : encoded ^ academy$e,
                encoded != null,
                original,
                super.getMaxHealth()
        );
        state = ProtectedHealthCache.subtract(state, amount);
        academy$a.put(uuid, state ^ academy$e);
        var cached = ProtectedHealthCache.health(state);
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
        var state = ProtectedHealthCache.reconcile(
                encoded == null ? 0L : encoded ^ academy$e,
                encoded != null,
                original,
                super.getMaxHealth()
        );
        academy$a.put(uuid, state ^ academy$e);
        var cached = ProtectedHealthCache.health(state);
        if (!Float.isFinite(reported) || reported != cached) {
            academy$c.set(items[academy$d], Float.valueOf(cached));
        }
        super.onSyncedDataUpdated(accessor);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (!academy$protected()) return super.hurtServer(level, source, damage);

        hurtTime = 0;
        hurtDuration = 0;
        hurtMarked = false;
        deathTime = 0;
        lastHurt = 0.0f;
        invulnerableTime = 0;

        var result = VectorReflection.Server.hurtServer(this, level, source, damage);
        var remaining = result.getRight();
        if (result.getLeft()) {
            if (!academy$protected() && remaining > 0.0f && Float.isFinite(remaining)) {
                return super.hurtServer(level, source, remaining);
            }
            return false;
        }
        return academy$protected() ? false : super.hurtServer(level, source, remaining);
    }

    @Override
    public void tick() {
        super.tick();
        if (!academy$protected()) return;

        var original = super.getHealth();
        var uuid = getUUID();
        var encoded = academy$a.get(uuid);
        var state = ProtectedHealthCache.reconcile(
                encoded == null ? 0L : encoded ^ academy$e,
                encoded != null,
                original,
                super.getMaxHealth()
        );
        academy$a.put(uuid, state ^ academy$e);
        var cached = ProtectedHealthCache.health(state);
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
    public void die(DamageSource source) {
        if (!academy$protected()) {
            super.die(source);
            return;
        }
        VectorReflection.Server.maintainProtection(this);
    }

    @Override
    public void kill(ServerLevel level) {
        if (!academy$protected()) {
            super.kill(level);
            return;
        }
        VectorReflection.Server.maintainProtection(this);
    }

    @Override
    protected void actuallyHurt(ServerLevel level, DamageSource source, float damage) {
        if (!academy$protected()) {
            super.actuallyHurt(level, source, damage);
            return;
        }
        hurtTime = 0;
        hurtDuration = 0;
        hurtMarked = false;
        deathTime = 0;
        lastHurt = 0.0f;
        invulnerableTime = 0;
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
        return !VectorReflection.Server.isActive(this)
                ? super.hasEffect(effect)
                : instance != null && !ReflectionFilter.shouldReflectEffect(this, instance);
    }

    @Override
    public MobEffectInstance getEffect(Holder<MobEffect> effect) {
        var instance = super.getEffect(effect);
        return VectorReflection.Server.isActive(this)
                && instance != null && ReflectionFilter.shouldReflectEffect(this, instance)
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
