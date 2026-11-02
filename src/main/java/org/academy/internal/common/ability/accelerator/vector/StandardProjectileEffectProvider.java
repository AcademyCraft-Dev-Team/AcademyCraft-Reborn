package org.academy.internal.common.ability.accelerator.vector;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.ability.vector.EffectDescriptor;
import org.academy.api.common.ability.vector.EffectFidelity;
import org.academy.api.common.ability.vector.EffectOperation;
import org.academy.api.server.ability.AbilityActorContext;
import org.academy.api.server.ability.AbilityExecutionHandle;
import org.academy.api.server.ability.vector.ExternalEffectProvider;
import org.academy.api.server.ability.vector.VectorOperationRequest;
import org.academy.internal.common.ability.accelerator.reflection.compat.VectorProjectileStateAdapter;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.Set;

/** Uses inherited vanilla flight only. Unknown tick/owner/collision implementations need a provider. */
final class StandardProjectileEffectProvider implements ExternalEffectProvider {
    static final Identifier ID = Identifier.fromNamespaceAndPath("academy", "standard_projectile");
    private static final Set<EffectOperation> OBSERVE = Set.of(EffectOperation.OBSERVE);
    private static final Set<EffectOperation> OPERATIONS = Set.of(
            EffectOperation.OBSERVE, EffectOperation.REDIRECT_EXISTING, EffectOperation.CONTROL_EXISTING);
    private static final ClassValue<Boolean> STANDARD = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                return vanilla(type.getMethod("tick"))
                        && type.getMethod("getOwner").getDeclaringClass() == Projectile.class
                        && standardOwnerSetter(type.getMethod("setOwner", Entity.class))
                        && firstDeclaration(type, "setOwner", EntityReference.class) == Projectile.class
                        && type.getMethod("getDeltaMovement").getDeclaringClass() == Entity.class
                        && type.getMethod("setDeltaMovement", Vec3.class).getDeclaringClass() == Entity.class
                        && vanillaDeclared(type, "canHitEntity", Entity.class);
            } catch (ReflectiveOperationException | SecurityException failure) {
                return false;
            }
        }
    };

    private static boolean vanilla(Method method) {
        return method.getDeclaringClass().getName().startsWith("net.minecraft.world.entity.");
    }

    private static boolean standardOwnerSetter(Method method) {
        // AbstractArrow delegates owner storage and only adjusts vanilla pickup policy.
        return method.getDeclaringClass() == Projectile.class || method.getDeclaringClass() == AbstractArrow.class;
    }

    private static boolean vanillaDeclared(Class<?> type, String name, Class<?> parameter)
            throws NoSuchMethodException {
        return firstDeclaration(type, name, parameter).getName().startsWith("net.minecraft.world.entity.");
    }

    private static Class<?> firstDeclaration(Class<?> type, String name, Class<?> parameter)
            throws NoSuchMethodException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                return current.getDeclaredMethod(name, parameter).getDeclaringClass();
            } catch (NoSuchMethodException ignored) {
                // The first declaration determines dispatch, including protected overrides.
            }
        }
        throw new NoSuchMethodException(name);
    }

    @Override
    public Identifier id() { return ID; }

    @Override
    public int priority() { return Integer.MIN_VALUE; }

    @Override
    public Optional<EffectDescriptor> describe(Projectile projectile) {
        boolean supported = supportsType(projectile.getClass());
        return Optional.of(new EffectDescriptor(BuiltInRegistries.ENTITY_TYPE.getKey(projectile.getType()),
                ID, supported ? OPERATIONS : OBSERVE,
                supported ? EffectFidelity.NATIVE : EffectFidelity.OBSERVATION_ONLY,
                supported ? "inherited_flight; original_payload; control_reissues_velocity_each_tick; native_drag_and_gravity"
                        : "custom_flight_owner_or_collision_requires_provider"));
    }

    static boolean supportsType(Class<? extends Projectile> type) {
        // These native implementations may attach to another entity, require a player owner,
        // or recompute guidance independently of the supplied velocity.
        return !FireworkRocketEntity.class.isAssignableFrom(type)
                && !FishingHook.class.isAssignableFrom(type)
                && !ShulkerBullet.class.isAssignableFrom(type)
                && !ThrownTrident.class.isAssignableFrom(type) && STANDARD.get(type);
    }

    @Override
    public String rejectionReason(AbilityActorContext actor, Projectile projectile, VectorOperationRequest request) {
        return request.velocity().lengthSqr() <= 1.0E-8 ? "stationary_control_requires_motion_hook" : "";
    }

    @Override
    public AbilityExecutionHandle start(AbilityActorContext actor, Projectile projectile,
                                         VectorOperationRequest request) {
        if (!VectorProjectileStateAdapter.resumeFlight(projectile, request.velocity())) {
            throw new IllegalArgumentException("Projectile did not accept motion");
        }
        return new AbilityExecutionHandle() {
            private boolean stopped;

            @Override
            public boolean tick() {
                return !stopped && request.operation() == EffectOperation.CONTROL_EXISTING
                        && VectorProjectileStateAdapter.resumeFlight(projectile, request.velocity());
            }

            @Override
            public void stop() { stopped = true; }
        };
    }
}
