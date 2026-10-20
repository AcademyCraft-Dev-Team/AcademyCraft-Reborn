package org.academy.mixin.common;

import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityPersistentStorage;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(PersistentEntitySectionManager.class)
public interface PersistentEntitySectionManagerAccessor<T extends EntityAccess> {
    @Accessor("sectionStorage")
    EntitySectionStorage<T> academy$getSectionStorage();

    @Accessor("permanentStorage")
    EntityPersistentStorage<T> academy$getPermanentStorage();

    @Invoker("ensureChunkQueuedForLoad")
    void academy$ensureChunkQueuedForLoad(long chunkKey);
}
