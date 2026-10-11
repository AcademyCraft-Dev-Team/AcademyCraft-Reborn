package org.academy.desktop.grapheditor.preview;

import com.google.gson.GsonBuilder;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.academy.api.client.render.vfxgraph.runtime.ActiveEffect;
import org.academy.api.client.render.vfxgraph.runtime.VfxGraphManager;
import org.academy.api.client.render.vfxgraph.render.GraphCamera;
import org.academy.internal.client.render.vfx.DarkmatterVfxClient;
import org.academy.internal.common.network.DarkmatterVisualPacket;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Independent-client GPU readback and continuous-attachment diagnostics. No local synthetic effects. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class DarkmatterReconstructionMultiplayerClient {
    private static final String ROLE = System.getProperty("academy.reconstructionMultiplayerRole", "");
    private static final List<Map<String, Object>> frames = new ArrayList<>();
    private static final List<Map<String, Object>> diagnostics = new ArrayList<>();
    private static final Set<String> observed = new HashSet<>();
    private static final Set<ActiveEffect> continuous = Collections.newSetFromMap(new IdentityHashMap<>());
    private static boolean ready, finished;
    private static int ticks, phaseTicks, pending, continuousSamples, centerHitBindingSamples;
    private static String phase = "loading";
    private static float minimumProgress = 1;
    private static float maximumProgress;
    private static final int[] BODY_PATCHES = {0, 2, 3};
    private static final float[] minimumPatchProgress = {1, 1, 1, 1, 1, 1};
    private static final float[] maximumPatchProgress = new float[6];
    private static final int[] bridgeSamples = new int[6];
    private static Path output() { return Path.of(System.getProperty("academy.reconstructionOutput")); }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (ROLE.isEmpty() || finished) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        try {
            if (!ready && ++ticks >= 100) {
                mc.options.pauseOnLostFocus = false; mc.options.enableVsync().set(false);
                mc.options.framerateLimit().set(120); mc.options.fov().set(50);
                mc.options.chatVisibility().set(ChatVisiblity.HIDDEN);
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                Files.createDirectories(output()); Files.writeString(output().resolve(ROLE + ".ready"), mc.player.getGameProfile().name());
                ready = true;
            }
            if (!ready) return;
            mc.gui.toastManager().clear();
            if (Files.exists(output().resolve("failure.txt"))) throw new IllegalStateException(Files.readString(output().resolve("failure.txt")));
            if (Files.exists(output().resolve("phase.txt"))) {
                var next = Files.readString(output().resolve("phase.txt")).trim();
                if (!next.isEmpty() && !next.equals(phase)) {
                    phase = next; phaseTicks = 0;
                    mc.options.setCameraType(ROLE.equals("observer") && (phase.equals("repair_stop") || phase.equals("disassemble_player"))
                            ? CameraType.THIRD_PERSON_FRONT : CameraType.FIRST_PERSON);
                }
            }
            phaseTicks++;
            var effects = VfxGraphManager.INSTANCE.activeEffects().stream().filter(e -> !e.isStopped()
                    && (e.assetKey().endsWith("darkmatter_repair") || e.assetKey().endsWith("darkmatter_disassemble"))).toList();
            for (var effect : effects) observed.add(effect.assetKey());
            var data = effects.stream().map(e -> Map.of("asset", e.assetKey(), "progress", e.effect().floatParameter("progress", -1),
                    "time", e.effect().floatParameter("time", -1), "slots", e.effect().buffer().count(),
                    "repairPatches", java.util.stream.IntStream.range(0, 6)
                            .mapToObj(i -> e.effect().floatParameter("repair_progress_" + i, -1)).toList())).toList();
            frames.add(Map.of("phase", phase, "phaseTick", phaseTicks, "graphs", data, "active", DarkmatterVfxClient.activeCount()));
            if (phase.equals("disassemble_player") && phaseTicks <= 12) captureDiagnostics();
            require(DarkmatterVfxClient.activeCount() <= 64, "admission limit");
            if (phase.equals("repair_continuous") && phaseTicks >= 20 && phaseTicks <= 65) {
                require(effects.size() == 1, "continuous repair has one attachment on " + ROLE);
                continuousSamples++;
                continuous.add(effects.getFirst());
                var graph = effects.getFirst().effect();
                float progress = graph.floatParameter("progress", -1);
                minimumProgress = Math.min(minimumProgress, progress);
                maximumProgress = Math.max(maximumProgress, progress);
                for (int patch : BODY_PATCHES) {
                    float value = graph.floatParameter("repair_progress_" + patch, -1);
                    require(Float.isFinite(value) && value >= .60f && value <= 1,
                            "BODY patch " + patch + " remains beyond initial growth on " + ROLE);
                    minimumPatchProgress[patch] = Math.min(minimumPatchProgress[patch], value);
                    maximumPatchProgress[patch] = Math.max(maximumPatchProgress[patch], value);
                    if (value >= .66f && value <= .72f) bridgeSamples[patch]++;
                }
                for (int patch : new int[]{1, 4, 5}) require(graph.floatParameter("repair_progress_" + patch, -1) == 1,
                        "BODY work does not create a patch on head or legs on " + ROLE);
            }
            if (!phase.equals("loading") && !phase.equals("done") && (phaseTicks <= 20 && phaseTicks % 2 == 0
                    || phaseTicks == 35 || phaseTicks == 55)) capture(phase + "/frame_" + phaseTicks);
            if (phase.equals("done") && pending == 0) {
                require(continuous.size() == 1 && minimumProgress >= .60f, "same graph and stable progress across TCP repair pulses");
                for (int patch : BODY_PATCHES) require(minimumPatchProgress[patch] <= .72f
                                && continuousSamples > 0 && bridgeSamples[patch] >= Math.ceil(continuousSamples * .5),
                        "BODY patch " + patch + " stays fully bridged for at least half of steady samples on " + ROLE);
                require(observed.stream().anyMatch(s -> s.endsWith("darkmatter_repair"))
                        && observed.stream().anyMatch(s -> s.endsWith("darkmatter_disassemble")), "both production events arrive at " + ROLE);
                require(effects.isEmpty() && DarkmatterVfxClient.activeCount() == 0, "all effects cleaned up on " + ROLE);
                require(centerHitBindingSamples > 0, "center-hit torso binding was actually sampled on " + ROLE);
                Files.writeString(output().resolve(ROLE + "-result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(Map.of(
                        "passed", true, "role", ROLE, "continuousGraphInstances", continuous.size(), "minimumProgress", minimumProgress,
                        "maximumProgress", maximumProgress, "continuousPatches", patchDiagnostics(), "continuousSamples", continuousSamples,
                        "centerHitBindingSamples", centerHitBindingSamples,
                        "network", "independent TCP game client connected to dedicated server", "frames", frames)));
                Files.writeString(output().resolve(ROLE + ".done"), "PASSED");
                finished = true; mc.stop();
            }
        } catch (Throwable failure) {
            try { Files.writeString(output().resolve("failure.txt"), ROLE + ": " + failure); } catch (Exception ignored) { }
            throw new IllegalStateException("Reconstruction multiplayer client failed", failure);
        }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    /** Read-only inspection of the completed frame. Never invokes surface binding or modifies production state. */
    private static void captureDiagnostics() throws Exception {
        var mc = Minecraft.getInstance();
        var camera = (GraphCamera) read(VfxGraphManager.INSTANCE, "lastCamera");
        var sample = new LinkedHashMap<String, Object>();
        sample.put("role", ROLE); sample.put("phase", phase); sample.put("phaseTick", phaseTicks);
        sample.put("cameraType", mc.options.getCameraType().toString()); sample.put("clientGameTime", mc.level.getGameTime());
        sample.put("sampling", "ClientTickEvent.Post; cached last completed visual frame and buffer, before screenshot request");
        if (camera != null) sample.put("camera", Map.of("position", vector(camera.position()),
                "viewRotation", matrix(camera.viewRotation()), "projection", matrix(camera.projection())));
        var effects = (Map<?, ?>) read(DarkmatterVfxClient.class, "EFFECTS");
        var visuals = new ArrayList<Map<String, Object>>();
        var invalidBindings = new ArrayList<String>();
        for (var visual : effects.values()) {
            var state = (DarkmatterVisualPacket) read(visual, "state");
            if (state.kind != DarkmatterVisualPacket.Kind.DISASSEMBLE || !(read(visual, "subject") instanceof Player subject)) continue;
            var active = (ActiveEffect) read(visual, "effect");
            var record = new LinkedHashMap<String, Object>();
            double frameTick = (Double) read(visual, "frameTick");
            float partial = (float) (frameTick - Math.floor(frameTick));
            record.put("subjectId", subject.getId()); record.put("localPlayer", subject == mc.player);
            record.put("subjectPosition", vector(subject.position().toVector3f()));
            record.put("subjectInterpolatedPosition", vector(subject.getPosition(partial).toVector3f()));
            record.put("subjectBodyYaw", subject.yBodyRot); record.put("subjectPreviousBodyYaw", subject.yBodyRotO);
            record.put("subjectHurtTime", subject.hurtTime); record.put("frameTick", frameTick); record.put("framePartial", partial);
            record.put("packetPosition", vector(state.position.toVector3f()));
            if (state.surfaceContact != null) record.put("packetContact", Map.of(
                    "localOffset", vector(state.surfaceContact.localOffset().toVector3f()), "normal", vector(state.surfaceContact.normal().toVector3f())));
            record.put("origin", vector(((net.minecraft.world.phys.Vec3) read(visual, "origin")).toVector3f()));
            for (var field : List.of("position", "contactPoint", "surfaceNormal", "viewPosition", "viewDirection"))
                record.put(field, vector((Vector3fc) read(visual, field)));
            for (var field : List.of("firstPerson", "posedAvailable", "contactBound", "contactPosed", "contactPart", "contactFace", "contactU", "contactV"))
                record.put(field, read(visual, field));
            if (active != null && !active.isStopped() && Boolean.TRUE.equals(read(visual, "contactBound"))
                    && Boolean.TRUE.equals(read(visual, "contactPosed"))) {
                centerHitBindingSamples++;
                int part = (Integer) read(visual, "contactPart");
                record.put("fixtureExpectedContactPart", 0);
                if (part != 0) invalidBindings.add("entity " + subject.getId() + " bound part " + part + " at phase tick " + phaseTicks);
            }
            record.put("modelRoot", matrix((Matrix4fc) read(visual, "modelRoot")));
            var registeredRoot = org.academy.internal.client.render.vfx.WingAvatarRegistry.entries().get(subject.getId());
            if (registeredRoot != null) record.put("registeredCameraRelativeRoot", matrix(registeredRoot));
            if (active != null) {
                record.put("activePosition", vector(active.position())); record.put("activeScale", active.scale());
                var rotation = active.rotation(); record.put("activeRotation", List.of(rotation.x(), rotation.y(), rotation.z(), rotation.w()));
                var posed = (Matrix4f[]) read(visual, "posed");
                var bones = (Matrix4f[]) read(visual, "bones");
                var mins = (Vector3f[]) read(visual, "bodyMin"); var maxes = (Vector3f[]) read(visual, "bodyMax");
                var parts = new ArrayList<Map<String, Object>>();
                boolean hasPose = Boolean.TRUE.equals(read(visual, "posedAvailable"));
                var rawContact = state.surfaceContact == null ? new Vector3f(0, state.height * .68f, -state.width * .5f)
                        : state.surfaceContact.localOffset().toVector3f();
                record.put("rawContactEffectLocal", vector(rawContact));
                int nearestNow = -1; float nearestDistance = Float.POSITIVE_INFINITY;
                for (int i = 0; i < posed.length; i++) {
                    var center = new Vector3f(mins[i]).add(maxes[i]).mul(.5f); posed[i].transformPosition(center);
                    var part = new LinkedHashMap<String, Object>();
                    part.put("index", i); part.put("min", vector(mins[i])); part.put("max", vector(maxes[i]));
                    part.put("posed", matrix(posed[i])); if (bones != null) part.put("bone", matrix(bones[i]));
                    part.put("centerEffectLocal", vector(center)); part.put("centerWorld", world(active, center.x, center.y, center.z));
                    if (hasPose) {
                        var closest = new Matrix4f(posed[i]).invert().transformPosition(new Vector3f(rawContact));
                        closest.max(mins[i]).min(maxes[i]); posed[i].transformPosition(closest);
                        float distance = closest.distanceSquared(rawContact);
                        part.put("closestToRawContactEffectLocal", vector(closest)); part.put("rawContactDistanceSquared", finite(distance));
                        if (distance < nearestDistance) { nearestDistance = distance; nearestNow = i; }
                    }
                    parts.add(part);
                }
                record.put("parts", parts);
                record.put("nearestPartNow", nearestNow);
                if (hasPose) record.put("nearestPartDistanceSquaredNow", finite(nearestDistance));
                var buffer = active.effect().buffer(); var slots = new ArrayList<Map<String, Object>>();
                for (int i = 0; i < buffer.count(); i++) if (buffer.alpha(i) > 0) {
                    var slot = new LinkedHashMap<String, Object>();
                    slot.put("index", i); slot.put("alpha", buffer.alpha(i)); slot.put("layer", buffer.layer(i));
                    slot.put("localPosition", List.of(buffer.positionX(i), buffer.positionY(i), buffer.positionZ(i)));
                    var position = world(active, buffer.positionX(i), buffer.positionY(i), buffer.positionZ(i)); slot.put("worldPosition", position);
                    if (camera != null) {
                        var ndc = new Vector3f(position[0], position[1], position[2]).sub(camera.position());
                        camera.viewRotation().transformPosition(ndc); camera.projection().transformProject(ndc); slot.put("ndc", vector(ndc));
                    }
                    slot.put("axis", List.of(buffer.velocityX(i), buffer.velocityY(i), buffer.velocityZ(i)));
                    slot.put("surfaceNormal", List.of(buffer.surfaceNormalX(i), buffer.surfaceNormalY(i), buffer.surfaceNormalZ(i)));
                    slot.put("size", buffer.size(i)); slot.put("motion", buffer.rotation(i)); slot.put("progress", buffer.age(i));
                    slot.put("detail", buffer.surfaceDetail(i)); slots.add(slot);
                }
                record.put("bufferCount", buffer.count()); record.put("visibleSlots", slots);
            }
            visuals.add(record);
        }
        sample.put("visuals", visuals); sample.put("centerHitBindingPassed", invalidBindings.isEmpty()); diagnostics.add(sample);
        Files.writeString(output().resolve(ROLE + "-diagnostics.json"), new GsonBuilder().setPrettyPrinting().create().toJson(diagnostics));
        require(invalidBindings.isEmpty(), "known center-abdomen fixture binds torso on " + ROLE + ": " + invalidBindings);
    }
    private static Object read(Object target, String name) throws ReflectiveOperationException {
        Class<?> type = target instanceof Class<?> owner ? owner : target.getClass();
        var field = type.getDeclaredField(name); field.setAccessible(true); return field.get(target instanceof Class<?> ? null : target);
    }
    private static float[] world(ActiveEffect effect, float x, float y, float z) {
        var result = new float[3]; effect.worldTransform().apply(x, y, z, result); return result;
    }
    private static List<Object> vector(Vector3fc value) { return List.of(finite(value.x()), finite(value.y()), finite(value.z())); }
    private static Object finite(float value) { return Float.isFinite(value) ? value : Float.toString(value); }
    private static float[] matrix(Matrix4fc value) { return value.get(new float[16]); }
    private static List<Map<String, Object>> patchDiagnostics() {
        return java.util.Arrays.stream(BODY_PATCHES).mapToObj(patch -> Map.<String, Object>of(
                "patch", patch, "minimum", minimumPatchProgress[patch], "maximum", maximumPatchProgress[patch],
                "bridgeSamples", bridgeSamples[patch], "bridgeRatio", (double) bridgeSamples[patch] / continuousSamples)).toList();
    }
    private static void capture(String name) {
        pending++;
        Screenshot.takeScreenshot(Minecraft.getInstance().gameRenderer.mainRenderTarget(), image -> {
            try (image) {
                var file = output().resolve(ROLE + "/" + name + ".png"); Files.createDirectories(file.getParent()); image.writeToFile(file); pending--;
            } catch (Exception e) { throw new IllegalStateException(e); }
        });
    }
}
