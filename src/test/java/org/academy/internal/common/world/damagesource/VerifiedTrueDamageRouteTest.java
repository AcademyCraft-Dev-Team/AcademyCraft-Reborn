package org.academy.internal.common.world.damagesource;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerifiedTrueDamageRouteTest {
    @Test
    void legacyDirectDamageInjectionDescriptorRemainsAvailable() throws ReflectiveOperationException {
        // Addons resolve this exact descriptor when SkillDamageUtil is transformed on first hit.
        // Missing it prevents the entire utility class (including CTA/VEC) from loading.
        var method = SkillDamageUtil.class.getDeclaredMethod("applyDirectWithFallback",
                net.minecraft.server.level.ServerLevel.class,
                net.minecraft.server.level.ServerPlayer.class,
                net.minecraft.world.entity.LivingEntity.class,
                org.academy.api.common.ability.Skill.class,
                net.minecraft.world.damagesource.DamageSource.class, float.class);
        assertTrue(java.lang.reflect.Modifier.isStatic(method.getModifiers()));
        assertTrue(method.getReturnType() == boolean.class);
    }

    @Test
    void kineticShockwaveKeepsTheFullVerifiedHealthSubtraction() throws IOException {
        var source = Files.readString(Path.of(
                "src/main/java/org/academy/internal/common/world/damagesource/CTADamageUtil.java"));

        assertTrue(source.contains("SkillDamageUtil.applyVerifiedTrueHealth(target, source, damage)"));
        assertFalse(source.contains("target.hurtServer"));
    }

    @Test
    void vectorBlastDoesNotOfferItsDamageAmountToCustomCaps() throws IOException {
        var source = Files.readString(Path.of(
                "src/main/java/org/academy/internal/common/ability/accelerator/skills/lv1/VectorBlast.java"));

        assertTrue(source.contains("SkillDamageUtil.applyVerifiedTrueHealth(target, source, damage)"));
        assertFalse(source.contains("target.hurtServer(level, source, damage)"));
    }

    @Test
    void baseHurtAndDeathHooksRecognizeCompatibilityPasses() throws IOException {
        var source = Files.readString(Path.of(
                "src/main/java/org/academy/mixin/common/MixinLivingEntity.java"));

        assertTrue(source.contains("TrueDamageCompatibility.isHurtProbe(victim, source)"));
        assertTrue(source.contains("TrueDamageCompatibility.onVanillaDieEntered"));
        assertTrue(source.contains("SkillDamageUtil.applyDirectFromHurtServer"));
    }
}
