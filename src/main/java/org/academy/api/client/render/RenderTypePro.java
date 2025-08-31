package org.academy.api.client.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.ScissorState;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public class RenderTypePro extends RenderType {
    public final RenderType.CompositeState state;
    public final RenderPipeline renderPipeline;
    public final Map<String, GpuBufferSlice> uniforms = new HashMap<>();

    public RenderTypePro(
            String name, int bufferSize, boolean affectsCrumbling, boolean sortOnUpload, RenderPipeline renderPipeline, RenderType.CompositeState state
    ) {
        super(
                name,
                bufferSize,
                affectsCrumbling,
                sortOnUpload,
                () -> state.states.forEach(RenderStateShard::setupRenderState),
                () -> state.states.forEach(RenderStateShard::clearRenderState)
        );
        this.state = state;
        this.renderPipeline = renderPipeline;
    }

    public RenderTypePro(RenderType.CompositeRenderType renderType) {
        this(renderType.getName(),
                renderType.bufferSize(),
                renderType.affectsCrumbling(),
                renderType.sortOnUpload(),
                renderType.renderPipeline,
                renderType.state
        );
    }

    @Override
    public VertexFormat format() {
        return this.renderPipeline.getVertexFormat();
    }

    @Override
    public VertexFormat.Mode mode() {
        return this.renderPipeline.getVertexFormatMode();
    }

    @Override
    public void draw(MeshData meshdata) {
        this.setupRenderState();
        var gpubufferslice = RenderSystem.getDynamicUniforms()
                .writeTransform(new Matrix4f().setTranslation(0.0F, 0.0F, -11000.0F), new Vector4f(1.0F, 1.0F, 1.0F, 1.0F), new Vector3f(), new Matrix4f(), 0.0F);

        try {
            GpuBuffer gpubuffer = this.renderPipeline.getVertexFormat().uploadImmediateVertexBuffer(meshdata.vertexBuffer());
            GpuBuffer gpubuffer1;
            VertexFormat.IndexType vertexformat$indextype;
            if (meshdata.indexBuffer() == null) {
                RenderSystem.AutoStorageIndexBuffer rendersystem$autostorageindexbuffer = RenderSystem.getSequentialBuffer(meshdata.drawState().mode());
                gpubuffer1 = rendersystem$autostorageindexbuffer.getBuffer(meshdata.drawState().indexCount());
                vertexformat$indextype = rendersystem$autostorageindexbuffer.type();
            } else {
                gpubuffer1 = this.renderPipeline.getVertexFormat().uploadImmediateIndexBuffer(meshdata.indexBuffer());
                vertexformat$indextype = meshdata.drawState().indexType();
            }

            RenderTarget rendertarget = this.state.outputState.getRenderTarget();
            GpuTextureView gputextureview = RenderSystem.outputColorTextureOverride != null
                    ? RenderSystem.outputColorTextureOverride
                    : rendertarget.getColorTextureView();
            GpuTextureView gputextureview1 = rendertarget.useDepth
                    ? (RenderSystem.outputDepthTextureOverride != null ? RenderSystem.outputDepthTextureOverride : rendertarget.getDepthTextureView())
                    : null;

            try (RenderPass renderpass = RenderSystem.getDevice()
                    .createCommandEncoder()
                    .createRenderPass(
                            () -> "Immediate draw for " + this.getName(), gputextureview, OptionalInt.empty(), gputextureview1, OptionalDouble.empty()
                    )) {
                renderpass.setPipeline(this.renderPipeline);
                ScissorState scissorstate = RenderSystem.getScissorStateForRenderTypeDraws();
                if (scissorstate.enabled()) {
                    renderpass.enableScissor(scissorstate.x(), scissorstate.y(), scissorstate.width(), scissorstate.height());
                }

                RenderSystem.bindDefaultUniforms(renderpass);
                renderpass.setUniform("DynamicTransforms", gpubufferslice);
                for (var key : uniforms.keySet()) {
                    var value = uniforms.get(key);
                    renderpass.setUniform(key, value);
                }
                renderpass.setVertexBuffer(0, gpubuffer);

                for (int i = 0; i < 12; i++) {
                    GpuTextureView gputextureview2 = RenderSystem.getShaderTexture(i);
                    if (gputextureview2 != null) {
                        renderpass.bindSampler("Sampler" + i, gputextureview2);
                    }
                }

                renderpass.setIndexBuffer(gpubuffer1, vertexformat$indextype);
                renderpass.drawIndexed(0, 0, meshdata.drawState().indexCount(), 1);
            }
        } catch (Throwable throwable2) {
            try {
                meshdata.close();
            } catch (Throwable throwable) {
                throwable2.addSuppressed(throwable);
            }

            throw throwable2;
        }

        meshdata.close();

        this.clearRenderState();
    }

    @Override
    public String toString() {
        return "RenderType[" + this.name + ":" + this.state + "]";
    }
}