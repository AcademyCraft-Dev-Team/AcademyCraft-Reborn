package org.academy.internal.client.render.vfx;

import org.academy.api.common.vfx.RepairVisualParts;

/** Conservative model-local offsets for standard player skin and humanoid equipment surfaces. */
final class ReconstructionSurfacePadding {
    // Minecraft 26.2: skin overlays grow .25 model pixels, the hat .5, outer armor 1 and inner armor .5.
    // A model pixel is 1/16 block. These bounds include a small margin before the lamina's own surface lift.
    static final float SKIN = .018f;
    static final float HAT = .034f;
    static final float OUTER_ARMOR = .070f;
    static final float INNER_ARMOR = .040f;

    private ReconstructionSurfacePadding() { }

    static float forPart(int part, int armorParts, boolean feetRegion) {
        boolean helmet = (armorParts & RepairVisualParts.HEAD) != 0;
        boolean chest = (armorParts & RepairVisualParts.CHEST) != 0;
        boolean leggings = (armorParts & RepairVisualParts.LEGS) != 0;
        boolean boots = (armorParts & RepairVisualParts.FEET) != 0;
        return switch (part) {
            case 0 -> chest ? OUTER_ARMOR : leggings ? INNER_ARMOR : SKIN;
            case 1 -> helmet ? OUTER_ARMOR : HAT;
            case 2, 3 -> chest ? OUTER_ARMOR : SKIN;
            case 4, 5 -> feetRegion && boots ? OUTER_ARMOR : leggings ? INNER_ARMOR : SKIN;
            default -> 0;
        };
    }
}
