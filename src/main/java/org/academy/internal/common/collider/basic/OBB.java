/*
 * This file is based on the “HitboxAPI” project:
 *   https://github.com/AnECanSaiTin/HitboxAPI
 *   Licensed under the GNU General Public License v3.0 or later.
 *
 * Minor modifications made by AcademyCraft-Dev-Team.
 * See the LICENSE file in the project root for the full license text.
 */

package org.academy.internal.common.collider.basic;

import org.academy.api.common.collider.IOBB;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class OBB<T, D> implements IOBB<T, D> {
    private final Vector3f halfExtents;
    private final Vector3f center;
    private final Quaternionf rotation;
    private boolean disable;

    public OBB(Vector3f halfExtents, Vector3f center, Quaternionf rotation) {
        this.halfExtents = halfExtents;
        this.center = center;
        this.rotation = rotation;
    }

    @Override
    public Vector3f getHalfExtents() {
        return halfExtents;
    }

    @Override
    public void setHalfExtents(Vector3f halfExtents) {
        this.halfExtents.set(halfExtents);
    }

    @Override
    public Vector3f getCenter() {
        return center;
    }

    @Override
    public void setCenter(Vector3f center) {
        this.center.set(center);
    }

    @Override
    public Quaternionf getRotation() {
        return rotation;
    }

    @Override
    public void setRotation(Quaternionf rotation) {
        this.rotation.set(rotation);
    }

    @Override
    public Vector3f[] getVertices() {
        var vertices = new Vector3f[8];

        // 计算局部坐标系下的顶点位置
        var localVertices = new Vector3f[]{
                new Vector3f(-halfExtents.x, -halfExtents.y, -halfExtents.z),
                new Vector3f(halfExtents.x, -halfExtents.y, -halfExtents.z),
                new Vector3f(halfExtents.x, halfExtents.y, -halfExtents.z),
                new Vector3f(-halfExtents.x, halfExtents.y, -halfExtents.z),
                new Vector3f(-halfExtents.x, -halfExtents.y, halfExtents.z),
                new Vector3f(halfExtents.x, -halfExtents.y, halfExtents.z),
                new Vector3f(halfExtents.x, halfExtents.y, halfExtents.z),
                new Vector3f(-halfExtents.x, halfExtents.y, halfExtents.z)
        };

        // 将局部坐标系下的顶点转换到世界坐标系
        for (var i = 0; i < 8; i++) {
            var vertex = localVertices[i];
            vertex.rotate(rotation);
            vertex.add(center);
            vertices[i] = vertex;
        }

        return vertices;
    }

    @Override
    public Vector3f[] getAxes() {
        var axes = new Vector3f[]{
                new Vector3f(1, 0, 0),
                new Vector3f(0, 1, 0),
                new Vector3f(0, 0, 1)};
        rotation.transform(axes[0]);
        rotation.transform(axes[1]);
        rotation.transform(axes[2]);
        return axes;
    }

    @Override
    public void setDisable(boolean disable) {
        this.disable = disable;
    }

    @Override
    public boolean disable() {
        return disable;
    }

    @Override
    public String toString() {
        return "OBB{" +
                "halfExtents=" + halfExtents +
                ", center=" + center +
                ", rotation=" + rotation +
                ", disable=" + disable +
                '}';
    }
}
