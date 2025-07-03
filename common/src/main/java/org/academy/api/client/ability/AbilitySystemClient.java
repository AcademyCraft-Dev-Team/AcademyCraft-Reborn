package org.academy.api.client.ability;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.util.ClientUtil;
import org.academy.api.common.ability.*;
import org.academy.api.common.config.IConfigAction;
import org.academy.api.common.network.SubscribePacket;
import org.academy.internal.common.ability.builtin.level0.Level0;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import static org.academy.api.common.ability.AbilitySystem.SKILL_MAP;

public final class AbilitySystemClient {
    public static final Set<Skill> LEARNED_SKILLS = new CopyOnWriteArraySet<>();
    public static final Map<Skill,Float> SKILL_EXP = new ConcurrentHashMap<>();
    public static final String CONFIG_KEY_ABILITY_SYSTEM = "ability_system";
    public static final String KEY_NAME_ACTIVATE_HUD = "activate_ability_hud";
    public static final InputSystem.InputPair ACTIVATE_HUD_KEY;
    public static final Map<AbilityCategory, List<SkillInfo>> SKILL_INFOS = new HashMap<>();

    @NotNull
    public static volatile AbilityCategory category = Level0.INSTANCE;
    private static volatile boolean activeHUD = false;
    private static volatile float computingPower;
    private static volatile float maximumComputingPower;
    private static volatile int level;

    static {
        AcademyCraftConfig.registerConfigActions(CONFIG_KEY_ABILITY_SYSTEM, Config.Action.INSTANCE);
        var configData = AcademyCraftClient.CLIENT_CONFIG.<Config>getConfig(CONFIG_KEY_ABILITY_SYSTEM);
        ACTIVATE_HUD_KEY = configData.getKeyBinding(KEY_NAME_ACTIVATE_HUD,
                new InputSystem.InputPair(
                        InputSystem.InputType.KEYBOARD,
                        new InputSystem.KeyInfo(
                                new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_V)),
                                GLFW.GLFW_PRESS,
                                new LinkedHashSet<>()
                        )
                )
        );
    }

    public static void init() {
        AcademyCraftClient.CLIENT_NETWORK_MANAGER.registerPacketListener(AbilitySystemClient.class);

        InputSystem.addKeyBinding(KEY_NAME_ACTIVATE_HUD, ACTIVATE_HUD_KEY, () -> {
            if (ClientUtil.hasScreen()) return;
            setActiveHUD(!activeHUD);
        });
        for (var abilityCategory : AbilitySystem.ABILITY_CATEGORY_MAP.values()) {
            abilityCategory.initClient();
            for (var skill : abilityCategory.skillList) {
                skill.initClient();
            }
        }
    }

    @SubscribePacket
    public static void handleExpSync(ExpSyncPacket packet) {
        var name = packet.skillName;
        float exp = packet.exp;
        var skill = SKILL_MAP.get(name);
        if (skill != null) {
            setSkillExp(skill, exp);
        }
    }

    @SubscribePacket
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
            var newCategory = AbilitySystem.ABILITY_CATEGORY_MAP.get(packet.abilityCategory);
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
                for (var skillName : packet.skills) {
                    var skill = SKILL_MAP.get(skillName);
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
        AcademyCraftClient.CLIENT_NETWORK_MANAGER.registerPacketListener(clientContext);
    }

    public static void unregisterContext(ClientContext clientContext) {
        AcademyCraft.EVENT_BUS.unregister(clientContext);
        AcademyCraftClient.CLIENT_NETWORK_MANAGER.unregisterPacketListener(clientContext);
    }

    public static class Config extends KeyBindingConfig {
        public static final class Action implements IConfigAction<Config> {
            public static final IConfigAction<Config> INSTANCE = new Action();

            private Action() {
            }

            @Override
            public @NotNull AbilitySystemClient.Config deserialize(@NotNull JsonElement jsonElement, @NotNull Gson gson) {
                return gson.fromJson(jsonElement, Config.class);
            }

            @Override
            public @NotNull JsonElement serialize(@NotNull AbilitySystemClient.Config configInstance, @NotNull Gson gson) {
                return gson.toJsonTree(configInstance);
            }

            @Override
            public @NotNull AbilitySystemClient.Config getDefaultConfig() {
                return new Config();
            }

            @Override
            public @NotNull Class<Config> getConfigClass() {
                return Config.class;
            }
        }
    }

    public record SkillInfo(Skill skill, List<SkillInfo> dependencies, ResourceLocation texture, float x, float y) {
    }
}