#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

layout(std140) uniform SkillProgress {
    float Progress;
};

layout(location = 0) in vec2 texCoord0;
layout(location = 1) in vec4 vertexColor;

layout(location = 0) out vec4 fragColor;

void main() {
    float threshold = texture(Sampler1, texCoord0).r;
    vec4 circleColor = texture(Sampler0, texCoord0);
    float visible = Progress > threshold ? 1.0 : 0.0;
    fragColor = circleColor * vertexColor * visible;
}
