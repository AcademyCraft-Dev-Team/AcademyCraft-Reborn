package org.academy.api.common.ability;

import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.locale.Language;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.academy.api.common.ability.event.AbilitySystemFinalizedEvent;
import org.academy.api.common.registries.Registries;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.skilldata.CommonSkillData;
import org.academy.internal.common.skilldata.SkillData;
import org.academy.internal.server.ability.SkillDataManager;
import org.academy.internal.server.world.level.storage.SkillDataSerializer;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public abstract class Skill {
    public static final int NO_STACK_LIMIT = -1;

    public static final Codec<Skill> CODEC =
            Codec.INT.xmap(Registries.SKILLS::byIdOrThrow, Registries.SKILLS::getId);
    public static final StreamCodec<ByteBuf, Skill> STREAM_CODEC = ByteBufCodecs.idMapper(Registries.SKILLS);
    public static final StreamCodec<ByteBuf, Set<Skill>> STREAM_CODEC_SET = STREAM_CODEC.apply(
            codec -> ByteBufCodecs.collection(HashSet::new, codec)
    );

    @Nullable
    private String cachedKeyString;

    private final AbilityLevel recommendedLevel;
    private final int energyCostToLearn;
    private final AbilityCategory category;
    private Set<Skill> dependencies = new HashSet<>();
    private final DataFactory dataFactory;
    private final int maxSkillLevel;
    private final int iterationTicks;// 技能迭代时间间隔，单位为tick
    private final int maxStacks;// 技能堆栈数量
    private final float maintenanceCost;// 持续性技能所占用的cp
    private final boolean isPassive;
    private final float cpCost;

    protected Skill(Builder builder) {
        recommendedLevel = builder.recommendedLevel;
        energyCostToLearn = builder.energyCostToLearn;
        maxSkillLevel = builder.maxSkillLevel;
        category = builder.category;
        category.addSkill(this);
        iterationTicks = builder.iterationTicks;
        maxStacks = builder.maxStacks;
        maintenanceCost = builder.maintenanceCost;
        isPassive = builder.isPassive;
        cpCost = builder.cpCost;

        dataFactory = builder.dataFactory;
        var dataClass = builder.dataClass;
        SkillDataSerializer.registerType(builder.dataTypeId, dataClass);

        if (builder.dependencyHolders.isEmpty()) {
            dependencies = ImmutableSet.of();
        } else {
            var dependencyResolver = new DependencyResolver(this, builder.dependencyHolders);
            NeoForge.EVENT_BUS.register(dependencyResolver);
        }
    }

    public record SkillContext(int level, float availableCP, AbilitySystemServer system) {
    }

    /**
     * 技能击中目标时触发，默认行为为增加经验。
     * 伤害类型需要设置为 SkillDamageSource 才能自动触发此事件
     * 重写时建议调用super.onHurt()
     */
    public void onHurt(ServerPlayer attacker, LivingEntity target, float amount) {
        AbilitySystemServer.getSystem(attacker)
                .addPlayerSkillExp(attacker.getUUID(), this, SkillDataManager.ExpEvent.ACT_EFFECTIVE);
    }

    /**
     * 技能击杀目标时触发，默认行为为增加经验。
     * 伤害类型需要设置为 SkillDamageSource 才能自动触发此事件
     * 重写时建议调用super.onKill()
     */
    public void onKill(ServerPlayer killer, LivingEntity target) {
        AbilitySystemServer.getSystem(killer)
                .addPlayerSkillExp(killer.getUUID(), this, SkillDataManager.ExpEvent.KILL_ENTITY);
    }

    /**
     * 技能逻辑执行，CostCalculator 用于动态计算技能消耗的CP
     */
    protected final boolean executeActive(ServerPlayer player, CostCalculator calculator, SkillAction action) {
        if (!isEnabled(player)) return false;
        return AbilitySystemServer.getSystem(player)
                .castCpIfPossible(player, this, calculator, action);
    }

    protected final boolean executeActive(ServerPlayer player, SkillAction action) {
        return executeActive(player, ctx -> cpCost, action);
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
        var level = system.getPlayerSkillLevel(uuid, getKeyString());
        var cost = getMaintenanceCost(level);

        var goingToEnable = !isEnabled(player);

        if (cost <= 0) {
            system.toggleSkill(uuid, getKeyString());
            return;
        }

        if (goingToEnable) {
            if (system.tryPermanentOccupation(uuid, cost, this)) {
                system.toggleSkill(uuid, getKeyString());
            }
        } else {
            system.toggleSkill(uuid, getKeyString());
            system.releaseMaintenanceOccupation(uuid, getKeyString());
        }
    }

    public final boolean isEnabled(ServerPlayer player) {
        return getRuntimeData(player).map(SkillData::isEnabled).orElse(false);
    }

    // 考虑到后续可能需要传入上下文，因此传入ServerPlayer
    public SkillData createData(ServerPlayer player) {
        return dataFactory.create(player);
    }

    public static <T extends Context> Map<Player, T> createContextMap() {
        return new WeakHashMap<>();
    }

    public final Set<Skill> getDependencies() {
        return dependencies;
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

    public int getEnergyCostToLearn() {
        return energyCostToLearn;
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

    public final int getLevel(ServerPlayer player) {
        return AbilitySystemServer.getSystem(player).getPlayerSkillLevel(player.getUUID(), getKeyString());
    }

    public float getCpCost(int skillLevel) {
        return cpCost;
    }

    public float getMaintenanceCost(int skillLevel) {
        return maintenanceCost;
    }

    public boolean isPassive(int skillLevel) {
        return isPassive;
    }

    public int getIterationTicks(int skillLevel) {
        return iterationTicks;
    }

    public int getMaxStacks(int skillLevel) {
        return maxStacks;
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
        return Language.getInstance().getOrDefault(getDescriptionId());
    }

    public String getKeyBindingKeyName(String name) {
        var key = getKey();
        var skillName = Util.makeDescriptionId("key", key);
        return skillName + "." + name;
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
        private AbilityLevel recommendedLevel = AbilityLevel.LEVEL0;
        private int energyCostToLearn = 5000;
        private final Set<DeferredHolder<Skill, ? extends Skill>> dependencyHolders = new HashSet<>();
        private int maxSkillLevel = 3;
        private int iterationTicks = 20;
        private int maxStacks = 2;
        private float maintenanceCost = 0f;
        private boolean isPassive = false;
        private float cpCost = 0;

        private DataFactory dataFactory = player -> new CommonSkillData();
        private Class<? extends SkillData> dataClass = CommonSkillData.class;
        private Identifier dataTypeId = CommonSkillData.ID;

        private Builder(AbilityCategory category) {
            this.category = category;
        }

        public Builder level(AbilityLevel level) {
            recommendedLevel = level;
            return this;
        }

        public Builder passive() {
            isPassive = true;
            return this;
        }

        public Builder cpCost(int cpCost) {
            this.cpCost = cpCost;
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

        @SafeVarargs
        public final Builder dependsOn(DeferredHolder<Skill, ? extends Skill>... dependencies) {
            Collections.addAll(dependencyHolders, dependencies);
            return this;
        }

        public static Builder of(AbilityCategory category) {
            return new Builder(category);
        }
    }

    @FunctionalInterface
    public interface DataFactory {
        SkillData create(ServerPlayer player);
    }

    @FunctionalInterface
    public interface CostCalculator {
        float calculate(SkillContext ctx);
    }

    @FunctionalInterface
    public interface SkillAction {
        void execute(SkillContext ctx, float actualCost);
    }
}