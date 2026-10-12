package org.academy.mixin.common;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only access to the vanilla teleport acknowledgement gate for custom interactions. */
@Mixin(ServerGamePacketListenerImpl.class)
public interface MixinServerGamePacketListenerNativeStructureAccess {
    @Accessor("awaitingPositionFromClient")
    @Nullable Vec3 academy$awaitingPositionFromClient();
}
