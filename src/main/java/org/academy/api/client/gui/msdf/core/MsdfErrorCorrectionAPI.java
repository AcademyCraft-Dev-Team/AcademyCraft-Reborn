package org.academy.api.client.gui.msdf.core;

public class MsdfErrorCorrectionAPI {
    public static void msdfErrorCorrection(FloatBitmapRef sdf, Shape shape, SDFTransformation transformation, MSDFGeneratorConfig config) {
        if (config.errorCorrection.mode == ErrorCorrectionConfig.Mode.DISABLED) {
            return;
        }

        ByteBitmap stencilBuffer = null;
        if (config.errorCorrection.buffer == null) {
            stencilBuffer = new ByteBitmap(sdf.width, sdf.height);
        }
        var stencil = new ByteBitmapRef(
                config.errorCorrection.buffer != null ? config.errorCorrection.buffer : stencilBuffer.pixels,
                sdf.width,
                sdf.height,
                sdf.width,
                YAxisOrientation.Y_UPWARD
        );

        var ec = new MSDFErrorCorrection(stencil, transformation);
        ec.setMinDeviationRatio(config.errorCorrection.minDeviationRatio);
        ec.setMinImproveRatio(config.errorCorrection.minImproveRatio);

        switch (config.errorCorrection.mode) {
            case DISABLED:
            case INDISCRIMINATE:
                break;
            case EDGE_PRIORITY:
                ec.protectCorners(shape);
                ec.protectEdges(sdf);
                break;
            case EDGE_ONLY:
                ec.protectAll();
                break;
        }

        if (config.errorCorrection.distanceCheckMode == ErrorCorrectionConfig.DistanceCheckMode.DO_NOT_CHECK_DISTANCE ||
                (config.errorCorrection.distanceCheckMode == ErrorCorrectionConfig.DistanceCheckMode.CHECK_DISTANCE_AT_EDGE && config.errorCorrection.mode != ErrorCorrectionConfig.Mode.EDGE_ONLY)) {
            ec.findErrors(sdf);
            if (config.errorCorrection.distanceCheckMode == ErrorCorrectionConfig.DistanceCheckMode.CHECK_DISTANCE_AT_EDGE) {
                ec.protectAll();
            }
        }

        if (config.errorCorrection.distanceCheckMode == ErrorCorrectionConfig.DistanceCheckMode.ALWAYS_CHECK_DISTANCE ||
                config.errorCorrection.distanceCheckMode == ErrorCorrectionConfig.DistanceCheckMode.CHECK_DISTANCE_AT_EDGE) {
            ec.findErrors(sdf, shape, config.overlapSupport);
        }
        ec.apply(sdf);
    }
}