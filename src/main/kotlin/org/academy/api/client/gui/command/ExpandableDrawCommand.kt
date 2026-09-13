package org.academy.api.client.gui.command

import com.mojang.blaze3d.vertex.PoseStack

interface ExpandableDrawCommand {
    fun expand(pose: PoseStack.Pose, alphaMul: Float): List<DrawCommand>
}
