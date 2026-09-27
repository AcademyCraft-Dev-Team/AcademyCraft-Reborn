package org.academy.hack;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.academy.AcademyCraft;
import org.slf4j.Logger;

@Mod(Hack.MOD_ID)
public final class Hack {
    public static final String MOD_ID = "academy_hack";
    public static final Logger LOGGER = AcademyCraft.getLogger();

    public Hack(IEventBus modEventBus) {
    }
}
