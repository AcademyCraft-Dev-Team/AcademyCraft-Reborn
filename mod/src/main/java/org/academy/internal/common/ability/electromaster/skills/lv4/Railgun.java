package org.academy.internal.common.ability.electromaster.skills.lv4;

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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.vfxgraph.render.GraphCamera;
import org.academy.api.client.render.vfxgraph.runtime.ActiveEffect;
import org.academy.api.client.render.vfxgraph.runtime.VfxGraphManager;
import org.academy.api.client.resources.R;
import org.academy.api.client.sync.ClientSyncManager;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.DevCondition;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.ability.ServerContext;
import org.academy.api.server.sync.DataSyncManager;
import org.academy.api.server.sync.ServerSyncManager;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.client.render.vfxgraph.FrameContext;
import org.academy.internal.client.render.vfxgraph.TickingVfx;
import org.academy.internal.client.render.vfxgraph.VfxContexts;
import org.academy.internal.client.renderer.entity.ReflectedBeamVisualGeometry;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.accelerator.Accelerator;
import org.academy.internal.common.ability.electromaster.skills.lv2.ThunderLance;
import org.academy.internal.common.ability.shared.linear.LinearAttackExecutor;
import org.academy.internal.common.ability.shared.linear.LinearAttackPayload;
import org.academy.internal.common.ability.shared.linear.LinearReflectionResolver;
import org.academy.internal.common.ability.shared.linear.LinearSegment;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.sounds.SoundEvents;
import org.academy.internal.common.sync.DataTypes;
import org.academy.internal.common.sync.SyncKeys;
import org.academy.internal.common.util.LevelUtil;
import org.academy.internal.common.world.damagesource.DestroyBlocksSetting;
import org.academy.internal.common.world.damagesource.PvpSetting;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.projectile.ThrownCoin;
import org.academy.internal.common.world.entity.skill.RailgunRay;
import org.academy.internal.common.world.item.Items;
import org.academy.internal.server.ability.AbilitySystemServer;
import org.academy.internal.server.team.TeamRelations;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.misaka.MisakaNetworkClient.send;


public final class Railgun extends Skill {
    public static final int CHARGE_TIME = 30;
    public static final int RELEASE_VISUAL_TICKS = 12;
    public static final int COIN_RETURN_HINT_TICKS = 12;

    public Railgun() {
        super(Builder
                .of(AbilityCategories.ELECTROMASTER.get())
                .damage()
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
        Railgun.Client.RailgunVfxClient.register();
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

        float damageRadius() {
            return 2.0f * beamWidthMultiplier;
        }

        float destructionRadius() {
            return 1.0f * beamWidthMultiplier;
        }

        float damageMultiplier() {
            return damageMultiplier;
        }
    }

    public static final class Client {
        @EventBusSubscriber(Dist.CLIENT)
        public static final class RailgunVfxClient {
            private static final Map<RailgunRay, RailgunShotVfx> SHOTS = new IdentityHashMap<>();

            private RailgunVfxClient() {
            }

            public static void register() {
                Railgun.Client.RailgunChargeVfxClient.register();
            }

            @SubscribeEvent
            public static void onEntityJoin(EntityJoinLevelEvent event) {
                if (!event.getLevel().isClientSide() || !(event.getEntity() instanceof RailgunRay ray)) return;
                if (SHOTS.containsKey(ray)) return;
                var shot = new RailgunShotVfx(ray);
                SHOTS.put(ray, shot);
                VfxContexts.register(shot);
            }

            @SubscribeEvent
            public static void onEntityLeave(EntityLeaveLevelEvent event) {
                if (!event.getLevel().isClientSide() || !(event.getEntity() instanceof RailgunRay ray)) return;
                var shot = SHOTS.remove(ray);
                if (shot != null) shot.stop();
            }

            @SubscribeEvent
            public static void onLevelUnload(LevelEvent.Unload event) {
                if (!event.getLevel().isClientSide()) return;
                SHOTS.values().forEach(RailgunShotVfx::stop);
                SHOTS.clear();
            }
        }

        /**
         * Charge and coin-return hand attachments. All geometry and materials are authored in VFXGraph.
         */
        @EventBusSubscriber(Dist.CLIENT)
        public static final class RailgunChargeVfxClient {
            private static final Map<Player, Hands> ACTIVE = new IdentityHashMap<>();
            private static boolean registered;

            private static final class Hands {
                final ActiveEffect charge;
                @Nullable ActiveEffect hint;

                Hands(Player player) {
                    charge = create(player, false);
                }

                void stop() {
                    charge.stop();
                    if (hint != null) hint.stop();
                }
            }

            private RailgunChargeVfxClient() {
            }

            public static void register() {
                registered = true;
            }

            @SubscribeEvent
            public static void tick(ClientTickEvent.Post event) {
                if (!registered) return;
                var mc = Minecraft.getInstance();
                if (mc.level == null) {
                    clear();
                    return;
                }
                ACTIVE.entrySet().removeIf(entry -> {
                    if (charging(entry.getKey())) return false;
                    entry.getValue().stop();
                    return true;
                });
                for (var player : mc.level.players()) {
                    if (!charging(player)) continue;
                    var hands = ACTIVE.computeIfAbsent(player, Hands::new);
                    var data = player.getExistingDataOrNull(AttachmentTypes.RAILGUN_DATA);
                    if (data != null && data.coinReturnHint()) {
                        if (hands.hint == null || hands.hint.isStopped()) hands.hint = create(player, true);
                    } else if (hands.hint != null) {
                        hands.hint.stop();
                        hands.hint = null;
                    }
                }
            }

            private static boolean charging(Player player) {
                if (player.isRemoved() || player.level() != Minecraft.getInstance().level) return false;
                var data = player.getExistingDataOrNull(AttachmentTypes.RAILGUN_DATA);
                return data != null && !data.released();
            }

            private static ActiveEffect create(Player player, boolean hint) {
                var effect = VfxGraphManager.INSTANCE.spawn(AcademyCraft.academy("vfxgraph/railgun_charge"), player.position().toVector3f());
                effect.bind("seed", () -> Value.of((float) (player.getUUID().getLeastSignificantBits() & 0xFFFFFFL)));
                effect.bind("hint", () -> Value.of(hint ? 1f : 0f));
                effect.bindFrame((active, camera, partialTick) -> attach(active, player, hint, camera, partialTick));
                effect.setRenderDistance(96);
                return effect;
            }

            private static boolean attach(ActiveEffect effect, Player player, boolean hint, GraphCamera camera, float partialTick) {
                if (!charging(player)) return false;
                var data = player.getExistingDataOrNull(AttachmentTypes.RAILGUN_DATA);
                if (data == null || hint && !data.coinReturnHint()) return false;
                var mc = Minecraft.getInstance();
                boolean firstPerson = player == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
                boolean right = hint ? data.mainHandRight() : data.rightHand();
                Matrix4f transform;
                if (firstPerson) {
                    transform = new Matrix4f(camera.viewRotation()).invert();
                    transform.setTranslation(camera.position());
                    transform.translate(right ? 0.30f : -0.30f, -0.22f, -0.46f).scale(0.90f);
                } else {
                    var root = Accelerator.Client.WingAvatarRegistry.entries().get(player.getId());
                    if (root == null) {
                        effect.setFrameVisible(false);
                        return true;
                    }
                    transform = new Matrix4f(root);
                    transform.m30(transform.m30() + camera.position().x);
                    transform.m31(transform.m31() + camera.position().y);
                    transform.m32(transform.m32() + camera.position().z);
                    transform.translate(right ? -0.38f : 0.38f, 0.55f, -0.24f);
                }
                effect.setFrameVisible(true);
                effect.setTransform(transform);
                effect.setCullingSphere(effect.position(), 1);
                effect.effect().setLiveParam("time", Value.of((player.tickCount + partialTick) / 20f));
                effect.effect().setLiveParam("strength", Value.of(hint ? 0.55f
                        : Math.clamp((data.ticks() + partialTick) / Math.max(1, Railgun.CHARGE_TIME), 0.15f, 1)));
                return true;
            }

            @SubscribeEvent
            public static void unload(LevelEvent.Unload event) {
                if (event.getLevel().isClientSide()) clear();
            }

            private static void clear() {
                ACTIVE.values().forEach(Hands::stop);
                ACTIVE.clear();
            }
        }

        /**
         * Maps the server's resolved beam segments to editor-authored visuals on one game clock.
         */
        static final class RailgunShotVfx implements TickingVfx {
            private static final Identifier ASSET = Identifier.fromNamespaceAndPath("academy", "vfxgraph/railgun_shot");
            private final RailgunRay ray;
            private @Nullable ActiveEffect outgoing;
            private @Nullable ActiveEffect reflected;
            private float ageSeconds;
            private float detail = 1;
            private boolean stopped;

            RailgunShotVfx(RailgunRay ray) {
                this.ray = ray;
            }

            @Override
            public void tick(FrameContext ctx) {
                if (ray.isRemoved() || ray.level() != Minecraft.getInstance().level) {
                    stop();
                    return;
                }
                ageSeconds = (ray.tickCount + ctx.partialTick()) / 20f;
                var origin = ray.position();
                var direction = Vec3.directionFromRotation(ray.getXRot(), ray.getYRot()).normalize();
                float originalLength = ReflectedBeamVisualGeometry.safeLength(ray.getBeamLength());
                float length = ray.isReflectionActive()
                        ? Math.min(originalLength, ReflectedBeamVisualGeometry.safeLength(ray.getReflectionDistance()))
                        : originalLength;
                detail = ctx.cameraPos().distance(origin.toVector3f()) > 72 ? 0.35f : 1;
                if (outgoing == null) outgoing = create(true);
                segment(outgoing, origin, direction, length, ctx.cameraPos());
                if (ray.isReflectionActive()) {
                    if (reflected == null) reflected = create(false);
                    segment(reflected, origin.add(direction.scale(length)), ray.getReflectionReturnDirection(),
                            ReflectedBeamVisualGeometry.safeLength(ray.getReflectionReturnLength()), ctx.cameraPos());
                } else if (reflected != null) {
                    reflected.stop();
                    reflected = null;
                }
            }

            private ActiveEffect create(boolean muzzle) {
                var effect = VfxGraphManager.INSTANCE.spawn(ASSET, ray.position().toVector3f());
                effect.bind("time", () -> Value.of(ageSeconds));
                effect.bind("width_scale", () -> Value.of(ray.getBeamWidthMultiplier()));
                effect.bind("seed", () -> Value.of((float) (ray.getUUID().getLeastSignificantBits() & 0xFFFFFFL)));
                effect.bind("detail", () -> Value.of(detail));
                effect.bind("muzzle", () -> Value.of(muzzle ? 1f : 0f));
                effect.setMinimumFarPlane(384);
                effect.setRenderDistance(256);
                return effect;
            }

            private void segment(ActiveEffect effect, Vec3 start, Vec3 direction, float length, Vector3f camera) {
                var unit = direction.normalize().toVector3f();
                boolean valid = unit.isFinite() && unit.lengthSquared() > 1.0e-12f && length > 0;
                effect.setPosition(start.toVector3f());
                if (valid) effect.setRotation(new Quaternionf().rotationTo(new Vector3f(0, 0, 1), unit));
                effect.effect().setLiveParam("length", Value.of(valid ? length : 0));
                float proximity = Math.clamp((3f - camera.distance(start.toVector3f())) / 1.5f, 0, 1);
                effect.effect().setLiveParam("view_near_origin", Value.of(proximity));
                // Include the entire segment, so a distant muzzle cannot cull a beam crossing the camera.
                var center = start.toVector3f().fma(valid ? length * 0.5f : 0, unit);
                effect.setCullingSphere(center, length * 0.5f + 14 * Math.max(1, ray.getBeamWidthMultiplier()));
                effect.setMinimumFarPlane(Math.max(384, length + 256));
            }

            void stop() {
                stopped = true;
                if (outgoing != null) outgoing.stop();
                if (reflected != null) reflected.stop();
            }

            @Override
            public boolean isAlive() {
                return !stopped;
            }

            @Override
            public void onRemoved() {
                stop();
            }
        }

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
                var damageRadius = profile.damageRadius();
                var destructionRadius = profile.destructionRadius();
                var railgunRay = new RailgunRay(EntityTypes.RAILGUN_RAY.get(), player.level());
                var beamLength = skill.scaledRange(player,
                        profile.beamLength() * (proficiencyMilestone >= 2 ? 1.2f : 1.0f));
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
                var hitIndex = new AtomicInteger();
                var shockTriggered = new AtomicBoolean();
                var payload = LinearAttackPayload.builder(
                                player,
                                skill,
                                damageSource,
                                damageRadius
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
                        resolved.isRedirected(),
                        (float) resolved.outbound().length(),
                        (float) resolved.returnVisualLength(),
                        resolved.returnSegment()
                                .map(LinearSegment::direction)
                                .orElse(Vec3.ZERO)
                );
                player.level().addFreshEntity(railgunRay);

                if (DestroyBlocksSetting.canDestroyBlocks(player, skill)) {
                    destroyBlocksAlongSegment(resolved.outbound(), player, destructionRadius);
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
                                    destructionRadius
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
                                                   Entity primary) {
                var source = SkillDamageSource.of(owner, Skills.RAILGUN.get());
                for (var target : owner.level().getEntities(owner, new AABB(center, center).inflate(4.0),
                        entity -> entity.isAlive() && entity != primary && !TeamRelations.areAllied(owner, entity)
                                && !PvpSetting.shouldPrevent(owner, entity))) {
                    target.hurtServer(owner.level(), source, damage);
                    var away = target.position().subtract(center);
                    if (away.lengthSqr() > 1.0e-8) {
                        target.setDeltaMovement(target.getDeltaMovement()
                                .add(away.normalize().scale(1.1))
                                .add(0, 0.25, 0));
                        target.syncVelocity = true;
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

    @EventBusSubscriber
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
