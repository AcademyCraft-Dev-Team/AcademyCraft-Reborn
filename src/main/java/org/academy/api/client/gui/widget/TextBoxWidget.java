package org.academy.api.client.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.Tickable;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.academy.api.client.gui.event.CharTypedEvent;
import org.academy.api.client.gui.event.KeyEvent;
import org.academy.api.client.gui.event.MouseEvent;
import org.academy.api.client.gui.framework.AbstractWidget;
import org.academy.api.client.render.MatrixStack;
import org.academy.api.client.util.RenderUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;
import java.util.function.Predicate;

public class TextBoxWidget extends AbstractWidget implements Tickable {
    protected final StringBuilder text = new StringBuilder();
    protected final int maxLength;
    protected int caretPos = 0;
    protected boolean showBackground = false;
    protected int bgColor = 0x5F1F1F1F;
    protected int borderColor = 0x5F5A5A5A;
    protected int textColor = 0xFFFFFFFF;
    protected Consumer<String> whenEnter;
    protected Runnable onFocusLostCallback = null;
    protected boolean clearWhenEnter = true;
    protected boolean forceScale = false;
    protected float scale = 1.0f;
    protected Predicate<String> inputValidator = null;

    private boolean showCaret = true;
    private long lastBlinkTime = 0L;

    public TextBoxWidget(int maxLength, float x, float y, float width, float height) {
        super(x, y, width, height);
        this.maxLength = maxLength;
        this.clickable = true;
    }

    public String getText() {
        return this.text.toString();
    }

    public void setText(String newText) {
        this.text.setLength(0);
        if (newText != null) {
            this.text.append(newText, 0, Math.min(newText.length(), this.maxLength));
        }
        this.caretPos = this.text.length();
        this.updateScale();
    }

    @Override
    public boolean canFocus() {
        return true;
    }

    @Override
    public void tick() {
        if (!this.isFocused()) return;

        long now = System.currentTimeMillis();
        if (now - this.lastBlinkTime >= 500) {
            this.showCaret = !this.showCaret;
            this.lastBlinkTime = now;
        }
    }

    @Override
    protected void onMousePressed(@NotNull MouseEvent event) {
        if (event.getButton() == 0 && this.isMouseOver(event.getX(), event.getY())) {
            event.consume();
        }
    }

    @Override
    protected void onCharTyped(@NotNull CharTypedEvent event) {
        if (!this.isFocused() || this.text.length() >= this.maxLength || Character.isISOControl(event.getCodePoint())) {
            return;
        }
        var potentialText = new StringBuilder(this.text).insert(this.caretPos, event.getCodePoint()).toString();
        if (this.inputValidator == null || this.inputValidator.test(potentialText)) {
            this.text.insert(this.caretPos++, event.getCodePoint());
            this.updateScale();
            event.consume();
        }
    }

    @Override
    protected void onKeyPressed(@NotNull KeyEvent event) {
        if (!isFocused()) return;
        boolean handled = switch (event.getKeyCode()) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (this.caretPos > 0) {
                    this.text.deleteCharAt(--this.caretPos);
                    this.updateScale();
                }
                yield true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (this.caretPos < this.text.length()) {
                    this.text.deleteCharAt(this.caretPos);
                    this.updateScale();
                }
                yield true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                if (this.caretPos < this.text.length()) this.caretPos++;
                yield true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                if (this.caretPos > 0) this.caretPos--;
                yield true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                if (this.whenEnter != null) {
                    this.whenEnter.accept(this.getText());
                }
                if (this.clearWhenEnter) {
                    this.setText("");
                }
                yield true;
            }
            case GLFW.GLFW_KEY_END, 334 -> {
                this.caretPos = this.text.length();
                yield true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                this.caretPos = 0;
                yield true;
            }
            default -> false;
        };
        if (handled) event.consume();
    }

    @Override
    public void onFocusGained() {
        var event = new FocusGainedEvent(this);
        NeoForge.EVENT_BUS.post(event);
        if (event.isCanceled()) return;
        this.showCaret = true;
        this.lastBlinkTime = System.currentTimeMillis();
    }

    @Override
    public void onFocusLost() {
        var event = new FocusLostEvent(this);
        NeoForge.EVENT_BUS.post(event);
        if (event.isCanceled()) return;
        this.showCaret = false;
        if (this.onFocusLostCallback != null) {
            this.onFocusLostCallback.run();
        }
    }

    private void updateScale() {
        if (this.forceScale) {
            return;
        }
        var font = Minecraft.getInstance().font;
        float textWidth = font.width(this.text.toString()) + 6;
        if (textWidth > this.getWidth()) {
            this.scale = this.getWidth() / textWidth;
        } else {
            this.scale = 1.0f;
        }
    }

    @Override
    public void render(@NotNull MatrixStack stack, MultiBufferSource.@NotNull BufferSource bufferSource, double mouseX, double mouseY, float partialTick) {
        if (!this.isVisible()) return;

        if (this.showBackground) {
            this.renderBackground(stack, bufferSource);
        }
        this.renderTextAndCaret(stack, bufferSource);
    }

    private void renderBackground(MatrixStack stack, MultiBufferSource.BufferSource bufferSource) {
        var finalBorderColor = RenderUtil.applyAlpha(this.borderColor, this.getAbsoluteAlpha());
        var finalBgColor = RenderUtil.applyAlpha(this.bgColor, this.getAbsoluteAlpha());
        RenderUtil.fill(stack, bufferSource, 0, 0, this.getWidth(), this.getHeight(), finalBorderColor);
        RenderUtil.fill(stack, bufferSource, 1, 1, this.getWidth() - 2, this.getHeight() - 2, finalBgColor);
    }

    private void renderTextAndCaret(MatrixStack stack, MultiBufferSource.BufferSource bufferSource) {
        var finalTextColor = RenderUtil.applyAlpha(this.textColor, this.getAbsoluteAlpha());
        var font = Minecraft.getInstance().font;
        var finalScale = this.scale * LabelWidget.globalScale;
        stack.pushPose();
        var textHeight = font.lineHeight;
        var scaledHeight = textHeight * finalScale;
        stack.translate(2, (this.getHeight() - scaledHeight) / 2.0f, 0);
        stack.scale(finalScale, finalScale, 1.0f);
        RenderUtil.drawString(stack, bufferSource, font, this.text.toString(), finalTextColor, false);
        if (this.isFocused() && this.showCaret) {
            var caretX = 0f;
            if (this.caretPos > 0) {
                var beforeCaret = this.text.substring(0, this.caretPos);
                caretX = font.width(beforeCaret);
            }
            RenderUtil.fill(stack, bufferSource, caretX - 1, -1, 1, textHeight + 1, finalTextColor);
        }
        stack.popPose();
    }

    @NotNull
    public TextBoxWidget setShowBackground(boolean show) {
        this.showBackground = show;
        return this;
    }

    @NotNull
    public TextBoxWidget setBgColor(int color) {
        this.bgColor = color;
        return this;
    }

    @NotNull
    public TextBoxWidget setBorderColor(int color) {
        this.borderColor = color;
        return this;
    }

    @NotNull
    public TextBoxWidget setTextColor(int color) {
        this.textColor = color;
        return this;
    }

    @NotNull
    public TextBoxWidget setWhenEnter(@Nullable Consumer<String> callback) {
        this.whenEnter = callback;
        return this;
    }

    @NotNull
    public TextBoxWidget setOnFocusLost(@Nullable Runnable callback) {
        this.onFocusLostCallback = callback;
        return this;
    }

    @NotNull
    public TextBoxWidget setClearWhenEnter(boolean clear) {
        this.clearWhenEnter = clear;
        return this;
    }

    @NotNull
    public TextBoxWidget setForceScale(boolean force, float scale) {
        this.forceScale = force;
        this.scale = scale;
        return this;
    }

    @NotNull
    public TextBoxWidget setInputValidator(@Nullable Predicate<String> validator) {
        this.inputValidator = validator;
        return this;
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