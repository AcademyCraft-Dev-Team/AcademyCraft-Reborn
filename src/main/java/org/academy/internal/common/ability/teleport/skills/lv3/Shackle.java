package org.academy.internal.common.ability.teleport.skills.lv3;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
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
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.teleport.TeleportTargeting;
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
    private static final double MAX_RANGE = 32.0;

    public Shackle() {
        super(Builder
                .of(AbilityCategories.TELEPORT.get())
                .level(AbilityLevel.LEVEL3)
                .energyCost(30_000)
                .cpCost(30)
                .iterationTicks(10)
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
            var minecraft = Minecraft.getInstance();
            var player = minecraft.player;
            if (player == null || minecraft.gui.screen() != null
                    || !AbilitySystemClient.canUseSkill(Skills.SHACKLE.get())) return;
            var target = findTarget(player);
            if (target != null) {
                MisakaNetworkClient.send(new UsePacket(target.getId()));
            }
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

            var target = findTarget(player);
            AABB preview;
            if (target != null) {
                preview = target.getBoundingBox().inflate(0.2);
            } else {
                var point = player.getEyePosition(event.getPartialTick())
                        .add(player.getViewVector(event.getPartialTick()).scale(MAX_RANGE));
                preview = new AABB(point.x - 0.5, point.y - 0.5, point.z - 0.5,
                        point.x + 0.5, point.y + 0.5, point.z + 0.5);
            }

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

        private static LivingEntity findTarget(LocalPlayer player) {
            return TeleportTargeting.findFirstLivingEntity(player, MAX_RANGE);
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
        private static final int SHACKLE_DURATION = 160;

        @SubscribePacket
        public static void handle(UsePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            if (!(player.level().getEntity(packet.getTargetEntityId()) instanceof LivingEntity target)
                    || target == player || !target.isAlive()
                    || !target.isPickable()
                    || !canShackle(player, target)
                    || player.distanceToSqr(target) > MAX_RANGE * MAX_RANGE) return;

            Skills.SHACKLE.get().executeActive(player, (ctx, actualCost) -> {
                if (!target.isAlive() || target.level() != player.level()
                        || !canShackle(player, target)
                        || player.distanceToSqr(target) > MAX_RANGE * MAX_RANGE) return;
                target.stopRiding();
                var duration = ctx.milestone() >= 2 && !(target instanceof Player) ? 200 : SHACKLE_DURATION;
                var sourceId = "shackle:" + player.getStringUUID();
                if (ctx.milestone() >= 3) {
                    EntityMotionGuard.imprison(target, sourceId, duration, 2.0, displaced -> {
                        if (displaced.isAlive() && player.isAlive()
                                && displaced.level() == player.level()) {
                            displaced.hurtServer(
                                    player.level(),
                                    SkillDamageSource.of(player, Skills.SHACKLE.get()),
                                    1.2f
                            );
                        }
                    });
                } else {
                    EntityMotionGuard.imprison(target, sourceId, duration);
                }
                target.hurtServer(
                        player.level(),
                        SkillDamageSource.of(player, Skills.SHACKLE.get()),
                        3.0f
                );
            });
        }

        private static boolean canShackle(LivingEntity source, LivingEntity target) {
            return EntityMotionGuard.canBeImprisoned(target)
                    && (EntityMotionGuard.isImprisoned(target)
                    || EntityMotionGuard.canApplyMotionFrom(source, target));
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class UsePacket extends Packet<ServerGamePacketListenerImpl, UsePacket> {
        public static final StreamCodec<ByteBuf, UsePacket> CODEC = ByteBufCodecs.VAR_INT
                .map(UsePacket::new, UsePacket::getTargetEntityId);
        private final int targetEntityId;

        public UsePacket(int targetEntityId) {
            this.targetEntityId = targetEntityId;
        }

        public int getTargetEntityId() {
            return targetEntityId;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, UsePacket> getPacketType() {
            return PacketTypes.SHACKLE_USE.get();
        }
    }
}
