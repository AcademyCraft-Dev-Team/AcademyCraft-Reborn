package org.academy.internal.client.render.vfx;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import org.academy.AcademyCraft;
import org.academy.api.client.render.vfx.VfxManager;
import org.academy.api.common.vfx.SkillVfxState;
import org.academy.internal.common.network.SkillVfxPacket;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.skill.HighSpeedElectronBeam;
import org.academy.internal.common.world.entity.skill.Plasma;

/** Client-only visual adapters. None of the mirror entities is added to ClientLevel. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class SkillVfxClient {
    private static final Map<Long, Replica> ACTIVE = new HashMap<>();
    private static final org.academy.api.common.vfx.EffectSequence SEQUENCE =
            new org.academy.api.common.vfx.EffectSequence(16384);
    private static ClientLevel world;
    private static long received;
    private static long clientTick;
    private SkillVfxClient() {}

    public static void accept(SkillVfxPacket packet) {
        var minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()) {
            minecraft.execute(() -> accept(packet));
            return;
        }
        var level = minecraft.level;
        if (level != world) reset(level);
        if (level == null || !level.dimension().identifier().equals(packet.dimension)) return;
        boolean terminal = packet.state.oneShot()
                || (packet.state instanceof SkillVfxState.End end && !end.hidden());
        if (!SEQUENCE.accept(packet.id, packet.revision, terminal)) return;
        received++;
        long now = clientTick;
        if (packet.state instanceof SkillVfxState.Smoke smoke) {
            SmokeVfxClient.spawn(smoke);
            return;
        }
        if (packet.state instanceof SkillVfxState.Slash slash) {
            VfxManager.INSTANCE.spawn(new DarkmatterSlashVfx(slash));
            return;
        }
        if (packet.state instanceof SkillVfxState.Burst burst) {
            if (burst.plasmaImpact()) PlasmaVfxClient.spawnImpact(burst.position().toVector3f());
            else VfxManager.INSTANCE.spawn(new ShockwaveVfx(burst));
            return;
        }
        if (packet.state instanceof SkillVfxState.End end) {
            var replica = ACTIVE.get(packet.id);
            if (replica != null) {
                if (!end.hidden() && replica.entity instanceof HighSpeedElectronBeam b && b.hasFired() && !b.isContinuous()) {
                    // A fire and end received in one client frame must still produce a visible flash.
                    replica.endingAt = now + Math.max(3, b.currentRayLifeTicks);
                } else {
                    replica.entity.discard();
                    ACTIVE.remove(packet.id);
                }
            }
            return;
        }
        var replica = ACTIVE.get(packet.id);
        if (replica == null) {
            Entity mirror = packet.state instanceof SkillVfxState.Beam
                    ? new HighSpeedElectronBeam(EntityTypes.HIGH_SPEED_ELECTRON_BEAM.get(), level)
                    : new Plasma(EntityTypes.PLASMA.get(), level);
            replica = new Replica(mirror, packet.state, now);
            ACTIVE.put(packet.id, replica);
            replica.apply(now);
            if (mirror instanceof HighSpeedElectronBeam beam) VfxManager.INSTANCE.spawn(new BeamVfx(beam));
            else PlasmaVfxClient.spawnCharge((Plasma) mirror);
        } else {
            boolean firstFire = packet.state instanceof SkillVfxState.Beam b && b.fired()
                    && replica.state instanceof SkillVfxState.Beam old && !old.fired();
            replica.state = packet.state;
            replica.receivedAt = now;
            replica.endingAt = Long.MAX_VALUE;
            replica.apply(now);
            if (firstFire && replica.entity instanceof HighSpeedElectronBeam b) {
                replica.fireVisibleUntil = now + 3;
                b.currentRayLifeTicks = Math.max(3, b.currentRayLifeTicks);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void tick(ClientTickEvent.Post event) {
        var level = Minecraft.getInstance().level;
        if (level != world) reset(level);
        if (level == null || Minecraft.getInstance().isPaused()) return;
        long now = ++clientTick;
        var iterator = ACTIVE.values().iterator();
        while (iterator.hasNext()) {
            var replica = iterator.next();
            if (now >= replica.endingAt || now - replica.receivedAt > 100) {
                replica.entity.discard();
                iterator.remove();
            } else replica.apply(now);
        }
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { reset(null); }

    private static void reset(ClientLevel level) {
        ACTIVE.values().forEach(replica -> replica.entity.discard());
        ACTIVE.clear(); SEQUENCE.clear();
        PlasmaVfxClient.clear();
        SmokeVfxClient.clear();
        clientTick = 0;
        world = level;
    }

    public static long receivedPackets() { return received; }

    private static final class Replica {
        final Entity entity;
        SkillVfxState state;
        long receivedAt;
        long endingAt = Long.MAX_VALUE;
        long fireVisibleUntil;
        Replica(Entity entity, SkillVfxState state, long now) {
            this.entity = entity; this.state = state; receivedAt = now;
            if (state instanceof SkillVfxState.Beam b && b.fired()) fireVisibleUntil = now + 3;
        }
        void apply(long now) {
            float elapsed = Math.min(25f, (float) (now - receivedAt));
            if (entity instanceof HighSpeedElectronBeam beam && state instanceof SkillVfxState.Beam b) {
                beam.applyVisualSnapshot(b, elapsed);
                if (now < fireVisibleUntil) beam.currentRayLifeTicks = Math.max(3, beam.currentRayLifeTicks);
            } else if (entity instanceof Plasma plasma && state instanceof SkillVfxState.Plasma p) {
                plasma.applyVisualSnapshot(p, elapsed);
            }
        }
    }
}
