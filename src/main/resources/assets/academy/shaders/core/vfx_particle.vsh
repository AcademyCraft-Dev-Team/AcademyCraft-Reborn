#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec3 InstancePos;
in float InstanceSize;
in vec4 InstanceColor;

out vec4 vertexColor;

void main() {
    vec4 viewPos = ModelViewMat * vec4(InstancePos, 1.0);
    vec2 offset = (Position.xy * 2.0 - 1.0) * InstanceSize;
    viewPos.xy += offset;
    gl_Position = ProjMat * viewPos;
    vertexColor = InstanceColor;
}
