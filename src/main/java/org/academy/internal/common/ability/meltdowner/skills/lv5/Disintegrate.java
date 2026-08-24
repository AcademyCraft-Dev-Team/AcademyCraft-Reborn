package org.academy.internal.common.ability.meltdowner.skills.lv5;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.resources.Identifier;
import org.academy.AcademyCraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.meltdowner.MeltdownerTargeting;
import org.academy.internal.common.ability.TimedSkillEffectRuntime;
import org.academy.internal.common.ability.meltdowner.skills.ContinuousBeam;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.world.damagesource.DestroyBlocksSetting;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.skill.HighSpeedElectronBeam;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.util.Mth;

public class Disintegrate extends Skill {
    private static final double PRIMARY_RANGE = 30.0;
    private static final double SCATTER_RADIUS = 12.0;
    private static final Identifier DISINTEGRATION_ID = AcademyCraft.academy("disintegration_armor");

    public Disintegrate() {
        super(Builder.of(AbilityCategories.MELTDOWNER.get())
                .damage()
                .level(AbilityLevel.LEVEL5)
                .energyCost(100_000)
                .cpCost(100)
                .iterationTicks(20)
                .maxStacks(2)
                .dependsOn(Skills.PARTICLE_WAVE_CANNON));
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(Client.KEY, Client.CONFIG.getKeyBinding(Client.KEY,
                        InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_K,
                                InputConstants.PRESS, InputConstants.MOD_ALT | InputConstants.MOD_SHIFT)),
                _ -> Client.onUse());
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    @Override
    public void onKill(ServerPlayer killer, LivingEntity target) {
        super.onKill(killer, target);
        Server.onKilled(killer, target);
    }

    public static final class Client {
        public static final String KEY = SkillNames.DISINTEGRATE + "_use";
        public static Config CONFIG = new Config();

        private static void onUse() {
            if (!AbilitySystemClient.canUseSkill(Skills.DISINTEGRATE.get())) return;
            if (Minecraft.getInstance().player != null) MisakaNetworkClient.send(UsePacket.INSTANCE);
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
        private static final Map<UUID, PendingStage> PENDING_STAGES = new HashMap<>();

        private Server() {
        }

        public static float calculateDamage(float maxHealth, float playerMultiplier) {
            return Math.max(0.0f, maxHealth) * 0.20f * Math.max(0.0f, playerMultiplier);
        }

        @SubscribePacket
        public static void handle(UsePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            Skills.DISINTEGRATE.get().executeActive(
                    player,
                    context -> Math.max(
                            100.0f,
                            context.system().getPlayerMaxCP(player.getUUID()) * 0.2f
                    ),
                    (context, _) -> {
                        var level = player.level();
                        cleanup(level.getGameTime());
                        var start = ContinuousBeam.mainHandOrigin(player, 0.2f);
                        var range = context.milestone() >= 2 ? PRIMARY_RANGE * 1.2 : PRIMARY_RANGE;
                        var end = start.add(player.getLookAngle().scale(range));
                        var target = findFirstTarget(level, player, start, end);
                        if (target != null) {
                            end = target.getBoundingBox().getCenter();
                        }
                        spawnBeam(player, start, end, 0,
                                DestroyBlocksSetting.canDestroyBlocks(player, Skills.DISINTEGRATE.get()),
                                context.milestone());
                    }
            );
        }

        private static void onKilled(ServerPlayer player, LivingEntity killed) {
            var level = player.level();
            var pending = PENDING_STAGES.remove(killed.getUUID());
            if (pending == null || !pending.owner().equals(player.getUUID())
                    || pending.expiresAt() < level.getGameTime() || pending.stage() >= 2) return;

            var count = pending.stage() == 0 ? (pending.milestone() >= 2 ? 4 : 3) : 1;
            var center = killed.getBoundingBox().getCenter();
            var targets = level.getEntitiesOfClass(
                            LivingEntity.class,
                            new AABB(center, center).inflate(SCATTER_RADIUS),
                            target -> target != player && target != killed && target.isAlive()
                                    && MeltdownerTargeting.canAffectNegatively(player, target)
                                    && (!(target instanceof ServerPlayer serverPlayer)
                                    || !serverPlayer.isCreative() && !serverPlayer.isSpectator())
                    ).stream()
                    .sorted(Comparator.comparingDouble(target -> target.distanceToSqr(center)))
                    .limit(count)
                    .toList();
            for (var target : targets) {
                var stage = pending.stage() + 1;
                spawnBeam(player, center, target.getBoundingBox().getCenter(), stage, false,
                        pending.milestone());
            }
        }

        private static LivingEntity findFirstTarget(ServerLevel level, ServerPlayer player,
                                                    Vec3 start, Vec3 end) {
            return level.getEntitiesOfClass(
                            LivingEntity.class,
                            new AABB(start, end).inflate(1.0),
                            target -> target.isAlive()
                                    && MeltdownerTargeting.canAffectNegatively(player, target)
                                    && (!(target instanceof ServerPlayer serverPlayer)
                                    || !serverPlayer.isCreative() && !serverPlayer.isSpectator())
                                    && distanceToSegmentSqr(target.getBoundingBox().getCenter(), start, end) <= 1.0
                    ).stream()
                    .min(Comparator.comparingDouble(target -> target.distanceToSqr(start)))
                    .orElse(null);
        }

        private static void spawnBeam(ServerPlayer player, Vec3 start, Vec3 end,
                                      int stage, boolean destroysBlocks, int milestone) {
            var level = player.level();
            var delta = end.subtract(start);
            if (delta.lengthSqr() <= 1.0e-8) return;
            if (stage < 2) markTargetsAlongBeam(player, start, end, stage, level.getGameTime(), milestone);
            var horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
            var beam = new HighSpeedElectronBeam(EntityTypes.HIGH_SPEED_ELECTRON_BEAM.get(), level);
            var multiplier = AbilitySystemServer.getSystem(player)
                    .getPlayerDamageMultiplier(player.getUUID());
            beam.configure(
                    player,
                    Skills.DISINTEGRATE.get(),
                    0.0f,
                    0.20f,
                    multiplier,
                    Skills.RADIATION_INTENSIFY.get().isEnabled(player),
                    destroysBlocks
            );
            beam.setAttackDelayTicks(0);
            beam.setBeamLength((float) delta.length() + 0.8f);
            beam.setBeamScale(stage == 0 ? 1.45f : 1.05f);
            beam.setBetaTrailOnFire(true);
            if (stage == 0) {
                beam.setVisualSideOffset(ContinuousBeam.mainHandVisualSideOffset(player));
            }
            beam.setPos(start);
            beam.setYRot((float) (Mth.atan2(-delta.x, delta.z)) * Mth.RAD_TO_DEG);
            beam.setXRot((float) (Mth.atan2(-delta.y, horizontal)) * Mth.RAD_TO_DEG);
            level.addFreshEntity(beam);
        }

        private static void markTargetsAlongBeam(ServerPlayer player, Vec3 start, Vec3 end,
                                                 int stage, long now, int milestone) {
            var search = new AABB(start, end).inflate(0.125);
            for (var candidate : player.level().getEntitiesOfClass(
                    LivingEntity.class,
                    search,
                    target -> target.isAlive()
                            && MeltdownerTargeting.canAffectNegatively(player, target)
                            && (!(target instanceof ServerPlayer serverPlayer)
                            || !serverPlayer.isCreative() && !serverPlayer.isSpectator())
                            && (target.getBoundingBox().inflate(0.125).contains(start)
                            || target.getBoundingBox().inflate(0.125).clip(start, end).isPresent())
            )) {
                mark(player, candidate, stage, now, milestone);
            }
        }

        private static void mark(ServerPlayer player, LivingEntity target, int stage, long now, int milestone) {
            PENDING_STAGES.put(target.getUUID(), new PendingStage(player.getUUID(), stage, now + 40, milestone));
            if (milestone >= 3) {
                TimedSkillEffectRuntime.schedule(player, 2, () -> {
                    if (target.isAlive()) applyDisintegration(player, target);
                });
            }
        }

        private static void applyDisintegration(ServerPlayer owner, LivingEntity target) {
            var armor = target.getAttribute(Attributes.ARMOR);
            if (armor == null) return;
            if (armor.getModifier(DISINTEGRATION_ID) != null) armor.removeModifier(DISINTEGRATION_ID);
            armor.addTransientModifier(new AttributeModifier(DISINTEGRATION_ID,
                    target instanceof Player ? -0.125 : -0.25,
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            var skill = Skills.DISINTEGRATE.get();
            TimedSkillEffectRuntime.put(owner, target.getUUID(), skill, "armor_disintegration", 100, 1.0f);
            TimedSkillEffectRuntime.schedule(owner, 100, () -> {
                if (TimedSkillEffectRuntime.get(owner.getUUID(), target.getUUID(), skill,
                        "armor_disintegration", owner.level().getGameTime()).isEmpty()) {
                    var currentArmor = target.getAttribute(Attributes.ARMOR);
                    if (currentArmor != null) currentArmor.removeModifier(DISINTEGRATION_ID);
                }
            });
        }

        private static void cleanup(long now) {
            PENDING_STAGES.values().removeIf(stage -> stage.expiresAt() < now);
        }

        private static double distanceToSegmentSqr(Vec3 point, Vec3 start, Vec3 end) {
            var segment = end.subtract(start);
            var lengthSqr = segment.lengthSqr();
            if (lengthSqr < 1.0e-9) return point.distanceToSqr(start);
            var progress = Mth.clamp(point.subtract(start).dot(segment) / lengthSqr, 0.0, 1.0);
            return point.distanceToSqr(start.add(segment.scale(progress)));
        }

        private record PendingStage(UUID owner, int stage, long expiresAt, int milestone) {
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class UsePacket extends Packet<ServerGamePacketListenerImpl, UsePacket> {
        public static final UsePacket INSTANCE = new UsePacket();
        public static final StreamCodec<ByteBuf, UsePacket> CODEC = StreamCodec.unit(INSTANCE);

        private UsePacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, UsePacket> getPacketType() {
            return PacketTypes.DISINTEGRATE_USE.get();
        }
    }
}
