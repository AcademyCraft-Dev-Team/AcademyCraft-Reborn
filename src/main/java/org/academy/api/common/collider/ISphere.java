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

/// 球体碰撞箱
public interface ISphere<T, D> extends ICollider<T, D> {
    /// 半径
    float getRadius();

    /// 设置半径
    void setRadius(float radius);

    /// 中心点
    Vector3f getCenter();

    /// 设置中心点
    void setCenter(Vector3f center);

    @Override
    default ColliderTyep getType() {
        return ColliderTyep.SPHERE;
    }
}
