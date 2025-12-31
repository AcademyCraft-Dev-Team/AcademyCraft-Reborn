package org.academy.api.client.gui.render;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.renderer.DynamicUniformStorage;
import org.academy.api.client.Render;
import org.academy.api.client.gui.command.SubmittedCommand;
import org.academy.api.client.render.TextureBinding;
import org.academy.api.client.render.UniformBinding;
import org.academy.api.client.render.UniformPayload;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class BatchProcessor {
    @FunctionalInterface
    public interface UboUploader {
        <T extends DynamicUniformStorage.DynamicUniform> GpuBufferSlice upload(UniformPayload<T> payload);
    }

    public static List<PendingBatch> process(
            List<SubmittedCommand> commands,
            float depthEpsilon,
            UboUploader uploader
    ) {
        if (commands.isEmpty()) return Collections.emptyList();

        commands.sort(BatchProcessor::compareCommands);

        var batches = new ArrayList<PendingBatch>();
        var iterator = commands.iterator();

        if (!iterator.hasNext()) return batches;

        var firstCommand = iterator.next();
        var state = new BatchState(firstCommand);

        applyVertices(state.builder, firstCommand, depthEpsilon);

        while (iterator.hasNext()) {
            var command = iterator.next();

            if (state.shouldBreakBatch(command)) {
                finishBatch(batches, state, uploader);
                state.reset(command);
            }

            applyVertices(state.builder, command, depthEpsilon);
        }

        finishBatch(batches, state, uploader);

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
        } else command.generateVertices(builder, pose);
    }

    private static void finishBatch(
            List<PendingBatch> batches,
            BatchState state,
            UboUploader uploader
    ) {
        var meshData = state.builder.build();
        if (meshData != null) {
            var bindings = new ArrayList<UniformBinding>(state.uniforms.size());
            for (var payload : state.uniforms) {
                var slice = uploader.upload(payload);
                bindings.add(new UniformBinding(payload.name(), slice));
            }
            batches.add(new PendingBatch(meshData, state.pipeline, state.scissor, state.textures, bindings));
        }
    }

    private static final class BatchState {
        private RenderPipeline pipeline;
        private @Nullable ScissorRect scissor;
        private long resourceKey;
        private List<TextureBinding> textures;
        private List<UniformPayload<?>> uniforms;
        private BufferBuilder builder;

        private BatchState(SubmittedCommand initialCommand) {
            reset(initialCommand);
        }

        private void reset(SubmittedCommand command) {
            var innerCommand = command.getCommand();
            pipeline = innerCommand.getPipeline();
            scissor = command.getScissorRect();
            resourceKey = command.getResourceKey();
            textures = innerCommand.getTextures();
            uniforms = innerCommand.getUniforms();
            builder = new BufferBuilder(
                    Render.Buffers.getByteBufferBuilder(),
                    pipeline.getVertexFormatMode(),
                    pipeline.getVertexFormat()
            );
        }

        private boolean shouldBreakBatch(SubmittedCommand nextCommand) {
            var nextInner = nextCommand.getCommand();
            return nextInner.getPipeline() != pipeline
                    || nextCommand.getResourceKey() != resourceKey
                    || !Objects.equals(nextCommand.getScissorRect(), scissor);
        }
    }

    private BatchProcessor() {
    }
}