package org.academy.internal.common.ability.accelerator.skills.lv1;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.resources.R;
import org.academy.api.client.util.ClientUtil;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.DevCondition;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.common.util.ViewTargetScanner;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.ability.HostileTargets;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.academy.internal.common.entitycontrol.EntityMotionGuard;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.network.SpawnVfxGraphPacket;
import org.academy.internal.common.world.damagesource.CTADamageUtil;
import org.academy.internal.common.world.damagesource.DamageTypes;
import org.academy.internal.common.world.damagesource.SkillDamageUtil;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.List;
import java.util.Map;

public final class VectorBlast extends Skill {
    private static final String BLAST_STACK_GROUP = "vector_blast:blast";
    private static final int BLAST_STACK_LIMIT = 10;
    static final double RANGE = 64.0;
    static final double BEAM_RADIUS = 1.0;
    static final double ABYSS_TARGET_RANGE = 32.0;
    static final double ABYSS_RADIUS = 8.0;
    static final float BASE_DAMAGE = 10.0f;

    public VectorBlast() {
        super(Builder
                .of(AbilityCategories.ACCELERATOR.get())
                .damage()
                .level(AbilityLevel.LEVEL1)
                .energyCost(5_000)
                .cpCost(10)
                .iterationTicks(5)
                .maxStacks(NO_STACK_LIMIT)
                .dependsOn(Skills.VECTOR_ACCEL)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL1))
        );
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);

        var blastBinding = InputSystem.combo(InputSystem.InputType.MOUSE,
                InputConstants.MOUSE_BUTTON_LEFT, InputConstants.RELEASE, InputConstants.MOD_SHIFT);
        InputSystem.addKeyBinding(
                Client.KEY_NAME_USE,
                Client.CONFIG.getKeyBinding(Client.KEY_NAME_USE, blastBinding),
                Client::use
        );
        for (var suffix : List.of("_pull_start", "_pull_stop", "_push_start", "_push_stop")) {
            Client.CONFIG.removeKeyBinding(SkillNames.VECTOR_BLAST + suffix);
        }
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    static boolean isInsideBeam(Vec3 origin, Vec3 direction, Vec3 target, double length) {
        return isInsideBeam(origin, direction, target, length, BEAM_RADIUS);
    }

    static boolean isInsideBeam(Vec3 origin, Vec3 direction, Vec3 target, double length, double radius) {
        return direction.lengthSqr() > 1.0e-6
                && ViewTargetScanner.matches(
                origin,
                direction,
                length,
                ViewTargetScanner.openCenteredCylinder(radius),
                new AABB(target, target)
        );
    }

    static Vec3 blastImpulse(Vec3 direction, double distance, double range) {
        if (direction.lengthSqr() < 1.0e-8 || range <= 0) return Vec3.ZERO;
        double strength = 3.2 * (1.0 - 0.55 * Math.clamp(distance / range, 0.0, 1.0));
        return direction.normalize().scale(strength).add(0, 0.35, 0);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.ACCELERATOR.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.VECTOR_BLAST.get(), List.of(), R.textures.vector_blast_icon, 30, 60)
        );
        public static final String KEY_NAME_USE = SkillNames.VECTOR_BLAST + "_use";
        public static Config CONFIG = new Config();

        private Client() {
        }

        private static void use(InputSystem.BindingContext ignored) {
            sendControl(UsePacket.Action.BLAST);
        }

        private static void sendControl(UsePacket.Action action) {
            var player = Minecraft.getInstance().player;
            if (player == null || ClientUtil.hasScreen()
                    || !AbilitySystemClient.canUseSkill(Skills.VECTOR_BLAST.get())) return;
            MisakaNetworkClient.send(new UsePacket(action));
        }

        public static final class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public Config getDefault() {
                    return new Config();
                }

                @Override
                public Class<Config> getTypeClass() {
                    return Config.class;
                }
            }
        }
    }

    public static final class Server {
        private Server() {
        }

        @SubscribePacket
        public static void handle(UsePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            if (packet.action == UsePacket.Action.BLAST) tryAutomatedAttack(player);
        }

        public static boolean tryAutomatedAttack(ServerPlayer player) {
            if (!(player.level() instanceof ServerLevel level)) return false;
            return Skills.VECTOR_BLAST.get().executeActive(
                    player, BLAST_STACK_GROUP, BLAST_STACK_LIMIT,
                    (context, actualCost) -> fire(player, level));
        }

        private static void fire(ServerPlayer player, ServerLevel level) {
            var skill = Skills.VECTOR_BLAST.get();
            var origin = player.getEyePosition();
            var direction = player.getLookAngle();
            if (direction.lengthSqr() <= 1.0e-6) return;
            direction = direction.normalize();
            var range = skill.scaledRange(player,
                    skill.hasProficiencyMilestone(player, 2) ? 72.0 : RANGE);
            var beamRadius = skill.hasProficiencyMilestone(player, 2) ? BEAM_RADIUS * 1.2 : BEAM_RADIUS;
            var end = origin.add(direction.scale(range));

            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.0f, 1.0f);
            SpawnVfxGraphPacket.broadcast(level,
                    Identifier.fromNamespaceAndPath("academy", "vfxgraph/vector_blast"),
                    origin, direction, -1, 1f, 0.65f,
                    Map.of("length", (float) range, "width", (float) beamRadius,
                            "seed", (float) player.getRandom().nextInt(65536)));

            var system = AbilitySystemServer.getSystem(player);
            var damage = BASE_DAMAGE
                    * system.getPlayerAbilityPowerMultiplier(player.getUUID())
                    * system.getPlayerDamageMultiplier(player.getUUID());
            var source = SkillDamageSource.of(
                    player,
                    skill,
                    DamageTypes.VEC
            );
            for (var target : ViewTargetScanner.scan(
                    level,
                    LivingEntity.class,
                    origin,
                    direction,
                    range,
                    ViewTargetScanner.openCenteredCylinder(beamRadius),
                    entity -> entity != player && entity.isAlive()
            )) {
                if (HostileTargets.isDetectable(player, target)) {
                    SkillDamageUtil.applyVerifiedTrueHealth(target, source, damage);
                }
                var impulse = blastImpulse(direction, target.getBoundingBox().getCenter().distanceTo(origin), range);
                EntityMotionGuard.runWithMotionSource(player, () -> target.setDeltaMovement(
                        target.getDeltaMovement().scale(0.25).add(impulse)));
                target.hurtMarked = true;
                if (target instanceof ServerPlayer victim) {
                    victim.connection.send(new ClientboundSetEntityMotionPacket(victim));
                }
            }

            if (skill.hasProficiencyMilestone(player, 3)) {
                var blastArea = new AABB(end, end).inflate(skill.scaledRange(player, 3.0));
                for (var target : level.getEntitiesOfClass(
                        LivingEntity.class,
                        blastArea,
                        entity -> HostileTargets.isDetectable(player, entity))) {
                    SkillDamageUtil.applyVerifiedTrueHealth(target, source, damage * 0.4f);
                }
            }

            if (player.getData(AttachmentTypes.CROSSING_THE_ABYSS_ACTIVE.get())) {
                fireAbyssBlast(player, level, origin, direction, damage, source);
            }
        }

        private static void fireAbyssBlast(ServerPlayer player, ServerLevel level, Vec3 origin,
                                           Vec3 direction, float damage, SkillDamageSource source) {
            var skill = Skills.VECTOR_BLAST.get();
            var abyssTargetRange = skill.scaledRange(player, ABYSS_TARGET_RANGE);
            var abyssRadius = skill.scaledRange(player, ABYSS_RADIUS);
            var end = origin.add(direction.scale(abyssTargetRange));
            var entityHit = ProjectileUtil.getEntityHitResult(
                    level, player, origin, end,
                    player.getBoundingBox().expandTowards(direction.scale(abyssTargetRange)).inflate(1.0),
                    entity -> entity instanceof LivingEntity living
                            && living != player
                            && living.isAlive(),
                    0.3f
            );

            Vec3 center;
            if (entityHit != null && entityHit.getType() != HitResult.Type.MISS) {
                center = entityHit.getLocation();
            } else {
                var blockHit = level.clip(new ClipContext(
                        origin, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
                center = blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getLocation();
            }

            var area = new AABB(center, center).inflate(abyssRadius);
            for (var target : level.getEntitiesOfClass(
                    LivingEntity.class, area, entity -> HostileTargets.isDetectable(player, entity))) {
                CTADamageUtil.applyCompositeDamage(
                        target,
                        player,
                        SkillDamageSource.of(
                                player,
                                Skills.VECTOR_BLAST.get(),
                                DamageTypes.CTA
                        ),
                        damage
                );
            }
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class UsePacket extends Packet<ServerGamePacketListenerImpl, UsePacket> {
        public static final StreamCodec<ByteBuf, UsePacket> CODEC = ByteBufCodecs.VAR_INT.map(
                ordinal -> {
                    if (ordinal != 0) throw new IllegalArgumentException("Obsolete vector blast action");
                    return new UsePacket(Action.BLAST);
                },
                packet -> packet.action.ordinal()
        );
        private final Action action;

        public UsePacket(Action action) {
            this.action = action;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, UsePacket> getPacketType() {
            return PacketTypes.VECTOR_BLAST_USE.get();
        }

        public enum Action {
            BLAST
        }
    }
}
