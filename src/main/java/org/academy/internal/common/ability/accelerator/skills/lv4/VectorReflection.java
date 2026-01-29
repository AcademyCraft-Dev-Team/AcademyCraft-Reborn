package org.academy.internal.common.ability.accelerator.skills.lv4;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.Resource;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.sync.ClientSyncManager;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.common.util.MathUtil;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.sounds.SoundEvents;
import org.academy.internal.common.sync.SyncKeys;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.skill.GlowCircle;
import org.apache.commons.lang3.tuple.Pair;
import org.lwjgl.glfw.GLFW;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.*;

public class VectorReflection extends Skill {
    public VectorReflection() {
        super(Builder
                .of(AbilityCategories.ACCELERATOR.get())
                .level(AbilityLevel.LEVEL4)
                .iterationTicks(200)
                .maintenanceCost(50)
                .passive()
                .maxStacks(NO_STACK_LIMIT)
        );
    }

    @Override
    public int getIterationTicks(int skillLevel) {
        if (skillLevel >= 1) return 160;
        return super.getIterationTicks(skillLevel);
    }

    public float getMaxAbsorptionRate(int level) {
        if (level >= 3) return 0.08f;
        return 0.05f;
    }

    public float getReflectionCostFactor(int level) {
        if (level >= 2) return 2.5f;
        return 3.0f;
    }

    public float getReflectionDamageFactor(int level) {
        if (level >= 1) return 1.5f;
        return 1f;
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);

        InputSystem.addKeyBinding(Client.KEY_NAME_TOGGLE, Client.CONFIG.getKeyBinding(Client.KEY_NAME_TOGGLE,
                        new InputSystem.InputPair(
                                InputSystem.InputType.KEYBOARD,
                                new InputSystem.KeyInfo(
                                        new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_R)),
                                        GLFW.GLFW_PRESS,
                                        new LinkedHashSet<>()
                                )
                        )
                ), Client::onToggle
        );
        ClientSyncManager.register(SyncKeys.VECTOR_REFLECTION_ACTIVE.get(), Client::setActive);
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.registerPacketListener(Server.class);
    }

    public static final class Client {
        private static boolean active = false;
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.ACCELERATOR.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.VECTOR_REFLECTION.get(),
                        List.of(),
                        Resource.Textures.VECTOR_REFLECTION_ICON,
                        20, 75
                )
        );

        public static final String KEY_NAME_TOGGLE = SkillNames.VECTOR_REFLECTION + "_toggle";
        public static Config CONFIG = new Config();

        public static void setActive(boolean active) {
            Client.active = active;
        }

        public static boolean isActive() {
            return active;
        }

        public static void onToggle() {
            MisakaNetworkClient.sendPacket(TogglePacket.INSTANCE);
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public VectorReflection.Client.Config getDefault() {
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
        public static void toggleReflection(TogglePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            Skills.VECTOR_REFLECTION.get().toggle(player);
        }

        public static boolean shouldReflection(Player player, DamageSource damageSource) {
            if (player.isCreative() || player.isSpectator()) return false;

            return !damageSource.is(DamageTypes.STARVE)
                    && !damageSource.is(DamageTypes.DROWN)
                    && !damageSource.is(DamageTypes.GENERIC_KILL);
        }

        public static Pair<Boolean, Float> hurtServer(Player player, ServerLevel level, DamageSource source, float originalDamage) {
            if (player.invulnerableTime > 0) return Pair.of(false, 0f);
            if (!shouldReflection(player, source)) return Pair.of(false, originalDamage);
            var skill = Skills.VECTOR_REFLECTION.get();

            final var remainingDamage = new float[]{originalDamage};
            var success = skill.executeActive((ServerPlayer) player,
                    ctx -> {
                        var limit = ctx.system().getPlayerMaxCP(player.getUUID()) * skill.getMaxAbsorptionRate(ctx.level());
                        var target = Math.min(originalDamage, limit);
                        return target * skill.getReflectionCostFactor(ctx.level());
                    },
                    (ctx, actualCost) -> {
                        var factor = skill.getReflectionCostFactor(ctx.level());
                        var targetDamage = actualCost / factor;
                        var canFullyAfford = ctx.availableCP() >= actualCost - 1E-3f;

                        if (canFullyAfford) {
                            player.invulnerableTime = 20;
                            remainingDamage[0] = Math.max(0, originalDamage - targetDamage);
                        } else {
                            var absorbed = ctx.availableCP() / factor;
                            remainingDamage[0] = originalDamage - absorbed;
                        }
                        applyReflection(player, level, source, targetDamage);
                    }
            );
            return Pair.of(success && remainingDamage[0] <= 1E-3f, remainingDamage[0]);
        }

        private static void applyReflection(Player player, ServerLevel level, DamageSource source, float reflectedDamage) {
            var causingEntity = source.getEntity();
            var directEntity = source.getDirectEntity();

            var hasCausingEntity = causingEntity != null;
            var hasDirectEntity = directEntity != null;

            if ((!hasCausingEntity && !hasDirectEntity) || directEntity == player || causingEntity == player) return;

            player.level().playSound(null, player, SoundEvents.VECTOR_REFLECTION.get(), SoundSource.PLAYERS, 1, 1);

            var sourceEntity = hasDirectEntity ? directEntity : causingEntity;

            var sourcePos = sourceEntity.getBoundingBox().getCenter();

            var width = player.getBbWidth();

            var direction = player.getBoundingBox().getCenter().subtract(sourcePos).normalize();
            var pos = MathUtil.intersectRayCapsule(sourcePos, direction, player.getBoundingBox().getCenter(), width, player.getBbHeight());

            var glowCircle = new GlowCircle(EntityTypes.GLOW_CIRCLE.get(), player.level());
            glowCircle.setPos(pos);

            var radius = width / 2.0;
            var halfEffectiveHeight = player.getBbHeight() / 2.0 - radius;
            var center = player.getBoundingBox().getCenter();
            var topCenterY = center.y + halfEffectiveHeight;
            var bottomCenterY = center.y - halfEffectiveHeight;

            Vec3 normal;

            if (halfEffectiveHeight > 0) {
                if (pos.y >= topCenterY) {
                    normal = pos.subtract(center.x, topCenterY, center.z);
                } else if (pos.y <= bottomCenterY) {
                    normal = pos.subtract(center.x, bottomCenterY, center.z);
                } else {
                    normal = new Vec3(pos.x - center.x, 0, pos.z - center.z);
                }
            } else {
                normal = pos.subtract(center);
            }

            normal = normal.normalize();

            var yaw = (float) (Math.toDegrees(Math.atan2(normal.z, normal.x))) - 90.0F;
            var pitch = (float) (-Math.toDegrees(Math.asin(normal.y)));

            glowCircle.setYRot(yaw);
            glowCircle.setXRot(pitch);

            player.level().addFreshEntity(glowCircle);

            sourceEntity.hurtServer(level, source, reflectedDamage);
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class TogglePacket extends Packet<ServerGamePacketListenerImpl, TogglePacket> {
        public static final TogglePacket INSTANCE = new TogglePacket();
        public static final StreamCodec<ByteBuf, TogglePacket> CODEC = StreamCodec.unit(INSTANCE);

        private TogglePacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, TogglePacket> getPacketType() {
            return PacketTypes.VECTOR_REFLECTION_TOGGLE.get();
        }
    }
}