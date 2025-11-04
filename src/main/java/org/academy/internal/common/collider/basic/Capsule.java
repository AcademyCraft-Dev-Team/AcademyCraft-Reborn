/*
 * This file is based on the “HitboxAPI” project:
 *   https://github.com/AnECanSaiTin/HitboxAPI
 *   Licensed under the GNU General Public License v3.0 or later.
 *
 * Minor modifications made by AcademyCraft-Dev-Team.
 * See the LICENSE file in the project root for the full license text.
 */

package org.academy.internal.common.collider.basic;

import org.academy.api.common.collider.ICapsule;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class Capsule<T, D> implements ICapsule<T, D> {
    private float height;
    private float radius;
    private final Vector3f center;
    private final Quaternionf rotation;
    private boolean disable;

    public Capsule(Vector3f center, Quaternionf rotation, float radius, float height) {
        this.center = center;
        this.rotation = rotation;
        this.radius = radius;
        this.height = height;
    }

    @Override
    public float getHeight() {
        return height;
    }

    @Override
    public void setHeight(float height) {
        this.height = height;
    }

    @Override
    public float getRadius() {
        return radius;
    }

    @Override
    public void setRadius(float radius) {
        this.radius = radius;
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
    public Vector3f getDirection() {
        return rotation.transform(new Vector3f(0, 1, 0));
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
        return "Capsule{" +
                "height=" + height +
                ", radius=" + radius +
                ", center=" + center +
                ", rotation=" + rotation +
                ", disable=" + disable +
                '}';
    }
}
