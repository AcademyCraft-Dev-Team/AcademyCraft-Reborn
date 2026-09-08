#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec2 UV0;
in mat4 InstanceMat;
in float InstanceAlpha;

out vec2 texCoord0;
out vec4 vertexColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * InstanceMat * vec4(Position, 1.0);
    texCoord0 = UV0;
    vertexColor = vec4(1.0, 1.0, 1.0, InstanceAlpha);
}
