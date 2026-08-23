package org.academy.internal.common.ability;

import org.academy.AcademyCraftServer;
import org.academy.api.server.wireless.WirelessManager;
import org.academy.internal.common.ability.mentalout.precision.PrecisionOperationManager;
import org.academy.internal.common.ability.program.AbilityProgramManager;
import org.academy.internal.common.network.MusicSyncPackets;
import org.academy.internal.common.world.damagesource.DestroyBlocksSetting;
import org.academy.internal.common.world.damagesource.FriendlyFireSetting;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DedicatedServerInitializerIsolationTest {
    private static final List<Class<?>> SERVER_INITIALIZERS = List.of(
            AcademyCraftServer.class,
            WirelessManager.class,
            FriendlyFireSetting.class,
            DestroyBlocksSetting.class,
            ProficiencySkillSettings.class,
            MusicSyncPackets.class,
            AbilityProgramManager.class,
            PrecisionOperationManager.class
    );
    private static final List<String> CLIENT_CLASS_PREFIXES = List.of(
            "net/minecraft/client/",
            "org/academy/api/client/",
            "org/academy/internal/client/",
            "org/misaka/MisakaNetworkClient"
    );

    @Test
    void serverInitializersHaveNoClientClassReferences() throws IOException {
        for (var initializer : SERVER_INITIALIZERS) {
            var resourceName = "/" + initializer.getName().replace('.', '/') + ".class";
            try (var stream = initializer.getResourceAsStream(resourceName)) {
                assertNotNull(stream);
                var classFile = new String(stream.readAllBytes(), StandardCharsets.ISO_8859_1);
                for (var clientClassPrefix : CLIENT_CLASS_PREFIXES) {
                    assertFalse(
                            classFile.contains(clientClassPrefix),
                            () -> initializer.getName() + " references client class " + clientClassPrefix
                    );
                }
            }
        }
    }
}
