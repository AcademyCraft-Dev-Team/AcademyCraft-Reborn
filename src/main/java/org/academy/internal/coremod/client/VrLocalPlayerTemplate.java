package org.academy.internal.coremod.client;

import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.chat.ChatAbilities;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.StatsCounter;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Input;
import org.academy.api.common.ability.ImagineBreakerHealthAccess;
import org.academy.internal.client.ability.VectorReflectionClientRuntime;

/**
 * Field-free bytecode template for generated local-player dispatch subclasses.
 */
public class VrLocalPlayerTemplate extends LocalPlayer implements ImagineBreakerHealthAccess {
    public VrLocalPlayerTemplate(Minecraft minecraft, ClientLevel level, ClientPacketListener connection,
                                 StatsCounter stats, ClientRecipeBook recipeBook, Input lastSentInput,
                                 boolean wasSprinting, ChatAbilities chatAbilities) {
        super(minecraft, level, connection, stats, recipeBook, lastSentInput, wasSprinting, chatAbilities);
    }

    private boolean academy$protected() {
        return VectorReflectionClientRuntime.isProtected(this);
    }

    @Override
    public float getHealth() {
        var original = super.getHealth();
        return academy$protected()
                ? VectorReflectionClientRuntime.protectHealthRead(this, original)
                : original;
    }

    @Override
    public void imaginebreaker(float amount) {
        VectorReflectionClientRuntime.imaginebreaker(this, amount);
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
    public boolean hurtClient(DamageSource source) {
        if (!academy$protected()) return super.hurtClient(source);
        VectorReflectionClientRuntime.sanitize(this);
        return true;
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
