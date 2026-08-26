package org.academy.api.common.ability;

import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.academy.api.client.resources.R;
import org.academy.api.common.ability.event.*;
import org.academy.api.common.data.AbilityData;
import org.academy.api.common.registries.Registries;
import org.academy.api.common.util.L10nUtil;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.ProficiencyPolicy;
import org.academy.internal.common.ability.darkmatter.skills.lv5.DarkmatterSixWings;
import org.academy.internal.common.ability.electromaster.skills.lv3.CurrentSymbiosis;
import org.academy.internal.common.skilldata.CommonSkillData;
import org.academy.internal.common.skilldata.SkillData;
import org.academy.internal.server.world.level.storage.SkillDataSerializer;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public abstract class Skill {
    public static final int NO_STACK_LIMIT = -1;
    public static final int MAX_CP_ITERATION_TICKS = 20;
    /**
     * Keep disabled until the skill stack system is redesigned and verified.
     */
    public static final boolean STACK_LIMITS_ENABLED = false;
    public static final Codec<Skill> CODEC =
            Codec.INT.xmap(Registries.SKILLS::byIdOrThrow, Registries.SKILLS::getId);
    public static final StreamCodec<ByteBuf, Skill> STREAM_CODEC = ByteBufCodecs.idMapper(Registries.SKILLS);
    public static final StreamCodec<ByteBuf, Set<Skill>> STREAM_CODEC_SET = STREAM_CODEC.apply(
            codec -> ByteBufCodecs.collection(HashSet::new, codec)
    );
    private static final float TOGGLE_CP_EPSILON = 1.0E-4f;
    private final AbilityLevel recommendedLevel;
    private final int energyCostToLearn;
    private final AbilityCategory category;
    private final SkillScope scope;
    private final DataFactory dataFactory;
    private final int maxSkillLevel;
    /**
     * 技能迭代时间间隔，单位为tick
     */
    private final int iterationTicks;
    /**
     * 技能堆栈数量
     */
    private final int maxStacks;
    /**
     * 持续性技能所占用的cp
     */
    private final float maintenanceCost;
    private final boolean isPassive;
    private final boolean initiallyEnabled;
    private final float cpCost;
    private final SkillProficiencyProfile proficiencyProfile;
    private final Identifier icon;
    private final List<DevCondition> devConditions;
    @Nullable
    private String cachedKeyString;
    private Set<Skill> dependencies = new HashSet<>();

    protected Skill(Builder builder) {
        recommendedLevel = builder.recommendedLevel;
        energyCostToLearn = builder.energyCostToLearn;
        maxSkillLevel = builder.maxSkillLevel;
        category = builder.category;
        scope = builder.scope;
        if (scope == SkillScope.CATEGORY) {
            category.addSkill(this);
        }
        iterationTicks = builder.cpCost > 0.0f || builder.maintenanceCost > 0.0f
                ? Math.min(builder.iterationTicks, MAX_CP_ITERATION_TICKS)
                : builder.iterationTicks;
        maxStacks = builder.maxStacks;
        maintenanceCost = builder.maintenanceCost;
        isPassive = builder.isPassive;
        initiallyEnabled = builder.initiallyEnabled;
        cpCost = builder.cpCost;
        proficiencyProfile = builder.proficiencyProfile;

        dataFactory = builder.dataFactory;
        var dataClass = builder.dataClass;
        SkillDataSerializer.registerType(builder.dataTypeId, dataClass);
        icon = builder.icon;
        devConditions = List.copyOf(builder.devConditions);

        if (builder.dependencyHolders.isEmpty()) {
            dependencies = ImmutableSet.of();
        } else {
            var dependencyResolver = new DependencyResolver(this, builder.dependencyHolders);
            NeoForge.EVENT_BUS.register(dependencyResolver);
        }
    }

    public static <T extends Context> Map<Player, T> createContextMap() {
        return new WeakHashMap<>();
    }

    static boolean hasSufficientCpToEnable(float availableCp, float maintenanceCost,
                                           float calculationIntensity) {
        if (!Float.isFinite(availableCp) || !Float.isFinite(maintenanceCost)
                || !Float.isFinite(calculationIntensity)
                || maintenanceCost < 0.0f || calculationIntensity < 0.0f) return false;
        var actualCost = maintenanceCost * calculationIntensity;
        return Float.isFinite(actualCost) && availableCp - actualCost > TOGGLE_CP_EPSILON;
    }

    /**
     * 技能击中目标时触发，默认行为为增加经验。
     * 伤害类型需要设置为 SkillDamageSource 才能自动触发此事件
     * 重写时建议调用super.onHurt()
     */
    public void onHurt(ServerPlayer attacker, LivingEntity target, float amount) {
        reportActivity(attacker, true);
    }

    /**
     * 技能击杀目标时触发，默认行为为增加经验。
     * 伤害类型需要设置为 SkillDamageSource 才能自动触发此事件
     * 重写时建议调用super.onKill()
     */
    public void onKill(ServerPlayer killer, LivingEntity target) {
        AbilitySystemServer.getSystem(killer)
                .addPlayerSkillProficiency(killer.getUUID(), this, ProficiencyEvent.KILL_ENTITY);
    }

    /**
     * 技能逻辑执行，CostCalculator 用于动态计算技能消耗的CP
     */
    protected final boolean executeActive(ServerPlayer player, CostCalculator calculator, SkillAction action) {
        return executeActiveInternal(
                player, calculator, action, null, NO_STACK_LIMIT);
    }

    protected final boolean executeActive(ServerPlayer player, SkillAction action) {
        return executeActive(player, ctx -> cpCost, action);
    }

    protected final boolean executeActive(ServerPlayer player, String stackGroup, int stackLimit,
                                          SkillAction action) {
        return executeActiveInternal(
                player, ctx -> cpCost, action, stackGroup, stackLimit);
    }

    private boolean executeActiveInternal(
            ServerPlayer player,
            CostCalculator calculator,
            SkillAction action,
            String stackGroup,
            int stackLimit
    ) {
        if (!isEnabled(player)) return false;

        var preEvent = new SkillExecutionPreEvent(this, player, false);
        NeoForge.EVENT_BUS.post(preEvent);
        if (preEvent.isCanceled()) return false;

        CostCalculator eventCost = ctx -> {
            var baseCost = calculator.calculate(ctx);
            var proficiencyCost = resolvedProficiencyProfile().adjustCost(
                    SkillProficiencyProfile.CostKind.CAST, ctx.milestone(), baseCost);
            var categoryCost = DarkmatterSixWings.Server.adjustCategoryCost(
                    player, this, baseCost, proficiencyCost);
            var resolvedCost = CurrentSymbiosis.Server.adjustNextCastCost(
                    player, this, categoryCost);
            var costEvent = new SkillExecutionCostEvent(
                    new ActiveCostContext(this, player, ctx, baseCost), false, resolvedCost);
            NeoForge.EVENT_BUS.post(costEvent);
            return costEvent.isCanceled() ? Float.NaN : costEvent.cost();
        };

        SkillAction eventAction = (ctx, actualCost) -> {
            var execution = new ActiveExecutionContext(this, player, ctx, actualCost);
            NeoForge.EVENT_BUS.post(new SkillExecutionStartEvent(execution, false));
            var successful = false;
            Throwable failure = null;
            try {
                action.execute(ctx, actualCost);
                successful = true;
            } catch (Throwable throwable) {
                failure = throwable;
                throw throwable;
            } finally {
                NeoForge.EVENT_BUS.post(
                        new SkillExecutionFinishEvent(execution, false, successful, failure));
            }
        };

        var system = AbilitySystemServer.getSystem(player);
        if (stackGroup == null) {
            return system.castCpIfPossible(player, this, eventCost, eventAction);
        }
        return system.castCpIfPossible(
                player, this, eventCost, eventAction, stackGroup, stackLimit);
    }

    /**
     * Pays CP for one tick of an already-running skill without treating the payment as a new cast.
     */
    protected final boolean executeContinuous(
            ServerPlayer player,
            CostCalculator calculator,
            SkillAction action,
            boolean effective
    ) {
        if (!isEnabled(player)) return false;

        var preEvent = new SkillExecutionPreEvent(this, player, true);
        NeoForge.EVENT_BUS.post(preEvent);
        if (preEvent.isCanceled()) return false;

        return AbilitySystemServer.getSystem(player)
                .castContinuousCpIfPossible(player, this, ctx -> {
                    var baseCost = calculator.calculate(ctx);
                    var proficiencyCost = resolvedProficiencyProfile().adjustCost(
                            SkillProficiencyProfile.CostKind.CONTINUOUS, ctx.milestone(), baseCost);
                    var resolvedCost = DarkmatterSixWings.Server.adjustCategoryCost(
                            player, this, baseCost, proficiencyCost);
                    var costEvent = new SkillExecutionCostEvent(
                            new ActiveCostContext(this, player, ctx, baseCost), true, resolvedCost);
                    NeoForge.EVENT_BUS.post(costEvent);
                    return costEvent.isCanceled() ? Float.NaN : costEvent.cost();
                }, (ctx, actualCost) -> {
                    var execution = new ActiveExecutionContext(this, player, ctx, actualCost);
                    NeoForge.EVENT_BUS.post(new SkillExecutionStartEvent(execution, true));
                    var successful = false;
                    Throwable failure = null;
                    try {
                        action.execute(ctx, actualCost);
                        successful = true;
                    } catch (Throwable throwable) {
                        failure = throwable;
                        throw throwable;
                    } finally {
                        NeoForge.EVENT_BUS.post(
                                new SkillExecutionFinishEvent(execution, true, successful, failure));
                    }
                }, effective);
    }

    protected final boolean executeContinuous(ServerPlayer player, SkillAction action, boolean effective) {
        return executeContinuous(player, ctx -> cpCost, action, effective);
    }

    public final void reportActivity(ServerPlayer player, boolean effective) {
        AbilitySystemServer.getSystem(player).reportSkillActivity(
                player.getUUID(),
                this,
                effective ? SkillActivity.EFFECTIVE : SkillActivity.ACTIVE
        );
    }

    /**
     * Records one server-confirmed successful activation.
     */
    public final void reportTrigger(ServerPlayer player) {
        AbilitySystemServer.getSystem(player)
                .addPlayerSkillProficiency(player.getUUID(), this, ProficiencyEvent.TRIGGER);
    }

    @SuppressWarnings("unchecked")
    public final <T extends SkillData> Optional<T> getRuntimeData(ServerPlayer player) {
        var system = AbilitySystemServer.getSystem(player);
        var data = system.getPlayerData(player.getUUID()).getSkillDataMap().get(getKeyString());
        return Optional.ofNullable((T) data);
    }

    public final void toggle(ServerPlayer player) {
        var uuid = player.getUUID();
        var system = AbilitySystemServer.getSystem(player);
        var runtimeData = getRuntimeData(player);
        if (runtimeData.isEmpty()) return;
        var goingToEnable = !runtimeData.get().isEnabled();

        if (!goingToEnable) {
            system.toggleSkill(uuid, getKeyString());
            return;
        }

        if (system.getPlayerStatus(uuid) == AbilityData.Status.OVERLOAD) return;
        if (!LearningHelper.isSkillAvailableForCategory(system.getPlayerAbilityCategory(uuid), this)) return;
        var cost = getMaintenanceCost(player);
        if (cost <= 0) {
            system.toggleSkill(uuid, getKeyString());
            return;
        }
        if (!hasSufficientCpToEnable(
                system.getPlayerAvailableCP(uuid),
                cost,
                system.getPlayerCalculationIntensity(uuid)
        )) return;

        if (system.tryPermanentOccupation(uuid, cost, this)) {
            if (system.getPlayerStatus(uuid) == AbilityData.Status.OVERLOAD) {
                system.releaseMaintenanceOccupation(uuid, getKeyString());
                return;
            }
            system.toggleSkill(uuid, getKeyString());
            if (!runtimeData.get().isEnabled()) {
                system.releaseMaintenanceOccupation(uuid, getKeyString());
            }
        }
    }

    public final boolean isEnabled(ServerPlayer player) {
        var system = AbilitySystemServer.getSystem(player);
        return LearningHelper.isSkillAvailableForCategory(
                system.getPlayerAbilityCategory(player.getUUID()), this
        ) && getRuntimeData(player).map(SkillData::isEnabled).orElse(false);
    }

    public SkillData createData() {
        var data = dataFactory.create();
        data.setEnabled(initiallyEnabled);
        return data;
    }

    public final Set<Skill> getDependencies() {
        return dependencies;
    }

    public List<DevCondition> getDevConditions() {
        return devConditions;
    }

    public void init() {
    }

    public void initClient() {
    }

    /**
     * 要注意服务器不一定只初始化一次喵
     */
    public void initServer(MinecraftServerContext context) {
    }

    public AbilityLevel getRecommendedLevel() {
        return recommendedLevel;
    }

    public AbilityCategory getCategory() {
        return category;
    }

    public SkillScope getScope() {
        return scope;
    }

    public int getEnergyCostToLearn() {
        return energyCostToLearn;
    }

    public Identifier getIcon() {
        return icon;
    }

    public Identifier getKey() {
        var key = Registries.SKILLS.getKey(this);
        if (key == null) {
            throw new IllegalStateException("This skill has not been registered: " + this);
        }
        return key;
    }

    public int getMaxSkillLevel() {
        return maxSkillLevel;
    }

    public int getLevelForProficiency(float proficiency) {
        if (maxSkillLevel <= 0) return 0;
        var clamped = Mth.clamp(proficiency, SkillData.MIN_PROFICIENCY, SkillData.MAX_PROFICIENCY);
        var level = Mth.floor(clamped / SkillData.MAX_PROFICIENCY * (maxSkillLevel + 1));
        return Math.min(maxSkillLevel, level);
    }

    public final int getLevel(ServerPlayer player) {
        return AbilitySystemServer.getSystem(player).getPlayerSkillLevel(player.getUUID(), getKeyString());
    }

    public final float getProficiency(ServerPlayer player) {
        return getRuntimeData(player).map(SkillData::getProficiency).orElse(0.0f);
    }

    public final int getProficiencyMilestone(ServerPlayer player) {
        return SkillData.getReachedProficiencyThresholds(getProficiency(player));
    }

    public final int getEffectiveProficiencyMilestone(ServerPlayer player) {
        return ProficiencyPolicy.server(player).enabled() ? getProficiencyMilestone(player) : 0;
    }

    public final boolean hasProficiencyMilestone(ServerPlayer player, int milestone) {
        return milestone >= 1 && milestone <= 3
                && getEffectiveProficiencyMilestone(player) >= milestone;
    }

    public float getCpCost(int skillLevel) {
        return cpCost;
    }

    public final float getCpCost(ServerPlayer player) {
        return adjustProficiencyCost(
                player,
                SkillProficiencyProfile.CostKind.CAST,
                getCpCost(getLevel(player))
        );
    }

    public float getMaintenanceCost(int skillLevel) {
        return maintenanceCost;
    }

    public float getMaintenanceCost(ServerPlayer player) {
        return adjustProficiencyCost(
                player,
                SkillProficiencyProfile.CostKind.MAINTENANCE,
                getMaintenanceCost(getLevel(player))
        );
    }

    public final float adjustProficiencyCost(
            ServerPlayer player,
            SkillProficiencyProfile.CostKind kind,
            float amount
    ) {
        return resolvedProficiencyProfile().adjustCost(kind, getEffectiveProficiencyMilestone(player), amount);
    }

    public boolean isPassive(int skillLevel) {
        return isPassive;
    }

    public int getIterationTicks(int skillLevel) {
        return iterationTicks;
    }

    public final int getIterationTicks(ServerPlayer player) {
        return resolvedProficiencyProfile().resolveIterationTicks(
                getEffectiveProficiencyMilestone(player),
                getIterationTicks(getLevel(player))
        );
    }

    public final SkillProficiencyProfile getProficiencyProfile() {
        return resolvedProficiencyProfile();
    }

    private SkillProficiencyProfile resolvedProficiencyProfile() {
        return proficiencyProfile == SkillProficiencyProfile.NONE
                ? SkillProficiencyProfiles.forSkill(getKeyString())
                : proficiencyProfile;
    }

    public int getMaxStacks(int skillLevel) {
        return STACK_LIMITS_ENABLED ? maxStacks : NO_STACK_LIMIT;
    }

    public final String getKeyString() {
        if (cachedKeyString == null) {
            cachedKeyString = getKey().toString();
        }
        return cachedKeyString;
    }

    public String getDescriptionId() {
        return Util.makeDescriptionId("skill", getKey());
    }

    public String getTranslatedName() {
        return L10nUtil.get(getDescriptionId());
    }

    public String getTranslatedDescription() {
        return L10nUtil.get(getDescriptionId() + ".desc");
    }

    public String getKeyBindingKeyName(String name) {
        var key = getKey();
        var skillName = Util.makeDescriptionId("key", key);
        return skillName + "." + name;
    }

    @FunctionalInterface
    public interface DataFactory {
        SkillData create();
    }

    @FunctionalInterface
    public interface CostCalculator {
        float calculate(SkillContext ctx);
    }

    public record ActiveCostContext(
            Skill skill,
            ServerPlayer player,
            SkillContext skillContext,
            float baseCost
    ) {
    }

    public record ActiveExecutionContext(
            Skill skill,
            ServerPlayer player,
            SkillContext skillContext,
            float actualCost
    ) {
    }

    @FunctionalInterface
    public interface SkillAction {
        void execute(SkillContext ctx, float actualCost);
    }

    public record SkillContext(
            int level,
            float proficiency,
            int milestone,
            float availableCP,
            AbilitySystemServer system
    ) {
    }

    private record DependencyResolver(Skill target, Set<DeferredHolder<Skill, ? extends Skill>> holders) {
        private DependencyResolver(Skill target, Set<DeferredHolder<Skill, ? extends Skill>> holders) {
            this.target = target;
            this.holders = Set.copyOf(holders);
        }

        @SubscribeEvent
        public void onFinalize(AbilitySystemFinalizedEvent event) {
            target.dependencies = holders.stream()
                    .map(DeferredHolder::get)
                    .collect(ImmutableSet.toImmutableSet());
            NeoForge.EVENT_BUS.unregister(this);
        }
    }

    public static final class Builder {
        private final AbilityCategory category;
        private final Set<DeferredHolder<Skill, ? extends Skill>> dependencyHolders = new HashSet<>();
        private final List<DevCondition> devConditions = new ArrayList<>();
        private AbilityLevel recommendedLevel = AbilityLevel.LEVEL0;
        private int energyCostToLearn = 5000;
        private int maxSkillLevel = 3;
        // CP iteration points; zero lets the server derive half of the CP cost.
        private int iterationTicks = 0;
        private int maxStacks = 2;
        private float maintenanceCost = 0f;
        private boolean isPassive = false;
        private boolean initiallyEnabled = true;
        private float cpCost = 0;
        private SkillProficiencyProfile proficiencyProfile = SkillProficiencyProfile.NONE;
        private SkillScope scope = SkillScope.CATEGORY;

        private DataFactory dataFactory = CommonSkillData::new;
        private Class<? extends SkillData> dataClass = CommonSkillData.class;
        private Identifier dataTypeId = CommonSkillData.ID;
        private Identifier icon = R.textures.gui.icon.close;

        private Builder(AbilityCategory category) {
            this.category = category;
        }

        public static Builder of(AbilityCategory category) {
            return new Builder(category);
        }

        public Builder level(AbilityLevel level) {
            recommendedLevel = level;
            return this;
        }

        public Builder passive() {
            isPassive = true;
            return this;
        }

        public Builder initiallyDisabled() {
            initiallyEnabled = false;
            return this;
        }

        public Builder common() {
            scope = SkillScope.COMMON;
            return this;
        }

        public Builder cpCost(int cpCost) {
            this.cpCost = cpCost;
            return this;
        }

        public Builder proficiencyProfile(SkillProficiencyProfile proficiencyProfile) {
            this.proficiencyProfile = Objects.requireNonNull(proficiencyProfile);
            return this;
        }

        public Builder energyCost(int cost) {
            energyCostToLearn = cost;
            return this;
        }

        public Builder maxSkillLevel(int maxSkillLevel) {
            this.maxSkillLevel = maxSkillLevel;
            return this;
        }

        /**
         * 技能迭代tick
         */
        public Builder iterationTicks(int iterationTicks) {
            this.iterationTicks = iterationTicks;
            return this;
        }

        /**
         * 技能最大叠加层数，可传入Skill.NO_STACK_LIMIT 表示不限制
         */
        public Builder maxStacks(int maxStacks) {
            this.maxStacks = maxStacks;
            return this;
        }

        /**
         * 被动类技能开启时的持续占用CP值
         */
        public Builder maintenanceCost(float maintenanceCost) {
            this.maintenanceCost = maintenanceCost;
            return this;
        }

        public <T extends SkillData> Builder withCustomData(
                Identifier typeId,
                Class<T> clazz,
                DataFactory factory
        ) {
            dataTypeId = typeId;
            dataClass = clazz;
            dataFactory = factory;
            return this;
        }

        public void setIcon(Identifier icon) {
            this.icon = icon;
        }

        @SafeVarargs
        public final Builder dependsOn(DeferredHolder<Skill, ? extends Skill>... dependencies) {
            Collections.addAll(dependencyHolders, dependencies);
            return this;
        }

        public Builder devCondition(DevCondition condition) {
            devConditions.add(condition);
            return this;
        }
    }
}
