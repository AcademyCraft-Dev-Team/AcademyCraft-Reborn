package org.academy.internal.common.ability.aeromanip.skills;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
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
import org.academy.internal.common.ability.ProficiencyPolicy;
import org.academy.internal.common.ability.aeromanip.AeromanipConfig;
import org.academy.internal.common.ability.aeromanip.AirflowField;
import org.academy.internal.common.ability.aeromanip.AeromanipFieldManager;
import org.academy.internal.common.ability.aeromanip.AeromanipTargeting;
import org.academy.internal.common.ability.aeromanip.AeromanipFieldSyncPacket;
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

public final class WindCorridor extends Skill {
    public WindCorridor() {
        super(Builder.of(AbilityCategories.AEROMANIP.get()).level(AbilityLevel.LEVEL4).energyCost(60_000)
                .cpCost(65).iterationTicks(20).maxStacks(1).dependsOn(Skills.TAILWIND_FIELD)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL4)));
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(Client.KEY_NAME_CAST, Client.CONFIG.getKeyBinding(Client.KEY_NAME_CAST,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_G, InputConstants.RELEASE, InputConstants.MOD_ALT)), _ -> Client.cast());
        Client.SKILL_INFO = AbilitySystemClient.addSkillInfo(AbilityCategories.AEROMANIP.get(), new AbilitySystemClient.SkillInfo(Skills.WIND_CORRIDOR.get(), List.of(), R.textures.wind_corridor_icon, 20, 136));
        ToggleStatusHud.Companion.registerStateProvider(Skills.WIND_CORRIDOR.get(), () -> {
            var player = Minecraft.getInstance().player;
            return player != null && AeromanipFieldSyncPacket.Client.snapshot().values().stream()
                    .anyMatch(field -> field.ownerId().equals(player.getUUID())
                            && field.type() == AirflowField.Type.WIND_CORRIDOR);
        });
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final String KEY_NAME_CAST = SkillNames.WIND_CORRIDOR + "_cast";
        public static AbilitySystemClient.SkillInfo SKILL_INFO;
        public static Config CONFIG = new Config();

        private static void cast() {
            if (AbilitySystemClient.canUseSkill(Skills.WIND_CORRIDOR.get()))
                MisakaNetworkClient.send(CastPacket.INSTANCE);
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
        @SubscribePacket
        public static void handle(CastPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var skill = Skills.WIND_CORRIDOR.get();
            var redirect = skill.hasProficiencyMilestone(player, 3)
                    && AeromanipFieldManager.getPlacedField(player, AirflowField.Type.WIND_CORRIDOR).isPresent();
            skill.executeActive(player, context -> skill.getCpCost(context.level()) * (redirect ? 0.5f : 1.0f)
                    * AeromanipConfig.cpMultiplier(player, SkillNames.WIND_CORRIDOR), (context, _) -> {
                if (!(player.level() instanceof ServerLevel level)) return;
                var direction = player.getLookAngle().normalize();
                var center = player.getEyePosition();
                var range = AeromanipConfig.rangeMultiplier(player, SkillNames.WIND_CORRIDOR);
                var durationTicks = context.milestone() >= 2 ? 220 : 160;
                var duration = Math.max(1, Math.round(durationTicks * AeromanipConfig.durationMultiplier(player, SkillNames.WIND_CORRIDOR)));
                if (redirect) duration = Math.max(1, duration / 2);
                var length = context.milestone() >= 2 ? 30.0 : 24.0;
                var field = new AirflowField(java.util.UUID.randomUUID(), player.getUUID(), level.dimension(), AirflowField.Type.WIND_CORRIDOR,
                        AirflowField.Shape.CAPSULE, center, direction, 2.5 * range, length * range, 1.0f, duration, context.milestone());
                AeromanipFieldManager.activate(player, skill, field, Server::tick);
            });
        }

        private static void tick(ServerPlayer owner, AirflowField field, int age) {
            spawnVisual(owner, field, age);
            transport(owner, owner, field);
            var handled = 0;
            var cap = ProficiencyPolicy.server(owner).maxBonusEntitiesPerTick();
            for (var target : owner.level().getEntities(owner, field.bounds().inflate(1.0), Entity::isAlive)) {
                if (handled++ >= cap) break;
                if (!field.contains(target.getBoundingBox().getCenter(), target.getBbWidth() * 0.5)) continue;
                var transportable = target instanceof Projectile
                        || target instanceof ItemEntity
                        || target == owner
                        || owner.isAlliedTo(target)
                        || target instanceof TamableAnimal animal && animal.isOwnedBy(owner);
                if (!transportable) continue;
                transport(owner, target, field);
            }
        }

        private static void spawnVisual(ServerPlayer owner,
                                        AirflowField field, int age) {
            if ((age & 1) != 0) return;
            for (var step = 0; step <= 12; step++) {
                var point = field.center().add(field.direction().scale(field.length() * step / 12.0));
                owner.level().sendParticles(
                        ParticleTypes.CLOUD,
                        point.x, point.y, point.z,
                        2,
                        field.radius() * 0.2,
                        field.radius() * 0.2,
                        field.radius() * 0.2,
                        0.012
                );
            }
        }

        private static void transport(ServerPlayer owner, Entity target, AirflowField field) {
            if (!field.contains(target.getBoundingBox().getCenter(), target.getBbWidth() * 0.5)) return;
            var projectile = target instanceof Projectile;
            var light = projectile || target instanceof ItemEntity;
            var targetSpeed = projectile ? 1.8 : light ? 1.45 : 1.2;
            var response = light ? 0.48 : 0.4;
            AeromanipTargeting.steerVelocity(target, field.direction(), response, targetSpeed);
            target.resetFallDistance();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class CastPacket extends Packet<ServerGamePacketListenerImpl, CastPacket> {
        public static final CastPacket INSTANCE = new CastPacket();
        public static final StreamCodec<ByteBuf, CastPacket> CODEC = StreamCodec.unit(INSTANCE);

        private CastPacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, CastPacket> getPacketType() {
            return PacketTypes.WIND_CORRIDOR_CAST.get();
        }
    }
}
