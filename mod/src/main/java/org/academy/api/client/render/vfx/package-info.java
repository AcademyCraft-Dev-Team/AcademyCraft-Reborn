/**
 * Legacy immediate-mode VFX API kept temporarily for effects that have not yet moved to the
 * data-driven graph runtime. New effects must use {@link org.academy.api.client.render.vfxgraph}
 * and existing effects should migrate in self-contained batches before this package is removed.
 * Use {@link org.academy.api.client.render.vfxgraph} for all new effects.
 */
@Deprecated(since = "0.0.4")
@NullMarked
package org.academy.api.client.render.vfx;

import org.jspecify.annotations.NullMarked;
