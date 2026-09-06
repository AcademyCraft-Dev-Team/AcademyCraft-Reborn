package org.academy.internal.common.ability.electromaster.skills.lv1;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
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
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.common.util.LevelUtil;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.team.TeamRelations;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.accelerator.reflection.LinearAttackExecutor;
import org.academy.internal.common.ability.accelerator.reflection.LinearAttackPayload;
import org.academy.internal.common.ability.accelerator.reflection.LinearReflectionResolver;
import org.academy.internal.common.ability.accelerator.reflection.LinearSegment;
import org.academy.internal.common.ability.accelerator.reflection.ResolvedLinearAttack;
import org.academy.internal.common.ability.electromaster.ElectromasterArcActions;
import org.academy.internal.common.ability.electromaster.ElectromasterArcTargeting;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.network.SpawnVfxGraphPacket;
import org.academy.internal.common.world.damagesource.PvpSetting;
import org.academy.internal.common.sounds.SoundEvents;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public final class ArcGenerate extends Skill {
    public static final String KEY_NAME_GENERATE = SkillNames.ARC_GENERATE + ".generate";
    static final float BASE_DAMAGE = 4.0f;

    public ArcGenerate() {
        super(
                Builder
                        .of(AbilityCategories.ELECTROMASTER.get())
                        .damage()
                        .level(AbilityLevel.LEVEL1)
                        .energyCost(5_000)
                        .cpCost(10)
                        .iterationTicks(5)
                        .maxStacks(20)
                        .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL1))
        );
    }

    static float getDamage(float abilityPower, float playerDamageMultiplier) {
        return BASE_DAMAGE * Math.max(0, abilityPower) * Math.max(0, playerDamageMultiplier);
    }

    /**
     * Server-scaled base damage shared with bounded Electromaster program actions.
     */
    public static float programDamage(float abilityPower, float playerDamageMultiplier) {
        return getDamage(abilityPower, playerDamageMultiplier);
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.ArcGenerateConfig.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);

        InputSystem.addKeyBinding(KEY_NAME_GENERATE, Client.CONFIG.getKeyBinding(KEY_NAME_GENERATE,
                InputSystem.combo(InputSystem.InputType.MOUSE, InputConstants.MOUSE_BUTTON_LEFT, InputConstants.RELEASE, InputConstants.MOD_ALT)
        ), ctx -> Client.handler());
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.ELECTROMASTER.get(),
                new AbilitySystemClient.SkillInfo(Skills.ARC_GENERATE.get(), List.of(), R.textures.ability.electromaster.skill.arc_generate.icon, 24, 46)
        );
        public static ArcGenerateConfig CONFIG = new ArcGenerateConfig();

        public static void handler() {
            if (!AbilitySystemClient.canUseSkill(Skills.ARC_GENERATE.get())) return;
            MisakaNetworkClient.send(GeneratePacket.INSTANCE);
        }

        public static class ArcGenerateConfig extends KeyBindingConfig {
            public static final class Action implements TypeHandler<ArcGenerateConfig> {
                public static final TypeHandler<ArcGenerateConfig> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public ArcGenerate.Client.ArcGenerateConfig getDefault() {
                    return new ArcGenerateConfig();
                }

                @Override
                public Class<ArcGenerateConfig> getTypeClass() {
                    return ArcGenerateConfig.class;
                }
            }
        }
    }

    public static final class Server {
        @SubscribePacket
        public static void handle(GeneratePacket packet) {
            tryAutomatedAttack(packet.getPacketListener().getPlayer());
        }

        public static boolean tryAutomatedAttack(ServerPlayer player) {
            var level = player.level();
            return Skills.ARC_GENERATE.get().executeActive(player, (context, _) -> {
                var yawRad = (float) Math.toRadians(-player.getVisualRotationYInDegrees());
                var eyePos = player.getEyePosition();

                var playerOrientation = new Quaternionf().rotateY(yawRad);

                var look = new Vector3f(0, 0, 1).rotate(playerOrientation);
                var up = new Vector3f(0, 1, 0).rotate(playerOrientation);
                var right = new Vector3f(-1, 0, 0).rotate(playerOrientation);

                var handPos = eyePos
                        .add(new Vec3(right).scale(0.35))
                        .add(new Vec3(up).scale(-0.8))
                        .add(new Vec3(look).scale(0.35));

                var length = LevelUtil.getValidViewDistance(player, context.milestone() >= 2 ? 12 : 10);
                var targetPos = eyePos.add(player.getLookAngle().scale(length));

                var radius = context.milestone() >= 2 ? 0.15f : 0.125f;
                var system = AbilitySystemServer.getSystem(player);
                var src = SkillDamageSource.of(player, Skills.ARC_GENERATE.get());
                var damage = getDamage(
                        system.getPlayerAbilityPowerMultiplier(player.getUUID()),
                        system.getPlayerDamageMultiplier(player.getUUID())
                );
                var payload = LinearAttackPayload.builder(
                                player,
                                Skills.ARC_GENERATE.get(),
                                src,
                                radius
                        )
                        .damage(_ -> damage)
                        .targetFilter(ElectromasterArcTargeting::canDamageAlongArc)
                        .build();
                var resolved = LinearReflectionResolver.resolve(
                        level,
                        new LinearSegment(handPos, targetPos),
                        payload
                );

                spawnArcVfx(level, handPos, targetPos, resolved);

                var result = LinearAttackExecutor.execute(level, resolved, payload);
                if (context.milestone() >= 3) {
                    chainArc(player, level, result, src, damage);
                }
            });
        }

        private static final Identifier ARC_GENERATE_VFX = AcademyCraft.academy("vfxgraph/arc_generate");

        private static void spawnArcVfx(ServerLevel level, Vec3 handPos, Vec3 targetPos,
                                        ResolvedLinearAttack resolved) {
            if (resolved.isReflected()) {
                var mirrorPoint = resolved.mirrorPoint();
                broadcastArc(level, handPos, mirrorPoint);
                var returnEnd = resolved.returnSegment().orElseThrow().end();
                broadcastArc(level, mirrorPoint, returnEnd);
            } else {
                broadcastArc(level, handPos, targetPos);
            }
            level.playSound(
                    null,
                    handPos.x,
                    handPos.y,
                    handPos.z,
                    SoundEvents.ARC_WEAK.get(),
                    SoundSource.PLAYERS,
                    1.0f,
                    1.0f
            );
        }

        private static void broadcastArc(ServerLevel level, Vec3 from, Vec3 to) {
            var length = (float) from.distanceTo(to);
            System.out.println(length);
            SpawnVfxGraphPacket.broadcast(
                    level,
                    ARC_GENERATE_VFX,
                    from,
                    to.subtract(from),
                    -1,
                    1f,
                    1.0f,
                    Map.of("branch_length_scale", length * 0.275f)
            );
        }

        private static void chainArc(ServerPlayer player,
                                     ServerLevel level,
                                     LinearAttackExecutor.ExecutionResult result,
                                     SkillDamageSource source,
                                     float damage) {
            var hit = new LinkedHashSet<Entity>();
            hit.addAll(result.outboundHits());
            hit.addAll(result.returnHits());
            var origin = hit.stream().filter(LivingEntity.class::isInstance)
                    .map(LivingEntity.class::cast).findFirst().orElse(null);
            if (origin == null) return;
            var candidates = level.getEntitiesOfClass(LivingEntity.class,
                    origin.getBoundingBox().inflate(4.0),
                    target -> target != player && target.isAlive() && !hit.contains(target)
                            && !TeamRelations.areAllied(player, target)
                            && !PvpSetting.shouldPrevent(player, target));
            candidates.sort(Comparator.comparingDouble(origin::distanceToSqr));
            var factors = new float[]{0.5f, 0.3f};
            for (var index = 0; index < Math.min(2, candidates.size()); index++) {
                var target = candidates.get(index);
                if (!ElectromasterArcActions.strikeChain(
                        level, player, source, origin, target, damage * factors[index])) break;
                origin = target;
            }
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class GeneratePacket extends Packet<ServerGamePacketListenerImpl, GeneratePacket> {
        public static final GeneratePacket INSTANCE = new GeneratePacket();
        public static final StreamCodec<ByteBuf, GeneratePacket> CODEC = StreamCodec.unit(INSTANCE);

        private GeneratePacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, GeneratePacket> getPacketType() {
            return PacketTypes.ARC_GENERATE_GENERATE.get();
        }
    }
}
