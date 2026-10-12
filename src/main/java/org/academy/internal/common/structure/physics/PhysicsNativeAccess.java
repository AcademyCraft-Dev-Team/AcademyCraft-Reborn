package org.academy.internal.common.structure.physics;

/** Primitive-only transport boundary, also allowing lifecycle tests without loading native code. */
public interface PhysicsNativeAccess extends AutoCloseable {
    long createWorld(double[] gravity);
    void destroyWorld(long world);
    void step(long world, double duration, int substeps);
    long createBody(long world, int kind, double[] description, double[] boxes);
    double[] state(long world, long body);
    void setPose(long world, long body, double[] pose);
    void setVelocity(long world, long body, double[] velocity);
    void impulse(long world, long body, double[] impulseAndPoint);
    void sleep(long world, long body);
    void wake(long world, long body);
    void removeBody(long world, long body);

    @Override
    default void close() {}
}
