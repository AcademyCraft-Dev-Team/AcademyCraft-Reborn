package org.academy.internal.client.compatibility;

import net.neoforged.fml.ModList;
import org.academy.AcademyCraft;

/** Compatibility bootstrap for Inventory Profiles Next 2.3.5 on Minecraft 26.2. */
public final class InventoryProfilesNextCompat {
    private static final String MOD_ID = "inventoryprofilesnext";
    private static final String CONFIG_ROOT = "org.anti_ad.mc.ipnext.config.ConfigScreenSettings";

    private InventoryProfilesNextCompat() {
    }

    public static void initialize() {
        if (!ModList.get().isLoaded(MOD_ID)) return;
        try {
            // IPN's input hook can initialize LockedSlotsSettings first. That enters
            // a Kotlin static-initializer cycle and observes Features delegates before
            // they are assigned. Initializing the intended config root first breaks
            // that cycle because ConfigScreenSettings.INSTANCE is assigned up front.
            Class.forName(CONFIG_ROOT, true, InventoryProfilesNextCompat.class.getClassLoader());
        } catch (ClassNotFoundException exception) {
            AcademyCraft.getLogger().warn(
                    "Inventory Profiles Next is loaded without its expected config bootstrap; compatibility init skipped"
            );
        }
    }
}
