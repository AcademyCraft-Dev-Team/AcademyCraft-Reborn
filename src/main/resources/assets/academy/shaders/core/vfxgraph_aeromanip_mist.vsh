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
out float flowStretch;

float hash1(float n) {
    return fract(sin(n * 127.1) * 43758.5453);
}

void main() {
    vec4 viewPos = View * vec4(InstancePos, 1.0);
    vec2 offset = Position.xy * 2.0 - 1.0;
    vec3 velocityView = (View * vec4(InstanceVel, 0.0)).xyz;
    float projectedSpeed = length(velocityView.xy);
    float worldSpeed = length(InstanceVel);
    float fallbackAngle = InstanceRot + hash1(InstanceSeed + 9.7) * 6.2831853;
    vec2 longAxis = projectedSpeed > 1e-4
            ? velocityView.xy / projectedSpeed
            : vec2(cos(fallbackAngle), sin(fallbackAngle));
    vec2 shortAxis = vec2(-longAxis.y, longAxis.x);
    flowStretch = 1.0 + clamp(worldSpeed * 0.28, 0.0, 2.8);
    viewPos.xy += shortAxis * offset.x * InstanceSize;
    viewPos.xy += longAxis * offset.y * InstanceSize * flowStretch;
    gl_Position = Projection * viewPos;
    texCoord = Position.xy;
    vertexColor = InstanceColor;
    mistSeed = hash1(InstanceSeed * 0.41 + 2.3);
    mistAge = InstanceAge;
}
