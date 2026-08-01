#version 330

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

layout(std140) uniform SkillProgress {
    float Progress;
};

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    float threshold = texture(Sampler1, texCoord0).r;
    vec4 circleColor = texture(Sampler0, texCoord0);
    float visible = Progress > threshold ? 1.0 : 0.0;
    fragColor = circleColor * vertexColor * visible;
}
