#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in float Flow;
in vec2 MaterialUv;

out vec4 vertexColor;
out float flowCoord;
out vec2 materialUv;
out vec4 portalProjection;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color * ColorModulator;
    flowCoord = Flow;
    materialUv = MaterialUv;
    // Match the vanilla End Portal vertex path. The portal layers are projected
    // from clip space instead of stretched along the cut's long local UV axis.
    portalProjection = projection_from_position(gl_Position);
}
