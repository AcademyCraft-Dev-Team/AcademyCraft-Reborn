package org.academy.internal.coremod;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import org.academy.api.common.ability.ImagineBreakerHealthAccess;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class DispatchSubclassFactoryTest {
    @Test
    void generatesFieldFreeSubclassForCustomPlayerLayout() throws NoSuchMethodException {
        var result = DispatchSubclassFactory.forPlayerType(CustomServerPlayer.class);
        assertTrue(result.successful(), result.failureReason());
        assertSame(CustomServerPlayer.class, result.dispatchType().getSuperclass());
        assertTrue(HotSpotClassPointerAccess.hasNoInstanceFields(result.dispatchType()));
        assertTrue(ImagineBreakerHealthAccess.class.isAssignableFrom(result.dispatchType()));
        assertTrue(Modifier.isPublic(result.dispatchType()
                .getMethod("imaginebreaker", float.class).getModifiers()));
        assertInlineProtectionBoundary(result.dispatchType());
        assertDamageStateBoundary(result.dispatchType());
        assertSame(result.dispatchType(),
                DispatchSubclassFactory.forPlayerType(CustomServerPlayer.class).dispatchType());
    }

    @Test
    void rejectsFinalPlayerTypeBeforePointerWrite() {
        var result = DispatchSubclassFactory.forPlayerType(FinalServerPlayer.class);
        assertFalse(result.successful());
        assertTrue(result.failureReason().contains("final"));
    }

    @Test
    void rejectsFinalProtectedOverrideBeforePointerWrite() {
        var result = DispatchSubclassFactory.forPlayerType(FinalHealthServerPlayer.class);
        assertFalse(result.successful());
        assertTrue(result.failureReason().contains("final"));
    }

    @Test
    void generatedServerDispatchDeclaresCompleteEffectBoundary() throws NoSuchMethodException {
        var result = DispatchSubclassFactory.forPlayerType(CustomServerPlayer.class);
        assertTrue(result.successful(), result.failureReason());
        assertEffectBoundary(result.dispatchType());
        assertDamageStateBoundary(result.dispatchType());
    }

    @Test
    void generatedClientDispatchDeclaresCompleteEffectBoundary() throws NoSuchMethodException {
        var result = DispatchSubclassFactory.forPlayerType(LocalPlayer.class);
        assertTrue(result.successful(), result.failureReason());
        assertSame(LocalPlayer.class, result.dispatchType().getSuperclass());
        assertEffectBoundary(result.dispatchType());
        assertDamageStateBoundary(result.dispatchType());
    }

    private static void assertEffectBoundary(Class<?> dispatchType) throws NoSuchMethodException {
        assertSame(dispatchType, dispatchType.getDeclaredMethod(
                "addEffect", MobEffectInstance.class, Entity.class).getDeclaringClass());
        assertSame(dispatchType, dispatchType.getDeclaredMethod(
                "canBeAffected", MobEffectInstance.class).getDeclaringClass());
        assertSame(dispatchType, dispatchType.getDeclaredMethod(
                "forceAddEffect", MobEffectInstance.class, Entity.class).getDeclaringClass());
        assertSame(dispatchType, dispatchType.getDeclaredMethod(
                "onEffectAdded", MobEffectInstance.class, Entity.class).getDeclaringClass());
        assertSame(dispatchType, dispatchType.getDeclaredMethod(
                "onEffectUpdated", MobEffectInstance.class, boolean.class, Entity.class).getDeclaringClass());
        assertSame(dispatchType, dispatchType.getDeclaredMethod(
                "sendEffectToPassengers", MobEffectInstance.class).getDeclaringClass());
        assertSame(dispatchType, dispatchType.getDeclaredMethod(
                "hasEffect", Holder.class).getDeclaringClass());
        assertSame(dispatchType, dispatchType.getDeclaredMethod(
                "getEffect", Holder.class).getDeclaringClass());
    }

    private static void assertInlineProtectionBoundary(Class<?> dispatchType)
            throws NoSuchMethodException {
        assertSame(dispatchType, dispatchType.getDeclaredMethod("getHealth").getDeclaringClass());
        assertSame(dispatchType, dispatchType.getDeclaredMethod(
                "setHealth", float.class).getDeclaringClass());
        assertSame(dispatchType, dispatchType.getDeclaredMethod(
                "hurtServer", ServerLevel.class, DamageSource.class, float.class).getDeclaringClass());
        assertSame(dispatchType, dispatchType.getDeclaredMethod(
                "actuallyHurt", ServerLevel.class, DamageSource.class, float.class).getDeclaringClass());
        assertSame(dispatchType, dispatchType.getDeclaredMethod("tick").getDeclaringClass());
        assertSame(dispatchType, dispatchType.getDeclaredMethod(
                "onSyncedDataUpdated", EntityDataAccessor.class).getDeclaringClass());

        for (var field : dispatchType.getDeclaredFields()) {
            assertTrue(Modifier.isPrivate(field.getModifiers()));
            assertTrue(Modifier.isStatic(field.getModifiers()));
            assertFalse(field.getName().toLowerCase().contains("health"));
            assertTrue(field.getName().startsWith("f$"));
        }
        assertEquals(5, dispatchType.getDeclaredFields().length);
        assertTrue(Arrays.stream(dispatchType.getDeclaredMethods())
                .filter(method -> Modifier.isPrivate(method.getModifiers()))
                .allMatch(method -> method.getName().startsWith("m$")
                        && !method.getName().toLowerCase().contains("health")));
        assertFalse(dispatchType.getName().toLowerCase().contains("health"));
        assertFalse(dispatchType.getName().contains("VectorReflection"));
    }

    private static void assertDamageStateBoundary(Class<?> dispatchType)
            throws NoSuchMethodException {
        assertSame(dispatchType, dispatchType.getDeclaredMethod("markHurt").getDeclaringClass());
        assertSame(dispatchType, dispatchType.getDeclaredMethod(
                "animateHurt", float.class).getDeclaringClass());
        assertSame(dispatchType, dispatchType.getDeclaredMethod(
                "indicateDamage", double.class, double.class).getDeclaringClass());
        assertSame(dispatchType, dispatchType.getDeclaredMethod(
                "handleDamageEvent", DamageSource.class).getDeclaringClass());
        assertSame(dispatchType, dispatchType.getDeclaredMethod(
                "onSyncedDataUpdated", EntityDataAccessor.class).getDeclaringClass());
    }

    public static class CustomServerPlayer extends ServerPlayer {
        int customNumber;
        Object customReference;

        public CustomServerPlayer(MinecraftServer server, ServerLevel level, GameProfile profile,
                                  ClientInformation clientInformation) {
            super(server, level, profile, clientInformation);
        }
    }

    public static final class FinalServerPlayer extends ServerPlayer {
        public FinalServerPlayer(MinecraftServer server, ServerLevel level, GameProfile profile,
                                 ClientInformation clientInformation) {
            super(server, level, profile, clientInformation);
        }
    }

    public static class FinalHealthServerPlayer extends ServerPlayer {
        public FinalHealthServerPlayer(MinecraftServer server, ServerLevel level, GameProfile profile,
                                       ClientInformation clientInformation) {
            super(server, level, profile, clientInformation);
        }

        @Override
        public final float getHealth() {
            return super.getHealth();
        }
    }
}
