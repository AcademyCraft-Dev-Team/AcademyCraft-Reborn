package org.academy.internal.common.world.entity.player;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.player.Player;

public interface PlayerSyncData {
    EntityDataAccessor<CompoundTag> DATA = SynchedEntityData.defineId(Player.class, EntityDataSerializers.COMPOUND_TAG);
}