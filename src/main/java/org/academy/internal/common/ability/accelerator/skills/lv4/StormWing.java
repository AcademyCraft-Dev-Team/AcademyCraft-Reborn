package org.academy.internal.common.ability.accelerator.skills.lv4;

import io.netty.buffer.ByteBuf;
import org.academy.api.common.ability.WingControlIntent;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
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
import org.academy.api.client.config.WingFlightConfig;
import org.academy.internal.client.ability.WingFlightClient;
import org.academy.internal.common.ability.accelerator.flight.WingFlightRuntime;
import org.academy.api.client.hud.ability.ToggleStatusHud;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.resources.R;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.DevCondition;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.ability.SkillProficiencyProfile;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.team.TeamRelations;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.effect.TimedSkillEffectRuntime;
import org.academy.internal.common.ability.accelerator.flight.WingFlightPose;
import org.academy.internal.common.ability.accelerator.skills.lv5.wing.BlackWing;
import org.academy.internal.common.ability.accelerator.skills.lv5.wing.PlatinumWing;
import org.academy.internal.common.ability.accelerator.skills.lv5.wing.WhiteWing;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.*;

import static org.lwjgl.glfw.GLFW.*;

public final class StormWing extends Skill {
    public static final float RESERVED_CP = 40.0f;
    private static final float UPKEEP_CP = 10.0f;
    private static final int UPKEEP_INTERVAL_TICKS = 20;

    public StormWing() {
        super(Builder
                .of(AbilityCategories.ACCELERATOR.get())
                .level(AbilityLevel.LEVEL4)
                .energyCost(60_000)
                .passive()
                .initiallyDisabled()
                .maintenanceCost(RESERVED_CP)
                .iterationTicks(5)
                .maxStacks(NO_STACK_LIMIT)
                .dependsOn(Skills.VECTOR_REFLECTION)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL4))
                .devCondition(new DevCondition.DependencyCondition("Vector Reflection", "academy:vector_reflection"))
        );
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        Client.CONFIG.register(this);

        InputSystem.addKeyBinding(Client.KEY_NAME_TOGGLE, Client.CONFIG.getKeyBinding(Client.KEY_NAME_TOGGLE,
                InputSystem.combo(
                        InputSystem.InputType.KEYBOARD,
                        GLFW_KEY_N,
                        GLFW_RELEASE,
                        GLFW_MOD_ALT
                )
        ), _ -> Client.toggle());
        ToggleStatusHud.Companion.registerStateProvider(Skills.STORM_WING.get(), () -> {
            var player = Minecraft.getInstance().player;
            return player != null && player.getData(AttachmentTypes.ACTIVATED_STORM_WING.get());
        });
        NeoForge.EVENT_BUS.register(Client.class);
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public enum State {
        FRONT, BACK, RIGHT, LEFT, KEEP, BOOST
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.ACCELERATOR.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.STORM_WING.get(),
                        List.of(VectorReflection.Client.SKILL_INFO),
                        R.textures.ability.accelerator.skill.storm_wing.icon,
                        130, 20
                )
        );

        public static final String KEY_NAME_TOGGLE = SkillNames.STORM_WING + "_toggle";
        public static Config CONFIG = new Config();
        private static final WingControlIntent.Sender CONTROL_SENDER = new WingControlIntent.Sender();

        @SubscribeEvent
        public static void tick(ClientTickEvent.Post event) {
            var mc = Minecraft.getInstance();
            var player = mc.player;
            if (player == null || !player.getData(AttachmentTypes.ACTIVATED_STORM_WING.get()) || mc.isPaused()) {
                CONTROL_SENDER.reset();
                return;
            }
            var input = WingFlightClient.readControl();
            input = new WingControlIntent(input.buttons(), input.yaw(), input.pitch(), CONFIG.getRemainingMomentum());
            if (CONTROL_SENDER.shouldSend(input, System.nanoTime())) MisakaNetworkClient.send(new ControlPacket(input));
        }

        public static void toggle() {
            if (!AbilitySystemClient.beginToggleRequest(Skills.STORM_WING.get())) return;
            MisakaNetworkClient.send(TogglePacket.INSTANCE);
        }

        public static class Config extends WingFlightConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public StormWing.Client.Config getDefault() {
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
        private static final Map<UUID, ArrayDeque<TrailPoint>> TRAILS = new HashMap<>();

        private Server() {
        }

        @SubscribePacket
        public static void handleToggle(TogglePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var skill = Skills.STORM_WING.get();
            if (!skill.isEnabled(player)) {
                BlackWing.Server.forceDeactivate(player);
                WhiteWing.Server.forceDeactivate(player);
                PlatinumWing.Server.forceDeactivate(player);
            }
            skill.toggle(player);
            sync(player);
        }

        public static void forceDeactivate(ServerPlayer player) {
            if (player == null) return;
            var skill = Skills.STORM_WING.get();
            var data = skill.getRuntimeData(player).orElse(null);
            var system = AbilitySystemServer.getSystem(player);
            if (data != null && data.isEnabled()) {
                system.toggleSkill(player.getUUID(), skill.getKeyString());
            }
            system.releaseMaintenanceOccupation(player.getUUID(), skill.getKeyString());
            sync(player);
        }

        @SubscribePacket
        public static void handleControl(ControlPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            if (isActive(player)) WingFlightRuntime.accept(player, Skills.STORM_WING.get(), packet.input);
        }

        public static boolean isActive(ServerPlayer player) {
            return Skills.STORM_WING.get().isEnabled(player)
                    && player.getData(AttachmentTypes.ACTIVATED_STORM_WING.get());
        }

        private static void sync(ServerPlayer player) {
            var active = Skills.STORM_WING.get().isEnabled(player)
                    && player.isAlive() && !player.hasDisconnected();
            var type = AttachmentTypes.ACTIVATED_STORM_WING.get();
            var wasActive = player.getData(type);
            if (wasActive != active) {
                player.setData(type, active);
                player.syncData(type);
            }
            if (!active) {
                // This path runs for every player tick. Never clear fall distance here.
                WingFlightRuntime.clear(player, Skills.STORM_WING.get());
                    if (wasActive) WingFlightPose.sync(player, WingFlightPose.Pose.IDLE);
            }
        }

        private static void tick(ServerPlayer player) {
            var skill = Skills.STORM_WING.get();
            var active = skill.isEnabled(player) && player.isAlive() && !player.hasDisconnected();
            if (active) {
                var system = AbilitySystemServer.getSystem(player);
                active = system.ensurePermanentOccupation(
                        player.getUUID(), skill.adjustProficiencyCost(
                                player, SkillProficiencyProfile.CostKind.MAINTENANCE, RESERVED_CP), skill);
                if (!active && skill.isEnabled(player)) skill.toggle(player);
                if (active && player.tickCount % UPKEEP_INTERVAL_TICKS == 0
                        && !system.tryTimedOccupation(player.getUUID(), UPKEEP_CP, skill, 5)) {
                    forceDeactivate(player);
                    active = false;
                }
            }
            sync(player);
            if (!isActive(player)) return;
            WingFlightRuntime.tick(player, skill);
            var now = player.level().getGameTime();
            var boosting = player.getData(AttachmentTypes.WING_FLIGHT_POSE.get()) == WingFlightPose.Pose.FAST;
            tickTurbulence(player, now, boosting);
        }

        private static void tickTurbulence(ServerPlayer player, long now, boolean boosting) {
            var skill = Skills.STORM_WING.get();
            var trails = TRAILS.computeIfAbsent(player.getUUID(), _ -> new ArrayDeque<>());
            trails.removeIf(point -> point.expiresAt() <= now);
            if (boosting && skill.hasProficiencyMilestone(player, 3) && player.tickCount % 2 == 0) {
                trails.addLast(new TrailPoint(player.position(), player.getLookAngle(), now + 40));
                while (trails.size() > 20) trails.removeFirst();
            }
            if (!skill.hasProficiencyMilestone(player, 3)) {
                trails.clear();
                return;
            }
            var turbulenceRadius = skill.scaledRange(player, 1.5f);
            for (var point : trails) {
                var area = new AABB(point.position(), point.position()).inflate(turbulenceRadius);
                for (var ally : player.level().getEntitiesOfClass(
                        LivingEntity.class,
                        area,
                        entity -> entity != player && entity.isAlive()
                                && TeamRelations.areAllied(player, entity))) {
                    if (TimedSkillEffectRuntime.get(
                            player.getUUID(), ally.getUUID(), skill,
                            "turbulence_ally", now).isPresent()) continue;
                    if (TimedSkillEffectRuntime.put(
                            player, ally.getUUID(), skill, "turbulence_ally", 20, 0.0f)) {
                        ally.setDeltaMovement(ally.getDeltaMovement().add(point.direction().scale(0.2)));
                        ally.hurtMarked = true;
                    }
                }
                for (var projectile : player.level().getEntitiesOfClass(
                        Projectile.class,
                        area,
                        entity -> entity.isAlive() && entity.getOwner() != player)) {
                    if (TimedSkillEffectRuntime.get(
                            player.getUUID(), projectile.getUUID(), skill,
                            "turbulence_projectile", now).isPresent()) continue;
                    if (TimedSkillEffectRuntime.put(
                            player, projectile.getUUID(), skill,
                            "turbulence_projectile", 20, 0.0f)) {
                        projectile.setDeltaMovement(projectile.getDeltaMovement().scale(0.6));
                        projectile.hurtMarked = true;
                    }
                }
            }
            if (trails.isEmpty()) TRAILS.remove(player.getUUID());
        }

        private record TrailPoint(Vec3 position, Vec3 direction, long expiresAt) {
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
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class ControlPacket extends Packet<ServerGamePacketListenerImpl, ControlPacket> {
        public static final StreamCodec<ByteBuf, ControlPacket> CODEC = StreamCodec.of(
                (buf, packet) -> WingControlIntent.CODEC.encode(buf, packet.input),
                buf -> new ControlPacket(WingControlIntent.CODEC.decode(buf)));
        private final WingControlIntent input;

        public ControlPacket(WingControlIntent input) {
            this.input = input;
        }
        @Override
        public PacketType<ServerGamePacketListenerImpl, ControlPacket> getPacketType() {
            return PacketTypes.STORM_WING_CONTROL.get();
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
            return PacketTypes.STORM_WING_TOGGLE.get();
        }
    }
}
