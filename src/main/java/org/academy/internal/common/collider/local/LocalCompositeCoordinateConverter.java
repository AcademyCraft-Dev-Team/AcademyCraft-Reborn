/*
 * This file is based on the “HitboxAPI” project:
 *   https://github.com/AnECanSaiTin/HitboxAPI
 *   Licensed under the GNU General Public License v3.0 or later.
 *
 * Minor modifications made by AcademyCraft-Dev-Team.
 * See the LICENSE file in the project root for the full license text.
 */

package org.academy.internal.common.collider.local;

import org.academy.api.common.collider.local.ICoordinateConverter;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class LocalCompositeCoordinateConverter implements ICoordinateConverter {
    private final short[] version = new short[2];
    private final LocalComposite<?, ?, ?> composite;

    public LocalCompositeCoordinateConverter(LocalComposite<?, ?, ?> composite) {
        this.composite = composite;
    }

    @Override
    public short positionVersion() {
        return version[0];
    }

    @Override
    public Vector3f getPosition() {
        return composite.getPosition();
    }

    @Override
    public short rotationVersion() {
        return version[1];
    }

    @Override
    public Quaternionf getRotation() {
        return composite.getRotation();
    }

    public void update() {
        version[0]++;
        version[1]++;
    }
}
