package org.academy.api.common.entitycontrol;

public interface ControlBinding extends AutoCloseable {
    ControlBinding NOOP = new ControlBinding() {
        @Override
        public void tick() {
        }

        @Override
        public void close() {
        }
    };

    void tick();

    @Override
    void close();

    static ControlBinding noop() {
        return NOOP;
    }
}
