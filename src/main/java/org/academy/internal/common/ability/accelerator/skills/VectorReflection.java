package org.academy.internal.common.ability.accelerator.skills;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
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
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.sync.DataSyncManager;
import org.academy.api.server.sync.ServerSyncManager;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.sounds.SoundEvents;
import org.academy.internal.common.sync.DataTypes;
import org.academy.internal.common.sync.SyncKeys;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.skill.GlowCircle;
import org.apache.commons.lang3.tuple.Pair;
import org.jspecify.annotations.Nullable;
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
                .level(AbilityLevel.LEVEL2)
        );
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
    public void initServer(MinecraftServer server) {
        MisakaNetworkServer.NETWORK_MANAGER.registerPacketListener(Server.class);
        var key = SyncKeys.VECTOR_REFLECTION_ACTIVE.get();
        Server.activeSyncManager = new DataSyncManager<>(key, DataTypes.BOOL.get(), server.getPlayerList());
        ServerSyncManager.register(key, Server.activeSyncManager);
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
        @Nullable
        private static DataSyncManager<Boolean> activeSyncManager;
        public static final Map<UUID, Boolean> ACTIVE_REFLECTION_MAP = new LinkedHashMap<>();

        @SubscribePacket
        public static void toggleReflection(TogglePacket packet) {
            var uuid = packet.getPacketListener().getPlayer().getUUID();
            ACTIVE_REFLECTION_MAP.compute(uuid, (_, value) -> value == null || !value);
        }

        public static boolean shouldReflection(Player player, DamageSource damageSource) {
            if (player.isCreative() || player.isSpectator()) {
                return false;
            }
            final var uuid = player.getUUID();
            if (ACTIVE_REFLECTION_MAP.containsKey(uuid)) return ACTIVE_REFLECTION_MAP.get(uuid);
            return !damageSource.is(DamageTypes.STARVE) && !damageSource.is(DamageTypes.DROWN) && !damageSource.is(DamageTypes.GENERIC_KILL);
        }

        public static Pair<Boolean, Float> hurtServer(Player player, ServerLevel level, DamageSource source, float originalDamage) {
            if (player.invulnerableTime > 0) {
                return Pair.of(false, 0f);
            }

            if (!shouldReflection(player, source)) {
                return Pair.of(false, originalDamage);
            }

            var uuid = player.getUUID();
            var requiredCP = originalDamage * 10f;
            var currentCP = AbilitySystemServer.getPlayerAvailableCP(uuid);

            var clampedCP = Mth.clamp(currentCP, 0f, 200f);
            var iterationTicks = (int) Mth.map(clampedCP, 0f, 200, 10, 150);

            if (AbilitySystemServer.requestCPOccupation(uuid, requiredCP, iterationTicks, true)) {
                if (currentCP >= requiredCP) {
                    player.invulnerableTime = 20;
                    applyReflection(player, level, source, originalDamage);
                    return Pair.of(false, 0f);
                } else {
                    var effectiveCP = Math.max(0f, currentCP);
                    var reflectedDamage = effectiveCP / 10f;
                    var remainingDamage = Math.max(0f, originalDamage - reflectedDamage);

                    applyReflection(player, level, source, reflectedDamage);
                    return Pair.of(true, remainingDamage);
                }
            }

            return Pair.of(false, originalDamage);
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