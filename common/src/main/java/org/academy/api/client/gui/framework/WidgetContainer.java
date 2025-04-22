package org.academy.api.client.gui.framework;

import net.minecraft.client.gui.GuiGraphics;

import java.util.Map;

public interface WidgetContainer extends Widget, Iterable<Widget> {
    void addChild(String name, Widget child);

    void removeChild(String name);

    void clearChildren();

    Map<String, Widget> getChildren();

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