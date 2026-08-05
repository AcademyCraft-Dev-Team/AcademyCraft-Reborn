package org.academy.internal.common.ability.electromaster.skills.lv5;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
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
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.DevCondition;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.arc.ArcPath;
import org.academy.api.common.arc.modifier.JaggedModifier;
import org.academy.api.common.arc.path.LinePath;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.common.util.MathUtil;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.electromaster.skills.lv3.ThunderLance;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.world.damagesource.CTAEntityActuallyHurt;
import org.academy.internal.common.world.entity.skill.ArcEffect;
import org.jspecify.annotations.Nullable;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.ArrayList;
import java.util.List;

public class Thunderclap extends Skill {
    static final double RANGE = 64.0;
    static final double RADIUS = 5.0;
    static final float HEALTH_DAMAGE_RATIO = 0.20f;

    public Thunderclap() {
        super(Builder
                .of(AbilityCategories.ELECTROMASTER.get())
                .level(AbilityLevel.LEVEL5)
                .energyCost(100_000)
                .cpCost(100)
                .iterationTicks(80)
                .maxStacks(NO_STACK_LIMIT)
                .dependsOn(Skills.THUNDER_LANCE)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL5))
                .devCondition(new DevCondition.DependencyCondition("Lightning Spear", "academy:thunder_lance"))
        );
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        if (!Client.CONFIG.containsKeyBinding(Client.KEY)
                && Client.CONFIG.containsKeyBinding(Client.OLD_KEY)) {
            Client.CONFIG.setKeyBinding(Client.KEY, Client.CONFIG.getKeyBinding(Client.OLD_KEY));
        }
        InputSystem.addKeyBinding(Client.KEY, Client.CONFIG.getKeyBinding(Client.KEY,
                        InputSystem.combo(InputSystem.InputType.MOUSE, InputConstants.MOUSE_BUTTON_4,
                                InputConstants.RELEASE, 0))
                , ctx -> Client.onUse());
    }

    @Override
    public void initServer(MinecraftServerContext c) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.ELECTROMASTER.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.THUNDERCLAP.get(), List.of(ThunderLance.Client.SKILL_INFO),
                        R.textures.ability.electromaster.skill.thunderclap.icon, 204, 80)
        );
        public static final String KEY = SkillNames.THUNDERCLAP + "_clap";
        private static final String OLD_KEY = SkillNames.THUNDERCLAP + "_use";
        public static Config CONFIG = new Config();

        public static void onUse() {
            if (!AbilitySystemClient.canUseSkill(Skills.THUNDERCLAP.get())) return;
            MisakaNetworkClient.send(UsePacket.INSTANCE);
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public Thunderclap.Client.Config getDefault() {
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
        @SubscribePacket
        public static void handle(UsePacket p) {
            var player = p.getPacketListener().getPlayer();
            if (!(player.level() instanceof ServerLevel level)) return;
            var targetPos = resolveTarget(player, level);
            if (targetPos == null) return;
            Skills.THUNDERCLAP.get().executeActive(player, (_, _) -> strike(player, level, targetPos));
        }

        private static @Nullable Vec3 resolveTarget(ServerPlayer player, ServerLevel level) {
            var start = player.getEyePosition();
            var end = start.add(player.getLookAngle().scale(RANGE));
            var blockHit = level.clip(new ClipContext(
                    start,
                    end,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    player
            ));
            var blockPos = blockHit.getType() == HitResult.Type.MISS ? null : blockHit.getLocation();
            var entityHit = ProjectileUtil.getEntityHitResult(
                    player,
                    start,
                    end,
                    new AABB(start, end).inflate(1.0),
                    entity -> entity instanceof LivingEntity
                            && entity != player
                            && entity.isAlive()
                            && entity.isPickable(),
                    RANGE * RANGE
            );
            return selectNearestTarget(start, blockPos, entityHit == null ? null : entityHit.getLocation());
        }

        private static void strike(ServerPlayer player, ServerLevel level, Vec3 targetPos) {
            var bolt = new LightningBolt(EntityTypes.LIGHTNING_BOLT, level);
            bolt.setPos(targetPos);
            bolt.setCause(player);
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);

            var system = AbilitySystemServer.getSystem(player);
            var abilityPower = system.getPlayerAbilityPowerMultiplier(player.getUUID());
            var source = SkillDamageSource.of(player, Skills.THUNDERCLAP.get());
            var radiusSquared = RADIUS * RADIUS;
            var targets = level.getEntitiesOfClass(
                    LivingEntity.class,
                    new AABB(targetPos, targetPos).inflate(RADIUS),
                    entity -> entity != player
                            && entity.isAlive()
                            && entity.distanceToSqr(targetPos) <= radiusSquared
            );
            for (var target : targets) {
                target.hurtServer(level, source, 1.0f);
                new CTAEntityActuallyHurt(target).actuallyHurt(
                        source, calculateDamage(target.getMaxHealth(), abilityPower), true);
            }
            spawnArcs(level, targetPos, player.getLookAngle());
        }

        private static void spawnArcs(ServerLevel level, Vec3 targetPos, Vec3 look) {
            var right = look.cross(new Vec3(0, 1, 0));
            if (right.lengthSqr() <= 1.0e-8) right = new Vec3(1, 0, 0);
            else right = right.normalize();
            var up = right.cross(look).normalize();
            var arcs = new ArrayList<ArcPath>();
            for (var i = 0; i < 10; i++) {
                var angle = MathUtil.RANDOM.nextDouble() * Math.PI * 2.0;
                var radius = 3.5 * MathUtil.RANDOM.nextDouble();
                var height = (MathUtil.RANDOM.nextDouble() - 0.5) * 2.0;
                var start = targetPos
                        .add(right.scale(Math.cos(angle) * radius))
                        .add(up.scale(Math.sin(angle) * radius))
                        .add(0, height, 0);
                var end = targetPos.add(
                        (MathUtil.RANDOM.nextDouble() - 0.5) * 0.8,
                        (MathUtil.RANDOM.nextDouble() - 0.5) * 0.8,
                        (MathUtil.RANDOM.nextDouble() - 0.5) * 0.8
                );
                arcs.add(new ArcPath(
                        new LinePath(start.toVector3f(), end.toVector3f()),
                        List.of(new JaggedModifier(1, 3, MathUtil.RANDOM.nextLong())),
                        2.0f,
                        List.of()
                ));
            }
            var effect = new ArcEffect(level, 8);
            effect.setPos(targetPos);
            effect.setArcPaths(arcs);
            level.addFreshEntity(effect);
        }
    }

    static @Nullable Vec3 selectNearestTarget(
            Vec3 start,
            @Nullable Vec3 blockTarget,
            @Nullable Vec3 entityTarget
    ) {
        if (entityTarget == null) return blockTarget;
        if (blockTarget == null) return entityTarget;
        return start.distanceToSqr(entityTarget) <= start.distanceToSqr(blockTarget)
                ? entityTarget
                : blockTarget;
    }

    static float calculateDamage(float maxHealth, float abilityPower) {
        if (!Float.isFinite(maxHealth) || !Float.isFinite(abilityPower)) return 0;
        return Math.max(0, maxHealth) * HEALTH_DAMAGE_RATIO * Math.max(0, abilityPower);
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class UsePacket extends Packet<ServerGamePacketListenerImpl, UsePacket> {
        public static final UsePacket INSTANCE = new UsePacket();
        public static final StreamCodec<ByteBuf, UsePacket> CODEC = StreamCodec.unit(INSTANCE);

        private UsePacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, UsePacket> getPacketType() {
            return PacketTypes.THUNDERCLAP_USE.get();
        }
    }
}
