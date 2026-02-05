package org.academy.api.client.gui.msdf.core;

public class MSDFGeneratorConfig extends GeneratorConfig {
    public ErrorCorrectionConfig errorCorrection;

    public MSDFGeneratorConfig() {
        errorCorrection = new ErrorCorrectionConfig();
    }

    public MSDFGeneratorConfig(boolean overlapSupport, ErrorCorrectionConfig errorCorrection) {
        super(overlapSupport);
        this.errorCorrection = errorCorrection;
    }
}