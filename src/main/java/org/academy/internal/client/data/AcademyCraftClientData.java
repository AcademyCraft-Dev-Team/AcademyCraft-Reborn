package org.academy.internal.client.data;

import net.neoforged.neoforge.data.event.GatherDataEvent;

public final class AcademyCraftClientData {
    public static void dataSetup(GatherDataEvent.Client event) {
        event.createProvider(AcademyCraftModelProvider::new);
        event.createProvider(output -> new AcademyCraftDatapackProvider(output, event.getLookupProvider()));
        event.createProvider(AcademyCraftRecipeProvider.Runner::new);
    }

    private AcademyCraftClientData() {
    }
}