package org.academy.internal.common.ability.accelerator.skills.lv5;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
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
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.client.render.vfx.PlatinumExecutionVfx;
import org.academy.internal.client.render.vfx.PlatinumExecutionVfxClient;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.accelerator.skills.lv4.StormWing;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.academy.internal.common.entitycontrol.EntityControlApi;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.world.damagesource.CTAEntityActuallyHurt;
import org.academy.internal.common.world.damagesource.CtaFriendlyFireWhitelist;
import org.academy.internal.common.world.damagesource.DamageTypes;
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
import java.util.concurrent.ConcurrentHashMap;

import static org.lwjgl.glfw.GLFW.*;

public final class PlatinumWing extends Skill {
    private static final double EXECUTION_REACH = 128.0;

    public PlatinumWing() {
        super(Builder.of(AbilityCategories.ACCELERATOR.get())
                .level(AbilityLevel.LEVEL5)
                .energyCost(100_000)
                .passive()
                .initiallyDisabled()
                .maintenanceCost(160)
                .iterationTicks(10)
                .maxStacks(NO_STACK_LIMIT)
                .dependsOn(Skills.WHITE_WING)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL5))
                .devCondition(new DevCondition.DependencyCondition("White Wing", "academy:white_wing")));
    }

    @Override
    public void initClient() {
        AdvancedWingSweepPacket.initClient();
        PlatinumExecutionVfxClient.register();
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(Client.KEY_NAME_TOGGLE, Client.CONFIG.getKeyBinding(
                Client.KEY_NAME_TOGGLE,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, GLFW_KEY_U, GLFW_RELEASE, GLFW_MOD_ALT)
        ), _ -> Client.toggle());
        ToggleStatusHud.Companion.registerStateProvider(Skills.PLATINUM_WING.get(), () -> {
            var player = Minecraft.getInstance().player;
            return player != null && player.getData(AttachmentTypes.ACTIVATED_PLATINUM_WING.get());
        });
        MisakaNetworkClient.NETWORK_MANAGER.register(Client.class);
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
                        Skills.PLATINUM_WING.get(),
                        List.of(WhiteWing.Client.SKILL_INFO),
                        R.textures.platinum_wing_icon,
                        205, 20
                )
        );
        public static final String KEY_NAME_TOGGLE = SkillNames.PLATINUM_WING + "_toggle";
        public static Config CONFIG = new Config();

        private Client() {
        }

        @SubscribeEvent
        public static void tick(ClientTickEvent.Post event) {
            var player = Minecraft.getInstance().player;
            WingFlightSupport.clientTick(
                    player != null && player.getData(AttachmentTypes.ACTIVATED_PLATINUM_WING.get()),
                    state -> MisakaNetworkClient.send(new ControlPacket(state))
            );
        }

        @SubscribePacket
        public static void handleExecutionVisual(ExecutionVisualPacket packet) {
            PlatinumExecutionVfx.enqueue(
                    packet.executionId,
                    packet.entityId,
                    packet.x,
                    packet.y,
                    packet.z,
                    packet.yRot,
                    packet.width,
                    packet.height,
                    packet.durationTicks
            );
        }

        private static void toggle() {
            if (!AbilitySystemClient.beginToggleRequest(Skills.PLATINUM_WING.get())) return;
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
        private static final Map<UUID, ExecutionTombstone> EXECUTIONS = new ConcurrentHashMap<>();
        private static final long SIGNATURE_SUPPRESSION_TICKS = 200L;
        private static volatile MinecraftServer currentServer;

        private Server() {
        }

        @SubscribePacket
        public static void handleToggle(TogglePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var skill = Skills.PLATINUM_WING.get();
            if (!skill.isEnabled(player)) {
                StormWing.Server.forceDeactivate(player);
                BlackWing.Server.forceDeactivate(player);
                WhiteWing.Server.forceDeactivate(player);
            }
            skill.toggle(player);
            WingFlightSupport.sync(player, AttachmentTypes.ACTIVATED_PLATINUM_WING.get(),
                    skill.isEnabled(player), LAST_BOOST_TICK);
        }

        @SubscribePacket
        public static void handleControl(ControlPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            if (!isActive(player)) return;
            WingFlightSupport.applyControl(player, packet.state, LAST_BOOST_TICK);
        }

        public static boolean isActive(ServerPlayer player) {
            return Skills.PLATINUM_WING.get().isEnabled(player)
                    && player.getData(AttachmentTypes.ACTIVATED_PLATINUM_WING.get());
        }

        public static void forceDeactivate(ServerPlayer player) {
            if (player == null) return;
            WingFlightSupport.forceDeactivateSkill(player, Skills.PLATINUM_WING.get());
            WingFlightSupport.sync(player, AttachmentTypes.ACTIVATED_PLATINUM_WING.get(), false, LAST_BOOST_TICK);
        }

        public static void onEntitySwing(ServerPlayer player, InteractionHand hand) {
            if (hand != InteractionHand.MAIN_HAND || !isActive(player)) return;
            if (!WingFlightSupport.trySweepCost(player, Skills.PLATINUM_WING.get())) return;
            try {
                WingFlightSupport.broadcastSweep(player, AdvancedWingSweepPacket.WingKind.PLATINUM);
                if (player.isShiftKeyDown()) executeCrosshairTarget(player);
                else WingFlightSupport.fanAttack(player, Skills.PLATINUM_WING.get());
            } catch (Throwable throwable) {
                AcademyCraft.getLogger().error(
                        "[PlatinumWing] execution attack failed for {}",
                        player.getName().getString(),
                        throwable
                );
            }
        }

        private static void executeCrosshairTarget(ServerPlayer player) {
            var target = pickTarget(player);
            if (!(target instanceof LivingEntity living)
                    || target instanceof Player
                    || CtaFriendlyFireWhitelist.shouldProtect(player, living)) {
                return;
            }
            var level = (ServerLevel) player.level();
            var skill = Skills.PLATINUM_WING.get();
            if (skill.hasProficiencyMilestone(player, 3)
                    && org.academy.internal.common.ability.aeromanip.AeromanipTargeting.isBoss(target)) {
                var trueMaxHealth = EntityControlApi.getTrueMaxHealth(living);
                if (!Float.isFinite(trueMaxHealth) || trueMaxHealth <= 0.0f) {
                    trueMaxHealth = living.getMaxHealth();
                }
                var damage = trueMaxHealth * 0.15f
                        * AbilitySystemServer.getSystem(player)
                        .getPlayerDamageMultiplier(player.getUUID());
                living.hurtServer(
                        level,
                        SkillDamageSource.of(player, skill,
                                org.academy.internal.common.world.damagesource.DamageTypes.VEC),
                        damage
                );
                level.playSound(null, target, SoundEvents.PLAYER_ATTACK_CRIT,
                        SoundSource.PLAYERS, 1.0f, 0.7f);
                return;
            }
            var typeId = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());
            var executionId = UUID.randomUUID();
            var controllerBacked = PlatinumExecutionCleanup.register(
                    executionId, target, level.getGameTime()
            );
            var tombstone = new ExecutionTombstone(
                    target.getUUID(),
                    typeId,
                    level.dimension().identifier(),
                    target.position(),
                    level.getGameTime(),
                    controllerBacked,
                    target.getClass().getName(),
                    target.getName().getString(),
                    target.getBbWidth(),
                    target.getBbHeight()
            );
            EXECUTIONS.put(target.getUUID(), tombstone);
            currentServer = level.getServer();
            broadcastExecutionVisual(player, executionId, target);

            var trueMaxHealth = EntityControlApi.getTrueMaxHealth(living);
            if (!Float.isFinite(trueMaxHealth) || trueMaxHealth <= 0.0f) {
                trueMaxHealth = living.getMaxHealth();
            }
            var damage = (trueMaxHealth * 2.0f + 1000.0f)
                    * AbilitySystemServer.getSystem(player).getPlayerDamageMultiplier(player.getUUID());
            var source = SkillDamageSource.of(
                    player,
                    Skills.PLATINUM_WING.get(),
                    DamageTypes.CTA
            );
            new CTAEntityActuallyHurt(living).actuallyHurt(source, damage, true);
            level.playSound(null, target, SoundEvents.PLAYER_ATTACK_CRIT,
                    SoundSource.PLAYERS, 1.0f, 0.7f);
        }

        private static void broadcastExecutionVisual(ServerPlayer player, UUID executionId, Entity target) {
            var packet = new ExecutionVisualPacket(
                    executionId,
                    target.getId(),
                    target.getX(),
                    target.getY(),
                    target.getZ(),
                    target.getYRot(),
                    target.getBbWidth(),
                    target.getBbHeight(),
                    PlatinumExecutionVfx.DURATION_TICKS
            );
            for (var other : player.level().players()) {
                if (other.distanceToSqr(player) <= 256.0 * 256.0) {
                    MisakaNetworkServer.send(other, packet);
                }
            }
        }

        private static Entity pickTarget(ServerPlayer player) {
            var start = player.getEyePosition();
            var end = start.add(player.getLookAngle().scale(EXECUTION_REACH));
            var search = player.getBoundingBox().expandTowards(player.getLookAngle().scale(EXECUTION_REACH)).inflate(1.0);
            var hit = ProjectileUtil.getEntityHitResult(
                    player.level(), player, start, end, search,
                    entity -> entity != player && entity.isAlive() && entity.isPickable(),
                    0.3f
            );
            return hit == null ? null : hit.getEntity();
        }

        private static void tick(ServerPlayer player) {
            WingFlightSupport.tick(player, Skills.PLATINUM_WING.get(),
                    AttachmentTypes.ACTIVATED_PLATINUM_WING.get(), LAST_BOOST_TICK);
        }

        private static void maintainExecutions() {
            var server = currentServer;
            if (server == null || EXECUTIONS.isEmpty()) return;
            for (var level : server.getAllLevels()) {
                for (var tombstone : EXECUTIONS.values()) {
                    var exact = level.getEntity(tombstone.entityId);
                    if (exact != null && !(exact instanceof Player)) exact.discard();
                }
            }
        }

        private static void onServerTick() {
            var server = currentServer;
            if (server == null) return;
            PlatinumExecutionCleanup.tick(server.overworld().getGameTime());
            if (server.getTickCount() % 20 == 0) maintainExecutions();
        }

        private static void clearExecutionState() {
            PlatinumExecutionCleanup.clear();
            EXECUTIONS.clear();
            LAST_BOOST_TICK.clear();
            currentServer = null;
        }

        private static void suppressRespawn(EntityJoinLevelEvent event) {
            if (event.getLevel().isClientSide() || event.getEntity() instanceof Player) return;
            var entity = event.getEntity();
            var exact = EXECUTIONS.get(entity.getUUID());
            if (exact != null) {
                entity.discard();
                event.setCanceled(true);
                return;
            }
            var level = event.getLevel();
            var typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
            for (var tombstone : EXECUTIONS.values()) {
                if (!tombstone.controllerBacked
                        && level.getGameTime() - tombstone.gameTime > SIGNATURE_SUPPRESSION_TICKS) continue;
                if (!tombstone.typeId.equals(typeId)) continue;
                if (!tombstone.dimension.equals(level.dimension().identifier())) continue;
                if (tombstone.controllerBacked && !tombstone.matchesControllerRespawn(entity)) continue;
                if (tombstone.position.distanceToSqr(entity.position()) > 4.0) continue;
                entity.discard();
                event.setCanceled(true);
                return;
            }
        }

        private record ExecutionTombstone(UUID entityId, Identifier typeId, Identifier dimension,
                                          Vec3 position, long gameTime,
                                          boolean controllerBacked, String className, String displayName,
                                          float width, float height) {
            private boolean matchesControllerRespawn(Entity entity) {
                if (!className.equals(entity.getClass().getName())) return false;
                if (!displayName.equals(entity.getName().getString())) return false;
                return Math.abs(width - entity.getBbWidth()) <= 0.25f
                        && Math.abs(height - entity.getBbHeight()) <= 0.5f;
            }
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
        public static void onEntityJoin(EntityJoinLevelEvent event) {
            Server.suppressRespawn(event);
        }

        @SubscribeEvent
        public static void onServerTick(ServerTickEvent.Post event) {
            Server.onServerTick();
        }

        @SubscribeEvent
        public static void onServerStopping(ServerStoppingEvent event) {
            Server.clearExecutionState();
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
            return PacketTypes.PLATINUM_WING_TOGGLE.get();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class ControlPacket extends Packet<ServerGamePacketListenerImpl, ControlPacket> {
        private static final StreamCodec<ByteBuf, StormWing.State> STATE_CODEC =
                ByteBufCodecs.idMapper(index -> StormWing.State.values()[index], Enum::ordinal);
        public static final StreamCodec<ByteBuf, ControlPacket> CODEC =
                STATE_CODEC.map(ControlPacket::new, packet -> packet.state);
        private final StormWing.State state;

        public ControlPacket(StormWing.State state) {
            this.state = state;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, ControlPacket> getPacketType() {
            return PacketTypes.PLATINUM_WING_CONTROL.get();
        }
    }

    @PacketTarget(ThreadType.CLIENT)
    public static final class ExecutionVisualPacket extends Packet<ClientPacketListener, ExecutionVisualPacket> {
        public static final StreamCodec<ByteBuf, ExecutionVisualPacket> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    buf.writeLong(packet.executionId.getMostSignificantBits());
                    buf.writeLong(packet.executionId.getLeastSignificantBits());
                    ByteBufCodecs.VAR_INT.encode(buf, packet.entityId);
                    buf.writeDouble(packet.x);
                    buf.writeDouble(packet.y);
                    buf.writeDouble(packet.z);
                    ByteBufCodecs.FLOAT.encode(buf, packet.yRot);
                    ByteBufCodecs.FLOAT.encode(buf, packet.width);
                    ByteBufCodecs.FLOAT.encode(buf, packet.height);
                    ByteBufCodecs.VAR_INT.encode(buf, packet.durationTicks);
                },
                buf -> new ExecutionVisualPacket(
                        new UUID(buf.readLong(), buf.readLong()),
                        ByteBufCodecs.VAR_INT.decode(buf),
                        buf.readDouble(),
                        buf.readDouble(),
                        buf.readDouble(),
                        ByteBufCodecs.FLOAT.decode(buf),
                        ByteBufCodecs.FLOAT.decode(buf),
                        ByteBufCodecs.FLOAT.decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf)
                )
        );
        private final UUID executionId;
        private final int entityId;
        private final double x;
        private final double y;
        private final double z;
        private final float yRot;
        private final float width;
        private final float height;
        private final int durationTicks;

        public ExecutionVisualPacket(UUID executionId, int entityId, double x, double y, double z,
                                     float yRot, float width, float height, int durationTicks) {
            this.executionId = executionId;
            this.entityId = entityId;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yRot = yRot;
            this.width = width;
            this.height = height;
            this.durationTicks = durationTicks;
        }

        @Override
        public PacketType<ClientPacketListener, ExecutionVisualPacket> getPacketType() {
            return PacketTypes.PLATINUM_WING_EXECUTION_VISUAL.get();
        }
    }
}
