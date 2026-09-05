package org.academy.internal.client.ability.mentalout;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import org.academy.api.common.entitycontrol.WorkSettings;
import java.util.*;

/** Last server acknowledgement; draft configuration remains screen-owned until explicitly loaded. */
public final class WorkOrderClientState {
    private static JsonObject snapshot = new JsonObject();
    private WorkOrderClientState() {}
    public static void accept(String json) {
        try { snapshot = JsonParser.parseString(json).getAsJsonObject(); }
        catch (RuntimeException ignored) { snapshot = new JsonObject(); }
    }
    public static Optional<WorkSettings> settings() {
        return snapshot.has("settings") ? WorkSettings.CODEC.parse(JsonOps.INSTANCE, snapshot.get("settings")).result() : Optional.empty();
    }
    public static Optional<BlockPos> position(String name) {
        return snapshot.has(name) ? BlockPos.CODEC.parse(JsonOps.INSTANCE, snapshot.get(name)).result() : Optional.empty();
    }
    public static String status(UUID id) {
        var states = snapshot.getAsJsonObject("states");
        return states != null && states.has(id.toString()) ? states.get(id.toString()).getAsString() : "none";
    }
    public static Optional<org.academy.api.common.entitycontrol.MiningWorkPlan.Progress> progress() {
        try {
            var value = snapshot.getAsJsonObject("progress");
            return value == null ? Optional.empty() : Optional.of(new org.academy.api.common.entitycontrol.MiningWorkPlan.Progress(
                    value.get("completed").getAsInt(), value.get("remaining").getAsInt(),
                    value.get("blocked").getAsInt(), value.get("active").getAsInt()));
        } catch (RuntimeException ignored) { return Optional.empty(); }
    }
    public static void clear() { snapshot = new JsonObject(); }
}
