package org.academy.internal.common.ability.teleport.skills.lv2;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.ability.ClientContext;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.render.LevelRenderEvent;
import org.academy.api.client.render.Render;
import org.academy.api.client.renderer.LineBoxRenderer;
import org.academy.api.client.util.ClientUtil;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.teleport.TeleportTargeting;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.world.damagesource.CtaFriendlyFireWhitelist;
import org.academy.api.common.damage.SkillDamageSource;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.ArrayList;
import java.util.Random;

public class Disarm extends Skill {
    private static final double MAX_RANGE = 16.0;

    public Disarm() {
        super(Builder
                .of(AbilityCategories.TELEPORT.get())
                .level(AbilityLevel.LEVEL2)
                .energyCost(10_000)
                .cpCost(40)
                .iterationTicks(20)
                .maxStacks(1)
                .dependsOn(Skills.SELF_TELEPORT)
        );
    }

    private static void disarmTarget(LivingEntity target) {
        var level = target.level();
        var rng = new Random();

        // Priority 1: offhand
        var offHand = target.getOffhandItem();
        if (!offHand.isEmpty()) {
            dropItem(level, target, offHand, rng);
            target.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            return;
        }

        // Priority 2: main hand
        var mainHand = target.getMainHandItem();
        if (!mainHand.isEmpty()) {
            dropItem(level, target, mainHand, rng);
            target.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            return;
        }

        // Priority 3: armor (random piece)
        var armorSlots = new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        var armorItems = new ArrayList<EquipmentSlot>();
        for (var slot : armorSlots) {
            if (!target.getItemBySlot(slot).isEmpty()) armorItems.add(slot);
        }
        if (!armorItems.isEmpty()) {
            var slot = armorItems.get(rng.nextInt(armorItems.size()));
            dropItem(level, target, target.getItemBySlot(slot), rng);
            target.setItemSlot(slot, ItemStack.EMPTY);
            return;
        }

        // Priority 4: hotbar and inventory (Player only)
        if (target instanceof Player playerTarget) {
            var inv = playerTarget.getInventory();
            for (var i = 0; i < 9; i++) {
                var stack = inv.getItem(i);
                if (!stack.isEmpty()) {
                    dropItem(level, target, stack, rng);
                    inv.setItem(i, ItemStack.EMPTY);
                    return;
                }
            }
            for (var i = 9; i < inv.getContainerSize(); i++) {
                var stack = inv.getItem(i);
                if (!stack.isEmpty()) {
                    dropItem(level, target, stack, rng);
                    inv.setItem(i, ItemStack.EMPTY);
                    return;
                }
            }
        }
    }

    private static void dropItem(Level level, LivingEntity target, ItemStack stack, Random rng) {
        var item = new ItemEntity(level, target.getX(), target.getY() + 1, target.getZ(), stack.copy());
        level.addFreshEntity(item);
        item.setDeltaMovement(new Vec3(
                rng.nextDouble() * 4 - 2, rng.nextDouble() * 2, rng.nextDouble() * 4 - 2));
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(Client.KEY_NAME_START, Client.CONFIG.getKeyBinding(
                Client.KEY_NAME_START,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_R,
                        InputConstants.PRESS, InputConstants.MOD_SHIFT)
        ), ctx -> Client.start());
        InputSystem.addKeyBinding(Client.KEY_NAME_END, Client.CONFIG.getKeyBinding(
                Client.KEY_NAME_END,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_R,
                        InputConstants.RELEASE, InputConstants.MOD_SHIFT)
        ), ctx -> Client.end());
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final String KEY_NAME_START = SkillNames.DISARM + "_start";
        public static final String KEY_NAME_END = SkillNames.DISARM + "_end";
        public static Config CONFIG = new Config();
        private static TargetContext currentContext;

        private static void start() {
            if (ClientUtil.hasScreen() || currentContext != null
                    || !AbilitySystemClient.canUseSkill(Skills.DISARM.get())) return;
            var player = Minecraft.getInstance().player;
            if (player == null) return;
            currentContext = new TargetContext(player);
            AbilitySystemClient.registerContext(currentContext);
        }

        private static void end() {
            var context = currentContext;
            if (context == null) return;
            var targetId = context.targetEntityId;
            context.cleanup();
            if (!ClientUtil.hasScreen() && targetId >= 0) {
                MisakaNetworkClient.send(new UsePacket(targetId));
            }
        }

        private static final class TargetContext extends ClientContext {
            private final LocalPlayer player;
            private int targetEntityId = -1;

            private TargetContext(LocalPlayer player) {
                this.player = player;
            }

            @SubscribeEvent
            public void onLevelRender(LevelRenderEvent event) {
                if (currentContext != this || player.isRemoved()
                        || !AbilitySystemClient.canUseSkill(Skills.DISARM.get())) {
                    cleanup();
                    return;
                }
                var minecraft = Minecraft.getInstance();
                var target = TeleportTargeting.findFirstLivingEntity(player, MAX_RANGE);
                AABB preview;
                if (target != null) {
                    targetEntityId = target.getId();
                    preview = target.getBoundingBox().inflate(0.2);
                } else {
                    targetEntityId = -1;
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
                                snapshot, consumer, preview,
                                targetEntityId >= 0 ? 1.0f : 1.0f,
                                targetEntityId >= 0 ? 0.85f : 0.1f,
                                targetEntityId >= 0 ? 0.1f : 0.1f,
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
                public Disarm.Client.Config getDefault() {
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
        public static void handle(UsePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            if (!(player.level().getEntity(packet.getTargetEntityId()) instanceof LivingEntity target)
                    || target == player || !target.isAlive()
                    || CtaFriendlyFireWhitelist.shouldProtect(player, target)
                    || player.distanceToSqr(target) > MAX_RANGE * MAX_RANGE) return;
            Skills.DISARM.get().executeActive(player, (ctx, actualCost) -> {
                if (!target.isAlive() || target.level() != player.level()
                        || player.distanceToSqr(target) > MAX_RANGE * MAX_RANGE) return;
                disarmTarget(target);
                target.hurtServer(
                        player.level(),
                        SkillDamageSource.of(player, Skills.DISARM.get()),
                        1.0f
                );
            });
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
            return PacketTypes.DISARM_USE.get();
        }
    }
}
