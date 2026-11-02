package org.academy.internal.common.ability.accelerator.vector;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StandardProjectileEffectProviderTest {
    @Test
    void inheritsOrdinaryFlightWithoutModNameAllowlist() {
        assertTrue(StandardProjectileEffectProvider.supportsType(Arrow.class));
        assertTrue(StandardProjectileEffectProvider.supportsType(InheritedArrow.class));
    }

    @Test
    void vanillaAttachmentAndGuidanceAreNotVelocityControl() {
        assertFalse(StandardProjectileEffectProvider.supportsType(FireworkRocketEntity.class));
        assertFalse(StandardProjectileEffectProvider.supportsType(FishingHook.class));
        assertFalse(StandardProjectileEffectProvider.supportsType(ShulkerBullet.class));
        assertFalse(StandardProjectileEffectProvider.supportsType(ThrownTrident.class));
    }

    @Test
    void customDispatchRequiresItsOwnProvider() {
        assertFalse(StandardProjectileEffectProvider.supportsType(GuidedArrow.class));
        assertFalse(StandardProjectileEffectProvider.supportsType(ComputedMotionArrow.class));
        assertFalse(StandardProjectileEffectProvider.supportsType(CustomOwnerArrow.class));
        assertFalse(StandardProjectileEffectProvider.supportsType(CustomOwnerStorageArrow.class));
    }

    private static class InheritedArrow extends Arrow {
        InheritedArrow(Level level) { super(EntityTypes.ARROW, level); }
    }
    private static final class GuidedArrow extends InheritedArrow {
        GuidedArrow(Level level) { super(level); }
        @Override public void tick() { super.tick(); }
    }
    private static final class ComputedMotionArrow extends InheritedArrow {
        ComputedMotionArrow(Level level) { super(level); }
        @Override public Vec3 getDeltaMovement() { return Vec3.ZERO; }
    }
    private static final class CustomOwnerArrow extends InheritedArrow {
        CustomOwnerArrow(Level level) { super(level); }
        @Override public void setOwner(Entity owner) { super.setOwner(owner); }
    }
    private static final class CustomOwnerStorageArrow extends InheritedArrow {
        CustomOwnerStorageArrow(Level level) { super(level); }
        @Override protected void setOwner(EntityReference<Entity> owner) { super.setOwner(owner); }
    }
}
