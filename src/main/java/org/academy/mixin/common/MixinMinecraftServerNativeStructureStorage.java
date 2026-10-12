package org.academy.mixin.common;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import org.academy.internal.common.structure.space.NativeStructureStorageErrors;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observes vanilla I/O diagnostics without replacing, cancelling, or redirecting them. */
@Mixin(MinecraftServer.class)
public abstract class MixinMinecraftServerNativeStructureStorage {
    @Inject(method = {
            "reportChunkLoadFailure(Ljava/lang/Throwable;Lnet/minecraft/world/level/chunk/storage/RegionStorageInfo;Lnet/minecraft/world/level/ChunkPos;)V",
            "reportChunkSaveFailure(Ljava/lang/Throwable;Lnet/minecraft/world/level/chunk/storage/RegionStorageInfo;Lnet/minecraft/world/level/ChunkPos;)V"
    }, at = @At("HEAD"))
    private void academy$retainNativeStorageFailure(Throwable failure, RegionStorageInfo storageInfo,
                                                    ChunkPos position, CallbackInfo callback) {
        NativeStructureStorageErrors.reported((MinecraftServer) (Object) this, storageInfo);
    }
}
