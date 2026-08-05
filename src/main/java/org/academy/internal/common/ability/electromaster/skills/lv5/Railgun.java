package org.academy.internal.common.ability.electromaster.skills.lv5;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.renderer.RendererManager;
import org.academy.api.client.resources.R;
import org.academy.api.client.sync.ClientSyncManager;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.DevCondition;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.common.util.LevelUtil;
import org.academy.api.common.util.MathUtil;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.ability.ServerContext;
import org.academy.api.server.sync.DataSyncManager;
import org.academy.api.server.sync.ServerSyncManager;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.client.renderer.effect.RailgunEffectRenderer;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.accelerator.reflection.LinearAttackExecutor;
import org.academy.internal.common.ability.accelerator.reflection.LinearAttackPayload;
import org.academy.internal.common.ability.accelerator.reflection.LinearReflectionResolver;
import org.academy.internal.common.ability.accelerator.reflection.LinearSegment;
import org.academy.internal.common.ability.electromaster.skills.lv3.ThunderLance;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.sounds.SoundEvents;
import org.academy.internal.common.world.damagesource.DestroyBlocksSetting;
import org.academy.internal.common.sync.DataTypes;
import org.academy.internal.common.sync.SyncKeys;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.skill.RailgunRay;
import org.academy.internal.common.world.entity.projectile.ThrownCoin;
import org.academy.internal.common.world.item.CoinItem;
import org.academy.internal.common.world.item.Items;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.*;

import static org.misaka.MisakaNetworkClient.send;

public final class Railgun extends Skill {
    public static final int CHARGE_TIME = 0;
    public static final int RELEASE_VISUAL_TICKS = 12;

    public Railgun() {
        super(Builder
                .of(AbilityCategories.ELECTROMASTER.get())
                .level(AbilityLevel.LEVEL4)
                .energyCost(60_000)
                .cpCost(100)
                .iterationTicks(80)
                .maxStacks(Skill.NO_STACK_LIMIT)
                .dependsOn(Skills.THUNDER_LANCE)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL4))
                .devCondition(new DevCondition.DependencyCondition("Thunder Lance", "academy:thunder_lance"))
        );
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
        var key = SyncKeys.RAILGUN_CHARGING.get();
        Server.chargingSyncManager = new DataSyncManager<>(
                key, DataTypes.BOOL.get(), context.getMinecraftServer().getPlayerList()
        );
        ServerSyncManager.register(key, Server.chargingSyncManager);
    }

    @Override
    public void initClient() {
        RendererManager.registerEffectRenderer(RailgunEffectRenderer.INSTANCE);
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CLIENT_CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);

        InputSystem.addKeyBinding(Client.KEY_NAME_START, Client.CLIENT_CONFIG.getKeyBinding(Client.KEY_NAME_START,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_R,
                        InputConstants.RELEASE, InputConstants.MOD_ALT)
        ), ctx -> Client.start());

        ClientSyncManager.register(SyncKeys.RAILGUN_CHARGING.get(), Client::setCharging);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.ELECTROMASTER.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.RAILGUN.get(),
                        List.of(ThunderLance.Client.SKILL_INFO),
                        R.textures.ability.electromaster.skill.railgun.icon,
                        164,
                        59
                )
        );
        public static final String KEY_NAME_START = SkillNames.RAILGUN + "_start";
        public static Config CLIENT_CONFIG = new Config();
        private static boolean charging = false;

        public static boolean isCharging() {
            return charging;
        }

        public static void setCharging(boolean charging) {
            Client.charging = charging;
        }

        public static void start() {
            if (Minecraft.getInstance().gui.screen() != null) return;
            if (!AbilitySystemClient.canUseSkill(Skills.RAILGUN.get())) return;
            send(StartPacket.INSTANCE);
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public Railgun.Client.Config getDefault() {
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
        public static final Map<Player, Context> CONTEXT_MAP = createContextMap();
        private static final Map<Player, ReleaseVisualContext> RELEASE_VISUALS = createContextMap();
        @Nullable
        private static DataSyncManager<Boolean> chargingSyncManager;

        @SubscribePacket
        public static void onStartCharge(StartPacket packet) {
            if (chargingSyncManager == null) return;

            var player = packet.getPacketListener().getPlayer();
            if (CONTEXT_MAP.containsKey(player)) return;
            var releaseVisual = RELEASE_VISUALS.get(player);
            if (releaseVisual != null) releaseVisual.end();
            var ammo = findAmmo(player);
            if (ammo == null) return;

            Skills.RAILGUN.get().executeActive(player, (_, _) -> {
                ammo.consume(player);
                chargingSyncManager.set(player.getUUID(), true);

                var context = new Context(player, ammo.renderHand());
                CONTEXT_MAP.put(player, context);
                AbilitySystemServer.registerContext(context);
            });
        }

        @SubscribePacket
        public static void onThrowCoin(CoinItem.ThrowCoinPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            if (!Skills.RAILGUN.get().isEnabled(player)) return;

            var hand = findHeldAmmoHand(player, true);
            if (hand == null) return;
            var stack = player.getItemInHand(hand);
            if (player.getCooldowns().isOnCooldown(stack)) return;
            player.getCooldowns().addCooldown(stack, 5);
            if (!player.isCreative()) stack.shrink(1);

            var thrownCoin = new ThrownCoin(player.level(), player);
            thrownCoin.setPos(player.getX(), player.getEyeY() - 0.1, player.getZ());
            var initialVelocity = player.onGround()
                    ? player.getDeltaMovement().multiply(2.25, 0, 2.25)
                    : player.getDeltaMovement().multiply(1.5, 0, 1.5);
            thrownCoin.setDeltaMovement(initialVelocity.add(0, 0.5, 0));
            thrownCoin.setYRot(player.getYRot());
            thrownCoin.setXRot(player.getXRot());
            thrownCoin.yRotO = player.getYRot();
            thrownCoin.xRotO = player.getXRot();
            player.level().addFreshEntity(thrownCoin);
            player.level().playSound(
                    null,
                    player,
                    SoundEvents.COIN.get(),
                    SoundSource.PLAYERS,
                    1.0f,
                    1.0f
            );
        }

        private static @Nullable AmmoSource findAmmo(ServerPlayer player) {
            var center = player.position().add(0, player.getBbHeight() / 2.0, 0);
            var box = new AABB(center.subtract(1.5, 1.5, 1.5), center.add(1.5, 1.5, 1.5));
            var thrownCoins = player.level().getEntitiesOfClass(
                    ThrownCoin.class,
                    box,
                    coin -> coin.isAlive()
            );
            if (!thrownCoins.isEmpty()) {
                return new AmmoSource(null, thrownCoins.getFirst());
            }

            var hand = findHeldAmmoHand(player, false);
            return hand == null ? null : new AmmoSource(hand, null);
        }

        private static @Nullable InteractionHand findHeldAmmoHand(Player player, boolean coinOnly) {
            for (var hand : InteractionHand.values()) {
                var stack = player.getItemInHand(hand);
                if (coinOnly ? stack.is(Items.COIN.get()) : isAmmo(stack)) return hand;
            }
            return null;
        }

        public static class Context extends ServerContext {
            private final InteractionHand hand;
            private final boolean rightHand;
            private final ResourceKey<Level> dimension;
            private int ticks = 0;
            private boolean ended;

            public Context(ServerPlayer player, InteractionHand hand) {
                super(player);
                this.hand = hand;
                rightHand = (player.getMainArm() == HumanoidArm.RIGHT)
                        == (hand == InteractionHand.MAIN_HAND);
                dimension = player.level().dimension();
            }

            @Override
            protected void onUnregistered() {
                ended = true;
                CONTEXT_MAP.remove(player, this);
                player.removeData(AttachmentTypes.RAILGUN_DATA);
                if (chargingSyncManager != null) {
                    chargingSyncManager.set(player.getUUID(), false);
                }
            }

            @SubscribeEvent
            public void onTick(ServerTickEvent.Post event) {
                ticks++;

                if (ended
                        || player.hasDisconnected()
                        || !player.isAlive()
                        || !player.level().dimension().equals(dimension)
                        || !Skills.RAILGUN.get().isEnabled(player)) {
                    end();
                    return;
                }

                player.setData(
                        AttachmentTypes.RAILGUN_DATA,
                        new Data(rightHand, ticks, false)
                );

                var lookDir = player.getLookAngle();
                var right = lookDir.cross(new Vec3(0, 1, 0));
                if (right.lengthSqr() < 1.0E-6) right = new Vec3(1, 0, 0);
                var startPos = player.position()
                        .add(right.normalize().scale(0.4))
                        .add(0, 1.2, 0)
                        .add(lookDir.scale(0.5));
                if (ticks > CHARGE_TIME) {

                    var railgunRay = new RailgunRay(EntityTypes.RAILGUN_RAY.get(), player.level());
                    var length = RailgunRay.DEFAULT_LENGTH;
                    var endPos = startPos.add(lookDir.scale(length));
                    railgunRay.setPos(startPos);
                    railgunRay.setYRot(player.getYRot());
                    railgunRay.setXRot(player.getXRot());
                    var originalSource = new DamageSource(
                            railgunRay.level().damageSources().damageTypes.getOrThrow(DamageTypes.MOB_ATTACK),
                            railgunRay,
                            player
                    );
                    var damageSource = SkillDamageSource.from(originalSource, Skills.RAILGUN.get());
                    var system = AbilitySystemServer.getSystem(player);
                    var damage = calculateDamage(
                            system.getPlayerAbilityPowerMultiplier(player.getUUID()),
                            system.getPlayerDamageMultiplier(player.getUUID())
                    );
                    var payload = LinearAttackPayload.builder(
                                    player,
                                    Skills.RAILGUN.get(),
                                    damageSource,
                                    0.125f
                            )
                            .damage(_ -> damage)
                            .build();
                    var resolved = LinearReflectionResolver.resolve(
                            player.level(),
                            new LinearSegment(startPos, endPos),
                            payload
                    );
                    railgunRay.setBeamPath(
                            (float) resolved.original().length(),
                            resolved.isReflected(),
                            (float) resolved.outbound().length()
                    );
                    player.level().addFreshEntity(railgunRay);

                    if (DestroyBlocksSetting.canDestroyBlocks(player, Skills.RAILGUN.get())) {
                        destroyBlocksAlongSegment(resolved.outbound(), player);
                    }
                    var outboundResult = LinearAttackExecutor.executeOutbound(
                            player.level(),
                            resolved,
                            payload
                    );
                    if (DestroyBlocksSetting.canDestroyBlocks(player, Skills.RAILGUN.get())) {
                        resolved.returnSegment().ifPresent(returnSegment -> resolved.reflectionCandidate()
                                .ifPresent(candidate -> destroyBlocksAlongSegment(
                                        returnSegment,
                                        candidate.reflector()
                                )));
                    }
                    LinearAttackExecutor.executeReturn(
                            player.level(),
                            resolved,
                            payload,
                            outboundResult
                    );
                    railgunRay.level().playSound(
                            null,
                            startPos.x,
                            startPos.y,
                            startPos.z,
                            SoundEvents.RAILGUN.get(),
                            SoundSource.PLAYERS,
                            1.0f,
                            1.0f
                    );
                    end();
                    startReleaseVisual(player, rightHand);
                }
            }

            private static void destroyBlocksAlongSegment(LinearSegment segment, ServerPlayer breaker) {
                LevelUtil.destroyBlocksAlongPath(
                        breaker.level(),
                        segment.start(),
                        segment.end(),
                        0.125f,
                        3,
                        !breaker.isCreative(),
                        false,
                        false,
                        false,
                        breaker
                );
            }

            public void end() {
                if (ended) return;
                ended = true;
                unregister();
            }

        }

        private static void startReleaseVisual(ServerPlayer player, boolean rightHand) {
            var previous = RELEASE_VISUALS.get(player);
            if (previous != null) previous.end();
            var context = new ReleaseVisualContext(player, rightHand);
            RELEASE_VISUALS.put(player, context);
            AbilitySystemServer.registerContext(context);
        }

        private static final class ReleaseVisualContext extends ServerContext {
            private final boolean rightHand;
            private final ResourceKey<Level> dimension;
            private int ticks;
            private boolean ended;

            private ReleaseVisualContext(ServerPlayer player, boolean rightHand) {
                super(player);
                this.rightHand = rightHand;
                dimension = player.level().dimension();
                player.setData(AttachmentTypes.RAILGUN_DATA, new Data(rightHand, 0, true));
            }

            @SubscribeEvent
            public void onTick(ServerTickEvent.Post event) {
                if (ended
                        || player.hasDisconnected()
                        || !player.isAlive()
                        || !player.level().dimension().equals(dimension)
                        || ticks >= RELEASE_VISUAL_TICKS) {
                    end();
                    return;
                }
                ticks++;
                player.setData(AttachmentTypes.RAILGUN_DATA, new Data(rightHand, ticks, true));
            }

            private void end() {
                if (ended) return;
                ended = true;
                unregister();
            }

            @Override
            protected void onUnregistered() {
                ended = true;
                RELEASE_VISUALS.remove(player, this);
                var data = player.getExistingDataOrNull(AttachmentTypes.RAILGUN_DATA);
                if (data != null && data.released()) {
                    player.removeData(AttachmentTypes.RAILGUN_DATA);
                }
            }
        }

        private record AmmoSource(
                @Nullable InteractionHand hand,
                @Nullable ThrownCoin thrownCoin
        ) {
            private InteractionHand renderHand() {
                return hand == null ? InteractionHand.MAIN_HAND : hand;
            }

            private void consume(ServerPlayer player) {
                if (thrownCoin != null) {
                    thrownCoin.discard();
                } else if (hand != null && !player.isCreative()) {
                    player.getItemInHand(hand).shrink(1);
                }
            }
        }
    }

    static boolean isVanillaAmmo(ItemStack stack) {
        return isVanillaAmmoId(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    static boolean isVanillaAmmoId(Identifier id) {
        return Identifier.withDefaultNamespace("iron_ingot").equals(id)
                || Identifier.withDefaultNamespace("iron_block").equals(id);
    }

    static boolean isAmmo(ItemStack stack) {
        return isVanillaAmmo(stack) || stack.is(Items.COIN.get());
    }

    static float calculateDamage(float abilityPower, float playerMultiplier) {
        if (!Float.isFinite(abilityPower) || !Float.isFinite(playerMultiplier)) return 0;
        return 150.0f * Math.max(0, abilityPower) * Math.max(0, playerMultiplier);
    }

    public record Data(boolean rightHand, int ticks, boolean released) {
        public static final StreamCodec<ByteBuf, Data> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Data::rightHand,
                ByteBufCodecs.INT, Data::ticks,
                ByteBufCodecs.BOOL, Data::released,
                Data::new
        );
        private static final Data DEFAULT = new Data(true, 0, false);

        public static Data getDefault() {
            return DEFAULT;
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
            return PacketTypes.RAILGUN_START_CHARGE.get();
        }
    }
}
