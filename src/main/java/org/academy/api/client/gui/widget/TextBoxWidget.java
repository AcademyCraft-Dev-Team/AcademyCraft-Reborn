package org.academy.api.client.gui.widget;

import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.academy.api.client.gui.command.FillRectDrawCommand;
import org.academy.api.client.gui.drawable.ColorDrawable;
import org.academy.api.client.gui.drawable.StateListDrawable;
import org.academy.api.client.gui.event.CharTypedEvent;
import org.academy.api.client.gui.event.KeyEvent;
import org.academy.api.client.gui.event.MouseEvent;
import org.academy.api.client.gui.layout.Gravity;
import org.academy.api.client.gui.render.RenderContext;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;
import java.util.function.Predicate;

public class TextBoxWidget extends LabelWidget {
    protected final StringBuilder stringBuilder = new StringBuilder();
    protected final int maxLength;
    protected int caretPos = 0;
    protected int selectionStart = 0;
    protected int selectionEnd = 0;
    protected boolean hasSelection = false;
    protected boolean allowLineBreak = false;
    @Nullable
    protected Consumer<String> whenEnter;
    @Nullable
    protected Runnable onFocusLostCallback = null;
    protected boolean clearWhenEnter = true;
    @Nullable
    protected Predicate<String> inputValidator = null;

    private boolean showCaret = true;
    private long lastBlinkTime = 0L;
    private boolean mouseDragging = false;
    private int dragStartPos = 0;

    public TextBoxWidget(int maxLength) {
        super("");
        this.maxLength = maxLength;
        setClickable(true);

        var sld = new StateListDrawable();
        sld.setDefault(new ColorDrawable(0x5F1F1F1F));
        sld.addState(State.FOCUSED, new ColorDrawable(0x5F5A5A5A));
        setBackground(sld);
    }

    @Override
    public void render(RenderContext context) {
        if (!isVisible()) return;

        context.drawOrder().push();
        {
            super.render(context);

            if (hasSelection) {
                context.drawOrder().advance();
                renderSelection(context);
            }

            if (isFocused() && showCaret) {
                context.drawOrder().advance();
                renderCaret(context);
            }
        }
        context.drawOrder().pop();
    }

    private void renderCaret(RenderContext context) {
        var lp = getLayoutParams();
        var finalScale = layoutScale * scale;
        var textBeforeCaret = stringBuilder.substring(0, getCodeUnitIndexForCodePoint(caretPos));
        var caretXOffset = getTextWidth(textBeforeCaret) * finalScale;
        var availableHeight = getHeight() - lp.paddingTop - lp.paddingBottom;
        var visualTextHeight = availableHeight * finalScale;
        var availableWidth = getWidth() - lp.paddingLeft - lp.paddingRight;

        var alignmentOffsetX = 0f;
        var horizontalGravity = (lp.gravity >> Gravity.AXIS_X_SHIFT) & 0x7;
        if (horizontalGravity == Gravity.AXIS_SPECIFIED)
            alignmentOffsetX = (availableWidth - getTextWidth(stringBuilder.toString()) * finalScale) / 2.0f;
        else if ((horizontalGravity & Gravity.AXIS_PULL_AFTER) != 0)
            alignmentOffsetX = availableWidth - getTextWidth(stringBuilder.toString()) * finalScale;
        var alignmentOffsetY = 0f;
        var verticalGravity = (lp.gravity >> Gravity.AXIS_Y_SHIFT) & 0x7;
        if (verticalGravity == Gravity.AXIS_SPECIFIED) alignmentOffsetY = (availableHeight - visualTextHeight) / 2.0f;
        else if ((verticalGravity & Gravity.AXIS_PULL_AFTER) != 0)
            alignmentOffsetY = availableHeight - visualTextHeight;

        var finalX = lp.paddingLeft + alignmentOffsetX + caretXOffset;
        var finalY = lp.paddingTop + alignmentOffsetY;

        context.pose().pushPose();
        context.pose().translate(finalX, finalY, 0);
        context.submit(new FillRectDrawCommand(0.5f, visualTextHeight, 1, 1, 1, getAlpha() * context.getAccumulatedAlpha()));
        context.pose().popPose();
    }

    private void renderSelection(RenderContext context) {
        var lp = getLayoutParams();
        var finalScale = layoutScale * scale;
        var fullText = stringBuilder.toString();

        var start = Math.min(selectionStart, selectionEnd);
        var end = Math.max(selectionStart, selectionEnd);

        if (start >= end) return;

        var textBeforeStart = fullText.substring(0, getCodeUnitIndexForCodePoint(start));
        var selectedText = fullText.substring(getCodeUnitIndexForCodePoint(start),
                getCodeUnitIndexForCodePoint(end));

        var startXOffset = getTextWidth(textBeforeStart) * finalScale + 0.5f;
        var selectionWidth = getTextWidth(selectedText) * finalScale;
        var visualTextHeight = getTextHeight(fullText) * finalScale;

        var availableWidth = getWidth() - lp.paddingLeft - lp.paddingRight;
        var availableHeight = getHeight() - lp.paddingTop - lp.paddingBottom;

        var alignmentOffsetX = 0f;
        var horizontalGravity = (lp.gravity >> Gravity.AXIS_X_SHIFT) & 0x7;
        if (horizontalGravity == Gravity.AXIS_SPECIFIED)
            alignmentOffsetX = (availableWidth - getTextWidth(fullText) * finalScale) / 2.0f;
        else if ((horizontalGravity & Gravity.AXIS_PULL_AFTER) != 0)
            alignmentOffsetX = availableWidth - getTextWidth(fullText) * finalScale;
        var alignmentOffsetY = 0f;
        var verticalGravity = (lp.gravity >> Gravity.AXIS_Y_SHIFT) & 0x7;
        if (verticalGravity == Gravity.AXIS_SPECIFIED) alignmentOffsetY = (availableHeight - visualTextHeight) / 2.0f;
        else if ((verticalGravity & Gravity.AXIS_PULL_AFTER) != 0)
            alignmentOffsetY = availableHeight - visualTextHeight;

        var finalX = lp.paddingLeft + alignmentOffsetX + startXOffset;
        var finalY = lp.paddingTop + alignmentOffsetY;

        context.pose().pushPose();
        context.pose().translate(finalX, finalY, 0);
        context.submit(new FillRectDrawCommand(selectionWidth, visualTextHeight, 0.3f, 0.5f, 0.8f, getAlpha() * context.getAccumulatedAlpha() * 0.5f));
        context.pose().popPose();
    }

    @Override
    public String getText() {
        return stringBuilder.toString();
    }

    @Override
    public LabelWidget setText(String text) {
        stringBuilder.setLength(0);
        var codePointCount = text.codePointCount(0, text.length());
        if (codePointCount > maxLength) {
            var endIndex = text.offsetByCodePoints(0, maxLength);
            stringBuilder.append(text, 0, endIndex);
        } else {
            stringBuilder.append(text);
        }
        caretPos = stringBuilder.codePointCount(0, stringBuilder.length());
        clearSelection();
        updateTextComponent();
        return this;
    }

    @Override
    protected void onCharTyped(CharTypedEvent event) {
        if (!isFocused() || stringBuilder.codePointCount(0, stringBuilder.length()) >= maxLength || Character.isISOControl(event.getCodePoint()))
            return;

        if (!allowLineBreak && (event.getCodePoint() == '\n' || event.getCodePoint() == '\r')) {
            return;
        }

        caretPos = Math.clamp(caretPos, 0, stringBuilder.codePointCount(0, stringBuilder.length()));

        if (hasSelection) {
            deleteSelectedText();
        }

        var potentialText = new StringBuilder(stringBuilder).insert(getCodeUnitIndexForCodePoint(caretPos), Character.toChars(event.getCodePoint())).toString();
        if (inputValidator == null || inputValidator.test(potentialText)) {
            stringBuilder.insert(getCodeUnitIndexForCodePoint(caretPos), Character.toChars(event.getCodePoint()));
            caretPos++;
            clearSelection();
            updateTextComponent();
            event.consume();
        }
    }

    @Override
    protected void onKeyPressed(KeyEvent event) {
        if (!isFocused()) return;

        var handled = switch (event.getKeyCode()) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (hasSelection) {
                    deleteSelectedText();
                } else if (caretPos > 0) {
                    caretPos--;
                    var deleteIndex = getCodeUnitIndexForCodePoint(caretPos);
                    var charCount = Character.charCount(stringBuilder.codePointAt(Math.min(deleteIndex, stringBuilder.length() - 1)));
                    stringBuilder.delete(deleteIndex, deleteIndex + charCount);
                    updateTextComponent();
                }
                yield true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (hasSelection) {
                    deleteSelectedText();
                } else if (caretPos < stringBuilder.codePointCount(0, stringBuilder.length())) {
                    var deleteIndex = getCodeUnitIndexForCodePoint(caretPos);
                    var charCount = Character.charCount(stringBuilder.codePointAt(Math.min(deleteIndex, stringBuilder.length() - 1)));
                    stringBuilder.delete(deleteIndex, deleteIndex + charCount);
                    updateTextComponent();
                }
                yield true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                var extend = event.hasShiftDown();
                if (!extend) {
                    clearSelection();
                } else if (!hasSelection) {
                    selectionStart = caretPos;
                    hasSelection = true;
                }

                if (caretPos < stringBuilder.codePointCount(0, stringBuilder.length())) {
                    caretPos++;
                    if (extend) {
                        selectionEnd = caretPos;
                    }
                }
                yield true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                var extend = event.hasShiftDown();
                if (!extend) {
                    clearSelection();
                } else if (!hasSelection) {
                    selectionStart = caretPos;
                    hasSelection = true;
                }

                if (caretPos > 0) {
                    caretPos--;
                    if (extend) {
                        selectionEnd = caretPos;
                    }
                }
                yield true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                if (allowLineBreak) {
                    if (hasSelection) {
                        deleteSelectedText();
                    }
                    var potentialText = new StringBuilder(stringBuilder).insert(getCodeUnitIndexForCodePoint(caretPos), '\n').toString();
                    if (inputValidator == null || inputValidator.test(potentialText)) {
                        stringBuilder.insert(getCodeUnitIndexForCodePoint(caretPos), '\n');
                        caretPos++;
                        clearSelection();
                        updateTextComponent();
                    }
                } else {
                    if (whenEnter != null) whenEnter.accept(getText());
                    if (clearWhenEnter) setText("");
                }
                yield true;
            }
            case GLFW.GLFW_KEY_END -> {
                var extend = event.hasShiftDown();
                if (!extend) {
                    clearSelection();
                } else if (!hasSelection) {
                    selectionStart = caretPos;
                    hasSelection = true;
                }

                caretPos = stringBuilder.codePointCount(0, stringBuilder.length());
                if (extend) {
                    selectionEnd = caretPos;
                }
                yield true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                var extend = event.hasShiftDown();
                if (!extend) {
                    clearSelection();
                } else if (!hasSelection) {
                    selectionStart = caretPos;
                    hasSelection = true;
                }

                caretPos = 0;
                if (extend) {
                    selectionEnd = caretPos;
                }
                yield true;
            }
            case GLFW.GLFW_KEY_A -> {
                if (event.hasControlDownWithQuirk()) {
                    selectAll();
                    yield true;
                }
                yield false;
            }
            case GLFW.GLFW_KEY_C -> {
                if (event.hasControlDownWithQuirk() && hasSelection) {
                    copyToClipboard();
                    yield true;
                }
                yield false;
            }
            case GLFW.GLFW_KEY_V -> {
                if (event.hasControlDownWithQuirk()) {
                    pasteFromClipboard();
                    yield true;
                }
                yield false;
            }
            case GLFW.GLFW_KEY_X -> {
                if (event.hasControlDownWithQuirk() && hasSelection) {
                    cutToClipboard();
                    yield true;
                }
                yield false;
            }
            default -> false;
        };
        if (handled) event.consume();
    }

    private void updateTextComponent() {
        super.setText(stringBuilder.toString());
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
            mouseDragging = true;
            dragStartPos = getCaretPosAtMouse(event.getX());
            caretPos = dragStartPos;
            selectionStart = dragStartPos;
            selectionEnd = dragStartPos;
            hasSelection = false;

            showCaret = true;
            lastBlinkTime = System.currentTimeMillis();
            event.consume();
        }
    }

    @Override
    protected void onMouseReleased(MouseEvent event) {
        if (event.getButton() == 0) {
            mouseDragging = false;
        }
    }

    protected void onMouseDragged(MouseEvent event) {
        if (mouseDragging && event.getButton() == 0) {
            var newCaretPos = getCaretPosAtMouse(event.getX());

            if (!hasSelection && newCaretPos != dragStartPos) {
                hasSelection = true;
            }

            if (hasSelection) {
                selectionStart = dragStartPos;
                selectionEnd = newCaretPos;
            }

            caretPos = newCaretPos;
            event.consume();
        }
    }

    private int getCaretPosAtMouse(double mouseX) {
        var lp = getLayoutParams();
        var finalScale = scale * layoutScale;
        var availableWidth = getWidth() - lp.paddingLeft - lp.paddingRight;
        var visualTextWidth = getTextWidth(stringBuilder.toString()) * finalScale;
        var alignmentOffsetX = 0f;
        var horizontalGravity = (lp.gravity >> Gravity.AXIS_X_SHIFT) & 0x7;
        if (horizontalGravity == Gravity.AXIS_SPECIFIED)
            alignmentOffsetX = (availableWidth - visualTextWidth) / 2.0f;
        else if ((horizontalGravity & Gravity.AXIS_PULL_AFTER) != 0)
            alignmentOffsetX = availableWidth - visualTextWidth;
        var localX = (float) mouseX - getAbsoluteX() - lp.paddingLeft - alignmentOffsetX;
        var fullText = stringBuilder.toString();
        var caretPos = 0;
        var codePointCount = fullText.codePointCount(0, fullText.length());
        for (var i = 1; i <= codePointCount; i++) {
            var codeUnitIndex = fullText.offsetByCodePoints(0, i);
            if (getTextWidth(fullText.substring(0, codeUnitIndex)) * finalScale > localX) break;
            caretPos = i;
        }
        return caretPos;
    }

    private int getCodeUnitIndexForCodePoint(int codePointIndex) {
        if (codePointIndex <= 0) return 0;
        return stringBuilder.offsetByCodePoints(0, Math.min(codePointIndex,
                stringBuilder.codePointCount(0, stringBuilder.length())));
    }

    private void clearSelection() {
        hasSelection = false;
        selectionStart = 0;
        selectionEnd = 0;
    }

    private void deleteSelectedText() {
        if (!hasSelection) return;

        var start = Math.min(selectionStart, selectionEnd);
        var end = Math.max(selectionStart, selectionEnd);

        var startIndex = getCodeUnitIndexForCodePoint(start);
        var endIndex = getCodeUnitIndexForCodePoint(end);

        stringBuilder.delete(startIndex, endIndex);
        caretPos = start;
        clearSelection();
        updateTextComponent();
    }

    private String getSelectedText() {
        if (!hasSelection) return "";

        var start = Math.min(selectionStart, selectionEnd);
        var end = Math.max(selectionStart, selectionEnd);

        var startIndex = getCodeUnitIndexForCodePoint(start);
        var endIndex = getCodeUnitIndexForCodePoint(end);

        return stringBuilder.substring(startIndex, endIndex);
    }

    private void selectAll() {
        selectionStart = 0;
        selectionEnd = stringBuilder.codePointCount(0, stringBuilder.length());
        caretPos = selectionEnd;
        hasSelection = true;
    }

    private void copyToClipboard() {
        var selectedText = getSelectedText();
        if (!selectedText.isEmpty()) {
            Minecraft.getInstance().keyboardHandler.setClipboard(selectedText);
        }
    }

    private void pasteFromClipboard() {
        var clipboardText = Minecraft.getInstance().keyboardHandler.getClipboard();
        if (!clipboardText.isEmpty()) {
            if (hasSelection) {
                deleteSelectedText();
            }

            var remainingCapacity = maxLength - stringBuilder.codePointCount(0, stringBuilder.length());
            if (remainingCapacity <= 0) return;

            var textToInsert = clipboardText;
            var textCodePoints = clipboardText.codePointCount(0, clipboardText.length());
            if (textCodePoints > remainingCapacity) {
                var endIndex = clipboardText.offsetByCodePoints(0, remainingCapacity);
                textToInsert = clipboardText.substring(0, endIndex);
            }

            if (!allowLineBreak) {
                textToInsert = textToInsert.replaceAll("[\\r\\n]+", "");
            }

            var potentialText = new StringBuilder(stringBuilder).insert(getCodeUnitIndexForCodePoint(caretPos), textToInsert).toString();
            if (inputValidator == null || inputValidator.test(potentialText)) {
                stringBuilder.insert(getCodeUnitIndexForCodePoint(caretPos), textToInsert);
                caretPos += textToInsert.codePointCount(0, textToInsert.length());
                clearSelection();
                updateTextComponent();
            }
        }
    }

    private void cutToClipboard() {
        copyToClipboard();
        deleteSelectedText();
    }

    @Override
    public boolean canFocus() {
        return true;
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

    public TextBoxWidget setAllowLineBreak(boolean allowLineBreak) {
        this.allowLineBreak = allowLineBreak;
        return this;
    }

    public boolean getAllowLineBreak() {
        return allowLineBreak;
    }

    public static final class FocusGainedEvent extends Event implements ICancellableEvent {
        public final TextBoxWidget textBoxWidget;

        public FocusGainedEvent(TextBoxWidget widget) {textBoxWidget = widget;}
    }

    public static final class FocusLostEvent extends Event implements ICancellableEvent {
        public final TextBoxWidget textBoxWidget;

        public FocusLostEvent(TextBoxWidget widget) {textBoxWidget = widget;}
    }
}