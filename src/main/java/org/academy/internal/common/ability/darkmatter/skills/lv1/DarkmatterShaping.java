package org.academy.internal.common.ability.darkmatter.skills.lv1;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
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
import org.academy.api.common.ability.SkillProficiencyProfile;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.ProficiencyPolicy;
import org.academy.internal.common.ability.ProficiencySkillSettings;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.darkmatter.DarkmatterEnchantments;
import org.academy.internal.common.ability.darkmatter.skills.lv5.DarkmatterSixWings;
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

public final class DarkmatterShaping extends Skill {
    public DarkmatterShaping() {
        super(Builder
                .of(AbilityCategories.DARKMATTER.get())
                .level(AbilityLevel.LEVEL1)
                .energyCost(5_000)
                .cpCost(50)
                .iterationTicks(20)
                .maxStacks(NO_STACK_LIMIT)
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
            var heldBeforeCast = player.getMainHandItem();
            skill.executeActive(player, context -> context.milestone() >= 2 && !heldBeforeCast.isEmpty()
                    ? 25.0f : 50.0f, (context, actualCost) -> {
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

        private static void tickAutoRepair(ServerPlayer player) {
            if (player.tickCount % 20 != 0 || !player.isAlive() || player.hasDisconnected()) return;
            var skill = Skills.DARKMATTER_SHAPING.get();
            if (!skill.isEnabled(player) || !skill.hasProficiencyMilestone(player, 3)
                    || !ProficiencyPolicy.server(player).enabled()
                    || !ProficiencySkillSettings.isEnabled(
                    player, ProficiencySkillSettings.DARKMATTER_SHAPING_AUTO_REPAIR)) return;
            var held = player.getMainHandItem();
            if (!DarkmatterItemUtil.hasFamilyEnchantment(held) || !held.isDamageableItem()
                    || !held.isDamaged() || held.getMaxDamage() <= 0
                    || held.getMaxDamage() - held.getDamageValue() >= held.getMaxDamage() * 0.25f) return;
            var repair = Math.min(held.getDamageValue(), Math.max(1, Math.round(held.getMaxDamage() * 0.15f)));
            var baseCost = 50.0f * repair / held.getMaxDamage();
            var proficiencyCost = skill.adjustProficiencyCost(
                    player, SkillProficiencyProfile.CostKind.CAST, baseCost);
            var finalCost = DarkmatterSixWings.Server.adjustCategoryCost(
                    player, skill, baseCost, proficiencyCost);
            var system = AbilitySystemServer.getSystem(player);
            if (!system.tryTimedOccupation(player.getUUID(), finalCost, skill, 20)) return;
            held.setDamageValue(Math.max(0, held.getDamageValue() - repair));
            skill.reportActivity(player, true);
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
        }
    }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post event) {
            if (event.getEntity() instanceof ServerPlayer player) Server.tickAutoRepair(player);
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
