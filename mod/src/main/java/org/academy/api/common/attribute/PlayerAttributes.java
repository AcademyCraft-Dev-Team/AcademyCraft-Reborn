package org.academy.api.common.attribute;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.academy.AcademyCraft;

/**
 * Public player attributes supplied by AcademyCraft.
 */
public final class PlayerAttributes {
    public static final DeferredRegister<Attribute> ATTRIBUTES =
            DeferredRegister.create(Registries.ATTRIBUTE, AcademyCraft.MOD_ID);
    public static final DeferredHolder<Attribute, Attribute> TRUE_RESISTANCE = register(
            "true_resistance", 8.0
    );
    private static final double GENERAL_MAX = 1_000_000_000.0;
    public static final DeferredHolder<Attribute, Attribute> MUSCLE_STRENGTH = register(
            "muscle_strength", GENERAL_MAX
    );
    public static final DeferredHolder<Attribute, Attribute> ENDURANCE = register(
            "endurance", GENERAL_MAX
    );
    public static final DeferredHolder<Attribute, Attribute> DEXTERITY = register(
            "dexterity", GENERAL_MAX
    );
    public static final DeferredHolder<Attribute, Attribute> PERCEPTION = register(
            "perception", GENERAL_MAX
    );
    public static final DeferredHolder<Attribute, Attribute> NEURAL_ACTIVITY = register(
            "neural_activity", GENERAL_MAX
    );

    private PlayerAttributes() {
    }

    private static DeferredHolder<Attribute, Attribute> register(String name, double maximum) {
        return ATTRIBUTES.register(name, () -> new RangedAttribute(
                "attribute.name.academy." + name,
                0.0,
                0.0,
                maximum
        ).setSyncable(true));
    }
}
