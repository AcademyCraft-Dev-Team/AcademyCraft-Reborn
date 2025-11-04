/*
 * This file is based on the “HitboxAPI” project:
 *   https://github.com/AnECanSaiTin/HitboxAPI
 *   Licensed under the GNU General Public License v3.0 or later.
 *
 * Minor modifications made by AcademyCraft-Dev-Team.
 * See the LICENSE file in the project root for the full license text.
 */

package org.academy.internal.common.collider.basic;

import org.academy.api.common.collider.ICollider;
import org.academy.api.common.collider.IComposite;

import java.util.List;

public class Composite<C extends ICollider<T, D>, T, D> implements IComposite<C, T, D> {
    private final List<C> colliders;
    private boolean disable;

    public Composite(List<C> colliders) {
        this.colliders = colliders;
    }

    @Override
    public int getCollidersCount() {
        return colliders.size();
    }

    @Override
    public C getCollider(int index) {
        return colliders.get(index);
    }

    @Override
    public void setCollider(int index, C collider) {
        colliders.set(index, collider);
    }

    @Override
    public void addCollider(C collider) {
        colliders.add(collider);
    }

    @Override
    public void removeCollider(int index) {
        colliders.remove(index);
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
        return "Composite{" +
                "colliders=" + colliders +
                ", disable=" + disable +
                '}';
    }
}
