package org.academy.mixin.common.compat;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

import java.nio.IntBuffer;
import java.util.BitSet;

/** Only enabled for the fingerprint-audited Sable storage layout. */
@Pseudo
@Mixin(targets = "dev.ryanhcode.sable.sublevel.storage.region.SubLevelStorageFile", remap = false)
public abstract class MixinSableStorageClear {
    @Shadow @Final private IntBuffer sectorSpans;
    @Shadow @Final protected BitSet usedSectors;
    @Shadow @Final protected BitSet usedIndices;
    @Shadow public abstract int getSpanStart(int span);
    @Shadow public abstract int getSpanLength(int span);

    @WrapMethod(method = "clear")
    private void academy$retainAllocationOnFailure(int index, Operation<Void> original) throws Throwable {
        int span = sectorSpans.get(index);
        if (span == 0) {
            original.call(index);
            return;
        }
        int start = getSpanStart(span);
        int end = start + getSpanLength(span);
        var occupied = usedSectors.get(start, end); // At most 255 sector bits in the pinned format.
        boolean used = usedIndices.get(index);
        try {
            original.call(index);
        } catch (Throwable failure) {
            // Sable clears these before writing the header. A failed write must not turn
            // the next read/retry into an empty-slot success, free the slot, or truncate its data.
            sectorSpans.put(index, span);
            usedIndices.set(index, used);
            usedSectors.clear(start, end);
            for (int bit = occupied.nextSetBit(0); bit >= 0; bit = occupied.nextSetBit(bit + 1)) {
                usedSectors.set(start + bit);
            }
            throw failure;
        }
    }
}
