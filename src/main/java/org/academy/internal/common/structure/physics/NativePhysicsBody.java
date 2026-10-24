package org.academy.internal.common.structure.physics;

/** A world-owned handle; closing its world permanently invalidates this object. */
public final class NativePhysicsBody implements AutoCloseable {
    private final NativePhysicsWorld world;
    final long handle;
    final BodyType type;
    final int boxCount;
    private final boolean ccd;
    boolean removed;
    boolean simulationPaused;

    NativePhysicsBody(NativePhysicsWorld world, long handle, BodyType type, int boxCount, boolean ccd) {
        this.world = world;
        this.handle = handle;
        this.type = type;
        this.boxCount = boxCount;
        this.ccd = ccd;
    }

    /** While paused, velocity and sleeping describe the saved motion restored on resume. */
    public PhysicsBodyState state() { return world.state(this); }
    public PhysicsPose pose() { return state().pose(); }
    public PhysicsVector linearVelocity() { return state().linearVelocity(); }
    public PhysicsVector angularVelocity() { return state().angularVelocity(); }
    public double mass() { return state().mass(); }
    public PhysicsVector localCenterOfMass() { return state().localCenterOfMass(); }
    public PhysicsVector principalInertia() { return state().principalInertia(); }
    public boolean isSleeping() { return state().sleeping(); }
    /** Explicit per-body request; Rapier also protects fast dynamic bodies against fixed geometry automatically. */
    public boolean isCcdEnabled() { world.requireBody(this); return ccd; }
    public void setPose(PhysicsPose pose) { world.setPose(this, pose); }
    /** Later steps retain Rapier's speed/rotation limits, including its default 400 units/second linear cap. */
    public void setVelocity(PhysicsVector linear, PhysicsVector angular) { world.setVelocity(this, linear, angular); }
    public void applyImpulse(PhysicsVector impulse, PhysicsVector worldPoint) { world.impulse(this, impulse, worldPoint); }
    public void sleep() { world.sleep(this); }
    public void wake() { world.wake(this); }
    /**
     * Freezes a dynamic body as a collidable obstacle without replacing its handle or joints.
     * Resume restores its saved velocity and sleeping state. Calls with the same flag are idempotent.
     * Pause every member of an assembly before stepping; this method affects only this body.
     * Velocity, impulse, sleep and wake commands are rejected while paused; explicit setPose remains allowed.
     */
    public void setSimulationPaused(boolean paused) { world.setSimulationPaused(this, paused); }
    public boolean isSimulationPaused() { world.requireBody(this); return simulationPaused; }

    @Override
    public void close() { world.remove(this); }
}
