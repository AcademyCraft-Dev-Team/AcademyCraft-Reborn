package org.academy.api.client.gui.framework;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import org.academy.api.client.gui.command.SubmittedCommand;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class BatchProcessor {
    private final ByteBufferBuilder sharedByteBufferBuilder;

    private static final Comparator<SubmittedCommand> COMMAND_COMPARATOR =
            Comparator.comparingDouble((SubmittedCommand cmd) -> cmd.getPose().pose().m32())
                    .thenComparingLong(cmd -> cmd.getCommand().getPipeline().getSortKey())
                    .thenComparingLong(SubmittedCommand::getResourceKey)
                    .thenComparing(SubmittedCommand::getScissorRect, Comparator.nullsFirst(ScissorRect::compareTo));

    public BatchProcessor(ByteBufferBuilder sharedByteBufferBuilder) {
        this.sharedByteBufferBuilder = sharedByteBufferBuilder;
    }

    public List<MeshToDraw> process(List<SubmittedCommand> commands) {
        if (commands.isEmpty())
            return Collections.emptyList();

        commands.sort(COMMAND_COMPARATOR);

        var meshesToDraw = new ArrayList<MeshToDraw>();
        var iterator = commands.iterator();
        var firstCommand = iterator.next();

        var currentPipeline = firstCommand.getCommand().getPipeline();
        var currentScissor = firstCommand.getScissorRect();
        var currentResourceKey = firstCommand.getResourceKey();
        var currentSamplers = firstCommand.getCommand().getSamplers();
        var currentUniforms = firstCommand.getCommand().getUniforms();
        var currentZ = firstCommand.getPose().pose().m32();

        var currentBuilder = new BufferBuilder(
                sharedByteBufferBuilder,
                currentPipeline.getVertexFormatMode(),
                currentPipeline.getVertexFormat()
        );

        firstCommand.getCommand().generateVertices(currentBuilder, firstCommand.getPose().pose());

        while (iterator.hasNext()) {
            var submittedCommand = iterator.next();
            var command = submittedCommand.getCommand();

            var shouldBreakBatch = command.getPipeline() != currentPipeline
                    || submittedCommand.getResourceKey() != currentResourceKey
                    || !Objects.equals(submittedCommand.getScissorRect(), currentScissor)
                    || Math.abs(submittedCommand.getPose().pose().m32() - currentZ) > 1e-6f;

            if (shouldBreakBatch) {
                finishBatch(meshesToDraw, currentBuilder, currentPipeline, currentScissor, currentSamplers, currentUniforms);

                currentPipeline = command.getPipeline();
                currentScissor = submittedCommand.getScissorRect();
                currentResourceKey = submittedCommand.getResourceKey();
                currentSamplers = command.getSamplers();
                currentUniforms = command.getUniforms();
                currentZ = submittedCommand.getPose().pose().m32();

                currentBuilder = new BufferBuilder(
                        sharedByteBufferBuilder,
                        currentPipeline.getVertexFormatMode(),
                        currentPipeline.getVertexFormat()
                );
            }

            command.generateVertices(currentBuilder, submittedCommand.getPose().pose());
        }

        finishBatch(meshesToDraw, currentBuilder, currentPipeline, currentScissor, currentSamplers, currentUniforms);

        return meshesToDraw;
    }

    private void finishBatch(
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
}