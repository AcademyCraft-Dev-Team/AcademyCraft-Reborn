package org.academy.internal.server.ability;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.server.ability.HostileTargets;
import org.academy.api.server.team.TeamRelations;
import org.academy.internal.common.ability.electromaster.skills.lv3.MagneticWeaponAttackContext;
import org.academy.internal.common.world.damagesource.PvpSetting;
import org.academy.internal.common.world.damagesource.VectorRedirectedDamageSourceInfo;

import java.util.Map;
import java.util.WeakHashMap;

/** Session-only marks, timed in physical server ticks independently of entity time scaling. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class HostileTargetRuntime {
    private static final Map<LivingEntity, Map<LivingEntity, Long>> MARKS = new WeakHashMap<>();

    private HostileTargetRuntime() {}

    public static boolean mark(LivingEntity attacker, LivingEntity target) {
        if (!canMark(attacker, target)) return false;
        MARKS.computeIfAbsent(attacker, _ -> new WeakHashMap<>())
                .put(target, now(attacker) + HostileTargets.MARK_DURATION_TICKS);
        return true;
    }

    public static boolean isMarked(LivingEntity attacker, LivingEntity target) {
        if (!canMark(attacker, target)) return false;
        var targets = MARKS.get(attacker);
        if (targets == null) return false;
        var expires = targets.get(target);
        return expires != null && now(attacker) < expires;
    }

    private static boolean canMark(LivingEntity attacker, LivingEntity target) {
        return attacker != null && target != null && attacker != target
                && attacker.level() instanceof ServerLevel && attacker.level() == target.level()
                && attacker.isAlive() && !attacker.isRemoved() && !attacker.isSpectator()
                && target.isAlive() && !target.isRemoved() && !target.isSpectator()
                && !(target instanceof Player player && player.isCreative())
                && !TeamRelations.areAllied(attacker, target) && !TeamRelations.areAllied(target, attacker)
                && !(attacker instanceof Player player && PvpSetting.shouldPrevent(player, target));
    }

    private static long now(LivingEntity attacker) {
        return Integer.toUnsignedLong(attacker.level().getServer().getTickCount());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onAttack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && event.getTarget() instanceof LivingEntity target
                && !MagneticWeaponAttackContext.shouldSuppressExhaustion(player)) {
            mark(player, target);
        }
    }

    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Post event) {
        if (!(event.getInflictedDamage() > 0.0f) || !Float.isFinite(event.getInflictedDamage())) return;
        var attacker = PvpSetting.resolveAttacker(event.getSource());
        if (attacker == null || MagneticWeaponAttackContext.shouldSuppressExhaustion(attacker)
                || !isIntentional(event.getSource())) return;
        // Pet and summoned-creature hits can resolve to an owner without being that owner's action.
        if (event.getSource().getDirectEntity() instanceof LivingEntity direct && direct != attacker) return;
        mark(attacker, event.getEntity());
    }

    private static boolean isIntentional(DamageSource source) {
        if (VectorRedirectedDamageSourceInfo.isRedirected(source)
                || source.is(net.minecraft.world.damagesource.DamageTypes.THORNS)) return false;
        if (!(source instanceof SkillDamageSource skillSource)) return true;
        if (!skillSource.canMarkHostility()) return false;
        // These skills only deal automatic damage; mixed skills opt out at their passive hit sites.
        return switch (skillSource.getSkill().getKey().getPath()) {
            case "auto_cruise_beam_cannon", "magnetic_weapon", "electrical_contact",
                 "light_shield", "darkmatter_creation", "vacuum_domain" -> false;
            default -> true;
        };
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        MARKS.entrySet().removeIf(entry -> {
            var attacker = entry.getKey();
            entry.getValue().entrySet().removeIf(target ->
                    !canMark(attacker, target.getKey()) || now(attacker) >= target.getValue());
            return entry.getValue().isEmpty();
        });
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof LivingEntity living) remove(living);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        remove(event.getEntity());
    }

    private static void remove(LivingEntity entity) {
        MARKS.remove(entity);
        MARKS.values().forEach(targets -> targets.remove(entity));
    }

    @SubscribeEvent
    public static void onStop(ServerStoppedEvent event) {
        MARKS.clear();
    }
}
