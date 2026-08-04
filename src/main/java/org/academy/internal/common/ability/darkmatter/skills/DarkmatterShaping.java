package org.academy.internal.common.ability.darkmatter.skills;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
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
import org.academy.internal.common.ability.darkmatter.DarkmatterEnchantments;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.world.item.DarkmatterItemUtil;
import org.academy.internal.common.world.item.Items;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.List;

public final class DarkmatterShaping extends Skill {
    public DarkmatterShaping() {
        super(Builder
                .of(AbilityCategories.DARKMATTER.get())
                .level(AbilityLevel.LEVEL1)
                .energyCost(5_000)
                .cpCost(50)
                .iterationTicks(40)
                .maxStacks(1)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL1))
        );
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(Client.KEY_NAME_CAST, Client.CONFIG.getKeyBinding(
                Client.KEY_NAME_CAST,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_U,
                        InputConstants.RELEASE, 0)
        ), context -> Client.cast());
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.DARKMATTER.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.DARKMATTER_SHAPING.get(),
                        List.of(),
                        R.textures.darkmatter_shaping_icon,
                        20,
                        40
                )
        );
        public static final String KEY_NAME_CAST = SkillNames.DARKMATTER_SHAPING + "_cast";
        public static Config CONFIG = new Config();

        private Client() {
        }

        private static void cast() {
            if (ClientUtil.hasScreen()
                    || !AbilitySystemClient.canUseSkill(Skills.DARKMATTER_SHAPING.get())) return;
            MisakaNetworkClient.send(CastPacket.INSTANCE);
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
        public static void handle(CastPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var skill = Skills.DARKMATTER_SHAPING.get();
            skill.executeActive(player, (context, actualCost) -> {
                var held = player.getMainHandItem();
                if (held.isEmpty()) {
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DARKMATTER.get()));
                } else {
                    DarkmatterItemUtil.repairDurability(held);
                    DarkmatterItemUtil.toggleEnchantment(player.registryAccess(), held,
                            DarkmatterEnchantments.DARKMATTER);
                    if (DarkmatterSixWings.Server.isActive(player)) {
                        for (var slot : new EquipmentSlot[]{
                                EquipmentSlot.HEAD,
                                EquipmentSlot.CHEST,
                                EquipmentSlot.LEGS,
                                EquipmentSlot.FEET
                        }) {
                            DarkmatterItemUtil.toggleEnchantment(
                                    player.registryAccess(),
                                    player.getItemBySlot(slot),
                                    DarkmatterEnchantments.DARKMATTER_DEFENSE
                            );
                        }
                    }
                }
                player.getInventory().setChanged();
                player.inventoryMenu.broadcastChanges();
                if (player.containerMenu != player.inventoryMenu) player.containerMenu.broadcastChanges();
            });
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class CastPacket extends Packet<ServerGamePacketListenerImpl, CastPacket> {
        public static final CastPacket INSTANCE = new CastPacket();
        public static final StreamCodec<ByteBuf, CastPacket> CODEC = StreamCodec.unit(INSTANCE);

        private CastPacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, CastPacket> getPacketType() {
            return PacketTypes.DARKMATTER_SHAPING_CAST.get();
        }
    }
}
