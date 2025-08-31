package org.academy.api.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

import java.util.List;

public final class ItemRenderEvent extends Event implements ICancellableEvent {
    private final ItemDisplayContext displayContext;
    private final PoseStack poseStack;
    private final MultiBufferSource bufferSource;
    private final int packedLight;
    private final int packedOverlay;
    private final int[] tintLayers;
    private final List<BakedQuad> quads;
    private final RenderType renderType;
    private final ItemStackRenderState.FoilType foilType;

    public ItemRenderEvent(ItemDisplayContext displayContext, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay, int[] tintLayers, List<BakedQuad> quads, RenderType renderType, ItemStackRenderState.FoilType foilType) {
        this.displayContext = displayContext;
        this.poseStack = poseStack;
        this.bufferSource = bufferSource;
        this.packedLight = packedLight;
        this.packedOverlay = packedOverlay;
        this.tintLayers = tintLayers;
        this.quads = quads;
        this.renderType = renderType;
        this.foilType = foilType;
    }

    public ItemDisplayContext getDisplayContext() {
        return displayContext;
    }

    public PoseStack getPoseStack() {
        return poseStack;
    }

    public MultiBufferSource getBufferSource() {
        return bufferSource;
    }

    public int getPackedLight() {
        return packedLight;
    }

    public int getPackedOverlay() {
        return packedOverlay;
    }

    public int[] getTintLayers() {
        return tintLayers;
    }

    public List<BakedQuad> getQuads() {
        return quads;
    }

    public RenderType getRenderType() {
        return renderType;
    }

    public ItemStackRenderState.FoilType getFoilType() {
        return foilType;
    }
}