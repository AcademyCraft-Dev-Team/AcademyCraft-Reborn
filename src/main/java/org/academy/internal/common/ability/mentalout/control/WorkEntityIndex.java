package org.academy.internal.common.ability.mentalout.control;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.academy.AcademyCraft;
import org.academy.api.server.ability.SectionEntityIndex;
import java.util.Map;
import java.util.WeakHashMap;

/** Event-maintained work candidates; no recurring full-world entity traversal. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class WorkEntityIndex {
    private static final Map<ServerLevel, SectionEntityIndex<Entity>> LEVELS = new WeakHashMap<>();
    private WorkEntityIndex() {}
    static SectionEntityIndex<Entity> get(ServerLevel level) {
        return LEVELS.computeIfAbsent(level, ignored -> new SectionEntityIndex<>());
    }
    private static void update(Entity entity) {
        if (entity.level() instanceof ServerLevel level && !entity.isRemoved()
                && (entity instanceof Animal || entity instanceof ItemEntity)) {
            try (var ignored = org.academy.api.common.entitycontrol.WorkScheduling.budget(level).measure()) {
                get(level).update(entity.getId(), entity.getX(), entity.getY(), entity.getZ(), entity);
            }
        }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void joined(EntityJoinLevelEvent event) {
        if (!event.isCanceled()) update(event.getEntity());
    }
    @SubscribeEvent public static void moved(EntityEvent.EnteringSection event) { update(event.getEntity()); }
    @SubscribeEvent public static void left(EntityLeaveLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            var index = LEVELS.get(level);
            if (index != null) try (var ignored = org.academy.api.common.entitycontrol.WorkScheduling.budget(level).measure()) {
                index.remove(event.getEntity().getId());
            }
        }
    }
    @SubscribeEvent public static void unloaded(LevelEvent.Unload event) { LEVELS.remove(event.getLevel()); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { LEVELS.clear(); }
}
