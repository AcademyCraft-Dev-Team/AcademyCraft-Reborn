package org.academy.internal.client.render.vfx;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.academy.api.client.render.vfxgraph.shape.SurfaceProjector;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/** Per-effect terrain snapshot. Cached block-column probes never load chunks or alter terrain. */
final class SkyStrikeTerrain implements SurfaceProjector {
    private final ClientLevel level;
    private final Vec3 origin;
    private final Map<Long, Float> heights = new HashMap<>();

    SkyStrikeTerrain(ClientLevel level, Vec3 origin) {
        this.level = level;
        this.origin = origin;
    }

    @Override
    public Vector3f project(float x, float y, float z, Vector3f destination) {
        int blockX = (int) Math.floor(origin.x + x);
        int blockZ = (int) Math.floor(origin.z + z);
        long key = ((long) blockX << 32) ^ (blockZ & 0xFFFFFFFFL);
        float ground = heights.computeIfAbsent(key, ignored -> sample(blockX, blockZ));
        return destination.set(x, ground + 0.08f, z);
    }

    private float sample(int x, int z) {
        if (!level.hasChunkAt(new BlockPos(x, (int) origin.y, z))) return Float.NaN;
        var start = new Vec3(x + 0.5, origin.y + 16, z + 0.5);
        var end = new Vec3(x + 0.5, origin.y - 32, z + 0.5);
        var hit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.ANY, CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS ? Float.NaN : (float) (hit.getLocation().y - origin.y);
    }
}
