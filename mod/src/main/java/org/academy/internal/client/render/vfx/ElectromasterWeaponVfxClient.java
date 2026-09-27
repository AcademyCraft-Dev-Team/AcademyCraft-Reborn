package org.academy.internal.client.render.vfx;

public final class ElectromasterWeaponVfxClient {
    private ElectromasterWeaponVfxClient() {
    }

    private static boolean registered;

    public static void register() {
        if (registered) return;
        registered = true;
        IronSandVfxClient.clear();
    }
}
