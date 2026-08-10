package org.academy.internal.common.ability.teleport.skills.lv4;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
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
import org.academy.internal.common.ability.ProficiencyPolicy;
import org.academy.internal.common.ability.teleport.AreaTeleportState;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.List;

public final class AreaTeleportSetup extends Skill {
    public AreaTeleportSetup() {
        super(Builder.of(AbilityCategories.TELEPORT.get())
                .level(AbilityLevel.LEVEL4)
                .energyCost(60_000)
                .iterationTicks(40)
                .maxStacks(1)
                .dependsOn(Skills.AREA_TELEPORT_SELECT)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL4))
                .devCondition(new DevCondition.DependencyCondition("Area Teleport Select", "academy:area_teleport_select")));
    }

    @Override public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(Client.KEY_NAME_MARK, Client.CONFIG.getKeyBinding(Client.KEY_NAME_MARK,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_Y,
                        InputConstants.PRESS, InputConstants.MOD_ALT)), ctx -> Client.mark());
        InputSystem.addKeyBinding(Client.KEY_NAME_SWAP, Client.CONFIG.getKeyBinding(Client.KEY_NAME_SWAP,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_Y,
                        InputConstants.PRESS,
                        InputConstants.MOD_ALT | InputConstants.MOD_CONTROL)),
                ctx -> Client.toggleSwap());
    }

    @Override public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.TELEPORT.get(), new AbilitySystemClient.SkillInfo(
                        Skills.AREA_TELEPORT_SETUP.get(), List.of(AreaTeleportSelect.Client.SKILL_INFO),
                        R.textures.area_teleport_setup_icon, 146, 86));
        public static final String KEY_NAME_MARK = SkillNames.AREA_TELEPORT_SETUP + "_mark";
        public static final String KEY_NAME_SWAP = SkillNames.AREA_TELEPORT_SETUP + "_swap";
        public static Config CONFIG = new Config();
        private static void mark() {
            if (ClientUtil.hasScreen() || !AbilitySystemClient.canUseSkill(Skills.AREA_TELEPORT_SETUP.get())) return;
            MisakaNetworkClient.send(MarkPacket.MARK);
        }
        private static void toggleSwap() {
            if (ClientUtil.hasScreen()
                    || !AbilitySystemClient.canUseSkill(Skills.AREA_TELEPORT_SETUP.get())) return;
            MisakaNetworkClient.send(MarkPacket.TOGGLE_SWAP);
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
        @SubscribePacket public static void handle(MarkPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            if (!Skills.AREA_TELEPORT_SETUP.get().isEnabled(player)
                    || AreaTeleportState.selected(player.getUUID()) == null) return;
            var skill = Skills.AREA_TELEPORT_SETUP.get();
            var milestone = skill.getEffectiveProficiencyMilestone(player);
            var policy = ProficiencyPolicy.server(player);
            if (packet.toggleSwap) {
                if (milestone >= 3 && policy.allowAreaTeleportSwap()) {
                    var enabled = AreaTeleportState.toggleSwap(player.getUUID());
                    player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable(
                            enabled
                                    ? "message.academy.area_teleport.swap_enabled"
                                    : "message.academy.area_teleport.swap_disabled"));
                    AreaTeleportSelect.Server.sync(player);
                }
                return;
            }
            if (player.isShiftKeyDown() && milestone >= 2 && policy.allowAreaTeleportTransforms()) {
                AreaTeleportState.cycleTransform(player.getUUID(), milestone >= 3);
                AreaTeleportSelect.Server.sync(player);
                return;
            }
            var pos = AreaTeleportSelect.Server.pickBlock(player);
            if (pos == null) return;
            AreaTeleportState.setDestination(player.getUUID(), player.level().dimension(), pos);
            AreaTeleportSelect.Server.sync(player);
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class MarkPacket extends Packet<ServerGamePacketListenerImpl, MarkPacket> {
        public static final MarkPacket MARK = new MarkPacket(false);
        public static final MarkPacket TOGGLE_SWAP = new MarkPacket(true);
        public static final StreamCodec<ByteBuf, MarkPacket> CODEC = StreamCodec.of(
                (buf, packet) -> buf.writeBoolean(packet.toggleSwap),
                buf -> buf.readBoolean() ? TOGGLE_SWAP : MARK);
        private final boolean toggleSwap;
        private MarkPacket(boolean toggleSwap) {
            this.toggleSwap = toggleSwap;
        }
        @Override public PacketType<ServerGamePacketListenerImpl, MarkPacket> getPacketType() { return PacketTypes.AREA_TELEPORT_SETUP_MARK.get(); }
    }
}
