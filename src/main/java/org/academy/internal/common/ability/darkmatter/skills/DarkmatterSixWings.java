package org.academy.internal.common.ability.darkmatter.skills;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.renderer.RendererManager;
import org.academy.api.client.resources.R;
import org.academy.api.client.util.ClientUtil;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.DevCondition;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.ability.SkillProficiencyProfile;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.client.renderer.effect.DarkmatterSixWingsEffectRenderer;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.server.ability.SkillFlightController;
import org.academy.internal.common.attribute.PlayerAttributeRuntime;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.List;

public final class DarkmatterSixWings extends Skill {
    public static final float RESERVED_CP = 70.0f;
    private static final Identifier FLIGHT_SOURCE =
            AcademyCraft.academy(SkillNames.DARKMATTER_SIX_WINGS);
    private static final Identifier TRUE_RESISTANCE_MODIFIER_ID =
            AcademyCraft.academy("darkmatter_six_wings_true_resistance");

    public DarkmatterSixWings() {
        super(Builder
                .of(AbilityCategories.DARKMATTER.get())
                .level(AbilityLevel.LEVEL5)
                .energyCost(100_000)
                .passive()
                .initiallyDisabled()
                .maintenanceCost(RESERVED_CP)
                .iterationTicks(40)
                .dependsOn(Skills.DARKMATTER_SHAPING)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL5))
                .devCondition(new DevCondition.DependencyCondition(
                        "Dark Matter Shaping", "academy:darkmatter_shaping"))
        );
    }

    @Override
    public void initClient() {
        RendererManager.registerEffectRenderer(DarkmatterSixWingsEffectRenderer.INSTANCE);
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(Client.KEY_NAME_TOGGLE, Client.CONFIG.getKeyBinding(
                Client.KEY_NAME_TOGGLE,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_R,
                        InputConstants.RELEASE, InputConstants.MOD_ALT)
        ), context -> Client.toggle());
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.DARKMATTER.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.DARKMATTER_SIX_WINGS.get(),
                        List.of(DarkmatterShaping.Client.SKILL_INFO),
                        R.textures.darkmatter_six_wings_icon,
                        150,
                        72
                )
        );
        public static final String KEY_NAME_TOGGLE = SkillNames.DARKMATTER_SIX_WINGS + "_toggle";
        public static Config CONFIG = new Config();

        private Client() {
        }

        private static void toggle() {
            if (ClientUtil.hasScreen()
                    || !AbilitySystemClient.beginToggleRequest(Skills.DARKMATTER_SIX_WINGS.get())) return;
            MisakaNetworkClient.send(TogglePacket.INSTANCE);
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
        private Server() {
        }

        @SubscribePacket
        public static void handle(TogglePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            Skills.DARKMATTER_SIX_WINGS.get().toggle(player);
            sync(player);
        }

        public static boolean isActive(ServerPlayer player) {
            return Skills.DARKMATTER_SIX_WINGS.get().isEnabled(player)
                    && player.getData(AttachmentTypes.DARKMATTER_SIX_WINGS.get());
        }

        public static float adjustCategoryCost(ServerPlayer player, Skill skill,
                                               float baseCost, float adjustedCost) {
            if (player == null || skill == null || !Float.isFinite(baseCost)
                    || !Float.isFinite(adjustedCost) || baseCost < 0.0f || adjustedCost < 0.0f
                    || skill == Skills.DARKMATTER_SIX_WINGS.get()
                    || skill.getCategory() != AbilityCategories.DARKMATTER.get()
                    || !isActive(player)
                    || !Skills.DARKMATTER_SIX_WINGS.get().hasProficiencyMilestone(player, 3)) {
                return adjustedCost;
            }
            return Math.max(baseCost * 0.75f, adjustedCost * 0.9f);
        }

        private static void sync(ServerPlayer player) {
            var active = Skills.DARKMATTER_SIX_WINGS.get().isEnabled(player)
                    && player.isAlive() && !player.hasDisconnected();
            var type = AttachmentTypes.DARKMATTER_SIX_WINGS.get();
            if (player.getData(type) != active) {
                player.setData(type, active);
                player.syncData(type);
            }
            SkillFlightController.setSource(player, FLIGHT_SOURCE, active);
            var flightSpeed = active
                    && Skills.DARKMATTER_SIX_WINGS.get().hasProficiencyMilestone(player, 2)
                    ? 0.0575f : 0.05f;
            if (Math.abs(player.getAbilities().getFlyingSpeed() - flightSpeed) > 1.0e-5f) {
                player.getAbilities().setFlyingSpeed(flightSpeed);
                player.onUpdateAbilities();
            }
            PlayerAttributeRuntime.syncTrueResistanceModifier(
                    player,
                    TRUE_RESISTANCE_MODIFIER_ID,
                    4.0,
                    active
            );
        }

        private static void tick(ServerPlayer player) {
            var skill = Skills.DARKMATTER_SIX_WINGS.get();
            var active = skill.isEnabled(player) && player.isAlive() && !player.hasDisconnected();
            if (active) {
                active = AbilitySystemServer.getSystem(player).ensurePermanentOccupation(
                        player.getUUID(), skill.adjustProficiencyCost(
                                player, SkillProficiencyProfile.CostKind.MAINTENANCE, RESERVED_CP), skill);
                if (!active && skill.isEnabled(player)) skill.toggle(player);
            }
            sync(player);
        }
    }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post event) {
            if (event.getEntity() instanceof ServerPlayer player) Server.tick(player);
        }

        @SubscribeEvent
        public static void onIncomingDamage(LivingIncomingDamageEvent event) {
            if (!(event.getEntity() instanceof ServerPlayer player)
                    || event.isCanceled()
                    || !(event.getAmount() > 0.0f)
                    || event.getAmount() >= 2.0f
                    || !Server.isActive(player)) return;
            if (AbilitySystemServer.getSystem(player).tryTimedOccupation(
                    player.getUUID(),
                    10.0f,
                    Skills.DARKMATTER_SIX_WINGS.get(),
                    5
            )) {
                event.setCanceled(true);
            }
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class TogglePacket extends Packet<ServerGamePacketListenerImpl, TogglePacket> {
        public static final TogglePacket INSTANCE = new TogglePacket();
        public static final StreamCodec<ByteBuf, TogglePacket> CODEC = StreamCodec.unit(INSTANCE);
        private TogglePacket() {
        }
        @Override public PacketType<ServerGamePacketListenerImpl, TogglePacket> getPacketType() {
            return PacketTypes.DARKMATTER_SIX_WINGS_TOGGLE.get();
        }
    }
}
