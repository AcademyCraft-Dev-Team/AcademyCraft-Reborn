package org.academy.api.client.gui.widget;

import net.minecraft.util.Mth;
import org.academy.api.client.gui.event.KeyEvent;
import org.academy.api.client.gui.event.MouseEvent;
import org.academy.api.client.gui.layout.Orientation;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

public class SeekBarWidget extends ProgressBarWidget {
    protected int keyProgressIncrement = 1;
    protected boolean isDragging = false;

    @Nullable
    private OnSeekBarChangeListener onSeekBarChangeListener;

    public interface OnSeekBarChangeListener {
        void onProgressChanged(SeekBarWidget seekBar, float progress, boolean fromUser);

        void onStartTrackingTouch(SeekBarWidget seekBar);

        void onStopTrackingTouch(SeekBarWidget seekBar);
    }

    public SeekBarWidget() {
        setClickable(true);
    }

    @Override
    protected void onMousePressed(MouseEvent event) {
        if (isMouseOver(event.getX(), event.getY())) {
            event.consume();
            isDragging = true;
            if (onSeekBarChangeListener != null) {
                onSeekBarChangeListener.onStartTrackingTouch(this);
            }
            updateProgressFromCoord(event.getX(), event.getY());
        }
    }

    @Override
    protected void onMouseDragged(MouseEvent event) {
        if (isDragging) {
            event.consume();
            updateProgressFromCoord(event.getX(), event.getY());
        }
    }

    @Override
    protected void onMouseReleased(MouseEvent event) {
        if (isDragging) {
            event.consume();
            isDragging = false;
            if (onSeekBarChangeListener != null) {
                onSeekBarChangeListener.onStopTrackingTouch(this);
            }
        }
    }

    @Override
    protected void onKeyReleased(KeyEvent event) {
        var oldProgress = getProgress();
        var handled = false;
        var keyCode = event.getKeyCode();

        if (getOrientation() == Orientation.HORIZONTAL) {
            if (keyCode == GLFW.GLFW_KEY_LEFT) {
                setProgress(getProgress() - keyProgressIncrement);
                handled = true;
            } else if (keyCode == GLFW.GLFW_KEY_RIGHT) {
                setProgress(getProgress() + keyProgressIncrement);
                handled = true;
            }
        } else {
            if (keyCode == GLFW.GLFW_KEY_DOWN) {
                setProgress(getProgress() - keyProgressIncrement);
                handled = true;
            } else if (keyCode == GLFW.GLFW_KEY_UP) {
                setProgress(getProgress() + keyProgressIncrement);
                handled = true;
            }
        }

        if (handled && oldProgress != getProgress() && onSeekBarChangeListener != null) {
            onSeekBarChangeListener.onProgressChanged(this, getProgress(), true);
        }
    }

    private void updateProgressFromCoord(double mouseX, double mouseY) {
        double ratio;
        if (getOrientation() == Orientation.HORIZONTAL) {
            var relativeX = mouseX - getAbsoluteX();
            var trackWidth = getWidth();
            var progressX = Mth.clamp(relativeX, 0, trackWidth);
            ratio = trackWidth > 0 ? progressX / trackWidth : 0;
        } else {
            var relativeY = mouseY - getAbsoluteY();
            var trackHeight = getHeight();
            var progressY = Mth.clamp(relativeY, 0, trackHeight);
            ratio = trackHeight > 0 ? 1.0 - (progressY / trackHeight) : 0;
        }

        var newProgress = getMin() + ratio * (getMax() - getMin());

        var oldProgress = getProgress();
        super.setProgress((float) newProgress);

        if (oldProgress != getProgress() && onSeekBarChangeListener != null) {
            onSeekBarChangeListener.onProgressChanged(this, getProgress(), true);
        }
    }

    @Override
    public ProgressBarWidget setProgress(float progress) {
        var oldProgress = getProgress();
        super.setProgress(progress);
        if (oldProgress != getProgress() && onSeekBarChangeListener != null) {
            onSeekBarChangeListener.onProgressChanged(this, getProgress(), false);
        }
        return this;
    }

    public int getKeyProgressIncrement() {
        return keyProgressIncrement;
    }

    public SeekBarWidget setKeyProgressIncrement(int keyProgressIncrement) {
        this.keyProgressIncrement = keyProgressIncrement;
        return this;
    }

    @Nullable
    public OnSeekBarChangeListener getOnSeekBarChangeListener() {
        return onSeekBarChangeListener;
    }

    public SeekBarWidget setOnSeekBarChangeListener(@Nullable OnSeekBarChangeListener onSeekBarChangeListener) {
        this.onSeekBarChangeListener = onSeekBarChangeListener;
        return this;
    }
}