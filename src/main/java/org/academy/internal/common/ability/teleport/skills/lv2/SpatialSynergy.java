package org.academy.internal.common.ability.teleport.skills.lv2;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.hud.ability.ToggleStatusHud;
import org.academy.api.client.input.InputSystem;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.teleport.TeleportSafety;
import org.academy.internal.common.ability.teleport.TeleportSync;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

public class SpatialSynergy extends Skill {
    private static final float RADIUS = 4.0f;

    public SpatialSynergy() {
        super(Builder
                .of(AbilityCategories.TELEPORT.get())
                .level(AbilityLevel.LEVEL2)
                .energyCost(10_000)
                .passive()
                .initiallyDisabled()
                .maintenanceCost(20)
                .iterationTicks(5)
                .maxStacks(NO_STACK_LIMIT)
                .maxSkillLevel(0)
                .dependsOn(Skills.SELF_TELEPORT)
        );
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);

        InputSystem.addKeyBinding(Client.KEY_NAME_TOGGLE, Client.CONFIG.getKeyBinding(Client.KEY_NAME_TOGGLE,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_H,
                        InputConstants.PRESS, InputConstants.MOD_ALT)
        ), ctx -> Client.onToggle());
        ToggleStatusHud.Companion.registerStateProvider(Skills.SPATIAL_SYNERGY.get(),
                () -> AbilitySystemClient.canUseSkillSilently(Skills.SPATIAL_SYNERGY.get()));
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final String KEY_NAME_TOGGLE = SkillNames.SPATIAL_SYNERGY + "_toggle";
        public static Config CONFIG = new Config();

        public static void onToggle() {
            if (!AbilitySystemClient.beginToggleRequest(Skills.SPATIAL_SYNERGY.get())) return;
            MisakaNetworkClient.send(TogglePacket.INSTANCE);
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public SpatialSynergy.Client.Config getDefault() {
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
        public static void handleToggle(TogglePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            Skills.SPATIAL_SYNERGY.get().toggle(player);
        }

        public static void teleportNearbyTeam(ServerPlayer owner, ServerLevel destinationLevel,
                                              Vec3 ownerDestination) {
            if (!Skills.SPATIAL_SYNERGY.get().isEnabled(owner) || owner.getTeam() == null) return;
            var skill = Skills.SPATIAL_SYNERGY.get();
            var milestone = skill.getEffectiveProficiencyMilestone(owner);
            var radius = milestone >= 2 ? 6.0f : RADIUS;
            var origin = owner.position();
            var nearby = owner.level().getEntitiesOfClass(
                    ServerPlayer.class,
                    owner.getBoundingBox().inflate(radius),
                    player -> player != owner && player.isAlive()
                            && player.getTeam() == owner.getTeam()
                            && player.distanceToSqr(owner) <= radius * radius
            );
            for (var teammate : nearby) {
                var desired = ownerDestination.add(teammate.position().subtract(origin));
                var safe = TeleportSafety.findSafe(teammate, destinationLevel, desired);
                if (safe == null) continue;
                TeleportSync.teleportInstantly(teammate, destinationLevel, safe);
                teammate.resetFallDistance();
            }
            if (milestone < 3) return;
            var extras = owner.level().getEntitiesOfClass(
                    LivingEntity.class,
                    owner.getBoundingBox().inflate(radius),
                    entity -> entity != owner && !(entity instanceof ServerPlayer)
                            && entity.isAlive() && entity.distanceToSqr(owner) <= radius * radius
                            && (owner.isAlliedTo(entity)
                            || entity instanceof TamableAnimal tame && tame.isOwnedBy(owner))
            );
            var system = AbilitySystemServer.getSystem(owner);
            var processed = 0;
            for (var entity : extras) {
                if (processed >= 96 || entity.isPassenger()) continue;
                if (!system.tryTimedOccupation(owner.getUUID(), 10.0f, skill, skill.getIterationTicks(owner))) {
                    break;
                }
                var desired = ownerDestination.add(entity.position().subtract(origin));
                var safe = TeleportSafety.findSafe(entity, destinationLevel, desired);
                if (safe == null) continue;
                teleportEntity(entity, destinationLevel, safe);
                entity.resetFallDistance();
                processed++;
            }
        }

        private static void teleportEntity(Entity entity, ServerLevel destinationLevel, Vec3 safe) {
            TeleportSync.teleportInstantly(entity, destinationLevel, safe);
        }
    }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class Events {
        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            var skill = Skills.SPATIAL_SYNERGY.get();
            if (!skill.isEnabled(player)) return;
            var system = AbilitySystemServer.getSystem(player);
            if (!player.isAlive() || player.hasDisconnected()
                    || !system.ensurePermanentOccupation(
                    player.getUUID(),
                    skill.getMaintenanceCost(player),
                    skill
            )) {
                if (skill.isEnabled(player)) skill.toggle(player);
            }
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
            return PacketTypes.SPATIAL_SYNERGY_TOGGLE.get();
        }
    }
}
