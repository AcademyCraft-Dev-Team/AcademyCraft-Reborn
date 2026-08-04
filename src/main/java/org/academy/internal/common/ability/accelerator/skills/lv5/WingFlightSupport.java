package org.academy.internal.common.ability.accelerator.skills.lv5;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.attachment.AttachmentType;
import org.academy.api.client.input.InputSystem;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.common.ability.accelerator.skills.lv4.StormWing;
import org.academy.internal.common.entitycontrol.EntityControlApi;
import org.academy.internal.common.world.damagesource.CTADamageUtil;
import org.academy.internal.common.world.damagesource.CTAEntityActuallyHurt;
import org.academy.internal.common.world.damagesource.CtaFriendlyFireWhitelist;
import org.academy.mixin.common.EntitySharedFlagInvoker;

import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_A;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_D;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_S;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_W;

final class WingFlightSupport {
    static final double ATTACK_RANGE = 32.0;
    static final double FAN_COS_THRESHOLD = 0.35;
    static final float MAX_HEALTH_DAMAGE_RATIO = 0.01f;
    static final float FIXED_DAMAGE = 10.0f;
    private static final long BOOST_GRACE_TICKS = 5L;

    private WingFlightSupport() {
    }

    static void clientTick(boolean active, Consumer<StormWing.State> sender) {
        if (!active) return;
        var minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        if (minecraft.gui.screen() != null) {
            sender.accept(StormWing.State.KEEP);
            return;
        }
        if (InputSystem.isDown(InputSystem.InputType.KEYBOARD, GLFW_KEY_SPACE)) {
            sender.accept(StormWing.State.BOOST);
            return;
        }

        var front = InputSystem.isDown(InputSystem.InputType.KEYBOARD, GLFW_KEY_W);
        var back = InputSystem.isDown(InputSystem.InputType.KEYBOARD, GLFW_KEY_S);
        var left = InputSystem.isDown(InputSystem.InputType.KEYBOARD, GLFW_KEY_A);
        var right = InputSystem.isDown(InputSystem.InputType.KEYBOARD, GLFW_KEY_D);
        var sent = false;
        if (front != back) {
            sender.accept(front ? StormWing.State.FRONT : StormWing.State.BACK);
            sent = true;
        }
        if (left != right) {
            sender.accept(left ? StormWing.State.LEFT : StormWing.State.RIGHT);
            sent = true;
        }
        if (!sent) sender.accept(StormWing.State.KEEP);
    }

    static void applyControl(ServerPlayer player, StormWing.State state, Map<UUID, Long> lastBoostTick) {
        if (state == StormWing.State.BOOST) {
            lastBoostTick.put(player.getUUID(), player.level().getGameTime());
        }
        switch (state) {
            case FRONT -> {
                var movement = player.getLookAngle().add(0, 0.35, 0).scale(0.2);
                player.push(movement.x, movement.y * 1.5, movement.z);
            }
            case BACK -> {
                var movement = player.getLookAngle().add(0, -0.35, 0).scale(-0.2);
                player.push(movement.x, movement.y, movement.z);
            }
            case LEFT -> {
                var look = player.getLookAngle();
                var movement = new Vec3(look.z, -look.y + 0.15, -look.x).scale(0.2);
                player.push(movement.x, movement.y, movement.z);
            }
            case RIGHT -> {
                var look = player.getLookAngle();
                var movement = new Vec3(-look.z, -look.y + 0.15, look.x).scale(0.2);
                player.push(movement.x, movement.y, movement.z);
            }
            case KEEP -> {
                if (Math.abs(player.getDeltaMovement().y) > 0.25) {
                    player.setDeltaMovement(player.getDeltaMovement().multiply(0.995, 0.685, 0.995));
                } else {
                    player.setDeltaMovement(player.getDeltaMovement().multiply(0.995, 0, 0.995));
                }
                player.resetFallDistance();
            }
            case BOOST -> {
                var movement = player.getLookAngle().scale(2.0);
                player.push(movement.x, movement.y, movement.z);
                player.resetFallDistance();
            }
        }
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
    }

    static boolean tick(ServerPlayer player, Skill skill, AttachmentType<Boolean> attachment,
                        Map<UUID, Long> lastBoostTick) {
        var active = skill.isEnabled(player) && player.isAlive() && !player.hasDisconnected();
        if (active) {
            var system = AbilitySystemServer.getSystem(player);
            active = system.ensurePermanentOccupation(
                    player.getUUID(),
                    skill.getMaintenanceCost(skill.getLevel(player)),
                    skill
            );
            if (!active && skill.isEnabled(player)) skill.toggle(player);
            if (active && player.tickCount % 20 == 0
                    && !system.tryTimedOccupation(player.getUUID(), upkeepCost(skill), skill, 1)) {
                forceDeactivateSkill(player, skill);
                active = false;
            }
        }
        if (!active && skill.getRuntimeData(player).map(data -> data.isEnabled()).orElse(false)) {
            forceDeactivateSkill(player, skill);
        }
        sync(player, attachment, active, lastBoostTick);
        return active;
    }

    static void forceDeactivateSkill(ServerPlayer player, Skill skill) {
        var data = skill.getRuntimeData(player).orElse(null);
        if (data == null || !data.isEnabled()) return;
        var system = AbilitySystemServer.getSystem(player);
        system.toggleSkill(player.getUUID(), skill.getKeyString());
        system.releaseMaintenanceOccupation(player.getUUID(), skill.getKeyString());
    }

    static void sync(ServerPlayer player, AttachmentType<Boolean> attachment, boolean active,
                     Map<UUID, Long> lastBoostTick) {
        var wasActive = player.getData(attachment);
        if (wasActive != active) {
            player.setData(attachment, active);
            player.syncData(attachment);
        }
        if (!active) {
            // All wing skills call this every player tick. Inactive cleanup must be transition-only.
            lastBoostTick.remove(player.getUUID());
            if (wasActive) {
                ((EntitySharedFlagInvoker) player).academy$setSharedFlag(7, false);
            }
            return;
        }
        var boostTick = lastBoostTick.get(player.getUUID());
        ((EntitySharedFlagInvoker) player).academy$setSharedFlag(
                7,
                boostTick != null && player.level().getGameTime() - boostTick <= BOOST_GRACE_TICKS
        );
    }

    static int fanAttack(ServerPlayer player, Skill skill) {
        var level = (ServerLevel) player.level();
        var origin = player.getEyePosition();
        var forward = player.getLookAngle().normalize();
        var searchBox = player.getBoundingBox().inflate(ATTACK_RANGE);
        var baseDamage = (float) player.getAttributeBaseValue(Attributes.ATTACK_DAMAGE);
        var multiplier = AbilitySystemServer.getSystem(player).getPlayerDamageMultiplier(player.getUUID());
        var source = SkillDamageSource.of(
                player,
                skill,
                net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL
        );
        var hitCount = new int[1];

        level.playSound(null, player, SoundEvents.PLAYER_ATTACK_NODAMAGE,
                SoundSource.PLAYERS, 0.85f, 0.9f + player.getRandom().nextFloat() * 0.15f);
        CTADamageUtil.runGuarded(player, () -> {
            for (var target : level.getEntitiesOfClass(LivingEntity.class, searchBox,
                    entity -> entity != player && entity.isAlive())) {
                if (CtaFriendlyFireWhitelist.shouldProtect(player, target)) continue;
                var targetCenter = target.position().add(0, target.getBbHeight() * 0.5, 0);
                if (!isInFan(origin, forward, targetCenter, ATTACK_RANGE, FAN_COS_THRESHOLD)) continue;
                var trueMaxHealth = EntityControlApi.getTrueMaxHealth(target);
                if (!Float.isFinite(trueMaxHealth) || trueMaxHealth <= 0.0f) {
                    trueMaxHealth = target.getMaxHealth();
                }
                var damage = (baseDamage + trueMaxHealth * MAX_HEALTH_DAMAGE_RATIO + FIXED_DAMAGE)
                        * multiplier;
                if (!Float.isFinite(damage) || damage <= 0) continue;
                new CTAEntityActuallyHurt(target).actuallyHurt(source, damage, true);
                hitCount[0]++;
            }
        });
        return hitCount[0];
    }

    static boolean trySweepCost(ServerPlayer player, Skill skill) {
        return AbilitySystemServer.getSystem(player)
                .tryTimedOccupation(player.getUUID(), 20.0f, skill, 10);
    }

    private static float upkeepCost(Skill skill) {
        if (skill instanceof PlatinumWing) return 80.0f;
        if (skill instanceof WhiteWing) return 40.0f;
        return 20.0f;
    }

    static void broadcastSweep(ServerPlayer player, AdvancedWingSweepPacket.WingKind kind) {
        var random = player.getRandom();
        var packet = new AdvancedWingSweepPacket(
                kind,
                player.getId(),
                random.nextBoolean(),
                -24.0f + random.nextFloat() * 48.0f,
                -8.0f + random.nextFloat() * 16.0f
        );
        for (var other : ((ServerLevel) player.level()).players()) {
            if (other.distanceToSqr(player) <= 128.0 * 128.0) {
                org.misaka.MisakaNetworkServer.send(other, packet);
            }
        }
    }

    static boolean isInFan(Vec3 origin, Vec3 forward, Vec3 target, double range, double cosThreshold) {
        if (origin == null || forward == null || target == null || range <= 0) return false;
        var delta = target.subtract(origin);
        var distanceSqr = delta.lengthSqr();
        if (distanceSqr <= 1.0E-6 || distanceSqr > range * range) return false;
        return forward.normalize().dot(delta.normalize()) >= cosThreshold;
    }
}
