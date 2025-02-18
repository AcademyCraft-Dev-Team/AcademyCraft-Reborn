package org.academy;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.render.RenderSystem;
import org.academy.api.client.util.RenderUtil;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.api.common.ability.Skill;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AbilitySystemClient {
    public static boolean active = false;

    public interface Renderer {
        void render(PoseStack poseStack, float f, long l, boolean bl, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f matrix4f, CallbackInfo ci);
    }

    public static void init() {
        InputSystem.KEY_RELEASE_MAP.put(Collections.singletonList(GLFW.GLFW_KEY_V), () -> active = !active);
        for (AbilityCategory abilityCategory : AbilitySystem.abilityCategoryMap.values()) {
            abilityCategory.initClient();
            for (Skill skill : abilityCategory.skillList) {
                skill.initClient();
            }
        }
    }
}
