package org.academy;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import org.academy.api.client.command.CommandManager;
import org.academy.api.client.input.InputSystem;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.api.common.ability.Skill;
import org.lwjgl.glfw.GLFW;

import java.util.Collections;

public final class AbilitySystemClient {
    public static boolean active = false;

    public static void init() {
        InputSystem.KEY_RELEASE_MAP.put(Collections.singletonList(GLFW.GLFW_KEY_V), () -> active = !active);
        ClientLifecycleEvents.CLIENT_STARTED.register(minecraft -> {
            for (AbilityCategory abilityCategory : AbilitySystem.abilityCategoryMap.values()) {
                abilityCategory.initClient();
                for (Skill skill : abilityCategory.skillList) {
                    skill.initClient();
                }
            }
            CommandManager.registerCommands();
        });
    }
}