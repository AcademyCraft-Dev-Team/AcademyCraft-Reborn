package org.academy.mixin.common.compat;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.server.level.ServerLevel;
import org.academy.internal.common.compat.PhysicsStaffDragAccess;
import org.academy.internal.common.compat.PhysicsStaffHandlerAccess;
import org.academy.internal.common.structure.sable.SableStaffCompatibility;
import org.academy.internal.common.structure.sable.SableStructureTraction;
import org.joml.Quaterniondc;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.UUID;

/** Read-only shadow views and acquisition interception; no staff state is changed by this mixin. */
@Pseudo
@Mixin(targets = "dev.simulated_team.simulated.content.physics_staff.PhysicsStaffServerHandler", remap = false)
public abstract class MixinSablePhysicsStaffHandler implements PhysicsStaffHandlerAccess {
    @Shadow @Final private Map<UUID, ?> locks;
    @Shadow @Final private Map<UUID, ?> draggingSessions;
    @Shadow private ServerLevel level;

    @Inject(method = "get(Lnet/minecraft/server/level/ServerLevel;)Ldev/simulated_team/simulated/content/physics_staff/PhysicsStaffServerHandler;",
            at = @At("RETURN"), require = 1)
    private static void academy$observeHandler(ServerLevel level, CallbackInfoReturnable<Object> cir) {
        SableStaffCompatibility.observedHandler(level, cir.getReturnValue());
    }

    @Override public Occupancy academy$staffOccupancy(Object exactSubLevel, UUID subLevelId) {
        academy$requireThread();
        // Persisted lock intent also reserves the body while its constraint is being restored.
        if (locks.containsKey(subLevelId)) return Occupancy.OCCUPIED;
        for (var session : draggingSessions.values()) {
            if (!(session instanceof PhysicsStaffDragAccess access)) return Occupancy.UNAVAILABLE;
            if (access.academy$staffSubLevel() == exactSubLevel) return Occupancy.OCCUPIED;
        }
        return Occupancy.FREE;
    }

    @Inject(method = "toggleLock(Ljava/util/UUID;)V", at = @At("HEAD"), cancellable = true, require = 1)
    private void academy$preventCompetingLock(UUID uuid, CallbackInfo ci) {
        academy$requireThread();
        // Unlocking remains possible; this only refuses a new exclusive controller.
        if (!locks.containsKey(uuid) && academy$owned(uuid)) ci.cancel();
    }

    @Inject(method = "applyLockIfNeeded(Ldev/ryanhcode/sable/sublevel/SubLevel;)V",
            at = @At("HEAD"), cancellable = true, require = 1)
    private void academy$preventCompetingRestoration(SubLevel subLevel, CallbackInfo ci) {
        academy$requireThread();
        if (subLevel instanceof ServerSubLevel ship && SableStructureTraction.exclusiveOwner(level, ship)) ci.cancel();
    }

    @Inject(method = "drag(Ljava/util/UUID;Ljava/util/UUID;Lorg/joml/Vector3dc;Lorg/joml/Vector3dc;Lorg/joml/Quaterniondc;)V",
            at = @At("HEAD"), cancellable = true, require = 1)
    private void academy$preventCompetingDrag(UUID player, UUID requestedShip, Vector3dc globalAnchor,
                                            Vector3dc localAnchor, Quaterniondc orientation, CallbackInfo ci) {
        academy$requireThread();
        if (academy$owned(requestedShip)) { ci.cancel(); return; }
        var existing = draggingSessions.get(player);
        if (existing == null) return;
        // This fork retains an existing drag's original body even if a packet names another.
        if (!(existing instanceof PhysicsStaffDragAccess access)) { ci.cancel(); return; }
        if (access.academy$staffSubLevel() instanceof ServerSubLevel ship
                && SableStructureTraction.exclusiveOwner(level, ship)) ci.cancel();
    }

    @Unique private boolean academy$owned(UUID uuid) {
        var container = SubLevelContainer.getContainer(level);
        return container != null && container.getSubLevel(uuid) instanceof ServerSubLevel ship
                && SableStructureTraction.exclusiveOwner(level, ship);
    }

    @Unique private void academy$requireThread() {
        if (level == null || !level.getServer().isSameThread()) {
            throw new IllegalStateException("Physics staff arbitration requires its owning server thread");
        }
    }
}
