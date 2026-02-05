package org.academy.api.client.gui.msdf.core;

public class ConvergentCurveOrdering {
    private static void simplifyDegenerateCurve(Point2[] controlPoints, int[] order) {
        if (order[0] == 3) {
            var cond1 = controlPoints[1].equals(controlPoints[0]) || controlPoints[1].equals(controlPoints[3]);
            var cond2 = controlPoints[2].equals(controlPoints[0]) || controlPoints[2].equals(controlPoints[3]);
            if (cond1 && cond2) {
                controlPoints[1] = controlPoints[3];
                order[0] = 1;
            }
        }
        if (order[0] == 2) {
            if (controlPoints[1].equals(controlPoints[0]) || controlPoints[1].equals(controlPoints[2])) {
                controlPoints[1] = controlPoints[2];
                order[0] = 1;
            }
        }
        if (order[0] == 1) {
            if (controlPoints[0].equals(controlPoints[1])) {
                order[0] = 0;
            }
        }
    }

    private static int convergentCurveOrderingInner(Point2[] controlPoints, int aOrder, int bOrder) {
        var corner = controlPoints[4];
        Vector2 a1, a2, a3, b1, b2, b3;

        a1 = Vector2.subtract(controlPoints[3], corner);
        b1 = Vector2.subtract(controlPoints[5], corner);

        a2 = new Vector2();
        b2 = new Vector2();
        a3 = new Vector2();
        b3 = new Vector2();

        if (aOrder >= 2) {
            a2 = Vector2.subtract(Vector2.subtract(controlPoints[2], controlPoints[3]), a1);
        }
        if (bOrder >= 2) {
            b2 = Vector2.subtract(Vector2.subtract(controlPoints[6], controlPoints[5]), b1);
        }
        if (aOrder >= 3) {
            var temp = Vector2.subtract(controlPoints[2], controlPoints[1]);
            a3 = Vector2.subtract(Vector2.subtract(controlPoints[1], controlPoints[2]), temp);
            a3 = Vector2.subtract(a3, a2);
            a2 = Vector2.multiply(a2, 3.0);
        }
        if (bOrder >= 3) {
            var temp = Vector2.subtract(controlPoints[6], controlPoints[5]);
            b3 = Vector2.subtract(Vector2.subtract(controlPoints[7], controlPoints[6]), temp);
            b3 = Vector2.subtract(b3, b2);
            b2 = Vector2.multiply(b2, 3.0);
        }

        a1 = Vector2.multiply(a1, aOrder);
        b1 = Vector2.multiply(b1, bOrder);

        if (!a1.isZero() && !b1.isZero()) {
            var as = a1.length();
            var bs = b1.length();

            if (Math.abs(as * Vector2.crossProduct(a1, b2) + bs * Vector2.crossProduct(a2, b1)) > 1e-12) {
                var d = as * Vector2.crossProduct(a1, b2) + bs * Vector2.crossProduct(a2, b1);
                return Arithmetic.sign(d);
            }

            if (Math.abs(as * as * Vector2.crossProduct(a1, b3) + as * bs * Vector2.crossProduct(a2, b2) + bs * bs * Vector2.crossProduct(a3, b1)) > 1e-12) {
                var d = as * as * Vector2.crossProduct(a1, b3) + as * bs * Vector2.crossProduct(a2, b2) + bs * bs * Vector2.crossProduct(a3, b1);
                return Arithmetic.sign(d);
            }

            if (Math.abs(as * Vector2.crossProduct(a2, b3) + bs * Vector2.crossProduct(a3, b2)) > 1e-12) {
                var d = as * Vector2.crossProduct(a2, b3) + bs * Vector2.crossProduct(a3, b2);
                return Arithmetic.sign(d);
            }

            var d = Vector2.crossProduct(a3, b3);
            return Arithmetic.sign(d);
        }

        var s = 1;
        if (a1.isZero() && !b1.isZero()) {
            var temp = b1;
            b1 = a1;
            a1 = temp;

            temp = b2;
            b2 = a2;
            a2 = temp;

            temp = b3;
            b3 = a3;
            a3 = temp;

            s = -1;
        }

        if (!b1.isZero() && a1.isZero()) {
            if (Math.abs(Vector2.crossProduct(a3, b1)) > 1e-12) {
                var d = Vector2.crossProduct(a3, b1);
                return s * Arithmetic.sign(d);
            }

            if (Math.abs(Vector2.crossProduct(a2, b2)) > 1e-12) {
                var d = Vector2.crossProduct(a2, b2);
                return s * Arithmetic.sign(d);
            }

            if (Math.abs(Vector2.crossProduct(a3, b2)) > 1e-12) {
                var d = Vector2.crossProduct(a3, b2);
                return s * Arithmetic.sign(d);
            }

            if (Math.abs(Vector2.crossProduct(a2, b3)) > 1e-12) {
                var d = Vector2.crossProduct(a2, b3);
                return s * Arithmetic.sign(d);
            }

            var d = Vector2.crossProduct(a3, b3);
            return s * Arithmetic.sign(d);
        }

        if (a1.isZero() && b1.isZero()) {
            if (Math.abs(Math.sqrt(a2.length()) * Vector2.crossProduct(a2, b3) + Math.sqrt(b2.length()) * Vector2.crossProduct(a3, b2)) > 1e-12) {
                var d = Math.sqrt(a2.length()) * Vector2.crossProduct(a2, b3) + Math.sqrt(b2.length()) * Vector2.crossProduct(a3, b2);
                return Arithmetic.sign(d);
            }

            var d = Vector2.crossProduct(a3, b3);
            return Arithmetic.sign(d);
        }

        return 0;
    }

    public static int convergentCurveOrdering(EdgeSegment a, EdgeSegment b) {
        if (a == null || b == null) return 0;

        var aOrder = a.type();
        var bOrder = b.type();

        if (!(aOrder >= 1 && aOrder <= 3 && bOrder >= 1 && bOrder <= 3)) {
            return 0;
        }

        var controlPoints = new Point2[12];
        for (var i = 0; i < 4; i++) controlPoints[i] = new Point2();
        for (var i = 0; i < 8; i++) controlPoints[i + 4] = new Point2();

        var aCpTmp = new Point2[4];
        for (var i = 0; i <= aOrder; ++i) {
            aCpTmp[i] = a.controlPoints()[i];
        }

        var corner = controlPoints[4];
        for (var i = 0; i <= bOrder; ++i) {
            controlPoints[4 + i] = b.controlPoints()[i];
        }

        if (!aCpTmp[aOrder].equals(corner)) {
            return 0;
        }

        var aOrderArr = new int[]{aOrder};
        var bOrderArr = new int[]{bOrder};

        simplifyDegenerateCurve(aCpTmp, aOrderArr);
        simplifyDegenerateCurve(controlPoints, bOrderArr);

        aOrder = aOrderArr[0];
        bOrder = bOrderArr[0];

        if (aOrder >= 0) System.arraycopy(aCpTmp, 0, controlPoints, 4 - aOrder, aOrder);

        return convergentCurveOrderingInner(controlPoints, aOrder, bOrder);
    }
}