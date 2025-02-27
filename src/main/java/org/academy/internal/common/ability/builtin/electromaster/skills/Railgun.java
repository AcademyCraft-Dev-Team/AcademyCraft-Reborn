package org.academy.internal.common.ability.builtin.electromaster.skills;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.network.AcademyCraftNetworkSystemClient;
import org.academy.api.client.network.packet.C2SRequestPacket;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.network.AcademyCraftNetworkResourceLocations;
import org.academy.api.server.network.AcademyCraftRequestHandlersServer;
import org.academy.internal.common.sounds.AcademyCraftSoundEvents;
import org.academy.internal.common.world.entity.AcademyCraftEntityTypes;
import org.academy.internal.common.world.entity.RailgunRay;
import org.academy.internal.common.world.entity.projectile.ThrownCoin;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class Railgun extends Skill {
    public static final Skill INSTANCE = new Railgun();

    private Railgun() {
        super("railgun", 5);
    }

    @Override
    public void initServer(MinecraftServer server) {
        AcademyCraftRequestHandlersServer.REQUEST_HANDLER_MAP.put(AcademyCraftNetworkResourceLocations.C2S_RAILGUN_REQUEST, (serverGamePacketListenerImpl, packet) -> {
            Player player = serverGamePacketListenerImpl.player;
            EntityType<?> targetEntity = AcademyCraftEntityTypes.THROWN_COIN_ENTITY_TYPE;
            Vec3 lookVec = player.getLookAngle().scale(2);
            BlockPos pos = new BlockPos((int) (lookVec.x + player.getX()), (int) (lookVec.y + player.getEyeY()), (int) (lookVec.z + player.getZ()));

            AABB box = new AABB(pos).inflate(2);
            List<Entity> entities = player.level().getEntities(player, box, entity -> entity.getType() == targetEntity);
            if (!entities.isEmpty()) {
                player.sendSystemMessage(Component.literal("Yes"));
                ThrownCoin coin = (ThrownCoin) entities.get(0);
                coin.setBaseDamage(1000D);
                coin.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 100F, 1.0F);
                RailgunRay railgunRay = new RailgunRay(AcademyCraftEntityTypes.RAILGUN_RAY_ENTITY_TYPE, player.level());
                railgunRay.setPos(player.getEyePosition().add(0, -0.5, 0));
                railgunRay.setYRot(player.getYRot());
                railgunRay.setXRot(player.getXRot());

                player.level().addFreshEntity(railgunRay);
                railgunRay.playSound(AcademyCraftSoundEvents.RAILGUN);
            } else {
                player.sendSystemMessage(Component.literal("No"));
            }
        });
    }

    @Environment(EnvType.CLIENT)
    @Override
    public void initClient() {
        Runnable runnable = () -> AcademyCraftNetworkSystemClient.sendPacket(new C2SRequestPacket(AcademyCraftNetworkResourceLocations.C2S_RAILGUN_REQUEST));
        InputSystem.KEY_RELEASE_MAP.put("railgun.shoot", new InputSystem.KeyBinding(List.of(() -> GLFW.GLFW_KEY_X), runnable));
    }
}