package org.academy.internal.client.renderer.entity.layers.quantum;

public class QuantumRenderContext {
    private static final ThreadLocal<State> STATE = ThreadLocal.withInitial(State::new);

    public static void setActive(boolean active, float time, float intensity) {
        State s = STATE.get();
        s.active = active;
        s.time = time;
        s.intensity = intensity;
    }

    public static boolean isActive() {
        return STATE.get().active;
    }

    public static float getTime() {
        return STATE.get().time;
    }

    public static float getIntensity() {
        return STATE.get().intensity;
    }

    private static class State {
        boolean active = false;
        float time = 0;
        float intensity = 0;
    }
}
