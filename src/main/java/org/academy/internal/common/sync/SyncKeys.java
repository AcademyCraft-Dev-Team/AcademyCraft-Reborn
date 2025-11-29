package org.academy.internal.common.sync;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.academy.AcademyCraft;
import org.academy.api.common.registries.Registries;
import org.academy.api.common.sync.SyncKey;

public final class SyncKeys {
    public static final DeferredRegister<SyncKey> SYNC_KEYS =
            DeferredRegister.create(Registries.Keys.SYNC_KEYS, AcademyCraft.MOD_ID);
    public static final DeferredHolder<SyncKey, SyncKey> VECTOR_REFLECTION_ACTIVE =
            SYNC_KEYS.register("vector_reflection_active", SyncKey::new);
    public static final DeferredHolder<SyncKey, SyncKey> RAILGUN_CHARGING =
            SYNC_KEYS.register("railgun_charging", SyncKey::new);

    private SyncKeys() {
    }
}