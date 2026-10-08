package org.academy.desktop.grapheditor.preview;

import com.google.gson.GsonBuilder;
import net.minecraft.client.model.geom.ModelPart;
import org.academy.internal.client.model.DarkmatterCreatureModel;
import org.academy.internal.client.renderer.entity.state.DarkmatterCreatureRenderState;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

/** Opt-in pose-only JVM benchmark. No window, game world, fake FPS or production changes. */
public final class BeetlePerformanceCaptureMain {
    private static volatile double consumedPose;
    private record PoseCase(DarkmatterCreatureRenderState state, ModelPart head, ModelPart torso) { }

    private BeetlePerformanceCaptureMain() { }

    public static void main(String[] args) throws Exception {
        var label = System.getProperty("academy.beetlePerformanceLabel", "baseline");
        if (!label.matches("[a-z0-9_]+")) throw new IllegalArgumentException("Invalid capture label");
        var root = DarkmatterCreatureModel.createBodyLayer().bakeRoot();
        var model = new DarkmatterCreatureModel(root);
        var all = root.getChild("all");
        var styles = List.of(new ArrayList<PoseCase>(), new ArrayList<PoseCase>(), new ArrayList<PoseCase>());
        for (var distance : new int[]{16, 24, 32}) {
            for (var head = 0; head < 3; head++) for (var torso = 0; torso < 3; torso++) {
                for (var limbs = 0; limbs < 3; limbs++) for (var additional = 0; additional < 4; additional++) {
                    var state = new DarkmatterCreatureRenderState();
                    state.headModel = head; state.torsoModel = torso; state.limbsModel = limbs;
                    state.additionalModel = additional; state.distanceToCameraSq = distance * distance;
                    state.ageInTicks = 31.5f; state.walkAnimationPos = 8.25f; state.walkAnimationSpeed = .6f;
                    state.swimming = torso == 2; state.aimYaw = 28; state.aimPitch = -12;
                    state.attackProgress = .45f; state.gammaCatalyzed = (head + torso + limbs + additional) % 2 == 0;
                    styles.get(torso).add(new PoseCase(state, all.getChild("head_" + head), all.getChild("torso_" + torso)));
                }
            }
        }
        // Interleave A flight, B swimming and C walking to exercise a shared renderer rig.
        var order = new ArrayList<PoseCase>(324);
        for (var i = 0; i < styles.getFirst().size(); i++) for (var torso : new int[]{1, 2, 0}) order.add(styles.get(torso).get(i));
        for (var pass = 0; pass < 250; pass++) exercise(model, order);
        var batches = new ArrayList<Object>();
        var collector = ManagementFactory.getGarbageCollectorMXBeans();
        var gcBefore = collector.stream().mapToLong(b -> Math.max(0, b.getCollectionCount())).sum();
        var means = new double[6];
        for (var batch = 0; batch < means.length; batch++) {
            var began = System.nanoTime();
            for (var pass = 0; pass < 200; pass++) exercise(model, order);
            var elapsed = System.nanoTime() - began;
            var calls = order.size() * 200;
            means[batch] = elapsed / (double) calls;
            var row = new LinkedHashMap<String, Object>();
            row.put("setupAnimCalls", calls); row.put("elapsedNanos", elapsed);
            row.put("meanPoseNanos", means[batch]); batches.add(row);
        }
        var report = new LinkedHashMap<String, Object>();
        report.put("label", label); report.put("benchmark", "detailed_rig_setup_anim_only");
        report.put("jvm", System.getProperty("java.runtime.version"));
        report.put("states", order.size()); report.put("combinationsPerDistance", 108);
        report.put("nominalDistancesBlocks", new int[]{16, 24, 32});
        report.put("warmupSetupAnimCalls", 250 * order.size()); report.put("batches", batches);
        Arrays.sort(means); report.put("medianBatchMeanPoseNanos", (means[2] + means[3]) * .5);
        report.put("gcCollectionsDuringMeasurement", collector.stream().mapToLong(b -> Math.max(0, b.getCollectionCount())).sum() - gcBefore);
        report.put("bakedParts", root.getAllParts().size());
        var cubeCount = new int[1]; root.visit(new com.mojang.blaze3d.vertex.PoseStack(), (pose, path, index, cube) -> cubeCount[0]++);
        report.put("bakedCubes", cubeCount[0]); report.put("poseChecksum", consumedPose);
        report.put("limitations", List.of("Measures CPU setupAnim with the detailed rig only, not GPU or actual FPS.",
                "Batch means include fixed state iteration and a pose checksum; they are not per-call latency percentiles.",
                "Real client captures independently measure renderer geometry selection and world/entity costs."));
        var output = Path.of(System.getProperty("academy.beetlePerformanceOutput", "docs/models/darkmatter_creature/performance"));
        Files.createDirectories(output);
        Files.writeString(output.resolve(label + "_pose.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
        System.out.println("[beetle-performance] " + label + " measured median batch mean " + report.get("medianBatchMeanPoseNanos") + " ns; " + output);
    }

    private static void exercise(DarkmatterCreatureModel model, List<PoseCase> states) {
        double sum = 0;
        for (var row : states) {
            model.setupAnim(row.state);
            sum += row.head.xRot + row.torso.xRot;
        }
        consumedPose = sum;
    }
}
