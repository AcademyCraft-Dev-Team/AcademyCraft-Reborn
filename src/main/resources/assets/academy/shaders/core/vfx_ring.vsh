#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec2 UV0;
in mat4 InstanceMat;

out vec2 texCoord0;
out vec4 vertexColor;

void main() {
    vec4 local = InstanceMat * vec4(Position, 1.0);
    gl_Position = ProjMat * ModelViewMat * local;
    texCoord0 = UV0;
    vertexColor = vec4(1.0);
}
