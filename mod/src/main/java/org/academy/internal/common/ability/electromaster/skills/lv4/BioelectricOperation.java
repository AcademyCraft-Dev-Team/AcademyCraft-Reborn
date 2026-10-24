package org.academy.internal.common.ability.electromaster.skills.lv4;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.render.post.GlowEffect;
import org.academy.api.client.render.vfxgraph.runtime.ActiveEffect;
import org.academy.api.client.render.vfxgraph.runtime.FirstPersonVfxEffect;
import org.academy.api.client.render.vfxgraph.runtime.VfxGraphManager;
import org.academy.api.client.render.vfxgraph.shape.FirstPersonHandSurfaceSampler;
import org.academy.api.client.render.vfxgraph.shape.HumanoidSurfaceSampler;
import org.academy.api.client.resources.R;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.DevCondition;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.ability.SyncTypes;
import org.academy.api.common.ability.data.SkillData;
import org.academy.api.common.ability.data.SkillDataSerializer;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.common.util.MathUtil;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.effect.TimedSkillEffectRuntime;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.skilldata.BioelectricSurgeData;
import org.academy.internal.server.ability.AbilitySystemServer;
import org.jspecify.annotations.Nullable;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public final class BioelectricOperation extends Skill {
    private static final Identifier MODIFIER_ID = AcademyCraft.academy(SkillNames.BIOELECTRIC_OPERATION);
    private static final String LEGACY_BIOELECTRIC_SURGE = "academy:bioelectric_surge";

    static {
        SkillDataSerializer.registerType(BioelectricSurgeData.ID, BioelectricSurgeData.class);
    }

    public BioelectricOperation() {
        super(Builder
                .of(AbilityCategories.ELECTROMASTER.get())
                .level(AbilityLevel.LEVEL4)
                .energyCost(60_000)
                .passive()
                .initiallyDisabled()
                .maintenanceCost(40)
                .iterationTicks(20)
                .maxStacks(NO_STACK_LIMIT)
                .dependsOn(Skills.ELECTRICAL_CONTACT)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL4))
        );
    }

    static double getAttackDamageBonus(float abilityPower) {
        return 4.0 * Math.max(0, abilityPower);
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        MisakaNetworkClient.NETWORK_MANAGER.register(SurfacePacket.Handler.class);
        InputSystem.addKeyBinding(
                Client.KEY_NAME_TOGGLE,
                Client.CONFIG.getKeyBinding(
                        Client.KEY_NAME_TOGGLE,
                        InputSystem.combo(
                                InputSystem.InputType.KEYBOARD,
                                InputConstants.KEY_Y,
                                InputConstants.RELEASE,
                                0
                        )
                ),
                _ -> Client.toggle()
        );
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.ELECTROMASTER.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.BIOELECTRIC_OPERATION.get(),
                        List.of(),
                        R.textures.bioelectric_operation_icon,
                        184,
                        46
                )
        );
        public static final String KEY_NAME_TOGGLE = SkillNames.BIOELECTRIC_OPERATION + "_toggle";
        public static Config CONFIG = new Config();

        private Client() {
        }

        private static void toggle() {
            if (!AbilitySystemClient.beginToggleRequest(Skills.BIOELECTRIC_OPERATION.get())) return;
            MisakaNetworkClient.send(TogglePacket.INSTANCE);
        }

        @EventBusSubscriber(Dist.CLIENT)
        public static final class SurfaceVisuals {
            private static final Identifier ASSET = AcademyCraft.academy("vfxgraph/bioelectric_surface");
            private static final long LEASE_TICKS = 40L;
            private static final int MAX_EFFECTS = 64;
            private static final Map<Integer, Visual> VISUALS = new HashMap<>();
            private static @Nullable ClientLevel world;

            private SurfaceVisuals() {
            }

            static void accept(SurfacePacket packet) {
                var minecraft = Minecraft.getInstance();
                if (!minecraft.isSameThread()) {
                    minecraft.execute(() -> accept(packet));
                    return;
                }
                resetWorld();
                if (world == null || !world.dimension().identifier().equals(packet.dimension)) return;
                if (!packet.active) {
                    remove(packet.entityId);
                    return;
                }
                if (!(world.getEntity(packet.entityId) instanceof LivingEntity subject)) {
                    remove(packet.entityId);
                    return;
                }
                var visual = VISUALS.get(packet.entityId);
                if (visual != null && (visual.subject != subject || visual.effect.isStopped())) {
                    remove(packet.entityId);
                    visual = null;
                }
                if (visual == null) {
                    if (VISUALS.size() >= MAX_EFFECTS) {
                        var iterator = VISUALS.entrySet().iterator();
                        if (iterator.hasNext()) {
                            iterator.next().getValue().effect.stop();
                            iterator.remove();
                        }
                    }
                    try {
                        var effect = VfxGraphManager.INSTANCE.spawn(ASSET, subject.position().toVector3f());
                        effect.follow(subject);
                        effect.setRenderDistance(64f);
                        effect.bindSurfaceSampler("player", new HumanoidSurfaceSampler(packet.entityId));
                        effect.bindFrame((active, camera, partialTick) -> {
                            var firstPerson = subject == minecraft.getCameraEntity()
                                    && minecraft.options.getCameraType().isFirstPerson();
                            active.setFrameVisible(!firstPerson);
                            active.setCullingSphere(active.position(), Math.max(1f, subject.getBbWidth() + 0.6f));
                            return subject.isAlive() && subject.level() == world;
                        });
                        visual = new Visual(subject, effect, world.getGameTime());
                        VISUALS.put(packet.entityId, visual);
                    } catch (RuntimeException failure) {
                        AcademyCraft.getLogger().error("Unable to create bioelectric surface effect", failure);
                        return;
                    }
                }
                visual.lease = world.getGameTime();
                var player = minecraft.player;
                if (player != null && player.getId() == packet.entityId) HandVisuals.enable();
            }

            @SubscribeEvent
            public static void tick(ClientTickEvent.Post event) {
                resetWorld();
                if (world == null) {
                    VISUALS.values().forEach(visual -> visual.effect.stop());
                    VISUALS.clear();
                    return;
                }
                var now = world.getGameTime();
                var iterator = VISUALS.entrySet().iterator();
                while (iterator.hasNext()) {
                    var visual = iterator.next().getValue();
                    if (visual.effect.isStopped() || now - visual.lease > LEASE_TICKS) {
                        visual.effect.stop();
                        iterator.remove();
                    }
                }
                var player = Minecraft.getInstance().player;
                if (player == null || !VISUALS.containsKey(player.getId())) HandVisuals.disable();
            }

            private static void resetWorld() {
                var next = Minecraft.getInstance().level;
                if (next == world) return;
                VISUALS.values().forEach(visual -> visual.effect.stop());
                VISUALS.clear();
                HandVisuals.disable();
                world = next;
            }

            private static void remove(int entityId) {
                var visual = VISUALS.remove(entityId);
                if (visual != null) visual.effect.stop();
                var player = Minecraft.getInstance().player;
                if (player != null && player.getId() == entityId) HandVisuals.disable();
            }

            private static final class Visual {
                private final LivingEntity subject;
                private final ActiveEffect effect;
                private long lease;

                private Visual(LivingEntity subject, ActiveEffect effect, long lease) {
                    this.subject = subject;
                    this.effect = effect;
                    this.lease = lease;
                }
            }
        }

        public static final class HandVisuals {
            private static final Identifier ASSET = AcademyCraft.academy("vfxgraph/bioelectric_hand");
            private static boolean enabled;
            private static @Nullable FirstPersonVfxEffect effect;

            private HandVisuals() {
            }

            static void enable() {
                enabled = true;
            }

            static void disable() {
                enabled = false;
                if (effect != null) {
                    effect.close();
                    effect = null;
                }
            }

            public static void render(GpuTextureView target, GpuTextureView depth, CameraRenderState state) {
                var minecraft = Minecraft.getInstance();
                if (!enabled || minecraft.player == null
                        || !minecraft.options.getCameraType().isFirstPerson()) return;
                if (effect == null) {
                    try {
                        effect = new FirstPersonVfxEffect(ASSET, "hand",
                                new FirstPersonHandSurfaceSampler(minecraft.player.getId()));
                    } catch (RuntimeException failure) {
                        AcademyCraft.getLogger().error("Unable to create first-person bioelectric effect", failure);
                        enabled = false;
                        return;
                    }
                }
                effect.render(target, depth, state);
            }

            public static void renderGlow(GpuTextureView glowTarget, GpuTextureView glowDepth,
                                          CameraRenderState state) {
                var minecraft = Minecraft.getInstance();
                if (!enabled || effect == null || glowTarget == null || glowDepth == null || minecraft.player == null
                        || !minecraft.options.getCameraType().isFirstPerson()) return;
                effect.renderGlow(glowTarget, glowDepth, state);
            }

            public static void requestGlow() {
                var minecraft = Minecraft.getInstance();
                if (enabled && minecraft.player != null
                        && minecraft.options.getCameraType().isFirstPerson()) {
                    GlowEffect.requestGlow();
                }
            }
        }

        public static final class Config extends KeyBindingConfig {
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
        private Server() {
        }

        @SubscribePacket
        public static void handle(TogglePacket packet) {
            Skills.BIOELECTRIC_OPERATION.get().toggle(packet.getPacketListener().getPlayer());
        }
    }

    @EventBusSubscriber
    public static final class Events {
        private static final Set<ServerPlayer> MIGRATED_PLAYERS =
                Collections.newSetFromMap(new WeakHashMap<>());
        private static final Map<ServerPlayer, Boolean> SURFACE_STATE = new WeakHashMap<>();

        private Events() {
        }

        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;

            migrateLegacySkill(player);
            var skill = Skills.BIOELECTRIC_OPERATION.get();
            var system = AbilitySystemServer.getSystem(player);
            var enabled = skill.isEnabled(player) && player.isAlive() && !player.hasDisconnected();
            if (enabled) {
                enabled = system.ensurePermanentOccupation(
                        player.getUUID(),
                        skill.getMaintenanceCost(player),
                        skill
                );
                if (!enabled) {
                    system.toggleSkill(player.getUUID(), skill.getKeyString());
                }
            }

            var milestone = skill.getEffectiveProficiencyMilestone(player);
            var overload = false;
            if (enabled && milestone >= 3 && player.getHealth() < player.getMaxHealth() * 0.3f) {
                var now = player.level().getGameTime();
                if (player.tickCount % 20 == 0 && system.tryTimedOccupation(
                        player.getUUID(), 5.0f, skill, 10)) {
                    TimedSkillEffectRuntime.put(player, player.getUUID(), skill,
                            "bioelectric_overload", 20, 1.0f);
                }
                overload = TimedSkillEffectRuntime.get(player.getUUID(), player.getUUID(), skill,
                        "bioelectric_overload", now).isPresent();
            }

            var power = enabled
                    ? system.getPlayerAbilityPowerMultiplier(player.getUUID())
                    : 0;
            var surfaceActive = SURFACE_STATE.getOrDefault(player, false);
            if (enabled && (player.tickCount % 10 == 0 || !surfaceActive)) {
                SurfacePacket.broadcast(player, true);
                SURFACE_STATE.put(player, true);
            } else if (!enabled && surfaceActive) {
                SurfacePacket.broadcast(player, false);
                SURFACE_STATE.put(player, false);
            }
            var nonDamageScale = milestone >= 2 ? 1.2 : 1.0;
            syncModifier(player.getAttribute(Attributes.MOVEMENT_SPEED),
                    0.1 * nonDamageScale + (overload ? 0.015 : 0.0), enabled);
            syncModifier(player.getAttribute(Attributes.STEP_HEIGHT), 0.4 * nonDamageScale, enabled);
            syncModifier(player.getAttribute(Attributes.MOVEMENT_EFFICIENCY), nonDamageScale, enabled);
            syncModifier(player.getAttribute(Attributes.JUMP_STRENGTH), 0.58 * nonDamageScale, enabled);
            syncModifier(player.getAttribute(Attributes.ATTACK_SPEED),
                    2.4 * nonDamageScale + (overload ? 0.36 : 0.0), enabled);
            syncModifier(player.getAttribute(Attributes.ATTACK_DAMAGE), getAttackDamageBonus(power), enabled);
            syncModifier(player.getAttribute(Attributes.BLOCK_BREAK_SPEED), 0.5 * nonDamageScale, enabled);
            syncModifier(player.getAttribute(Attributes.SAFE_FALL_DISTANCE), 10.0 * nonDamageScale, enabled);
        }

        private static void migrateLegacySkill(ServerPlayer player) {
            if (MIGRATED_PLAYERS.contains(player)) return;
            var system = AbilitySystemServer.getSystem(player);
            var playerData = system.getPlayerData(player.getUUID());
            var map = playerData.getMutableSkillDataMap();
            var legacy = map.remove(LEGACY_BIOELECTRIC_SURGE);
            if (legacy != null) {
                var skill = Skills.BIOELECTRIC_OPERATION.get();
                var target = map.get(skill.getKeyString());
                if (target == null) {
                    target = skill.createData();
                    map.put(skill.getKeyString(), target);
                }
                mergeProgress(target, legacy);
                target.setEnabled(target.isEnabled() || legacy.isEnabled());
                playerData.markDirty();
                system.releaseMaintenanceOccupation(player.getUUID(), LEGACY_BIOELECTRIC_SURGE);
                system.getSyncManager().schedulePlayerSync(player.getUUID(), SyncTypes.SKILL_DATA);
            }
            MIGRATED_PLAYERS.add(player);
        }

        private static void mergeProgress(SkillData target, SkillData legacy) {
            target.setProficiency(Math.max(target.getProficiency(), legacy.getProficiency()));
        }

        private static void syncModifier(@Nullable AttributeInstance attribute, double amount, boolean enabled) {
            if (attribute == null) return;
            var current = attribute.getModifier(MODIFIER_ID);
            if (!enabled) {
                if (current != null) attribute.removeModifier(MODIFIER_ID);
                return;
            }
            if (current != null && Double.compare(current.amount(), amount) == 0) return;
            if (current != null) attribute.removeModifier(MODIFIER_ID);
            attribute.addTransientModifier(new AttributeModifier(
                    MODIFIER_ID,
                    amount,
                    AttributeModifier.Operation.ADD_VALUE
            ));
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
            return PacketTypes.BIOELECTRIC_OPERATION_TOGGLE.get();
        }
    }

    @PacketTarget(ThreadType.CLIENT)
    public static final class SurfacePacket extends Packet<ClientPacketListener, SurfacePacket> {
        public static final StreamCodec<ByteBuf, SurfacePacket> CODEC = StreamCodec.of(
                (buffer, packet) -> {
                    Identifier.STREAM_CODEC.encode(buffer, packet.dimension);
                    ByteBufCodecs.VAR_INT.encode(buffer, packet.entityId);
                    buffer.writeBoolean(packet.active);
                },
                buffer -> new SurfacePacket(
                        Identifier.STREAM_CODEC.decode(buffer),
                        ByteBufCodecs.VAR_INT.decode(buffer),
                        buffer.readBoolean()
                )
        );

        private final Identifier dimension;
        private final int entityId;
        private final boolean active;

        private SurfacePacket(Identifier dimension, int entityId, boolean active) {
            this.dimension = dimension;
            this.entityId = entityId;
            this.active = active;
        }

        public static void broadcast(ServerPlayer source, boolean active) {
            if (!(source.level() instanceof ServerLevel level)) return;
            var dimension = level.dimension().identifier();
            var packet = new SurfacePacket(dimension, source.getId(), active);
            var center = source.position();
            var range = 64.0 * 64.0;
            for (var observer : level.players()) {
                if (observer == source || observer.position().distanceToSqr(center) <= range) {
                    MisakaNetworkServer.send(observer, packet);
                }
            }
        }

        @Override
        public PacketType<ClientPacketListener, SurfacePacket> getPacketType() {
            return PacketTypes.BIOELECTRIC_OPERATION_SURFACE.get();
        }

        public static final class Handler {
            private Handler() {
            }

            @SubscribePacket
            public static void handle(SurfacePacket packet) {
                Client.SurfaceVisuals.accept(packet);
            }
        }
    }
}
