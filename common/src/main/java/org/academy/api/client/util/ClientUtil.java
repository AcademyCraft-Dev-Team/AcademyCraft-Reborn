package org.academy.api.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.common.ability.Skill;
import org.academy.internal.common.sounds.AcademyCraftSoundEvents;

public class ClientUtil {
    public static final Minecraft MINECRAFT = Minecraft.getInstance();

    private ClientUtil() {
    }

    public static boolean hasScreen() {
        return Minecraft.getInstance().screen != null;
    }

    public static boolean lacksSkill(Skill skill) {
        return !AbilitySystemClient.LEARNED_SKILLS.contains(skill);
    }

    public static float animationFactor(float animationDuration) {
        return MINECRAFT.getDeltaFrameTime() / animationDuration;
    }

    public static double animationFactor(double animationDuration) {
        return MINECRAFT.getDeltaFrameTime() / animationDuration;
    }

    public static float magicAnimationFactor(float animationDuration) {
        return 1 - (float) Math.exp(-Math.log(20) * MINECRAFT.getDeltaFrameTime() / 20 / animationDuration);
    }

    public static void playDownSound(){
        MINECRAFT.getSoundManager().play(SimpleSoundInstance.forUI(AcademyCraftSoundEvents.SELECT, 1.0F));
    }
}