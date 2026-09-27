package org.academy;

public final class Dev {
    public static final boolean HAS_IM_GUI;

    static {
        var hasImGui = false;
        try {
            var loader = Dev.class.getClassLoader();
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
