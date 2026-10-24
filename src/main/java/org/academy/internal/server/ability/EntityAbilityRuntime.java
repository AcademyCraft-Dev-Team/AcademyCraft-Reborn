package org.academy.internal.server.ability;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.academy.api.common.ability.*;
import org.academy.api.server.ability.*;
import org.academy.api.server.ability.electromaster.ElectromasterActions;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.misaka.*;
import org.academy.internal.common.world.entity.misaka.favor.FavorService;
import org.academy.internal.server.world.level.storage.MisakaSisterRoster;
import java.util.List;
import java.util.function.Predicate;

@EventBusSubscriber
public final class EntityAbilityRuntime {
    private static boolean retaliating;
    private EntityAbilityRuntime() {}

    public static AbilityActorProfile sisterProfile() {
        return new AbilityActorProfile(Identifier.fromNamespaceAndPath("academy", "misaka_lv2"),
                Skills.ARC_GENERATE.get().getCategory().getKey(), 2, 100, 1, 1,
                List.of(Skills.ELECTRICAL_CONTACT.get().getKey(), Skills.ARC_GENERATE.get().getKey(),
                        Skills.THUNDER_LANCE.get().getKey(), Skills.LIGHTNING_NOVA.get().getKey()));
    }

    public static void install() {
        EntityAbilityService.registerProvider(EntityTypes.MISAKA_SISTER.get(), new AbilityActorProvider() {
            public AbilityActorState state(LivingEntity actor) {
                var sister = (MisakaSisterEntity) actor;
                var record = sister.rosterRecord().orElse(null);
                if (record == null) return null;
                if (record.ability == null) { record.ability = new AbilityActorState(sisterProfile()); changed(actor); }
                return record.ability;
            }
            public void setState(LivingEntity actor, AbilityActorState state) {
                ((MisakaSisterEntity) actor).rosterRecord().orElseThrow().ability = state;
            }
            public void changed(LivingEntity actor) {
                MisakaSisterRoster.get(((ServerLevel) actor.level()).getServer()).setDirty();
            }
            public boolean canAct(LivingEntity actor) {
                var sister = (MisakaSisterEntity) actor;
                return sister.isAlive() && sister.isAwakened() && !sister.isStarving() && !sister.isIncapacitated()
                        && !sister.isSleeping() && !sister.isNoAi() && !sister.isPassenger();
            }
            public boolean mayTarget(LivingEntity actor, LivingEntity target) {
                if (target instanceof MisakaSisterEntity) return false;
                if (target instanceof Player player) return ((MisakaSisterEntity) actor).rosterRecord().map(record -> {
                    var relation = FavorService.relation(record, player.getGameProfile().name());
                    return relation == MobRelation.HOSTILE || relation == MobRelation.DEADLY_ENEMY;
                }).orElse(false);
                return HostileTargets.isHostile(actor, target);
            }
        });
        register(Skills.ARC_GENERATE.get(), 1, 40, 16, 0);
        register(Skills.THUNDER_LANCE.get(), 2, 120, 32, 1);
        register(Skills.LIGHTNING_NOVA.get(), 2, 300, 16, 2);
        register(Skills.ELECTRICAL_CONTACT.get(), 1, 0, 2, 3);
    }

    private static void register(Skill skill, int level, int cooldown, double range, int kind) {
        EntityAbilityService.register(new AbilitySkillExecutor() {
            public Skill skill() { return skill; }
            public int minimumLevel() { return level; }
            public int cooldownTicks() { return cooldown; }
            public double range() { return range; }
            public boolean requiresTarget() { return kind < 2; }
            public boolean permanent() { return kind == 3; }
            public AbilityExecutionHandle start(AbilityActorContext actor, AbilityCastRequest request, Predicate<LivingEntity> allowed) {
                switch (kind) {
                    case 0 -> ElectromasterActions.fireArc(actor, request.target(), allowed);
                    case 1 -> ElectromasterActions.fireLance(actor, request.target(), allowed);
                    case 2 -> { return ElectromasterActions.lightningNova(actor, allowed); }
                    case 3 -> { return ElectromasterActions.electricalContact(actor, allowed); }
                    default -> throw new IllegalStateException();
                }
                return null;
            }
        });
    }

    @SubscribeEvent
    public static void tick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity actor) || actor instanceof Player || !(actor.level() instanceof ServerLevel)) return;
        if (actor instanceof MisakaSisterEntity sister && sister.getWanderStyle() == WanderStyle.WAITING)
            EntityAbilityService.stopAll(actor);
        EntityAbilityService.tick(actor);
        if (actor instanceof MisakaSisterEntity sister && sister.getWanderStyle() != WanderStyle.WAITING
                && EntityAbilityService.canAct(actor) && Math.floorMod(actor.tickCount + actor.getId(), 10) == 0) {
            EntityAbilityService.tryCast(actor, AbilityCastRequest.autonomous(Skills.ELECTRICAL_CONTACT.get().getKey(), null));
        }
    }

    @SubscribeEvent
    public static void retaliate(LivingIncomingDamageEvent event) {
        var actor = event.getEntity();
        var skill = Skills.ELECTRICAL_CONTACT.get();
        if (retaliating || !(actor.level() instanceof ServerLevel) || actor instanceof Player
                || !EntityAbilityService.isRunning(actor, skill.getKey()) || !EntityAbilityService.canAct(actor)
                || !(event.getSource().getEntity() instanceof LivingEntity attacker)
                || !EntityAbilityService.mayTarget(actor, attacker)) return;
        var state = EntityAbilityService.inspect(actor).orElseThrow();
        retaliating = true;
        try {
            ElectromasterActions.contactHit(actor, attacker, skill,
                    (float) (2 * state.profile().abilityPower() * state.profile().damageMultiplier()),
                    target -> EntityAbilityService.mayTarget(actor, target), true);
        } finally { retaliating = false; }
    }

    @SubscribeEvent
    public static void leave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof LivingEntity actor && actor.level() instanceof ServerLevel) EntityAbilityService.stopAll(actor);
    }

    @SubscribeEvent
    public static void stop(ServerStoppingEvent event) {
        for (var level : event.getServer().getAllLevels()) for (var entity : level.getAllEntities())
            if (entity instanceof LivingEntity actor) EntityAbilityService.stopAll(actor);
    }
}
