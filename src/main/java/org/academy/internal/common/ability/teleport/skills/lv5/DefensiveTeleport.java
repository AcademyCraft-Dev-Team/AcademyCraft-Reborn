package org.academy.internal.common.ability.teleport.skills.lv5;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.academy.AcademyCraft;
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
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.teleport.TeleportSync;
import org.academy.internal.common.ability.teleport.TeleportSafety;
import org.academy.internal.common.ability.teleport.skills.lv4.QuickLocationTeleport;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.List;

public final class DefensiveTeleport extends Skill {
    static final int SCAN_INTERVAL_TICKS = 2;
    static final double SCAN_RADIUS = 3.0;
    static final double REPEL_DISTANCE = 16.0;

    public DefensiveTeleport() {
        super(Builder
                .of(AbilityCategories.TELEPORT.get())
                .level(AbilityLevel.LEVEL5)
                .energyCost(100_000)
                .passive()
                .initiallyDisabled()
                .maintenanceCost(30)
                .cpCost(10)
                .iterationTicks(1)
                .maxStacks(NO_STACK_LIMIT)
                .dependsOn(Skills.QUICK_LOCATION_TELEPORT)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL5))
                .devCondition(new DevCondition.DependencyCondition(
                        "Quick Location Teleport", "academy:quick_location_teleport"))
        );
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(Client.KEY_NAME_TOGGLE, Client.CONFIG.getKeyBinding(
                Client.KEY_NAME_TOGGLE,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_G,
                        InputConstants.RELEASE, InputConstants.MOD_ALT)
        ), context -> Client.toggle());
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.TELEPORT.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.DEFENSIVE_TELEPORT.get(),
                        List.of(QuickLocationTeleport.Client.SKILL_INFO),
                        R.textures.defensive_teleport_icon,
                        30,
                        30
                )
        );
        public static final String KEY_NAME_TOGGLE = SkillNames.DEFENSIVE_TELEPORT + "_toggle";
        public static Config CONFIG = new Config();

        private Client() {
        }

        private static void toggle() {
            if (ClientUtil.hasScreen() || Minecraft.getInstance().player == null) return;
            if (!AbilitySystemClient.canToggleSkill(Skills.DEFENSIVE_TELEPORT.get())) return;
            MisakaNetworkClient.send(TogglePacket.INSTANCE);
        }

        public static class Config extends KeyBindingConfig {
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
        public static void handleToggle(TogglePacket packet) {
            Skills.DEFENSIVE_TELEPORT.get().toggle(packet.getPacketListener().getPlayer());
        }
    }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post event) {
            if (!(event.getEntity() instanceof ServerPlayer player)
                    || !player.isAlive() || player.hasDisconnected()
                    || player.tickCount % SCAN_INTERVAL_TICKS != 0) return;

            var skill = Skills.DEFENSIVE_TELEPORT.get();
            if (!skill.isEnabled(player)) return;
            var system = AbilitySystemServer.getSystem(player);
            if (!system.ensurePermanentOccupation(
                    player.getUUID(),
                    skill.getMaintenanceCost(skill.getLevel(player)),
                    skill
            )) {
                if (skill.isEnabled(player)) skill.toggle(player);
                return;
            }

            var center = player.getBoundingBox().getCenter();
            var bounds = player.getBoundingBox().inflate(SCAN_RADIUS);
            for (var entity : player.level().getEntities(player, bounds,
                    candidate -> isThreat(player, candidate, center))) {
                repel(player, skill, entity, center);
            }
        }

        static boolean isThreat(ServerPlayer player, Entity entity, Vec3 center) {
            if (entity == player || !entity.isAlive() || entity.isRemoved()) return false;
            if (entity.getBoundingBox().getCenter().distanceToSqr(center)
                    > SCAN_RADIUS * SCAN_RADIUS) return false;

            if (entity instanceof LivingEntity living) return isHostile(player, living);
            if (!(entity instanceof Projectile projectile)) return false;

            var owner = projectile.getOwner();
            if (owner == player || owner != null && player.isAlliedTo(owner)) return false;
            return isHeadingToward(projectile.getDeltaMovement(), center.subtract(projectile.position()));
        }

        static boolean isHostile(ServerPlayer player, LivingEntity target) {
            if (target == player || target instanceof Player || !target.isAlive() || target.isRemoved()) {
                return false;
            }
            if (target instanceof TamableAnimal tameable && tameable.isOwnedBy(player)) return false;
            if (player.isAlliedTo(target)) return false;
            return target instanceof Enemy || target instanceof Mob mob && mob.getTarget() == player;
        }

        static boolean isHeadingToward(Vec3 velocity, Vec3 toPlayer) {
            return velocity.lengthSqr() > 1.0e-8 && velocity.dot(toPlayer) > 0;
        }

        private static void repel(ServerPlayer player, DefensiveTeleport skill,
                                  Entity entity, Vec3 center) {
            var outward = entity.getBoundingBox().getCenter().subtract(center);
            if (outward.lengthSqr() < 1.0e-6) outward = player.getLookAngle().reverse();
            if (outward.lengthSqr() < 1.0e-6) outward = new Vec3(0, 0, 1);

            var destination = TeleportSafety.findSafe(entity,
                    entity.position().add(outward.normalize().scale(REPEL_DISTANCE)));
            if (destination == null) return;

            skill.executeActive(player, (context, actualCost) -> {
                TeleportSync.teleportInstantly(entity, destination);
                entity.resetFallDistance();
            });
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
            return PacketTypes.DEFENSIVE_TELEPORT_TOGGLE.get();
        }
    }
}
