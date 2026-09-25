package org.academy;

public final class Dev {
    public static final boolean HAS_IM_GUI;

    static {
        var hasImGui = false;
        try {
            var loader = Dev.class.getClassLoader();
            // 26.3 的 Minecraft 使用 SDL3。ImGui 的 GLFW 后端不可用，改用自带的 SDL 输入后端，
            // 因此只要求 ImGui binding 与 LWJGL SDL 同时存在。
            // 这里刻意不初始化目标类：早期加载 SDL 类可能触发 native 初始化而失败。
            Class.forName("imgui.ImGui", false, loader);
            Class.forName("org.lwjgl.sdl.SDLEvents", false, loader);
            hasImGui = true;
        } catch (Throwable _) {
        }
        HAS_IM_GUI = hasImGui;
    }

    private Dev() {
    }
}
