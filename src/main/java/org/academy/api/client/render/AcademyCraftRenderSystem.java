package org.academy.api.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

public final class AcademyCraftRenderSystem {
    public static final Minecraft mc = Minecraft.getInstance();
    public static final LocalPlayer player = mc.player;
    public static final List<Renderer> RENDERER_LIST = new ArrayList<>();

    public interface Renderer {
        void render(PoseStack poseStack, float f, long l, boolean bl, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f matrix4f, CallbackInfo ci);
    }
}