package org.academy.api.client.gui.framework;

import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.NoSuchElementException;

public interface WidgetContainer extends Widget {
    void addChild(String name, Widget child);

    void removeChild(String name);

    void clearChildren();

    Map<String, Widget> getChildren();

    @SuppressWarnings("unchecked")
    @NotNull
    default <T extends Widget> T getChildUnSafe(String name) {
        if (!getChildren().containsKey(name)) {
            throw new NoSuchElementException("No such child: " + name);
        }
        return (T) getChildren().get(name);
    }

    @Override
    default void render(GuiGraphics guiGraphics, double mouseX, double mouseY, float partialTick) {
        if (!isVisible()) return;

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(getX(), getY(), getZ());

        for (Widget child : getChildren().values()) {
            child.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        guiGraphics.pose().popPose();
    }
}