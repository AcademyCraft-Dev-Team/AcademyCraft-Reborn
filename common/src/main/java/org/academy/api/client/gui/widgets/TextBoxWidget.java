package org.academy.api.client.gui.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.academy.api.client.gui.framework.AbstractWidget;

public class TextBoxWidget extends AbstractWidget {
    private final StringBuilder text = new StringBuilder();
    public final int maxLength;
    public int caretPos = 0;
    public boolean showBackground = false;
    public boolean showCaret = true;
    public long lastBlinkTime = System.currentTimeMillis();
    public final int bgColor = 0xFF1F1F1F;
    public final int borderColor = 0xFF5A5A5A;
    public final int textColor = 0xFFFFFFFF;
    public final int padding = 4;

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
            case 335 -> {
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
        if (showBackground){
            guiGraphics.fill((int) x, (int) y, (int) (x + width), (int) (y + height), borderColor);
            guiGraphics.fill((int) (x + 1), (int) (y + 1), (int) (x + width - 1), (int) (y + height - 1), bgColor);
        }

        Font font = Minecraft.getInstance().font;
        float textY = y + (height - font.lineHeight) / 2f;
        guiGraphics.drawString(font, text.toString(), (int) (x + padding), (int) textY, textColor, false);

        if (isFocused()) {
            long now = System.currentTimeMillis();
            if (now - lastBlinkTime >= 500) {
                showCaret = !showCaret;
                lastBlinkTime = now;
            }
            if (showCaret) {
                String beforeCaret = text.substring(0, caretPos);
                int caretX = (int) (x + padding + font.width(beforeCaret));
                guiGraphics.fill(caretX, (int) (y + 2), caretX + 1, (int) (y + height - 2), textColor);
            }
        }
    }
}