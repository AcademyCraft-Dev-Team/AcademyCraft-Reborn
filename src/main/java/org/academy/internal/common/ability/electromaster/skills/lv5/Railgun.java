package org.academy.internal.common.ability.electromaster.skills.lv5;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
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
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.resources.R;
import org.academy.api.client.sync.ClientSyncManager;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.DevCondition;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.common.util.LevelUtil;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.ability.ServerContext;
import org.academy.api.server.sync.DataSyncManager;
import org.academy.api.server.sync.ServerSyncManager;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.client.render.vfx.RailgunVfxClient;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.accelerator.reflection.LinearAttackExecutor;
import org.academy.internal.common.ability.accelerator.reflection.LinearAttackPayload;
import org.academy.internal.common.ability.accelerator.reflection.LinearReflectionResolver;
import org.academy.internal.common.ability.accelerator.reflection.LinearSegment;
import org.academy.internal.common.ability.electromaster.ElectromasterArcEffects;
import org.academy.internal.common.ability.electromaster.skills.lv3.ThunderLance;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.sounds.SoundEvents;
import org.academy.internal.common.sync.DataTypes;
import org.academy.internal.common.sync.SyncKeys;
import org.academy.internal.common.world.damagesource.DestroyBlocksSetting;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.projectile.ThrownCoin;
import org.academy.internal.common.world.entity.skill.RailgunRay;
import org.academy.internal.common.world.item.CoinItem;
import org.academy.internal.common.world.item.Items;
import org.jspecify.annotations.Nullable;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.List;
import java.util.Map;

import static org.misaka.MisakaNetworkClient.send;

public final class Railgun extends Skill {
    public static final int CHARGE_TIME = 30;
    public static final int RELEASE_VISUAL_TICKS = 12;
    public static final int COIN_RETURN_HINT_TICKS = 12;

    public Railgun() {
        super(Builder
                .of(AbilityCategories.ELECTROMASTER.get())
                .level(AbilityLevel.LEVEL4)
                .energyCost(60_000)
                .cpCost(100)
                .iterationTicks(20)
                .maxStacks(Skill.NO_STACK_LIMIT)
                .dependsOn(Skills.THUNDER_LANCE)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL4))
                .devCondition(new DevCondition.DependencyCondition("Thunder Lance", "academy:thunder_lance"))
        );
    }

    static boolean isVanillaAmmo(ItemStack stack) {
        return isVanillaAmmoId(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    static boolean isVanillaAmmoId(Identifier id) {
        return AmmoKind.fromVanillaId(id) != null;
    }

    static boolean isAmmo(ItemStack stack) {
        return ammoKind(stack) != null;
    }

    private static @Nullable AmmoKind ammoKind(ItemStack stack) {
        if (stack.is(Items.COIN.get())) return AmmoKind.COIN;
        return AmmoKind.fromVanillaId(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    static float calculateDamage(float abilityPower, float playerMultiplier) {
        if (!Float.isFinite(abilityPower) || !Float.isFinite(playerMultiplier)) return 0;
        return 150.0f * Math.max(0, abilityPower) * Math.max(0, playerMultiplier);
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
        RailgunVfxClient.register();
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CLIENT_CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);

        var startBinding = Client.CLIENT_CONFIG.getKeyBinding(
                Client.KEY_NAME_START,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_R,
                        InputConstants.PRESS, InputConstants.MOD_ALT)
        );
        if (startBinding.action() != InputConstants.PRESS) {
            startBinding = Client.withAction(startBinding, InputConstants.PRESS);
            Client.CLIENT_CONFIG.setKeyBinding(Client.KEY_NAME_START, startBinding);
            AcademyCraftClient.Config.INSTANCE.save();
        }
        InputSystem.addKeyBinding(Client.KEY_NAME_START, startBinding, _ -> Client.start());

        var endBinding = Client.CLIENT_CONFIG.getKeyBinding(
                Client.KEY_NAME_END,
                Client.withAction(startBinding, InputConstants.RELEASE)
        );
        if (endBinding.action() != InputConstants.RELEASE) {
            endBinding = Client.withAction(endBinding, InputConstants.RELEASE);
            Client.CLIENT_CONFIG.setKeyBinding(Client.KEY_NAME_END, endBinding);
            AcademyCraftClient.Config.INSTANCE.save();
        }
        InputSystem.addKeyBinding(Client.KEY_NAME_END, endBinding, _ -> Client.end());

        ClientSyncManager.register(SyncKeys.RAILGUN_CHARGING.get(), Client::setCharging);
    }

    enum AmmoKind {
        COIN(0, 1.0f, 0.0f, 0.8f),
        IRON_INGOT(10, 1.5f, 8.0f, 1.0f),
        IRON_BLOCK(20, 2.0f, 16.0f, 1.5f),
        ANVIL(30, 2.5f, 24.0f, 2.0f);

        private final int minimumChargeTicks;
        private final float beamWidthMultiplier;
        private final float rangeBonus;
        private final float damageMultiplier;

        AmmoKind(
                int minimumChargeTicks,
                float beamWidthMultiplier,
                float rangeBonus,
                float damageMultiplier
        ) {
            this.minimumChargeTicks = minimumChargeTicks;
            this.beamWidthMultiplier = beamWidthMultiplier;
            this.rangeBonus = rangeBonus;
            this.damageMultiplier = damageMultiplier;
        }

        private static @Nullable AmmoKind fromVanillaId(Identifier id) {
            if (Identifier.withDefaultNamespace("iron_ingot").equals(id)) return IRON_INGOT;
            if (Identifier.withDefaultNamespace("iron_block").equals(id)) return IRON_BLOCK;
            if (Identifier.withDefaultNamespace("anvil").equals(id)
                    || Identifier.withDefaultNamespace("chipped_anvil").equals(id)
                    || Identifier.withDefaultNamespace("damaged_anvil").equals(id)) return ANVIL;
            return null;
        }

        int minimumChargeTicks() {
            return minimumChargeTicks;
        }

        float beamWidthMultiplier() {
            return beamWidthMultiplier;
        }

        float beamLength() {
            return RailgunRay.DEFAULT_LENGTH + rangeBonus;
        }

        float damageMultiplier() {
            return damageMultiplier;
        }
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
        public static final String KEY_NAME_END = SkillNames.RAILGUN + "_end";
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

        public static void end() {
            send(EndPacket.INSTANCE);
        }

        private static InputSystem.KeyCombination withAction(
                InputSystem.KeyCombination binding,
                int action
        ) {
            return new InputSystem.KeyCombination(
                    binding.type(),
                    binding.keys(),
                    action,
                    binding.modifiers(),
                    binding.availableWhenScreen(),
                    binding.unbound()
            );
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
            if (!Skills.RAILGUN.get().isEnabled(player)) return;
            var releaseVisual = RELEASE_VISUALS.get(player);
            if (releaseVisual != null) releaseVisual.end();

            var previewAmmo = findAmmo(player);
            var renderHand = previewAmmo == null
                    ? InteractionHand.MAIN_HAND
                    : previewAmmo.renderHand();
            chargingSyncManager.set(player.getUUID(), true);
            var context = new Context(player, renderHand);
            CONTEXT_MAP.put(player, context);
            AbilitySystemServer.registerContext(context);
        }

        @SubscribePacket
        public static void onEndCharge(EndPacket packet) {
            var context = CONTEXT_MAP.get(packet.getPacketListener().getPlayer());
            if (context != null) context.release();
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

        private static void onCoinReturned(ServerPlayer player) {
            var context = CONTEXT_MAP.get(player);
            if (context != null) context.flashCoinReturnHint();
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
                return new AmmoSource(AmmoKind.COIN, null, thrownCoins.getFirst());
            }

            var hand = findHeldAmmoHand(player, false);
            if (hand == null) return null;
            var kind = ammoKind(player.getItemInHand(hand));
            return kind == null ? null : new AmmoSource(kind, hand, null);
        }

        private static @Nullable InteractionHand findHeldAmmoHand(Player player, boolean coinOnly) {
            for (var hand : InteractionHand.values()) {
                var stack = player.getItemInHand(hand);
                if (coinOnly ? stack.is(Items.COIN.get()) : isAmmo(stack)) return hand;
            }
            return null;
        }

        private static void startReleaseVisual(
                ServerPlayer player,
                boolean rightHand,
                boolean mainHandRight
        ) {
            var previous = RELEASE_VISUALS.get(player);
            if (previous != null) previous.end();
            var context = new ReleaseVisualContext(player, rightHand, mainHandRight);
            RELEASE_VISUALS.put(player, context);
            AbilitySystemServer.registerContext(context);
        }

        public static class Context extends ServerContext {
            private final boolean rightHand;
            private final boolean mainHandRight;
            private final ResourceKey<Level> dimension;
            private final int proficiencyMilestone;
            private int ticks = 0;
            private int coinReturnHintTicks;
            @Nullable
            private AmmoKind readyHintAmmo;
            private boolean ended;

            public Context(ServerPlayer player, InteractionHand hand) {
                super(player);
                rightHand = (player.getMainArm() == HumanoidArm.RIGHT)
                        == (hand == InteractionHand.MAIN_HAND);
                mainHandRight = player.getMainArm() == HumanoidArm.RIGHT;
                dimension = player.level().dimension();
                proficiencyMilestone = Skills.RAILGUN.get().getEffectiveProficiencyMilestone(player);
            }

            private static void destroyBlocksAlongSegment(
                    LinearSegment segment,
                    ServerPlayer breaker,
                    float radius
            ) {
                LevelUtil.destroyBlocksAlongPath(
                        breaker.level(),
                        segment.start(),
                        segment.end(),
                        radius,
                        3,
                        !breaker.isCreative(),
                        false,
                        false,
                        false,
                        breaker
                );
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
                var skill = Skills.RAILGUN.get();
                skill.reportActivity(player, false);
                updateChargeReadyHint();

                player.setData(
                        AttachmentTypes.RAILGUN_DATA,
                        new Data(rightHand, mainHandRight, ticks, false, coinReturnHintTicks > 0)
                );
                if (coinReturnHintTicks > 0) coinReturnHintTicks--;
            }

            private void updateChargeReadyHint() {
                var hand = findHeldAmmoHand(player, false);
                var kind = hand == null ? null : ammoKind(player.getItemInHand(hand));
                if (kind == null || kind == AmmoKind.COIN || ticks < minimumChargeTicks(kind)) {
                    readyHintAmmo = null;
                    return;
                }
                if (readyHintAmmo == kind) return;
                readyHintAmmo = kind;
                flashCoinReturnHint();
            }

            private void flashCoinReturnHint() {
                coinReturnHintTicks = COIN_RETURN_HINT_TICKS;
            }

            private void release() {
                if (ended) return;
                var ammo = findAmmo(player);
                if (ammo == null || ticks < minimumChargeTicks(ammo.kind())) {
                    end();
                    return;
                }

                var fired = new boolean[1];
                var skill = Skills.RAILGUN.get();
                skill.executeActive(player, (_, _) -> {
                    ammo.consume(player);
                    fire(skill, ammo);
                    fired[0] = true;
                });
                end();
                if (fired[0]) startReleaseVisual(player, rightHand, mainHandRight);
            }

            private void fire(Skill skill, AmmoSource ammo) {
                var lookDir = player.getLookAngle();
                var right = lookDir.cross(new Vec3(0, 1, 0));
                if (right.lengthSqr() < 1.0E-6) right = new Vec3(1, 0, 0);
                var startPos = player.position()
                        .add(right.normalize().scale(rightHand ? 0.4 : -0.4))
                        .add(0, 1.2, 0)
                        .add(lookDir.scale(0.5));
                skill.reportActivity(player, true);

                var profile = ammo.kind();
                var beamRadius = 0.125f * profile.beamWidthMultiplier();
                var railgunRay = new RailgunRay(EntityTypes.RAILGUN_RAY.get(), player.level());
                var beamLength = profile.beamLength() * (proficiencyMilestone >= 2 ? 1.2f : 1.0f);
                var endPos = startPos.add(lookDir.scale(beamLength));
                railgunRay.setPos(startPos);
                railgunRay.setYRot(player.getYRot());
                railgunRay.setXRot(player.getXRot());
                var originalSource = new DamageSource(
                        railgunRay.level().damageSources().damageTypes.getOrThrow(DamageTypes.MOB_ATTACK),
                        railgunRay,
                        player
                );
                var damageSource = SkillDamageSource.from(originalSource, skill);
                var system = AbilitySystemServer.getSystem(player);
                var damage = calculateDamage(
                        system.getPlayerAbilityPowerMultiplier(player.getUUID()),
                        system.getPlayerDamageMultiplier(player.getUUID())
                ) * profile.damageMultiplier();
                var hitIndex = new java.util.concurrent.atomic.AtomicInteger();
                var shockTriggered = new java.util.concurrent.atomic.AtomicBoolean();
                var payload = LinearAttackPayload.builder(
                                player,
                                skill,
                                damageSource,
                                beamRadius
                        )
                        .damage(_ -> damage * (proficiencyMilestone >= 2 && hitIndex.getAndIncrement() > 0
                                ? 0.6f : 1.0f))
                        .onHit((target, _, hurt) -> {
                            if (hurt && proficiencyMilestone >= 3 && shockTriggered.compareAndSet(false, true)) {
                                triggerImpactShock(player, target.position(), damage * 0.25f, target);
                            }
                        })
                        .build();
                var resolved = LinearReflectionResolver.resolve(
                        player.level(),
                        new LinearSegment(startPos, endPos),
                        payload
                );
                railgunRay.setBeamPath(
                        (float) resolved.original().length(),
                        profile.beamWidthMultiplier(),
                        resolved.isReflected(),
                        (float) resolved.outbound().length(),
                        (float) resolved.returnVisualLength(),
                        resolved.returnSegment()
                                .map(LinearSegment::direction)
                                .orElse(Vec3.ZERO)
                );
                player.level().addFreshEntity(railgunRay);
                ElectromasterArcEffects.spawnBeamCoils(player.level(), resolved.outbound());
                resolved.returnSegment().ifPresent(segment ->
                        ElectromasterArcEffects.spawnBeamCoils(player.level(), segment));

                if (DestroyBlocksSetting.canDestroyBlocks(player, skill)) {
                    destroyBlocksAlongSegment(resolved.outbound(), player, beamRadius);
                }
                var outboundResult = LinearAttackExecutor.executeOutbound(
                        player.level(),
                        resolved,
                        payload
                );
                if (DestroyBlocksSetting.canDestroyBlocks(player, skill)) {
                    resolved.returnSegment().ifPresent(returnSegment -> resolved.reflectionCandidate()
                            .ifPresent(candidate -> destroyBlocksAlongSegment(
                                    returnSegment,
                                    candidate.reflector(),
                                    beamRadius
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
            }

            private int minimumChargeTicks(AmmoKind kind) {
                return proficiencyMilestone >= 1
                        ? Math.max(0, (int) Math.ceil(kind.minimumChargeTicks() * 0.9))
                        : kind.minimumChargeTicks();
            }

            private static void triggerImpactShock(ServerPlayer owner, Vec3 center, float damage,
                                                   net.minecraft.world.entity.Entity primary) {
                var source = SkillDamageSource.of(owner, Skills.RAILGUN.get());
                for (var target : owner.level().getEntities(owner, new AABB(center, center).inflate(4.0),
                        entity -> entity.isAlive() && entity != primary && !owner.isAlliedTo(entity))) {
                    target.hurtServer(owner.level(), source, damage);
                    var away = target.position().subtract(center);
                    if (away.lengthSqr() > 1.0e-8) {
                        target.setDeltaMovement(target.getDeltaMovement()
                                .add(away.normalize().scale(1.1))
                                .add(0, 0.25, 0));
                        target.hurtMarked = true;
                    }
                }
            }

            public void end() {
                if (ended) return;
                ended = true;
                unregister();
            }

        }

        private static final class ReleaseVisualContext extends ServerContext {
            private final boolean rightHand;
            private final boolean mainHandRight;
            private final ResourceKey<Level> dimension;
            private int ticks;
            private boolean ended;

            private ReleaseVisualContext(
                    ServerPlayer player,
                    boolean rightHand,
                    boolean mainHandRight
            ) {
                super(player);
                this.rightHand = rightHand;
                this.mainHandRight = mainHandRight;
                dimension = player.level().dimension();
                player.setData(AttachmentTypes.RAILGUN_DATA,
                        new Data(rightHand, mainHandRight, 0, true, false));
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
                player.setData(AttachmentTypes.RAILGUN_DATA,
                        new Data(rightHand, mainHandRight, ticks, true, false));
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
                AmmoKind kind,
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

    public record Data(
            boolean rightHand,
            boolean mainHandRight,
            int ticks,
            boolean released,
            boolean coinReturnHint
    ) {
        public static final StreamCodec<ByteBuf, Data> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Data::rightHand,
                ByteBufCodecs.BOOL, Data::mainHandRight,
                ByteBufCodecs.INT, Data::ticks,
                ByteBufCodecs.BOOL, Data::released,
                ByteBufCodecs.BOOL, Data::coinReturnHint,
                Data::new
        );
        private static final Data DEFAULT = new Data(true, true, 0, false, false);

        public static Data getDefault() {
            return DEFAULT;
        }
    }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onCoinPickup(ItemEntityPickupEvent.Post event) {
            if (!(event.getPlayer() instanceof ServerPlayer player)) return;
            if (!event.getOriginalStack().is(Items.COIN.get())) return;
            if (!player.getUUID().equals(event.getItemEntity().getTarget())) return;
            if (!player.getMainHandItem().is(Items.COIN.get())) return;
            Server.onCoinReturned(player);
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

    @PacketTarget(ThreadType.SERVER)
    public static final class EndPacket extends Packet<ServerGamePacketListenerImpl, EndPacket> {
        public static final EndPacket INSTANCE = new EndPacket();
        public static final StreamCodec<ByteBuf, EndPacket> CODEC = StreamCodec.unit(INSTANCE);

        private EndPacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, EndPacket> getPacketType() {
            return PacketTypes.RAILGUN_END_CHARGE.get();
        }
    }
}
