package org.academy.internal.common.ability.teleport.skills.lv4;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
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
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.teleport.TeleportSync;
import org.academy.internal.common.ability.teleport.skills.lv3.LocationTeleport;
import org.academy.internal.common.entitycontrol.EntityMotionGuard;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class QuickLocationTeleport extends Skill {
    private static final double PICK_REACH = 32.0;

    public QuickLocationTeleport() {
        super(Builder
                .of(AbilityCategories.TELEPORT.get())
                .level(AbilityLevel.LEVEL4)
                .energyCost(60_000)
                .cpCost(30)
                .iterationTicks(20)
                .maxStacks(1)
                .dependsOn(Skills.LOCATION_TELEPORT)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL4))
                .devCondition(new DevCondition.DependencyCondition("Location Teleport", "academy:location_teleport"))
        );
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(Client.KEY_NAME_RUN, Client.CONFIG.getKeyBinding(
                Client.KEY_NAME_RUN,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_G, InputConstants.PRESS, 0)
        ), ctx -> Client.run());
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.TELEPORT.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.QUICK_LOCATION_TELEPORT.get(),
                        List.of(LocationTeleport.Client.SKILL_INFO),
                        R.textures.quick_location_teleport_icon,
                        90,
                        70
                )
        );
        public static final String KEY_NAME_RUN = SkillNames.QUICK_LOCATION_TELEPORT + "_run";
        public static Config CONFIG = new Config();

        private static void run() {
            if (ClientUtil.hasScreen() || Minecraft.getInstance().player == null
                    || !AbilitySystemClient.canUseSkill(Skills.QUICK_LOCATION_TELEPORT.get())) return;
            MisakaNetworkClient.send(RunPacket.INSTANCE);
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();
                private Action() {
                }
                @Override public Config getDefault() { return new Config(); }
                @Override public Class<Config> getTypeClass() { return Config.class; }
            }
        }
    }

    public static final class Server {
        @SubscribePacket
        public static void handle(RunPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var skill = Skills.QUICK_LOCATION_TELEPORT.get();
            var mark = LocationTeleport.Server.getQuickMark(player);
            if (mark == null) return;
            var level = LocationTeleport.Server.resolveLevel(player, mark);
            if (level == null) return;
            var picked = pickEntity(player, skill.hasProficiencyMilestone(player, 2) ? PICK_REACH * 1.5 : PICK_REACH);
            Entity target = player;
            var destination = LocationTeleport.Server.safeDestination(player, level, mark);
            if (picked != null && picked.level() == level) {
                if (!EntityMotionGuard.canApplyMotionFrom(player, picked)) return;
                LocationTeleport.Server.forceDestinationChunk(level, mark.x(), mark.z(),
                        "quick_location_" + player.getStringUUID());
                level.getChunk(mark.x() >> 4, mark.z() >> 4);
                var entityDestination = new Vec3(mark.x() + 0.5, mark.y() + 0.5, mark.z() + 0.5);
                var moved = picked.getBoundingBox().move(entityDestination.subtract(picked.position()));
                if (level.noCollision(picked, moved)) {
                    target = picked;
                    destination = entityDestination;
                }
            }
            if (destination == null || !EntityMotionGuard.canApplyMotionFrom(player, target)) return;

            var finalTarget = target;
            var finalDestination = destination;
            skill.executeActive(player, (ctx, actualCost) -> {
                if (finalTarget == player) {
                    player.teleportTo(level, finalDestination.x, finalDestination.y, finalDestination.z,
                            Set.of(), player.getYRot(), player.getXRot(), false);
                } else {
                    var hierarchyRoot = ctx.milestone() >= 3 ? finalTarget.getRootVehicle() : finalTarget;
                    var offset = finalDestination.subtract(finalTarget.position());
                    TeleportSync.teleportInstantly(hierarchyRoot, hierarchyRoot.position().add(offset));
                }
                finalTarget.resetFallDistance();
            });
        }

        private static Entity pickEntity(ServerPlayer player, double reach) {
            var eye = player.getEyePosition();
            var look = player.getLookAngle().normalize();
            var end = eye.add(look.scale(reach));
            var search = player.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.0);
            var closest = reach * reach;
            Entity best = null;
            for (var entity : player.level().getEntities(player, search,
                    entity -> entity != player && entity.isPickable())) {
                var hit = entity.getBoundingBox().inflate(0.3).clip(eye, end);
                if (hit.isEmpty()) continue;
                var distance = eye.distanceToSqr(hit.get());
                if (distance < closest) {
                    closest = distance;
                    best = entity;
                }
            }
            return best;
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class RunPacket extends Packet<ServerGamePacketListenerImpl, RunPacket> {
        public static final RunPacket INSTANCE = new RunPacket();
        public static final StreamCodec<ByteBuf, RunPacket> CODEC = StreamCodec.unit(INSTANCE);
        private RunPacket() {
        }
        @Override public PacketType<ServerGamePacketListenerImpl, RunPacket> getPacketType() {
            return PacketTypes.QUICK_LOCATION_TELEPORT_RUN.get();
        }
    }
}
