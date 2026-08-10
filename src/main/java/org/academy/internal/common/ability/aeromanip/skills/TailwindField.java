package org.academy.internal.common.ability.aeromanip.skills;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.hud.ability.ToggleStatusHud;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.resources.R;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.DevCondition;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.aeromanip.AeromanipFieldManager;
import org.academy.internal.common.ability.aeromanip.AeromanipFieldSyncPacket;
import org.academy.internal.common.ability.aeromanip.AeromanipTargeting;
import org.academy.internal.common.ability.aeromanip.AirflowField;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.List;
import java.util.UUID;

public final class TailwindField extends Skill {
    public TailwindField() {
        super(Builder.of(AbilityCategories.AEROMANIP.get())
                .level(AbilityLevel.LEVEL2)
                .energyCost(10_000)
                .passive()
                .initiallyDisabled()
                .maintenanceCost(30)
                .iterationTicks(40)
                .dependsOn(Skills.AIR_CUSHION)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL2)));
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(Client.KEY_NAME_TOGGLE,
                Client.CONFIG.getKeyBinding(Client.KEY_NAME_TOGGLE,
                        InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_T,
                                InputConstants.RELEASE, InputConstants.MOD_ALT)), _ -> Client.toggle());
        Client.SKILL_INFO = AbilitySystemClient.addSkillInfo(AbilityCategories.AEROMANIP.get(),
                new AbilitySystemClient.SkillInfo(Skills.TAILWIND_FIELD.get(), List.of(AirCushion.Client.SKILL_INFO),
                        R.textures.tailwind_field_icon, 20, 72));
        ToggleStatusHud.Companion.registerStateProvider(Skills.TAILWIND_FIELD.get(), () -> {
            var player = Minecraft.getInstance().player;
            return player != null && AeromanipFieldSyncPacket.Client.snapshot().values().stream()
                    .anyMatch(field -> field.ownerId().equals(player.getUUID())
                            && field.type() == AirflowField.Type.TAILWIND);
        });
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final String KEY_NAME_TOGGLE = SkillNames.TAILWIND_FIELD + "_toggle";
        public static AbilitySystemClient.SkillInfo SKILL_INFO;
        public static Config CONFIG = new Config();

        private Client() {
        }

        private static void toggle() {
            if (AbilitySystemClient.beginToggleRequest(Skills.TAILWIND_FIELD.get())) {
                MisakaNetworkClient.send(TogglePacket.INSTANCE);
            }
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
        public static void handle(TogglePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var skill = Skills.TAILWIND_FIELD.get();
            skill.toggle(player);
            if (!skill.isEnabled(player)) {
                AeromanipFieldManager.endPersonal(player);
                return;
            }
            var level = Math.max(0, Math.min(2, skill.getLevel(player)));
            var direction = player.getLookAngle();
            var field = new AirflowField(UUID.randomUUID(), player.getUUID(), player.level().dimension(),
                    AirflowField.Type.TAILWIND, AirflowField.Shape.CAPSULE, player.position(), direction,
                    4.0, 12.0, 0.15f + level * 0.05f, Integer.MAX_VALUE);
            AeromanipFieldManager.activatePersonal(player, skill, field, Server::tick);
        }

        private static void tick(ServerPlayer player, AirflowField field, int age) {
            var direction = AeromanipTargeting.horizontalDirection(player.getLookAngle());
            if (direction.lengthSqr() <= 1.0e-8) return;
            var currentField = new AirflowField(field.id(), field.ownerId(), field.dimension(), field.type(), field.shape(),
                    player.position().add(direction.scale(-2.0)), direction, field.radius(), 14.0,
                    field.strength(), field.durationTicks());
            spawnVisual(player, currentField, age);
            boostFriendly(player, player, direction, field.strength());
            var box = new AABB(currentField.center(), currentField.center().add(direction.scale(currentField.length())))
                    .inflate(currentField.radius());
            for (var entity : player.level().getEntities(player, box, Entity::isAlive)) {
                if (!currentField.contains(entity.getBoundingBox().getCenter(), entity.getBbWidth() * 0.5)) continue;
                var dot = entity.getDeltaMovement().dot(direction);
                var friendly = player.isAlliedTo(entity)
                        || entity instanceof TamableAnimal animal && animal.isOwnedBy(player);
                var hostile = !friendly && AeromanipTargeting.canAffectNegatively(player, entity);
                if ((entity instanceof Projectile || friendly) && dot > 0.01) {
                    boostFriendly(player, entity, direction, field.strength());
                } else if (hostile && dot < 0) {
                    var multiplier = AeromanipTargeting.forceMultiplier(player, entity);
                    AeromanipTargeting.accelerateAlong(entity, direction,
                            (0.08 + field.strength() * 0.12) * multiplier,
                            (0.45 + field.strength() * 0.6) * multiplier);
                }
            }
        }

        private static void spawnVisual(ServerPlayer player,
                                        AirflowField field, int age) {
            if ((age & 1) != 0) return;
            for (var step = 0; step <= 8; step++) {
                var point = field.center().add(field.direction().scale(field.length() * step / 8.0));
                player.level().sendParticles(
                        ParticleTypes.CLOUD,
                        point.x, point.y + 0.5, point.z,
                        2,
                        field.radius() * 0.18,
                        field.radius() * 0.1,
                        field.radius() * 0.18,
                        0.015
                );
            }
        }

        private static void boostFriendly(ServerPlayer owner, Entity entity,
                                          Vec3 direction, float strength) {
            if (entity.getDeltaMovement().dot(direction) <= 0.01) return;
            var projectile = entity instanceof Projectile;
            var maxSpeed = projectile ? 1.6 : 0.42 + strength * 0.8;
            AeromanipTargeting.accelerateAlong(entity, direction,
                    0.06 + strength * 0.16, maxSpeed);
            if (entity == owner) owner.resetFallDistance();
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
            return PacketTypes.TAILWIND_FIELD_TOGGLE.get();
        }
    }
}
