package org.academy.api.client.gui.msdf.core;

import java.util.function.Supplier;
import java.util.stream.IntStream;

public class Msdfgen {
    private static void distanceToSdfPixel(float[] pixels, Double distance, DistanceMapping mapping) {
        pixels[0] = (float) mapping.apply(distance);
    }

    private static void distanceToMsdfPixel(float[] pixels, MultiDistance distance, DistanceMapping mapping) {
        pixels[0] = (float) mapping.apply(distance.r);
        pixels[1] = (float) mapping.apply(distance.g);
        pixels[2] = (float) mapping.apply(distance.b);
    }

    private static void distanceToMtsdfPixel(float[] pixels, MultiAndTrueDistance distance, DistanceMapping mapping) {
        pixels[0] = (float) mapping.apply(distance.r);
        pixels[1] = (float) mapping.apply(distance.g);
        pixels[2] = (float) mapping.apply(distance.b);
        pixels[3] = (float) mapping.apply(distance.a);
    }

    private static <D> void generateDistanceField(FloatBitmapRef output, Shape shape, SDFTransformation transformation, Supplier<ContourCombiner<D>> combinerFactory, DistanceConverter<D> converter) {
        output.reorient(YAxisOrientation.values()[shape.getYAxisOrientation()]);

        IntStream.range(0, output.height).parallel().forEach(y -> {
            var combiner = combinerFactory.get();
            var distanceFinder = new ShapeDistanceFinder<>(shape, combiner);
            var pixel = new float[output.nChannels];
            var p = new Point2();

            var xDirection = (y % 2 == 0) ? 1 : -1;
            var x = (xDirection == 1) ? 0 : output.width - 1;

            for (var col = 0; col < output.width; ++col) {
                p.x = x + 0.5;
                p.y = y + 0.5;
                var unprojected = transformation.unproject(p);
                var distance = distanceFinder.distance(unprojected);
                converter.convert(pixel, distance, transformation.distanceMapping);
                System.arraycopy(pixel, 0, output.pixels, output.getIndex(x, y), output.nChannels);
                x += xDirection;
            }
        });
    }

    public static void generateSDF(FloatBitmapRef output, Shape shape, SDFTransformation transformation, GeneratorConfig config) {
        Supplier<ContourCombiner<Double>> factory = () -> config.overlapSupport ? new OverlappingSDFCombiner(shape) : new SimpleSDFCombiner();
        generateDistanceField(output, shape, transformation, factory, Msdfgen::distanceToSdfPixel);
    }

    public static void generatePSDF(FloatBitmapRef output, Shape shape, SDFTransformation transformation, GeneratorConfig config) {
        Supplier<ContourCombiner<Double>> factory = () -> config.overlapSupport ? new OverlappingPSDFCombiner(shape) : new SimplePSDFCombiner();
        generateDistanceField(output, shape, transformation, factory, Msdfgen::distanceToSdfPixel);
    }

    public static void generateMSDF(FloatBitmapRef output, Shape shape, SDFTransformation transformation, MSDFGeneratorConfig config) {
        Supplier<ContourCombiner<MultiDistance>> factory = () -> config.overlapSupport ? new OverlappingMSDFCombiner(shape) : new SimpleMSDFCombiner();
        generateDistanceField(output, shape, transformation, factory, Msdfgen::distanceToMsdfPixel);
        MsdfErrorCorrectionAPI.msdfErrorCorrection(output, shape, transformation, config);
    }

    public static void generateMTSDF(FloatBitmapRef output, Shape shape, SDFTransformation transformation, MSDFGeneratorConfig config) {
        Supplier<ContourCombiner<MultiAndTrueDistance>> factory = () -> config.overlapSupport ? new OverlappingMTSDFCombiner(shape) : new SimpleMTSDFCombiner();
        generateDistanceField(output, shape, transformation, factory, Msdfgen::distanceToMtsdfPixel);
        MsdfErrorCorrectionAPI.msdfErrorCorrection(output, shape, transformation, config);
    }

    @FunctionalInterface
    private interface DistanceConverter<D> {
        void convert(float[] pixels, D distance, DistanceMapping mapping);
    }
}