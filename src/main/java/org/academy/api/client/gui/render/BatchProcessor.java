package org.academy.api.client.gui.render;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import org.academy.api.client.Render;
import org.academy.api.client.gui.command.SubmittedCommand;
import org.joml.Matrix4f;

import javax.annotation.Nullable;
import java.util.*;

public final class BatchProcessor {
    private static final Comparator<SubmittedCommand> COMMAND_COMPARATOR =
            Comparator.comparingDouble((SubmittedCommand cmd) -> cmd.getPose().pose().m32())
                    .thenComparingLong(SubmittedCommand::getDrawOrder)
                    .thenComparingLong(cmd -> cmd.getCommand().getPipeline().getSortKey())
                    .thenComparingLong(SubmittedCommand::getResourceKey)
                    .thenComparing(SubmittedCommand::getScissorRect, Comparator.nullsFirst(ScissorRect::compareTo));

    public static List<MeshToDraw> process(List<SubmittedCommand> commands, float depthEpsilon) {
        if (commands.isEmpty()) return Collections.emptyList();

        commands.sort(COMMAND_COMPARATOR);

        var meshesToDraw = new ArrayList<MeshToDraw>();
        var iterator = commands.iterator();
        var firstCommand = iterator.next();

        var currentPipeline = firstCommand.getCommand().getPipeline();
        var currentScissor = firstCommand.getScissorRect();
        var currentResourceKey = firstCommand.getResourceKey();
        var currentSamplers = firstCommand.getCommand().getSamplers();
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
                finishBatch(meshesToDraw, currentBuilder, currentPipeline, currentScissor, currentSamplers, currentUniforms);

                currentPipeline = command.getPipeline();
                currentScissor = submittedCommand.getScissorRect();
                currentResourceKey = submittedCommand.getResourceKey();
                currentSamplers = command.getSamplers();
                currentUniforms = command.getUniforms();

                currentBuilder = new BufferBuilder(
                        Render.Buffers.getByteBufferBuilder(),
                        currentPipeline.getVertexFormatMode(),
                        currentPipeline.getVertexFormat()
                );
            }

            applyVertices(currentBuilder, submittedCommand, depthEpsilon);
        }

        finishBatch(meshesToDraw, currentBuilder, currentPipeline, currentScissor, currentSamplers, currentUniforms);

        return meshesToDraw;
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
            List<MeshToDraw> meshesToDraw,
            BufferBuilder builder,
            RenderPipeline pipeline,
            @Nullable ScissorRect scissor,
            Map<String, GpuTextureView> samplers,
            Map<String, GpuBufferSlice> uniforms
    ) {
        var meshData = builder.build();
        if (meshData != null)
            meshesToDraw.add(new MeshToDraw(meshData, pipeline, scissor, samplers, uniforms));
    }

    private BatchProcessor() {
    }
}