/*
 * This file is based on the “HitboxAPI” project:
 *   https://github.com/AnECanSaiTin/HitboxAPI
 *   Licensed under the GNU General Public License v3.0 or later.
 *
 * Minor modifications made by AcademyCraft-Dev-Team.
 * See the LICENSE file in the project root for the full license text.
 */

package org.academy.api.common.collider.local;

import org.academy.api.common.collider.IComposite;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public interface ILocalComposite<C extends ILocalCollider<T, D>, T, D> extends IComposite<C, T, D> {
    /// @return  局部坐标系坐标
    Vector3f getLocalPosition();

    /// 设置局部坐标系坐标
    void setLocalPosition(Vector3f position);

    /// @return 局部坐标系旋转
    Quaternionf getLocalRotation();

    /// 设置局部坐标系旋转
    void setLocalRotation(Quaternionf rotation);

    /// @return 坐标
    Vector3f getPosition();

    /// @return 旋转
    Quaternionf getRotation();

    /// @return 坐标转换器
    ICoordinateConverter getConverter();
}
