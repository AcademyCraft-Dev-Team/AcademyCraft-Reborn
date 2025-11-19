package org.academy.api.client.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.academy.api.client.gui.command.FillRectDrawCommand;
import org.academy.api.client.gui.drawable.ColorDrawable;
import org.academy.api.client.gui.drawable.Drawable;
import org.academy.api.client.gui.drawable.StateListDrawable;
import org.academy.api.client.gui.drawable.WidgetState;
import org.academy.api.client.gui.event.CharTypedEvent;
import org.academy.api.client.gui.event.KeyEvent;
import org.academy.api.client.gui.event.MouseEvent;
import org.academy.api.client.gui.layout.Gravity;
import org.academy.api.client.gui.layout.MeasureSpec;
import org.academy.api.client.gui.render.RenderContext;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;
import java.util.function.Predicate;

public class TextBoxWidget extends LabelWidget {
    protected final StringBuilder text = new StringBuilder();
    protected final int maxLength;
    protected int caretPos = 0;
    @Nullable
    protected Consumer<String> whenEnter;
    @Nullable
    protected Runnable onFocusLostCallback = null;
    protected boolean clearWhenEnter = true;
    @Nullable
    protected Predicate<String> inputValidator = null;

    private boolean showCaret = true;
    private long lastBlinkTime = 0L;

    public TextBoxWidget(int maxLength) {
        super(Component.empty());
        this.maxLength = maxLength;
        setClickable(true);

        var sld = new StateListDrawable();
        sld.addState(WidgetState.DEFAULT, new ColorDrawable(0x5F1F1F1F));
        sld.addState(WidgetState.FOCUSED, new ColorDrawable(0x5F5A5A5A));
        setBackground(sld);
    }

    @Override
    protected float calculateLayoutScale(float baseTextWidth, float baseTextHeight) {
        return 1.0f;
    }

    @Override
    protected void onMeasure(MeasureSpec widthMeasureSpec, MeasureSpec heightMeasureSpec) {
        var font = Minecraft.getInstance().font;
        var lp = getLayoutParams();

        var desiredHeight = (float) font.lineHeight + lp.paddingTop + lp.paddingBottom;
        var measuredHeight = resolveSize(desiredHeight, heightMeasureSpec);
        var measuredWidth = resolveSize(lp.width, widthMeasureSpec);

        setMeasuredDimension(measuredWidth, measuredHeight);
    }

    @Override
    public void render(RenderContext context) {
        if (!isVisible()) return;

        context.drawOrder().push();
        {
            super.render(context);

            if (isFocused() && showCaret) {
                context.drawOrder().advance();
                renderCaret(context);
            }
        }
        context.drawOrder().pop();
    }

    private void renderCaret(RenderContext context) {
        var lp = getLayoutParams();
        var font = Minecraft.getInstance().font;
        var finalScale = getScale() * globalScale;

        var textBeforeCaret = text.substring(0, caretPos);
        var caretXOffset = font.width(textBeforeCaret) * finalScale;

        var visualTextWidth = font.width(text.toString()) * finalScale;
        var visualTextHeight = font.lineHeight * finalScale;

        var availableWidth = getWidth() - lp.paddingLeft - lp.paddingRight;
        var availableHeight = getHeight() - lp.paddingTop - lp.paddingBottom;

        var alignmentOffsetX = 0f;
        var horizontalGravity = (lp.gravity >> Gravity.AXIS_X_SHIFT) & 0x7;
        if (horizontalGravity == Gravity.AXIS_SPECIFIED)
            alignmentOffsetX = (availableWidth - visualTextWidth) / 2.0f;
        else if ((horizontalGravity & Gravity.AXIS_PULL_AFTER) != 0)
            alignmentOffsetX = availableWidth - visualTextWidth;

        var alignmentOffsetY = 0f;
        var verticalGravity = (lp.gravity >> Gravity.AXIS_Y_SHIFT) & 0x7;
        if (verticalGravity == Gravity.AXIS_SPECIFIED)
            alignmentOffsetY = (availableHeight - visualTextHeight) / 2.0f;
        else if ((verticalGravity & Gravity.AXIS_PULL_AFTER) != 0)
            alignmentOffsetY = availableHeight - visualTextHeight;

        var finalX = lp.paddingLeft + alignmentOffsetX + caretXOffset;
        var finalY = lp.paddingTop + alignmentOffsetY;

        context.pose().pushPose();
        context.pose().translate(finalX, finalY, 0);
        {
            var finalAlpha = getAlpha() * context.getAccumulatedAlpha();
            context.submit(new FillRectDrawCommand(0.75f, visualTextHeight, 1, 1, 1, finalAlpha));
        }
        context.pose().popPose();
    }

    @Override
    public String getText() {
        return text.toString();
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
            var lp = getLayoutParams();
            var finalScale = getScale() * globalScale;

            var visualTextWidth = font.width(text.toString()) * finalScale;
            var availableWidth = getWidth() - lp.paddingLeft - lp.paddingRight;

            var alignmentOffsetX = 0f;
            var horizontalGravity = (lp.gravity >> Gravity.AXIS_X_SHIFT) & 0x7;
            if (horizontalGravity == Gravity.AXIS_SPECIFIED)
                alignmentOffsetX = (availableWidth - visualTextWidth) / 2.0f;
            else if ((horizontalGravity & Gravity.AXIS_PULL_AFTER) != 0)
                alignmentOffsetX = availableWidth - visualTextWidth;

            var localX = (float) event.getX() - getAbsoluteX() - lp.paddingLeft - alignmentOffsetX;
            caretPos = font.plainSubstrByWidth(getText(), (int) (localX / finalScale)).length();
            event.consume();
        }
    }

    @Override
    public LabelWidget setText(String text) {
        this.text.setLength(0);
        this.text.append(text, 0, Math.min(text.length(), maxLength));
        caretPos = this.text.length();
        updateTextComponent();
        return this;
    }

    @Override
    public LabelWidget setText(Component component) {
        return setText(component.getString());
    }

    @Override
    protected void onCharTyped(CharTypedEvent event) {
        if (!isFocused() || text.length() >= maxLength || Character.isISOControl(event.getCodePoint())) return;

        caretPos = Mth.clamp(caretPos, 0, text.length());
        var potentialText = new StringBuilder(text).insert(caretPos, event.getCodePoint()).toString();

        if (inputValidator == null || inputValidator.test(potentialText)) {
            text.insert(caretPos, Character.toChars(event.getCodePoint()));
            caretPos++;
            updateTextComponent();
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
                    updateTextComponent();
                }
                yield true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (caretPos < text.length()) {
                    text.deleteCharAt(caretPos);
                    updateTextComponent();
                }
                yield true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                if (caretPos < text.length()) caretPos++;
                yield true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                if (caretPos > 0) caretPos--;
                yield true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                if (whenEnter != null) whenEnter.accept(getText());
                if (clearWhenEnter) setText("");
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

        if (handled) event.consume();
    }

    private void updateTextComponent() {
        super.setText(Component.literal(text.toString()));
        updateTextScale();
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
        if (onFocusLostCallback != null) onFocusLostCallback.run();
    }

    private void updateTextScale() {
        var font = Minecraft.getInstance().font;
        var lp = getLayoutParams();
        var textWidth = font.width(text.toString());
        var availableWidth = getWidth() - lp.paddingLeft - lp.paddingRight - 2;
        if (textWidth > availableWidth && textWidth > 0 && availableWidth > 0)
            scale = availableWidth / textWidth;
        else scale = 1.0f;
    }

    /**
     * @deprecated Use {@link #setBackground(Drawable)} to provide a custom background.
     */
    @Deprecated
    public TextBoxWidget setShowBackground(boolean show) {
        if (!show) setBackground(null);
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