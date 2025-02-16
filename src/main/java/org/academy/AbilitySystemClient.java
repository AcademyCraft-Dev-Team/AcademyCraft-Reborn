package org.academy;

import net.fabricmc.api.ClientModInitializer;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.api.common.ability.Skill;
import org.academy.api.client.input.InputSystem;
import org.lwjgl.glfw.GLFW;

import java.util.Collections;

public class AbilitySystemClient implements ClientModInitializer {
    public static boolean active = false;

    @Override
    public void onInitializeClient() {
        InputSystem.addKeyRelease(Collections.singletonList(GLFW.GLFW_KEY_V), () -> active = !active);
        for (AbilityCategory abilityCategory : AbilitySystem.abilityCategoryMap.values()) {
            for (Skill skill : abilityCategory.skillList) {
                skill.initClient();
            }
        }
    }
}
