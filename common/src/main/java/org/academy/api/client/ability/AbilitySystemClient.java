package org.academy.api.client.ability;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftClient;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.network.NetworkSystemClient;
import org.academy.api.client.util.ClientUtil;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.api.common.ability.AbilitySystem;
import org.academy.api.common.ability.PlayerSyncPacket;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.network.ClassPacketHandler;
import org.academy.api.common.network.NetworkSystem;
import org.academy.api.common.network.Packets;
import org.academy.internal.common.ability.builtin.level0.Level0;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import static org.academy.api.common.ability.AbilitySystem.SKILL_MAP;

public final class AbilitySystemClient {
    public static final Set<Skill> LEARNED_SKILLS = new CopyOnWriteArraySet<>();
    public static final Map<Skill,Float> SKILL_EXP = new ConcurrentHashMap<>();
    public static final String KEY_NAME = "activate_ability";
    public static final InputSystem.InputPair KEY = AcademyCraftClient.CLIENT_CONFIG.getKey(
            KEY_NAME,
            new InputSystem.InputPair(
                    InputSystem.InputType.KEYBOARD,
                    new InputSystem.KeyInfo(
                            new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_V)),
                            GLFW.GLFW_PRESS,
                            new LinkedHashSet<>()
                    )
            )
    );
    @NotNull
    public static volatile AbilityCategory category = Level0.INSTANCE;
    private static volatile boolean activeHUD = false;
    private static volatile float computingPower;
    private static volatile float maximumComputingPower;
    private static volatile int level;

    public static void init() {
        NetworkSystem.registerPacketListener(AbilitySystemClient.class);

        InputSystem.addKeyBinding(KEY_NAME, KEY, () -> {
            if (ClientUtil.hasScreen()) return;
            setActiveHUD(!activeHUD);
        });
        for (AbilityCategory abilityCategory : AbilitySystem.ABILITY_CATEGORY_MAP.values()) {
            abilityCategory.initClient();
            for (Skill skill : abilityCategory.skillList) {
                skill.initClient();
            }
        }

        NetworkSystemClient.registerS2CPacketHandler(
                Packets.S2C_EXP_SYNC,
                (listener, packet) -> {
                    FriendlyByteBuf friendlyByteBuf = packet.friendlyByteBuf;
                    String name =  friendlyByteBuf.readUtf();
                    float exp =  friendlyByteBuf.readFloat();
                    Skill skill = SKILL_MAP.get(name);
                    if (skill != null) {
                        setSkillExp(skill, exp);
                    }
                }
        );
    }

    @ClassPacketHandler
    public static void handleSync(PlayerSyncPacket packet) {
        if (packet.levelChanged) {
            setLevel(packet.level);
        }
        if (packet.maxComputingPowerChanged) {
            setMaximumComputingPower(packet.maxComputingPower);
        }
        if (packet.currentComputingPowerChanged) {
            setComputingPower(packet.currentComputingPower);
        }
        if (packet.abilityCategoryChanged) {
            AbilityCategory newCategory = AbilitySystem.ABILITY_CATEGORY_MAP.get(packet.abilityCategory);
            if (newCategory != null) {
                category = newCategory;
            } else {
                AcademyCraft.LOGGER.warn("Received unknown ability category: {}", packet.abilityCategory);
                category = Level0.INSTANCE;
            }
        }
        if (packet.skillsChanged) {
            LEARNED_SKILLS.clear();
            if (packet.skills != null) {
                for (String skillName : packet.skills) {
                    Skill skill = SKILL_MAP.get(skillName);
                    if (skill != null) {
                        LEARNED_SKILLS.add(skill);
                    } else {
                        AcademyCraft.LOGGER.warn("Received unknown skill name during sync: {}", skillName);
                    }
                }
            }
        }
    }

    public static float getSkillExp(Skill skill) {
        return SKILL_EXP.getOrDefault(skill, 0f);
    }

    public static void setSkillExp(Skill skill, float exp) {
        SKILL_EXP.put(skill, exp);
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

    public static int getLevel() {
        return level;
    }

    public static void setLevel(int level) {
        Minecraft.getInstance().execute(() -> AbilitySystemClient.level = level);
    }

    public static boolean isActiveHUD() {
        return activeHUD;
    }

    public static void setActiveHUD(boolean activeHUD) {
        Minecraft.getInstance().execute(() -> AbilitySystemClient.activeHUD = activeHUD);
    }

    @NotNull
    public static AbilityCategory getCategory() {
        return category;
    }

    public static void registerContext(ClientContext clientContext) {
        AcademyCraft.EVENT_BUS.register(clientContext);
    }

    public static void unregisterContext(ClientContext clientContext) {
        AcademyCraft.EVENT_BUS.unregister(clientContext);
    }
}