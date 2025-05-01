package org.academy.api.client.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import org.academy.api.client.gui.framework.AbstractWidget;
import org.academy.api.client.gui.framework.Widget;
import org.academy.api.client.gui.framework.WidgetContainer;
import org.academy.internal.common.sounds.AcademyCraftSoundEvents;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class PanelButtonWidget extends AbstractWidget implements WidgetContainer {
    protected final Map<String, Widget> children = new LinkedHashMap<>();
    protected Runnable onPress;

    public PanelButtonWidget(float x, float y, float width, float height, Runnable onPress) {
        super(x, y, width, height);
        this.onPress = onPress;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isFocused() && button == 0) {
            playDownSound(Minecraft.getInstance().getSoundManager());
            if (onPress != null) {
                onPress.run();
            }
            return true;
        }
        return false;
    }

    public void playDownSound(net.minecraft.client.sounds.SoundManager soundManager) {
        soundManager.play(SimpleSoundInstance.forUI(AcademyCraftSoundEvents.SELECT, 1.0F));
    }

    @Override
    public boolean canFocus() {
        return this.enabled;
    }

    @Override
    public void addChild(String name, Widget child) {
        if (child.getParent() != null) {
            child.getParent().removeChild(name);
        }

        child.setParent(this);
        this.children.put(name, child);
    }

    @Override
    public void removeChild(String name) {
        if (children.containsKey(name)) {
            Widget widget = children.get(name);
            widget.setParent(null);
            children.remove(name);
        }
    }

    @Override
    public void clearChildren() {
        children.clear();
    }

    @Override
    public Map<String, Widget> getChildren() {
        return children;
    }

    @NotNull
    @Override
    public Iterator<Widget> iterator() {
        return children.values().iterator();
    }
}