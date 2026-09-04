package org.academy.internal.common.ability.program;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.academy.AcademyCraft;

import java.util.*;

/**
 * Server-authoritative automatic entry dispatch for every programmable ability category.
 */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class AbilityProgramTriggerRuntime {
    private static final Map<UUID, MovementState> MOVEMENT = new HashMap<>();
    private static final Map<UUID, Object> PENDING_MELEE = new HashMap<>();
    private static final Set<UUID> EXECUTING = new HashSet<>();
    private static final Map<UUID, Object> DAMAGE_ATTACKERS = new HashMap<>();
    private static final Map<UUID, Float> DAMAGE_AMOUNTS = new HashMap<>();
    private static final Map<UUID, Object> MELEE_TARGETS = new HashMap<>();

    private AbilityProgramTriggerRuntime() {
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PENDING_MELEE.put(player.getUUID(), event.getTarget());
        }
    }

    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            dispatch(player, ProgramTriggers.Type.MOVEMENT,
                    CommonProgramNodeCatalog.MovementCondition.JUMP);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            var attacker = event.getSource().getEntity();
            if (attacker == null) attacker = event.getSource().getDirectEntity();
            var id = player.getUUID();
            var previous = DAMAGE_ATTACKERS.get(id);
            var previousAmount = DAMAGE_AMOUNTS.put(id, event.getAmount());
            if (attacker == null) DAMAGE_ATTACKERS.remove(id);
            else DAMAGE_ATTACKERS.put(id, attacker);
            try {
                dispatch(player, ProgramTriggers.Type.HURT, null);
            } finally {
                if (previous == null) DAMAGE_ATTACKERS.remove(id);
                else DAMAGE_ATTACKERS.put(id, previous);
                if (previousAmount == null) DAMAGE_AMOUNTS.remove(id);
                else DAMAGE_AMOUNTS.put(id, previousAmount);
            }
        }
    }

    static java.util.Optional<Object> currentDamageAttacker(Object entity) {
        if (!(entity instanceof ServerPlayer player)) return java.util.Optional.empty();
        return java.util.Optional.ofNullable(DAMAGE_ATTACKERS.get(player.getUUID()));
    }

    static OptionalDouble currentDamageAmount(Object entity) {
        if (!(entity instanceof ServerPlayer player)) return OptionalDouble.empty();
        var amount = DAMAGE_AMOUNTS.get(player.getUUID());
        return amount == null ? OptionalDouble.empty() : OptionalDouble.of(amount);
    }

    static Optional<Object> currentMeleeTarget(Object entity) {
        if (!(entity instanceof ServerPlayer player)) return Optional.empty();
        return Optional.ofNullable(MELEE_TARGETS.get(player.getUUID()));
    }

    static ProgramInvocationContext invocation(
            ServerPlayer player,
            UUID programId,
            int slot,
            ProgramTriggers.Type trigger,
            CommonProgramNodeCatalog.MovementCondition movement,
            long loopIndex
    ) {
        var id = player.getUUID();
        return new ProgramInvocationContext(
                programId,
                slot,
                trigger,
                movement,
                loopIndex,
                DAMAGE_ATTACKERS.get(id),
                DAMAGE_AMOUNTS.get(id),
                MELEE_TARGETS.get(id)
        );
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var id = player.getUUID();
        var current = MovementState.capture(player);
        var previous = MOVEMENT.put(id, current);
        if (previous != null) {
            if (previous.sneaking != current.sneaking) {
                dispatch(player, ProgramTriggers.Type.MOVEMENT,
                        CommonProgramNodeCatalog.MovementCondition.SNEAK);
            }
            if (previous.sprinting != current.sprinting) {
                dispatch(player, ProgramTriggers.Type.MOVEMENT,
                        CommonProgramNodeCatalog.MovementCondition.SPRINT);
            }
            if (previous.elytra != current.elytra) {
                dispatch(player, ProgramTriggers.Type.MOVEMENT,
                        CommonProgramNodeCatalog.MovementCondition.ELYTRA);
            }
            if (previous.swimming != current.swimming) {
                dispatch(player, ProgramTriggers.Type.MOVEMENT,
                        CommonProgramNodeCatalog.MovementCondition.SWIM);
            }
        }
        var meleeTarget = PENDING_MELEE.remove(id);
        if (meleeTarget != null) {
            var previousTarget = MELEE_TARGETS.put(id, meleeTarget);
            try {
                dispatch(player, ProgramTriggers.Type.MELEE, null);
            } finally {
                if (previousTarget == null) MELEE_TARGETS.remove(id);
                else MELEE_TARGETS.put(id, previousTarget);
            }
        }
        dispatch(player, ProgramTriggers.Type.LOOP, null);
        dispatch(player, ProgramTriggers.Type.HEALTH, null);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        var id = event.getEntity().getUUID();
        MOVEMENT.remove(id);
        PENDING_MELEE.remove(id);
        EXECUTING.remove(id);
        DAMAGE_ATTACKERS.remove(id);
        DAMAGE_AMOUNTS.remove(id);
        MELEE_TARGETS.remove(id);
        ProgramTriggers.clear(id);
    }

    private static void dispatch(
            ServerPlayer player,
            ProgramTriggers.Type type,
            CommonProgramNodeCatalog.MovementCondition movement
    ) {
        var id = player.getUUID();
        if (!EXECUTING.add(id)) return;
        try {
            AbilityProgramManager.executeTriggered(player, type, movement);
        } finally {
            EXECUTING.remove(id);
        }
    }

    private record MovementState(
            boolean sneaking,
            boolean sprinting,
            boolean elytra,
            boolean swimming
    ) {
        private static MovementState capture(ServerPlayer player) {
            return new MovementState(
                    player.isShiftKeyDown(),
                    player.isSprinting(),
                    player.isFallFlying(),
                    player.isSwimming()
            );
        }
    }
}
