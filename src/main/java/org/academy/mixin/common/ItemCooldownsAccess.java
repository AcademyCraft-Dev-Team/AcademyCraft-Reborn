package org.academy.mixin.common;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemCooldowns;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.Map;

@Mixin(ItemCooldowns.class)
public interface ItemCooldownsAccess {
    @Accessor("tickCount") int academy$tickCount();
    @Accessor("cooldowns") Map<Identifier, ?> academy$cooldowns();
}
