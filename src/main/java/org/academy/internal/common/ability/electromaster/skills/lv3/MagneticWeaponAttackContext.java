package org.academy.internal.common.ability.electromaster.skills.lv3;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.KineticWeapon;
import org.academy.internal.common.ability.Skills;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Sidecar attribution for a synchronous vanilla attack performed by Magnetic Weapon.
 */
public final class MagneticWeaponAttackContext implements AutoCloseable {
    private static final ThreadLocal<Deque<Frame>> FRAMES = ThreadLocal.withInitial(ArrayDeque::new);

    private final Frame frame;
    private boolean closed;

    private MagneticWeaponAttackContext(ServerPlayer player, LivingEntity target, float playerDamageMultiplier) {
        frame = new Frame(player, target, Math.max(0.0f, playerDamageMultiplier),
                SpearEffect.from(player));
        FRAMES.get().push(frame);
    }

    static MagneticWeaponAttackContext open(
            ServerPlayer player,
            LivingEntity target,
            float playerDamageMultiplier
    ) {
        return new MagneticWeaponAttackContext(player, target, playerDamageMultiplier);
    }

    public static float prepareDamage(
            Player player,
            DamageSource source,
            float damage
    ) {
        var frame = currentFrame(player);
        if (frame == null || frame.damageSource != null) return damage;
        frame.damageSource = source;
        return MagneticWeapon.Server.calculateDamage(
                damage + frame.spearEffect.additionalDamage(), frame.playerDamageMultiplier);
    }

    public static boolean isCurrentAttack(Player player, Entity target) {
        var frame = currentFrame(player);
        return frame != null && frame.target == target;
    }

    public static boolean shouldSuppressExhaustion(Player player) {
        return currentFrame(player) != null;
    }

    public static boolean onHurt(DamageSource source, LivingEntity target, float damage) {
        var frame = findDamageFrame(source, target);
        if (frame == null || frame.hurtReported) return false;
        frame.hurtReported = true;
        frame.spearEffect.apply(frame.player, target);
        Skills.MAGNETIC_WEAPON.get().onHurt(frame.player, target, damage);
        return true;
    }

    public static boolean onKill(DamageSource source, LivingEntity target) {
        var frame = findDamageFrame(source, target);
        if (frame == null || frame.killReported) return false;
        frame.killReported = true;
        Skills.MAGNETIC_WEAPON.get().onKill(frame.player, target);
        return true;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        var frames = FRAMES.get();
        if (frames.peek() != frame) {
            throw new IllegalStateException("Magnetic weapon attack contexts closed out of order");
        }
        frames.pop();
        if (frames.isEmpty()) FRAMES.remove();
    }

    private static Frame currentFrame(Player player) {
        for (var frame : FRAMES.get()) {
            if (frame.player == player) return frame;
        }
        return null;
    }

    private static Frame findDamageFrame(DamageSource source, LivingEntity target) {
        for (var frame : FRAMES.get()) {
            if (frame.target != target) continue;
            if (frame.damageSource == source
                    || frame.damageSource == null && source.getEntity() == frame.player) return frame;
        }
        return null;
    }

    private static final class Frame {
        private final ServerPlayer player;
        private final LivingEntity target;
        private final float playerDamageMultiplier;
        private final SpearEffect spearEffect;
        private DamageSource damageSource;
        private boolean hurtReported;
        private boolean killReported;

        private Frame(ServerPlayer player, LivingEntity target, float playerDamageMultiplier,
                      SpearEffect spearEffect) {
            this.player = player;
            this.target = target;
            this.playerDamageMultiplier = playerDamageMultiplier;
            this.spearEffect = spearEffect;
        }
    }

    private record SpearEffect(float additionalDamage, boolean knockback, boolean dismount) {
        private static final SpearEffect NONE = new SpearEffect(0.0f, false, false);

        private static SpearEffect from(ServerPlayer player) {
            var stack = player.getItemBySlot(EquipmentSlot.MAINHAND);
            var kineticWeapon = stack.get(DataComponents.KINETIC_WEAPON);
            if (kineticWeapon == null) return NONE;
            var speed = KineticWeapon.getMotion(player).length();
            if (!Double.isFinite(speed)) return NONE;
            var damage = kineticWeapon.damageConditions()
                    .filter(condition -> condition.test(0, speed, speed, 1.0))
                    .map(_ -> (float) Math.floor(speed * kineticWeapon.damageMultiplier()))
                    .orElse(0.0f);
            var knockback = kineticWeapon.knockbackConditions()
                    .map(condition -> condition.test(0, speed, speed, 1.0)).orElse(false);
            var dismount = kineticWeapon.dismountConditions()
                    .map(condition -> condition.test(0, speed, speed, 1.0)).orElse(false);
            return new SpearEffect(damage, knockback, dismount);
        }

        private void apply(ServerPlayer player, LivingEntity target) {
            if (dismount && target.isPassenger()) target.stopRiding();
            if (!knockback) return;
            var direction = target.position().subtract(player.position());
            if (direction.horizontalDistanceSqr() <= 1.0e-8) {
                direction = player.getLookAngle();
            }
            var horizontal = direction.multiply(1.0, 0.0, 1.0).normalize().scale(0.4);
            target.push(horizontal.x, 0.1, horizontal.z);
            if (target instanceof ServerPlayer serverPlayer) {
                serverPlayer.connection.send(new ClientboundSetEntityMotionPacket(serverPlayer));
            }
        }
    }
}
