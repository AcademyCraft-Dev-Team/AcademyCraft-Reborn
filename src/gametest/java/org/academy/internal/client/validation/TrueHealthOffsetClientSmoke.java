package org.academy.internal.client.validation;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.academy.AcademyCraft;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.academy.internal.common.world.damagesource.DamageTypes;
import org.academy.internal.common.world.damagesource.SkillDamageUtil;
import org.slf4j.Logger;

/** Opt-in integrated-client test; only use with an isolated quick-play world. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class TrueHealthOffsetClientSmoke {
    private static final Logger LOGGER = AcademyCraft.getLogger();
    private static boolean started;
    private static boolean sawProjection;
    private static volatile int entityId = -1;
    private static volatile long startedAt;
    private static volatile String failure;
    private static int waited;
    private TrueHealthOffsetClientSmoke() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("academy.trueHealthSmoke")) return;
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;
        if (!started) {
            var server = minecraft.getSingleplayerServer();
            if (server == null) return;
            started = true;
            var playerId = minecraft.player.getUUID();
            server.execute(() -> {
                try {
                    var player = server.getPlayerList().getPlayer(playerId);
                    if (player == null) throw new IllegalStateException("Missing integrated player");
                    var level = player.level();
                    var cow = new LockedCow(level);
                    cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
                    cow.setHealth(100);
                    cow.locked = true;
                    cow.setNoAi(true);
                    cow.setNoGravity(true);
                    cow.setPersistenceRequired();
                    cow.setPos(player.position().add(2, 0, 0));
                    level.addFreshEntity(cow);
                    var holder = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE)
                            .getOrThrow(DamageTypes.CTA);
                    if (!SkillDamageUtil.applyVerifiedTrueHealth(cow,
                            new net.minecraft.world.damagesource.DamageSource(holder), 30)) {
                        throw new IllegalStateException("Server rejected projected CTA");
                    }
                    startedAt = level.getGameTime();
                    entityId = cow.getId();
                } catch (Throwable error) {
                    failure = error.toString();
                }
            });
        }
        if (failure != null) throw new IllegalStateException("True-health client smoke failed: " + failure);
        if (++waited > 1000) throw new IllegalStateException("True-health client smoke timed out");
        if (entityId < 0) return;
        var entity = minecraft.level.getEntity(entityId);
        if (!(entity instanceof Cow cow)) return;
        if (cow.hasData(AttachmentTypes.TRUE_HEALTH_CEILING)
                && Math.abs(cow.getData(AttachmentTypes.TRUE_HEALTH_CEILING) - 70) < 0.01f) {
            if (Math.abs(cow.getHealth() - 70) > 0.01f) {
                throw new IllegalStateException("Client getHealth did not apply the synchronized ceiling");
            }
            if (!sawProjection) LOGGER.info("TRUE_HEALTH_CLIENT_PROJECTION_OK");
            sawProjection = true;
        }
        if (sawProjection && minecraft.level.getGameTime() >= startedAt + 420
                && !cow.hasData(AttachmentTypes.TRUE_HEALTH_CEILING) && cow.getHealth() >= 99.9f) {
            LOGGER.info("TRUE_HEALTH_CLIENT_SMOKE_PASSED");
            var server = minecraft.getSingleplayerServer();
            if (server != null) server.execute(() -> {
                for (var level : server.getAllLevels()) {
                    var target = level.getEntity(entityId);
                    if (target instanceof LockedCow) target.discard();
                }
            });
            minecraft.stop();
        }
    }

    private static final class LockedCow extends Cow {
        private boolean locked;
        private LockedCow(Level level) { super(EntityTypes.COW, level); }
        @Override public void setHealth(float value) {
            if (!locked || value >= 100) super.setHealth(value);
        }
        @Override public void tick() {
            super.tick();
            if (locked) super.setHealth(100);
        }
    }
}
