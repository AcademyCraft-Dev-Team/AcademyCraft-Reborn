package org.academy.internal.common.ability.accelerator.skills.lv3;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.resources.R;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.DevCondition;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.common.structure.LegacyMotionUnits;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.accelerator.movement.VectorStructureService;
import org.academy.internal.common.ability.accelerator.skills.lv2.KineticEnergyApplied;
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
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Captures terrain as a hovering structure and throws it along the caster's view vector. */
public final class KineticThrow extends Skill {
    public static final double TARGET_RANGE = 24.0;
    public static final int TICKS_PER_CHARGE_TIER = 20;
    public static final int HOVER_TICKS = 10 * 20;
    public static final double PLAYER_CLEARANCE = 1.0;
    public static final int MAXIMUM_FLIGHT_TICKS = 30 * 20;
    public static final double BASE_THROW_SPEED = 2.5;
    public static final float DAMAGE_PER_BLOCK = 5.0f;

    private static final int[] HORIZONTAL_RADII = {5, 7, 9};
    private static final int[] VERTICAL_RADII = {2, 4, 6};
    private static final int[] CYLINDER_RADII = {5, 7, 9};
    private static final int[] CYLINDER_HEIGHTS = {5, 7, 9};
    private static final int[] LIFT_HEIGHTS = {4, 5, 7};
    private static final int[] EXPLOSION_RADII = {5, 7, 9};

    public KineticThrow() {
        super(Builder
                .of(AbilityCategories.ACCELERATOR.get())
                .damage()
                .level(AbilityLevel.LEVEL3)
                .energyCost(20_000)
                .cpCost(30)
                .iterationTicks(5)
                .maxStacks(3)
                .dependsOn(Skills.KINETIC_ENERGY_APPLIED)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL3))
                .devCondition(new DevCondition.DependencyCondition(
                        "Kinetic Energy Applied", "academy:kinetic_energy_applied"))
        );
    }

    public static int chargeTier(long chargeTicks) {
        return Mth.clamp((int) (Math.max(0L, chargeTicks) / TICKS_PER_CHARGE_TIER), 0, 2);
    }

    public static int horizontalRadius(int tier) {
        return HORIZONTAL_RADII[Mth.clamp(tier, 0, 2)];
    }

    public static int verticalRadius(int tier) {
        return VERTICAL_RADII[Mth.clamp(tier, 0, 2)];
    }

    public static int cylinderRadius(int tier) {
        return CYLINDER_RADII[Mth.clamp(tier, 0, 2)];
    }

    public static int cylinderHeight(int tier) {
        return CYLINDER_HEIGHTS[Mth.clamp(tier, 0, 2)];
    }

    public static int liftHeight(int tier) {
        return LIFT_HEIGHTS[Mth.clamp(tier, 0, 2)];
    }

    public static int explosionRadius(int tier) {
        return EXPLOSION_RADII[Mth.clamp(tier, 0, 2)];
    }

    public static float structureDamage(int blockCount) {
        return Math.max(0, blockCount) * DAMAGE_PER_BLOCK;
    }

    public static Vec3 throwDirection(Vec3 viewDirection) {
        return viewDirection != null && viewDirection.lengthSqr() > 1.0e-8
                ? viewDirection.normalize()
                : Vec3.ZERO;
    }

    /** Shared per-second launch calibration; the tier-3 milestone adds its documented 20%. */
    public static double throwSpeed(boolean speedMilestone) {
        return LegacyMotionUnits.velocityPerSecond(BASE_THROW_SPEED) * (speedMilestone ? 1.2 : 1.0);
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(
                Client.KEY_NAME_CHARGE,
                Client.CONFIG.getKeyBinding(
                        Client.KEY_NAME_CHARGE,
                        InputSystem.combo(InputSystem.InputType.KEYBOARD,
                                InputConstants.KEY_H, InputConstants.PRESS, 0)),
                _ -> Client.pressHeld()
        );
        InputSystem.addKeyBinding(
                Client.KEY_NAME_RELEASE,
                Client.CONFIG.getKeyBinding(
                        Client.KEY_NAME_RELEASE,
                        InputSystem.combo(InputSystem.InputType.KEYBOARD,
                                InputConstants.KEY_H, InputConstants.RELEASE, 0)),
                _ -> Client.releaseCharge()
        );
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.ACCELERATOR.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.KINETIC_THROW.get(),
                        List.of(KineticEnergyApplied.Client.SKILL_INFO),
                        R.textures.kinetic_throw_icon,
                        150,
                        100
                )
        );
        public static final String KEY_NAME_CHARGE = SkillNames.KINETIC_THROW + "_charge";
        public static final String KEY_NAME_RELEASE = SkillNames.KINETIC_THROW + "_release";
        public static Config CONFIG = new Config();
        private static boolean pressed;

        private Client() {
        }

        private static void pressHeld() {
            var minecraft = Minecraft.getInstance();
            if (pressed || minecraft.gui.screen() != null
                    || !AbilitySystemClient.canUseSkill(Skills.KINETIC_THROW.get())) return;
            pressed = true;
            MisakaNetworkClient.send(PressPacket.INSTANCE);
        }

        private static void releaseCharge() {
            if (!pressed) return;
            pressed = false;
            MisakaNetworkClient.send(ReleasePacket.INSTANCE);
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
        private static final Map<UUID, ChargeState> CHARGES = new HashMap<>();

        private Server() {
        }

        /** Public stage observation; the shared service owns all capture, lift and flight state. */
        public static Optional<VectorStructureService.Diagnostic> diagnostic(ServerPlayer player) {
            requireThread(player);
            return VectorStructureService.diagnostic(player);
        }

        @SubscribePacket
        public static void handleStart(StartPacket packet) {
            startCharge(packet.getPacketListener().getPlayer());
        }

        @SubscribePacket
        public static void handlePress(PressPacket packet) {
            pressHeld(packet.getPacketListener().getPlayer());
        }

        /**
         * Unified H-press entry: the server owns the whole decision. A looked-at physicalized
         * structure is thrown directly (any owner, no mass gate); no hit starts the existing
         * charge flow. {@link #throwHeld} remains the programmatic launch entry.
         */
        public static boolean pressHeld(ServerPlayer player) {
            requireThread(player);
            if (!Skills.KINETIC_THROW.get().isEnabled(player)) return false;
            var outcome = VectorStructureService.press(player);
            return switch (outcome.status()) {
                case CHARGING -> startCharge(player);
                case THROWN -> true;
                case REJECTED -> {
                    AcademyCraft.LOGGER.info("Kinetic Throw H press was rejected for {}: {}",
                            player.getUUID(), outcome.detail());
                    player.sendOverlayMessage(Component.translatable(
                            "message.academy.kinetic_throw.throw_rejected"));
                    yield false;
                }
            };
        }

        /** Same server-owned entry point used by the skill packet; it grants no extra capability. */
        public static boolean startCharge(ServerPlayer player) {
            requireThread(player);
            if (!Skills.KINETIC_THROW.get().isEnabled(player)
                    || !(player.level() instanceof ServerLevel level)) return false;
            var hit = lookedAtBlock(player);
            if (hit == null) {
                player.sendOverlayMessage(Component.translatable(
                        "message.academy.kinetic_throw.block_target_required"));
                return false;
            }
            CHARGES.put(player.getUUID(), new ChargeState(
                    level.dimension(), hit.getBlockPos().immutable(), level.getGameTime()));
            return true;
        }

        @SubscribePacket
        public static void handleRelease(ReleasePacket packet) {
            releaseCharge(packet.getPacketListener().getPlayer());
        }

        /** Same server-owned entry point used by the skill packet. */
        public static boolean releaseCharge(ServerPlayer player) {
            requireThread(player);
            var charge = CHARGES.remove(player.getUUID());
            if (charge == null || !charge.dimension.equals(player.level().dimension())
                    || !(player.level() instanceof ServerLevel level)
                    || !Skills.KINETIC_THROW.get().isEnabled(player)) return false;
            var chargeTicks = Math.max(0L, level.getGameTime() - charge.startTick);
            if (Skills.KINETIC_THROW.get().hasProficiencyMilestone(player, 2)) {
                chargeTicks = Math.round(chargeTicks / 0.8);
            }
            var plan = VectorStructureService.planCapture(player, charge.seed, chargeTier(chargeTicks));
            if (plan.isPresent()) {
                Skills.KINETIC_THROW.get().executeActive(player,
                        (_, _) -> VectorStructureService.beginCapture(player, plan.get()));
            }
            return true;
        }

        @SubscribePacket
        public static void handleThrow(ThrowPacket packet) {
            throwHeld(packet.getPacketListener().getPlayer());
        }

        /** Same server-owned entry point used by the skill packet. */
        public static boolean throwHeld(ServerPlayer player) {
            requireThread(player);
            return VectorStructureService.launch(player);
        }

        /** Test/program entry: releases the lift or flight session and keeps the structure active. */
        public static void stopHeld(ServerPlayer player) {
            requireThread(player);
            VectorStructureService.terminate(player);
        }

        private static void tick(ServerPlayer player) {
            tickCharge(player);
            VectorStructureService.tick(player);
        }

        private static void tickCharge(ServerPlayer player) {
            var charge = CHARGES.get(player.getUUID());
            if (charge == null) return;
            if (!charge.dimension.equals(player.level().dimension())
                    || !player.isAlive()
                    || player.hasDisconnected()
                    || !Skills.KINETIC_THROW.get().isEnabled(player)) {
                CHARGES.remove(player.getUUID());
            } else {
                Skills.KINETIC_THROW.get().reportActivity(player, true);
            }
        }

        private static BlockHitResult lookedAtBlock(ServerPlayer player) {
            var range = Skills.KINETIC_THROW.get().scaledRange(player, TARGET_RANGE);
            var start = player.getEyePosition();
            var end = start.add(player.getLookAngle().scale(range));
            var hit = player.level().clip(new ClipContext(
                    start,
                    end,
                    ClipContext.Block.OUTLINE,
                    ClipContext.Fluid.NONE,
                    player
            ));
            return hit.getType() == HitResult.Type.BLOCK
                    && start.distanceToSqr(hit.getLocation()) <= range * range
                    ? hit
                    : null;
        }

        private static void clear(ServerPlayer player) {
            CHARGES.remove(player.getUUID());
            VectorStructureService.clear(player);
        }

        private static void requireThread(ServerPlayer player) {
            Objects.requireNonNull(player, "player");
            if (!player.level().getServer().isSameThread()) {
                throw new IllegalStateException("Kinetic Throw requires the owning server thread");
            }
        }

        private record ChargeState(
                ResourceKey<Level> dimension,
                BlockPos seed,
                long startTick
        ) {
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
        public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) Server.clear(player);
        }

        @SubscribeEvent
        public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) Server.clear(player);
        }

        @SubscribeEvent
        public static void onLivingDeath(LivingDeathEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) Server.clear(player);
        }

        @SubscribeEvent
        public static void onServerStopped(ServerStoppedEvent event) {
            VectorStructureService.clearAll();
            Server.CHARGES.clear();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class StartPacket extends Packet<ServerGamePacketListenerImpl, StartPacket> {
        public static final StartPacket INSTANCE = new StartPacket();
        public static final StreamCodec<ByteBuf, StartPacket> CODEC = StreamCodec.unit(INSTANCE);

        private StartPacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, StartPacket> getPacketType() {
            return PacketTypes.KINETIC_THROW_START.get();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class PressPacket extends Packet<ServerGamePacketListenerImpl, PressPacket> {
        public static final PressPacket INSTANCE = new PressPacket();
        public static final StreamCodec<ByteBuf, PressPacket> CODEC = StreamCodec.unit(INSTANCE);

        private PressPacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, PressPacket> getPacketType() {
            return PacketTypes.KINETIC_THROW_PRESS.get();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class ReleasePacket extends Packet<ServerGamePacketListenerImpl, ReleasePacket> {
        public static final ReleasePacket INSTANCE = new ReleasePacket();
        public static final StreamCodec<ByteBuf, ReleasePacket> CODEC = StreamCodec.unit(INSTANCE);

        private ReleasePacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, ReleasePacket> getPacketType() {
            return PacketTypes.KINETIC_THROW_RELEASE.get();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class ThrowPacket extends Packet<ServerGamePacketListenerImpl, ThrowPacket> {
        public static final ThrowPacket INSTANCE = new ThrowPacket();
        public static final StreamCodec<ByteBuf, ThrowPacket> CODEC = StreamCodec.unit(INSTANCE);

        private ThrowPacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, ThrowPacket> getPacketType() {
            return PacketTypes.KINETIC_THROW_THROW.get();
        }
    }
}
