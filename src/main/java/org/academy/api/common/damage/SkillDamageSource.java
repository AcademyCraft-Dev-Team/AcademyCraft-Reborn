package org.academy.api.common.damage;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import org.academy.api.common.ability.Skill;
import org.academy.internal.common.world.damagesource.SkillDamageTypeResolver;
import org.jetbrains.annotations.Nullable;

public class SkillDamageSource extends DamageSource {

    private final Skill skill;
    private final int electricalChargePoints;
    private final boolean canMarkHostility;

    protected SkillDamageSource(Holder<DamageType> type, @Nullable Entity directEntity, @Nullable Entity causingEntity, Skill skill) {
        this(type, directEntity, causingEntity, skill, -1);
    }

    protected SkillDamageSource(Holder<DamageType> type, @Nullable Entity directEntity,
                                @Nullable Entity causingEntity, Skill skill, int electricalChargePoints) {
        this(type, directEntity, causingEntity, skill, electricalChargePoints, true);
    }

    private SkillDamageSource(Holder<DamageType> type, @Nullable Entity directEntity,
                              @Nullable Entity causingEntity, Skill skill, int electricalChargePoints,
                              boolean canMarkHostility) {
        super(type, directEntity, causingEntity);
        this.skill = skill;
        this.electricalChargePoints = electricalChargePoints;
        this.canMarkHostility = canMarkHostility;
    }

    /**
     * 创建一个玩家攻击的技能伤害源(默认)
     *
     * @param player 受影响的玩家
     * @param skill  触发的技能
     * @return 技能伤害源
     */
    public static SkillDamageSource of(ServerPlayer player, Skill skill) {
        var categoryType = SkillDamageTypeResolver.resolve(skill);
        if (categoryType != null) return of(player, skill, categoryType);
        var original = player.damageSources().playerAttack(player);
        return new SkillDamageSource(original.typeHolder(), original.getDirectEntity(), original.getEntity(), skill);
    }

    /**
     * Creates skill damage attributed to the player while retaining the actual
     * moving entity as the direct source. This lets defenses redirect that entity.
     */
    public static SkillDamageSource ofDirect(
            ServerPlayer player,
            Skill skill,
            Entity directEntity
    ) {
        if (directEntity == null) throw new IllegalArgumentException("directEntity cannot be null");
        var categoryType = SkillDamageTypeResolver.resolve(skill);
        if (categoryType != null) {
            var registry = player.level().registryAccess()
                    .lookupOrThrow(Registries.DAMAGE_TYPE);
            return new SkillDamageSource(
                    registry.getOrThrow(categoryType), directEntity, player, skill);
        }
        var original = player.damageSources().playerAttack(player);
        return new SkillDamageSource(
                original.typeHolder(), directEntity, player, skill);
    }

    /**
     * 创建一个指定伤害类型的技能伤害源
     *
     * @param player  受影响的玩家
     * @param skill   触发的技能
     * @param typeKey 伤害类型的资源键
     * @return 技能伤害源
     */
    public static SkillDamageSource of(ServerPlayer player, Skill skill, ResourceKey<DamageType> typeKey) {
        var registry = player.level().registryAccess()
                .lookupOrThrow(Registries.DAMAGE_TYPE);
        Holder<DamageType> typeHolder = registry.getOrThrow(typeKey);
        return new SkillDamageSource(typeHolder, player, player, skill);
    }

    /**
     * 从原始伤害源创建技能伤害源
     *
     * @param original 原始伤害源
     * @param skill    触发的技能
     * @return 技能伤害源
     */
    public static SkillDamageSource from(DamageSource original, Skill skill) {
        var categoryType = SkillDamageTypeResolver.resolve(skill);
        var copy = categoryType != null && original.getEntity() instanceof ServerPlayer player
                ? of(player, skill, categoryType)
                : new SkillDamageSource(original.typeHolder(), original.getDirectEntity(), original.getEntity(), skill);
        return original instanceof SkillDamageSource source && !source.canMarkHostility()
                ? copy.withoutHostilityMark() : copy;
    }

    /** Returns a copy with an explicit charge award for a primary, echo or secondary hit. */
    public SkillDamageSource withElectricalChargePoints(int points) {
        if (points < 0) throw new IllegalArgumentException("Charge points must be non-negative");
        return new SkillDamageSource(typeHolder(), getDirectEntity(), getEntity(), skill, points, canMarkHostility());
    }

    /** Returns a source for an automatic pulse or retaliation that must not refresh hostility. */
    public SkillDamageSource withoutHostilityMark() {
        return new SkillDamageSource(typeHolder(), getDirectEntity(), getEntity(), skill, electricalChargePoints, false);
    }

    public boolean canMarkHostility() {
        return canMarkHostility;
    }

    /** -1 selects the category's skill default. */
    public int electricalChargePoints() {
        return electricalChargePoints;
    }

    public Skill getSkill() {
        return skill;
    }
}
