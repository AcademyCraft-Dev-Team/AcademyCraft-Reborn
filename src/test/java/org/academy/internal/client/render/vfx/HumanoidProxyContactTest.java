package org.academy.internal.client.render.vfx;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HumanoidProxyContactTest {
    private static final Vector3f[] MIN = {
            pixels(-4, 0, -2), pixels(-4, -8, -4), pixels(-3, -2, -2),
            pixels(-1, -2, -2), pixels(-2, 0, -2), pixels(-2, 0, -2)};
    private static final Vector3f[] MAX = {
            pixels(4, 12, 2), pixels(4, 0, 4), pixels(1, 10, 2),
            pixels(3, 10, 2), pixels(2, 12, 2), pixels(2, 12, 2)};

    @Test void resolvesAbdomenHeadBothArmsAndBothLegsInTheirAnatomicalRegions() {
        var root = playerRoot(0, .9375f);
        assertEquals(0, selectModelPoint(pixels(0, 8.5f, -5), root));
        assertEquals(1, selectModelPoint(pixels(0, -4, -5), root));
        assertEquals(2, selectModelPoint(pixels(-6, 8, -5), root));
        assertEquals(3, selectModelPoint(pixels(6, 8, -5), root));
        assertEquals(4, selectModelPoint(pixels(-2, 20, -5), root));
        assertEquals(5, selectModelPoint(pixels(2, 20, -5), root));
    }

    @Test void proxyIdentityIsIndependentOfBodyYawRootTiltAndScale() {
        Vector3f[] contacts = {pixels(0, 8.5f, -5), pixels(0, -4, -5), pixels(-6, 8, -5),
                pixels(6, 8, -5), pixels(-2, 20, -5), pixels(2, 20, -5)};
        for (float yaw : new float[]{-135, -60, 0, 75, 180}) {
            for (float scale : new float[]{.5f, .9375f, 2}) {
                var root = playerRoot(yaw, scale).rotateX(.35f).rotateZ(-.2f);
                for (int part = 0; part < contacts.length; part++)
                    assertEquals(part, selectModelPoint(contacts[part], root));
            }
        }
    }

    @Test void aRaisedLegCannotStealTheRecordedAbdominalAabbContact() {
        var root = playerRoot(0, .9375f);
        var contact = new Vector3f(0, .9f, .3f);
        var firstLegPose = new Matrix4f(root).translate(1.9f / 16, .75f, 0).rotateX(-1.7f);
        var otherLegPose = new Matrix4f(root).translate(1.9f / 16, .75f, 0).rotateX(.8f);
        float torsoDistance = distanceToBox(contact, root, MIN[0], MAX[0]);
        assertTrue(distanceToBox(contact, firstLegPose, MIN[5], MAX[5]) < torsoDistance,
                "A sufficiently raised leg reproduces the unstable closest-animated-part choice");
        assertTrue(distanceToBox(contact, otherLegPose, MIN[5], MAX[5]) > torsoDistance);
        int part = HumanoidProxyContact.select(contact, root, MIN, MAX);
        assertEquals(0, part);
        // Animation changes where a selected part is posed, not the identity of the AABB contact.
        var legAnchor = pixels(0, 6, -2);
        assertTrue(firstLegPose.transformPosition(new Vector3f(legAnchor))
                .distance(otherLegPose.transformPosition(new Vector3f(legAnchor))) > .5f);
        assertEquals(part, HumanoidProxyContact.select(contact, root, MIN, MAX));
    }

    @Test void armorInflationIsRespectedWithoutMovingCentralHitsToAnotherPart() {
        var min = new Vector3f[6];
        var max = new Vector3f[6];
        for (int part = 0; part < 6; part++) {
            min[part] = new Vector3f(MIN[part]).sub(.07f, .07f, .07f);
            max[part] = new Vector3f(MAX[part]).add(.07f, .07f, .07f);
        }
        assertEquals(0, HumanoidProxyContact.select(new Vector3f(0, .9f, .3f), playerRoot(0, .9375f), min, max));
    }

    private static int selectModelPoint(Vector3f point, Matrix4f root) {
        return HumanoidProxyContact.select(root.transformPosition(new Vector3f(point)), root, MIN, MAX);
    }

    private static float distanceToBox(Vector3f point, Matrix4f transform, Vector3f min, Vector3f max) {
        var nearest = new Matrix4f(transform).invert().transformPosition(new Vector3f(point)).max(min).min(max);
        return transform.transformPosition(nearest).distanceSquared(point);
    }

    private static Matrix4f playerRoot(float yaw, float scale) {
        return new Matrix4f().rotateY((float) Math.toRadians(180 - yaw)).scale(-scale, -scale, scale).translate(0, -1.501f, 0);
    }

    private static Vector3f pixels(float x, float y, float z) { return new Vector3f(x, y, z).div(16); }
}
