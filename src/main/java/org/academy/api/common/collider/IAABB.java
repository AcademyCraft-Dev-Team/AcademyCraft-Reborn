/*
 * This file is based on the “HitboxAPI” project:
 *   https://github.com/AnECanSaiTin/HitboxAPI
 *   Licensed under the GNU General Public License v3.0 or later.
 *
 * Minor modifications made by AcademyCraft-Dev-Team.
 * See the LICENSE file in the project root for the full license text.
 */

package org.academy.api.common.collider;

import org.joml.Vector3f;

/// AABB碰撞箱
///
/// 区别于原版实现，使用中心点加半长宽高定义。
public interface IAABB<T, D> extends ICollider<T, D> {
    /// @return 轴半长
    Vector3f getHalfExtents();

    /// 设置轴半长
    void setHalfExtents(Vector3f halfExtents);

    /// @return 中心点
    Vector3f getCenter();

    /// 设置中心点
    void setCenter(Vector3f center);

    /// @return 最小点
    Vector3f getMin();

    /// @return 最大点
    Vector3f getMax();

    @Override
    default ColliderTyep getType() {
        return ColliderTyep.AABB;
    }
}
