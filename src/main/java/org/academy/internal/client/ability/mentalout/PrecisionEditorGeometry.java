package org.academy.internal.client.ability.mentalout;

import net.minecraft.util.Mth;

final class PrecisionEditorGeometry {
    private PrecisionEditorGeometry() {
    }

    static Layout layout(int screenWidth, int screenHeight) {
        var panelX = 2;
        var panelY = 2;
        var panelW = Math.max(1, screenWidth - 4);
        var panelH = Math.max(1, screenHeight - 4);
        var compactLeft = panelW < 560;
        var compactRight = panelW < 760;
        var leftW = compactLeft ? 18 : panelW < 760 ? 96 : 112;
        var rightW = compactRight ? 18 : 128;
        var leftX = panelX;
        var rightX = panelX + panelW - rightW;
        var canvasX = leftX + leftW + 3;
        var canvasY = panelY + 22;
        var canvasW = Math.max(40, rightX - canvasX - 3);
        var canvasH = Math.max(40, panelH - 40);
        return new Layout(
                panelX, panelY, panelW, panelH,
                leftX, leftW, rightX, rightW,
                canvasX, canvasY, canvasW, canvasH,
                compactLeft, compactRight
        );
    }

    static double screenToGraph(double screen, double origin, double pan, double zoom) {
        return (screen - origin - pan) / zoom;
    }

    static double graphToScreen(double graph, double origin, double pan, double zoom) {
        return origin + pan + graph * zoom;
    }

    static View zoomAt(
            double mouseX,
            double mouseY,
            double canvasX,
            double canvasY,
            double panX,
            double panY,
            double currentZoom,
            double requestedZoom
    ) {
        var graphX = screenToGraph(mouseX, canvasX, panX, currentZoom);
        var graphY = screenToGraph(mouseY, canvasY, panY, currentZoom);
        var zoom = Mth.clamp(requestedZoom, PrecisionOperationScreen.MIN_ZOOM, PrecisionOperationScreen.MAX_ZOOM);
        return new View(
                mouseX - canvasX - graphX * zoom,
                mouseY - canvasY - graphY * zoom,
                zoom
        );
    }

    record Layout(
            int panelX,
            int panelY,
            int panelW,
            int panelH,
            int leftX,
            int leftW,
            int rightX,
            int rightW,
            int canvasX,
            int canvasY,
            int canvasW,
            int canvasH,
            boolean compactLeft,
            boolean compactRight
    ) {
    }

    record View(double panX, double panY, double zoom) {
    }
}
