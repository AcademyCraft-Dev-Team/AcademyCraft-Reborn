package org.academy.api.client.ability;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.common.NeoForge;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.hud.ability.AbilityInfoHud;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.util.ClientUtil;
import org.academy.api.common.ability.*;
import org.academy.api.common.ability.pakcet.SyncAbilityCategoryPacket;
import org.academy.api.common.ability.pakcet.SyncAbilityDataPacket;
import org.academy.api.common.ability.pakcet.SyncSkillDataPacket;
import org.academy.api.common.data.AbilityData;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.common.registries.Registries;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.skilldata.SkillData;
import org.jspecify.annotations.Nullable;
import org.misaka.MisakaNetworkClient;
import org.misaka.api.common.network.annotation.SubscribePacket;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

public final class AbilitySystemClient {
    public static final Set<Skill> LEARNED_SKILLS = new CopyOnWriteArraySet<>();
    public static final String CONFIG_KEY_ABILITY_SYSTEM = "ability_system";
    public static final String KEY_NAME_ACTIVATE_HUD = "activate_ability_hud";
    public static final InputSystem.KeyCombination ACTIVATE_HUD_KEY;
    private static final Map<String, SkillData> SKILL_DATA = new ConcurrentHashMap<>();
    private static final Map<AbilityCategory, List<SkillInfo>> SKILL_INFOS = new HashMap<>();
    @Nullable
    public static AbilityCategory category;
    private static boolean activeHUD = false;
    private static AbilityData cpData = new AbilityData();

    private static volatile DevState devState = DevState.IDLE;
    private static volatile float devProgress = 0f;
    private static volatile String devMessage = "";

    static {
        AcademyCraftConfig.registerTypeHandler(CONFIG_KEY_ABILITY_SYSTEM, Config.Action.INSTANCE);
        var configData = AcademyCraftClient.Config.INSTANCE.<Config>getConfig(CONFIG_KEY_ABILITY_SYSTEM);
        ACTIVATE_HUD_KEY = configData.getKeyBinding(KEY_NAME_ACTIVATE_HUD,
                InputSystem.combo(
                        InputSystem.InputType.KEYBOARD,
                        InputConstants.KEY_V,
                        InputConstants.PRESS,
                        0
                )
        );
    }

    public static SkillInfo addSkillInfo(AbilityCategory category, SkillInfo skillInfo) {
        if (!SKILL_INFOS.containsKey(category)) {
            SKILL_INFOS.put(category, new ArrayList<>());
        }
        SKILL_INFOS.get(category).add(skillInfo);
        return skillInfo;
    }

    public static Map<AbilityCategory, List<SkillInfo>> getSkillInfos() {
        return SKILL_INFOS;
    }

    public static void init() {
        MisakaNetworkClient.NETWORK_MANAGER.register(AbilitySystemClient.class);
        InputSystem.addKeyBinding(KEY_NAME_ACTIVATE_HUD, ACTIVATE_HUD_KEY, ctx -> {
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
    public static void handleSync(SyncAbilityCategoryPacket packet) {
        category = packet.getAbilityCategory();
    }

    @SubscribePacket
    public static void handleSync(SyncAbilityDataPacket packet) {
        cpData = packet.getAbilityData();
    }

    @SubscribePacket
    public static void handleDevSync(DevSyncPacket packet) {
        devState = packet.getState();
        devProgress = packet.getProgress();
        devMessage = packet.getMessage();
    }

    public static DevState getDevState() {
        return devState;
    }

    public static float getDevProgress() {
        return devProgress;
    }

    public static String getDevMessage() {
        return devMessage;
    }

    public static void resetDevState() {
        devState = DevState.IDLE;
        devProgress = 0;
        devMessage = "";
    }

    @SubscribePacket
    public static void handleSync(SyncSkillDataPacket packet) {
        var newData = packet.getSkillDataMap();

        SKILL_DATA.clear();
        SKILL_DATA.putAll(newData);

        LEARNED_SKILLS.clear();
        newData.keySet().forEach(skillId -> Registries.SKILLS.get(Identifier.parse(skillId))
                .ifPresent(holder -> LEARNED_SKILLS.add(holder.value())));
    }

    public static boolean canUseSkill(Skill skill) {
        return LEARNED_SKILLS.contains(skill)
                && getSkillData(skill).map(SkillData::isEnabled).orElse(false);
    }

    public static boolean isSkillLearned(Skill skill) {
        return LEARNED_SKILLS.contains(skill);
    }

    public static float getAvailableCP() {
        return cpData.getAvailableCP();
    }

    public static float getMaxCP() {
        return cpData.getMaxCP();
    }

    public static AbilityLevel getLevel() {
        return cpData.getLevel();
    }

    public static float getAbilityExp() {
        return cpData.getAbilityExp();
    }

    public static boolean canLevelUp() {
        return getLevel().getLevelCode() < 5 && getAbilityExp() >= 1f;
    }

    public static boolean isActiveHUD() {
        return activeHUD;
    }

    public static void setActiveHUD(boolean activeHUD) {
        AbilitySystemClient.activeHUD = activeHUD;
        AbilityInfoHud.Companion.getInstance().toggleActive();
    }

    public static int getCurrSP() {
        return cpData.getCurrSP();
    }

    public static int getMaxSP() {
        return cpData.getMaxSP();
    }

    public static float getFreeCPRatio() {
        var max = cpData.getMaxCP();
        if (max <= 0) return 0f;
        return cpData.getAvailableCP() / max;
    }

    public static float getCurrMP() {
        return cpData.getCurrMP();
    }

    public static float getMaxMP() {
        return cpData.getMaxMP();
    }

    public static AbilityCategory getCategory() {
        return category == null ? AbilityCategories.LEVEL0.get() : category;
    }

    public static float getSkillExp(Skill skill) {
        return SKILL_DATA.get(skill.getKeyString()).getExp();
    }

    public static void setSkillExp(Skill skill, float exp) {
        var skillId = Objects.requireNonNull(Registries.SKILLS.getKey(skill)).toString();
        var data = SKILL_DATA.get(skillId);
        if (data != null) {
            data.setExp(exp);
        }
    }

    public static Optional<SkillData> getSkillData(Skill skill) {
        var key = Registries.SKILLS.getKey(skill);
        if (key == null) return Optional.empty();
        return Optional.ofNullable(SKILL_DATA.get(key.toString()));
    }

    public static <T extends SkillData> Optional<T> getSkillData(Skill skill, Class<T> type) {
        return getSkillData(skill).filter(type::isInstance).map(type::cast);
    }

    public static void registerContext(ClientContext clientContext) {
        NeoForge.EVENT_BUS.register(clientContext);
        MisakaNetworkClient.NETWORK_MANAGER.register(clientContext);
    }

    public static void unregisterContext(ClientContext clientContext) {
        NeoForge.EVENT_BUS.unregister(clientContext);
        MisakaNetworkClient.NETWORK_MANAGER.unregister(clientContext);
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

    public record SkillInfo(Skill skill, List<SkillInfo> dependencies, Identifier texture, float x, float y) {
    }
}
