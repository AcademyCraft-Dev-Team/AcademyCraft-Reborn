package org.academy.internal.common.ability.aeromanip.skills.lv5;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
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
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.ProficiencyPolicy;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.aeromanip.AeromanipConfig;
import org.academy.internal.common.ability.aeromanip.AeromanipTargeting;
import org.academy.internal.common.ability.aeromanip.AeromanipVfx;
import org.academy.internal.common.ability.aeromanip.skills.lv2.BreathingBubble;
import org.academy.internal.common.ability.aeromanip.skills.lv4.VortexPull;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.world.damagesource.DamageTypes;
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

/** Toggleable player-centred vacuum that rapidly exhausts the air of drowning-capable targets. */
public final class VacuumDomain extends Skill {
    static final double RADIUS = 50.0;
    static final double MILESTONE_THREE_RADIUS = 100.0;
    static final int BASE_AIR_DRAIN_PER_PULSE = 40;
    static final int MILESTONE_TWO_AIR_DRAIN_PER_PULSE = 60;
    static final int DROWNING_AIR_THRESHOLD = -20;
    private static final int EFFECT_INTERVAL_TICKS = 2;
    private static final int DAMAGE_INTERVAL_TICKS = 10;
    private static final int VISUAL_INTERVAL_TICKS = 10;
    private static final float DAMAGE_FRACTION = 0.05f;

    public VacuumDomain() {
        super(Builder.of(AbilityCategories.AEROMANIP.get())
                .damage()
                .level(AbilityLevel.LEVEL5)
                .energyCost(100_000)
                .passive()
                .initiallyDisabled()
                .maintenanceCost(80)
                .iterationTicks(20)
                .maxStacks(NO_STACK_LIMIT)
                .dependsOn(Skills.VORTEX_PULL)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL5)));
    }

    static double radiusForMilestone(int milestone) {
        return milestone >= 3 ? MILESTONE_THREE_RADIUS : RADIUS;
    }

    static boolean isInsideDomain(Vec3 center, Vec3 target, double radius) {
        return target.distanceToSqr(center) <= radius * radius;
    }

    static int airSupplyAfterPulse(
            int currentAirSupply,
            int maxAirSupply,
            int drain,
            boolean protectedByBreathingBubble
    ) {
        if (protectedByBreathingBubble) return Math.max(0, maxAirSupply);
        return Math.max(DROWNING_AIR_THRESHOLD, currentAirSupply - Math.max(0, drain));
    }

    static boolean shouldDealDamage(int ticks, int airSupply) {
        return ticks > 0 && ticks % DAMAGE_INTERVAL_TICKS == 0
                && airSupply <= DROWNING_AIR_THRESHOLD;
    }

    static float baseDamage(float maxHealth) {
        return Math.max(0.0f, maxHealth) * DAMAGE_FRACTION;
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        var defaultBinding = InputSystem.combo(
                InputSystem.InputType.KEYBOARD,
                InputConstants.KEY_Y,
                InputConstants.RELEASE,
                0);
        var binding = Client.CONFIG.getKeyBinding(
                Client.KEY_NAME_TOGGLE,
                Client.CONFIG.getKeyBinding(Client.LEGACY_KEY_NAME_CAST, defaultBinding));
        InputSystem.addKeyBinding(Client.KEY_NAME_TOGGLE, binding, _ -> Client.toggle());
        ToggleStatusHud.Companion.registerStateProvider(
                Skills.VACUUM_DOMAIN.get(),
                () -> AbilitySystemClient.getSkillData(Skills.VACUUM_DOMAIN.get())
                        .map(data -> data.isEnabled())
                        .orElse(false));
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO =
                AbilitySystemClient.addSkillInfo(
                        AbilityCategories.AEROMANIP.get(),
                        new AbilitySystemClient.SkillInfo(
                                Skills.VACUUM_DOMAIN.get(),
                                List.of(VortexPull.Client.SKILL_INFO),
                                R.textures.vacuum_domain_icon,
                                130,
                                72));
        public static final String KEY_NAME_TOGGLE = SkillNames.VACUUM_DOMAIN + "_toggle";
        public static final String LEGACY_KEY_NAME_CAST = SkillNames.VACUUM_DOMAIN + "_cast";
        public static Config CONFIG = new Config();

        private Client() {
        }

        private static void toggle() {
            if (!AbilitySystemClient.beginToggleRequest(Skills.VACUUM_DOMAIN.get())) return;
            MisakaNetworkClient.send(TogglePacket.INSTANCE);
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

    public static final class Server {
        private static final Map<ServerPlayer, AeromanipResourceManager.UsageLease> ACTIVE =
                new WeakHashMap<>();

        private Server() {
        }

        @SubscribePacket
        public static void handle(TogglePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var skill = Skills.VACUUM_DOMAIN.get();
            skill.toggle(player);
            if (!skill.isEnabled(player)) stop(player);
        }

        private static void start(ServerPlayer player) {
            ACTIVE.computeIfAbsent(player, current -> AbilitySystemServer.getSystem(current)
                    .getAeromanipResourceManager().beginUse(current));
        }

        private static void stop(ServerPlayer player) {
            var lease = ACTIVE.remove(player);
            if (lease != null) lease.close();
        }

        private static void disable(ServerPlayer player, VacuumDomain skill) {
            stop(player);
            var system = AbilitySystemServer.getSystem(player);
            system.releaseMaintenanceOccupation(player.getUUID(), skill.getKeyString());
            if (skill.isEnabled(player)) system.toggleSkill(player.getUUID(), skill.getKeyString());
        }

        private static boolean canAffectTarget(ServerPlayer owner, LivingEntity target) {
            if (target == owner || !target.isAlive() || target.isRemoved()
                    || target.getMaxAirSupply() <= 0 || target.canBreatheUnderwater()) {
                return false;
            }
            if (target instanceof TamableAnimal animal && animal.isOwnedBy(owner)) return false;
            return AeromanipTargeting.canAffectNegatively(owner, target);
        }

        private static void applyDomain(ServerPlayer owner, VacuumDomain skill, int milestone) {
            if (!(owner.level() instanceof ServerLevel level)) return;
            var radius = radiusForMilestone(milestone)
                    * AeromanipConfig.rangeMultiplier(owner, SkillNames.VACUUM_DOMAIN);
            var center = owner.getBoundingBox().getCenter();
            var bounds = new AABB(
                    center.subtract(radius, radius, radius),
                    center.add(radius, radius, radius));
            var targets = level.getEntitiesOfClass(
                    LivingEntity.class,
                    bounds,
                    target -> canAffectTarget(owner, target)
                            && isInsideDomain(center, target.getBoundingBox().getCenter(), radius));
            var cap = ProficiencyPolicy.server(owner).maxBonusEntitiesPerTick();
            var system = AbilitySystemServer.getSystem(owner);
            var power = system.getPlayerAbilityPowerMultiplier(owner.getUUID())
                    * system.getPlayerDamageMultiplier(owner.getUUID());
            var damageSource = SkillDamageSource.of(
                    owner, skill, DamageTypes.VACUUM_SUFFOCATION);
            var drain = milestone >= 2
                    ? MILESTONE_TWO_AIR_DRAIN_PER_PULSE
                    : BASE_AIR_DRAIN_PER_PULSE;
            var handled = 0;
            for (var target : targets) {
                if (handled++ >= cap) break;
                var protectedByBubble = target instanceof ServerPlayer targetPlayer
                        && BreathingBubble.Server.isSustained(targetPlayer);
                var air = airSupplyAfterPulse(
                        target.getAirSupply(), target.getMaxAirSupply(), drain, protectedByBubble);
                target.setAirSupply(air);
                if (!protectedByBubble && shouldDealDamage(owner.tickCount, air)) {
                    target.invulnerableTime = 0;
                    var damage = baseDamage(target.getMaxHealth())
                            * AeromanipConfig.damageMultiplier(owner, SkillNames.VACUUM_DOMAIN)
                            * power;
                    target.hurtServer(level, damageSource, damage);
                }
            }
            spawnVisual(level, center, radius, owner.tickCount);
        }

        private static void spawnVisual(ServerLevel level, Vec3 center, double radius, int ticks) {
            if (ticks % VISUAL_INTERVAL_TICKS != 0) return;
            AeromanipVfx.vortex(level, center, Math.min(radius, 12.0));
        }
    }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            var skill = Skills.VACUUM_DOMAIN.get();
            var enabled = skill.isEnabled(player) && player.isAlive() && !player.hasDisconnected();
            if (enabled) {
                enabled = AbilitySystemServer.getSystem(player).ensurePermanentOccupation(
                        player.getUUID(),
                        skill.getMaintenanceCost(player)
                                * AeromanipConfig.cpMultiplier(player, SkillNames.VACUUM_DOMAIN),
                        skill);
            }
            if (enabled) {
                Server.start(player);
                var interval = Math.max(1, Math.round(AeromanipConfig.skillFloat(
                        player, SkillNames.VACUUM_DOMAIN,
                        "compressedAirIntervalTicks", 10.0f)));
                if (player.tickCount % interval == 0) {
                    var baseCost = Math.max(0.0f, AeromanipConfig.skillFloat(
                            player, SkillNames.VACUUM_DOMAIN,
                            "compressedAirPerInterval", 8.0f));
                    var airCost = skill.getEffectiveProficiencyMilestone(player) >= 1
                            ? baseCost * 0.75f
                            : baseCost;
                    enabled = skill.executeContinuousWithResource(
                            player, _ -> 0.0f, _ -> airCost, (_, _) -> { }, true);
                }
            }
            if (!enabled) {
                Server.disable(player, skill);
                return;
            }
            if (player.tickCount % EFFECT_INTERVAL_TICKS == 0) {
                Server.applyDomain(
                        player, skill, skill.getEffectiveProficiencyMilestone(player));
            }
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class TogglePacket
            extends Packet<ServerGamePacketListenerImpl, TogglePacket> {
        public static final TogglePacket INSTANCE = new TogglePacket();
        public static final StreamCodec<ByteBuf, TogglePacket> CODEC = StreamCodec.unit(INSTANCE);

        private TogglePacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, TogglePacket> getPacketType() {
            return PacketTypes.VACUUM_DOMAIN_TOGGLE.get();
        }
    }
}
