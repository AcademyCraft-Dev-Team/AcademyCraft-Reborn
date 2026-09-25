package org.academy.internal.common.world.damagesource;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import org.academy.api.common.damage.SkillDamageSource;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public final class DamageCompletionTracker {
    private final Map<DamageContainer, Float> before = new WeakHashMap<>();
    private final Set<DamageContainer> completed = Collections.newSetFromMap(new WeakHashMap<>());

    public static DamageCompletionTracker of(MinecraftServer server) {
        return server.getAcademyCraftServer().getDamageCompletionTracker();
    }

    public static DamageCompletionTracker of(LivingEntity entity) {
        return of(entity.level().getServer());
    }

    public void track(LivingEntity target, DamageContainer hit) {
        before.put(hit, target.getHealth());
    }

    public float complete(LivingEntity target, DamageContainer hit) {
        if (!completed.add(hit)) return 0.0f;
        var beforeHealth = before.remove(hit);
        var healthDamage = beforeHealth == null
                ? hit.getNewDamage()
                : Math.max(0.0f, beforeHealth - target.getHealth());
        if (!(healthDamage > 0.0f)
                || !(hit.getSource() instanceof SkillDamageSource source)
                || source instanceof ReflectedSkillDamageSource reflected
                && !reflected.shouldTriggerSkillCallbacks()) {
            return 0.0f;
        }
        return healthDamage;
    }

    public void clear() {
        before.clear();
        completed.clear();
    }
}
