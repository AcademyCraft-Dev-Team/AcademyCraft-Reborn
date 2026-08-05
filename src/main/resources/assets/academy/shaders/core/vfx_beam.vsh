#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in mat4 InstanceMat;

out vec4 vertexColor;

void main() {
    vec4 local = InstanceMat * vec4(Position, 1.0);
    gl_Position = ProjMat * ModelViewMat * local;
    vertexColor = Color;
}
