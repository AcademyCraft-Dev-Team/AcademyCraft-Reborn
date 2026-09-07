package org.academy.internal.common.ability.accelerator.skills.lv5;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.LivingEntity;
import org.academy.api.common.ability.VortexAttackPattern;
import org.academy.api.common.ability.WingControlIntent;
import org.academy.api.common.ability.VortexAttackTargets;
import org.academy.api.common.ability.VortexAttackSequence;
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
import org.academy.api.client.hud.ability.ToggleStatusHud;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.resources.R;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.DevCondition;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.accelerator.skills.lv4.StormWing;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.lwjgl.glfw.GLFW.*;

public final class BlackWing extends Skill {
    public BlackWing() {
        super(Builder.of(AbilityCategories.ACCELERATOR.get())
                .damage()
                .level(AbilityLevel.LEVEL5)
                .energyCost(100_000)
                .passive()
                .initiallyDisabled()
                .maintenanceCost(60)
                .iterationTicks(10)
                .maxStacks(NO_STACK_LIMIT)
                .dependsOn(Skills.STORM_WING)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL5))
                .devCondition(new DevCondition.DependencyCondition("Storm Wing", "academy:storm_wing")));
    }

    @Override
    public void initClient() {
        AdvancedWingSweepPacket.initClient();
        BlackWingAttackPacket.initClient();
        BlackWingStatePacket.initClient();
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(Client.KEY_NAME_TOGGLE, Client.CONFIG.getKeyBinding(
                Client.KEY_NAME_TOGGLE,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, GLFW_KEY_B, GLFW_RELEASE, GLFW_MOD_ALT)
        ), _ -> Client.toggle());
        ToggleStatusHud.Companion.registerStateProvider(Skills.BLACK_WING.get(), () -> {
            var player = Minecraft.getInstance().player;
            return player != null && player.getData(AttachmentTypes.ACTIVATED_BLACK_WING.get());
        });
        NeoForge.EVENT_BUS.register(Client.class);
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.ACCELERATOR.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.BLACK_WING.get(),
                        List.of(StormWing.Client.SKILL_INFO),
                        R.textures.black_wing_icon,
                        155, 20
                )
        );
        public static final String KEY_NAME_TOGGLE = SkillNames.BLACK_WING + "_toggle";
        public static Config CONFIG = new Config();
        private static final WingControlIntent.Sender CONTROL_SENDER = new WingControlIntent.Sender();
        private static long controlEpoch = -1, controlSequence;
        private static net.minecraft.client.multiplayer.ClientLevel controlLevel;

        private Client() {
        }

        @SubscribeEvent
        public static void tick(ClientTickEvent.Post event) {
            var player = Minecraft.getInstance().player;
            long epoch = player == null ? -1 : org.academy.internal.client.render.vfx.WingVfx.blackActivationEpoch(player.getId());
            if (player == null || !player.getData(AttachmentTypes.ACTIVATED_BLACK_WING.get()) || epoch < 0) {
                CONTROL_SENDER.reset(); controlEpoch = -1; controlLevel = null; return;
            }
            if (controlEpoch != epoch || controlLevel != Minecraft.getInstance().level) {
                CONTROL_SENDER.reset(); controlEpoch = epoch; controlSequence = 0;
                controlLevel = Minecraft.getInstance().level;
            }
            var input = WingFlightSupport.readControl();
            if (CONTROL_SENDER.shouldSend(input, player.tickCount))
                MisakaNetworkClient.send(new ControlPacket(epoch, ++controlSequence, input));
        }

        private static void toggle() {
            if (!AbilitySystemClient.beginToggleRequest(Skills.BLACK_WING.get())) return;
            MisakaNetworkClient.send(TogglePacket.INSTANCE);
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
        private static final Map<UUID, Long> LAST_BOOST_TICK = new HashMap<>();
        private static final Map<ServerPlayer, AttackSession> ATTACK_SESSIONS = new java.util.WeakHashMap<>();
        private static final Map<ServerPlayer, java.util.Set<ServerPlayer>> OBSERVERS = new java.util.WeakHashMap<>();
        private static long nextEpoch;

        private static final class AttackSession {
            final long epoch = ++nextEpoch;
            final VortexAttackSequence attacks = new VortexAttackSequence();
            final net.minecraft.server.level.ServerLevel level;
            long sequence;
            long lastControlTick = Long.MIN_VALUE;
            final WingControlIntent.Mailbox controls = new WingControlIntent.Mailbox();
            BlackWingAttackPacket lastAttack;
            AttackSession(ServerPlayer player) { level = player.level(); }
        }

        private static void sendObservers(ServerPlayer player, java.util.function.Consumer<ServerPlayer> send) {
            send.accept(player);
            var observers = OBSERVERS.get(player);
            if (observers == null) return;
            for (var observer : observers) {
                if (observer != player && !observer.hasDisconnected() && observer.level() == player.level()
                        && observer.distanceToSqr(player) <= 128.0 * 128.0) send.accept(observer);
            }
        }

        private static AttackSession refreshSession(ServerPlayer player) {
            var session = ATTACK_SESSIONS.get(player);
            boolean active = isActive(player) && player.isAlive() && !player.hasDisconnected();
            if (session != null && (!active || session.level != player.level())) {
                var ended = new BlackWingStatePacket(player.getId(), session.epoch, session.sequence, false);
                sendObservers(player, observer -> MisakaNetworkServer.send(observer, ended));
                ATTACK_SESSIONS.remove(player);
                session = null;
            }
            if (active && session == null) {
                session = new AttackSession(player);
                ATTACK_SESSIONS.put(player, session);
                var started = new BlackWingStatePacket(player.getId(), session.epoch, 0, true);
                sendObservers(player, observer -> MisakaNetworkServer.send(observer, started));
            }
            return session;
        }

        private static void startTracking(ServerPlayer observer, ServerPlayer target) {
            OBSERVERS.computeIfAbsent(target, _ -> java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>())).add(observer);
            var session = refreshSession(target);
            if (session == null) return;
            var last = session.lastAttack;
            float progress = last == null ? 1f : last.pattern().progress(last.startTick(), target.level().getGameTime(), 0f);
            boolean snapshot = progress >= 0f && progress < 1f;
            MisakaNetworkServer.send(observer, new BlackWingStatePacket(target.getId(), session.epoch,
                    snapshot ? session.sequence - 1 : session.sequence, true));
            if (snapshot) MisakaNetworkServer.send(observer, new BlackWingAttackPacket(target.getId(), last.pattern(),
                    last.startTick(), last.targets(), session.epoch, session.sequence, progress));
        }

        private static void stopTracking(ServerPlayer observer, ServerPlayer target) {
            var observers = OBSERVERS.get(target);
            if (observers != null) observers.remove(observer);
            // Entity removal also clears the client state. Do not cancel the epoch: it may be tracked again.
        }

        private static void disconnected(ServerPlayer player) {
            ATTACK_SESSIONS.remove(player);
            OBSERVERS.remove(player);
            OBSERVERS.values().forEach(observers -> observers.remove(player));
            LAST_BOOST_TICK.remove(player.getUUID());
        }

        private Server() {
        }

        @SubscribePacket
        public static void handleToggle(TogglePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var skill = Skills.BLACK_WING.get();
            if (!skill.isEnabled(player)) {
                StormWing.Server.forceDeactivate(player);
                WhiteWing.Server.forceDeactivate(player);
                PlatinumWing.Server.forceDeactivate(player);
            }
            skill.toggle(player);
            WingFlightSupport.sync(player, AttachmentTypes.ACTIVATED_BLACK_WING.get(),
                    skill.isEnabled(player), LAST_BOOST_TICK);
            refreshSession(player);
        }

        @SubscribePacket
        public static void handleControl(ControlPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            if (!isActive(player)) return;
            var session = refreshSession(player);
            if (session == null || session.epoch != packet.epoch) return;
            session.controls.accept(packet.sequence, packet.input, player.level().getGameTime());
        }

        public static boolean isActive(ServerPlayer player) {
            return Skills.BLACK_WING.get().isEnabled(player)
                    && player.getData(AttachmentTypes.ACTIVATED_BLACK_WING.get());
        }

        public static void forceDeactivate(ServerPlayer player) {
            if (player == null) return;
            WingFlightSupport.forceDeactivateSkill(player, Skills.BLACK_WING.get());
            WingFlightSupport.sync(player, AttachmentTypes.ACTIVATED_BLACK_WING.get(), false, LAST_BOOST_TICK);
            refreshSession(player);
        }

        public static void onLeftClickSwing(ServerPlayer player) {
            if (!isActive(player)) return;
            var session = refreshSession(player);
            if (session == null) return;
            var pattern = session.attacks.tryBegin(player.level().getGameTime(),
                    () -> WingFlightSupport.trySweepCost(player, Skills.BLACK_WING.get()));
            if (pattern == null) return;
            var packet = new BlackWingAttackPacket(player.getId(), pattern,
                    player.level().getGameTime(), attackTargets(player, pattern), session.epoch, ++session.sequence, 0f);
            session.lastAttack = packet;
            sendObservers(player, observer -> MisakaNetworkServer.send(observer, packet));
            WingFlightSupport.fanAttack(player, Skills.BLACK_WING.get());
        }

        private static List<Vec3> attackTargets(ServerPlayer player, VortexAttackPattern pattern) {
            if (pattern == VortexAttackPattern.FOURFOLD_SLAM) {
                // Freeze the four corners at cast time, each projected onto its own terrain column.
                return VortexAttackTargets.quadrilateral(player.position(),
                                Vec3.directionFromRotation(0f, player.getYRot()), 6.0, 6.0)
                        .stream().map(corner -> groundTarget(player, corner)).toList();
            }
            return List.of(aimTarget(player));
        }

        private static Vec3 groundTarget(ServerPlayer player, Vec3 corner) {
            var top = corner.add(0, 16, 0);
            var ground = player.level().clip(new ClipContext(top,
                    new Vec3(corner.x, player.level().getMinY(), corner.z),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            return ground.getType() == HitResult.Type.MISS ? corner : ground.getLocation().add(0, 0.05, 0);
        }

        private static Vec3 aimTarget(ServerPlayer player) {
            var origin = player.getEyePosition();
            var hit = player.pick(WingFlightSupport.ATTACK_RANGE, 1f, false);
            var target = hit.getLocation();
            double distance = origin.distanceToSqr(target);
            // Use the nearest entity along the aim ray, stopping at the first block.
            var rayBounds = player.getBoundingBox().expandTowards(target.subtract(origin)).inflate(1.0);
            for (var entity : player.level().getEntitiesOfClass(LivingEntity.class, rayBounds,
                    e -> e != player && e.isAlive())) {
                var intersection = entity.getBoundingBox().inflate(0.15).clip(origin, target);
                if (intersection.isPresent() && origin.distanceToSqr(intersection.get()) < distance) {
                    target = intersection.get();
                    distance = origin.distanceToSqr(target);
                }
            }
            return target;
        }

        private static void tick(ServerPlayer player) {
            WingFlightSupport.tick(player, Skills.BLACK_WING.get(),
                    AttachmentTypes.ACTIVATED_BLACK_WING.get(), LAST_BOOST_TICK);
            var session = refreshSession(player);
            long tick = player.level().getGameTime();
            if (session != null && session.lastControlTick != tick) {
                session.lastControlTick = tick;
                WingFlightSupport.applyHeldControl(player, session.controls.sample(tick, player.getYRot(), player.getXRot()), LAST_BOOST_TICK);
            }
        }
    }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onStartTracking(net.neoforged.neoforge.event.entity.player.PlayerEvent.StartTracking event) {
            if (event.getEntity() instanceof ServerPlayer observer && event.getTarget() instanceof ServerPlayer target)
                Server.startTracking(observer, target);
        }

        @SubscribeEvent
        public static void onStopTracking(net.neoforged.neoforge.event.entity.player.PlayerEvent.StopTracking event) {
            if (event.getEntity() instanceof ServerPlayer observer && event.getTarget() instanceof ServerPlayer target)
                Server.stopTracking(observer, target);
        }

        @SubscribeEvent
        public static void onServerStopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
            Server.ATTACK_SESSIONS.clear();
            Server.OBSERVERS.clear();
            Server.LAST_BOOST_TICK.clear();
        }

        @SubscribeEvent
        public static void onLogout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) Server.disconnected(player);
        }

        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post event) {
            if (event.getEntity() instanceof ServerPlayer player) Server.tick(player);
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
            return PacketTypes.BLACK_WING_TOGGLE.get();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class ControlPacket extends Packet<ServerGamePacketListenerImpl, ControlPacket> {
        public static final StreamCodec<ByteBuf, ControlPacket> CODEC = StreamCodec.of((buf, packet) -> {
            ByteBufCodecs.VAR_LONG.encode(buf, packet.epoch);
            ByteBufCodecs.VAR_LONG.encode(buf, packet.sequence);
            buf.writeByte(packet.input.buttons());
            buf.writeFloat(packet.input.yaw());
            buf.writeFloat(packet.input.pitch());
        }, buf -> new ControlPacket(ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf),
                new WingControlIntent(buf.readUnsignedByte(), buf.readFloat(), buf.readFloat())));
        final long epoch, sequence;
        final WingControlIntent input;
        public ControlPacket(long epoch, long sequence, WingControlIntent input) {
            this.epoch = epoch; this.sequence = sequence; this.input = java.util.Objects.requireNonNull(input);
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, ControlPacket> getPacketType() {
            return PacketTypes.BLACK_WING_CONTROL.get();
        }
    }
}
