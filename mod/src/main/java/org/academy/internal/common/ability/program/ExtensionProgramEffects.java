package org.academy.internal.common.ability.program;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.ability.program.ProgramEffect;
import org.academy.internal.server.ability.AbilitySystemServer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class ExtensionProgramEffects {
    private static final List<Lease> ACTIVE = new ArrayList<>();

    private ExtensionProgramEffects() {
    }

    static ProgramActionTransaction.Undo track(ServerPlayer player, List<LivingEntity> targets, ProgramEffect effect,
                                               ServerProgramScheduler.SessionKey key,
                                               Identifier category, Skill skill) {
        var lease = new Lease(player, targets, effect, player.level().dimension().identifier(),
                player.level().getGameTime() + effect.lifetimeTicks(), key, category, skill);
        if (effect.lifetimeTicks() > 0) ACTIVE.add(lease);
        return lease::close;
    }

    static void cancel(MinecraftServer server, ServerProgramScheduler.SessionKey key) {
        for (var lease : List.copyOf(ACTIVE)) {
            if (lease.player.level().getServer() == server && key.equals(lease.key)) lease.close();
        }
    }

    static void cancelOwner(MinecraftServer server, UUID owner) {
        for (var lease : List.copyOf(ACTIVE)) {
            if (lease.player.level().getServer() == server && lease.player.getUUID().equals(owner)) lease.close();
        }
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        for (var lease : List.copyOf(ACTIVE)) {
            if (lease.player.level().getServer() != event.getServer()) continue;
            var system = AbilitySystemServer.getSystem(lease.player);
            if (!lease.player.isAlive() || lease.player.hasDisconnected()
                    || !system.getPlayerAbilityCategory(lease.player.getUUID()).getKey().equals(lease.category)
                    || system.getPlayerLevel(lease.player.getUUID()) < 5 || !lease.skill.isEnabled(lease.player)
                    || !lease.player.level().dimension().identifier().equals(lease.dimension)
                    || lease.player.level().getGameTime() >= lease.until
                    || lease.targets.stream().anyMatch(target -> !target.isAlive() || target.isRemoved()
                    || target.level() != lease.player.level())) lease.close();
        }
    }

    @SubscribeEvent
    public static void stop(ServerStoppedEvent event) {
        for (var lease : List.copyOf(ACTIVE)) {
            if (lease.player.level().getServer() == event.getServer()) lease.close();
        }
    }

    private static final class Lease {
        private final ServerPlayer player;
        private final List<LivingEntity> targets;
        private final ProgramEffect effect;
        private final Identifier category;
        private final Skill skill;
        private final Identifier dimension;
        private final long until;
        private final ServerProgramScheduler.SessionKey key;
        private boolean closed;

        private Lease(ServerPlayer player, List<LivingEntity> targets, ProgramEffect effect,
                      Identifier dimension, long until, ServerProgramScheduler.SessionKey key,
                      Identifier category, Skill skill) {
            this.player = player;
            this.targets = targets;
            this.effect = effect;
            this.dimension = dimension;
            this.until = until;
            this.key = key;
            this.category = category;
            this.skill = skill;
        }

        private void close() {
            if (closed) return;
            closed = true;
            ACTIVE.remove(this);
            try {
                effect.cleanup().close();
            } catch (Exception exception) {
                AcademyCraft.LOGGER.error("Addon program effect cleanup failed", exception);
            }
        }
    }
}
