package org.academy.api.client.render.vfxgraph.runtime;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.vfx.EffectUpdateCadence;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/** Low-frequency neutral world lighting sample; local coordinate conversion remains per frame. */
public final class EffectEnvironmentSample {
    private final EffectUpdateCadence cadence = new EffectUpdateCadence();
    private final BlockPos.MutableBlockPos block = new BlockPos.MutableBlockPos();
    private final Vector3f sunlight = new Vector3f();
    private int skyDarken = Integer.MIN_VALUE;
    private float light;
    public void update(ClientLevel level, double x, double y, double z, int stagger) {
        block.set(x, y, z);
        if (skyDarken != level.getSkyDarken()) { skyDarken = level.getSkyDarken(); cadence.invalidate(); }
        if (!cadence.due(level, level.getGameTime(), block.asLong(), 4, stagger)) return;
        light = Math.max(level.getBrightness(LightLayer.BLOCK, block), Math.max(0, level.getBrightness(LightLayer.SKY, block) - skyDarken)) / 15f;
        float sun = level.environmentAttributes().getValue(EnvironmentAttributes.SUN_ANGLE, new Vec3(x, y, z), null) * (float) (Math.PI / 180);
        sunlight.set((float) Math.sin(sun), (float) Math.cos(sun), 0);
    }
    public float light() { return light; }
    public Vector3fc sunlight() { return sunlight; }
}
