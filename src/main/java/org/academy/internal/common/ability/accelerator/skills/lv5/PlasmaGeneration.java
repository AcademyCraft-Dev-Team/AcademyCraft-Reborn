package org.academy.internal.common.ability.accelerator.skills.lv5;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
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
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.accelerator.skills.lv4.StormWing;
import org.academy.internal.common.ability.accelerator.skills.lv4.VectorReflection;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.sounds.SoundEvents;
import org.academy.internal.common.world.damagesource.DestroyBlocksSetting;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.skill.Plasma;
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

public class PlasmaGeneration extends Skill {
    public static final int MAX_CHARGE_TICKS = 60;
    public static final double MAX_TARGET_RANGE = 128.0;
    public static final double TRAVEL_SPEED = 2.5;
    public static final float AOE_RADIUS = 20.0f;
    public static final float BASE_DAMAGE = 200.0f;
    public static final float EXPLOSION_POWER = 20.0f;
    public static final int CP_PER_SECOND = 20;

    public PlasmaGeneration() {
        super(Builder
                .of(AbilityCategories.ACCELERATOR.get())
                .level(AbilityLevel.LEVEL5)
                .energyCost(100_000)
                .cpCost(CP_PER_SECOND)
                .iterationTicks(20)
                .maxStacks(NO_STACK_LIMIT)
                .dependsOn(Skills.VECTOR_REFLECTION, Skills.STORM_WING)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL5))
                .devCondition(new DevCondition.DependencyCondition("Storm Wing", "academy:storm_wing"))
        );
    }

    public static float calculateDamage(long chargeTicks) {
        return BASE_DAMAGE;
    }

    public static float calculateExplosionRadius(long chargeTicks) {
        return AOE_RADIUS;
    }

    public static float getChargeProgress(long startTick, long currentTick) {
        return Mth.clamp((float) Math.max(0, currentTick - startTick) / MAX_CHARGE_TICKS, 0.0f, 1.0f);
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);

        InputSystem.addKeyBinding(Client.KEY_NAME_CHARGE, Client.CONFIG.getKeyBinding(Client.KEY_NAME_CHARGE,
                        InputSystem.combo(InputSystem.InputType.MOUSE, InputConstants.MOUSE_BUTTON_4, InputConstants.PRESS, 0)),
                _ -> Client.onChargeStart());
        InputSystem.addKeyBinding(Client.KEY_NAME_RELEASE, Client.CONFIG.getKeyBinding(Client.KEY_NAME_RELEASE,
                        InputSystem.combo(InputSystem.InputType.MOUSE, InputConstants.MOUSE_BUTTON_4, InputConstants.RELEASE, 0)),
                _ -> Client.onFire());
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.ACCELERATOR.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.PLASMA_GENERATION.get(),
                        List.of(VectorReflection.Client.SKILL_INFO, StormWing.Client.SKILL_INFO),
                        R.textures.ability.accelerator.skill.plasma_generation.icon,
                        175,
                        14
                )
        );
        public static final String KEY_NAME_CHARGE = SkillNames.PLASMA_GENERATION + "_charge";
        public static final String KEY_NAME_RELEASE = SkillNames.PLASMA_GENERATION + "_release";
        public static Config CONFIG = new Config();
        private static boolean charging;

        private Client() {
        }

        public static void onChargeStart() {
            if (charging || Minecraft.getInstance().gui.screen() != null
                    || !AbilitySystemClient.canUseSkill(Skills.PLASMA_GENERATION.get())) return;
            charging = true;
            MisakaNetworkClient.send(StartPacket.INSTANCE);
        }

        public static void onFire() {
            if (!charging) return;
            charging = false;
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
        private static final Map<UUID, ChargeState> CHARGE_STATES = new HashMap<>();

        private Server() {
        }

        @SubscribePacket
        public static void handleStart(StartPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            if (!Skills.PLASMA_GENERATION.get().isEnabled(player)) return;
            clearCharge(player);
            if (!(player.level() instanceof ServerLevel level)) return;
            var spawnY = Mth.clamp(player.getY() + 15.0, level.getMinY() + 1.0, level.getMaxY() - 1.0);
            var plasma = new Plasma(EntityTypes.PLASMA.get(), level);
            plasma.setPos(player.getX(), spawnY, player.getZ());
            plasma.setGatherProgress(0.01f);
            if (!level.addFreshEntity(plasma)) return;
            CHARGE_STATES.put(player.getUUID(), new ChargeState(
                    level.dimension(), plasma.getId(), level.getGameTime()
            ));
            player.level().playSound(null, player.blockPosition(), SoundEvents.PLASMA_GENERATION.get(),
                    SoundSource.PLAYERS, 1.0f, 1.0f);
        }

        @SubscribePacket
        public static void handleRelease(ReleasePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var state = CHARGE_STATES.remove(player.getUUID());
            if (state == null || !state.dimension().equals(player.level().dimension())) return;
            if (!(player.level() instanceof ServerLevel level)
                    || !(level.getEntity(state.plasmaEntityId()) instanceof Plasma plasma)) {
                return;
            }
            launch(player, plasma);
        }

        private static void launch(ServerPlayer player, Plasma plasma) {
            var targetPos = findTarget(player);
            var damage = BASE_DAMAGE
                    * AbilitySystemServer.getSystem(player).getPlayerAbilityPowerMultiplier(player.getUUID())
                    * AbilitySystemServer.getSystem(player).getPlayerDamageMultiplier(player.getUUID());
            var destroyBlocks = DestroyBlocksSetting.canDestroyBlocks(player, Skills.PLASMA_GENERATION.get());
            plasma.launch(
                    player.getUUID(),
                    targetPos,
                    TRAVEL_SPEED,
                    damage,
                    AOE_RADIUS,
                    destroyBlocks ? EXPLOSION_POWER : 0.0f,
                    destroyBlocks
            );
        }

        private static Vec3 findTarget(ServerPlayer player) {
            var start = player.getEyePosition();
            var look = player.getLookAngle();
            var end = start.add(look.scale(MAX_TARGET_RANGE));
            var searchBox = player.getBoundingBox().expandTowards(look.scale(MAX_TARGET_RANGE)).inflate(1.0);
            var entityHit = ProjectileUtil.getEntityHitResult(
                    player.level(), player, start, end, searchBox,
                    entity -> entity != player && !entity.isSpectator()
                            && entity.isAlive() && entity.isPickable(),
                    0.3f
            );
            if (entityHit != null) {
                return entityHit.getEntity().getBoundingBox().getCenter();
            }
            var blockHit = player.level().clip(new ClipContext(
                    start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player
            ));
            return blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getLocation();
        }

        private static void tick(ServerPlayer player) {
            var state = CHARGE_STATES.get(player.getUUID());
            if (state == null) return;
            if (!player.isAlive() || player.hasDisconnected()
                    || !state.dimension().equals(player.level().dimension())
                    || !Skills.PLASMA_GENERATION.get().isEnabled(player)) {
                clearCharge(player);
                return;
            }
            if (!(player.level() instanceof ServerLevel level)
                    || !(level.getEntity(state.plasmaEntityId()) instanceof Plasma plasma)) {
                CHARGE_STATES.remove(player.getUUID());
                return;
            }
            var ticks = Math.max(0, player.level().getGameTime() - state.startTick());
            var seconds = ticks / 20;
            if (seconds > 0 && seconds > state.lastConsumedSecond()) {
                var paid = Skills.PLASMA_GENERATION.get().executeActive(player, (_, _) -> {
                });
                if (!paid) {
                    clearCharge(player);
                    return;
                }
                state.lastConsumedSecond = seconds;
            }
            plasma.setGatherProgress(Math.max(
                    0.01f,
                    getChargeProgress(state.startTick(), player.level().getGameTime())
            ));
        }

        private static void clearCharge(ServerPlayer player) {
            var state = CHARGE_STATES.remove(player.getUUID());
            if (state == null) return;
            var level = player.level().getServer().getLevel(state.dimension());
            if (level != null && level.getEntity(state.plasmaEntityId()) instanceof Plasma plasma) {
                plasma.discard();
            }
        }

        private static final class ChargeState {
            private final ResourceKey<Level> dimension;
            private final int plasmaEntityId;
            private final long startTick;
            private long lastConsumedSecond;

            private ChargeState(ResourceKey<Level> dimension,
                                int plasmaEntityId, long startTick) {
                this.dimension = dimension;
                this.plasmaEntityId = plasmaEntityId;
                this.startTick = startTick;
            }

            private ResourceKey<Level> dimension() {
                return dimension;
            }

            private long startTick() {
                return startTick;
            }

            private int plasmaEntityId() {
                return plasmaEntityId;
            }

            private long lastConsumedSecond() {
                return lastConsumedSecond;
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
        public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) Server.clearCharge(player);
        }

        @SubscribeEvent
        public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) Server.clearCharge(player);
        }

        @SubscribeEvent
        public static void onLivingDeath(LivingDeathEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) Server.clearCharge(player);
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
            return PacketTypes.PLASMA_GENERATION_START.get();
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
            return PacketTypes.PLASMA_GENERATION_RELEASE.get();
        }
    }
}
