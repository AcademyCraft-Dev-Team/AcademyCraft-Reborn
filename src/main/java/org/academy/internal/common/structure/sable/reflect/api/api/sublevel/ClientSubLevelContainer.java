package org.academy.internal.common.structure.sable.reflect.api.api.sublevel;

import org.academy.internal.common.structure.sable.reflect.SableReflection;
import org.academy.internal.common.structure.sable.reflect.SableType;

/** Academy-owned runtime view; contains no linkage to optional mod classes. */
@SableType("dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer")
public interface ClientSubLevelContainer extends org.academy.internal.common.structure.sable.reflect.api.api.sublevel.SubLevelContainer, org.academy.internal.common.structure.sable.reflect.SableView {
    void tick();
    java.util.List<org.academy.internal.common.structure.sable.reflect.api.sublevel.ClientSubLevel> getAllSubLevels();
    net.minecraft.client.multiplayer.ClientLevel getLevel();
    void removeSubLevel(int a0, int a1, org.academy.internal.common.structure.sable.reflect.api.sublevel.storage.SubLevelRemovalReason a2);
}
