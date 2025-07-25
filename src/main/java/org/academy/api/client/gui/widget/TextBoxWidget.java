package org.academy.api.client.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.academy.api.client.gui.framework.AbstractWidget;
import org.academy.api.client.render.MatrixStack;
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

    public TextBoxWidget(int newMaxLength, float x, float y, float width, float height) {
        super(x, y, width, height);
        maxLength = newMaxLength;
    }

    public String getText() {
        return text.toString();
    }

    public void setText(String newText) {
        text.setLength(0);
        if (newText != null) {
            text.append(newText, 0, Math.min(newText.length(), maxLength));
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

        var potentialText = new StringBuilder(text).insert(caretPos, codePoint).toString();

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
            case GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_ENTER -> {
                if (whenEnter != null) {
                    whenEnter.accept(getText());
                }
                if (clearWhenEnter) {
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
        var event = new FocusGainedEvent(this);
        NeoForge.EVENT_BUS.post(event);
        if (event.isCanceled()) return;
        showCaret = true;
        lastBlinkTime = System.currentTimeMillis();
    }

    @Override
    public void onFocusLost() {
        var event = new FocusLostEvent(this);
        NeoForge.EVENT_BUS.post(event);
        if (event.isCanceled()) return;
        showCaret = false;
        if (onFocusLostCallback != null) {
            onFocusLostCallback.run();
        }
    }

    @Override
    public void render(MatrixStack stack, MultiBufferSource.BufferSource bufferSource, double mouseX, double mouseY, float partialTick) {
        var absoluteAlpha = getAbsoluteAlpha();

        if (showBackground) {
            var finalBorderColor = (borderColor & 0x00FFFFFF) | ((int) (((borderColor >> 24) & 0xFF) * absoluteAlpha) << 24);
            var finalBgColor = (bgColor & 0x00FFFFFF) | ((int) (((bgColor >> 24) & 0xFF) * absoluteAlpha) << 24);

            RenderUtil.fill(stack, bufferSource, x, y, x + width, y + height, finalBorderColor);
            RenderUtil.fill(stack, bufferSource, x + 1, y + 1, x + width - 1, y + height - 1, finalBgColor);
        }
        var finalAlpha = (int) (((textColor >> 24) & 0xFF) * absoluteAlpha);
        if (finalAlpha <= 3) finalAlpha = 4;
        var finalTextColor = (textColor & 0x00FFFFFF) | (finalAlpha << 24);

        var font = Minecraft.getInstance().font;
        var finalScale = scale * LabelWidget.globalScale;

        stack.pushPose();

        var textWidth = font.width(text.toString()) + 6;
        if (textWidth > width) {
            if (!forceScale) {
                scale = width / textWidth;
            }
        }
        var textHeight = font.lineHeight;
        var scaledHeight = textHeight * finalScale;
        var offsetY = (scaledHeight - textHeight) / 2;

        stack.translate(x + 1, y + (height - scaledHeight) / 4 - offsetY, 0);
        stack.scale(finalScale, finalScale, 1.0f);

        RenderUtil.drawString(stack, bufferSource, font, text.toString(), 0, 0, finalTextColor, false);

        if (isFocused()) {
            var now = System.currentTimeMillis();
            if (now - lastBlinkTime >= 500) {
                showCaret = !showCaret;
                lastBlinkTime = now;
            }
            if (showCaret) {
                var caretX = 0f;
                if (!text.isEmpty() && caretPos > 0) {
                    var beforeCaret = text.substring(0, caretPos);
                    caretX += font.width(beforeCaret);
                }
                RenderUtil.fill(
                        stack,
                        bufferSource,
                        caretX, textHeight - 0.5f, caretX + 4, textHeight,
                        finalTextColor
                );
            }
        }

        stack.popPose();
    }

    public static final class FocusGainedEvent extends Event implements ICancellableEvent {
        public final TextBoxWidget textBoxWidget;

        public FocusGainedEvent(TextBoxWidget widget) {
            textBoxWidget = widget;
        }
    }

    public static final class FocusLostEvent extends Event implements ICancellableEvent {
        public final TextBoxWidget textBoxWidget;

        public FocusLostEvent(TextBoxWidget widget) {
            textBoxWidget = widget;
        }
    }
}