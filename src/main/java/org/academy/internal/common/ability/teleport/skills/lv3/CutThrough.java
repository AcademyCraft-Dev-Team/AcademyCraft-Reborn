package org.academy.internal.common.ability.teleport.skills.lv3;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.ability.ClientContext;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.input.MouseScrollEvent;
import org.academy.api.client.render.LevelRenderEvent;
import org.academy.api.client.render.Render;
import org.academy.api.client.renderer.LineBoxRenderer;
import org.academy.api.client.renderer.RendererManager;
import org.academy.api.client.resources.R;
import org.academy.api.client.util.ClientUtil;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.DevCondition;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.client.renderer.effect.DistortionEffectWrapper;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.teleport.skills.SelfTeleport;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.sounds.SoundEvents;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.List;

/** The 26.2 target for the reference Penetrate Teleport skill. */
public final class CutThrough extends Skill {
    private static final double MAX_DISTANCE = 36.0;

    public CutThrough() {
        super(Builder
                .of(AbilityCategories.TELEPORT.get())
                .level(AbilityLevel.LEVEL2)
                .energyCost(10_000)
                .cpCost(20)
                .iterationTicks(8)
                .maxStacks(1)
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
        RendererManager.registerEffectRenderer(DistortionEffectWrapper.INSTANCE);
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
                        Skills.CUT_THROUGH.get(),
                        List.of(SelfTeleport.Client.SKILL_INFO),
                        R.textures.cut_through_icon,
                        60,
                        46
                )
        );
        public static final String KEY_NAME_START = SkillNames.CUT_THROUGH + "_start";
        public static final String KEY_NAME_END = SkillNames.CUT_THROUGH + "_end";
        public static Config CONFIG = new Config();
        private static PreviewContext currentContext;

        private static void start() {
            if (ClientUtil.hasScreen() || currentContext != null
                    || !AbilitySystemClient.canUseSkill(Skills.CUT_THROUGH.get())) return;
            var player = Minecraft.getInstance().player;
            if (player == null) return;
            currentContext = new PreviewContext(player);
            AbilitySystemClient.registerContext(currentContext);
        }

        private static void end() {
            var context = currentContext;
            if (context == null) return;
            var distance = context.distance;
            var valid = context.validDestination;
            context.cleanup();
            if (!ClientUtil.hasScreen() && valid) {
                MisakaNetworkClient.send(new TeleportPacket(distance));
                var player = Minecraft.getInstance().player;
                if (player != null) {
                    DistortionEffectWrapper.INSTANCE.trigger(
                            (float) player.getX(), (float) player.getY() + 1.0f, (float) player.getZ(),
                            1.0f, 1.0f,
                            0.5f, 0.2f, 0.8f, 0.7f,
                            0.1f, 0.0f, 0.3f, 0.0f);
                }
            }
        }

        public static final class PreviewContext extends ClientContext {
            private final LocalPlayer player;
            private double distance = 10.0;
            private boolean validDestination;

            private PreviewContext(LocalPlayer player) {
                this.player = player;
            }

            @SubscribeEvent
            public void onScroll(MouseScrollEvent event) {
                distance = Math.clamp(distance + event.yOffset, 0.0, MAX_DISTANCE);
                event.setCanceled(true);
            }

            @SubscribeEvent
            public void onLevelRender(LevelRenderEvent event) {
                if (currentContext != this || player.isRemoved()
                        || !AbilitySystemClient.canUseSkill(Skills.CUT_THROUGH.get())) {
                    cleanup();
                    return;
                }
                var center = player.getEyePosition(event.getPartialTick())
                        .add(player.getViewVector(event.getPartialTick()).scale(distance));
                var dimensions = player.getDimensions(Pose.STANDING);
                var halfWidth = dimensions.width() / 2.0;
                var halfHeight = dimensions.height() / 2.0;
                var preview = new AABB(
                        center.x - halfWidth, center.y - halfHeight, center.z - halfWidth,
                        center.x + halfWidth, center.y + halfHeight, center.z + halfWidth
                );
                validDestination = player.level().hasChunkAt(BlockPos.containing(center))
                        && player.level().noCollision(player, preview);

                var minecraft = Minecraft.getInstance();
                var renderType = Render.RenderTypes.MINE_DETECT_LINES;
                var camera = minecraft.gameRenderer.mainCamera().position();
                var matrices = event.getMatrixStack();
                matrices.pushPose();
                matrices.translate((float) -camera.x, (float) -camera.y, (float) -camera.z);
                event.submitCustomGeometry(renderType, (snapshot, consumer) ->
                        LineBoxRenderer.renderWireframeBox(snapshot, consumer, preview,
                                1.0f,
                                validDestination ? 1.0f : 0.0f,
                                validDestination ? 1.0f : 0.0f,
                                1.0f));
                matrices.popPose();
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

    public static final class Server {
        @SubscribePacket
        public static void handle(TeleportPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var distance = packet.getDistance();
            if (!Double.isFinite(distance) || distance < 0.0 || distance > MAX_DISTANCE) return;

            var center = player.getEyePosition().add(player.getLookAngle().normalize().scale(distance));
            if (!player.level().hasChunkAt(BlockPos.containing(center))) return;
            var dimensions = player.getDimensions(Pose.STANDING);
            var halfWidth = dimensions.width() / 2.0;
            var halfHeight = dimensions.height() / 2.0;
            var targetBox = new AABB(
                    center.x - halfWidth, center.y - halfHeight, center.z - halfWidth,
                    center.x + halfWidth, center.y + halfHeight, center.z + halfWidth
            );
            if (!player.level().noCollision(player, targetBox)) return;

            Skills.CUT_THROUGH.get().executeActive(player, (ctx, actualCost) -> {
                var direction = player.getLookAngle().normalize();
                player.teleportTo(center.x, center.y - dimensions.height() / 2.0, center.z);
                player.resetFallDistance();
                player.setDeltaMovement(direction.scale(0.1));
                player.connection.send(new ClientboundSetEntityMotionPacket(player));
                player.level().playSound(null, player.blockPosition(), SoundEvents.PENETRATE_TELEPORT.get(),
                        SoundSource.PLAYERS, 1.0f, 1.0f);
            });
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class TeleportPacket extends Packet<ServerGamePacketListenerImpl, TeleportPacket> {
        public static final StreamCodec<ByteBuf, TeleportPacket> CODEC = ByteBufCodecs.DOUBLE
                .map(TeleportPacket::new, TeleportPacket::getDistance);
        private final double distance;

        public TeleportPacket(double distance) {
            this.distance = distance;
        }

        public double getDistance() {
            return distance;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, TeleportPacket> getPacketType() {
            return PacketTypes.CUT_THROUGH_TELEPORT.get();
        }
    }
}
