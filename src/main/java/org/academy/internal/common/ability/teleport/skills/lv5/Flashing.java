package org.academy.internal.common.ability.teleport.skills.lv5;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
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
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.resources.R;
import org.academy.api.client.util.ClientUtil;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.DevCondition;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.teleport.TeleportSafety;
import org.academy.internal.common.ability.teleport.skills.lv3.LocationTeleport;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.sounds.SoundEvents;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_A;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_D;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_S;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_W;

public final class Flashing extends Skill {
    static final double DASH_DISTANCE = 8.0;
    static final int REPEAT_TICKS = 6;

    public Flashing() {
        super(Builder
                .of(AbilityCategories.TELEPORT.get())
                .level(AbilityLevel.LEVEL5)
                .energyCost(100_000)
                .passive()
                .initiallyDisabled()
                .maintenanceCost(30)
                .cpCost(10)
                .iterationTicks(40)
                .maxStacks(NO_STACK_LIMIT)
                .dependsOn(Skills.LOCATION_TELEPORT)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL5))
                .devCondition(new DevCondition.DependencyCondition(
                        "Location Teleport", "academy:location_teleport"))
        );
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(Client.KEY_NAME_TOGGLE, Client.CONFIG.getKeyBinding(
                Client.KEY_NAME_TOGGLE,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_H,
                        InputConstants.PRESS, 0)
        ), context -> Client.toggle());
        NeoForge.EVENT_BUS.register(Client.class);
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public enum Direction {
        FORWARD,
        BACK,
        LEFT,
        RIGHT
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.TELEPORT.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.FLASHING.get(),
                        List.of(LocationTeleport.Client.SKILL_INFO),
                        R.textures.flashing_icon,
                        220,
                        20
                )
        );
        public static final String KEY_NAME_TOGGLE = SkillNames.FLASHING + "_toggle";
        public static Config CONFIG = new Config();
        private static final int[] HOLD_TICKS = new int[Direction.values().length];

        private Client() {
        }

        private static void toggle() {
            if (ClientUtil.hasScreen() || Minecraft.getInstance().player == null) return;
            if (!AbilitySystemClient.canToggleSkill(Skills.FLASHING.get())) return;
            MisakaNetworkClient.send(TogglePacket.INSTANCE);
        }

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            var minecraft = Minecraft.getInstance();
            if (minecraft.player == null || minecraft.gui.screen() != null
                    || !AbilitySystemClient.canUseSkill(Skills.FLASHING.get())) {
                resetHolds();
                return;
            }

            update(Direction.FORWARD, InputSystem.isDown(InputSystem.InputType.KEYBOARD, GLFW_KEY_W));
            update(Direction.BACK, InputSystem.isDown(InputSystem.InputType.KEYBOARD, GLFW_KEY_S));
            update(Direction.LEFT, InputSystem.isDown(InputSystem.InputType.KEYBOARD, GLFW_KEY_A));
            update(Direction.RIGHT, InputSystem.isDown(InputSystem.InputType.KEYBOARD, GLFW_KEY_D));
        }

        private static void update(Direction direction, boolean down) {
            var index = direction.ordinal();
            if (!down) {
                HOLD_TICKS[index] = 0;
                return;
            }
            var previous = HOLD_TICKS[index]++;
            if (previous == 0 || HOLD_TICKS[index] % REPEAT_TICKS == 0) {
                MisakaNetworkClient.send(new DashPacket(direction));
            }
        }

        private static void resetHolds() {
            Arrays.fill(HOLD_TICKS, 0);
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
        private static final Map<UUID, Long> LAST_DASH = new WeakHashMap<>();

        private Server() {
        }

        @SubscribePacket
        public static void handleToggle(TogglePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var skill = Skills.FLASHING.get();
            skill.toggle(player);
            if (!skill.isEnabled(player)) LAST_DASH.remove(player.getUUID());
        }

        @SubscribePacket
        public static void handleDash(DashPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var skill = Skills.FLASHING.get();
            if (!skill.isEnabled(player)) return;

            var now = player.level().getGameTime();
            var last = LAST_DASH.get(player.getUUID());
            if (last != null && now - last < 2) return;

            var direction = directionFromLook(player.getLookAngle(), player.getYRot(), packet.direction);
            if (direction.lengthSqr() < 1.0e-6) return;
            var destination = TeleportSafety.findSafe(player,
                    player.position().add(direction.scale(DASH_DISTANCE)));
            if (destination == null) return;

            if (skill.executeActive(player, (context, actualCost) -> {
                player.teleportTo(destination.x, destination.y, destination.z);
                player.resetFallDistance();
                player.setDeltaMovement(0, 0.15, 0);
                player.connection.send(new ClientboundSetEntityMotionPacket(player));
                player.level().playSound(null, player.blockPosition(), SoundEvents.FLASHING.get(),
                        SoundSource.PLAYERS, 1.0f, 1.0f);
            })) {
                LAST_DASH.put(player.getUUID(), now);
            }
        }

        static Vec3 directionFromLook(Vec3 look, float yaw, Direction direction) {
            var forward = look.lengthSqr() < 1.0e-6
                    ? Vec3.directionFromRotation(0, yaw)
                    : look.normalize();
            var right = new Vec3(-forward.z, 0, forward.x);
            if (right.lengthSqr() < 1.0e-6) {
                var yawForward = Vec3.directionFromRotation(0, yaw).normalize();
                right = new Vec3(-yawForward.z, 0, yawForward.x);
            }
            right = right.normalize();
            return switch (direction) {
                case FORWARD -> forward;
                case BACK -> forward.scale(-1);
                case LEFT -> right.scale(-1);
                case RIGHT -> right;
            };
        }
    }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            var skill = Skills.FLASHING.get();
            if (!skill.isEnabled(player)) return;
            var system = AbilitySystemServer.getSystem(player);
            if (!player.isAlive() || player.hasDisconnected()
                    || !system.ensurePermanentOccupation(
                    player.getUUID(),
                    skill.getMaintenanceCost(skill.getLevel(player)),
                    skill
            )) {
                if (skill.isEnabled(player)) skill.toggle(player);
                Server.LAST_DASH.remove(player.getUUID());
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
            return PacketTypes.FLASHING_TOGGLE.get();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class DashPacket extends Packet<ServerGamePacketListenerImpl, DashPacket> {
        public static final StreamCodec<ByteBuf, DashPacket> CODEC = ByteBufCodecs.VAR_INT.map(
                ordinal -> new DashPacket(Direction.values()[Mth.clamp(
                        ordinal, 0, Direction.values().length - 1)]),
                packet -> packet.direction.ordinal()
        );
        private final Direction direction;

        public DashPacket(Direction direction) {
            this.direction = direction;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, DashPacket> getPacketType() {
            return PacketTypes.FLASHING_DASH.get();
        }
    }
}
