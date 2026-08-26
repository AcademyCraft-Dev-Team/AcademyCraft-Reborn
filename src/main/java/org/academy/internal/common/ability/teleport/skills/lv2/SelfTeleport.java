package org.academy.internal.common.ability.teleport.skills.lv2;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.ability.ClientContext;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.input.MouseScrollEvent;
import org.academy.api.client.render.LevelRenderEvent;
import org.academy.api.client.resources.R;
import org.academy.api.client.util.ClientUtil;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.DevCondition;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.client.render.vfx.TeleportCursorRenderer;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.teleport.TeleportSafety;
import org.academy.internal.common.ability.teleport.TeleportSync;
import org.academy.internal.common.ability.teleport.TeleportTargeting;
import org.academy.internal.common.ability.teleport.skills.lv1.ThreateningTeleport;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.sounds.SoundEvents;
import org.jspecify.annotations.Nullable;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public final class SelfTeleport extends Skill {
    private static final double MAX_DISTANCE = 64.0;
    private static final double DEFAULT_DISTANCE = 40.0;
    public static InputSystem.@Nullable KeyCombination KEY_START;
    public static InputSystem.@Nullable KeyCombination KEY_END;
    public static Client.@Nullable Config CONFIG;

    public SelfTeleport() {
        super(Builder
                .of(AbilityCategories.TELEPORT.get())
                .level(AbilityLevel.LEVEL2)
                .energyCost(10_000)
                .cpCost(10)
                .iterationTicks(5)
                .maxStacks(20)
                .dependsOn(Skills.THREATENING_TELEPORT)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL2))
                .devCondition(new DevCondition.DependencyCondition(
                        "Dangerous Teleport",
                        "academy:threatening_teleport"
                ))
        );
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);

        KEY_START = CONFIG.getKeyBinding(Client.KEY_NAME_START,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_R, InputConstants.PRESS, 0));
        KEY_END = CONFIG.getKeyBinding(Client.KEY_NAME_END,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_R, InputConstants.RELEASE, 0));

        InputSystem.addKeyBinding(Client.KEY_NAME_START, KEY_START, ctx -> Client.start());
        InputSystem.addKeyBinding(Client.KEY_NAME_END, KEY_END, ctx -> Client.end());
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Server {
        private static final Map<UUID, ReturnAnchor> RETURN_ANCHORS = new WeakHashMap<>();

        @SubscribePacket
        public static void handleTeleport(SelfTeleportPacket packet) {
            var serverPlayer = packet.getPacketListener().getPlayer();
            var skill = Skills.SELF_TELEPORT.get();
            var now = serverPlayer.level().getGameTime();
            var anchor = skill.hasProficiencyMilestone(serverPlayer, 3)
                    ? RETURN_ANCHORS.get(serverPlayer.getUUID()) : null;
            var returning = serverPlayer.isShiftKeyDown() && anchor != null && anchor.expiresAt >= now;
            Vec3 destination;
            if (returning) {
                destination = TeleportSafety.findSafe(
                        serverPlayer, serverPlayer.level(), anchor.position);
            } else {
                var distance = packet.getDistance();
                if (!Double.isFinite(distance) || distance < 0.0 || distance > MAX_DISTANCE) return;
                var targetCenter = TeleportTargeting.findSelfTeleportCenter(serverPlayer, distance);
                if (targetCenter == null) return;
                var dimensions = serverPlayer.getDimensions(Pose.STANDING);
                destination = new Vec3(targetCenter.x(), targetCenter.y() - dimensions.height() / 2.0,
                        targetCenter.z());
            }
            if (destination == null) return;
            var origin = serverPlayer.position();

            skill.executeActive(serverPlayer, ctx -> returning ? 5.0f : 10.0f, (ctx, actualCost) -> {
                SpatialSynergy.Server.teleportNearbyTeam(serverPlayer, serverPlayer.level(), destination);
                TeleportSync.teleportInstantly(serverPlayer, destination);
                serverPlayer.resetFallDistance();
                serverPlayer.setDeltaMovement(0, 0.25, 0);
                serverPlayer.connection.send(new ClientboundSetEntityMotionPacket(serverPlayer));
                serverPlayer.level().playSound(null, serverPlayer.blockPosition(), SoundEvents.SELF_TELEPORT.get(),
                        SoundSource.PLAYERS, 1.0f, 1.0f);
                if (returning) {
                    RETURN_ANCHORS.remove(serverPlayer.getUUID());
                } else if (ctx.milestone() >= 3) {
                    RETURN_ANCHORS.put(serverPlayer.getUUID(), new ReturnAnchor(origin, now + 60));
                }
            });
        }

        private record ReturnAnchor(Vec3 position, long expiresAt) {
        }
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.TELEPORT.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.SELF_TELEPORT.get(),
                        List.of(ThreateningTeleport.Client.SKILL_INFO),
                        R.textures.self_teleport_icon,
                        120,
                        50
                )
        );
        public static final String KEY_NAME_START = SkillNames.SELF_TELEPORT + "_start";
        public static final String KEY_NAME_END = SkillNames.SELF_TELEPORT + "_end";
        @Nullable
        public static TeleportRenderContext currentContext = null;

        private static void start() {
            if (ClientUtil.hasScreen()) return;
            var player = Minecraft.getInstance().player;
            if (player == null) return;
            if (currentContext != null) return;
            if (!AbilitySystemClient.canUseSkill(Skills.SELF_TELEPORT.get())) return;

            currentContext = new TeleportRenderContext(player);
            AbilitySystemClient.registerContext(currentContext);
        }

        private static void end() {
            if (currentContext != null) {
                var selectedDistance = currentContext.distance;
                currentContext.cleanup();
                if (ClientUtil.hasScreen()) return;
                MisakaNetworkClient.send(new SelfTeleportPacket(selectedDistance));
            }
        }

        public static class TeleportRenderContext extends ClientContext {
            private final LocalPlayer player;
            private final EntityDimensions playerDimensions;
            public Vec3 currentRenderPos;
            private double distance = DEFAULT_DISTANCE;
            private Vec3 visualRenderPos;

            public TeleportRenderContext(LocalPlayer player) {
                this.player = player;
                playerDimensions = player.getDimensions(Pose.STANDING);
                currentRenderPos = calculateIdealTargetCenterPosFromEyes();
                visualRenderPos = currentRenderPos;
            }

            private Vec3 calculateIdealTargetCenterPosFromEyes() {
                var eyePos = player.getEyePosition();
                var lookVec = player.getViewVector(1.0f);
                var target = TeleportTargeting.findSelfTeleportCenter(player, eyePos, lookVec, distance);
                return target == null ? eyePos : target;
            }

            @SubscribeEvent
            public void onScroll(MouseScrollEvent event) {
                distance += event.yOffset;
                distance = Math.clamp(distance, 0, MAX_DISTANCE);
                event.setCanceled(true);
            }

            @SubscribeEvent
            public void onLevelRender(LevelRenderEvent event) {
                if (player.isRemoved()) {
                    cleanup();
                    return;
                }

                var eyePos = player.getEyePosition(event.getPartialTick());
                var lookVec = player.getViewVector(event.getPartialTick());

                var resolvedTarget = TeleportTargeting.findSelfTeleportCenter(
                        player, eyePos, lookVec, distance);
                var logicalTargetPos = resolvedTarget == null ? eyePos : resolvedTarget;

                var factor = ClientUtil.animationFactor(1.25);
                currentRenderPos = logicalTargetPos;
                visualRenderPos = visualRenderPos.lerp(currentRenderPos, factor);
                var feetPosition = visualRenderPos.add(0, -playerDimensions.height() / 2.0, 0);
                TeleportCursorRenderer.render(event, feetPosition, true);
            }

            public void cleanup() {
                AbilitySystemClient.unregisterContext(this);
                if (currentContext == this) {
                    currentContext = null;
                }
            }
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public SelfTeleport.Client.Config getDefault() {
                    return new Config();
                }

                @Override
                public Class<Config> getTypeClass() {
                    return Config.class;
                }
            }
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class SelfTeleportPacket extends Packet<ServerGamePacketListenerImpl, SelfTeleportPacket> {
        public static final StreamCodec<ByteBuf, SelfTeleportPacket> CODEC = ByteBufCodecs.DOUBLE
                .map(SelfTeleportPacket::new, SelfTeleportPacket::getDistance);

        private final double distance;

        public SelfTeleportPacket(double distance) {
            this.distance = distance;
        }

        public double getDistance() {
            return distance;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, SelfTeleportPacket> getPacketType() {
            return PacketTypes.SELF_TELEPORT.get();
        }
    }
}
