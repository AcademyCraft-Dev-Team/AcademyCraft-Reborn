package org.academy.api.client.gui.command;

import com.mojang.blaze3d.vertex.PoseStack;
import org.academy.api.client.gui.framework.ScissorRect;

import javax.annotation.Nullable;
import java.util.Objects;

public final class SubmittedCommand {
    private final DrawCommand command;
    private final PoseStack.Pose pose;
    @Nullable
    private final ScissorRect scissorRect;
    private final long resourceKey;

    public SubmittedCommand(DrawCommand command, PoseStack.Pose pose, @Nullable ScissorRect scissorRect) {
        this.command = command;
        this.pose = pose;
        this.scissorRect = scissorRect;
        resourceKey = calculateResourceKey(command);
    }

    private static long calculateResourceKey(DrawCommand command) {
        return Objects.hash(command.getSamplers(), command.getUniforms());
    }

    public DrawCommand getCommand() {
        return command;
    }

    public PoseStack.Pose getPose() {
        return pose;
    }

    @Nullable
    public ScissorRect getScissorRect() {
        return scissorRect;
    }

    public long getResourceKey() {
        return resourceKey;
    }
}