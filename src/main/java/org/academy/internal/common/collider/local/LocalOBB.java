/*
 * This file is based on the “HitboxAPI” project:
 *   https://github.com/AnECanSaiTin/HitboxAPI
 *   Licensed under the GNU General Public License v3.0 or later.
 *
 * Minor modifications made by AcademyCraft-Dev-Team.
 * See the LICENSE file in the project root for the full license text.
 */

package org.academy.internal.common.collider.local;

import org.academy.api.common.collider.local.ILocalOBB;
import org.academy.api.common.collider.local.ICoordinateConverter;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class LocalOBB<T, D> implements ILocalOBB<T, D> {
    private final Vector3f halfExtents;
    private final Vector3f localCenter;
    private final Quaternionf localRotation;
    private final Vector3f globalCenter = new Vector3f();
    private final Quaternionf globalRotation = new Quaternionf();
    private final Vector3f[] vertices = new Vector3f[8];
    private final Vector3f[] axes = new Vector3f[3];
    private final ICoordinateConverter parent;
    /// 0 - 中心点, 1 - 旋转
    private final short[] version = new short[2];
    /// 0 - 中心点, 1 - 旋转, 2 - 轴半长
    private final boolean[] dirty = new boolean[]{true, true, true};
    private boolean disable;

    public LocalOBB(Vector3f halfExtents, Vector3f localCenter, Quaternionf localRotation, ICoordinateConverter parent) {
        this.halfExtents = halfExtents;
        this.localCenter = localCenter;
        this.localRotation = localRotation;
        this.parent = parent;
        version[0] = (short) (parent.positionVersion() - 1);
        version[1] = (short) (parent.rotationVersion() - 1);

        for (int i = 0, globalVerticesLength = vertices.length; i < globalVerticesLength; i++) {
            vertices[i] = new Vector3f();
        }

        for (int i = 0, globalAxesLength = axes.length; i < globalAxesLength; i++) {
            axes[i] = new Vector3f();
        }
    }

    @Override
    public Vector3f getLocalCenter() {
        return localCenter;
    }

    @Override
    public void setLocalCenter(Vector3f center) {
        dirty[0] = true;
        localCenter.set(center);
    }

    @Override
    public Quaternionf getLocalRotation() {
        return localRotation;
    }

    @Override
    public void setLocalRotation(Quaternionf rotation) {
        dirty[1] = true;
        localRotation.set(rotation);
    }

    @Override
    public Vector3f getHalfExtents() {
        return halfExtents;
    }

    @Override
    public void setHalfExtents(Vector3f halfExtents) {
        dirty[2] = true;
        this.halfExtents.set(halfExtents);
    }

    @Override
    public Vector3f getCenter() {
        update();
        return globalCenter;
    }

    @Override
    public void setCenter(Vector3f center) {
        dirty[0] = true;
        version[0] = parent.positionVersion();
        version[1] = parent.rotationVersion();
        var position = parent.getPosition();
        var rotation = parent.getRotation().conjugate(new Quaternionf());
        localCenter.set(center).sub(position).rotate(rotation);
        globalCenter.set(center);
    }

    @Override
    public Quaternionf getRotation() {
        update();
        return globalRotation;
    }

    @Override
    public void setRotation(Quaternionf rotation) {
        dirty[1] = true;
        version[1] = parent.rotationVersion();
        localRotation.set(rotation).mul(parent.getRotation().conjugate(new Quaternionf()));
        globalRotation.set(rotation);
    }

    @Override
    public Vector3f[] getVertices() {
        update();
        return vertices;
    }

    @Override
    public Vector3f[] getAxes() {
        update();
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

    protected void setCenterDirty() {
        dirty[0] = true;
    }

    protected void setRotationDirty() {
        dirty[1] = true;
    }

    protected void setHalfExtentsDirty() {
        dirty[2] = true;
    }

    private void update() {
        if ((!dirty[1] && !dirty[2] && (version[0] != parent.positionVersion() && version[1] == parent.rotationVersion()) || dirty[0])) {
            dirty[0] = false;
            version[0] = parent.positionVersion();
            //仅进行位置移动
            var position = parent.getPosition();
            var rotation = parent.getRotation();
            var center = rotation.transform(localCenter, new Vector3f()).add(position);
            var offset = center.sub(globalCenter, new Vector3f());
            globalCenter.set(center);

            for (var vertex : vertices) {
                vertex.add(offset);
            }

            return;
        } else if (version[0] == parent.positionVersion() && version[1] == parent.rotationVersion() && !dirty[0] && !dirty[1] && !dirty[2]) {
            return;
        }

        dirty[0] = false;
        dirty[1] = false;
        dirty[2] = false;
        version[0] = parent.positionVersion();
        version[1] = parent.rotationVersion();
        var position = parent.getPosition();
        var rotation = parent.getRotation();
        var center = rotation.transform(localCenter, globalCenter).add(position);
        rotation.mul(localRotation, globalRotation);

        // 旋转轴向
        axes[0].set(1, 0, 0);
        axes[1].set(0, 1, 0);
        axes[2].set(0, 0, 1);


        for (var axe : axes) {
            globalRotation.transform(axe);
        }

        var v = new Vector3f();

        // 计算顶点
        vertices[0].set(center).add(axes[0].mul(halfExtents.x, v)).add(axes[1].mul(halfExtents.y, v)).add(axes[2].mul(halfExtents.z, v));
        vertices[1].set(center).add(axes[0].mul(halfExtents.x, v)).add(axes[1].mul(halfExtents.y, v)).sub(axes[2].mul(halfExtents.z, v));
        vertices[2].set(center).add(axes[0].mul(halfExtents.x, v)).sub(axes[1].mul(halfExtents.y, v)).add(axes[2].mul(halfExtents.z, v));
        vertices[3].set(center).add(axes[0].mul(halfExtents.x, v)).sub(axes[1].mul(halfExtents.y, v)).sub(axes[2].mul(halfExtents.z, v));
        vertices[4].set(center).sub(axes[0].mul(halfExtents.x, v)).add(axes[1].mul(halfExtents.y, v)).add(axes[2].mul(halfExtents.z, v));
        vertices[5].set(center).sub(axes[0].mul(halfExtents.x, v)).add(axes[1].mul(halfExtents.y, v)).sub(axes[2].mul(halfExtents.z, v));
        vertices[6].set(center).sub(axes[0].mul(halfExtents.x, v)).sub(axes[1].mul(halfExtents.y, v)).add(axes[2].mul(halfExtents.z, v));
        vertices[7].set(center).sub(axes[0].mul(halfExtents.x, v)).sub(axes[1].mul(halfExtents.y, v)).sub(axes[2].mul(halfExtents.z, v));
    }
}
