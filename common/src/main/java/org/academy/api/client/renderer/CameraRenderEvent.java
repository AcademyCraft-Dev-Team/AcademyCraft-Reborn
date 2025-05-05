package org.academy.api.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import org.joml.Matrix4f;

public class CameraRenderEvent extends Event implements ICancellableEvent {
    public PoseStack poseStack;
    public float partialTick;
    public long finishNanoTime;
    public boolean renderBlockOutline;
    public Camera camera;
    public GameRenderer gameRenderer;
    public LightTexture lightTexture;
    public Matrix4f projectionMatrix;

    public CameraRenderEvent(PoseStack poseStack, float partialTick, long finishNanoTime, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f projectionMatrix) {
        this.poseStack = poseStack;
        this.partialTick = partialTick;
        this.finishNanoTime = finishNanoTime;
        this.renderBlockOutline = renderBlockOutline;
        this.camera = camera;
        this.gameRenderer = gameRenderer;
        this.lightTexture = lightTexture;
        this.projectionMatrix = projectionMatrix;
    }
}