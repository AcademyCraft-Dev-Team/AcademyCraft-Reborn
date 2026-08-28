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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.render.LevelRenderEvent;
import org.academy.api.client.resources.R;
import org.academy.api.client.util.ClientUtil;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.DevCondition;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.client.render.vfx.TeleportCursorRenderer;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.ProficiencySkillSettings;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.teleport.TeleportSafety;
import org.academy.internal.common.ability.teleport.TeleportSync;
import org.academy.internal.common.ability.teleport.TeleportTargeting;
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

import java.util.*;

import static org.lwjgl.glfw.GLFW.*;

public final class Flashing extends Skill {
    static final double DASH_DISTANCE = 8.0;
    static final int REPEAT_TICKS = 6;
    static final int DASH_INVULNERABILITY_TICKS = 4;
    static final int AUTO_ESCAPE_COOLDOWN_TICKS = 200;
    private static final double[] AUTO_ESCAPE_ANGLE_OFFSETS = {
            0.0,
            Math.PI / 4.0, -Math.PI / 4.0,
            Math.PI / 2.0, -Math.PI / 2.0,
            Math.PI * 3.0 / 4.0, -Math.PI * 3.0 / 4.0,
            Math.PI
    };

    public Flashing() {
        super(Builder
                .of(AbilityCategories.TELEPORT.get())
                .level(AbilityLevel.LEVEL5)
                .energyCost(100_000)
                .passive()
                .initiallyDisabled()
                .maintenanceCost(50)
                .cpCost(5)
                .iterationTicks(5)
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

    static Vec3 findDashDestination(
            LivingEntity player,
            Vec3 eyePosition,
            Vec3 direction
    ) {
        var targetCenter = TeleportTargeting.findSelfTeleportCenter(
                player,
                eyePosition,
                direction,
                DASH_DISTANCE
        );
        if (targetCenter == null) return null;
        var dimensions = player.getDimensions(Pose.STANDING);
        return targetCenter.add(0.0, -dimensions.height() / 2.0, 0.0);
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
        private static final int[] HOLD_TICKS = new int[Direction.values().length];
        public static Config CONFIG = new Config();

        private Client() {
        }

        private static void toggle() {
            if (ClientUtil.hasScreen() || Minecraft.getInstance().player == null) return;
            if (!AbilitySystemClient.beginToggleRequest(Skills.FLASHING.get())) return;
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

            for (var direction : Direction.values()) {
                update(direction, isDirectionKeyDown(direction));
            }
        }

        @SubscribeEvent
        public static void onLevelRender(LevelRenderEvent event) {
            var minecraft = Minecraft.getInstance();
            var player = minecraft.player;
            if (player == null || minecraft.gui.screen() != null
                    || !AbilitySystemClient.canUseSkillSilently(Skills.FLASHING.get())) return;

            var partialTick = event.getPartialTick();
            var look = player.getViewVector(partialTick);
            var eyePosition = player.getEyePosition(partialTick);
            for (var direction : Direction.values()) {
                if (!isDirectionKeyDown(direction)) continue;
                var dashDirection = Server.directionFromLook(look, player.getYRot(), direction);
                if (dashDirection.lengthSqr() < 1.0e-6) continue;
                var destination = findDashDestination(player, eyePosition, dashDirection);
                if (destination != null) TeleportCursorRenderer.render(event, destination, true);
            }
        }

        private static void update(Direction direction, boolean down) {
            var index = direction.ordinal();
            if (!down) {
                HOLD_TICKS[index] = 0;
                return;
            }
            var previous = HOLD_TICKS[index]++;
            var repeatTicks = AbilitySystemClient.getSkillProficiencyMilestone(Skills.FLASHING.get()) >= 2
                    ? 4 : REPEAT_TICKS;
            if (previous == 0 || HOLD_TICKS[index] % repeatTicks == 0) {
                MisakaNetworkClient.send(new DashPacket(direction));
            }
        }

        private static void resetHolds() {
            Arrays.fill(HOLD_TICKS, 0);
        }

        private static boolean isDirectionKeyDown(Direction direction) {
            var key = switch (direction) {
                case FORWARD -> GLFW_KEY_W;
                case BACK -> GLFW_KEY_S;
                case LEFT -> GLFW_KEY_A;
                case RIGHT -> GLFW_KEY_D;
            };
            return InputSystem.isDown(InputSystem.InputType.KEYBOARD, key);
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
        private static final Map<UUID, Long> LAST_AUTO_ESCAPE = new HashMap<>();
        private static final Map<UUID, ArrayDeque<Direction>> DASH_QUEUES = new WeakHashMap<>();
        private static final Map<UUID, DashInvulnerabilityState> DASH_INVULNERABILITY = new HashMap<>();

        private Server() {
        }

        @SubscribePacket
        public static void handleToggle(TogglePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var skill = Skills.FLASHING.get();
            skill.toggle(player);
            if (!skill.isEnabled(player)) {
                LAST_DASH.remove(player.getUUID());
                DASH_QUEUES.remove(player.getUUID());
                cancelPendingDashInvulnerability(player.getUUID(), player.level().getGameTime());
            }
        }

        @SubscribePacket
        public static void handleDash(DashPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var skill = Skills.FLASHING.get();
            if (!skill.isEnabled(player)) return;
            if (skill.hasProficiencyMilestone(player, 3)) {
                var queue = DASH_QUEUES.computeIfAbsent(player.getUUID(), ignored -> new ArrayDeque<>());
                if (queue.size() < 3) {
                    beginDashInvulnerability(player.getUUID());
                    queue.addLast(packet.direction);
                }
                return;
            }
            performDash(player, packet.direction, false);
        }

        private static void performDash(
                ServerPlayer player,
                Direction requestedDirection,
                boolean protectionStarted
        ) {
            var skill = Skills.FLASHING.get();
            var playerId = player.getUUID();
            var now = player.level().getGameTime();
            var last = LAST_DASH.get(playerId);
            if (last != null && now - last < 2) {
                if (protectionStarted) cancelDashInvulnerability(playerId, now);
                return;
            }

            if (!protectionStarted) beginDashInvulnerability(playerId);
            var completed = new boolean[1];
            try {
                var direction = directionFromLook(player.getLookAngle(), player.getYRot(), requestedDirection);
                if (direction.lengthSqr() < 1.0e-6) return;
                var destination = findDashDestination(player, player.getEyePosition(), direction);
                if (destination == null) return;

                skill.executeActive(player, (context, actualCost) -> {
                    if (!TeleportSync.teleportInstantly(player, destination)) return;
                    completed[0] = true;
                    completeDashInvulnerability(playerId, player.level().getGameTime());
                    player.resetFallDistance();
                    player.setDeltaMovement(0, 0.15, 0);
                    player.connection.send(new ClientboundSetEntityMotionPacket(player));
                    player.level().playSound(null, player.blockPosition(), SoundEvents.FLASHING.get(),
                            SoundSource.PLAYERS, 1.0f, 1.0f);
                });
                if (completed[0]) LAST_DASH.put(playerId, now);
            } finally {
                if (!completed[0]) cancelDashInvulnerability(playerId, now);
            }
        }

        public static boolean isDashInvulnerable(ServerPlayer player) {
            return player != null
                    && isDashInvulnerable(player.getUUID(), player.level().getGameTime());
        }

        public static boolean blocksNegativeHealthWrite(ServerPlayer player, float requestedHealth) {
            return player != null && blocksNegativeHealthWrite(
                    player.getUUID(),
                    player.level().getGameTime(),
                    player.getHealth(),
                    requestedHealth
            );
        }

        static boolean blocksNegativeHealthWrite(
                UUID playerId,
                long now,
                float currentHealth,
                float requestedHealth
        ) {
            return isDashInvulnerable(playerId, now) && requestedHealth < currentHealth;
        }

        static boolean tryAutoEscape(ServerPlayer player, DamageSource source) {
            if (player == null || source == null || !player.isAlive() || player.hasDisconnected()) return false;
            var attacker = source.getEntity();
            var directAttacker = source.getDirectEntity();
            if ((attacker == null && directAttacker == null)
                    || attacker == player || directAttacker == player) return false;

            var skill = Skills.FLASHING.get();
            if (!skill.isEnabled(player)
                    || !ProficiencySkillSettings.isEnabled(
                    player,
                    ProficiencySkillSettings.FLASHING_AUTO_ESCAPE
            )) return false;

            var playerId = player.getUUID();
            var now = player.level().getGameTime();
            if (!isAutoEscapeReady(playerId, now)) return false;
            var sourcePosition = attacker != null ? attacker.position() : source.getSourcePosition();
            if (sourcePosition == null && directAttacker != null) {
                sourcePosition = directAttacker.position();
            }
            var destination = findAutoEscapeDestination(player, sourcePosition);
            if (destination == null) return false;

            beginDashInvulnerability(playerId);
            var completed = new boolean[1];
            try {
                skill.executeActive(player, (context, actualCost) -> {
                    if (!TeleportSync.teleportInstantly(player, destination)) return;
                    completed[0] = true;
                    var completionTick = player.level().getGameTime();
                    completeDashInvulnerability(playerId, completionTick);
                    markAutoEscapeTriggered(playerId, completionTick);
                    LAST_DASH.put(playerId, completionTick);
                    player.resetFallDistance();
                    player.setDeltaMovement(0, 0.15, 0);
                    player.connection.send(new ClientboundSetEntityMotionPacket(player));
                    player.level().playSound(null, player.blockPosition(), SoundEvents.FLASHING.get(),
                            SoundSource.PLAYERS, 1.0f, 1.0f);
                });
                return completed[0];
            } finally {
                if (!completed[0]) cancelDashInvulnerability(playerId, now);
            }
        }

        private static Vec3 findAutoEscapeDestination(ServerPlayer player, Vec3 sourcePosition) {
            var origin = player.position();
            var fallbackAngle = player.getRandom().nextDouble() * Mth.TWO_PI;
            var away = autoEscapeDirection(origin, sourcePosition, fallbackAngle);
            var baseAngle = Math.atan2(away.z, away.x);
            var fullDistance = DASH_DISTANCE;
            var distances = new double[]{fullDistance, fullDistance * 0.75, 4.0};
            for (var distance : distances) {
                for (var angleOffset : AUTO_ESCAPE_ANGLE_OFFSETS) {
                    var angle = baseAngle + angleOffset;
                    var desired = origin.add(
                            Math.cos(angle) * distance,
                            0.0,
                            Math.sin(angle) * distance
                    );
                    var safe = TeleportSafety.findSafe(player, desired);
                    if (safe != null && safe.distanceToSqr(origin) >= 4.0) return safe;
                }
            }
            return null;
        }

        static Vec3 autoEscapeDirection(Vec3 origin, Vec3 sourcePosition, double fallbackAngle) {
            if (origin != null && sourcePosition != null) {
                var away = origin.subtract(sourcePosition).multiply(1.0, 0.0, 1.0);
                if (away.lengthSqr() >= 1.0e-6) return away.normalize();
            }
            return new Vec3(Math.cos(fallbackAngle), 0.0, Math.sin(fallbackAngle));
        }

        static boolean isAutoEscapeReady(UUID playerId, long now) {
            var lastTrigger = LAST_AUTO_ESCAPE.get(playerId);
            return lastTrigger == null || now - lastTrigger >= AUTO_ESCAPE_COOLDOWN_TICKS;
        }

        static void markAutoEscapeTriggered(UUID playerId, long now) {
            LAST_AUTO_ESCAPE.put(playerId, now);
        }

        static void clearAutoEscapeCooldown(UUID playerId) {
            LAST_AUTO_ESCAPE.remove(playerId);
        }

        static void beginDashInvulnerability(UUID playerId) {
            DASH_INVULNERABILITY.computeIfAbsent(
                    playerId,
                    ignored -> new DashInvulnerabilityState()
            ).pendingDashes++;
        }

        static void completeDashInvulnerability(UUID playerId, long completionTick) {
            var state = DASH_INVULNERABILITY.computeIfAbsent(
                    playerId,
                    ignored -> new DashInvulnerabilityState()
            );
            if (state.pendingDashes > 0) state.pendingDashes--;
            state.graceEndTick = Math.max(
                    state.graceEndTick,
                    completionTick + DASH_INVULNERABILITY_TICKS
            );
        }

        static boolean isDashInvulnerable(UUID playerId, long now) {
            var state = DASH_INVULNERABILITY.get(playerId);
            return state != null && (state.pendingDashes > 0 || now < state.graceEndTick);
        }

        static void cancelDashInvulnerability(UUID playerId, long now) {
            var state = DASH_INVULNERABILITY.get(playerId);
            if (state == null) return;
            if (state.pendingDashes > 0) state.pendingDashes--;
            removeExpiredDashInvulnerability(playerId, state, now);
        }

        private static void cancelPendingDashInvulnerability(UUID playerId, long now) {
            var state = DASH_INVULNERABILITY.get(playerId);
            if (state == null) return;
            state.pendingDashes = 0;
            removeExpiredDashInvulnerability(playerId, state, now);
        }

        static void clearDashInvulnerability(UUID playerId) {
            DASH_INVULNERABILITY.remove(playerId);
        }

        private static void clearExpiredDashInvulnerability(UUID playerId, long now) {
            var state = DASH_INVULNERABILITY.get(playerId);
            if (state != null) removeExpiredDashInvulnerability(playerId, state, now);
        }

        private static void removeExpiredDashInvulnerability(
                UUID playerId,
                DashInvulnerabilityState state,
                long now
        ) {
            if (state.pendingDashes == 0 && now >= state.graceEndTick) {
                DASH_INVULNERABILITY.remove(playerId);
            }
        }

        private static void processQueue(ServerPlayer player) {
            var queue = DASH_QUEUES.get(player.getUUID());
            if (queue == null || queue.isEmpty()) return;
            performDash(player, queue.removeFirst(), true);
            if (queue.isEmpty()) DASH_QUEUES.remove(player.getUUID());
        }

        private static final class DashInvulnerabilityState {
            private int pendingDashes;
            private long graceEndTick = Long.MIN_VALUE;
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
            Server.clearExpiredDashInvulnerability(player.getUUID(), player.level().getGameTime());
            var skill = Skills.FLASHING.get();
            if (!skill.isEnabled(player)) return;
            Server.processQueue(player);
            var system = AbilitySystemServer.getSystem(player);
            if (!player.isAlive() || player.hasDisconnected()
                    || !system.ensurePermanentOccupation(
                    player.getUUID(),
                    skill.getMaintenanceCost(player),
                    skill
            )) {
                if (skill.isEnabled(player)) skill.toggle(player);
                Server.LAST_DASH.remove(player.getUUID());
                Server.DASH_QUEUES.remove(player.getUUID());
                Server.cancelPendingDashInvulnerability(
                        player.getUUID(),
                        player.level().getGameTime()
                );
            }
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
        public static void onIncomingDamage(LivingIncomingDamageEvent event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            if (Server.isDashInvulnerable(player)) {
                event.setCanceled(true);
                return;
            }
            if (!event.isCanceled() && Server.tryAutoEscape(player, event.getSource())) {
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
            var playerId = event.getEntity().getUUID();
            Server.clearDashInvulnerability(playerId);
            Server.clearAutoEscapeCooldown(playerId);
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
