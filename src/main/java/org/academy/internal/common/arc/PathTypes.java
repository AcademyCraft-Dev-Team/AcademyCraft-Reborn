package org.academy.internal.common.arc;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.academy.AcademyCraft;
import org.academy.api.common.arc.PathType;
import org.academy.api.common.arc.path.CirclePath;
import org.academy.api.common.arc.path.LinePath;
import org.academy.api.common.arc.path.PolylinePath;
import org.academy.api.common.registries.Registries;

public final class PathTypes {
    public static final DeferredRegister<PathType<?>> PATH_TYPES =
            DeferredRegister.create(Registries.Keys.PATH_TYPES, AcademyCraft.MOD_ID);
    public static final DeferredHolder<PathType<?>, PathType<LinePath>> LINE =
            PATH_TYPES.register("line", () -> new PathType<>(LinePath.CODEC));
    public static final DeferredHolder<PathType<?>, PathType<PolylinePath>> POLYLINE =
            PATH_TYPES.register("polyline", () -> new PathType<>(PolylinePath.CODEC));
    public static final DeferredHolder<PathType<?>, PathType<CirclePath>> CIRCLE =
            PATH_TYPES.register("circle", () -> new PathType<>(CirclePath.CODEC));

    private PathTypes() {
    }
}