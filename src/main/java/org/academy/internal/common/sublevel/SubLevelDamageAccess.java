package org.academy.internal.common.sublevel;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import org.academy.api.common.structure.StructureHandle;
import org.academy.api.common.structure.StructureLifecycle;
import org.academy.internal.common.structure.upgrade.damage.DamageDomain;

import java.util.ArrayList;
import java.util.List;

/** Content candidates, including non-collidable decoration; never starts a native runtime. */
public final class SubLevelDamageAccess {
    private SubLevelDamageAccess() {}

    public static List<DamageDomain> nearby(ServerLevel host, AABB area, int limit) {
        var runtime = SubLevelServer.runtime(host).orElse(null);
        if (runtime == null) return List.of();
        var result = new ArrayList<DamageDomain>();
        int scanned = 0;
        for (var body : runtime.subLevels()) {
            if (++scanned > 4096) throw new IllegalStateException("damage_body_scan_budget");
            if (body.state() != SubLevelState.ACTIVE && body.state() != SubLevelState.SLEEPING) continue;
            if (!body.worldBounds().intersects(area)) continue;
            if (result.size() >= limit) throw new IllegalStateException("damage_body_budget");
            var domain = new Domain(runtime, body);
            if (!domain.transactionPending()) result.add(domain);
        }
        return List.copyOf(result);
    }

    private static final class Domain implements DamageDomain {
        private final SubLevelRuntime runtime;
        private final SubLevel body;
        private final SubLevelStructureHandle handle;
        private final PlotPos plot;
        private boolean dirty;

        Domain(SubLevelRuntime runtime, SubLevel body) {
            this.runtime = runtime; this.body = body; this.plot = body.plot();
            handle = new SubLevelStructureHandle(body, runtime.storage(), runtime.host().dimension().identifier());
        }
        public StructureHandle handle() { return handle; }
        public ServerLevel storage() { return runtime.storage().level(); }
        public BlockPos origin() { return runtime.storage().worldPosition(plot, BlockPos.ZERO); }
        public AABB localBounds() { return body.localBounds(); }
        public boolean current() {
            return runtime.get(body.id()).orElse(null) == body && body.plot().equals(plot)
                    && handle.lifecycle() == StructureLifecycle.ACTIVE && !transactionPending();
        }
        public long contentRevision() { return body.geometryRevision(); }
        public net.minecraft.world.level.chunk.@org.jspecify.annotations.Nullable LevelChunk chunk(BlockPos position) {
            if (!current() || !runtime.storage().resident(plot, position.subtract(origin()))) return null;
            return DamageDomain.super.chunk(position);
        }
        public void changed() { body.geometryChanged(); dirty = true; }
        public void flush() {
            if (!dirty) return;
            dirty = false;
            // Menu sessions cannot continue reading a removed inventory.
            SubLevelEnhancementBootstrap.INTERACTIONS.cancelStructure(runtime.host().getServer(), handle.reference(),
                    handle.runtimeId(), org.academy.api.common.structure.StructureInteractionController.CancelReason.STRUCTURE_UNAVAILABLE,
                    SubLevelServer.MINING_FEEDBACK);
            runtime.refreshGeometry(body.id());
        }
    }
}
