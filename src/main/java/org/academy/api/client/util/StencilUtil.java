package org.academy.api.client.util;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL30;

import static org.lwjgl.opengl.GL30.*;

public final class StencilUtil {
    public static void init(){
        var main = Minecraft.getInstance().getMainRenderTarget();
        if (!main.isStencilEnabled()) {
            main.enableStencil();
        }
    }

    public static void beginDrawMask() {
        RenderSystem.clear(GL_STENCIL_BUFFER_BIT, Minecraft.ON_OSX);
        GL30.glEnable(GL_STENCIL_TEST);
        RenderSystem.colorMask(false, false, false, false);
        RenderSystem.depthMask(false);
        RenderSystem.stencilFunc(GL_ALWAYS, 1, 0xFF);
        RenderSystem.stencilOp(GL_REPLACE, GL_REPLACE, GL_REPLACE);
    }

    public static void useMask() {
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.depthMask(true);
        RenderSystem.stencilFunc(GL_EQUAL, 1, 0xFF);
        RenderSystem.stencilOp(GL_KEEP, GL_KEEP, GL_KEEP);
    }

    public static void end() {
        RenderSystem.clear(GL_STENCIL_BUFFER_BIT, Minecraft.ON_OSX);
        GL30.glDisable(GL_STENCIL_TEST);
    }

    private StencilUtil() {
    }
}