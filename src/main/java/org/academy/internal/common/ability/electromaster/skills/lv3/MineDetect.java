package org.academy.internal.common.ability.electromaster.skills.lv3;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.config.SkillSettingsRegistry;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.resources.R;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.DevCondition;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.client.ability.electromaster.mine.MineDetectClient;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.List;

public final class MineDetect extends Skill {
    static final int RADIUS = 64;
    static final int RADIUS_SQUARED = RADIUS * RADIUS;

    public MineDetect() {
        super(Builder
                .of(AbilityCategories.ELECTROMASTER.get())
                .level(AbilityLevel.LEVEL3)
                .energyCost(5_000)
                .passive()
                .initiallyDisabled()
                .maintenanceCost(30)
                .iterationTicks(20)
                .maxStacks(NO_STACK_LIMIT)
                .dependsOn(Skills.MAGNET_MANIPULATION)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL3))
        );
    }

    static boolean isInsideScanRadius(int dx, int dy, int dz) {
        return dx * dx + dy * dy + dz * dz <= RADIUS_SQUARED;
    }

    static boolean isOrePath(String path) {
        return path.endsWith("_ore") || path.contains("ore_") || path.contains("_ore_");
    }

    @Override
    public void initClient() {
        org.academy.internal.client.ability.electromaster.mine.OreScanCache.prepare();
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        Client.CONFIG.registerSettings(this);
        InputSystem.addKeyBinding(
                Client.KEY_NAME_TOGGLE,
                Client.CONFIG.getKeyBinding(
                        Client.KEY_NAME_TOGGLE,
                        InputSystem.combo(
                                InputSystem.InputType.KEYBOARD,
                                InputConstants.KEY_M,
                                InputConstants.RELEASE,
                                InputConstants.MOD_ALT
                        )
                ),
                _ -> Client.toggle()
        );
        NeoForge.EVENT_BUS.register(Client.class);
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.ELECTROMASTER.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.MINE_DETECT.get(),
                        List.of(MagnetManipulation.Client.SKILL_INFO),
                        R.textures.mine_detect_icon,
                        96,
                        24
                )
        );
        public static final String KEY_NAME_TOGGLE = SkillNames.MINE_DETECT + "_toggle";
        public static Config CONFIG = new Config();

        private Client() {
        }

        private static void toggle() {
            if (!AbilitySystemClient.beginToggleRequest(Skills.MINE_DETECT.get())) return;
            MisakaNetworkClient.send(TogglePacket.INSTANCE);
        }

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            var minecraft = Minecraft.getInstance();
            var level = minecraft.level;
            var player = minecraft.player;
            var skill = Skills.MINE_DETECT.get();
            if (level == null || player == null || !AbilitySystemClient.canUseSkill(skill)) {
                MineDetectClient.reset();
                return;
            }
            var milestone = AbilitySystemClient.getSkillProficiencyMilestone(skill);
            MineDetectClient.tick(level, player.blockPosition(), CONFIG.getEffectiveScanRadius(milestone));
        }

        public static final class Config extends KeyBindingConfig {
            public static final int MIN_SCAN_RADIUS = 8;
            public static final int MAX_SCAN_RADIUS = 80;
            private int scanRadius = MAX_SCAN_RADIUS;

            public int getScanRadius() {
                return Math.clamp(scanRadius, MIN_SCAN_RADIUS, MAX_SCAN_RADIUS);
            }

            public void setScanRadius(int radius) {
                scanRadius = Math.clamp(radius, MIN_SCAN_RADIUS, MAX_SCAN_RADIUS);
            }

            public int getEffectiveScanRadius(int milestone) {
                return Math.min(getScanRadius(), milestone >= 2 ? MAX_SCAN_RADIUS : RADIUS);
            }

            public void registerSettings(Skill skill) {
                SkillSettingsRegistry.INSTANCE.register(skill, new SkillSettingsRegistry.Module(
                        "mine_detect_range", "app.academy.skill_settings.advanced.mine_detect.range_limit",
                        List.of(new SkillSettingsRegistry.FloatRange(
                                "scan_radius", "app.academy.skill_settings.advanced.mine_detect.scan_radius",
                                MIN_SCAN_RADIUS, MAX_SCAN_RADIUS, 1.0f,
                                () -> (float) getScanRadius(),
                                value -> setScanRadius(Math.round(value)),
                                () -> AcademyCraftClient.Config.INSTANCE.save(),
                                value -> Integer.toString(Math.round(value))
                        ))));
            }

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
        public static void handle(TogglePacket packet) {
            Skills.MINE_DETECT.get().toggle(packet.getPacketListener().getPlayer());
        }
    }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;

            var skill = Skills.MINE_DETECT.get();
            if (!skill.isEnabled(player)) return;

            var system = AbilitySystemServer.getSystem(player);
            var uuid = player.getUUID();
            if (!system.ensurePermanentOccupation(
                    uuid,
                    skill.getMaintenanceCost(player),
                    skill
            )) {
                system.toggleSkill(uuid, skill.getKeyString());
            }
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class TogglePacket extends Packet<ServerGamePacketListenerImpl, TogglePacket> {
        public static final TogglePacket INSTANCE = new TogglePacket();
        public static final StreamCodec<ByteBuf, TogglePacket> CODEC = StreamCodec.unit(INSTANCE);

        private TogglePacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, TogglePacket> getPacketType() {
            return PacketTypes.MINE_DETECT_TOGGLE.get();
        }
    }
}
