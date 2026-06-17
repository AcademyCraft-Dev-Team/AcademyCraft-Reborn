package org.academy;

import com.mojang.blaze3d.systems.RenderSystem;
import imgui.ImGui;

@SuppressWarnings("ResultOfMethodCallIgnored")
public final class Dev {
    public static final boolean HAS_IM_GUI;

    static {
        var hasImGui = false;
        try {
            ImGui.class.getClass();
            hasImGui = true;
        } catch (Throwable _) {
        }
        if (RenderSystem.getDevice().getDeviceInfo().backendName().equals("Vulkan")) hasImGui = false;
        HAS_IM_GUI = hasImGui;
    }

    private Dev() {
    }
}