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
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.common.ability.AbilityCategoryNames;
import org.academy.internal.common.ability.mentalout.precision.PrecisionOperationManager;

import java.util.*;

/**
 * Server-authoritative automatic entry dispatch for every programmable ability category.
 */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class AbilityProgramTriggerRuntime {
    private static final Map<UUID, MovementState> MOVEMENT = new HashMap<>();
    private static final Set<UUID> PENDING_MELEE = new HashSet<>();
    private static final Set<UUID> EXECUTING = new HashSet<>();
    private static final Map<UUID, Object> DAMAGE_ATTACKERS = new HashMap<>();

    private AbilityProgramTriggerRuntime() {
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PENDING_MELEE.add(player.getUUID());
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
            if (attacker == null) DAMAGE_ATTACKERS.remove(id);
            else DAMAGE_ATTACKERS.put(id, attacker);
            try {
                dispatch(player, ProgramTriggers.Type.HURT, null);
            } finally {
                if (previous == null) DAMAGE_ATTACKERS.remove(id);
                else DAMAGE_ATTACKERS.put(id, previous);
            }
        }
    }

    static java.util.Optional<Object> currentDamageAttacker(Object entity) {
        if (!(entity instanceof ServerPlayer player)) return java.util.Optional.empty();
        return java.util.Optional.ofNullable(DAMAGE_ATTACKERS.get(player.getUUID()));
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
        if (PENDING_MELEE.remove(id)) {
            dispatch(player, ProgramTriggers.Type.MELEE, null);
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
            var category = AbilitySystemServer.getSystem(player)
                    .getPlayerAbilityCategory(id);
            if (category == null) return;
            if (category.getKey().equals(AcademyCraft.academy(AbilityCategoryNames.MENTALOUT))) {
                PrecisionOperationManager.executeTriggered(player, type, movement);
            } else {
                AbilityProgramManager.executeTriggered(player, type, movement);
            }
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
