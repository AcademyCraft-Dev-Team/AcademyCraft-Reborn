package org.academy.internal.common.structure.physics.validation;

import org.academy.internal.common.structure.physics.BodyType;
import org.academy.internal.common.structure.physics.NativePhysicsLibrary;
import org.academy.internal.common.structure.physics.PhysicsBox;
import org.academy.internal.common.structure.physics.PhysicsPose;
import org.academy.internal.common.structure.physics.PhysicsQuaternion;
import org.academy.internal.common.structure.physics.PhysicsVector;

import java.io.IOException;
import java.util.List;

/** Small real solver check shared by the opt-in mod loading probes; never touches a game world. */
final class NativePhysicsSmokeChecks {
    private NativePhysicsSmokeChecks() {}

    static String run() throws IOException {
        var ownerModule = NativePhysicsLibrary.class.getModule();
        require(ownerModule.isNamed() && "academy".equals(ownerModule.getName()),
                "Mod loading probe must execute its owner in the academy game module");
        String bindingModule;
        boolean bindingNativeAccess;
        double height;
        double velocity;
        try (var library = NativePhysicsLibrary.openConfigured();
             var world = library.createWorld(PhysicsVector.ZERO, new PhysicsVector(0, -9.81, 0))) {
            bindingModule = library.bindingModuleName();
            bindingNativeAccess = library.bindingNativeAccessEnabled();
            require("ALL-UNNAMED".equals(bindingModule) && bindingNativeAccess,
                    "FFM binding must run in an explicitly authorized unnamed module");
            var floor = world.createBody(BodyType.FIXED, pose(0, -0.5, 0), boxes(8, 0.5, 8), 1, 0.6, 0, false);
            var falling = world.createBody(BodyType.DYNAMIC, pose(0, 4, 0), boxes(0.5, 0.5, 0.5), 1, 0.6, 0, true);
            for (int step = 0; step < 60; step++) world.step(1.0 / 120.0, 1);
            double intermediate = falling.pose().position().y();
            require(intermediate > 2 && intermediate < 3.1 && falling.linearVelocity().y() < -4,
                    "Real solver did not apply gravity");
            for (int step = 0; step < 660; step++) world.step(1.0 / 120.0, 1);
            height = falling.pose().position().y();
            velocity = falling.linearVelocity().y();
            require(Double.isFinite(height) && Math.abs(height - 0.5) < 0.05 && Math.abs(velocity) < 0.05,
                    "Real solver failed its static floor contact");
            require(floor.pose().position().y() == -0.5, "Fixed floor moved");
            require(falling.isCcdEnabled(), "CCD creation flag was lost");
        }
        return "owner_module=" + ownerModule.getName()
                + "\nbinding_module=" + bindingModule + "\nbinding_native_access=" + bindingNativeAccess
                + "\nsteps=720\nheight=" + height + "\nvertical_velocity=" + velocity
                + "\nclosed=true\n";
    }

    private static PhysicsPose pose(double x, double y, double z) {
        return new PhysicsPose(new PhysicsVector(x, y, z), PhysicsQuaternion.IDENTITY);
    }

    private static List<PhysicsBox> boxes(double x, double y, double z) {
        return List.of(new PhysicsBox(PhysicsVector.ZERO, PhysicsQuaternion.IDENTITY, new PhysicsVector(x, y, z)));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
