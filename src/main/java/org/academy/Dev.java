package org.academy;

import imgui.ImGui;

@SuppressWarnings("ResultOfMethodCallIgnored")
public final class Dev {
    public static final boolean HAS_IM_GUI;

    static {
        var hasImGui = false;
        try {
            ImGui.class.getClass();
            hasImGui = true;
        } catch (Throwable ignored) {
        }
        HAS_IM_GUI = hasImGui;
    }

    private Dev() {
    }
}