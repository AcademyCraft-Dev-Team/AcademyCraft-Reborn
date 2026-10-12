package org.academy.internal.common.structure.physics;

/** Explicit C ABI failure, never a simulated success or a fallback to another backend. */
public final class NativePhysicsException extends IllegalStateException {
    private final int status;

    public NativePhysicsException(String operation, int status) {
        super("Native physics " + operation + " failed with status " + status + " (" + description(status) + ")");
        this.status = status;
    }

    public int status() {
        return status;
    }

    private static String description(int status) {
        return switch (status) {
            case 1 -> "invalid argument";
            case 2 -> "invalid handle";
            case 3 -> "resource limit";
            case 4 -> "buffer size mismatch";
            case 5 -> "world failed";
            case 6 -> "native panic";
            default -> "unknown ABI error";
        };
    }
}
