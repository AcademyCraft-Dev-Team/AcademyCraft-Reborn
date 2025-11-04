/*
 * This file is based on the “HitboxAPI” project:
 *   https://github.com/AnECanSaiTin/HitboxAPI
 *   Licensed under the GNU General Public License v3.0 or later.
 *
 * Minor modifications made by AcademyCraft-Dev-Team.
 * See the LICENSE file in the project root for the full license text.
 */

package org.academy.api.common.collider;

import org.joml.Intersectionf;
import org.joml.Vector3f;

public class ColliderUtil {
    /// 判断两个碰撞箱是否相交
    ///
    /// 适用于碰撞箱处于不同的坐标系中，例如A、B实体存在以实体自身为中心的碰撞箱，
    /// 此时需要使用坐标变换栈将碰撞箱转换为世界坐标。
    ///
    /// <pre>{@code
    ///    Entity A = ...;
    ///    Entity B = ...;
    ///    //假设有数据附加能获取碰撞箱
    ///    ICollider colliderA = A.getData(...);
    ///    ICollider colliderB = B.getData(...);
    ///    //够造从实体局部坐标到世界坐标的坐标变换栈
    ///     A = new ();
    ///    //将实体坐标存入栈
    ///    A.setPosition(A.position().toVector3f());
    ///     B = new ();
    ///    B.setPosition(B.position().toVector3f());
    ///    //判断碰撞箱是否相交
    ///    boolean result = ColliderUtil.isColliding(colliderA, A, colliderB, B);
    /// }</pre>
    ///
    /// @param collider 任意碰撞箱
    /// @param entity1  碰撞箱附加实体
    /// @param data1    碰撞箱附加数据
    /// @param other    另一个碰撞箱
    /// @param entity2  另一个碰撞箱附加实体
    /// @param data2    另一个碰撞箱附加数据
    /// @return 相交返回true
    public static <T1, D1, T2, D2> boolean colliding(ICollider<T1, D1> collider, T1 entity1, D1 data1, ICollider<T2, D2> other, T2 entity2, D2 data2) {
        var colliderType = collider.getType();
        var otherType = other.getType();
        var fastCollider1 = collider.getFastCollider();
        var fastCollider2 = other.getFastCollider();

        // 快速碰撞检测
        if (fastCollider1 != null && fastCollider2 != null) {
            var colliding = isColliding(fastCollider1, fastCollider2);

            if (!colliding) {
                return false;
            }
        }

        var result = switch (colliderType) {
            case OBB -> switch (otherType) {
                case OBB -> isColliding((IOBB<T1, D1>) collider, (IOBB<T2, D2>) other);
                case SPHERE -> isColliding((ISphere<T2, D2>) other, (IOBB<T1, D1>) collider);
                case CAPSULE -> isColliding((ICapsule<T2, D2>) other, (IOBB<T1, D1>) collider);
                case AABB -> isColliding((IOBB<T1, D1>) collider, (IAABB<T2, D2>) other);
                case RAY -> isColliding((IRay<T2, D2>) other, (IOBB<T1, D1>) collider);
                case COMPOSITE ->
                        isColliding((IComposite<ICollider<T2, D2>, T2, D2>) other, entity2, data2, collider, entity1, data1);
            };
            case SPHERE -> switch (otherType) {
                case OBB -> isColliding((ISphere<T1, D1>) collider, (IOBB<T2, D2>) other);
                case SPHERE -> isColliding((ISphere<T1, D1>) collider, (ISphere<T2, D2>) other);
                case CAPSULE -> isColliding((ICapsule<T2, D2>) other, (ISphere<T1, D1>) collider);
                case AABB -> isColliding((ISphere<T1, D1>) collider, (IAABB<T2, D2>) other);
                case RAY -> isColliding((IRay<T2, D2>) other, (ISphere<T1, D1>) collider);
                case COMPOSITE ->
                        isColliding((IComposite<ICollider<T2, D2>, T2, D2>) other, entity2, data2, collider, entity1, data1);
            };
            case CAPSULE -> switch (otherType) {
                case OBB -> isColliding((ICapsule<T1, D1>) collider, (IOBB<T2, D2>) other);
                case SPHERE -> isColliding((ICapsule<T1, D1>) collider, (ISphere<T2, D2>) other);
                case CAPSULE -> isColliding((ICapsule<T1, D1>) collider, (ICapsule<T2, D2>) other);
                case AABB -> isColliding((ICapsule<T1, D1>) collider, (IAABB<T2, D2>) other);
                case RAY -> isColliding((IRay<T2, D2>) other, (ICapsule<T1, D1>) collider);
                case COMPOSITE -> isColliding((IComposite<ICollider<T2, D2>, T2, D2>) other, entity2, data2, collider, entity1, data1);

            };
            case AABB -> switch (otherType) {
                case OBB -> isColliding((IOBB<T2, D2>) other, (IAABB<T1, D1>) collider);
                case SPHERE -> isColliding((ISphere<T2, D2>) other, (IAABB<T1, D1>) collider);
                case CAPSULE -> isColliding((ICapsule<T2, D2>) other, (IAABB<T1, D1>) collider);
                case AABB -> isColliding((IAABB<T1, D1>) collider, (IAABB<T2, D2>) other);
                case RAY -> isColliding((IRay<T2, D2>) other, (IAABB<T1, D1>) collider);
                case COMPOSITE -> isColliding((IComposite<ICollider<T2, D2>, T2, D2>) other, entity2, data2, collider, entity1, data1);
            };
            case RAY -> switch (otherType) {
                case OBB -> isColliding((IRay<T1, D1>) collider, (IOBB<T2, D2>) other);
                case SPHERE -> isColliding((IRay<T1, D1>) collider, (ISphere<T2, D2>) other);
                case CAPSULE -> isColliding((IRay<T1, D1>) collider, (ICapsule<T2, D2>) other);
                case AABB -> isColliding((IRay<T1, D1>) collider, (IAABB<T2, D2>) other);
                case RAY -> isColliding((IRay<T1, D1>) collider, (IRay<T2, D2>) other);
                case COMPOSITE -> isColliding((IComposite<ICollider<T2, D2>, T2, D2>) other, entity2, data2, collider, entity1, data1);
            };
            case COMPOSITE -> isColliding((IComposite<ICollider<T1, D1>, T1, D1>) collider, entity1, data1, other, entity2, data2);
        };

        if (result) {
            if (colliderType == ColliderTyep.COMPOSITE && otherType != ColliderTyep.COMPOSITE) collider.onCollide(entity1, entity2, other, data1);
            else if (otherType == ColliderTyep.COMPOSITE && colliderType != ColliderTyep.COMPOSITE) other.onCollide(entity2, entity1, collider, data2);
            else {
                collider.onCollide(entity1, entity2, other, data1);
                other.onCollide(entity2, entity1, collider, data2);
            }
        }

        return result;
    }

    public static <T1, D1, T2, D2> boolean colliding(ICollider<T1, D1> collider, ICollider<T2, D2> other) {
        if (other == null) return false;
        if (collider.disable() || other.disable()) return false;
        if (other == collider) return true;

        var fastCollider1 = collider.getFastCollider();
        var fastCollider2 = other.getFastCollider();
        // 快速碰撞检测
        if (fastCollider1 != null && fastCollider2 != null) {
            var colliding = isColliding(fastCollider1, fastCollider2);

            if (!colliding) {
                return false;
            }
        }

        return switch (collider.getType()) {
            case OBB -> switch (other.getType()) {
                case OBB -> isColliding((IOBB<T1, D1>) collider, (IOBB<T2, D2>) other);
                case SPHERE -> isColliding((ISphere<T2, D2>) other, (IOBB<T1, D1>) collider);
                case CAPSULE -> isColliding((ICapsule<T2, D2>) other, (IOBB<T1, D1>) collider);
                case AABB -> isColliding((IOBB<T1, D1>) collider, (IAABB<T2, D2>) other);
                case RAY -> isColliding((IRay<T2, D2>) other, (IOBB<T1, D1>) collider);
                case COMPOSITE -> isColliding((IComposite<ICollider<T2, D2>, T2, D2>) other, collider);
            };
            case SPHERE -> switch (other.getType()) {
                case OBB -> isColliding((ISphere<T1, D1>) collider, (IOBB<T2, D2>) other);
                case SPHERE -> isColliding((ISphere<T1, D1>) collider, (ISphere<T2, D2>) other);
                case CAPSULE -> isColliding((ICapsule<T2, D2>) other, (ISphere<T1, D1>) collider);
                case AABB -> isColliding((ISphere<T1, D1>) collider, (IAABB<T2, D2>) other);
                case RAY -> isColliding((IRay<T2, D2>) other, (ISphere<T1, D1>) collider);
                case COMPOSITE -> isColliding((IComposite<ICollider<T2, D2>, T2, D2>) other, collider);
            };
            case CAPSULE -> switch (other.getType()) {
                case OBB -> isColliding((ICapsule<T1, D1>) collider, (IOBB<T2, D2>) other);
                case SPHERE -> isColliding((ICapsule<T1, D1>) collider, (ISphere<T2, D2>) other);
                case CAPSULE -> isColliding((ICapsule<T1, D1>) collider, (ICapsule<T2, D2>) other);
                case AABB -> isColliding((ICapsule<T1, D1>) collider, (IAABB<T2, D2>) other);
                case RAY -> isColliding((IRay<T2, D2>) other, (ICapsule<T1, D1>) collider);
                case COMPOSITE -> isColliding((IComposite<ICollider<T2, D2>, T2, D2>) other, collider);

            };
            case AABB -> switch (other.getType()) {
                case OBB -> isColliding((IOBB<T2, D2>) other, (IAABB<T1, D1>) collider);
                case SPHERE -> isColliding((ISphere<T2, D2>) other, (IAABB<T1, D1>) collider);
                case CAPSULE -> isColliding((ICapsule<T2, D2>) other, (IAABB<T1, D1>) collider);
                case AABB -> isColliding((IAABB<T1, D1>) collider, (IAABB<T2, D2>) other);
                case RAY -> isColliding((IRay<T2, D2>) other, (IAABB<T1, D1>) collider);
                case COMPOSITE -> isColliding((IComposite<ICollider<T2, D2>, T2, D2>) other, collider);
            };
            case RAY -> switch (other.getType()) {
                case OBB -> isColliding((IRay<T1, D1>) collider, (IOBB<T2, D2>) other);
                case SPHERE -> isColliding((IRay<T1, D1>) collider, (ISphere<T2, D2>) other);
                case CAPSULE -> isColliding((IRay<T1, D1>) collider, (ICapsule<T2, D2>) other);
                case AABB -> isColliding((IRay<T1, D1>) collider, (IAABB<T2, D2>) other);
                case RAY -> isColliding((IRay<T1, D1>) collider, (IRay<T2, D2>) other);
                case COMPOSITE -> isColliding((IComposite<ICollider<T2, D2>, T2, D2>) other, collider);
            };
            case COMPOSITE -> isColliding((IComposite<ICollider<T1, D1>, T1, D1>) collider, other);
        };
    }

    /// 判断两个胶囊体是否碰撞
    ///
    /// @param capsule 胶囊体
    /// @param other   胶囊体
    /// @return 碰撞返回true
    public static boolean isColliding(ICapsule<?, ?> capsule, ICapsule<?, ?> other) {
        //计算头尾点最值
        var h = capsule.getHeight() / 2;
        var pointA1 = capsule.getDirection().mul(h, new Vector3f()).add(capsule.getCenter());
        var pointA2 = capsule.getDirection().mul(-h, new Vector3f()).add(capsule.getCenter());

        h = other.getHeight() / 2;
        var pointB1 = other.getDirection().mul(h, new Vector3f()).add(other.getCenter());
        var pointB2 = other.getDirection().mul(-h, new Vector3f()).add(other.getCenter());

        // 求两条线段的最短距离
        var distance = getClosestDistanceBetweenSegmentsSqr(pointA1, pointA2, pointB1, pointB2);

        //求两个球半径和
        var totalRadius = (float) Math.pow(capsule.getRadius() + other.getRadius(), 2);
        //距离小于等于半径和则碰撞
        return distance <= totalRadius;
    }

    /**
     * 判断胶囊体与球体是否碰撞
     *
     * @param capsule 胶囊体
     * @param sphere  球体
     * @return 有碰撞返回true
     */
    public static boolean isColliding(ICapsule<?, ?> capsule, ISphere<?, ?> sphere) {
        //计算头尾点最值
        var height = capsule.getHeight() / 2;
        var point1 = capsule.getDirection().mul(height, new Vector3f()).add(capsule.getCenter());
        var point2 = capsule.getDirection().mul(-height, new Vector3f()).add(capsule.getCenter());

        var closest = getClosestPointOnSegment(point1, point2, sphere.getCenter());

        //求两个球半径和
        var totalRadius = (float) Math.pow(capsule.getRadius() + sphere.getRadius(), 2);
        //球两个球心之间的距离
        var distance = closest.sub(sphere.getCenter()).lengthSquared();
        //距离小于等于半径和则碰撞
        return distance <= totalRadius;
    }

    /**
     * 判断胶囊体与OBB是否碰撞
     *
     * @param capsule 胶囊体
     * @param obb     OBB盒
     * @return 有碰撞返回true
     */
    public static boolean isColliding(ICapsule<?, ?> capsule, IOBB<?, ?> obb) {
        //计算头尾点最值
        var height = capsule.getHeight() / 2;
        var point1 = capsule.getDirection().mul(height, new Vector3f()).add(capsule.getCenter());
        var point2 = capsule.getDirection().mul(-height, new Vector3f()).add(capsule.getCenter());

        var closest1 = getClosestPointOnSegment(point1, point2, obb.getCenter());
        var closest2 = getClosestPointOBB(closest1, obb);

        //求胶囊体半径平方
        var totalRadius = (float) Math.pow(capsule.getRadius(), 2);
        //求两个点之间的距离
        var distance = (closest1.sub(closest2)).lengthSquared();
        //距离小于等于半径平方则碰撞
        return distance <= totalRadius;
    }

    /**
     * 判断OBB盒与OBB盒是否碰撞
     *
     * @param obb   OBB盒
     * @param other OBB盒
     * @return 有碰撞返回true
     */
    public static boolean isColliding(IOBB<?, ?> obb, IOBB<?, ?> other) {
        //joml居然实现了obb碰撞
        var axes1 = obb.getAxes();
        var axes2 = other.getAxes();
        return Intersectionf.testObOb(obb.getCenter(), axes1[0], axes1[1], axes1[2], obb.getHalfExtents(), other.getCenter(), axes2[0], axes2[1], axes2[2], other.getHalfExtents());
    }

    /**
     * 判断球体与OBB是否碰撞
     *
     * @param sphere 球体
     * @param obb    OBB盒
     * @return 有碰撞返回true
     */
    public static boolean isColliding(ISphere<?, ?> sphere, IOBB<?, ?> obb) {
        //求最近点
        var nearP = getClosestPointOBB(sphere.getCenter(), obb);
        //与AABB检测原理相同
        var distance = nearP.sub(sphere.getCenter()).lengthSquared();
        var radius = (float) Math.pow(sphere.getRadius(), 2);
        return distance <= radius;
    }

    /**
     * 判断球体与球体是否碰撞
     *
     * @param sphere 球体
     * @param other  球体
     * @return 有碰撞返回true
     */
    public static boolean isColliding(ISphere<?, ?> sphere, ISphere<?, ?> other) {
//        return Intersectionf.testSphereSphere(sphere.Center, sphere.radius, other.Center, other.radius);
        return sphere.getCenter().distanceSquared(other.getCenter()) <= Math.pow(sphere.getRadius() + other.getRadius(), 2);
    }

    /**
     * 判断球体与AABB盒是否碰撞
     *
     * @param sphere 球体
     * @param aabb   AABB盒
     * @return 有碰撞返回true
     */
    public static boolean isColliding(ISphere<?, ?> sphere, IAABB<?, ?> aabb) {
        return Intersectionf.testAabSphere(aabb.getMin(), aabb.getMax(), sphere.getCenter(), sphere.getRadius());
        /*//求出最近点
        Vector3f nearP = getClosestPointAABB(center, aabb);
        //求出最近点与球心的距离
        float distance = nearP.sub(center).lengthSquared();
        float radius = (float) Math.pow(sphere.radius, 2);
        //距离小于半径则碰撞
        return distance <= radius;*/
    }

    /**
     * 判断胶囊体与AABB盒是否碰撞
     *
     * @param capsule 胶囊体
     * @param aabb    AABB盒
     * @return 有碰撞返回true
     */
    public static boolean isColliding(ICapsule<?, ?> capsule, IAABB<?, ?> aabb) {
        //计算头尾点最值
        var height = capsule.getHeight() / 2;
        var pointA1 = capsule.getDirection().mul(height, new Vector3f()).add(capsule.getCenter());
        var pointA2 = capsule.getDirection().mul(-height, new Vector3f()).add(capsule.getCenter());

        var closest1 = getClosestPointOnSegment(pointA1, pointA2, aabb.getCenter());
        var closest2 = getClosestPointAABB(closest1, aabb);

        //求胶囊体半径平方
        var totalRadius = (float) Math.pow(capsule.getRadius(), 2);
        //求两个点之间的距离
        var distance = closest1.sub(closest2).lengthSquared();

        //距离小于等于半径平方则碰撞
        return distance <= totalRadius;
    }

    /**
     * 判断射线与AABB盒是否碰撞
     *
     * @param ray  射线
     * @param aabb AABB盒
     * @return 有碰撞返回true
     */
    public static boolean isColliding(IRay<?, ?> ray, IAABB<?, ?> aabb) {
        return Intersectionf.testRayAab(ray.getOrigin(), ray.getDirection(), aabb.getMin(), aabb.getMax());
    }

    /**
     * 判断射线与球体是否碰撞
     *
     * @param ray    射线
     * @param sphere 球体
     * @return 有碰撞返回true
     */
    public static boolean isColliding(IRay<?, ?> ray, ISphere<?, ?> sphere) {
        var origin = ray.getOrigin();
        var end = ray.getEnd();

        var center = sphere.getCenter();
        var radius = sphere.getRadius();

        return Intersectionf.testLineSegmentSphere(
                origin.x, origin.y, origin.z,
                end.x, end.y, end.z,
                center.x, center.y, center.z,
                radius * radius);
    }

    /**
     * 判断射线与OBB盒是否碰撞
     *
     * @param ray 射线
     * @param obb OBB盒
     * @return 有碰撞返回true
     */
    public static boolean isColliding(IRay<?, ?> ray, IOBB<?, ?> obb) {
        var origin = ray.getOrigin();
        var direction = ray.getDirection();

        var v = new Vector3f();
        var center = obb.getCenter();
        var axes = obb.getAxes();
        var halfExtents = obb.getHalfExtents();
        var vertices = obb.getVertices();

        //判断不在OBB内
        var centerDis = origin.sub(center, v);
        var ray2ObbX = centerDis.dot(axes[0]);
        var ray2ObbY = centerDis.dot(axes[1]);
        var ray2ObbZ = centerDis.dot(axes[2]);
        var checkNotInside = ray2ObbX < -halfExtents.x || ray2ObbX > halfExtents.x ||
                ray2ObbY < -halfExtents.y || ray2ObbY > halfExtents.y ||
                ray2ObbZ < -halfExtents.z || ray2ObbZ > halfExtents.z;
        //判断反向情况
        var checkFoward = center.sub(center, v).dot(direction) < 0;

        if (checkNotInside && checkFoward) {
            return false;
        }

        //判断是否相交
        var min = new Vector3f();
        var minP = vertices[4].sub(origin, v);
        min.x = minP.dot(axes[0]);
        min.y = minP.dot(axes[1]);
        min.z = minP.dot(axes[2]);

        var max = new Vector3f();
        var maxP = vertices[2].sub(origin, v);
        max.x = maxP.dot(axes[0]);
        max.y = maxP.dot(axes[1]);
        max.z = maxP.dot(axes[2]);


        var projection = new Vector3f();
        projection.x = 1 / direction.dot(axes[2]);

        var pMin = min.mul(projection);
        var pMax = max.mul(projection);

        if (projection.x < 0) {
            var t = pMin.x;
            pMin.x = pMax.x;
            pMax.x = t;
        }

        if (projection.y < 0) {
            var t = pMin.y;
            pMin.y = pMax.y;
            pMax.y = t;
        }

        if (projection.z < 0) {
            var t = pMin.z;
            pMin.z = pMax.z;
            pMax.z = t;
        }


        var n = Math.max(Math.max(pMin.x, pMin.y), pMin.z);
        var f = Math.min(Math.min(pMax.x, pMax.y), pMax.z);

        if (checkNotInside) {
            return n < f && ray.getLength() >= n;
        }

        return true;
    }

    /**
     * 判断射线与胶囊体是否碰撞
     *
     * @param ray     射线
     * @param capsule 胶囊体
     * @return 有碰撞返回true
     */
    public static boolean isColliding(IRay<?, ?> ray, ICapsule<?, ?> capsule) {
        var halfHeight = capsule.getHeight() / 2.0f;
        var startPoint = capsule.getDirection().mul(-halfHeight, new Vector3f()).add(capsule.getCenter());
        var endPoint = capsule.getDirection().mul(halfHeight, new Vector3f()).add(capsule.getCenter());
        var sqr = getClosestDistanceBetweenSegmentsSqr(ray.getOrigin(), ray.getEnd(), startPoint, endPoint);
        return sqr <= Math.pow(capsule.getRadius(), 2);
    }

    /**
     * 判断射线与射线是否碰撞<br/>
     * 这有必要吗🤣
     *
     * @param ray   射线
     * @param other 射线
     * @return 有碰撞返回true
     */
    public static boolean isColliding(IRay<?, ?> ray, IRay<?, ?> other) {
        return isSegmentCross(ray.getOrigin(), ray.getEnd(), other.getOrigin(), other.getEnd());
    }

    /**
     * 判断两个AABB盒是否碰撞
     *
     * @param aabb  AABB盒
     * @param other AABB盒
     * @return 有碰撞返回true
     */
    public static boolean isColliding(IAABB<?, ?> aabb, IAABB<?, ?> other) {
        return Intersectionf.testAabAab(aabb.getMin(), aabb.getMax(), other.getMin(), other.getMax());
    }

    public static boolean isColliding(IOBB<?, ?> obb, IAABB<?, ?> aabb) {
        var obbCenter = obb.getCenter();
        var obbAxes = obb.getAxes();
        var obbHalfExtents = obb.getHalfExtents();
        var aabbCenter = aabb.getCenter();
        var aabbHalfExtents = aabb.getHalfExtents();
        return Intersectionf.testObOb(
                obbCenter.x, obbCenter.y, obbCenter.z,
                obbAxes[0].x, obbAxes[0].y, obbAxes[0].z,
                obbAxes[1].x, obbAxes[1].y, obbAxes[1].z,
                obbAxes[2].x, obbAxes[2].y, obbAxes[2].z,
                obbHalfExtents.x, obbHalfExtents.y, obbHalfExtents.z,
                aabbCenter.x, aabbCenter.y, aabbCenter.z,
                1, 0, 0,
                0, 1, 0,
                0, 0, 1,
                aabbHalfExtents.x, aabbHalfExtents.y, aabbHalfExtents.z
        );
    }

    /**
     * 判断复合碰撞箱与其他碰撞体是否碰撞
     *
     * @param composite 复合碰撞箱
     * @param other     其他碰撞体
     * @return 有碰撞返回true
     */
    public static <T1, D1, T2, D2> boolean isColliding(IComposite<ICollider<T1, D1>, T1, D1> composite, T1 entity1, D1 data1, ICollider<T2, D2> other, T2 entity2, D2 data2) {
        var count = composite.getCollidersCount();

        for (var i = 0; i < count; i++) {
            if (colliding(composite.getCollider(i), entity1, data1, other, entity2, data2)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 判断复合碰撞箱与其他碰撞体是否碰撞
     *
     * @param composite 复合碰撞箱
     * @param other     其他碰撞体
     * @return 有碰撞返回true
     */
    public static <T1, D1, T2, D2> boolean isColliding(IComposite<ICollider<T1, D1>, T1, D1> composite, ICollider<T2, D2> other) {
        var count = composite.getCollidersCount();

        for (var i = 0; i < count; i++) {
            if (colliding(composite.getCollider(i), other)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 获取线段上最近待判定点的坐标
     *
     * @param start 线段起点
     * @param end   线段终点
     * @param point 待判定点
     * @return 线段上最接近判定点的坐标
     */
    public static Vector3f getClosestPointOnSegment(Vector3f start, Vector3f end, Vector3f point) {
        var se = end.sub(start, new Vector3f());
        var sp = point.sub(start, new Vector3f());
        var f = se.dot(sp) / se.lengthSquared();
        f = Math.min(Math.max(f, 0), 1);
        return se.mul(f).add(start, sp);
    }

    /**
     * 判断线段相交
     *
     * @param start1 线段1起点
     * @param end1   线段1终点
     * @param start2 线段2起点
     * @param end2   线段2终点
     * @return 相交返回true
     */
    private static boolean isSegmentCross(Vector3f start1, Vector3f end1, Vector3f start2, Vector3f end2) {
        if (Math.min(start1.x, end1.x) - Math.max(start2.x, end2.x) > 0.01 ||
                Math.min(start1.y, end1.y) - Math.max(start2.y, end2.y) > 0.01 ||
                Math.min(start1.z, end1.z) - Math.max(start2.z, end2.z) > 0.01 ||
                Math.min(start2.x, end2.x) - Math.max(start1.x, end1.x) > 0.01 ||
                Math.min(start2.y, end2.y) - Math.max(start1.y, end1.y) > 0.01 ||
                Math.min(start2.z, end2.z) - Math.max(start1.z, end1.z) > 0.01) {
            return false;
        }

        var line1 = end1.sub(start1, new Vector3f());
        var line2 = end2.sub(start2, new Vector3f());
        Vector3f v1 = new Vector3f(),
                v2 = new Vector3f();

        return !line1.cross(start2.sub(start1, v1), v1).normalize().equals(line1.cross(end2.sub(start1, v2), v2).normalize()) &&
                !line2.cross(start1.sub(start2, v1), v1).normalize().equals(line2.cross(end1.sub(start2, v2), v2).normalize());
    }

    /**
     * 计算两个线段最近距离的平方
     *
     * @param start1 线段1起点
     * @param end1   线段1终点
     * @param start2 线段2起点
     * @param end2   线段2终点
     * @return 两个线段最近距离的平方
     */
    public static float getClosestDistanceBetweenSegmentsSqr(Vector3f start1, Vector3f end1, Vector3f start2, Vector3f end2) {
        var u = new Vector3f(end1).sub(start1);
        var v = new Vector3f(end2).sub(start2);
        var w = new Vector3f(start1).sub(start2);

        var a = u.dot(u); // u*u
        var b = u.dot(v); // u*v
        var c = v.dot(v); // v*v
        var d = u.dot(w); // u*w
        var e = v.dot(w); // v*w
        var dt = a * c - b * b;

        var sd = dt;
        var td = dt;

        float sn; // sn = be-cd
        float tn; // tn = ae-bd

        if (Math.abs(dt - 0) < 1e-6) {
            // 两直线平行
            sn = 0;    // 在s上指定取s0
            sd = 1;   // 防止计算时除0错误

            tn = e;      // 按(公式3)求tc
            td = c;
        } else {
            sn = (b * e - c * d);
            tn = (a * e - b * d);
            if (sn < 0) {
                // 最近点在s起点以外，同平行条件
                sn = 0;
                tn = e;
                td = c;
            } else if (sn > sd) {
                // 最近点在s终点以外(即sc>1,则取sc=1)
                sn = sd;
                tn = e + b; // 按(公式3)计算
                td = c;
            }
        }
        if (tn < 0.0) {
            // 最近点在t起点以外
            tn = 0;
            if (-d < 0) // 按(公式2)计算，如果等号右边小于0，则sc也小于零，取sc=0
                sn = 0;
            else if (-d > a) // 按(公式2)计算，如果sc大于1，取sc=1
                sn = sd;
            else {
                sn = -d;
                sd = a;
            }
        } else if (tn > td) {
            tn = td;
            if ((-d + b) < 0.0)
                sn = 0;
            else if ((-d + b) > a)
                sn = sd;
            else {
                sn = (-d + b);
                sd = a;
            }
        }

        float sc;
        float tc;

        if (Math.abs(sn - 0) < 1e-6) sc = 0.0F;
        else sc = sn / sd;

        if (Math.abs(tn - 0) < 1e-6) tc = 0.0F;
        else tc = tn / td;

        var dP = new Vector3f(w).add(u.mul(sc)).sub(v.mul(tc));
        return dP.dot(dP);
    }

    /**
     * 计算OBB上离待判定点最近的点
     *
     * @param point 待判定点
     * @param obb   OBB盒
     * @return 在OBB上离待判定点最近的点
     */
    public static Vector3f getClosestPointOBB(Vector3f point, IOBB<?, ?> obb) {
        var nearP = new Vector3f(obb.getCenter());
        //求球心与OBB中心的距离向量 从OBB中心指向球心
        var dist = point.sub(nearP, new Vector3f());

        var extents = new float[]{obb.getHalfExtents().x, obb.getHalfExtents().y, obb.getHalfExtents().z};
        var axes = obb.getAxes();

        for (var i = 0; i < 3; i++) {
            //计算距离向量到OBB坐标轴的投影长度 即距离向量在OBB坐标系中的对应坐标轴的长度
            var distance = dist.dot(axes[i]);
            distance = Math.clamp(distance, -extents[i], extents[i]);
            //还原到世界坐标
            nearP.x += distance * axes[i].x;
            nearP.y += distance * axes[i].y;
            nearP.z += distance * axes[i].z;
        }

        return nearP;
    }

    private static Vector3f getClosestPointAABB(Vector3f point, IAABB<?, ?> aabb) {
        var nearP = new Vector3f();
        var min = aabb.getMin();
        var max = aabb.getMax();
        nearP.x = Math.clamp(point.x, min.x, max.x);
        nearP.y = Math.clamp(point.y, min.y, max.y);
        nearP.z = Math.clamp(point.z, min.z, max.z);
        return nearP;
    }
}
