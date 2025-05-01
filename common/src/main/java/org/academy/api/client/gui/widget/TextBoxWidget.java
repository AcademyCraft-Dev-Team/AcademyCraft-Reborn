package org.academy.api.client.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.academy.api.client.gui.framework.AbstractWidget;
import org.academy.api.client.util.RenderUtil;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

public class TextBoxWidget extends AbstractWidget {
    private final StringBuilder text = new StringBuilder();
    public final int maxLength;
    public int caretPos = 0;
    public boolean showBackground = false;
    public boolean showCaret = true;
    public long lastBlinkTime = System.currentTimeMillis();
    public int bgColor = 0xFF1F1F1F;
    public int borderColor = 0xFF5A5A5A;
    public int textColor = 0xFFFFFFFF;
    public Consumer<String> whenEnter;
    public boolean clearWhenEnter = true;
    public float scale = 1.0f;

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
    public boolean canFocus() {
        return true;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (!isFocused()) return false;
        if (text.length() < maxLength && !Character.isISOControl(codePoint)) {
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
        showCaret = true;
        lastBlinkTime = System.currentTimeMillis();
    }

    @Override
    public void onFocusLost() {
        showCaret = false;
    }

    @Override
    public void render(GuiGraphics guiGraphics, double mouseX, double mouseY, float partialTicks) {
        if (showBackground) {
            RenderUtil.GeneralRenderer.fill(guiGraphics.pose().last().pose(), x, y, x + width, y + height, borderColor, guiGraphics.bufferSource());
            RenderUtil.GeneralRenderer.fill(guiGraphics.pose().last().pose(), x + 1, y + 1, x + width - 1, y + height - 1, bgColor, guiGraphics.bufferSource());
        }

        Font font = Minecraft.getInstance().font;
        float finalScale = scale * LabelWidget.globalScale;

        guiGraphics.pose().pushPose();

        float textHeight = font.lineHeight;
        float scaledHeight = textHeight * finalScale;
        float offsetY = (scaledHeight - textHeight) / 2;

        guiGraphics.pose().translate(x, y + (height - scaledHeight) / 2 - offsetY, 0);
        guiGraphics.pose().scale(finalScale, finalScale, 1.0f);

        guiGraphics.drawString(font, text.toString(), 0, 0, textColor, false);

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
                RenderUtil.GeneralRenderer.fill(
                        guiGraphics.pose().last().pose(),
                        caretX, textHeight - 0.5f, caretX + 4, textHeight,
                        textColor,
                        guiGraphics.bufferSource()
                );
            }
        }

        guiGraphics.pose().popPose();
    }
}