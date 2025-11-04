/*
 * This file is based on the “HitboxAPI” project:
 *   https://github.com/AnECanSaiTin/HitboxAPI
 *   Licensed under the GNU General Public License v3.0 or later.
 *
 * Minor modifications made by AcademyCraft-Dev-Team.
 * See the LICENSE file in the project root for the full license text.
 */

package org.academy.api.common.collider;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/// 有向包围盒OBB
public interface IOBB<T, D> extends ICollider<T, D> {
    /// 轴半长
    Vector3f getHalfExtents();

    /// 设置轴半长
    void setHalfExtents(Vector3f halfExtents);

    /// 中心点
    Vector3f getCenter();

    /// 设置中心点
    void setCenter(Vector3f center);

    /// 旋转
    Quaternionf getRotation();

    /// 设置旋转
    void setRotation(Quaternionf rotation);

    /// 顶点
    Vector3f[] getVertices();

    /// 轴向
    Vector3f[] getAxes();

    @Override
    default ColliderTyep getType() {
        return ColliderTyep.OBB;
    }
}
