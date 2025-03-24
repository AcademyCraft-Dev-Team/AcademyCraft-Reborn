package org.academy;

import net.minecraft.client.Minecraft;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.network.AcademyCraftNetworkSystemClient;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.command.CommandManager;
import org.academy.api.common.network.AcademyCraftNetworkResourceLocations;
import org.academy.api.common.network.FriendlyByteBufDeserializers;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashSet;
import java.util.Set;

public final class AbilitySystemClient {
    private static volatile boolean activeHUD = false;
    private static volatile float computingPower;
    private static volatile float maximumComputingPower;
    public static volatile AbilityCategory category;
    public static final String KEY_NAME = "activate_ability";
    public static final AcademyCraftClientConfig.InputPair KEY = AcademyCraftClient.clientConfig.getKey(
            KEY_NAME,
            new AcademyCraftClientConfig.InputPair(
                    AcademyCraftClientConfig.InputType.KEYBOARD,
                    new InputSystem.InputEvent(
                            new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_V)),
                            GLFW.GLFW_PRESS,
                            new LinkedHashSet<>()
                    )
            )
    );

    public static void initClient() {
        registerPacketHandler();
        InputSystem.addKeyBinding(KEY_NAME, KEY, () -> setActiveHUD(!activeHUD));
        for (AbilityCategory abilityCategory : AbilitySystem.ABILITY_CATEGORY_MAP.values()) {
            abilityCategory.initClient();
            for (Skill skill : abilityCategory.skillList) {
                skill.initClient();
            }
        }
        CommandManager.Client.registerCommands();
        CommandManager.Client.registerPacketHandler();
    }

    public static void registerPacketHandler() {
        AcademyCraftNetworkSystemClient.registerServerToClientPacketHandler(
                AcademyCraftNetworkResourceLocations.S2C_ABILITY_CATEGORY_SYNC_PACKET,
                (handler, packet) ->
                        category = FriendlyByteBufDeserializers
                                .ABILITY_CATEGORY_FRIENDLY_BYTE_BUF_DESERIALIZER
                                .deserialize(packet.friendlyByteBuf)
        );
        AcademyCraftNetworkSystemClient.registerServerToClientPacketHandler(
                AcademyCraftNetworkResourceLocations.S2C_COMPUTING_POWER_SYNC_PACKET,
                (handler, packet) ->
                        setComputingPower(packet.friendlyByteBuf.readFloat())
        );
        AcademyCraftNetworkSystemClient.registerServerToClientPacketHandler(
                AcademyCraftNetworkResourceLocations.S2C_MAX_COMPUTING_POWER_SYNC_PACKET,
                (handler, packet) ->
                        setMaximumComputingPower(packet.friendlyByteBuf.readFloat())
        );
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

    public static AbilityCategory getCategory() {
        return category;
    }
}