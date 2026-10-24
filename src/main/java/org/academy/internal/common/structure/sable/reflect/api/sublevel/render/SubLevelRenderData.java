package org.academy.internal.common.structure.sable.reflect.api.sublevel.render;

import org.academy.internal.common.structure.sable.reflect.SableReflection;
import org.academy.internal.common.structure.sable.reflect.SableType;

/** Academy-owned runtime view; contains no linkage to optional mod classes. */
@SableType("dev.ryanhcode.sable.sublevel.render.SubLevelRenderData")
public interface SubLevelRenderData extends org.academy.internal.common.structure.sable.reflect.SableView {
    void close();
    void rebuild();
    void setDirty(int a0, int a1, int a2, boolean a3);
    org.academy.internal.common.structure.sable.reflect.api.sublevel.ClientSubLevel getSubLevel();
}
