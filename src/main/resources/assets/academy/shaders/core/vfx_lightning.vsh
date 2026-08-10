#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

layout(std140) uniform LightningUniforms {
    vec4 LightningBaseColor;
    vec4 LightningEmissionColor;
    vec4 LightningParams;
    vec4 LightningCameraOffset;
};

in vec3 Position;
in vec2 UV0;

out vec2 texCoord0;

void main() {
    vec3 camRel = Position - LightningCameraOffset.xyz;
    gl_Position = ProjMat * ModelViewMat * vec4(camRel, 1.0);
    texCoord0 = UV0;
}
