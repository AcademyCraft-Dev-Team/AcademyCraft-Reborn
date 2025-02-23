package org.academy.internal.common.ability.builtin.electromaster.skills;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraft;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.network.AcademyCraftNetworkSystemClient;
import org.academy.api.client.network.packet.C2SRequestPacket;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.network.AcademyCraftNetworkResourceLocations;
import org.academy.api.server.network.AcademyCraftRequestHandlersServer;
import org.academy.internal.common.sounds.AcademyCraftSoundEvents;
import org.academy.internal.common.world.entity.Arc;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ArcGenerate extends Skill {
    public static final Skill INSTANCE = new ArcGenerate();

    private ArcGenerate() {
        super("arc_generate", 1);
    }

    @Override
    public void initServer(MinecraftServer server) {
        AcademyCraftRequestHandlersServer.REQUEST_HANDLER_MAP.put(AcademyCraftNetworkResourceLocations.C2S_ARC_REQUEST, serverGamePacketListenerImpl -> {
            Player player = serverGamePacketListenerImpl.getPlayer();
            Level level = player.level();
            if (level instanceof ServerLevel) {
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
                    entity.hurt(player.damageSources().playerAttack(player), 2);
                    AcademyCraft.LOGGER.info(entity.toString());
                });

                AcademyCraft.LOGGER.info("Arc generated");
            }
        });
    }

    @Override
    public void initClient() {
        Runnable runnable = () -> {
            AcademyCraftNetworkSystemClient.sendPacket(new C2SRequestPacket(AcademyCraftNetworkResourceLocations.C2S_ARC_REQUEST));
            AcademyCraft.LOGGER.info("Arc Generation request handled");
        };
        InputSystem.KEY_RELEASE_MAP.put(List.of(GLFW.GLFW_KEY_V), runnable);
    }
}