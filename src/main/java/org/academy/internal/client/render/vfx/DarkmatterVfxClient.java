package org.academy.internal.client.render.vfx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.vfxgraph.render.GraphCamera;
import org.academy.api.client.render.vfxgraph.runtime.ActiveEffect;
import org.academy.api.client.render.vfxgraph.runtime.VfxGraphManager;
import org.academy.api.client.render.vfxgraph.shape.HumanoidSurfacePose;
import org.academy.api.client.render.vfxgraph.shape.SurfaceSampler;
import org.academy.api.common.vfx.RepairVisualParts;
import org.academy.internal.common.network.DarkmatterVisualPacket;
import org.academy.internal.common.network.DarkmatterVisualPacket.Kind;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/** One bounded graph per actor/target and kind, with explicit stop, leases and world identity checks. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class DarkmatterVfxClient {
    private record Key(Kind kind, int entity, BlockPos block) { }
    private static final int MAX_EFFECTS = 64;
    private static final Map<Key, Visual> EFFECTS = new LinkedHashMap<>();
    private static ClientLevel world;
    private DarkmatterVfxClient() { }

    public static void accept(DarkmatterVisualPacket packet) {
        var mc = Minecraft.getInstance();
        if (!mc.isSameThread()) { mc.execute(() -> accept(packet)); return; }
        if (packet.kind == Kind.INTERFERENCE) { InterferenceFieldClient.accept(packet); return; }
        resetWorld();
        if (world == null || !world.dimension().identifier().equals(packet.dimension)) return;
        var key = new Key(packet.kind, packet.entityId, packet.entityId < 0 ? BlockPos.containing(packet.position) : BlockPos.ZERO);
        if (!packet.active) { remove(key); return; }
        var subject = packet.entityId < 0 ? null : world.getEntity(packet.entityId);
        if (packet.entityId >= 0 && !(subject instanceof LivingEntity)) return;
        var current = EFFECTS.get(key);
        if (current != null && (current.subject != subject || current.effect.isStopped())) { remove(key); current = null; }
        if (current == null) {
            if (EFFECTS.size() >= MAX_EFFECTS) remove(EFFECTS.keySet().iterator().next());
            try {
                current = new Visual(packet, (LivingEntity) subject);
                EFFECTS.put(key, current);
            } catch (RuntimeException failure) {
                AcademyCraft.getLogger().error("Unable to create darkmatter surface effect", failure);
                return;
            }
        }
        current.refresh(packet);
    }

    /** Diagnostics used by the isolated client capture, including continuous-pulse deduplication. */
    public static int activeCount() { return EFFECTS.size() + InterferenceFieldClient.activeCount(); }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        InterferenceFieldClient.tick();
        resetWorld();
        var iterator = EFFECTS.values().iterator();
        while (iterator.hasNext()) {
            var visual = iterator.next();
            if (!visual.valid()) { visual.effect.stop(); iterator.remove(); }
        }
    }

    private static void resetWorld() {
        var next = Minecraft.getInstance().level;
        if (next == world) return;
        EFFECTS.values().forEach(v -> v.effect.stop());
        EFFECTS.clear();
        world = next;
    }
    private static void remove(Key key) { var previous = EFFECTS.remove(key); if (previous != null) previous.effect.stop(); }

    private static final class Visual {
        final LivingEntity subject;
        final ActiveEffect effect;
        final ClientLevel level;
        final long[] partTicks = new long[7];
        DarkmatterVisualPacket state;
        long updated;
        Vec3 origin;
        GraphCamera camera;
        boolean firstPerson;
        Matrix4f[] bones;
        Matrix4f modelRoot;

        Visual(DarkmatterVisualPacket packet, LivingEntity subject) {
            this.subject = subject;
            level = world;
            state = packet;
            origin = packet.position;
            java.util.Arrays.fill(partTicks, Long.MIN_VALUE / 2);
            effect = VfxGraphManager.INSTANCE.spawn(AcademyCraft.academy("vfxgraph/" + packet.kind.graph), origin.toVector3f());
            effect.setRenderDistance(96);
            effect.bindSurfaceSampler("surface", this::sampleSubject);
            effect.bindFrame(this::frame);
        }

        void refresh(DarkmatterVisualPacket packet) {
            state = packet;
            updated = level.getGameTime();
            for (int i = 0; i < partTicks.length; i++) if ((packet.parts & (1 << i)) != 0) partTicks[i] = updated;
        }

        boolean valid() {
            if (world != level || effect.isStopped() || level.getGameTime() - updated > state.kind.leaseTicks) return false;
            if (subject == null) return true;
            if (subject.level() != world) return false;
            var present = world.getEntity(subject.getId());
            if (present != subject && !(present == null && state.finished)) return false;
            // Terminal erosion may finish on the death pose; maintained fields and repair stop immediately.
            return (!subject.isRemoved() && subject.isAlive()) || state.finished && (state.kind == Kind.DISASSEMBLE || state.kind == Kind.CONTACT);
        }

        boolean frame(ActiveEffect active, GraphCamera camera, float partialTick) {
            if (!valid()) return false;
            this.camera = camera;
            var mc = Minecraft.getInstance();
            firstPerson = subject == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
            if (subject != null && !subject.isRemoved()) origin = subject.getPosition(partialTick);
            active.setPosition(origin.toVector3f());
            var rotation = new Quaternionf();
            active.setRotation(rotation);
            var inverse = new Quaternionf(rotation).conjugate();
            var viewPosition = inverse.transform(new Vector3f(camera.position()).sub(origin.toVector3f()));
            var viewDirection = new Matrix4f(camera.viewRotation()).invert().transformDirection(new Vector3f(0, 0, -1));
            inverse.transform(viewDirection).normalize();
            bones = subject == null ? null : HumanoidSurfacePose.get(subject.getId());
            var root = subject == null ? null : WingAvatarRegistry.entries().get(subject.getId());
            modelRoot = root == null ? null : new Matrix4f(root);
            if (modelRoot != null) {
                modelRoot.m30(modelRoot.m30() + camera.position().x - (float) origin.x);
                modelRoot.m31(modelRoot.m31() + camera.position().y - (float) origin.y);
                modelRoot.m32(modelRoot.m32() + camera.position().z - (float) origin.z);
            }
            float elapsed = (level.getGameTime() - updated + partialTick) / 20f;
            float progress = Math.clamp(elapsed / (state.kind == Kind.CONTACT ? 0.18f : state.kind == Kind.REPAIR ? 0.38f : 0.65f), 0, 1);
            var pos = BlockPos.containing(origin.add(0, state.height * 0.6, 0));
            int brightness = Math.max(level.getBrightness(LightLayer.BLOCK, pos),
                    Math.max(0, level.getBrightness(LightLayer.SKY, pos) - level.getSkyDarken()));
            var graph = active.effect();
            graph.setLiveParam("time", Value.of(active.gameAgeSeconds()));
            graph.setLiveParam("progress", Value.of(progress));
            graph.setLiveParam("sheet_scale", Value.of(firstPerson && state.kind == Kind.REPAIR ? 0.45f : 1f));
            graph.setLiveParam("width", Value.of(state.width));
            graph.setLiveParam("height", Value.of(state.height));
            graph.setLiveParam("range", Value.of(state.range));
            graph.setLiveParam("seed", Value.of((float) (state.seed & 65535)));
            graph.setLiveParam("light", Value.of(brightness / 15f));
            float sunAngle = level.environmentAttributes().getValue(net.minecraft.world.attribute.EnvironmentAttributes.SUN_ANGLE, origin, null)
                    * (float) (Math.PI / 180);
            var sunlight = inverse.transform(new Vector3f((float) Math.sin(sunAngle), (float) Math.cos(sunAngle), 0));
            graph.setLiveParam("sun_direction", Value.of(sunlight));
            graph.setLiveParam("finished", Value.of(state.finished ? 1f : 0));
            graph.setLiveParam("view_first_person", Value.of(mc.options.getCameraType().isFirstPerson() ? 1f : 0));
            graph.setLiveParam("camera", Value.of(viewPosition));
            graph.setLiveParam("camera_forward", Value.of(viewDirection));
            active.setCullingSphere(origin.toVector3f(), Math.max(state.width, state.height) + 1);
            return true;
        }

        int parts() {
            int result = 0;
            for (int i = 0; i < partTicks.length; i++) if (level.getGameTime() - partTicks[i] <= Kind.REPAIR.leaseTicks) result |= 1 << i;
            return result;
        }

        boolean sampleSubject(int index, float u, float v, Vector3f p, Vector3f n) {
            if (state.kind == Kind.REPAIR && firstPerson) return sampleHand(index, u, v, p, n);
            if (subject instanceof Player && bones != null && modelRoot != null) {
                var choices = new ArrayList<Integer>(6);
                int parts = parts();
                if (state.kind != Kind.REPAIR || (parts & (RepairVisualParts.BODY | RepairVisualParts.CHEST)) != 0) choices.add(0);
                if (state.kind != Kind.REPAIR || (parts & RepairVisualParts.HEAD) != 0) choices.add(1);
                boolean rightMain = subject.getMainArm() == HumanoidArm.RIGHT;
                if (state.kind != Kind.REPAIR || (parts & (RepairVisualParts.BODY | (rightMain ? RepairVisualParts.MAIN_HAND : RepairVisualParts.OFF_HAND))) != 0) choices.add(2);
                if (state.kind != Kind.REPAIR || (parts & (RepairVisualParts.BODY | (rightMain ? RepairVisualParts.OFF_HAND : RepairVisualParts.MAIN_HAND))) != 0) choices.add(3);
                if (state.kind != Kind.REPAIR || (parts & (RepairVisualParts.LEGS | RepairVisualParts.FEET)) != 0) { choices.add(4); choices.add(5); }
                if (choices.isEmpty()) return false;
                int part = choices.get(index % choices.size());
                var min = switch (part) {
                    case 0 -> new Vector3f(-4, 0, -2);
                    case 1 -> new Vector3f(-4, -8, -4);
                    case 2 -> new Vector3f(-3, -2, -2);
                    case 3 -> new Vector3f(-1, -2, -2);
                    default -> new Vector3f(-2, 0, -2);
                };
                var max = switch (part) {
                    case 0 -> new Vector3f(4, 12, 2);
                    case 1 -> new Vector3f(4, 0, 4);
                    case 2 -> new Vector3f(1, 10, 2);
                    case 3 -> new Vector3f(3, 10, 2);
                    default -> new Vector3f(2, 12, 2);
                };
                // Equipment repairs are anchored on the corresponding body slot; feet stay below the knee.
                if (part >= 4 && (parts & RepairVisualParts.FEET) != 0 && (parts & RepairVisualParts.LEGS) == 0) min.y = 8;
                if ((part == 2 || part == 3) && state.kind == Kind.REPAIR && (parts & RepairVisualParts.BODY) == 0) min.y = 7;
                SurfaceSampler.box(4 + index / choices.size(), u, v, min.div(16), max.div(16),
                        new Matrix4f(modelRoot).mul(bones[part]), p, n);
                return true;
            }
            int face = index % 6;
            if (subject == null && state.parts != 0) {
                // Direction ordinals DOWN, UP, NORTH, SOUTH, WEST, EAST -> box face order.
                int[] faceOrder = {2, 3, 4, 5, 0, 1};
                var exposed = new ArrayList<Integer>(6);
                for (int i = 0; i < 6; i++) if ((state.parts & (1 << i)) != 0) exposed.add(faceOrder[i]);
                if (exposed.isEmpty()) return false;
                face = exposed.get(index % exposed.size());
            }
            SurfaceSampler.box(face, u, v, new Vector3f(-state.width / 2, 0, -state.width / 2),
                    new Vector3f(state.width / 2, state.height, state.width / 2), new Matrix4f(), p, n);
            return true;
        }

        boolean sampleHand(int index, float u, float v, Vector3f p, Vector3f n) {
            int parts = parts();
            boolean body = (parts & RepairVisualParts.BODY) != 0;
            boolean main = (parts & RepairVisualParts.MAIN_HAND) != 0, off = (parts & RepairVisualParts.OFF_HAND) != 0;
            if (!body && !main && !off || index >= (main && off ? 4 : 2)) return false;
            boolean right = subject.getMainArm() == HumanoidArm.RIGHT;
            if (off && (!main || index % 2 != 0)) right = !right;
            var transform = new Matrix4f(camera.viewRotation()).invert();
            transform.setTranslation(new Vector3f(camera.position()).sub(origin.toVector3f()));
            transform.translate(right ? 0.30f : -0.30f, -0.23f, -0.53f);
            SurfaceSampler.box(5, u, v, new Vector3f(-0.05f, -0.05f, -0.02f),
                    new Vector3f(0.05f, 0.05f, 0.02f), transform, p, n);
            return true;
        }
    }
}
