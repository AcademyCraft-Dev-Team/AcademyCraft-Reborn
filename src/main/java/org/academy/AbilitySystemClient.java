package org.academy;

import net.minecraft.client.Minecraft;
import org.academy.api.client.command.CommandManager;
import org.academy.api.client.input.InputSystem;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.api.common.ability.Skill;
import org.lwjgl.glfw.GLFW;

import java.util.Set;

public final class AbilitySystemClient {
    private static volatile boolean activeHUD = false;
    private static volatile float computingPower;
    private static volatile float maximumComputingPower;
    private static AbilityCategory category;
    public static final String KEY_NAME = "activate_ability";
    public static final AcademyCraftClientConfig.InputPair KEY = AcademyCraftClient.clientConfig.getKey(KEY_NAME,
            new AcademyCraftClientConfig.InputPair(AcademyCraftClientConfig.InputType.KEYBOARD, new InputSystem.InputEvent(
                    Set.of(GLFW.GLFW_KEY_V),
                    GLFW.GLFW_PRESS,
                    Set.of()
            )));

    public static void initClient() {
        Runnable runnable = () -> setActiveHUD(!activeHUD);
        InputSystem.registerKeyBinding(KEY_NAME, KEY, runnable);
        for (AbilityCategory abilityCategory : AbilitySystem.ABILITY_CATEGORY_MAP.values()) {
            abilityCategory.initClient();
            for (Skill skill : abilityCategory.skillList) {
                skill.initClient();
            }
        }
        CommandManager.registerCommands();
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

    public static boolean isActiveHUD() {
        return activeHUD;
    }

    public static void setActiveHUD(boolean activeHUD) {
        Minecraft.getInstance().execute(() -> AbilitySystemClient.activeHUD = activeHUD);
    }
}