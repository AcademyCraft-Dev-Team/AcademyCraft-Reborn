package org.academy.api.common.vfx;

/** A bounded effect envelope whose direction can change without restarting from an endpoint. */
public final class EffectScalarTransition {
    private float startValue;
    private float target;
    private float started;
    private float duration = 1;

    public EffectScalarTransition(float initialValue) {
        if (!Float.isFinite(initialValue)) throw new IllegalArgumentException("Non-finite effect envelope");
        startValue = target = Math.clamp(initialValue, 0, 1);
    }

    /** Uses a monotonic presentation clock; pauses need no additional state or timer. */
    public float update(float requestedTarget, float time, float seconds) {
        if (!Float.isFinite(requestedTarget) || !Float.isFinite(time) || time < 0
                || !Float.isFinite(seconds) || seconds <= 0) {
            throw new IllegalArgumentException("Invalid effect transition");
        }
        float next = Math.clamp(requestedTarget, 0, 1);
        if (next != target || seconds != duration) {
            startValue = sample(time);
            target = next;
            started = time;
            duration = seconds;
        }
        return sample(time);
    }

    private float sample(float time) {
        float progress = Math.clamp((time - started) / duration, 0, 1);
        float smooth = progress * progress * (3 - 2 * progress);
        return Math.clamp(startValue + (target - startValue) * smooth, 0, 1);
    }
}
