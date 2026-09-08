package org.academy.api.server.ability;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import org.academy.AcademyCraft;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Captures secondary item spawns while a block mutation is part of a reversible transaction. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class BlockItemDropCapture implements AutoCloseable {
    private static final ThreadLocal<BlockItemDropCapture> ACTIVE = new ThreadLocal<>();
    private final ServerLevel level;
    private final @Nullable BlockItemDropCapture previous;
    private final List<ItemStack> drops = new ArrayList<>();
    private boolean closed;

    private BlockItemDropCapture(ServerLevel level) {
        this.level = level;
        previous = ACTIVE.get();
        ACTIVE.set(this);
    }

    public static BlockItemDropCapture capture(ServerLevel level) {
        return new BlockItemDropCapture(level);
    }

    public List<ItemStack> drops() {
        return drops.stream().map(ItemStack::copy).toList();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onItemAdded(EntityJoinLevelEvent event) {
        var capture = ACTIVE.get();
        if (capture == null || event.getLevel() != capture.level
                || !(event.getEntity() instanceof ItemEntity item)) return;
        if (!item.getItem().isEmpty()) capture.drops.add(item.getItem().copy());
        event.setCanceled(true);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (previous == null) ACTIVE.remove();
        else ACTIVE.set(previous);
    }
}
