/*
 * This file is based on the “HitboxAPI” project:
 *   https://github.com/AnECanSaiTin/HitboxAPI
 *   Licensed under the GNU General Public License v3.0 or later.
 *
 * Minor modifications made by AcademyCraft-Dev-Team.
 * See the LICENSE file in the project root for the full license text.
 */

package org.academy.api.common.collider.local;

import org.academy.api.common.collider.IRay;
import org.joml.Vector3f;

public interface ILocalRay<T, D> extends IRay<T, D>, ILocalCollider<T, D> {
    /// @return 局部坐标系起点
    Vector3f getLocalOrigin();

    /// 设置局部坐标系起点
    void setLocalOrigin(Vector3f localOrigin);

    /// @return 局部坐标系方向
    Vector3f getLocalDirection();

    /// 设置局部坐标系方向
    void setLocalDirection(Vector3f localDirection);
}
