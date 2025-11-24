/*
 * This file is based on the “HitboxAPI” project:
 *   https://github.com/AnECanSaiTin/HitboxAPI
 *   Licensed under the GNU General Public License v3.0 or later.
 *
 * Minor modifications made by AcademyCraft-Dev-Team.
 * See the LICENSE file in the project root for the full license text.
 */

package org.academy.api.common.collider;

import org.jspecify.annotations.Nullable;

/// 碰撞箱接口
///
/// 依附与玩家且在碰撞时需要额外参数String
///
/// `ICollider<Player, String>`
///
/// 依附与物品且不需要额外参数，可使用
///
/// `ICollider<ItemStack, Void>`
/// @param <T> 所依附的实体类型
/// @param <D> 碰撞时传入的额外数据类型
public interface ICollider<T, D> {
    /// 当与其他碰撞体发生碰撞时调用
    ///
    /// @param entity 发生碰撞的实体
    /// @param otherEntity 与其发生碰撞的实体
    /// @param other 发生碰撞的另一个碰撞箱
    /// @param data 额外数据
    default <O> void onCollide(T entity, O otherEntity, ICollider<O, ?> other, @Nullable D data) {
    }

    /// @return 碰撞箱类型
    ColliderTyep getType();

    /// 通过AABB碰撞箱进行快速排除，提高碰撞检测效率。
    ///
    /// AABB要求：
    /// - 完整涵盖整个碰撞箱
    /// - 尽可能小
    ///
    /// 可为Null，则不进行快速排除。
    ///
    /// @return 快速碰撞箱
    @Nullable
    default IAABB<T, D> getFastCollider() {
        return null;
    }

    /// @param disable 设置禁用
    void setDisable(boolean disable);

    /// @return 是否禁用
    boolean disable();
}
