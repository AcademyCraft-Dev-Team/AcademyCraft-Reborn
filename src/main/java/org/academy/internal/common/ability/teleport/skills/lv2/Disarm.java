package org.academy.internal.common.ability.teleport.skills.lv2;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.common.util.LevelUtil;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.network.PacketTypes;
import org.lwjgl.glfw.GLFW;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.*;

public class Disarm extends Skill {
    public Disarm() {
        super(Builder
                .of(AbilityCategories.TELEPORT.get())
                .level(AbilityLevel.LEVEL2)
                .cpCost(80)
                .iterationTicks(10)
                .maxStacks(1)
                .dependsOn(Skills.MATTER_WARP)
        );
    }

    @Override
    public float getCpCost(int skillLevel) {
        if (skillLevel >= 2) return 50;
        return super.getCpCost(skillLevel);
    }

    @Override
    public int getIterationTicks(int skillLevel) {
        if (skillLevel >= 3) return 8;
        return super.getIterationTicks(skillLevel);
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(Client.KEY_NAME_USE, Client.CONFIG.getKeyBinding(Client.KEY_NAME_USE,
                new InputSystem.InputPair(InputSystem.InputType.KEYBOARD, new InputSystem.KeyInfo(
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_D)), GLFW.GLFW_RELEASE,
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_MOD_ALT)))))
        , Client::onUse);
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final String KEY_NAME_USE = SkillNames.DISARM + "_use";
        public static Config CONFIG = new Config();
        public static void onUse() { MisakaNetworkClient.sendPacket(UsePacket.INSTANCE); }
        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();
                private Action() {}
                @Override public Disarm.Client.Config getDefault() { return new Config(); }
                @Override public Class<Config> getTypeClass() { return Config.class; }
            }
        }
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
        var armorItems = new java.util.ArrayList<EquipmentSlot>();
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

    private static void dropItem(net.minecraft.world.level.Level level, LivingEntity target, ItemStack stack, Random rng) {
        var item = new ItemEntity(level, target.getX(), target.getY() + 1, target.getZ(), stack.copy());
        level.addFreshEntity(item);
        item.setDeltaMovement(new Vec3(
                rng.nextDouble() * 4 - 2, rng.nextDouble() * 2, rng.nextDouble() * 4 - 2));
    }

    public static final class Server {
        @SubscribePacket
        public static void handle(UsePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            Skills.DISARM.get().executeActive(player, (ctx, actualCost) -> {
                var distance = LevelUtil.getValidViewDistance(player, 8);
                var targetPos = player.getEyePosition().add(player.getLookAngle().scale(distance));
                var box = new AABB(targetPos.add(-2, -2, -2), targetPos.add(2, 2, 2));
                var targets = player.level().getEntitiesOfClass(LivingEntity.class, box,
                        e -> e != player && e.isAlive());

                if (!targets.isEmpty()) {
                    var target = targets.getFirst();
                    disarmTarget(target);
                    target.hurtServer(player.level(), player.damageSources().playerAttack(player), 1.0f);
                }
            });
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class UsePacket extends Packet<ServerGamePacketListenerImpl, UsePacket> {
        public static final UsePacket INSTANCE = new UsePacket();
        public static final StreamCodec<ByteBuf, UsePacket> CODEC = StreamCodec.unit(INSTANCE);
        private UsePacket() {}
        @Override public PacketType<ServerGamePacketListenerImpl, UsePacket> getPacketType() {
            return PacketTypes.DISARM_USE.get();
        }
    }
}
