#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec2 UV0;
in mat4 InstanceMat;

out vec2 texCoord0;
out vec3 viewPosition;

void main() {
    vec4 view = ModelViewMat * InstanceMat * vec4(Position, 1.0);
    gl_Position = ProjMat * view;
    texCoord0 = UV0;
    viewPosition = view.xyz;
}
