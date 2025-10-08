package org.academy.api.client.gui.framework;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import org.academy.AcademyCraft;

import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public final class CommandExecutor {
    private final Map<VertexFormat, MappableRingBuffer> vertexBuffers;

    public CommandExecutor(Map<VertexFormat, MappableRingBuffer> vertexBuffers) {
        this.vertexBuffers = vertexBuffers;
    }

    public void execute(List<MeshToDraw> meshesToDraw, RenderTarget target, GpuBufferSlice projectionUbo, GpuBuffer dynamicTransformsUbo, float guiScale) {
        if (meshesToDraw.isEmpty())
            return;

        ensureVertexBufferSizes(meshesToDraw);

        var safeVertexBuffers = new Object2ObjectOpenHashMap<VertexFormat, GpuBuffer>();
        for (var entry : vertexBuffers.entrySet()) {
            safeVertexBuffers.put(entry.getKey(), entry.getValue().currentBuffer());
        }

        var commandEncoder = RenderSystem.getDevice().createCommandEncoder();
        var baseVertices = new IntArrayList(meshesToDraw.size());
        var writeOffsets = new Object2IntOpenHashMap<VertexFormat>();

        for (var meshToDraw : meshesToDraw) {
            var mesh = meshToDraw.mesh();
            var format = mesh.drawState().format();
            var vertexBufferData = mesh.vertexBuffer();
            var vertexDataSize = vertexBufferData.remaining();
            var currentOffset = writeOffsets.getOrDefault(format, 0);
            var baseVertex = currentOffset / format.getVertexSize();
            var gpuBuffer = safeVertexBuffers.get(format);

            commandEncoder.writeToBuffer(gpuBuffer.slice(currentOffset, vertexDataSize), vertexBufferData);
            writeOffsets.put(format, currentOffset + vertexDataSize);
            baseVertices.add(baseVertex);
        }

        var maxIndexCount = calculateMaxIndexCount(meshesToDraw);
        var sequentialIndexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        var indexBuffer = sequentialIndexBuffer.getBuffer(maxIndexCount);
        var indexType = sequentialIndexBuffer.type();
        var window = Minecraft.getInstance().getWindow();
        var physicalHeight = window.getHeight();

        try (var renderPass = commandEncoder.createRenderPass(
                () -> "UIRender", target.getColorTextureView(), OptionalInt.empty(), target.getDepthTextureView(), OptionalDouble.empty()
        )) {
            for (var i = 0; i < meshesToDraw.size(); i++) {
                var meshToDraw = meshesToDraw.get(i);
                var mesh = meshToDraw.mesh();
                var format = mesh.drawState().format();
                var baseVertex = baseVertices.getInt(i);
                var safeBuffer = safeVertexBuffers.get(format);

                {
                    renderPass.setPipeline(meshToDraw.pipeline());

                    var scissor = meshToDraw.scissorArea();
                    if (scissor != null) {
                        var pos = scissor.getPosition();
                        var screenX = (int) (pos.x * guiScale);
                        var screenWidth = (int) (scissor.getWidth() * guiScale);
                        var screenHeight = (int) (scissor.getHeight() * guiScale);
                        var screenY = (int) (physicalHeight - (pos.y + scissor.getHeight()) * guiScale);
                        renderPass.enableScissor(screenX, screenY, screenWidth, screenHeight);
                    } else {
                        renderPass.disableScissor();
                    }

                    RenderSystem.bindDefaultUniforms(renderPass);
                    renderPass.setUniform("Projection", projectionUbo);
                    renderPass.setUniform("DynamicTransforms", dynamicTransformsUbo);

                    for (var entry : meshToDraw.samplers().entrySet()) {
                        var value = entry.getValue();
                        if (value.isClosed()) {
                            AcademyCraft.LOGGER.error("Sampler {} has been closed", entry.getKey());
                            continue;
                        }
                        renderPass.bindSampler(entry.getKey(), value);
                    }
                    for (var entry : meshToDraw.uniforms().entrySet()) {
                        renderPass.setUniform(entry.getKey(), entry.getValue());
                    }
                }

                {
                    renderPass.setVertexBuffer(0, safeBuffer);
                    renderPass.setIndexBuffer(indexBuffer, indexType);
                    renderPass.drawIndexed(baseVertex, 0, mesh.drawState().indexCount(), 1);
                }

                meshToDraw.close();
            }
        }

        for (var ringBuffer : vertexBuffers.values()) {
            ringBuffer.rotate();
        }
    }

    private void ensureVertexBufferSizes(List<MeshToDraw> meshesToDraw) {
        var requiredSizes = new Object2IntOpenHashMap<VertexFormat>();
        for (var meshToDraw : meshesToDraw) {
            var mesh = meshToDraw.mesh();
            var format = mesh.drawState().format();
            var vertexSize = mesh.vertexBuffer().remaining();
            requiredSizes.addTo(format, vertexSize);
        }

        for (var entry : requiredSizes.object2IntEntrySet()) {
            var format = entry.getKey();
            var requiredSize = entry.getIntValue();
            var ringBuffer = vertexBuffers.get(format);

            if (ringBuffer == null || ringBuffer.size() < requiredSize) {
                if (ringBuffer != null) {
                    ringBuffer.close();
                }
                var usage = GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_WRITE;
                var newBuffer = new MappableRingBuffer(() -> "AC_UI_VB_" + format.toString(), usage, requiredSize);
                vertexBuffers.put(format, newBuffer);
            }
        }
    }

    private int calculateMaxIndexCount(List<MeshToDraw> meshesToDraw) {
        var maxCount = 0;
        for (var meshToDraw : meshesToDraw) {
            maxCount = Math.max(maxCount, meshToDraw.mesh().drawState().indexCount());
        }
        return maxCount;
    }
}