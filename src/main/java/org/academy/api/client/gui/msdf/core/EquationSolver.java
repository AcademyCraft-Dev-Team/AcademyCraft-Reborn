package org.academy.api.client.gui.msdf.core;

public class EquationSolver {
    public static int solveQuadratic(double[] x, double a, double b, double c) {
        if (a == 0 || Math.abs(b) > 1e12 * Math.abs(a)) {
            if (b == 0) {
                if (c == 0) return -1;
                return 0;
            }
            x[0] = -c / b;
            return 1;
        }
        var dscr = b * b - 4 * a * c;
        if (dscr > 0) {
            dscr = Math.sqrt(dscr);
            var div = 1.0 / (2 * a);
            x[0] = (-b + dscr) * div;
            x[1] = (-b - dscr) * div;
            return 2;
        } else if (dscr == 0) {
            x[0] = -b / (2 * a);
            return 1;
        } else {
            return 0;
        }
    }

    private static int solveCubicNormed(double[] x, double a, double b, double c) {
        var a2 = a * a;
        var q = (a2 - 3 * b) * 0.11111111111111111;
        var r = (a * (2 * a2 - 9 * b) + 27 * c) * 0.01851851851851852;
        var r2 = r * r;
        var q3 = q * q * q;
        a *= 0.33333333333333333;

        if (r2 < q3) {
            var t = r / Math.sqrt(q3);
            if (t < -1) t = -1;
            if (t > 1) t = 1;
            t = Math.acos(t);
            q = -2 * Math.sqrt(q);
            x[0] = q * Math.cos(t * 0.33333333333333333) - a;
            x[1] = q * Math.cos((t + 2 * Math.PI) * 0.33333333333333333) - a;
            x[2] = q * Math.cos((t - 2 * Math.PI) * 0.33333333333333333) - a;
            return 3;
        } else {
            var A = Math.abs(r) + Math.sqrt(r2 - q3);
            var u = (r < 0 ? 1 : -1) * Math.cbrt(A);
            var v = u == 0 ? 0 : q / u;
            x[0] = (u + v) - a;
            if (u == v || Math.abs(u - v) < 1e-12 * Math.abs(u + v)) {
                x[1] = -0.5 * (u + v) - a;
                return 2;
            }
            return 1;
        }
    }

    public static int solveCubic(double[] x, double a, double b, double c, double d) {
        if (a != 0) {
            var bn = b / a;
            if (Math.abs(bn) < 1e6) {
                return solveCubicNormed(x, bn, c / a, d / a);
            }
        }
        return solveQuadratic(x, b, c, d);
    }
}