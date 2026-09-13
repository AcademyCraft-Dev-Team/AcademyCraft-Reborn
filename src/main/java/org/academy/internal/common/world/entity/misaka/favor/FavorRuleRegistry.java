package org.academy.internal.common.world.entity.misaka.favor;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.academy.AcademyCraft;
import org.academy.internal.common.world.entity.misaka.MisakaSisterEntity;
import org.academy.internal.common.world.entity.misaka.MisakaSisterRosterSync;
import org.academy.internal.common.world.entity.misaka.MobRelation;
import org.academy.internal.common.world.entity.misaka.favor.rules.AnniversaryCakeRule;
import org.academy.internal.common.world.entity.misaka.favor.rules.AttackedByPlayerRule;
import org.academy.internal.common.world.entity.misaka.favor.rules.FeedFavoriteFoodRule;
import org.academy.internal.common.world.entity.misaka.favor.rules.HotSpringSoakRule;
import org.academy.internal.common.world.entity.misaka.favor.rules.KilledByPlayerRule;
import org.academy.internal.server.misaka.MisakaComputeContribution;
import org.academy.internal.server.world.level.storage.MisakaSisterRecord;
import org.academy.internal.server.world.level.storage.MisakaSisterRoster;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class FavorRuleRegistry {
    public static final List<IFavorRule> RULES = List.of(
            new AttackedByPlayerRule(),
            new KilledByPlayerRule(),
            new FeedFavoriteFoodRule(),
            new AnniversaryCakeRule(),
            new HotSpringSoakRule()
    );

    private FavorRuleRegistry() {
    }

    public static void trigger(FavorContext context) {
        MinecraftServer server = resolveServer(context);
        for (var rule : RULES) {
            if (rule.matches(context)) {
                rule.apply(context, server);
            }
        }
        if (server != null) {
            // Defer CP refresh so favor-max rebuild/sync cannot run inside hand-feed /
            // damage handlers before action-bar feedback returns.
            final MisakaSisterRecord target = context.record();
            server.execute(() -> MisakaComputeContribution.refreshCpForRecord(server, target));
        }
    }

    @SubscribeEvent
    public static void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof MisakaSisterEntity sister)) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) {
            return;
        }
        var record = sister.rosterRecord().orElse(null);
        if (record == null) {
            return;
        }
        trigger(FavorContext.attacked(sister, record, attacker, event.getSource()));
        MisakaSisterRoster.get(sister.level().getServer()).setDirty();
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        if (!(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        var killerEntity = event.getSource().getEntity();
        LivingEntity killer = killerEntity instanceof LivingEntity living ? living : null;
        MinecraftServer server = level.getServer();

        if (event.getEntity() instanceof MisakaSisterEntity sister) {
            enterIncapacitated(sister, killer, event);
            return;
        }

        if (event.getEntity() instanceof Player victim) {
            applyKilledBenevolentLan(server, victim, killer);
            MisakaSisterRoster.get(server).setDirty();
        }

        // Witness kills (villagers / players) — 16 blocks + line of sight, LAN-deduped
        propagateWitnessDeaths(level, killer, event.getEntity());
    }

    /**
     * Lethal damage becomes incapacitated instead of permanent death / roster release.
     */
    private static void enterIncapacitated(
            MisakaSisterEntity sister,
            @Nullable LivingEntity killer,
            LivingDeathEvent event
    ) {
        event.setCanceled(true);
        sister.setHealth(1.0f);
        sister.sanitizePoseAfterLoad();
        sister.setPersistenceRequired();
        if (sister.isPassenger()) {
            sister.stopRiding();
        }
        sister.setTarget(null);
        sister.getNavigation().stop();
        if (sister.isSleeping()) {
            sister.stopSleeping();
        }

        var record = sister.rosterRecord().orElse(null);
        if (record == null) {
            sister.applyIncapacitatedHold();
            return;
        }
        boolean entering = !record.incapacitated;
        if (entering) {
            record.incapacitated = true;
            MisakaSisterRosterSync.syncFromRecord(sister, record);
            MisakaSisterRoster.get(sister.level().getServer()).setDirty();
            MisakaComputeContribution.refreshCpForRecord(sister.level().getServer(), record);
            // Critical harm: same -5 as former kill rule, once per incap entry.
            trigger(FavorContext.killedSister(sister, record, killer));
        } else {
            MisakaSisterRosterSync.syncFromRecord(sister, record);
        }
        sister.applyIncapacitatedHold();
    }

    private static void applyKilledBenevolentLan(
            MinecraftServer server,
            Player victim,
            LivingEntity killer
    ) {
        if (!(killer instanceof Player)) {
            return;
        }
        String killerName = ((Player) killer).getGameProfile().name();
        String victimName = victim.getGameProfile().name();
        List<MisakaSisterRecord> seeds = new ArrayList<>();
        for (var record : MisakaSisterRoster.get(server).all()) {
            if (!record.awakened) {
                continue;
            }
            if (FavorService.relation(record, victimName) == MobRelation.BENEVOLENT) {
                seeds.add(record);
            }
        }
        if (!seeds.isEmpty()) {
            FavorService.modifyFavorLan(server, seeds, killerName, -8);
            for (var seed : seeds) {
                MisakaComputeContribution.refreshCpForRecord(server, seed);
            }
        }
    }

    private static void propagateWitnessDeaths(ServerLevel level, LivingEntity killer, LivingEntity victim) {
        if (killer == null || !(killer instanceof Player player)) {
            return;
        }
        int delta;
        if (victim instanceof Player) {
            delta = -2;
        } else if (victim instanceof Villager) {
            delta = -1;
        } else {
            return;
        }
        List<MisakaSisterRecord> seeds = new ArrayList<>();
        for (var entity : level.getEntitiesOfClass(MisakaSisterEntity.class, killer.getBoundingBox().inflate(16.0))) {
            var record = entity.rosterRecord().orElse(null);
            if (record == null || !record.awakened || !entity.hasLineOfSight(killer)) {
                continue;
            }
            if (entity.distanceToSqr(killer) > 16.0 * 16.0) {
                continue;
            }
            seeds.add(record);
        }
        if (!seeds.isEmpty()) {
            FavorService.modifyFavorLan(level.getServer(), seeds, player.getGameProfile().name(), delta);
        }
        MisakaSisterRoster.get(level.getServer()).setDirty();
    }

    private static MinecraftServer resolveServer(FavorContext context) {
        if (context.witness() != null) {
            return context.witness().level().getServer();
        }
        if (context.victim() != null) {
            return context.victim().level().getServer();
        }
        if (context.actor() != null) {
            return context.actor().level().getServer();
        }
        return null;
    }
}
