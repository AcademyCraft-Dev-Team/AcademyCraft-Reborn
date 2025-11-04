/*
 * This file is based on the “HitboxAPI” project:
 *   https://github.com/AnECanSaiTin/HitboxAPI
 *   Licensed under the GNU General Public License v3.0 or later.
 *
 * Minor modifications made by AcademyCraft-Dev-Team.
 * See the LICENSE file in the project root for the full license text.
 */

package org.academy.internal.common.collider.basic;

import org.academy.api.common.collider.IRay;
import org.joml.Vector3f;

public class Ray<T, D> implements IRay<T, D> {
    private float length;
    private final Vector3f origin;
    private final Vector3f direction;
    private boolean disable;

    public Ray(Vector3f origin, float length, Vector3f direction) {
        this.origin = origin;
        this.length = length;
        this.direction = direction;
    }

    @Override
    public float getLength() {
        return length;
    }

    @Override
    public void setLength(float length) {
        this.length = length;
    }

    @Override
    public Vector3f getOrigin() {
        return origin;
    }

    @Override
    public Vector3f getEnd() {
        return new Vector3f(origin).add(direction.mul(length));
    }

    @Override
    public Vector3f getDirection() {
        return direction;
    }

    @Override
    public void setDirection(Vector3f direction) {
        this.direction.set(direction);
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
        return "Ray{" +
                "length=" + length +
                ", origin=" + origin +
                ", direction=" + direction +
                ", disable=" + disable +
                '}';
    }
}
