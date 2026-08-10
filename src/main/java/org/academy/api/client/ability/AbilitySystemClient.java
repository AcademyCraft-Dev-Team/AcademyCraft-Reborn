package org.academy.api.client.ability;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.neoforged.neoforge.common.NeoForge;
import org.academy.AcademyCraft;
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
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.skilldata.SkillData;
import org.jspecify.annotations.Nullable;
import org.misaka.MisakaNetworkClient;
import org.misaka.api.common.network.annotation.SubscribePacket;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import net.minecraft.util.Mth;

public final class AbilitySystemClient {
    public static final Set<Skill> LEARNED_SKILLS = new CopyOnWriteArraySet<>();
    public static final String CONFIG_KEY_ABILITY_SYSTEM = "ability_system";
    public static final String KEY_NAME_ACTIVATE_HUD = "activate_ability_hud";
    public static final InputSystem.KeyCombination ACTIVATE_HUD_KEY;
    private static final Map<String, SkillData> SKILL_DATA = new ConcurrentHashMap<>();
    private static final Map<String, SyncAbilityDataPacket.SkillOccupationSnapshot> SKILL_OCCUPATIONS =
            new ConcurrentHashMap<>();
    private static final Map<String, Long> LAST_SKILL_DENIAL_MESSAGES = new ConcurrentHashMap<>();
    private static final Map<String, Long> PENDING_TOGGLE_REQUESTS = new ConcurrentHashMap<>();
    private static final Map<AbilityCategory, List<SkillInfo>> SKILL_INFOS = new HashMap<>();
    private static final List<SkillInfo> COMMON_SKILL_INFOS = new ArrayList<>();
    private static final long SKILL_DENIAL_MESSAGE_INTERVAL_MS = 1_500L;
    private static final long TOGGLE_REQUEST_TIMEOUT_MS = 750L;
    @Nullable
    public static AbilityCategory category;
    private static boolean activeHUD = false;
    private static AbilityData cpData = new AbilityData();
    private static float calculationIntensity = 1.0f;
    private static volatile DevState devState = DevState.IDLE;
    private static volatile float devProgress = 0f;
    private static volatile String devMessage = "";
    private static volatile String devTargetId = "";
    private static volatile boolean devRequestPending = false;

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
        if (skillInfo.skill().getScope() != SkillScope.CATEGORY
                || skillInfo.skill().getCategory() != category) {
            throw new IllegalArgumentException("Category skill info does not match its skill scope or owner");
        }
        if (!SKILL_INFOS.containsKey(category)) {
            SKILL_INFOS.put(category, new ArrayList<>());
        }
        SKILL_INFOS.get(category).add(skillInfo);
        return skillInfo;
    }

    public static SkillInfo addCommonSkillInfo(SkillInfo skillInfo) {
        if (skillInfo.skill().getScope() != SkillScope.COMMON) {
            throw new IllegalArgumentException("Common skill info requires a common-scoped skill");
        }
        COMMON_SKILL_INFOS.add(skillInfo);
        return skillInfo;
    }

    public static Map<AbilityCategory, List<SkillInfo>> getSkillInfos() {
        return SKILL_INFOS;
    }

    public static List<SkillInfo> getSkillInfosForCategory(@Nullable AbilityCategory category) {
        if (category == null) return List.of();

        var categoryInfos = SKILL_INFOS.getOrDefault(category, List.of());
        if (!category.supportsCommonSkills()) return List.copyOf(categoryInfos);

        var result = new ArrayList<SkillInfo>(categoryInfos.size() + COMMON_SKILL_INFOS.size());
        result.addAll(categoryInfos);
        result.addAll(COMMON_SKILL_INFOS);
        return List.copyOf(result);
    }

    public static List<SkillInfo> getCommonSkillInfos() {
        return List.copyOf(COMMON_SKILL_INFOS);
    }

    public static List<SkillInfo> getCategorySkillInfos(@Nullable AbilityCategory category) {
        if (category == null) return List.of();
        return List.copyOf(SKILL_INFOS.getOrDefault(category, List.of()));
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

        ensureCompleteSkillInfos();
    }

    private static void ensureCompleteSkillInfos() {
        for (var category : Registries.ABILITY_CATEGORIES) {
            var infos = SKILL_INFOS.computeIfAbsent(category, ignored -> new ArrayList<>());
            var bySkill = new IdentityHashMap<Skill, SkillInfo>();
            infos.forEach(info -> bySkill.put(info.skill(), info));

            var fallbackSkills = Registries.SKILLS.stream()
                    .filter(skill -> skill.getScope() == SkillScope.CATEGORY)
                    .filter(skill -> skill.getCategory() == category)
                    .filter(skill -> !bySkill.containsKey(skill))
                    .sorted(Comparator
                            .comparingInt((Skill skill) -> skill.getRecommendedLevel().getLevelCode())
                            .thenComparing(Skill::getKeyString))
                    .toList();

            for (var skill : fallbackSkills) {
                var position = findFallbackPosition(infos, skill);
                var info = new SkillInfo(
                        skill,
                        List.of(),
                        resolveFallbackTexture(skill),
                        position[0],
                        position[1]
                );
                infos.add(info);
                bySkill.put(skill, info);
            }

            COMMON_SKILL_INFOS.forEach(info -> bySkill.put(info.skill(), info));
            for (var index = 0; index < infos.size(); index++) {
                var info = infos.get(index);
                var dependencies = info.skill().getDependencies().stream()
                        .map(bySkill::get)
                        .filter(Objects::nonNull)
                        .toList();
                infos.set(index, new SkillInfo(
                        info.skill(), dependencies, info.texture(), info.x(), info.y()
                ));
            }
        }
    }

    private static float[] findFallbackPosition(List<SkillInfo> infos, Skill skill) {
        var level = Math.max(1, skill.getRecommendedLevel().getLevelCode());
        var x = 24f + (level - 1) * 48f;
        var y = 18f;
        while (isFallbackPositionOccupied(infos, x, y)) {
            y += 28f;
        }
        return new float[]{x, y};
    }

    private static boolean isFallbackPositionOccupied(List<SkillInfo> infos, float x, float y) {
        for (var info : infos) {
            if (Math.abs(info.x() - x) < 20f && Math.abs(info.y() - y) < 20f) return true;
        }
        return false;
    }

    private static Identifier resolveFallbackTexture(Skill skill) {
        var skillKey = skill.getKey();
        var categoryPath = skill.getCategory().getKey().getPath();
        var inferred = Identifier.fromNamespaceAndPath(
                skillKey.getNamespace(),
                "textures/ability/" + categoryPath + "/skill/" + skillKey.getPath() + "/icon.png"
        );
        return Minecraft.getInstance().getResourceManager().getResource(inferred).isPresent()
                ? inferred
                : skill.getCategory().getDeveloperIcon();
    }

    @SubscribePacket
    public static void handleSync(SyncAbilityCategoryPacket packet) {
        category = packet.getAbilityCategory();
        PENDING_TOGGLE_REQUESTS.clear();
    }

    @SubscribePacket
    public static void handleSync(SyncAbilityDataPacket packet) {
        cpData = packet.getAbilityData();
        calculationIntensity = packet.getCalculationIntensity();
        SKILL_OCCUPATIONS.clear();
        packet.getSkillOccupations().forEach(snapshot ->
                SKILL_OCCUPATIONS.put(snapshot.skillId(), snapshot));
    }

    @SubscribePacket
    public static void handleDevSync(DevSyncPacket packet) {
        devState = packet.getState();
        devProgress = packet.getProgress();
        devMessage = packet.getMessage();
        devTargetId = packet.getTargetId();
        devRequestPending = false;
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

    public static String getDevTargetId() {
        return devTargetId;
    }

    public static boolean isDevRequestPending() {
        return devRequestPending;
    }

    public static boolean isDevelopmentActive() {
        return devRequestPending || devState == DevState.DEVELOPING;
    }

    public static void beginDevelopmentRequest(String targetId) {
        devState = DevState.IDLE;
        devProgress = 0.0f;
        devMessage = "";
        devTargetId = targetId == null ? "" : targetId;
        devRequestPending = true;
    }

    public static void rejectDevelopmentRequest(String targetId, String message) {
        if (!devRequestPending || !Objects.equals(devTargetId, targetId)) return;
        devRequestPending = false;
        devState = DevState.FAILED;
        devMessage = message == null ? "Failed" : message;
    }

    public static void resetDevState() {
        devState = DevState.IDLE;
        devProgress = 0;
        devMessage = "";
        devTargetId = "";
        devRequestPending = false;
    }

    public static boolean isSkillLearned(String skillId) {
        if (skillId == null || skillId.isBlank()) return false;
        return LEARNED_SKILLS.stream().anyMatch(skill -> skillId.equals(skill.getKeyString()));
    }

    @SubscribePacket
    public static void handleSync(SyncSkillDataPacket packet) {
        var newData = packet.getSkillDataMap();

        SKILL_DATA.clear();
        SKILL_DATA.putAll(newData);

        LEARNED_SKILLS.clear();
        newData.keySet().forEach(skillId -> Registries.SKILLS.get(Identifier.parse(skillId))
                .ifPresent(holder -> LEARNED_SKILLS.add(holder.value())));
        PENDING_TOGGLE_REQUESTS.clear();
    }

    public static boolean canUseSkill(Skill skill) {
        return canUseSkill(skill, true);
    }

    public static boolean canUseSkillSilently(Skill skill) {
        return canUseSkill(skill, false);
    }

    private static boolean canUseSkill(Skill skill, boolean showFailureMessage) {
        var status = getSkillUseStatus(skill);
        if (!status.allowed() && showFailureMessage && shouldNotifySkillUseDenied(skill, status)) {
            notifySkillUseDenied(skill, status);
        }
        return status.allowed();
    }

    private static boolean shouldNotifySkillUseDenied(Skill skill, SkillUseStatus status) {
        if (skill == null || !canToggleSkill(skill)) return false;
        return status.failure() != SkillUseFailure.UNAVAILABLE
                && status.failure() != SkillUseFailure.DISABLED;
    }

    public static SkillUseStatus getSkillUseStatus(Skill skill) {
        if (skill == null || !LearningHelper.isSkillAvailableForCategory(category, skill)
                || !LEARNED_SKILLS.contains(skill)) {
            return SkillUseStatus.denied(SkillUseFailure.UNAVAILABLE);
        }
        var skillData = getSkillData(skill).orElse(null);
        if (skillData == null || !skillData.isEnabled()) {
            return SkillUseStatus.denied(SkillUseFailure.DISABLED);
        }
        if (cpData.getStatus() == AbilityData.Status.OVERLOAD) {
            return SkillUseStatus.denied(SkillUseFailure.OVERLOAD);
        }

        var level = skill.getLevelForProficiency(skillData.getProficiency());
        var requiredCp = Math.max(0.0f, skill.getCpCost(level) * calculationIntensity);
        if (cpData.getAvailableCP() + 1.0e-4f < requiredCp) {
            return new SkillUseStatus(
                    false,
                    SkillUseFailure.INSUFFICIENT_CP,
                    requiredCp,
                    cpData.getAvailableCP(),
                    0,
                    getEffectiveMaxStacks(skill, level),
                    0
            );
        }

        var occupation = SKILL_OCCUPATIONS.get(skill.getKeyString());
        var stackCount = occupation == null ? 0 : occupation.stackCount();
        var remaining = occupation == null ? 0 : occupation.remainingIterationPoints();
        var maxStacks = getEffectiveMaxStacks(skill, level);
        if (maxStacks != Skill.NO_STACK_LIMIT && stackCount >= maxStacks) {
            return new SkillUseStatus(
                    false,
                    remaining > 0 ? SkillUseFailure.SERVER_COOLDOWN : SkillUseFailure.STACK_LIMIT,
                    requiredCp,
                    cpData.getAvailableCP(),
                    stackCount,
                    maxStacks,
                    remaining
            );
        }
        return new SkillUseStatus(
                true,
                SkillUseFailure.NONE,
                requiredCp,
                cpData.getAvailableCP(),
                stackCount,
                maxStacks,
                remaining
        );
    }

    private static void notifySkillUseDenied(Skill skill, SkillUseStatus status) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        var messageKey = skill.getKeyString() + '|' + status.failure();
        var now = Util.getMillis();
        var previous = LAST_SKILL_DENIAL_MESSAGES.put(messageKey, now);
        if (previous != null && now - previous < SKILL_DENIAL_MESSAGE_INTERVAL_MS) return;

        var skillName = Component.translatable(skill.getDescriptionId());
        var message = switch (status.failure()) {
            case INSUFFICIENT_CP -> Component.translatable(
                    "message.academy.skill_use.insufficient_cp",
                    skillName,
                    formatCp(status.requiredCp()),
                    formatCp(status.availableCp())
            );
            case STACK_LIMIT -> Component.translatable(
                    "message.academy.skill_use.stack_limit",
                    skillName,
                    status.stackCount(),
                    status.maxStacks()
            );
            case SERVER_COOLDOWN -> Component.translatable(
                    "message.academy.skill_use.server_cooldown",
                    skillName,
                    status.remainingIterationPoints(),
                    status.stackCount(),
                    status.maxStacks()
            );
            case OVERLOAD -> Component.translatable("message.academy.skill_use.overload", skillName);
            default -> Component.translatable("message.academy.skill_use.unavailable", skillName);
        };
        player.sendOverlayMessage(message.withStyle(ChatFormatting.RED));
    }

    private static String formatCp(float value) {
        return String.format(Locale.ROOT, "%.1f", Math.max(0.0f, value));
    }

    public static boolean canToggleSkill(Skill skill) {
        return skill != null
                && LearningHelper.isSkillAvailableForCategory(category, skill)
                && LEARNED_SKILLS.contains(skill);
    }

    public static boolean beginToggleRequest(Skill skill) {
        if (skill == null) return false;
        var shuttingDown = getSkillData(skill).map(SkillData::isEnabled).orElse(false);
        if (!shuttingDown && !canToggleSkill(skill)) return false;
        var now = Util.getMillis();
        var skillId = skill.getKeyString();
        var pendingSince = PENDING_TOGGLE_REQUESTS.get(skillId);
        if (pendingSince != null && now - pendingSince < TOGGLE_REQUEST_TIMEOUT_MS) return false;
        PENDING_TOGGLE_REQUESTS.put(skillId, now);
        return true;
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
        var level = getLevel().getLevelCode();
        if (level >= 5) return false;
        if (getCategory() == AbilityCategories.LEVEL0.get()) return level == 0;
        return getAbilityExp() >= LearningHelper.getAbilityExpRequirement(getCategory(), level);
    }

    public static float getAbilityProgress() {
        return LearningHelper.getAbilityProgress(getCategory(), getLevel().getLevelCode(), getAbilityExp());
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
        return getSkillProficiency(skill);
    }

    public static void setSkillExp(Skill skill, float exp) {
        setSkillProficiency(skill, exp);
    }

    public static float getSkillProficiency(Skill skill) {
        var data = SKILL_DATA.get(skill.getKeyString());
        return data == null ? 0.0f : data.getProficiency();
    }

    public static int getSkillProficiencyMilestone(Skill skill) {
        return SkillData.getReachedProficiencyThresholds(getSkillProficiency(skill));
    }

    public static int getSkillLevel(Skill skill) {
        return skill.getLevelForProficiency(getSkillProficiency(skill));
    }

    public static int getEffectiveMaxStacks(Skill skill, int skillLevel) {
        var base = skill.getMaxStacks(skillLevel);
        if (base == Skill.NO_STACK_LIMIT) return Skill.NO_STACK_LIMIT;
        var category = getCategory();
        if (category == null || !category.supportsCommonSkills()) return base;
        var stackData = SKILL_DATA.get(
                AcademyCraft.academy(SkillNames.LEVEL0_PASSIVE_LV2).toString()
        );
        var bonus = stackData == null
                ? 0
                : SkillData.getProficiencyTier(stackData.getProficiency());
        return Math.max(0, base + bonus);
    }

    public static float getSkillProficiencyProgress(Skill skill) {
        return Mth.clamp(getSkillProficiency(skill) / SkillData.MAX_PROFICIENCY, 0.0f, 1.0f);
    }

    public static void setSkillProficiency(Skill skill, float proficiency) {
        var skillId = Objects.requireNonNull(Registries.SKILLS.getKey(skill)).toString();
        var data = SKILL_DATA.get(skillId);
        if (data != null) {
            data.setProficiency(proficiency);
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

    public static void addSkillProficiency(Skill skill, float amount) {
        setSkillProficiency(skill, getSkillProficiency(skill) + amount);
    }

    public enum SkillUseFailure {
        NONE,
        UNAVAILABLE,
        DISABLED,
        OVERLOAD,
        INSUFFICIENT_CP,
        STACK_LIMIT,
        SERVER_COOLDOWN
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

    public record SkillUseStatus(
            boolean allowed,
            SkillUseFailure failure,
            float requiredCp,
            float availableCp,
            int stackCount,
            int maxStacks,
            int remainingIterationPoints
    ) {
        private static SkillUseStatus denied(SkillUseFailure failure) {
            return new SkillUseStatus(false, failure, 0.0f, 0.0f, 0, 0, 0);
        }
    }
}
