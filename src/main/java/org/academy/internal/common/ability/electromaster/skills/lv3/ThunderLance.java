package org.academy.internal.common.ability.electromaster.skills.lv3;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.resources.R;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.arc.ArcPath;
import org.academy.api.common.arc.modifier.JaggedModifier;
import org.academy.api.common.arc.path.LinePath;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.accelerator.reflection.LinearAttackExecutor;
import org.academy.internal.common.ability.accelerator.reflection.LinearAttackPayload;
import org.academy.internal.common.ability.accelerator.reflection.LinearReflectionResolver;
import org.academy.internal.common.ability.accelerator.reflection.LinearSegment;
import org.academy.internal.common.ability.electromaster.ElectromasterArcEffects;
import org.academy.internal.common.ability.electromaster.skills.lv1.ArcGenerate;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.sounds.SoundEvents;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.skill.ArcEffect;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;

public class ThunderLance extends Skill {
    static final float QUICK_BASE_DAMAGE = 16.0f;
    static final float QUICK_RANGE = 32.0f;
    static final float QUICK_RADIUS = 2.0f;
    static final float QUICK_CP_COST = 20.0f;
    private static final long RETURN_SEED_MASK = 0xA24BAED4963EE407L;

    public ThunderLance() {
        super(Builder
                .of(AbilityCategories.ELECTROMASTER.get())
                .level(AbilityLevel.LEVEL2)
                .energyCost(10_000)
                .cpCost(20)
                .iterationTicks(10)
                .maxStacks(10)
                .dependsOn(Skills.ARC_GENERATE)
        );
    }

    static float calculateQuickDamage(float abilityPower, float damageMultiplier) {
        if (!Float.isFinite(abilityPower) || !Float.isFinite(damageMultiplier)) return 0;
        return QUICK_BASE_DAMAGE * Math.max(0, abilityPower) * Math.max(0, damageMultiplier);
    }

    static Vec3 calculateHandPosition(Vec3 playerPosition, Vec3 look) {
        var right = look.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() <= 1.0e-8) right = new Vec3(1, 0, 0);
        else right = right.normalize();
        return playerPosition.add(right.scale(0.4)).add(0, 1.2, 0).add(look.scale(0.5));
    }

    static long deriveReturnSeed(long seed) {
        return seed ^ RETURN_SEED_MASK;
    }

    static List<ArcPath> createUnreflectedQuickArcPaths(
            Vec3 handPos,
            Vec3 targetPos,
            List<Vec3> offsets,
            List<Long> seeds
    ) {
        validateStrands(offsets, seeds);
        var paths = new ArrayList<ArcPath>(offsets.size());
        for (var i = 0; i < offsets.size(); i++) {
            paths.add(createQuickArcPath(handPos, targetPos.add(offsets.get(i)), seeds.get(i)));
        }
        return List.copyOf(paths);
    }

    static List<ArcPath> createReflectedQuickArcPaths(
            Vec3 handPos,
            Vec3 mirrorPoint,
            Vec3 returnEnd,
            double reflectionProgress,
            List<Vec3> offsets,
            List<Long> seeds
    ) {
        validateStrands(offsets, seeds);
        var t = Mth.clamp(reflectionProgress, 0.0, 1.0);
        var reflectionPoints = new ArrayList<Vec3>(offsets.size());
        var returnEndpoints = new ArrayList<Vec3>(offsets.size());
        var paths = new ArrayList<ArcPath>(offsets.size() * 2);

        for (var i = 0; i < offsets.size(); i++) {
            var offset = offsets.get(i);
            var reflectionPoint = mirrorPoint.add(offset.scale(t));
            reflectionPoints.add(reflectionPoint);
            returnEndpoints.add(returnEnd.subtract(offset.scale(1.0 - t)));
            paths.add(createQuickArcPath(handPos, reflectionPoint, seeds.get(i)));
        }
        for (var i = 0; i < reflectionPoints.size(); i++) {
            paths.add(createQuickArcPath(
                    reflectionPoints.get(i),
                    returnEndpoints.get(i),
                    deriveReturnSeed(seeds.get(i))
            ));
        }
        return List.copyOf(paths);
    }

    private static ArcPath createQuickArcPath(Vec3 start, Vec3 end, long seed) {
        return new ArcPath(
                new LinePath(start.toVector3f(), end.toVector3f()),
                List.of(new JaggedModifier(1, 4, seed)),
                3.0f,
                List.of()
        );
    }

    private static void validateStrands(List<Vec3> offsets, List<Long> seeds) {
        if (offsets.size() != seeds.size()) {
            throw new IllegalArgumentException("Each Thunder Lance strand requires one seed");
        }
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        if (!Client.CONFIG.containsKeyBinding(Client.KEY_NAME_SPEAR)
                && Client.CONFIG.containsKeyBinding(Client.OLD_KEY_NAME_QUICK)) {
            Client.CONFIG.setKeyBinding(Client.KEY_NAME_SPEAR,
                    Client.CONFIG.getKeyBinding(Client.OLD_KEY_NAME_QUICK));
        }
        InputSystem.addKeyBinding(Client.KEY_NAME_SPEAR, Client.CONFIG.getKeyBinding(Client.KEY_NAME_SPEAR,
                InputSystem.combo(
                        InputSystem.InputType.MOUSE,
                        InputConstants.MOUSE_BUTTON_RIGHT,
                        InputConstants.RELEASE,
                        InputConstants.MOD_ALT
                )
        ), _ -> Client.onQuickUse());
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.ELECTROMASTER.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.THUNDER_LANCE.get(),
                        List.of(ArcGenerate.Client.SKILL_INFO),
                        R.textures.thunder_lance_icon,
                        64,
                        86
                )
        );
        public static final String KEY_NAME_SPEAR = SkillNames.THUNDER_LANCE + "_spear";
        private static final String OLD_KEY_NAME_QUICK = SkillNames.THUNDER_LANCE + "_quick";
        public static Config CONFIG = new Config();

        public static void onQuickUse() {
            if (!AbilitySystemClient.canUseSkill(Skills.THUNDER_LANCE.get())) return;
            MisakaNetworkClient.send(QuickPacket.INSTANCE);
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public ThunderLance.Client.Config getDefault() {
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
        @SubscribePacket
        public static void handle(StartPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            Skills.THUNDER_LANCE.get().executeActive(player, (context, _) -> fireQuick(player, context.milestone()));
        }

        @SubscribePacket
        public static void handle(QuickPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            Skills.THUNDER_LANCE.get().executeActive(player, _ -> QUICK_CP_COST,
                    (context, _) -> fireQuick(player, context.milestone()));
        }

        private static void fireQuick(ServerPlayer player, int milestone) {
            var level = player.level();
            var look = player.getLookAngle();
            var handPos = calculateHandPosition(player.position(), look);
            var targetPos = player.getEyePosition().add(look.scale(milestone >= 2 ? 40.0 : QUICK_RANGE));

            var system = AbilitySystemServer.getSystem(player);
            var source = SkillDamageSource.of(player, Skills.THUNDER_LANCE.get());
            var damage = calculateQuickDamage(
                    system.getPlayerAbilityPowerMultiplier(player.getUUID()),
                    system.getPlayerDamageMultiplier(player.getUUID())
            );
            var payload = LinearAttackPayload.builder(
                            player,
                            Skills.THUNDER_LANCE.get(),
                            source,
                            milestone >= 2 ? QUICK_RADIUS * 1.2f : QUICK_RADIUS
                    )
                    .damage(_ -> damage)
                    .targetFilter(entity -> entity.getType() != EntityTypes.HIGH_SPEED_ELECTRON_BEAM.get())
                    .build();
            var resolved = LinearReflectionResolver.resolve(
                    level,
                    new LinearSegment(handPos, targetPos),
                    payload
            );

            var arcs = new ArrayList<>(ElectromasterArcEffects.intertwinedBundle(
                    resolved.outbound().start(), resolved.outbound().end(), 7, 0.34f));
            resolved.returnSegment().ifPresent(segment -> arcs.addAll(
                    ElectromasterArcEffects.intertwinedBundle(
                            segment.start(), segment.end(), 7, 0.34f)));
            var arc = new ArcEffect(level, 10);
            arc.setPos(handPos);
            arc.setArcPaths(arcs);
            level.addFreshEntity(arc);
            arc.playSound(SoundEvents.ARC_WEAK.get());

            var result = LinearAttackExecutor.execute(level, resolved, payload);
            if (milestone >= 3) branchDoubleHit(player, level, result, source, damage);
        }

        private static void branchDoubleHit(ServerPlayer player, net.minecraft.server.level.ServerLevel level,
                                            LinearAttackExecutor.ExecutionResult result,
                                            SkillDamageSource source, float damage) {
            var hits = new java.util.LinkedHashSet<net.minecraft.world.entity.Entity>();
            hits.addAll(result.outboundHits());
            hits.addAll(result.returnHits());
            var origin = hits.stream().filter(net.minecraft.world.entity.LivingEntity.class::isInstance)
                    .map(net.minecraft.world.entity.LivingEntity.class::cast).findFirst().orElse(null);
            if (origin == null) return;
            var target = level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                            origin.getBoundingBox().inflate(6.0), candidate -> candidate != player
                                    && !hits.contains(candidate) && candidate.isAlive() && !player.isAlliedTo(candidate))
                    .stream().min(java.util.Comparator.comparingDouble(origin::distanceToSqr)).orElse(null);
            if (target != null) target.hurtServer(level, source, damage * 0.4f);
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
            return PacketTypes.THUNDER_LANCE_START.get();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class QuickPacket extends Packet<ServerGamePacketListenerImpl, QuickPacket> {
        public static final QuickPacket INSTANCE = new QuickPacket();
        public static final StreamCodec<ByteBuf, QuickPacket> CODEC = StreamCodec.unit(INSTANCE);

        private QuickPacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, QuickPacket> getPacketType() {
            return PacketTypes.THUNDER_LANCE_QUICK.get();
        }
    }
}
