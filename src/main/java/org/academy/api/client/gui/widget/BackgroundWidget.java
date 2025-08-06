package org.academy.api.client.gui.widget;

import net.minecraft.client.renderer.MultiBufferSource;
import org.academy.api.client.gui.event.MouseEvent;
import org.academy.api.client.gui.framework.AbstractWidget;
import org.academy.api.client.render.MatrixStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A simple, often screen-sized, widget used to capture background clicks.
 * This widget itself is transparent; the actual background rendering (e.g., a gradient or texture)
 * should be handled by the screen class before rendering the main widget tree.
 */
public class BackgroundWidget extends AbstractWidget {
    @Nullable
    private final Runnable onClick;

    /**
     * Creates a clickable background area. Its dimensions should be set by its parent
     * container, typically to fill the entire screen.
     * @param onClick The action to perform when the background is clicked. Can be null.
     */
    public BackgroundWidget(@Nullable Runnable onClick) {
        super(0, 0, 0, 0);
        this.onClick = onClick;
        this.clickable = true;
    }

    @Override
    public void render(@NotNull MatrixStack stack, @NotNull MultiBufferSource.BufferSource bufferSource, double mouseX, double mouseY, float partialTick) {
        // This widget is intended to be a transparent, clickable area.
        // The visual background is rendered by the Screen class before this widget.
    }

    @Override
    protected void onMousePressed(@NotNull MouseEvent event) {
        if (event.getButton() == 0 && this.onClick != null) {
            this.onClick.run();
            event.consume();
        }
    }
}