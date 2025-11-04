/*
 * This file is based on the “HitboxAPI” project:
 *   https://github.com/AnECanSaiTin/HitboxAPI
 *   Licensed under the GNU General Public License v3.0 or later.
 *
 * Minor modifications made by AcademyCraft-Dev-Team.
 * See the LICENSE file in the project root for the full license text.
 */

package org.academy.api.common.collider.local;

import org.academy.api.common.collider.ICapsule;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/// 局部坐标系胶囊体碰撞箱
public interface ILocalCapsule<T, D> extends ICapsule<T, D>, ILocalCollider<T, D> {
    /// @return 局部坐标系中心点
    Vector3f getLocalCenter();

    /// 设置局部坐标系中心点
    void setLocalCenter(Vector3f center);

    /// @return 局部坐标系旋转
    Quaternionf getLocalRotation();

    /// 设置局部坐标系旋转
    void setLocalRotation(Quaternionf rotation);
}
