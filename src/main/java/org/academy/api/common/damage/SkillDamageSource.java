package org.academy.api.common.damage;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import org.academy.api.common.ability.Skill;
import org.jetbrains.annotations.Nullable;

public class SkillDamageSource extends DamageSource {

    private final Skill skill;

    protected SkillDamageSource(Holder<DamageType> type, @Nullable Entity directEntity, @Nullable Entity causingEntity, Skill skill) {
        super(type, directEntity, causingEntity);
        this.skill = skill;
    }

    public Skill getSkill() {
        return skill;
    }

    /**
     * 创建一个玩家攻击的技能伤害源(默认)
     *
     * @param player 受影响的玩家
     * @param skill  触发的技能
     * @return 技能伤害源
     */
    public static SkillDamageSource of(ServerPlayer player, Skill skill) {
        var original = player.damageSources().playerAttack(player);
        return new SkillDamageSource(original.typeHolder(), original.getDirectEntity(), original.getEntity(), skill);
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
        return new SkillDamageSource(original.typeHolder(), original.getDirectEntity(), original.getEntity(), skill);
    }
}