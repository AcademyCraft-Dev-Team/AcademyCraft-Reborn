package org.academy.internal.common.ability.aeromanip;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.Skill;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.ability.ServerContext;

import java.util.Map;
import java.util.WeakHashMap;

public final class AeromanipFieldManager {
    private static final Map<ServerPlayer, FieldContext> ACTIVE = new WeakHashMap<>();
    private static final Map<ServerPlayer, FieldContext> PERSONAL = new WeakHashMap<>();

    private AeromanipFieldManager() {
    }

    public static void activate(
            ServerPlayer player,
            Skill skill,
            AirflowField field,
            FieldTicker ticker
    ) {
        if (AeromanipConfig.settings(player).maxPlacedFieldsPerPlayer < 1) return;
        var previous = ACTIVE.get(player);
        if (previous != null) previous.end();
        var context = new FieldContext(player, skill, field, ticker);
        ACTIVE.put(player, context);
        AbilitySystemServer.registerContext(context);
        AeromanipFieldSyncPacket.sendToTracking(player, field, true);
        var sound = field.type() == AirflowField.Type.VACUUM
                || field.type() == AirflowField.Type.ATMOSPHERIC_DOMINION
                ? org.academy.internal.common.sounds.SoundEvents.AIRFLOW_DOMAIN.get()
                : org.academy.internal.common.sounds.SoundEvents.AIRFLOW_FIELD.get();
        player.level().playSound(null, player.blockPosition(), sound,
                SoundSource.PLAYERS, 0.55f, 0.9f + player.getRandom().nextFloat() * 0.2f);
    }

    /** Activates a personal flow state without replacing the player's placed domain. */
    public static void activatePersonal(
            ServerPlayer player,
            Skill skill,
            AirflowField field,
            FieldTicker ticker
    ) {
        var previous = PERSONAL.get(player);
        if (previous != null) previous.end();
        var context = new FieldContext(player, skill, field, ticker, PERSONAL);
        PERSONAL.put(player, context);
        AbilitySystemServer.registerContext(context);
        AeromanipFieldSyncPacket.sendToTracking(player, field, true);
    }

    public static boolean hasActiveField(ServerPlayer player, AirflowField.Type type) {
        var context = ACTIVE.get(player);
        if (context != null && context.field.type() == type) return true;
        context = PERSONAL.get(player);
        return context != null && context.field.type() == type;
    }

    public static void end(ServerPlayer player) {
        var context = ACTIVE.get(player);
        if (context != null) context.end();
        context = PERSONAL.get(player);
        if (context != null) context.end();
    }

    public static void endPersonal(ServerPlayer player) {
        var context = PERSONAL.get(player);
        if (context != null) context.end();
    }

    public static void endPlaced(ServerPlayer player) {
        var context = ACTIVE.get(player);
        if (context != null) context.end();
    }

    public static float rangeMultiplier(ServerPlayer player) {
        return hasActiveField(player, AirflowField.Type.ATMOSPHERIC_DOMINION) ? 1.25f : 1.0f;
    }

    @FunctionalInterface
    public interface FieldTicker {
        void tick(ServerPlayer player, AirflowField field, int ageTicks);
    }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class Events {
        private Events() { }

        @SubscribeEvent
        public static void onServerStopping(ServerStoppingEvent event) {
            for (var context : ACTIVE.values().toArray(FieldContext[]::new)) context.end();
            for (var context : PERSONAL.values().toArray(FieldContext[]::new)) context.end();
            ACTIVE.clear();
            PERSONAL.clear();
        }
    }

    private static final class FieldContext extends ServerContext {
        private final Skill skill;
        private final AirflowField field;
        private final FieldTicker ticker;
        private final Map<ServerPlayer, FieldContext> ownerMap;
        private int ageTicks;
        private boolean ended;

        private FieldContext(ServerPlayer player, Skill skill, AirflowField field, FieldTicker ticker) {
            this(player, skill, field, ticker, ACTIVE);
        }

        private FieldContext(ServerPlayer player, Skill skill, AirflowField field, FieldTicker ticker,
                             Map<ServerPlayer, FieldContext> ownerMap) {
            super(player);
            this.skill = skill;
            this.field = field;
            this.ticker = ticker;
            this.ownerMap = ownerMap;
        }

        @SubscribeEvent
        public void onTick(ServerTickEvent.Pre event) {
            ageTicks++;
            if (ended
                    || player.hasDisconnected()
                    || !player.isAlive()
                    || !player.level().dimension().equals(field.dimension())
                    || !skill.isEnabled(player)
                    || ageTicks >= field.durationTicks()) {
                end();
                return;
            }
            skill.reportActivity(player, false);
            if ((ageTicks & 1) != 0) return;
            skill.reportActivity(player, true);
            ticker.tick(player, field, ageTicks);
            spawnVisual();
        }

        private void spawnVisual() {
            var level = level();
            var center = field.center();
            var count = field.type() == AirflowField.Type.ATMOSPHERIC_DOMINION ? 10 : 5;
            level.sendParticles(
                    ParticleTypes.CLOUD,
                    center.x, center.y, center.z,
                    count,
                    Math.max(0.2, field.radius() * 0.4),
                    Math.max(0.2, field.radius() * 0.2),
                    Math.max(0.2, field.radius() * 0.4),
                    0.01
            );
        }

        private void end() {
            if (ended) return;
            ended = true;
            if (!player.hasDisconnected()) AeromanipFieldSyncPacket.sendToTracking(player, field, false);
            unregister();
        }

        @Override
        protected void onUnregistered() {
            ended = true;
            ACTIVE.remove(player, this);
            PERSONAL.remove(player, this);
            ownerMap.remove(player, this);
        }
    }
}
