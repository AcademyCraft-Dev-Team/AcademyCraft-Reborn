package org.academy.internal.common.structure.sable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.*;
import org.academy.internal.common.structure.StructureBackendRouter;

import java.util.Optional;
import java.util.UUID;
import java.util.function.BiPredicate;

/** Loaded only after the common router has verified the installed optional dependency. */
public final class SableStructureBridge implements StructureBackendRouter.Bridge {
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

    @Override public Optional<StructureHit> raycast(ServerLevel host, Vec3 from, Vec3 to) {
        return SableStructureAccess.raycast(host, from, to);
    }

    @Override public boolean clearSightline(ServerLevel host, Vec3 from, Vec3 to) {
        return SableStructureAccess.clearSightline(host, from, to);
    }

    @Override public StructureHitValidation validate(ServerLevel host, Vec3 from, Vec3 to, StructureHit proposed) {
        return SableStructureAccess.validate(host, from, to, proposed);
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

    @Override public void shutdown(MinecraftServer server) {
        SableStructureTraction.shutdown(server);
        SableStaffCompatibility.shutdown(server);
        SableStructureAccess.shutdown(server);
    }
}
