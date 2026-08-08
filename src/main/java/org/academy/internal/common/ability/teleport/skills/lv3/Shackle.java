package org.academy.internal.common.ability.teleport.skills.lv3;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.render.LevelRenderEvent;
import org.academy.api.client.render.Render;
import org.academy.api.client.renderer.LineBoxRenderer;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.common.util.LevelUtil;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.entitycontrol.EntityMotionGuard;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

public class Shackle extends Skill {
    private static final double MAX_RANGE = 16.0;
    private static final double TARGET_RADIUS = 1.5;

    public Shackle() {
        super(Builder
                .of(AbilityCategories.TELEPORT.get())
                .level(AbilityLevel.LEVEL3)
                .energyCost(30_000)
                .cpCost(40)
                .iterationTicks(20)
                .maxStacks(1)
                .dependsOn(Skills.SELF_TELEPORT)
        );
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(Client.KEY_NAME_USE, Client.CONFIG.getKeyBinding(Client.KEY_NAME_USE,
                        InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_R, InputConstants.PRESS, InputConstants.MOD_CONTROL))
                , ctx -> Client.onUse());
        NeoForge.EVENT_BUS.register(Client.class);
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final String KEY_NAME_USE = SkillNames.SHACKLE + "_use";
        public static Config CONFIG = new Config();

        public static void onUse() {
            MisakaNetworkClient.send(UsePacket.INSTANCE);
        }

        @SubscribeEvent
        public static void onLevelRender(LevelRenderEvent event) {
            var minecraft = Minecraft.getInstance();
            var player = minecraft.player;
            if (player == null
                    || minecraft.gui.screen() != null
                    || !AbilitySystemClient.canUseSkill(Skills.SHACKLE.get())
                    || !isPreviewing()) {
                return;
            }

            var distance = LevelUtil.getValidViewDistance(player, MAX_RANGE);
            var targetPos = player.getEyePosition(event.getPartialTick())
                    .add(player.getViewVector(event.getPartialTick()).scale(distance));
            var selection = new AABB(
                    targetPos.add(-TARGET_RADIUS, -TARGET_RADIUS, -TARGET_RADIUS),
                    targetPos.add(TARGET_RADIUS, TARGET_RADIUS, TARGET_RADIUS)
            );
            var target = player.level().getEntitiesOfClass(LivingEntity.class, selection,
                            entity -> entity != player && entity.isAlive())
                    .stream()
                    .min((first, second) -> Double.compare(
                            first.distanceToSqr(targetPos),
                            second.distanceToSqr(targetPos)
                    ))
                    .orElse(null);
            var preview = target == null ? selection : target.getBoundingBox().inflate(0.2);

            var camera = minecraft.gameRenderer.mainCamera().position();
            var matrices = event.getMatrixStack();
            matrices.pushPose();
            matrices.translate((float) -camera.x, (float) -camera.y, (float) -camera.z);
            event.submitCustomGeometry(Render.RenderTypes.MINE_DETECT_LINES,
                    (snapshot, consumer) -> LineBoxRenderer.renderWireframeBox(
                            snapshot,
                            consumer,
                            preview,
                            target == null ? 1.0f : 0.6f,
                            target == null ? 0.1f : 0.2f,
                            target == null ? 0.1f : 1.0f,
                            1.0f
                    ));
            matrices.popPose();
        }

        private static boolean isPreviewing() {
            return InputSystem.isDown(InputSystem.InputType.KEYBOARD, InputConstants.KEY_LCONTROL)
                    || InputSystem.isDown(InputSystem.InputType.KEYBOARD, InputConstants.KEY_RCONTROL);
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public Shackle.Client.Config getDefault() {
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
        private static final int SHACKLE_DURATION = 100;

        @SubscribePacket
        public static void handle(UsePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            Skills.SHACKLE.get().executeActive(player, (ctx, actualCost) -> {
                var distance = LevelUtil.getValidViewDistance(player, MAX_RANGE);
                var targetPos = player.getEyePosition().add(player.getLookAngle().scale(distance));
                var box = new AABB(
                        targetPos.add(-TARGET_RADIUS, -TARGET_RADIUS, -TARGET_RADIUS),
                        targetPos.add(TARGET_RADIUS, TARGET_RADIUS, TARGET_RADIUS)
                );
                var target = player.level().getEntitiesOfClass(LivingEntity.class, box,
                                entity -> entity != player
                                        && entity.isAlive()
                                        && EntityMotionGuard.canApplyMotionFrom(player, entity)
                                        && EntityMotionGuard.canBeImprisoned(entity))
                        .stream()
                        .min((first, second) -> Double.compare(
                                first.distanceToSqr(targetPos),
                                second.distanceToSqr(targetPos)
                        ))
                        .orElse(null);

                if (target != null) {
                    target.stopRiding();
                    EntityMotionGuard.imprison(
                            target,
                            "shackle:" + player.getStringUUID(),
                            SHACKLE_DURATION
                    );
                    target.hurtServer(
                            player.level(),
                            SkillDamageSource.of(player, Skills.SHACKLE.get()),
                            3.0f
                    );
                }
            });
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class UsePacket extends Packet<ServerGamePacketListenerImpl, UsePacket> {
        public static final UsePacket INSTANCE = new UsePacket();
        public static final StreamCodec<ByteBuf, UsePacket> CODEC = StreamCodec.unit(INSTANCE);

        private UsePacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, UsePacket> getPacketType() {
            return PacketTypes.SHACKLE_USE.get();
        }
    }
}
