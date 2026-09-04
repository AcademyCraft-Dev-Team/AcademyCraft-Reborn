package org.academy.api.client.render.vfxgraph.serialize;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AeromanipBladeAssetTest {
    private static final String GRAPH_PATH = "/assets/academy/vfxgraph/aeromanip_mist_blade.json";

    @Test
    void bladeUsesFineNeutralParticlesAndDedicatedMaskShaders() {
        var graphStream = getClass().getResourceAsStream(GRAPH_PATH);
        assertNotNull(graphStream);
        var graph = JsonParser.parseReader(
                new InputStreamReader(graphStream, StandardCharsets.UTF_8)).getAsJsonObject();

        var spawn = context(graph, "SPAWN").getAsJsonArray("blocks");
        var particleCount = 0;
        var maximumSize = 0.0;
        var maximumHalfThickness = 0.0;
        var hasLeftFlow = false;
        var hasRightFlow = false;
        var layers = new HashSet<String>();
        for (var element : spawn) {
            var properties = element.getAsJsonObject().getAsJsonObject("properties");
            particleCount += properties.get("count").getAsInt();
            maximumSize = Math.max(maximumSize, properties.get("size").getAsDouble());
            maximumHalfThickness = Math.max(
                    maximumHalfThickness, properties.get("half_z").getAsDouble());
            var velocityX = properties.get("vx").getAsDouble();
            hasLeftFlow |= velocityX < 0.0;
            hasRightFlow |= velocityX > 0.0;
            layers.add(properties.get("layer").getAsString());

            var rgba = color(properties.get("color").getAsString());
            var darkest = Math.min(rgba[0], Math.min(rgba[1], rgba[2]));
            var lightest = Math.max(rgba[0], Math.max(rgba[1], rgba[2]));
            assertTrue(lightest - darkest <= 0.04,
                    "air-blade particles should remain neutral pale gray");
        }

        assertTrue(particleCount >= 200, "small particles need enough density to read as gas");
        assertTrue(maximumSize <= 0.08, "blade particles must not regress to large fog cards");
        assertTrue(maximumHalfThickness <= 0.06, "the cutting plane must stay visually thin");
        assertTrue(hasLeftFlow && hasRightFlow, "the horizontal blade should peel away in both directions");
        assertEquals(Set.of("blade", "mist", "grain"), layers);

        var output = context(graph, "OUTPUT").getAsJsonArray("blocks");
        assertEquals(3, output.size());
        for (var element : output) {
            var properties = element.getAsJsonObject().getAsJsonObject("properties");
            assertShaderExists(properties.get("vertex").getAsString(), ".vsh");
            assertShaderExists(properties.get("shader").getAsString(), ".fsh");
        }
    }

    private static JsonObject context(JsonObject graph, String type) {
        JsonArray contexts = graph.getAsJsonArray("contexts");
        for (var element : contexts) {
            var context = element.getAsJsonObject();
            if (type.equals(context.get("type").getAsString())) return context;
        }
        throw new AssertionError("missing context " + type);
    }

    private static double[] color(String value) {
        var channels = value.split(",");
        return new double[]{
                Double.parseDouble(channels[0]),
                Double.parseDouble(channels[1]),
                Double.parseDouble(channels[2]),
                Double.parseDouble(channels[3])
        };
    }

    private void assertShaderExists(String id, String extension) {
        var separator = id.indexOf(':');
        var namespace = id.substring(0, separator);
        var path = id.substring(separator + 1);
        assertNotNull(getClass().getResourceAsStream(
                "/assets/" + namespace + "/shaders/" + path + extension),
                "missing shader resource " + id + extension);
    }
}
