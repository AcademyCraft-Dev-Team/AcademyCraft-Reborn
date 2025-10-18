package org.academy.api.client.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.academy.api.client.gui.command.FillRectDrawCommand;
import org.academy.api.client.gui.event.CharTypedEvent;
import org.academy.api.client.gui.event.KeyEvent;
import org.academy.api.client.gui.event.MouseEvent;
import org.academy.api.client.gui.framework.WidgetRenderContext;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;
import java.util.function.Predicate;

public class TextBoxWidget extends LabelWidget {
    protected final StringBuilder text = new StringBuilder();
    protected final int maxLength;
    protected int caretPos = 0;
    protected boolean showBackground = false;
    protected int bgColor = 0x5F1F1F1F;
    protected int borderColor = 0x5F5A5A5A;
    @Nullable
    protected Consumer<String> whenEnter;
    @Nullable
    protected Runnable onFocusLostCallback = null;
    protected boolean clearWhenEnter = true;
    @Nullable
    protected Predicate<String> inputValidator = null;

    private boolean showCaret = true;
    private long lastBlinkTime = 0L;

    public TextBoxWidget(int maxLength, float x, float y, float width, float height) {
        super(Component.empty(), x, y, width, height);
        this.maxLength = maxLength;
        clickable = true;
        setVerticalAlignment(VerticalAlignment.MIDDLE);
    }

    @Override
    public void render(WidgetRenderContext context, double mouseX, double mouseY, float partialTick) {
        if (!isVisible()) {
            return;
        }

        context.pose().pushPose();
        context.pose().translate(getX(), getY(), getZ());
        {
            if (showBackground) {
                renderBackground(context);
            }
        }
        context.pose().popPose();

        context.pose().pushPose();
        context.pose().translate(0, 0, 0.2f);
        {
            super.render(context, mouseX, mouseY, partialTick);
        }
        context.pose().popPose();

        if (isFocused() && showCaret) {
            renderCaret(context);
        }
    }

    private void renderBackground(WidgetRenderContext context) {
        var finalAlpha = getAbsoluteAlpha() * context.getAccumulatedAlpha();

        var borderR = ARGB.red(borderColor) / 255.0f;
        var borderG = ARGB.green(borderColor) / 255.0f;
        var borderB = ARGB.blue(borderColor) / 255.0f;
        var borderA = ARGB.alpha(borderColor) / 255.0f * finalAlpha;
        context.submit(new FillRectDrawCommand(getWidth(), getHeight(), borderR, borderG, borderB, borderA));
    }

    private void renderCaret(WidgetRenderContext context) {
        var font = Minecraft.getInstance().font;
        var finalScale = getScale() * globalScale;
        var textBeforeCaret = text.substring(0, caretPos);
        var caretXOffset = font.width(textBeforeCaret) * finalScale;

        var textHeight = font.lineHeight * finalScale * 0.85f;
        var alignmentOffsetY = (getHeight() - textHeight) / 2.0f;

        var finalCaretX = getX() + caretXOffset;
        var finalCaretY = getY() + alignmentOffsetY;

        context.pose().pushPose();
        context.pose().translate(finalCaretX, finalCaretY, getZ() + 0.2f);
        {
            var finalTextColor = ARGB.color((int) (getAbsoluteAlpha() * 255), ARGB.red(getColor()), ARGB.green(getColor()), ARGB.blue(getColor()));
            var r = ARGB.red(finalTextColor) / 255.0f;
            var g = ARGB.green(finalTextColor) / 255.0f;
            var b = ARGB.blue(finalTextColor) / 255.0f;
            var a = ARGB.alpha(finalTextColor) / 255.0f;

            context.submit(new FillRectDrawCommand(0.75f, textHeight, r, g, b, a));
        }
        context.pose().popPose();
    }

    @Override
    public String getText() {
        return text.toString();
    }

    @Override
    public LabelWidget setText(String text) {
        this.text.setLength(0);
        this.text.append(text, 0, Math.min(text.length(), maxLength));
        super.setText(Component.literal(this.text.toString()));
        caretPos = this.text.length();
        updateScale();
        return this;
    }

    @Override
    public LabelWidget setText(Component component) {
        var textStr = component.getString();
        text.setLength(0);
        text.append(textStr, 0, Math.min(textStr.length(), maxLength));
        super.setText(component);
        caretPos = text.length();
        updateScale();
        return this;
    }

    @Override
    public boolean canFocus() {
        return true;
    }

    @Override
    public void tick() {
        if (!isFocused()) return;

        var now = System.currentTimeMillis();
        if (now - lastBlinkTime >= 500) {
            showCaret = !showCaret;
            lastBlinkTime = now;
        }
    }

    @Override
    protected void onMousePressed(MouseEvent event) {
        if (event.getButton() == 0 && isMouseOver(event.getX(), event.getY())) {
            var font = Minecraft.getInstance().font;
            var localX = (float) event.getX() - getAbsoluteX() - 2;
            var textStr = getText();
            caretPos = font.plainSubstrByWidth(textStr, (int) (localX / getScale())).length();
            event.consume();
        }
    }

    @Override
    protected void onCharTyped(CharTypedEvent event) {
        if (!isFocused() || text.length() >= maxLength || Character.isISOControl(event.getCodePoint())) {
            return;
        }

        caretPos = Mth.clamp(caretPos, 0, text.length());
        var potentialText = new StringBuilder(text).insert(caretPos, event.getCodePoint()).toString();

        if (inputValidator == null || inputValidator.test(potentialText)) {
            text.insert(caretPos, Character.toChars(event.getCodePoint()));
            caretPos++;
            super.setText(text.toString());
            updateScale();
            event.consume();
        }
    }

    @Override
    protected void onKeyPressed(KeyEvent event) {
        if (!isFocused()) return;

        var handled = switch (event.getKeyCode()) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (caretPos > 0) {
                    --caretPos;
                    text.deleteCharAt(caretPos);
                    super.setText(text.toString());
                    updateScale();
                }
                yield true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (caretPos < text.length()) {
                    text.deleteCharAt(caretPos);
                    super.setText(text.toString());
                    updateScale();
                }
                yield true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                if (caretPos < text.length()) {
                    caretPos++;
                }
                yield true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                if (caretPos > 0) {
                    caretPos--;
                }
                yield true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                if (whenEnter != null) {
                    whenEnter.accept(getText());
                }
                if (clearWhenEnter) {
                    setText("");
                }
                yield true;
            }
            case GLFW.GLFW_KEY_END -> {
                caretPos = text.length();
                yield true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                caretPos = 0;
                yield true;
            }
            default -> false;
        };

        if (handled) {
            event.consume();
        }
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

    private void updateScale() {
        var font = Minecraft.getInstance().font;
        var textWidth = font.width(text.toString());
        var availableWidth = getWidth() - 1;
        if (textWidth > availableWidth && textWidth > 0) {
            setScale(availableWidth / textWidth);
        } else {
            setScale(1.0f);
        }
    }

    public TextBoxWidget setShowBackground(boolean show) {
        showBackground = show;
        return this;
    }

    public TextBoxWidget setBgColor(int color) {
        bgColor = color;
        return this;
    }

    public TextBoxWidget setBorderColor(int color) {
        borderColor = color;
        return this;
    }

    @Override
    public LabelWidget setColor(int color) {
        super.setColor(color);
        return this;
    }

    public TextBoxWidget setWhenEnter(@Nullable Consumer<String> callback) {
        whenEnter = callback;
        return this;
    }

    public TextBoxWidget setOnFocusLost(@Nullable Runnable callback) {
        onFocusLostCallback = callback;
        return this;
    }

    public TextBoxWidget setClearWhenEnter(boolean clear) {
        clearWhenEnter = clear;
        return this;
    }

    public TextBoxWidget setInputValidator(@Nullable Predicate<String> validator) {
        inputValidator = validator;
        return this;
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