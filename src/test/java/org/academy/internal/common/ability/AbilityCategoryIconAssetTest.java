package org.academy.internal.common.ability;

import net.minecraft.resources.Identifier;
import org.academy.api.client.resources.R;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.internal.common.ability.accelerator.Accelerator;
import org.academy.internal.common.ability.aeromanip.Aeromanip;
import org.academy.internal.common.ability.electromaster.Electromaster;
import org.academy.internal.common.ability.meltdowner.Meltdowner;
import org.academy.internal.common.ability.mentalout.Mentalout;
import org.academy.internal.common.ability.teleport.Teleport;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbilityCategoryIconAssetTest {
    @Test
    void developerUsesEachCategoryCanonicalSixtyFourPixelIcon() throws Exception {
        var expectations = List.of(
                new IconExpectation(new Aeromanip(), R.textures.ICON_AEROMANIP),
                new IconExpectation(new Accelerator(), R.textures.ability.accelerator.icon),
                new IconExpectation(new Electromaster(), R.textures.ability.electromaster.icon),
                new IconExpectation(new Meltdowner(), R.textures.ability.meltdowner.icon),
                new IconExpectation(new Teleport(), R.textures.ability.teleport.icon),
                new IconExpectation(new Mentalout(), R.textures.ability.mentalout.icon)
        );

        for (var expectation : expectations) {
            assertEquals(expectation.icon(), expectation.category().getDeveloperIcon());
            var resourcePath = "/assets/" + expectation.icon().getNamespace()
                    + "/" + expectation.icon().getPath();
            try (var stream = getClass().getResourceAsStream(resourcePath)) {
                assertNotNull(stream, "missing category icon " + resourcePath);
                var image = ImageIO.read(stream);
                assertNotNull(image, "invalid PNG category icon " + resourcePath);
                assertEquals(64, image.getWidth(), "unexpected icon width " + resourcePath);
                assertEquals(64, image.getHeight(), "unexpected icon height " + resourcePath);
                assertTrue(image.getColorModel().hasAlpha(), "category icon must preserve transparency");
            }
        }
    }

    @Test
    void legacyGuiAliasesStayIdenticalToCanonicalCategoryIcons() throws Exception {
        var aliases = List.of(
                new IconAlias(R.textures.ability.accelerator.icon, R.textures.gui.icon.icon_accelerator),
                new IconAlias(R.textures.ability.electromaster.icon, R.textures.gui.icon.icon_electromaster),
                new IconAlias(R.textures.ability.meltdowner.icon, R.textures.gui.icon.icon_meltdowner),
                new IconAlias(R.textures.ability.teleport.icon, R.textures.gui.icon.icon_teleporter)
        );

        for (var alias : aliases) {
            assertArrayEquals(iconBytes(alias.canonical()), iconBytes(alias.legacy()),
                    "legacy category icon must match " + alias.canonical());
        }
    }

    private byte[] iconBytes(Identifier icon) throws Exception {
        var resourcePath = "/assets/" + icon.getNamespace() + "/" + icon.getPath();
        try (var stream = getClass().getResourceAsStream(resourcePath)) {
            assertNotNull(stream, "missing category icon " + resourcePath);
            return stream.readAllBytes();
        }
    }

    private record IconExpectation(AbilityCategory category, Identifier icon) {
    }

    private record IconAlias(Identifier canonical, Identifier legacy) {
    }
}
