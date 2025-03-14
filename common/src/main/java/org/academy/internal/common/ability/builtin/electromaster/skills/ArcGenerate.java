package org.academy.internal.common.ability.builtin.electromaster.skills;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.AbilitySystemServer;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftClientConfig;
import org.academy.AcademyCraftServer;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.network.AcademyCraftNetworkSystemClient;
import org.academy.api.client.network.packet.C2SRequestPacket;
import org.academy.api.client.util.ClientUtil;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.network.AcademyCraftNetworkResourceLocations;
import org.academy.api.server.network.AcademyCraftRequestHandlersServer;
import org.academy.internal.common.sounds.AcademyCraftSoundEvents;
import org.academy.internal.common.world.entity.skill.Arc;
import org.academy.internal.server.world.level.storage.AcademyCraftWorldData;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ArcGenerate extends Skill {
    public static final Skill INSTANCE = new ArcGenerate();
    public static final String KEY_NAME = "arc_generate.generate";
    public static AcademyCraftClientConfig.InputPair KEY;
    public static final float BASE_DAMAGE = 2.0F;

    private ArcGenerate() {
        super("arc_generate", 1);
    }

    @Override
    public void initClient() {
        KEY = AcademyCraftClient.clientConfig.getKey(KEY_NAME,
                new AcademyCraftClientConfig.InputPair(AcademyCraftClientConfig.InputType.KEYBOARD, new InputSystem.InputEvent(
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_G)),
                        GLFW.GLFW_RELEASE,
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_MOD_ALT))
                )));
        Runnable runnable = () -> {
            if (ClientUtil.isScreenNull()) {
                AcademyCraftNetworkSystemClient.sendPacket(new C2SRequestPacket(AcademyCraftNetworkResourceLocations.C2S_ARC_GENERATE_REQUEST));
            }
        };
        InputSystem.registerKeyBinding(KEY_NAME, KEY, runnable);
    }

    @Override
    public void initServer(MinecraftServer server) {
        AcademyCraftRequestHandlersServer.REQUEST_HANDLER_MAP.put(AcademyCraftNetworkResourceLocations.C2S_ARC_GENERATE_REQUEST, (serverGamePacketListenerImpl, packet) -> {
            Player player = serverGamePacketListenerImpl.getPlayer();
            Level level = player.level();
            if (level instanceof ServerLevel) {
                AcademyCraftWorldData.Player data = AcademyCraftServer.academyCraftWorldData.getPlayers().get(player.getUUID());
                float currentComputingPower = data.getComputingPower();
                if (currentComputingPower > 10) {
                    data.setComputingPower(currentComputingPower - 10);
                } else {
                    return;
                }

                Arc arc = new Arc(level, player);
                level.addFreshEntity(arc);
                arc.playSound(AcademyCraftSoundEvents.ARC_WEAK);
                Vec3 lookVec = arc.getLookAngle();
                Vec3 start = arc.position();
                int steps = 10;

                Set<LivingEntity> detectedEntities = new HashSet<>();

                for (int i = 0; i < steps; i++) {
                    Vec3 segmentStart = start.add(lookVec.scale(i));
                    Vec3 segmentEnd = start.add(lookVec.scale(i + 1));

                    AABB box = new AABB(segmentStart, segmentEnd);
                    List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, box);

                    detectedEntities.addAll(entities);
                }

                detectedEntities.forEach(entity -> {
                    if (entity == player) {
                        return;
                    }
                    float damage = BASE_DAMAGE * AbilitySystemServer.getDamageMultiplier();
                    entity.hurt(player.damageSources().playerAttack(player), damage);
                });
            }
        });
    }
}