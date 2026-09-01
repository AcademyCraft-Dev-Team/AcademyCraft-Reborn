package org.academy.internal.common.ability.electromaster.skills.lv3;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
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
import org.academy.api.client.util.ClientUtil;
import org.academy.api.common.ability.*;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.ability.ServerContext;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.world.damagesource.PvpSetting;
import org.academy.internal.common.skilldata.SkillData;
import org.academy.internal.common.util.EnergyChargeHelper;
import org.jspecify.annotations.Nullable;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.*;

public final class CurrentRecharge extends Skill {
    static final double CHARGE_REACH = 5.0;
    static final float CP_COST_PER_CHARGE_TICK = 30.0f;
    static final int CP_CHARGE_INTERVAL_TICKS = 20;
    private static final String LEGACY_PULSE_CHARGE = "academy:pulse_charge";

    public CurrentRecharge() {
        super(Builder
                .of(AbilityCategories.ELECTROMASTER.get())
                .level(AbilityLevel.LEVEL3)
                .energyCost(30_000)
                .iterationTicks(10)
                .maxStacks(Skill.NO_STACK_LIMIT)
                .dependsOn(Skills.MAGNET_MANIPULATION)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL3))
                .devCondition(new DevCondition.DependencyCondition(
                        "Magnet Manipulation", "academy:magnet_manipulation"))
        );
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        if (!Client.CONFIG.containsKeyBinding(Client.KEY_NAME_START)
                && Client.CONFIG.containsKeyBinding(Client.OLD_KEY_NAME_START)) {
            Client.CONFIG.setKeyBinding(Client.KEY_NAME_START,
                    Client.CONFIG.getKeyBinding(Client.OLD_KEY_NAME_START));
        }
        if (!Client.CONFIG.containsKeyBinding(Client.KEY_NAME_END)
                && Client.CONFIG.containsKeyBinding(Client.OLD_KEY_NAME_STOP)) {
            Client.CONFIG.setKeyBinding(Client.KEY_NAME_END,
                    Client.CONFIG.getKeyBinding(Client.OLD_KEY_NAME_STOP));
        }
        InputSystem.addKeyBinding(Client.KEY_NAME_START,
                Client.CONFIG.getKeyBinding(Client.KEY_NAME_START,
                        InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_H,
                                InputConstants.PRESS, 0)),
                ctx -> Client.start());
        InputSystem.addKeyBinding(Client.KEY_NAME_END,
                Client.CONFIG.getKeyBinding(Client.KEY_NAME_END,
                        InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_H,
                                InputConstants.RELEASE, 0)),
                ctx -> Client.stop());
        ToggleStatusHud.Companion.registerStateProvider(Skills.CURRENT_RECHARGE.get(), () -> Client.active);
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.ELECTROMASTER.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.CURRENT_RECHARGE.get(),
                        List.of(MagnetManipulation.Client.SKILL_INFO),
                        R.textures.current_recharge_icon,
                        104,
                        46
                )
        );
        public static final String KEY_NAME_START = SkillNames.CURRENT_RECHARGE + "_start";
        public static final String KEY_NAME_END = SkillNames.CURRENT_RECHARGE + "_end";
        private static final String OLD_KEY_NAME_START = SkillNames.CURRENT_RECHARGE + ".use";
        private static final String OLD_KEY_NAME_STOP = SkillNames.CURRENT_RECHARGE + ".stop";
        public static Config CONFIG = new Config();
        private static boolean active;

        private Client() {
        }

        private static void start() {
            if (ClientUtil.hasScreen() || !AbilitySystemClient.canUseSkill(Skills.CURRENT_RECHARGE.get())) return;
            active = true;
            MisakaNetworkClient.send(StartPacket.INSTANCE);
        }

        private static void stop() {
            active = false;
            MisakaNetworkClient.send(StopPacket.INSTANCE);
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
        private static final Map<Player, Context> ACTIVE = createContextMap();
        private static final Set<ServerPlayer> MIGRATED_PLAYERS = Collections.newSetFromMap(new WeakHashMap<>());
        private static final Map<ServerLevel, Map<BlockPos, Integer>> POWERED_BLOCKS = new WeakHashMap<>();

        private Server() {
        }

        @SubscribePacket
        public static void handle(StartPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            migrateLegacySkill(player);
            if (ACTIVE.containsKey(player)) return;

            var skill = Skills.CURRENT_RECHARGE.get();
            if (!skill.isEnabled(player)) return;
            var context = new Context(player);
            ACTIVE.put(player, context);
            AbilitySystemServer.registerContext(context);
            skill.reportTrigger(player);
        }

        @SubscribePacket
        public static void handle(StopPacket packet) {
            var context = ACTIVE.get(packet.getPacketListener().getPlayer());
            if (context != null) context.end();
        }

        /**
         * Starts the same charge-or-redstone behavior for exactly ten server ticks.
         */
        public static ServerContext startProgramCharge(
                ServerPlayer player,
                @Nullable LivingEntity entity,
                @Nullable BlockPos block
        ) {
            var context = new ProgramChargeContext(player, entity, block);
            AbilitySystemServer.registerContext(context);
            return context;
        }

        private static ChargeTarget resolveChargeTarget(ServerLevel level, ServerPlayer player, int milestone) {
            var eye = player.getEyePosition();
            var look = player.getLookAngle();
            if (look.lengthSqr() <= 1.0E-6) return null;
            var end = eye.add(look.normalize().scale(milestone >= 2 ? 8.0 : CHARGE_REACH));
            var blockHit = level.clip(new ClipContext(
                    eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
            var blockPoint = blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getLocation();
            var entityHit = ProjectileUtil.getEntityHitResult(
                    level, player, eye, blockPoint,
                    new AABB(eye, blockPoint).inflate(1.0),
                    entity -> entity instanceof LivingEntity
                            && entity != player
                            && entity.isAlive()
                            && entity.isPickable()
                            && !entity.isSpectator()
                            && !PvpSetting.shouldPrevent(player, entity),
                    0.3f
            );
            if (entityHit != null && entityHit.getEntity() instanceof LivingEntity living) {
                return ChargeTarget.forEntity(living);
            }
            if (blockHit.getType() == HitResult.Type.BLOCK) {
                return ChargeTarget.forBlock(blockHit.getBlockPos(), blockHit.getDirection());
            }
            return null;
        }

        public static synchronized boolean hasArtificialSignal(Level level, BlockPos pos) {
            if (!(level instanceof ServerLevel serverLevel)) return false;
            var positions = POWERED_BLOCKS.get(serverLevel);
            return positions != null && positions.getOrDefault(pos, 0) > 0;
        }

        private static synchronized void addArtificialSignal(ServerLevel level, BlockPos pos) {
            var positions = POWERED_BLOCKS.computeIfAbsent(level, ignored -> new HashMap<>());
            var previous = positions.getOrDefault(pos, 0);
            positions.put(pos.immutable(), previous + 1);
            if (previous == 0) refreshRedstoneTarget(level, pos);
        }

        private static synchronized void removeArtificialSignal(ServerLevel level, BlockPos pos) {
            var positions = POWERED_BLOCKS.get(level);
            if (positions == null) return;
            var count = positions.getOrDefault(pos, 0);
            if (count <= 1) {
                positions.remove(pos);
                if (positions.isEmpty()) POWERED_BLOCKS.remove(level);
                refreshRedstoneTarget(level, pos);
            } else {
                positions.put(pos.immutable(), count - 1);
            }
        }

        private static void refreshRedstoneTarget(ServerLevel level, BlockPos pos) {
            if (!level.isLoaded(pos)) return;
            var state = level.getBlockState(pos);
            level.neighborChanged(pos, Blocks.REDSTONE_BLOCK, null);
            level.updateNeighborsAt(pos, state.getBlock());
        }

        private static void migrateLegacySkill(ServerPlayer player) {
            if (MIGRATED_PLAYERS.contains(player)) return;
            var system = AbilitySystemServer.getSystem(player);
            var playerData = system.getPlayerData(player.getUUID());
            if (playerData == null) return;
            var map = playerData.getMutableSkillDataMap();
            var legacy = map.remove(LEGACY_PULSE_CHARGE);
            if (legacy != null) {
                var skill = Skills.CURRENT_RECHARGE.get();
                var target = map.get(skill.getKeyString());
                if (target == null) {
                    target = skill.createData();
                    map.put(skill.getKeyString(), target);
                }
                mergeProgress(target, legacy);
                playerData.markDirty();
                system.releaseMaintenanceOccupation(player.getUUID(), LEGACY_PULSE_CHARGE);
                system.getSyncManager().schedulePlayerSync(player.getUUID(), SyncTypes.SKILL_DATA);
            }
            MIGRATED_PLAYERS.add(player);
        }

        private static void mergeProgress(SkillData target, SkillData legacy) {
            target.setProficiency(Math.max(target.getProficiency(), legacy.getProficiency()));
        }
    }

    public static final class Context extends ServerContext {
        private final ResourceKey<Level> dimension;
        private final ServerLevel startedLevel;
        private BlockPos poweredBlock;
        private int ticks;
        private boolean ended;

        private Context(ServerPlayer player) {
            super(player);
            dimension = player.level().dimension();
            startedLevel = player.level();
        }

        @SubscribeEvent
        public void onTick(ServerTickEvent.Pre event) {
            var skill = Skills.CURRENT_RECHARGE.get();
            if (player.hasDisconnected()
                    || !player.isAlive()
                    || !player.level().dimension().equals(dimension)
                    || !skill.isEnabled(player)) {
                end();
                return;
            }
            var milestone = skill.getEffectiveProficiencyMilestone(player);
            var target = Server.resolveChargeTarget(level(), player, milestone);
            skill.reportActivity(player, false);
            if (target == null) {
                clearPoweredBlock();
                return;
            }

            ticks++;
            var system = AbilitySystemServer.getSystem(player);
            var costTick = ticks % CP_CHARGE_INTERVAL_TICKS == 0;
            if (costTick && !system.tryTimedOccupation(
                    player.getUUID(), skill.adjustProficiencyCost(player,
                            SkillProficiencyProfile.CostKind.CONTINUOUS, CP_COST_PER_CHARGE_TICK), skill, 10
            )) {
                clearPoweredBlock();
                return;
            }

            boolean effective;
            if (target.entity() != null) {
                clearPoweredBlock();
                effective = EnergyChargeHelper.chargeEntity(target.entity());
                if (milestone >= 2) effective |= EnergyChargeHelper.chargeEntity(player);
            } else {
                effective = EnergyChargeHelper.chargeBlock(
                        startedLevel,
                        target.blockPos(),
                        target.side()
                );
                if (effective) clearPoweredBlock();
                else {
                    setPoweredBlock(target.blockPos());
                    effective = true;
                }
            }
            if (effective) skill.reportActivity(player, true);
            if (!effective && costTick) clearPoweredBlock();
        }

        private void setPoweredBlock(BlockPos pos) {
            var immutable = pos.immutable();
            if (immutable.equals(poweredBlock)) return;
            clearPoweredBlock();
            poweredBlock = immutable;
            Server.addArtificialSignal(startedLevel, immutable);
        }

        private void clearPoweredBlock() {
            if (poweredBlock == null) return;
            Server.removeArtificialSignal(startedLevel, poweredBlock);
            poweredBlock = null;
        }

        private void end() {
            if (ended) return;
            ended = true;
            unregister();
        }

        @Override
        protected void onUnregistered() {
            ended = true;
            clearPoweredBlock();
            Server.ACTIVE.remove(player, this);
        }
    }

    private static final class ProgramChargeContext extends ServerContext {
        private final ServerLevel startedLevel;
        private final @Nullable LivingEntity entity;
        private final @Nullable BlockPos block;
        private boolean artificialSignal;
        private int ticks;

        private ProgramChargeContext(
                ServerPlayer player,
                @Nullable LivingEntity entity,
                @Nullable BlockPos block
        ) {
            super(player);
            if ((entity == null) == (block == null)) {
                throw new IllegalArgumentException("Program charge needs exactly one target");
            }
            startedLevel = player.level();
            this.entity = entity;
            this.block = block == null ? null : block.immutable();
        }

        @SubscribeEvent
        public void onTick(ServerTickEvent.Pre event) {
            if (player.hasDisconnected()
                    || !player.isAlive()
                    || player.level() != startedLevel
                    || !Skills.CURRENT_RECHARGE.get().isEnabled(player)
                    || ++ticks > 10) {
                unregister();
                return;
            }
            if (entity != null) {
                if (!entity.isAlive() || entity.level() != startedLevel) {
                    unregister();
                    return;
                }
                EnergyChargeHelper.chargeEntity(entity);
                return;
            }
            if (!startedLevel.hasChunkAt(block)) {
                unregister();
                return;
            }
            var charged = EnergyChargeHelper.chargeBlock(startedLevel, block, null);
            if (!charged && !artificialSignal) {
                artificialSignal = true;
                Server.addArtificialSignal(startedLevel, block);
            } else if (charged && artificialSignal) {
                artificialSignal = false;
                Server.removeArtificialSignal(startedLevel, block);
            }
        }

        @Override
        protected void onUnregistered() {
            if (artificialSignal) {
                artificialSignal = false;
                Server.removeArtificialSignal(startedLevel, block);
            }
        }
    }

    private record ChargeTarget(LivingEntity entity, BlockPos blockPos, Direction side) {
        private static ChargeTarget forEntity(LivingEntity entity) {
            return new ChargeTarget(entity, null, null);
        }

        private static ChargeTarget forBlock(BlockPos pos, Direction side) {
            return new ChargeTarget(null, pos.immutable(), side);
        }
    }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post event) {
            if (event.getEntity() instanceof ServerPlayer player) Server.migrateLegacySkill(player);
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
            return PacketTypes.CURRENT_RECHARGE_START.get();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class StopPacket extends Packet<ServerGamePacketListenerImpl, StopPacket> {
        public static final StopPacket INSTANCE = new StopPacket();
        public static final StreamCodec<ByteBuf, StopPacket> CODEC = StreamCodec.unit(INSTANCE);

        private StopPacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, StopPacket> getPacketType() {
            return PacketTypes.CURRENT_RECHARGE_STOP.get();
        }
    }
}
