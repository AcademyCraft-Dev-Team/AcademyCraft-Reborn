package org.academy.api.client.gui.animation;

public final class EasingFunctions {
    public static final TimeInterpolator LINEAR = input -> input;

    public static final TimeInterpolator EASE_IN_SINE = input ->
            1 - (float) Math.cos((input * Math.PI) / 2);

    public static final TimeInterpolator EASE_OUT_SINE = input ->
            (float) Math.sin((input * Math.PI) / 2);

    public static final TimeInterpolator EASE_IN_OUT_SINE = input ->
            -0.5f * ((float) Math.cos(Math.PI * input) - 1);

    public static final TimeInterpolator EASE_IN_QUAD = input ->
            input * input;

    public static final TimeInterpolator EASE_OUT_QUAD = input ->
            1 - (1 - input) * (1 - input);

    public static final TimeInterpolator EASE_IN_OUT_QUAD = input ->
            input < 0.5f ? 2 * input * input : 1 - (float) Math.pow(-2 * input + 2, 2) / 2;

    public static final TimeInterpolator EASE_IN_CUBIC = input ->
            input * input * input;

    public static final TimeInterpolator EASE_OUT_CUBIC = input ->
            1 - (float) Math.pow(1 - input, 3);

    public static final TimeInterpolator EASE_IN_OUT_CUBIC = input ->
            input < 0.5f ? 4 * input * input * input : 1 - (float) Math.pow(-2 * input + 2, 3) / 2;

    public static final TimeInterpolator EASE_IN_EXPO = input ->
            input == 0 ? 0 : (float) Math.pow(2, 10 * input - 10);

    public static final TimeInterpolator EASE_OUT_EXPO = input ->
            input == 1 ? 1 : 1 - (float) Math.pow(2, -10 * input);

    public static final TimeInterpolator EASE_IN_OUT_EXPO = input ->
            input == 0 ? 0 : input == 1 ? 1 :
                    input < 0.5f ? (float) Math.pow(2, 20 * input - 10) / 2 :
                            (2 - (float) Math.pow(2, -20 * input + 10)) / 2;

    public static final TimeInterpolator EASE_IN_BACK = input -> {
        final var c1 = 1.70158f;
        final var c3 = c1 + 1;
        return c3 * input * input * input - c1 * input * input;
    };

    public static final TimeInterpolator EASE_OUT_BACK = input -> {
        final var c1 = 1.70158f;
        final var c3 = c1 + 1;
        return 1 + c3 * (float) Math.pow(input - 1, 3) + c1 * (float) Math.pow(input - 1, 2);
    };

    public static final TimeInterpolator EASE_IN_OUT_BACK = input -> {
        final var c1 = 1.70158f;
        final var c2 = c1 * 1.525f;
        return input < 0.5f ?
                ((float) Math.pow(2 * input, 2) * ((c2 + 1) * 2 * input - c2)) / 2 :
                ((float) Math.pow(2 * input - 2, 2) * ((c2 + 1) * (input * 2 - 2) + c2) + 2) / 2;
    };

    public static final TimeInterpolator EASE_IN_ELASTIC = input -> {
        final var c4 = (float) (2 * Math.PI) / 3;
        return input == 0 ? 0 : input == 1 ? 1 :
                -(float) Math.pow(2, 10 * input - 10) * (float) Math.sin((input * 10 - 10.75) * c4);
    };

    public static final TimeInterpolator EASE_OUT_ELASTIC = input -> {
        final var c4 = (float) (2 * Math.PI) / 3;
        return input == 0 ? 0 : input == 1 ? 1 :
                (float) Math.pow(2, -10 * input) * (float) Math.sin((input * 10 - 0.75) * c4) + 1;
    };

    public static final TimeInterpolator EASE_IN_OUT_ELASTIC = input -> {
        final var c5 = (float) (2 * Math.PI) / 4.5f;
        var sin = Math.sin((20 * input - 11.125) * c5);
        return input == 0 ? 0 : input == 1 ? 1 :
                input < 0.5f ?
                        -((float) Math.pow(2, 20 * input - 10) * (float) Math.sin((20 * input - 11.125) * c5)) / 2 :
                        ((float) Math.pow(2, -20 * input + 10) * (float) sin) / 2 + 1;
    };

    public static TimeInterpolator createExpoOut(float strength) {
        return input -> input == 1 ? 1 : 1 - (float) Math.pow(2, -strength * input);
    }

    public static final TimeInterpolator EASE_OUT_AC = createExpoOut(6.0f);

    private EasingFunctions() {}
}