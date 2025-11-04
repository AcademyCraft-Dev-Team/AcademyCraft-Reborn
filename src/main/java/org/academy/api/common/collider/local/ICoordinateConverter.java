/*
 * This file is based on the “HitboxAPI” project:
 *   https://github.com/AnECanSaiTin/HitboxAPI
 *   Licensed under the GNU General Public License v3.0 or later.
 *
 * Minor modifications made by AcademyCraft-Dev-Team.
 * See the LICENSE file in the project root for the full license text.
 */

package org.academy.api.common.collider.local;

import org.joml.Quaternionf;
import org.joml.Vector3f;

public interface ICoordinateConverter {
    /// 位置版本
    short positionVersion();

    /// 位置
    Vector3f getPosition();

    /// 旋转版本
    short rotationVersion();

    /// 旋转
    Quaternionf getRotation();
}
