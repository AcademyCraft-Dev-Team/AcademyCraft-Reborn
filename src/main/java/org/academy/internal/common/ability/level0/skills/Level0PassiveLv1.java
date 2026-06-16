package org.academy.internal.common.ability.level0.skills;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.Skills;

public class Level0PassiveLv1 extends Skill {
    public Level0PassiveLv1() {
        super(Builder
                .of(AbilityCategories.LEVEL0.get())
                .level(AbilityLevel.LEVEL1)
                .passive()
                .maintenanceCost(0)
        );
    }

    @Override
    public void initServer(MinecraftServerContext c) {
    }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class Events {
        @SubscribeEvent
        public static void onTick(PlayerTickEvent.Post e) {
            if (!(e.getEntity() instanceof ServerPlayer p)) return;
            if (!Skills.LEVEL0_PASSIVE_LV1.get().isEnabled(p)) return;

            var hp = p.getAttribute(Attributes.MAX_HEALTH);
            if (hp != null && !hp.hasModifier(Modifier.HP_ID)) {
                hp.addPermanentModifier(new AttributeModifier(Modifier.HP_ID, 4,
                        AttributeModifier.Operation.ADD_VALUE));
            }

            var atk = p.getAttribute(Attributes.ATTACK_DAMAGE);
            if (atk != null && !atk.hasModifier(Modifier.ATK_ID)) {
                atk.addPermanentModifier(new AttributeModifier(Modifier.ATK_ID, 2,
                        AttributeModifier.Operation.ADD_VALUE));
            }
        }

        static class Modifier {
            static final net.minecraft.resources.Identifier HP_ID =
                    AcademyCraft.academy("basic_physique_hp");
            static final net.minecraft.resources.Identifier ATK_ID =
                    AcademyCraft.academy("basic_physique_atk");
        }
    }
}
