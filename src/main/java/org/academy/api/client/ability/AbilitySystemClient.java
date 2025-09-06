package org.academy.api.client.ability;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.NeoForge;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.util.ClientUtil;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.api.common.ability.ExpSyncPacket;
import org.academy.api.common.ability.PlayerSyncPacket;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.common.network.SubscribePacket;
import org.academy.api.common.registries.Registries;
import org.academy.internal.common.ability.AbilityCategories;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

public final class AbilitySystemClient {
    public static final Set<Skill> LEARNED_SKILLS = new CopyOnWriteArraySet<>();
    public static final Map<Skill,Float> SKILL_EXP = new ConcurrentHashMap<>();
    public static final String CONFIG_KEY_ABILITY_SYSTEM = "ability_system";
    public static final String KEY_NAME_ACTIVATE_HUD = "activate_ability_hud";
    public static final InputSystem.InputPair ACTIVATE_HUD_KEY;
    public static final Map<AbilityCategory, List<SkillInfo>> SKILL_INFOS = new HashMap<>();
    @Nullable
    public static AbilityCategory category;
    private static boolean activeHUD = false;
    private static float computingPower;
    private static float maximumComputingPower;
    private static int level;

    static {
        AcademyCraftConfig.registerTypeHandler(CONFIG_KEY_ABILITY_SYSTEM, Config.Action.INSTANCE);
        var configData = AcademyCraftClient.Config.INSTANCE.<Config>getConfig(CONFIG_KEY_ABILITY_SYSTEM);
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

        for (var category : Registries.ABILITY_CATEGORIES) {
            category.initClient();
        }

        for (var skill : Registries.SKILLS) {
            skill.initClient();
        }
    }

    @SubscribePacket
    public static void handleExpSync(ExpSyncPacket packet) {
        var skillKey = ResourceLocation.parse(packet.getSkillName());
        var exp = packet.getExp();
        var skill = Registries.SKILLS.get(skillKey);
        skill.ifPresent(skillReference -> setSkillExp(skillReference.value(), exp));
    }

    @SubscribePacket
    public static void handleSync(PlayerSyncPacket packet) {
        setLevel(packet.getLevel());
        setMaximumComputingPower(packet.getMaxComputingPower());
        setComputingPower(packet.getCurrentComputingPower());
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

    public static AbilityCategory getCategory() {
        return category == null ? AbilityCategories.LEVEL0.get() : category;
    }

    public static void registerContext(ClientContext clientContext) {
        NeoForge.EVENT_BUS.register(clientContext);
        AcademyCraftClient.CLIENT_NETWORK_MANAGER.registerPacketListener(clientContext);
    }

    public static void unregisterContext(ClientContext clientContext) {
        NeoForge.EVENT_BUS.unregister(clientContext);
        AcademyCraftClient.CLIENT_NETWORK_MANAGER.unregisterPacketListener(clientContext);
    }

    public static class Config extends KeyBindingConfig {
        public static final class Action implements TypeHandler<Config> {
            public static final TypeHandler<Config> INSTANCE = new Action();

            private Action() {
            }

            @Override
            public AbilitySystemClient.Config getDefault() {
                return new Config();
            }

            @Override
            public Class<Config> getTypeClass() {
                return Config.class;
            }
        }
    }

    public record SkillInfo(Skill skill, List<SkillInfo> dependencies, ResourceLocation texture, float x, float y) {
    }
}