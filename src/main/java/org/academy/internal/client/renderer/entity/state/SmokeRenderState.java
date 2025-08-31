package org.academy.internal.client.renderer.entity.state;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.academy.api.common.util.MathUtil;

public class SmokeRenderState extends LivingEntityRenderState {
    public float alpha;
    public float lifeTime = 35;
    public float renderAlpha = 0;
    public int renderCount;
    public int frame = MathUtil.RANDOM.nextInt(4);
}