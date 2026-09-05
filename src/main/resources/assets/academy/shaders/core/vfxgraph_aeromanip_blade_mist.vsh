#version 330

layout(std140) uniform GraphCamera {
    mat4 View;
    mat4 Projection;
};

in vec3 Position;
in vec3 InstancePos;
in vec3 InstanceVel;
in float InstanceSize;
in vec4 InstanceColor;
in float InstanceRot;
in float InstanceSeed;
in float InstanceAge;

out vec2 texCoord;
out vec4 vertexColor;
out float mistSeed;
out float mistAge;

const float CARD_LENGTH_SCALE = 1.5;
const float CARD_WIDTH_SCALE = 1.3;

float hash1(float value) {
    return fract(sin(value * 127.1) * 43758.5453);
}

void main() {
    vec4 viewPos = View * vec4(InstancePos, 1.0);
    vec2 offset = Position.xy * 2.0 - 1.0;
    vec3 velocityView = (View * vec4(InstanceVel, 0.0)).xyz;
    float projectedSpeed = length(velocityView.xy);
    float fallbackAngle = InstanceRot + hash1(InstanceSeed + 2.8) * 6.2831853;
    vec2 longAxis = projectedSpeed > 1e-4
            ? velocityView.xy / projectedSpeed
            : vec2(cos(fallbackAngle), sin(fallbackAngle));
    vec2 shortAxis = vec2(-longAxis.y, longAxis.x);

    mistSeed = hash1(InstanceSeed * 0.39 + 6.2);
    mistAge = InstanceAge;
    float lengthVariation = mix(0.72, 1.34, hash1(InstanceSeed * 0.63 + 1.4));
    float widthVariation = mix(0.56, 1.12, hash1(InstanceSeed * 0.27 + 8.7));
    float stretch = (1.65 + clamp(length(InstanceVel) * 0.075, 0.0, 1.25))
            * lengthVariation;
    viewPos.xy += longAxis * offset.y * InstanceSize * stretch * CARD_LENGTH_SCALE;
    viewPos.xy += shortAxis * offset.x * InstanceSize * 0.72 * widthVariation * CARD_WIDTH_SCALE;

    gl_Position = Projection * viewPos;
    texCoord = Position.xy;
    vertexColor = InstanceColor;
}
