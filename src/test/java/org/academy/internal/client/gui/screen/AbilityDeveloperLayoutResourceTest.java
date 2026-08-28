package org.academy.internal.client.gui.screen;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbilityDeveloperLayoutResourceTest {
    private static final String RESOURCE_PATH =
            "/assets/academy/gui/ability_developer_gui_layout.txt";
    private static final float MAX_NODE_X = 257.0f - 16.0f;
    private static final float MAX_NODE_Y = 139.0f - 16.0f;

    @Test
    void everySkillNodeFitsInsideTheDeveloperTreeArea() throws Exception {
        var input = getClass().getResourceAsStream(RESOURCE_PATH);
        assertNotNull(input, "Missing developer skill layout resource");

        try (var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String category = "<none>";
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.strip();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (line.startsWith("[") && line.endsWith("]")) {
                    category = line.substring(1, line.length() - 1);
                    continue;
                }

                var equals = line.indexOf('=');
                var comma = line.indexOf(',', equals + 1);
                if (equals <= 0 || comma <= equals) continue;
                var skill = line.substring(0, equals);
                var x = Float.parseFloat(line.substring(equals + 1, comma));
                var y = Float.parseFloat(line.substring(comma + 1));
                var node = category + " / " + skill;

                assertTrue(x >= 0.0f && x <= MAX_NODE_X,
                        () -> node + " x coordinate is outside the tree: " + x);
                assertTrue(y >= 0.0f && y <= MAX_NODE_Y,
                        () -> node + " y coordinate is outside the tree: " + y);
                assertTrue(isHalfPixelSnapped(x) && isHalfPixelSnapped(y),
                        () -> node + " is not snapped to the 0.5 pixel grid");
            }
        }
    }

    private static boolean isHalfPixelSnapped(float value) {
        return Math.abs(value * 2.0f - Math.round(value * 2.0f)) < 0.0001f;
    }
}
