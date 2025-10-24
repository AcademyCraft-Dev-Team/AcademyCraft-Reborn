package org.academy.api.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.util.thread.ReentrantBlockableEventLoop;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.common.ability.Skill;
import org.academy.internal.common.sounds.SoundEvents;

public class ClientUtil {
    private ClientUtil() {
    }

    public static boolean hasScreen() {
        return Minecraft.getInstance().screen != null;
    }

    public static boolean lacksSkill(Skill skill) {
        return !AbilitySystemClient.LEARNED_SKILLS.contains(skill);
    }

    public static float animationFactor(float animationDuration) {
        return Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaTicks() / animationDuration;
    }

    public static double animationFactor(double animationDuration) {
        return Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaTicks() / animationDuration;
    }

    public static void playDownSound() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.SELECT, 1.0F));
    }

    public static ReentrantBlockableEventLoop<?> getRenderThreadEventLoop() {
        return Minecraft.getInstance();
    }

    public static ReentrantBlockableEventLoop<?> getMainThreadEventLoop() {
        return Minecraft.getInstance();
    }
}