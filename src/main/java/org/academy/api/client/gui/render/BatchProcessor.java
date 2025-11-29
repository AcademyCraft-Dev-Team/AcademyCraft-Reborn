package org.academy.api.client.gui.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.BufferBuilder;
import org.academy.api.client.Render;
import org.academy.api.client.gui.command.SubmittedCommand;
import org.academy.api.client.render.TextureBinding;
import org.academy.api.client.render.UniformBinding;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class BatchProcessor {

    public static List<PendingBatch> process(List<SubmittedCommand> commands, float depthEpsilon) {
        if (commands.isEmpty()) return Collections.emptyList();

        commands.sort(BatchProcessor::compareCommands);

        var batches = new ArrayList<PendingBatch>();
        var iterator = commands.iterator();
        var firstCommand = iterator.next();

        var currentPipeline = firstCommand.getCommand().getPipeline();
        var currentScissor = firstCommand.getScissorRect();
        var currentResourceKey = firstCommand.getResourceKey();
        var currentTextures = firstCommand.getCommand().getTextures();
        var currentUniforms = firstCommand.getCommand().getUniforms();

        var currentBuilder = new BufferBuilder(
                Render.Buffers.getByteBufferBuilder(),
                currentPipeline.getVertexFormatMode(),
                currentPipeline.getVertexFormat()
        );

        applyVertices(currentBuilder, firstCommand, depthEpsilon);

        while (iterator.hasNext()) {
            var submittedCommand = iterator.next();
            var command = submittedCommand.getCommand();

            var shouldBreakBatch = command.getPipeline() != currentPipeline
                    || submittedCommand.getResourceKey() != currentResourceKey
                    || !Objects.equals(submittedCommand.getScissorRect(), currentScissor);

            if (shouldBreakBatch) {
                finishBatch(batches, currentBuilder, currentPipeline, currentScissor, currentTextures, currentUniforms);

                currentPipeline = command.getPipeline();
                currentScissor = submittedCommand.getScissorRect();
                currentResourceKey = submittedCommand.getResourceKey();
                currentTextures = command.getTextures();
                currentUniforms = command.getUniforms();

                currentBuilder = new BufferBuilder(
                        Render.Buffers.getByteBufferBuilder(),
                        currentPipeline.getVertexFormatMode(),
                        currentPipeline.getVertexFormat()
                );
            }

            applyVertices(currentBuilder, submittedCommand, depthEpsilon);
        }

        finishBatch(batches, currentBuilder, currentPipeline, currentScissor, currentTextures, currentUniforms);

        return batches;
    }

    private static int compareCommands(SubmittedCommand c1, SubmittedCommand c2) {
        var pose1 = c1.getPose().pose().m32();
        var pose2 = c2.getPose().pose().m32();
        var zCompare = Double.compare(pose1, pose2);
        if (zCompare != 0) return zCompare;

        var orderCompare = Long.compare(c1.getDrawOrder(), c2.getDrawOrder());
        if (orderCompare != 0) return orderCompare;

        var pipelineCompare = Integer.compare(c1.getCommand().getPipeline().getSortKey(), c2.getCommand().getPipeline().getSortKey());
        if (pipelineCompare != 0) return pipelineCompare;

        var resCompare = Long.compare(c1.getResourceKey(), c2.getResourceKey());
        if (resCompare != 0) return resCompare;

        var s1 = c1.getScissorRect();
        var s2 = c2.getScissorRect();
        if (s1 == null && s2 == null) return 0;
        if (s1 == null) return -1;
        if (s2 == null) return 1;
        return s1.compareTo(s2);
    }

    private static void applyVertices(BufferBuilder builder, SubmittedCommand submittedCommand, float depthEpsilon) {
        var command = submittedCommand.getCommand();
        var pose = submittedCommand.getPose().pose();
        var sequenceId = submittedCommand.getDrawOrder();

        if (sequenceId > 0 && depthEpsilon > 0) {
            var tempPose = new Matrix4f(pose);
            tempPose.translate(0, 0, sequenceId * depthEpsilon);
            command.generateVertices(builder, tempPose);
        } else {
            command.generateVertices(builder, pose);
        }
    }

    private static void finishBatch(
            List<PendingBatch> batches,
            BufferBuilder builder,
            RenderPipeline pipeline,
            @Nullable ScissorRect scissor,
            List<TextureBinding> textures,
            List<UniformBinding> uniforms
    ) {
        var meshData = builder.build();
        if (meshData != null) batches.add(new PendingBatch(meshData, pipeline, scissor, textures, uniforms));
    }

    private BatchProcessor() {
    }
}