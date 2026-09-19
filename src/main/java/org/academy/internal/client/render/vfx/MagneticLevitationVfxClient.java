package org.academy.internal.client.render.vfx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.client.render.vfxgraph.runtime.ActiveEffect;
import org.academy.api.client.render.vfxgraph.runtime.VfxGraphManager;
import org.academy.api.common.arc.ArcPath;
import org.academy.api.common.arc.modifier.JaggedModifier;
import org.academy.api.common.arc.path.LinePath;
import org.academy.internal.common.network.MagneticSupportPacket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class MagneticLevitationVfxClient {
    private static final Map<Integer, SupportEffect> EFFECTS = new HashMap<>();
    private static ClientLevel world;

    private MagneticLevitationVfxClient() {}

    public static void accept(MagneticSupportPacket packet) {
        var minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()) { minecraft.execute(() -> accept(packet)); return; }
        resetWorld();
        if (world == null || !world.dimension().identifier().equals(packet.dimension)) return;
        if (!packet.active) { remove(packet.entityId); return; }
        if (!(world.getEntity(packet.entityId) instanceof LivingEntity subject)) return;
        var current = EFFECTS.get(packet.entityId);
        if (current != null && current.subject != subject) { remove(packet.entityId); current = null; }
        if (current == null) {
            try {
                current = new SupportEffect(subject);
                EFFECTS.put(packet.entityId, current);
            } catch (RuntimeException exception) {
                AcademyCraft.getLogger().error("Unable to spawn magnetic support VFX", exception);
                return;
            }
        }
        current.point = packet.point.add(Vec3.atLowerCornerOf(packet.face.getUnitVec3i()).scale(0.015));
        current.updated = world.getGameTime();
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        resetWorld();
        var iterator = EFFECTS.values().iterator();
        while (iterator.hasNext()) {
            var effect = iterator.next();
            if (!effect.valid()) { VfxGraphManager.INSTANCE.stop(effect.effect); iterator.remove(); }
        }
    }

    private static void resetWorld() {
        var next = Minecraft.getInstance().level;
        if (world == next) return;
        EFFECTS.values().forEach(e -> VfxGraphManager.INSTANCE.stop(e.effect));
        EFFECTS.clear();
        world = next;
    }

    private static void remove(int id) {
        var effect = EFFECTS.remove(id);
        if (effect != null) VfxGraphManager.INSTANCE.stop(effect.effect);
    }

    private static final class SupportEffect {
        final LivingEntity subject;
        final ActiveEffect effect;
        final List<ArcPath> paths = new ArrayList<>(2);
        Vec3 point;
        long updated;

        SupportEffect(LivingEntity subject) {
            this.subject = subject;
            point = subject.position();
            updated = subject.level().getGameTime();
            effect = VfxGraphManager.INSTANCE.spawn(AcademyCraft.academy("vfxgraph/magnetic_levitation_support"),
                    subject.position().toVector3f());
            effect.setRenderDistance(96);
            effect.bindGameTime("time");
            effect.bindArcs("paths", _ -> paths);
            effect.bindFrame((active, camera, partialTick) -> {
                if (!valid()) return false;
                var origin = subject.getPosition(partialTick);
                var end = point.subtract(origin);
                if (end.lengthSqr() > 64 * 64) return false;
                double yaw = Math.toRadians(subject.getYRot());
                var right = new Vec3(Math.cos(yaw), 0, Math.sin(yaw)).scale(subject.getBbWidth() * 0.24);
                paths.clear();
                for (int side : new int[]{-1, 1}) {
                    var start = right.scale(side).add(0, 0.18, 0);
                    paths.add(new ArcPath(new LinePath(start.toVector3f(), end.toVector3f()),
                            List.of(new JaggedModifier(0.42f, 3,
                                    subject.getId() * 31L + side + (subject.tickCount / 2) * 101L)), 1.5f, List.of()));
                }
                active.setPosition(origin.toVector3f());
                active.setCullingSphere(origin.add(point).scale(0.5).toVector3f(), (float) end.length() * 0.5f + 1f);
                return true;
            });
        }

        boolean valid() {
            return world != null && subject.level() == world && subject.isAlive() && !subject.isRemoved()
                    && world.getGameTime() - updated <= 6;
        }
    }
}
