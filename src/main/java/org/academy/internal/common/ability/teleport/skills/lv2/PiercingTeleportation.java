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
import org.academy.internal.client.render.vfx.DistortionVfx;
import org.academy.internal.client.render.vfx.DistortionVfxClient;
import org.academy.internal.client.render.vfx.TeleportCursorRenderer;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.teleport.TeleportSync;
import org.academy.internal.common.ability.teleport.TeleportTargeting;
import org.academy.internal.common.ability.teleport.skills.lv2.SpatialSynergy;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.sounds.SoundEvents;
import org.academy.internal.common.ability.TimedSkillEffectRuntime;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.List;

/**
 * The 26.2 target for the reference Penetrate Teleport skill.
 */
public final class PiercingTeleportation extends Skill {
    private static final double MAX_DISTANCE = 64.0;
    private static final double DEFAULT_DISTANCE = 40.0;

    public PiercingTeleportation() {
        super(Builder
                .of(AbilityCategories.TELEPORT.get())
                .level(AbilityLevel.LEVEL2)
                .energyCost(10_000)
                .cpCost(15)
                .iterationTicks(5)
                .maxStacks(20)
                .dependsOn(Skills.SELF_TELEPORT)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL2))
                .devCondition(new DevCondition.DependencyCondition("Self Teleport", "academy:self_teleport"))
        );
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        DistortionVfxClient.register();
        InputSystem.addKeyBinding(Client.KEY_NAME_START, Client.CONFIG.getKeyBinding(
                Client.KEY_NAME_START,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_R,
                        InputConstants.PRESS, InputConstants.MOD_ALT)
        ), ctx -> Client.start());
        InputSystem.addKeyBinding(Client.KEY_NAME_END, Client.CONFIG.getKeyBinding(
                Client.KEY_NAME_END,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_R,
                        InputConstants.RELEASE, InputConstants.MOD_ALT)
        ), ctx -> Client.end());
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.TELEPORT.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.PIERCING_TELEPORTATION.get(),
                        List.of(SelfTeleport.Client.SKILL_INFO),
                        R.textures.piercing_teleportation_icon,
                        60,
                        46
                )
        );
        public static final String KEY_NAME_START = SkillNames.PIERCING_TELEPORTATION + "_start";
        public static final String KEY_NAME_END = SkillNames.PIERCING_TELEPORTATION + "_end";
        public static Config CONFIG = new Config();
        private static PreviewContext currentContext;

        private static void start() {
            if (ClientUtil.hasScreen() || currentContext != null
                    || !AbilitySystemClient.canUseSkill(Skills.PIERCING_TELEPORTATION.get())) return;
            var player = Minecraft.getInstance().player;
            if (player == null) return;
            currentContext = new PreviewContext(player);
            AbilitySystemClient.registerContext(currentContext);
        }

        private static void end() {
            var context = currentContext;
            if (context == null) return;
            var distance = context.distance;
            var useDefaultTarget = context.useDefaultTarget;
            var valid = context.validDestination;
            context.cleanup();
            if (!ClientUtil.hasScreen() && valid) {
                MisakaNetworkClient.send(new TeleportPacket(distance, useDefaultTarget));
                var player = Minecraft.getInstance().player;
                if (player != null) {
                    DistortionVfx.INSTANCE.trigger(
                            (float) player.getX(), (float) player.getY() + 1.0f, (float) player.getZ(),
                            1.0f, 1.0f,
                            0.5f, 0.2f, 0.8f, 0.7f,
                            0.1f, 0.0f, 0.3f, 0.0f);
                }
            }
        }

        public static final class PreviewContext extends ClientContext {
            private final LocalPlayer player;
            private double distance = DEFAULT_DISTANCE;
            private boolean useDefaultTarget = true;
            private boolean validDestination;

            private PreviewContext(LocalPlayer player) {
                this.player = player;
            }

            @SubscribeEvent
            public void onScroll(MouseScrollEvent event) {
                var defaultDestinationDistance = Double.NaN;
                if (useDefaultTarget) {
                    var eyePosition = player.getEyePosition();
                    var defaultCenter = TeleportTargeting.findDefaultPiercingTeleportCenter(
                            player, eyePosition, player.getLookAngle(), DEFAULT_DISTANCE);
                    if (defaultCenter != null) {
                        defaultDestinationDistance = eyePosition.distanceTo(defaultCenter);
                    }
                }
                distance = resolveScrolledDistance(
                        distance, defaultDestinationDistance, useDefaultTarget, event.yOffset);
                useDefaultTarget = false;
                event.setCanceled(true);
            }

            @SubscribeEvent
            public void onLevelRender(LevelRenderEvent event) {
                if (currentContext != this || player.isRemoved()
                        || !AbilitySystemClient.canUseSkill(Skills.PIERCING_TELEPORTATION.get())) {
                    cleanup();
                    return;
                }
                var eyePosition = player.getEyePosition(event.getPartialTick());
                var viewDirection = player.getViewVector(event.getPartialTick());
                var resolvedCenter = useDefaultTarget
                        ? TeleportTargeting.findDefaultPiercingTeleportCenter(
                        player, eyePosition, viewDirection, distance)
                        : TeleportTargeting.findPiercingTeleportCenter(
                        player, eyePosition, viewDirection, distance);
                var center = resolvedCenter == null
                        ? eyePosition.add(viewDirection.scale(distance))
                        : resolvedCenter;
                var dimensions = player.getDimensions(Pose.STANDING);
                validDestination = resolvedCenter != null;
                var feetPosition = center.add(0, -dimensions.height() / 2.0, 0);
                TeleportCursorRenderer.render(event, feetPosition, validDestination);
            }

            private void cleanup() {
                AbilitySystemClient.unregisterContext(this);
                if (currentContext == this) currentContext = null;
            }
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

    static double resolveScrolledDistance(
            double currentDistance,
            double defaultDestinationDistance,
            boolean useDefaultTarget,
            double scrollOffset
    ) {
        var baseDistance = useDefaultTarget && Double.isFinite(defaultDestinationDistance)
                ? defaultDestinationDistance
                : currentDistance;
        return Math.clamp(baseDistance + scrollOffset, 0.0, MAX_DISTANCE);
    }

    public static final class Server {
        @SubscribePacket
        public static void handle(TeleportPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var distance = packet.getDistance();
            var skill = Skills.PIERCING_TELEPORTATION.get();
            if (!Double.isFinite(distance) || distance < 0.0 || distance > MAX_DISTANCE) return;

            var center = packet.useDefaultTarget
                    ? TeleportTargeting.findDefaultPiercingTeleportCenter(player, distance)
                    : TeleportTargeting.findPiercingTeleportCenter(player, distance);
            if (center == null) return;
            var dimensions = player.getDimensions(Pose.STANDING);
            skill.executeActive(player, (ctx, actualCost) -> {
                var previousMovement = player.getDeltaMovement();
                var direction = player.getLookAngle().normalize();
                var destination = new Vec3(center.x, center.y - dimensions.height() / 2.0, center.z);
                SpatialSynergy.Server.teleportNearbyTeam(player, player.level(), destination);
                TeleportSync.teleportInstantly(player, destination);
                player.resetFallDistance();
                if (ctx.milestone() >= 3) {
                    player.setDeltaMovement(previousMovement.x * 0.5, 0.1, previousMovement.z * 0.5);
                    var previousNoPhysics = player.noPhysics;
                    player.noPhysics = true;
                    TimedSkillEffectRuntime.schedule(player, 10, () -> {
                        if (!player.isRemoved()) player.noPhysics = previousNoPhysics;
                    });
                } else {
                    player.setDeltaMovement(direction.scale(0.1));
                }
                player.connection.send(new ClientboundSetEntityMotionPacket(player));
                player.level().playSound(null, player.blockPosition(), SoundEvents.PENETRATE_TELEPORT.get(),
                        SoundSource.PLAYERS, 1.0f, 1.0f);
            });
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class TeleportPacket extends Packet<ServerGamePacketListenerImpl, TeleportPacket> {
        public static final StreamCodec<ByteBuf, TeleportPacket> CODEC = StreamCodec.composite(
                ByteBufCodecs.DOUBLE,
                TeleportPacket::getDistance,
                ByteBufCodecs.BOOL,
                TeleportPacket::useDefaultTarget,
                TeleportPacket::new
        );
        private final double distance;
        private final boolean useDefaultTarget;

        public TeleportPacket(double distance, boolean useDefaultTarget) {
            this.distance = distance;
            this.useDefaultTarget = useDefaultTarget;
        }

        public double getDistance() {
            return distance;
        }

        public boolean useDefaultTarget() {
            return useDefaultTarget;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, TeleportPacket> getPacketType() {
            return PacketTypes.PIERCING_TELEPORTATION_TELEPORT.get();
        }
    }
}
