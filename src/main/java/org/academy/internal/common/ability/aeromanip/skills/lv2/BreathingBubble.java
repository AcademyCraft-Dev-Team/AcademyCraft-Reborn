package org.academy.internal.common.ability.aeromanip.skills.lv2;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
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
import org.academy.api.common.ability.LearningHelper;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.aeromanip.AeromanipConfig;
import org.academy.internal.common.ability.aeromanip.AeromanipVfx;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.server.ability.AeromanipResourceManager;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Maintains a compressed-air bubble when its owner or nearby allies cannot breathe. */
public final class BreathingBubble extends Skill {
    static final int REFRESH_INTERVAL_TICKS = 10;
    static final int PASSIVE_VFX_COOLDOWN_TICKS = 10 * 20;
    private static final float BASE_COMPRESSED_AIR_COST = 4.0f;
    private static final float SHARED_COMPRESSED_AIR_COST = 2.0f;

    public BreathingBubble() {
        super(Builder
                .of(AbilityCategories.AEROMANIP.get())
                .level(AbilityLevel.LEVEL2)
                .energyCost(10_000)
                .passive()
                .maintenanceCost(20)
                .iterationTicks(20)
                .maxStacks(NO_STACK_LIMIT)
                .dependsOn(Skills.FLOW_SENSE)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL2))
        );
    }

    @Override
    public void initClient() {
        Client.initialize();
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    static float compressedAirCost(int milestone, boolean sharing) {
        return (milestone >= 1 ? 3.0f : BASE_COMPRESSED_AIR_COST)
                + (sharing ? SHARED_COMPRESSED_AIR_COST : 0.0f);
    }

    static double activeRadius(int milestone) {
        return milestone >= 3 ? 24.0 : 16.0;
    }

    static boolean passiveVfxCooldownElapsed(long currentGameTime, long lastGameTime) {
        return currentGameTime < lastGameTime
                || currentGameTime - lastGameTime >= PASSIVE_VFX_COOLDOWN_TICKS;
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.AEROMANIP.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.BREATHING_BUBBLE.get(),
                        List.of(FlowSense.Client.SKILL_INFO),
                        R.textures.breathing_film_icon,
                        75,
                        40
                )
        );
        public static final String KEY_NAME_CAST = SkillNames.BREATHING_BUBBLE + "_cast";
        public static Config CONFIG = new Config();

        private Client() {
        }

        private static void initialize() {
            var skill = Skills.BREATHING_BUBBLE.get();
            AcademyCraftConfig.registerTypeHandler(skill.getKey(), Config.Action.INSTANCE);
            CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(skill.getKey());
            var defaultBinding = InputSystem.combo(
                    InputSystem.InputType.KEYBOARD,
                    InputConstants.KEY_U,
                    InputConstants.RELEASE,
                    InputConstants.MOD_ALT);
            var binding = CONFIG.getKeyBindingMigratingDefaults(
                    KEY_NAME_CAST,
                    defaultBinding,
                    InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_H,
                            InputConstants.RELEASE, 0));
            InputSystem.addKeyBinding(KEY_NAME_CAST, binding, _ -> cast());
        }

        private static void cast() {
            if (AbilitySystemClient.canUseSkill(Skills.BREATHING_BUBBLE.get())) {
                MisakaNetworkClient.send(CastPacket.INSTANCE);
            }
        }

        public static final class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final Action INSTANCE = new Action();

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

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class Events {
        private static final Map<ServerPlayer, Long> LAST_PASSIVE_VFX_TICKS = new WeakHashMap<>();

        private Events() {
        }

        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            if (player.level().getGameTime() % REFRESH_INTERVAL_TICKS != 0) return;

            var skill = Skills.BREATHING_BUBBLE.get();
            var system = AbilitySystemServer.getSystem(player);
            var runtimeData = skill.getRuntimeData(player);
            var available = runtimeData.isPresent() && LearningHelper.isSkillAvailableForCategory(
                    system.getPlayerAbilityCategory(player.getUUID()), skill);
            if (!available || !player.isAlive()) {
                Server.stopSustaining(player);
                system.releaseMaintenanceOccupation(player.getUUID(), skill.getKeyString());
                return;
            }

            var sharedTargets = skill.hasProficiencyMilestone(player, 2)
                    ? player.level().getEntitiesOfClass(ServerPlayer.class, player.getBoundingBox().inflate(4.0),
                    target -> target != player && target.isAlive() && player.isAlliedTo(target)
                            && target.distanceToSqr(player) <= 16.0 && isHazardous(target))
                    : List.<ServerPlayer>of();
            var hazardous = isHazardous(player);
            if (!hazardous && sharedTargets.isEmpty()) {
                Server.stopSustaining(player);
                system.releaseMaintenanceOccupation(player.getUUID(), skill.getKeyString());
                return;
            }

            if (!runtimeData.orElseThrow().isEnabled()) {
                system.toggleSkill(player.getUUID(), skill.getKeyString());
            }
            if (!system.ensurePermanentOccupation(
                    player.getUUID(),
                    skill.getMaintenanceCost(player)
                            * AeromanipConfig.cpMultiplier(player, SkillNames.BREATHING_BUBBLE)
                            + (sharedTargets.isEmpty() ? 0.0f : 5.0f),
                    skill)) {
                Server.stopSustaining(player);
                return;
            }

            Server.startSustaining(player);
            var milestone = skill.getEffectiveProficiencyMilestone(player);
            if (!skill.executeContinuousWithResource(
                    player,
                    _ -> 0.0f,
                    _ -> compressedAirCost(milestone, !sharedTargets.isEmpty()),
                    (_, _) -> refillAir(player, sharedTargets),
                    true)) {
                Server.stopSustaining(player);
                system.releaseMaintenanceOccupation(player.getUUID(), skill.getKeyString());
            }
        }

        private static boolean isHazardous(ServerPlayer player) {
            return player.isEyeInFluid(FluidTags.WATER)
                    || player.getAirSupply() < player.getMaxAirSupply();
        }

        private static void refillAir(ServerPlayer player, List<ServerPlayer> sharedTargets) {
            player.setAirSupply(player.getMaxAirSupply());
            for (var target : sharedTargets) target.setAirSupply(target.getMaxAirSupply());
            if (player.level() instanceof ServerLevel level
                    && shouldPlayPassiveVfx(player, level.getGameTime())) {
                AeromanipVfx.burst(level, new net.minecraft.world.phys.Vec3(
                        player.getX(), player.getEyeY(), player.getZ()), 0.72);
            }
        }

        private static boolean shouldPlayPassiveVfx(ServerPlayer player, long gameTime) {
            var lastGameTime = LAST_PASSIVE_VFX_TICKS.get(player);
            if (lastGameTime != null
                    && !passiveVfxCooldownElapsed(gameTime, lastGameTime)) return false;
            LAST_PASSIVE_VFX_TICKS.put(player, gameTime);
            return true;
        }
    }

    public static final class Server {
        private static final Map<ServerPlayer, AeromanipResourceManager.UsageLease> ACTIVE =
                new WeakHashMap<>();

        private Server() {
        }

        @SubscribePacket
        public static void handle(CastPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var skill = Skills.BREATHING_BUBBLE.get();
            if (!skill.isEnabled(player)) return;
            var milestone = skill.getEffectiveProficiencyMilestone(player);
            var radius = activeRadius(milestone);
            skill.executeActiveWithResource(
                    player,
                    _ -> 15.0f * AeromanipConfig.cpMultiplier(player, SkillNames.BREATHING_BUBBLE),
                    _ -> Math.max(0.0f, AeromanipConfig.skillFloat(
                            player, SkillNames.BREATHING_BUBBLE, "activeCompressedAirCost", 24.0f)),
                    (_, _) -> refillSupportedTargets(player, radius));
        }

        public static boolean isSustained(ServerPlayer player) {
            return ACTIVE.containsKey(player);
        }

        private static void startSustaining(ServerPlayer player) {
            ACTIVE.computeIfAbsent(player, current -> AbilitySystemServer.getSystem(current)
                    .getAeromanipResourceManager().beginUse(current));
        }

        private static void stopSustaining(ServerPlayer player) {
            var lease = ACTIVE.remove(player);
            if (lease != null) lease.close();
        }

        private static void refillSupportedTargets(ServerPlayer player, double radius) {
            var targets = player.level().getEntitiesOfClass(
                    LivingEntity.class,
                    player.getBoundingBox().inflate(radius),
                    target -> target.isAlive()
                            && target.distanceToSqr(player) <= radius * radius
                            && isSupportedTarget(player, target));
            for (var target : targets) target.setAirSupply(target.getMaxAirSupply());
            if (player.level() instanceof ServerLevel level) {
                AeromanipVfx.burst(level, new net.minecraft.world.phys.Vec3(
                        player.getX(), player.getEyeY(), player.getZ()),
                        Math.max(0.75, radius * 0.34));
            }
        }

        private static boolean isSupportedTarget(ServerPlayer owner, LivingEntity target) {
            if (target == owner) return true;
            if (target instanceof Player) return owner.isAlliedTo(target);
            return target instanceof TamableAnimal animal && animal.isOwnedBy(owner);
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class CastPacket extends Packet<ServerGamePacketListenerImpl, CastPacket> {
        public static final CastPacket INSTANCE = new CastPacket();
        public static final StreamCodec<ByteBuf, CastPacket> CODEC = StreamCodec.unit(INSTANCE);

        private CastPacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, CastPacket> getPacketType() {
            return PacketTypes.BREATHING_BUBBLE_CAST.get();
        }
    }
}
