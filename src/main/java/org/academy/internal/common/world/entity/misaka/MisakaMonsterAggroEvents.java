package org.academy.internal.common.world.entity.misaka;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import org.academy.AcademyCraft;

@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class MisakaMonsterAggroEvents {
    private MisakaMonsterAggroEvents() {
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof Mob mob) || !(mob instanceof Enemy)) {
            return;
        }
        mob.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(
                mob,
                MisakaSisterEntity.class,
                10,
                true,
                false,
                (target, level) -> target instanceof MisakaSisterEntity sister && !sister.isStarving()
        ));
    }

    @SubscribeEvent
    public static void onLivingChangeTarget(LivingChangeTargetEvent event) {
        if (event.getNewAboutToBeSetTarget() instanceof MisakaSisterEntity sister && sister.isStarving()) {
            event.setNewAboutToBeSetTarget(null);
        }
    }
}
