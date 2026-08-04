package org.academy.internal.common.ability.meltdowner;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.internal.common.ability.meltdowner.skills.RadiationIntensify;

public final class MeltdownerBeamActions {
    private MeltdownerBeamActions() {
    }

    public static void damageAlong(
            ServerLevel level,
            ServerPlayer player,
            Skill skill,
            Vec3 start,
            Vec3 end,
            float radius,
            float baseDamage,
            float maxHealthRatio,
            float playerMultiplier,
            boolean radiationEnabled
    ) {
        var pathBounds = new AABB(start, end).inflate(radius);
        var source = SkillDamageSource.of(player, skill);
        var now = level.getGameTime();
        var targets = level.getEntitiesOfClass(
                LivingEntity.class,
                pathBounds,
                target -> target != player
                        && target.isAlive()
                        && !player.isAlliedTo(target)
        );
        for (var target : targets) {
            var hitBounds = target.getBoundingBox().inflate(radius);
            if (!hitBounds.contains(start) && hitBounds.clip(start, end).isEmpty()) continue;
            var marked = radiationEnabled && RadiationIntensify.isMarked(target, now);
            var damage = MeltdownerBeamDamage.calculate(
                    baseDamage,
                    maxHealthRatio,
                    target.getMaxHealth(),
                    playerMultiplier,
                    marked
            );
            var hurt = target.hurtServer(level, source, damage);
            if (hurt && radiationEnabled) RadiationIntensify.mark(target, now);
        }
    }
}
