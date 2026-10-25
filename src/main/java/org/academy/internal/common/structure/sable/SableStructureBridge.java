package org.academy.internal.common.structure.sable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.*;
import org.academy.internal.common.structure.StructureBackendRouter;
import org.academy.internal.common.structure.transaction.CaptureTarget;

import java.util.Optional;
import java.util.UUID;
import java.util.function.BiPredicate;

/** Loaded only after the common router has verified the installed optional dependency. */
public final class SableStructureBridge implements StructureBackendRouter.Bridge {
    @Override public boolean privateBlockPosition(ServerLevel host, BlockPos position) {
        var container = org.academy.internal.common.structure.sable.reflect.api.api.sublevel.SubLevelContainer.getContainer(host);
        return container != null && container.getPlot(new net.minecraft.world.level.ChunkPos(position.getX() >> 4, position.getZ() >> 4)) != null;
    }
    @Override public java.util.List<org.academy.internal.common.structure.upgrade.damage.DamageDomain> damageDomains(
            ServerLevel host, net.minecraft.world.phys.AABB area, int maximum) {
        return SableDamageAccess.nearby(host, area, maximum);
    }
    public SableStructureBridge() {
        SableStructureTraction.initialize();
        if (Boolean.getBoolean("academy.sable.retention")) {
            if (Boolean.getBoolean("academy.sable.smoke")) throw new IllegalStateException("Sable smoke and retention are mutually exclusive");
            org.academy.internal.common.structure.sable.validation.SableStructureRetentionSmoke.initialize();
        }
        if (Boolean.getBoolean("academy.sable.smoke")) {
            org.academy.internal.common.structure.sable.validation.SableStructureSmoke.initialize();
        }
    }

    @Override public Optional<StructureHandle> resolve(MinecraftServer server, StructureRef reference) {
        return SableStructureAccess.resolve(server, reference);
    }

    @Override public Optional<StructureDynamics.State> motion(ServerLevel host, StructureRef reference) {
        return SableStructureTraction.motion(host, reference);
    }

    @Override public Optional<org.academy.internal.common.structure.upgrade.control.OrientationBackendAccess> orientationAccess() {
        return Optional.of(SableOrientationAccess.INSTANCE);
    }

    @Override public Optional<StructureHit> raycast(ServerLevel host, Vec3 from, Vec3 to) {
        return SableStructureAccess.raycast(host, from, to);
    }

    @Override public Optional<StructureHit> raycast(ServerLevel host, Vec3 from, Vec3 to,
                                                   net.minecraft.world.level.ClipContext.Block shape) {
        return SableStructureAccess.raycast(host, from, to, shape);
    }

    @Override public boolean clearSightline(ServerLevel host, Vec3 from, Vec3 to) {
        return SableStructureAccess.clearSightline(host, from, to);
    }

    @Override public StructureHitValidation validate(ServerLevel host, Vec3 from, Vec3 to, StructureHit proposed) {
        return SableStructureAccess.validate(host, from, to, proposed);
    }

    @Override public StructureHitValidation validate(ServerLevel host, Vec3 from, Vec3 to, StructureHit proposed,
                                                       net.minecraft.world.level.ClipContext.Block shape) {
        return SableStructureAccess.validate(host, from, to, proposed, shape);
    }

    @Override public StructurePresentationStatus presentationStatus(MinecraftServer server, StructureRef reference) {
        return SableStructureAccess.presentationStatus(server, reference);
    }

    @Override public StructureTraction.Acquisition acquire(Entity actor, UUID owner, StructureHit proposed,
            StructureTraction.Settings settings, BiPredicate<Entity, StructureHandle> permission) {
        return SableStructureTraction.acquire(actor, owner, proposed, settings, permission);
    }

    @Override public void onBlockChanged(ServerLevel host, BlockPos position) {
        SableStructureAccess.onBlockChanged(host, position);
        SableStructureTraction.onBlockChanged(host, position);
    }

    @Override public void observeLevel(ServerLevel host) {
        SableStructureAccess.observe(host);
    }

    @Override public boolean launch(ServerLevel host, StructureRef reference, Vec3 direction, double speed) {
        return SableStructureTraction.launch(host, reference, direction, speed);
    }

    @Override public StructureDirectControl.Probe probeDirectControl(ServerLevel host, StructureRef reference) {
        return SableStructureDirectControl.probe(host, reference);
    }

    @Override public boolean submitDirectControl(ServerLevel host, StructureRef reference, Entity actor,
                                                 UUID controlId, StructurePhysicsControl control) {
        return SableStructureDirectControl.submit(host, reference, actor, controlId, control);
    }

    @Override public Optional<CaptureTarget> captureTarget(MinecraftServer server) {
        return Optional.of(new SableStructureTransactions(server));
    }

    @Override public void shutdown(MinecraftServer server) {
        SableOrientationAccess.shutdown(server);
        SableStructureTraction.shutdown(server);
        SableStructureDirectControl.shutdown(server);
        SableStaffCompatibility.shutdown(server);
        SableStructureAccess.shutdown(server);
        StructureDirectControl.clearAll();
    }
}
