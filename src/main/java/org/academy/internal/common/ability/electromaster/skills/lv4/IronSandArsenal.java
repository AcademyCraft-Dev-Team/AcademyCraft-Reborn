package org.academy.internal.common.ability.electromaster.skills.lv4;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.hud.ability.ToggleStatusHud;
import org.academy.api.client.input.InputSystem;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.ability.ServerContext;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.client.render.vfx.ElectromasterWeaponVfx;
import org.academy.internal.client.render.vfx.ElectromasterWeaponVfxClient;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.TimedSkillEffectRuntime;
import org.academy.internal.common.ability.electromaster.skills.lv3.MagneticWeapon;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.util.Mth;

public class IronSandArsenal extends Skill {
    public static final float PROXIMITY_DAMAGE = 4.0f;
    public static final float SWEEP_DAMAGE = 10.0f;
    public static final double PROXIMITY_RADIUS = 2.0;
    public static final double SWEEP_RADIUS = 12.0;
    private static final double SWEEP_HALF_ANGLE_COS = Mth.cos((60.0) * Mth.DEG_TO_RAD);
    private static final int HIT_COOLDOWN = 10;

    public IronSandArsenal() {
        super(Builder.of(AbilityCategories.ELECTROMASTER.get())
                .level(AbilityLevel.LEVEL4)
                .energyCost(60_000)
                .passive()
                .initiallyDisabled()
                .maintenanceCost(50)
                .iterationTicks(20)
                .dependsOn(Skills.MAGNETIC_WEAPON));
    }

    @Override
    public void initClient() {
        Client.init();
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(Client.KEY_TOGGLE, Client.CONFIG.getKeyBinding(Client.KEY_TOGGLE,
                        InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_G,
                                InputConstants.PRESS, InputConstants.MOD_ALT)),
                _ -> Client.onToggle());
        ToggleStatusHud.Companion.registerStateProvider(Skills.IRON_SAND_ARSENAL.get(), Client::isActive);
        ElectromasterWeaponVfxClient.register();
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final String KEY_TOGGLE = SkillNames.IRON_SAND_ARSENAL + "_toggle";
        public static Config CONFIG = new Config();
        private static boolean initialized;

        private static void init() {
            if (initialized) return;
            initialized = true;
            MisakaNetworkClient.NETWORK_MANAGER.register(Client.class);
            NeoForge.EVENT_BUS.register(Client.class);
        }

        private static void onToggle() {
            if (AbilitySystemClient.beginToggleRequest(Skills.IRON_SAND_ARSENAL.get())) {
                MisakaNetworkClient.send(TogglePacket.INSTANCE);
            }
        }

        private static boolean isActive() {
            var player = Minecraft.getInstance().player;
            return player != null && player.getData(AttachmentTypes.IRON_SAND_DATA.get()).active();
        }

        @SubscribePacket
        public static void handleSweepVisual(SweepVisualPacket packet) {
            ElectromasterWeaponVfx.enqueueIronSandSweep(packet.entityId());
        }

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            ElectromasterWeaponVfx.clientTick();
        }

        @SubscribeEvent
        public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
            ElectromasterWeaponVfx.clearSweeps();
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
        private static final Map<Player, Context> CONTEXT_MAP = createContextMap();

        private Server() {
        }

        public static float calculateDamage(float baseDamage, float playerMultiplier) {
            return Math.max(0.0f, baseDamage) * Math.max(0.0f, playerMultiplier);
        }

        public static boolean isActive(ServerPlayer player) {
            return CONTEXT_MAP.containsKey(player) && Skills.IRON_SAND_ARSENAL.get().isEnabled(player);
        }

        public static void forceDisable(ServerPlayer player) {
            var context = CONTEXT_MAP.remove(player);
            if (context != null) context.end(false);
            var skill = Skills.IRON_SAND_ARSENAL.get();
            if (skill.isEnabled(player)) skill.toggle(player);
            clearData(player);
        }

        public static void onEntitySwing(ServerPlayer player, InteractionHand hand) {
            if (hand != InteractionHand.MAIN_HAND) return;
            var context = CONTEXT_MAP.get(player);
            if (context != null) context.sweep();
        }

        @SubscribePacket
        public static void handleToggle(TogglePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            if (isActive(player)) {
                forceDisable(player);
                return;
            }
            MagneticWeapon.Server.forceDisable(player);
            var skill = Skills.IRON_SAND_ARSENAL.get();
            if (!skill.isEnabled(player)) skill.toggle(player);
            if (!skill.isEnabled(player)) return;
            var context = new Context(player);
            CONTEXT_MAP.put(player, context);
            AbilitySystemServer.registerContext(context);
        }

        private static void clearData(ServerPlayer player) {
            player.setData(AttachmentTypes.IRON_SAND_DATA.get(), Data.DEFAULT);
            player.syncData(AttachmentTypes.IRON_SAND_DATA.get());
        }
    }

    public static final class Context extends ServerContext {
        private final Map<Integer, Integer> hitCooldowns = new HashMap<>();
        private int sweepCooldown;
        private int swingTicks;
        private boolean ended;

        private Context(ServerPlayer player) {
            super(player);
            syncData();
        }

        @SubscribeEvent
        public void onTick(ServerTickEvent.Pre event) {
            var skill = Skills.IRON_SAND_ARSENAL.get();
            if (!skill.isEnabled(player) || !player.isAlive() || player.hasDisconnected()) {
                end(true);
                return;
            }
            var system = AbilitySystemServer.getSystem(player);
            if (!system.ensurePermanentOccupation(
                    player.getUUID(), skill.getMaintenanceCost(player), skill)) {
                end(true);
                return;
            }

            hitCooldowns.replaceAll((_, ticks) -> ticks - 1);
            hitCooldowns.values().removeIf(ticks -> ticks <= 0);
            if (sweepCooldown > 0) sweepCooldown--;
            if (swingTicks > 0) swingTicks++;
            if (swingTicks > 10) swingTicks = 0;

            if (player.level() instanceof ServerLevel level) {
                var multiplier = system.getPlayerDamageMultiplier(player.getUUID());
                var milestone = skill.getEffectiveProficiencyMilestone(player);
                var proximityRadius = milestone >= 2 ? PROXIMITY_RADIUS * 1.2 : PROXIMITY_RADIUS;
                for (var target : level.getEntitiesOfClass(
                        LivingEntity.class,
                        player.getBoundingBox().inflate(proximityRadius),
                        entity -> entity != player && entity.isAlive()
                                && entity instanceof Enemy && !player.isAlliedTo(entity)
                )) {
                    if (hitCooldowns.containsKey(target.getId())) continue;
                    if (target.hurtServer(level, SkillDamageSource.of(player, skill),
                            Server.calculateDamage(PROXIMITY_DAMAGE, multiplier))) {
                        if (milestone >= 3 && TimedSkillEffectRuntime.consume(player.getUUID(),
                                target.getUUID(), skill, "sweep_mark", level.getGameTime()).isPresent()) {
                            target.hurtServer(level, SkillDamageSource.of(player, skill),
                                    Server.calculateDamage(SWEEP_DAMAGE * 0.5f, multiplier));
                            var destination = player.position().add(player.getLookAngle().normalize().scale(2.0));
                            var pull = destination.subtract(target.position());
                            if (pull.lengthSqr() > 1.0e-8) target.setDeltaMovement(pull.normalize().scale(0.8));
                            target.hurtMarked = true;
                        }
                        var direction = target.position().subtract(player.position());
                        direction = new Vec3(direction.x, 0, direction.z);
                        if (direction.lengthSqr() > 1.0e-8) {
                            direction = direction.normalize().scale(0.8);
                            target.push(direction.x, 0.18, direction.z);
                        }
                    }
                    hitCooldowns.put(target.getId(), HIT_COOLDOWN);
                }
            }
            syncData();
        }

        private void sweep() {
            if (ended || sweepCooldown > 0 || !(player.level() instanceof ServerLevel level)) return;
            var forward = player.getLookAngle();
            forward = new Vec3(forward.x, 0, forward.z);
            if (forward.lengthSqr() <= 1.0e-8) forward = new Vec3(0, 0, 1);
            else forward = forward.normalize();
            var multiplier = AbilitySystemServer.getSystem(player)
                    .getPlayerDamageMultiplier(player.getUUID());
            var source = SkillDamageSource.of(player, Skills.IRON_SAND_ARSENAL.get());
            var skill = Skills.IRON_SAND_ARSENAL.get();
            var sweepRadius = skill.hasProficiencyMilestone(player, 2) ? SWEEP_RADIUS * 1.2 : SWEEP_RADIUS;
            var radiusSquared = sweepRadius * sweepRadius;
            for (var target : level.getEntitiesOfClass(
                    LivingEntity.class,
                    player.getBoundingBox().inflate(sweepRadius),
                    entity -> entity != player && entity.isAlive() && !player.isAlliedTo(entity)
            )) {
                var delta = target.position().subtract(player.position());
                var horizontal = new Vec3(delta.x, 0, delta.z);
                if (horizontal.lengthSqr() > radiusSquared || horizontal.lengthSqr() <= 1.0e-8) continue;
                if (forward.dot(horizontal.normalize()) < SWEEP_HALF_ANGLE_COS) continue;
                if (target.hurtServer(level, source, Server.calculateDamage(SWEEP_DAMAGE, multiplier))
                        && skill.hasProficiencyMilestone(player, 3)) {
                    TimedSkillEffectRuntime.put(player, target.getUUID(), skill, "sweep_mark", 80, 1.0f);
                }
            }
            sweepCooldown = HIT_COOLDOWN;
            swingTicks = 1;
            syncData();
            var packet = new SweepVisualPacket(player.getId());
            for (var observer : level.players()) {
                if (observer.distanceToSqr(player) <= 128.0 * 128.0) {
                    MisakaNetworkServer.send(observer, packet);
                }
            }
        }

        private void syncData() {
            var data = new Data(true, swingTicks);
            if (data.equals(player.getData(AttachmentTypes.IRON_SAND_DATA.get()))) return;
            player.setData(AttachmentTypes.IRON_SAND_DATA.get(), data);
            player.syncData(AttachmentTypes.IRON_SAND_DATA.get());
        }

        private void end(boolean disableSkill) {
            if (ended) return;
            ended = true;
            Server.CONTEXT_MAP.remove(player, this);
            if (disableSkill && Skills.IRON_SAND_ARSENAL.get().isEnabled(player)) {
                Skills.IRON_SAND_ARSENAL.get().toggle(player);
            }
            Server.clearData(player);
            unregister();
        }
    }

    public record Data(boolean active, int swingTicks) {
        public static final Data DEFAULT = new Data(false, 0);
        public static final StreamCodec<ByteBuf, Data> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Data::active,
                ByteBufCodecs.VAR_INT, Data::swingTicks,
                Data::new
        );
    }

    @PacketTarget(ThreadType.CLIENT)
    public static final class SweepVisualPacket extends Packet<ClientPacketListener, SweepVisualPacket> {
        public static final StreamCodec<ByteBuf, SweepVisualPacket> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, SweepVisualPacket::entityId,
                SweepVisualPacket::new
        );
        private final int entityId;

        public SweepVisualPacket(int entityId) {
            this.entityId = entityId;
        }

        public int entityId() {
            return entityId;
        }

        @Override
        public PacketType<ClientPacketListener, SweepVisualPacket> getPacketType() {
            return PacketTypes.IRON_SAND_ARSENAL_SWEEP_VISUAL.get();
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
            return PacketTypes.IRON_SAND_ARSENAL_TOGGLE.get();
        }
    }

    /**
     * Kept for protocol compatibility with older clients; forms no longer exist.
     */
    @PacketTarget(ThreadType.SERVER)
    public static final class FormSelectPacket extends Packet<ServerGamePacketListenerImpl, FormSelectPacket> {
        public static final FormSelectPacket INSTANCE = new FormSelectPacket();
        public static final StreamCodec<ByteBuf, FormSelectPacket> CODEC = StreamCodec.unit(INSTANCE);

        private FormSelectPacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, FormSelectPacket> getPacketType() {
            return PacketTypes.IRON_SAND_ARSENAL_FORM_SELECT.get();
        }
    }
}
