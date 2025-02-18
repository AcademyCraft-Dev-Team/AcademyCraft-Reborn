package org.academy.internal.common.ability.builtin.electromaster.skills;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraft;
import org.academy.api.client.network.NetworkSystemClient;
import org.academy.api.client.network.packet.C2SRequestPacket;
import org.academy.api.client.util.KeyBindingUtil;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.network.AcademyCraftNetworkResourceLocations;
import org.academy.api.server.network.AcademyCraftServerRequestHandlers;
import org.academy.internal.common.world.entity.AcademyCraftEntityTypes;
import org.academy.internal.common.world.entity.RailgunRay;
import org.academy.internal.common.world.entity.projectile.ThrownCoin;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class Railgun extends Skill {
    public Railgun() {
        super("railgun", 5);
    }

    @Override
    public void initServer(MinecraftServer server) {
        AcademyCraftServerRequestHandlers.REQUEST_HANDLER_MAP.put(AcademyCraftNetworkResourceLocations.C2S_RAILGUN_REQUEST, (serverGamePacketListenerImpl) -> {
            Player player = serverGamePacketListenerImpl.player;
            EntityType<?> targetEntity = AcademyCraftEntityTypes.THROWN_COIN_ENTITY_TYPE;
            double depth = 5;
            double width = 5;
            double height = 5;
            Vec3 lookVec = player.getViewVector(1.0F);
            Vec3 center = player.position().add(lookVec.scale(depth / 2));
            Vec3 rightVec = new Vec3(-lookVec.z, 0, lookVec.x).normalize();

            AABB box = new AABB(center.x - (width / 2) * rightVec.x, center.y, center.z - (width / 2) * rightVec.z, center.x + (width / 2) * rightVec.x, center.y + height, center.z + (width / 2) * rightVec.z);
            List<Entity> entities = player.level().getEntities(player, box, entity -> entity.getType() == targetEntity);
            if (!entities.isEmpty()) {
                player.sendSystemMessage(Component.literal("Yes"));
                ThrownCoin coin = (ThrownCoin) entities.get(0);
                coin.setBaseDamage(1000D);
                coin.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 100F, 1.0F);
                RailgunRay railgunRay = new RailgunRay(AcademyCraftEntityTypes.RAILGUN_RAY_ENTITY_TYPE, player.level());
                railgunRay.setPos(player.position());
                railgunRay.setYRot(player.getYRot());
                railgunRay.setXRot(player.getXRot());

                player.level().addFreshEntity(railgunRay);
            } else {
                player.sendSystemMessage(Component.literal("No"));
            }
        });
    }

    @Environment(EnvType.CLIENT)
    @Override
    public void initClient() {
        Runnable runnable = () -> {
            NetworkSystemClient.sendPacket(new C2SRequestPacket(AcademyCraftNetworkResourceLocations.C2S_RAILGUN_REQUEST));
        };
        List<Integer> keys = new ArrayList<>();
        keys.add(GLFW.GLFW_KEY_X);
        AcademyCraft.LOGGER.info("Why001");
        KeyBindingUtil.registerSkillKeyBinding(this, runnable, keys);
    }
}