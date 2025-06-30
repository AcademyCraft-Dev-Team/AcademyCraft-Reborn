package org.academy.api.client.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import org.academy.AcademyCraft;
import org.academy.api.client.gui.framework.AbstractWidget;
import org.academy.api.client.util.RenderUtil;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;
import java.util.function.Predicate;

public class TextBoxWidget extends AbstractWidget {
    private final StringBuilder text = new StringBuilder();
    public final int maxLength;
    public int caretPos = 0;
    public boolean showBackground = false;
    public boolean showCaret = true;
    public long lastBlinkTime = System.currentTimeMillis();
    public int bgColor = 0x5F1F1F1F;
    public int borderColor = 0x5F5A5A5A;
    public int textColor = 0xFFFFFFFF;
    public Consumer<String> whenEnter;
    public Runnable onFocusLostCallback = null;
    public boolean clearWhenEnter = true;
    public boolean forceScale = false;
    public float scale = 1.0f;
    public Predicate<String> inputValidator = null;

    public TextBoxWidget(int maxLength, float x, float y, float width, float height) {
        super(x, y, width, height);
        this.maxLength = maxLength;
    }

    public String getText() {
        return text.toString();
    }

    public void setText(String t) {
        text.setLength(0);
        if (t != null) {
            text.append(t, 0, Math.min(t.length(), maxLength));
        }
        caretPos = text.length();
    }

    @Override
    public boolean mousePressed(double mouseX, double mouseY, int button) {
        return button == 0 && isAbsoluteMouseOver(mouseX, mouseY);
    }

    @Override
    public boolean canFocus() {
        return true;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (!isFocused() || text.length() >= maxLength || Character.isISOControl(codePoint)) {
            return false;
        }

        String potentialText = new StringBuilder(text).insert(caretPos, codePoint).toString();

        if (inputValidator == null || inputValidator.test(potentialText)) {
            text.insert(caretPos++, codePoint);
            return true;
        }

        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isFocused()) return false;
        return switch (keyCode) {
            case 259 -> {
                if (caretPos > 0) {
                    text.deleteCharAt(--caretPos);
                }
                yield true;
            }
            case 261 -> {
                if (caretPos < text.length()) {
                    text.deleteCharAt(caretPos);
                }
                yield true;
            }
            case 262 -> {
                if (caretPos < text.length()) caretPos++;
                yield true;
            }
            case 263 -> {
                if (caretPos > 0) caretPos--;
                yield true;
            }
            case GLFW.GLFW_KEY_KP_ENTER ,GLFW.GLFW_KEY_ENTER-> {
                if (whenEnter != null){
                    whenEnter.accept(getText());
                }
                if (clearWhenEnter){
                    text.setLength(0);
                }
                caretPos = 0;
                yield true;
            }
            case 334 -> {
                caretPos = text.length();
                yield true;
            }
            default -> false;
        };
    }

    @Override
    public void onFocusGained() {
        FocusGainedEvent event = new FocusGainedEvent(this);
        AcademyCraft.EVENT_BUS.post(event);
        if (event.isCanceled()) return;
        showCaret = true;
        lastBlinkTime = System.currentTimeMillis();
    }

    @Override
    public void onFocusLost() {
        FocusLostEvent event = new FocusLostEvent(this);
        AcademyCraft.EVENT_BUS.post(event);
        if (event.isCanceled()) return;
        showCaret = false;
        if (onFocusLostCallback != null) {
            onFocusLostCallback.run();
        }
    }

    @Override
    public void render(GuiGraphics graphics, double mouseX, double mouseY, float partialTicks) {
        if (showBackground) {
            RenderUtil.fill(graphics.pose().last().pose(), x, y, x + width, y + height, borderColor, graphics.bufferSource());
            RenderUtil.fill(graphics.pose().last().pose(), x + 1, y + 1, x + width - 1, y + height - 1, bgColor, graphics.bufferSource());
        }

        Font font = Minecraft.getInstance().font;
        float finalScale = scale * LabelWidget.globalScale;

        graphics.pose().pushPose();

        float textWidth = font.width(text.toString()) + 6;
        if (textWidth > width) {
            if (!forceScale) {
                scale = width / textWidth;
            }
        }
        float textHeight = font.lineHeight;
        float scaledHeight = textHeight * finalScale;
        float offsetY = (scaledHeight - textHeight) / 2;

        graphics.pose().translate(x + 1, y + (height - scaledHeight) / 4 - offsetY, 0);
        graphics.pose().scale(finalScale, finalScale, 1.0f);

        graphics.drawString(font, text.toString(), 0, 0, textColor, false);

        if (isFocused()) {
            long now = System.currentTimeMillis();
            if (now - lastBlinkTime >= 500) {
                showCaret = !showCaret;
                lastBlinkTime = now;
            }
            if (showCaret) {
                float caretX = 0;
                if (!text.isEmpty() && caretPos > 0) {
                    String beforeCaret = text.substring(0, caretPos);
                    caretX += font.width(beforeCaret);
                }
                RenderUtil.fill(
                        graphics.pose().last().pose(),
                        caretX, textHeight - 0.5f, caretX + 4, textHeight,
                        textColor,
                        graphics.bufferSource()
                );
            }
        }

        graphics.pose().popPose();
    }

    public static final class FocusGainedEvent extends Event implements ICancellableEvent {
        public final TextBoxWidget textBoxWidget;

        public FocusGainedEvent(TextBoxWidget widget) {
            this.textBoxWidget = widget;
        }
    }

    public static final class FocusLostEvent extends Event implements ICancellableEvent {
        public final TextBoxWidget textBoxWidget;

        public FocusLostEvent(TextBoxWidget widget) {
            this.textBoxWidget = widget;
        }
    }
}