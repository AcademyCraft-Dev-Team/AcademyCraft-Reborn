package org.academy.api.client.render.vfxgraph.nodes;

import org.academy.api.client.render.vfxgraph.shape.BoxShape;
import org.academy.api.client.render.vfxgraph.shape.CircleEdgeShape;
import org.academy.api.client.render.vfxgraph.shape.ConeShape;
import org.academy.api.client.render.vfxgraph.shape.CylinderShape;
import org.academy.api.client.render.vfxgraph.shape.DiscShape;
import org.academy.api.client.render.vfxgraph.shape.EmitterShape;
import org.academy.api.client.render.vfxgraph.shape.MeshAssets;
import org.academy.api.client.render.vfxgraph.shape.MeshShape;
import org.academy.api.client.render.vfxgraph.shape.PointShape;
import org.academy.api.client.render.vfxgraph.shape.SphereShape;
import org.academy.api.client.render.vfxgraph.shape.TorusShape;

import java.util.Map;

final class BlockProps {
    private BlockProps() {
    }

    static float getFloat(Map<String, String> properties, String id, float def) {
        var v = properties.get(id);
        return v == null ? def : Float.parseFloat(v);
    }

    static int getInt(Map<String, String> properties, String id, int def) {
        var v = properties.get(id);
        return v == null ? def : Integer.parseInt(v);
    }

    static boolean getBool(Map<String, String> properties, String id, boolean def) {
        var v = properties.get(id);
        return v == null ? def : Boolean.parseBoolean(v);
    }

    static String getString(Map<String, String> properties, String id, String def) {
        return properties.getOrDefault(id, def);
    }

    static float[] getColor(Map<String, String> properties, String id) {
        var csv = properties.get(id);
        if (csv == null) return new float[]{1f, 1f, 1f, 1f};
        var parts = csv.split(",");
        return new float[]{
                parts.length > 0 ? Float.parseFloat(parts[0].trim()) : 1f,
                parts.length > 1 ? Float.parseFloat(parts[1].trim()) : 1f,
                parts.length > 2 ? Float.parseFloat(parts[2].trim()) : 1f,
                parts.length > 3 ? Float.parseFloat(parts[3].trim()) : 1f
        };
    }

    static EmitterShape buildShape(Map<String, String> properties) {
        var ox = getFloat(properties, "origin_x", 0f);
        var oy = getFloat(properties, "origin_y", 0f);
        var oz = getFloat(properties, "origin_z", 0f);
        var scale = getFloat(properties, "mesh_scale", 1f);
        return switch (getString(properties, "shape", "point")) {
            case "sphere" -> new SphereShape(ox, oy, oz, getFloat(properties, "radius", 1f));
            case "box" -> new BoxShape(ox, oy, oz,
                    getFloat(properties, "half_x", 1f),
                    getFloat(properties, "half_y", 1f),
                    getFloat(properties, "half_z", 1f));
            case "cone" -> new ConeShape(ox, oy, oz,
                    getFloat(properties, "radius", 1f),
                    getFloat(properties, "cone_height", 2f));
            case "cylinder" -> new CylinderShape(ox, oy, oz,
                    getFloat(properties, "radius", 1f),
                    getFloat(properties, "cone_height", 2f));
            case "torus" -> new TorusShape(ox, oy, oz,
                    getFloat(properties, "radius", 1f),
                    getFloat(properties, "half_x", 0.25f));
            case "circle_edge" -> new CircleEdgeShape(ox, oy, oz, getFloat(properties, "radius", 1f));
            case "disc" -> new DiscShape(ox, oy, oz, getFloat(properties, "radius", 1f));
            case "mesh" -> meshShape(properties, ox, oy, oz, scale);
            default -> new PointShape(ox, oy, oz);
        };
    }

    private static EmitterShape meshShape(Map<String, String> properties, float ox, float oy, float oz, float scale) {
        var id = getString(properties, "mesh", "");
        var triangles = id.isEmpty() ? null : MeshAssets.triangles(id);
        return triangles == null
                ? MeshShape.unitCube(ox, oy, oz, scale)
                : new MeshShape(ox, oy, oz, scale, triangles);
    }
}
