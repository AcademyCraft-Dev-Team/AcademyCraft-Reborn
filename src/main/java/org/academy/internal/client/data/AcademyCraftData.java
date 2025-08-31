package org.academy.internal.client.data;

import net.neoforged.neoforge.data.event.GatherDataEvent;

public final class AcademyCraftData {
    public static void dataSetup(GatherDataEvent.Client event) {
        event.createProvider(AcademyCraftModelProvider::new);
    }

    private AcademyCraftData() {
    }
}