package org.academy.api.client.gui.imgui;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.logging.LogUtils;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import org.academy.Dev;
import org.academy.internal.client.gui.imgui.ImGuiUtilInternal;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

public final class ImGuiUtilApi {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final Runnable EMPTY_RUNNABLE = () -> {
    };
    public static final BiConsumer<Object, Object> EMPTY_BI_CONSUMER = (a, b) -> {
    };
    public static final Supplier<Object> NULL_SUPPLIER = () -> null;
    private static final Runnable IM_GUI_INIT;
    private static final Runnable IM_GUI_CLOSE;
    private static final Runnable CLEAR_EVENTS_QUEUE;
    private static final BiConsumer<RenderTarget, Runnable> RENDER;
    private static final Supplier<Object> IM_GUI_IMPL_GL_3_GETTER;
    private static final Supplier<Object> IM_GUI_IMPL_GLFW_GETTER;
    private static final Supplier<Boolean> WANT_CAPTURE_MOUSE_GETTER;
    private static final Supplier<Boolean> WANT_CAPTURE_KEYBOARD_GETTER;

    static {
        var imGuiInit = EMPTY_RUNNABLE;
        var imGuiClose = EMPTY_RUNNABLE;
        var clearEventsQueue = EMPTY_RUNNABLE;
        var render = (BiConsumer<RenderTarget, Runnable>) (renderTarget, runnable) -> {
        };

        var imGuiImplGl3Getter = NULL_SUPPLIER;
        var imGuiImplGlfwGetter = NULL_SUPPLIER;
        var wantCaptureMouseGetter = (Supplier<Boolean>) () -> false;
        var wantCaptureKeyboardGetter = (Supplier<Boolean>) () -> false;

        if (Dev.HAS_IM_GUI) {
            imGuiInit = ImGuiUtilInternal::init;
            imGuiClose = ImGuiUtilInternal::dispose;
            clearEventsQueue = ImGuiUtilInternal::clearEventsQueue;
            render = ImGuiUtilInternal::render;

            imGuiImplGl3Getter = ImGuiUtilInternal::getImGuiImplGl3;
            imGuiImplGlfwGetter = ImGuiUtilInternal::getImGuiImplGlfw;
            wantCaptureMouseGetter = ImGuiUtilInternal::wantCaptureMouse;
            wantCaptureKeyboardGetter = ImGuiUtilInternal::wantCaptureKeyboard;
        }

        IM_GUI_INIT = imGuiInit;
        IM_GUI_CLOSE = imGuiClose;
        CLEAR_EVENTS_QUEUE = clearEventsQueue;
        RENDER = render;
        IM_GUI_IMPL_GL_3_GETTER = imGuiImplGl3Getter;
        IM_GUI_IMPL_GLFW_GETTER = imGuiImplGlfwGetter;
        WANT_CAPTURE_MOUSE_GETTER = wantCaptureMouseGetter;
        WANT_CAPTURE_KEYBOARD_GETTER = wantCaptureKeyboardGetter;
    }

    private ImGuiUtilApi() {
    }

    public static void init() {
        IM_GUI_INIT.run();
    }

    public static void close() {
        IM_GUI_CLOSE.run();
    }

    public static void clearEventsQueue() {
        CLEAR_EVENTS_QUEUE.run();
    }

    public static void render(RenderTarget renderTarget, Runnable renderCommand) {
        RENDER.accept(renderTarget, renderCommand);
    }

    public static boolean wantCaptureMouse() {
        return WANT_CAPTURE_MOUSE_GETTER.get();
    }

    public static boolean wantCaptureKeyboard() {
        return WANT_CAPTURE_KEYBOARD_GETTER.get();
    }

    @Nullable
    public static ImGuiImplGl3 getImGuiImplGl3() {
        return (ImGuiImplGl3) IM_GUI_IMPL_GL_3_GETTER.get();
    }

    @Nullable
    public static ImGuiImplGlfw getImGuiImplGlfw() {
        return (ImGuiImplGlfw) IM_GUI_IMPL_GLFW_GETTER.get();
    }
}