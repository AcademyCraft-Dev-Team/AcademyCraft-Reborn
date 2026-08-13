package org.academy.internal.common.ability.accelerator.reflection.compat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.Projectile;
import org.academy.internal.common.ability.accelerator.skills.lv3.VectorDeviation;
import org.academy.internal.common.ability.accelerator.skills.lv4.VectorReflection;

public final class VectorProjectileInterceptionService {
    private VectorProjectileInterceptionService() {
    }

    public static boolean canIntercept(ServerPlayer player, Projectile projectile) {
        if (VectorProjectileRedirects.isRedirected(projectile)) return false;
        return VectorReflection.Server.shouldReflectProjectileFor(player, projectile)
                || VectorDeviation.Server.shouldRefractProjectileFor(player, projectile);
    }

    public static boolean intercept(ServerPlayer player, Projectile projectile) {
        if (VectorProjectileRedirects.isRedirected(projectile)) return false;
        if (VectorReflection.Server.shouldReflectProjectileFor(player, projectile)) {
            return VectorReflection.Server.reflectProjectile(player, projectile);
        }
        return VectorDeviation.Server.shouldRefractProjectileFor(player, projectile)
                && VectorDeviation.Server.refractProjectile(player, projectile);
    }
}
