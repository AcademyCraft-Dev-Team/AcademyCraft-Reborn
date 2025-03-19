package org.academy;

import net.minecraft.client.Minecraft;
import org.academy.api.client.command.CommandManager;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.network.AcademyCraftNetworkSystemClient;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.network.AcademyCraftNetworkResourceLocations;
import org.academy.api.common.network.FriendlyByteBufDeserializers;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashSet;
import java.util.Set;

public final class AbilitySystemClient {
    private static volatile boolean activeHUD = false;
    private static volatile float computingPower;
    private static volatile float maximumComputingPower;
    private static AbilityCategory category;
    public static final String KEY_NAME = "activate_ability";
    public static final AcademyCraftClientConfig.InputPair KEY = AcademyCraftClient.clientConfig.getKey(KEY_NAME, new AcademyCraftClientConfig.InputPair(AcademyCraftClientConfig.InputType.KEYBOARD, new InputSystem.InputEvent(new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_V)), GLFW.GLFW_PRESS, new LinkedHashSet<>())));

    public static void initClient() {
        AcademyCraftNetworkSystemClient.SERVER_TO_CLIENT_PACKET_HANDLER_MAP.put(AcademyCraftNetworkResourceLocations.S2C_SYNC_PACKET, (handler, packet) -> synchronizeServerToClient(packet.friendlyByteBuf.readFloat(), packet.friendlyByteBuf.readFloat()));
        AcademyCraftNetworkSystemClient.SERVER_TO_CLIENT_PACKET_HANDLER_MAP.put(AcademyCraftNetworkResourceLocations.S2C_INIT_PACKET, (handler, packet) -> initServerPlayerToClient(FriendlyByteBufDeserializers.getDeserializer(AbilityCategory.class).isPresent() ? FriendlyByteBufDeserializers.getDeserializer(AbilityCategory.class).get().deserialize(packet.friendlyByteBuf) : null));
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

    public static void initServerPlayerToClient(@Nullable AbilityCategory abilityCategory) {
        if (abilityCategory == null) {
            AcademyCraft.LOGGER.warn("Init: AbilityCategory is null");
        } else {
            category = abilityCategory;
        }
    }

    public static void synchronizeServerToClient(final float currentComputingPower, final float maxComputingPower) {
        AbilitySystemClient.setComputingPower(currentComputingPower);
        AbilitySystemClient.setMaximumComputingPower(maxComputingPower);
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