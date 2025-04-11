package org.academy.api.client.ability;

import net.minecraft.client.Minecraft;
import org.academy.AcademyCraftClient;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.network.NetworkSystemClient;
import org.academy.api.client.util.ClientUtil;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.api.common.ability.AbilitySystem;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.command.CommandManager;
import org.academy.api.common.network.FriendlyByteBufDeserializer;
import org.academy.api.common.network.FriendlyByteBufDeserializers;
import org.academy.api.common.network.Packets;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

public final class AbilitySystemClient {
    public static final Set<Skill> SKILLS = new HashSet<>();
    public static final String KEY_NAME = "activate_ability";
    public static final InputSystem.InputPair KEY = AcademyCraftClient.CLIENT_CONFIG.getKey(
            KEY_NAME,
            new InputSystem.InputPair(
                    InputSystem.InputType.KEYBOARD,
                    new InputSystem.InputEvent(
                            new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_V)),
                            GLFW.GLFW_PRESS,
                            new LinkedHashSet<>()
                    )
            )
    );
    public static volatile AbilityCategory category;
    private static volatile boolean activeHUD = false;
    private static volatile float computingPower;
    private static volatile float maximumComputingPower;

    public static void init() {
        registerPacketHandler();
        InputSystem.addKeyBinding(KEY_NAME, KEY, () -> {
            if (!ClientUtil.isScreenNull()) return;
            setActiveHUD(!activeHUD);
        });
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
        NetworkSystemClient.registerS2CPacketHandler(
                Packets.S2C_ABILITY_CATEGORY_SYNC,
                (handler, packet) ->
                        category = FriendlyByteBufDeserializers
                                .ABILITY_CATEGORY_FRIENDLY_BYTE_BUF_DESERIALIZER.deserialize(packet.friendlyByteBuf)
        );
        NetworkSystemClient.registerS2CPacketHandler(
                Packets.S2C_COMPUTING_POWER_SYNC,
                (handler, packet) ->
                        setComputingPower(packet.friendlyByteBuf.readFloat())
        );
        NetworkSystemClient.registerS2CPacketHandler(
                Packets.S2C_MAX_COMPUTING_POWER_SYNC,
                (handler, packet) ->
                        setMaximumComputingPower(packet.friendlyByteBuf.readFloat())
        );
        NetworkSystemClient.registerS2CPacketHandler(
                Packets.S2C_SKILLS_SYC,
                (listener, packet) -> {
                    FriendlyByteBufDeserializer<ArrayList<Skill>> friendlyByteBufDeserializer =
                            FriendlyByteBufDeserializers.getArrayListFriendlyByteBufDeserializer(Skill.class);
                    ArrayList<Skill> skillList = friendlyByteBufDeserializer.deserialize(packet.friendlyByteBuf);
                    SKILLS.clear();
                    SKILLS.addAll(skillList);
                }
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