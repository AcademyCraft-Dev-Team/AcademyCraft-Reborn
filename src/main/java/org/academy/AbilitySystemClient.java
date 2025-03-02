package org.academy;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.Minecraft;
import org.academy.api.client.command.CommandManager;
import org.academy.api.client.input.InputSystem;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.api.common.ability.Skill;
import org.academy.internal.client.ui.hud.AcademyCraftHUDSystem;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class AbilitySystemClient {
    private static volatile boolean active = false;
    private static volatile float computingPower;
    private static volatile float maximumComputingPower;
    public static final String KEY_NAME = "activate_ability";
    public static final Supplier<List<Integer>> KEY = () -> AcademyCraftClient.clientConfig.getKey(KEY_NAME, List.of(GLFW.GLFW_KEY_V));

    public static void init() {
        ClientLifecycleEvents.CLIENT_STARTED.register(minecraft -> {
            for (AbilityCategory abilityCategory : AbilitySystem.ABILITY_CATEGORY_MAP.values()) {
                abilityCategory.initClient();
                for (Skill skill : abilityCategory.skillList) {
                    skill.initClient();
                }
            }

            CommandManager.registerCommands();

            AcademyCraft.executorService.scheduleAtFixedRate(() -> {
                if (minecraft.level != null) {
                }
            }, 0, 50, TimeUnit.MILLISECONDS);
        });
        AcademyCraftHUDSystem.init();
        InputSystem.KEY_RELEASE_MAP.put(KEY_NAME, new InputSystem.KeyBinding(KEY, () -> setActive(!active)));
    }

    public static float getComputingPower() {
        return computingPower;
    }

    public static void setComputingPower(float computingPower) {
        Minecraft.getInstance().execute(() -> AbilitySystemClient.computingPower = computingPower);
    }

    public static float getMaximumComputingPower() {
        return maximumComputingPower;
    }

    public static void setMaximumComputingPower(float maximumComputingPower) {
        Minecraft.getInstance().execute(() -> AbilitySystemClient.maximumComputingPower = maximumComputingPower);
    }

    public static boolean isActive() {
        return active;
    }

    public static void setActive(boolean active) {
        Minecraft.getInstance().execute(() -> AbilitySystemClient.active = active);
    }
}