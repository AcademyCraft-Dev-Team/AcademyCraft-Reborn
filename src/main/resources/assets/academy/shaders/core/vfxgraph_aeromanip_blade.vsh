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
out float bladeSeed;
out float bladeAge;

float hash1(float value) {
    return fract(sin(value * 127.1) * 43758.5453);
}

void main() {
    vec4 viewPos = View * vec4(InstancePos, 1.0);
    vec2 offset = Position.xy * 2.0 - 1.0;
    vec3 velocityView = (View * vec4(InstanceVel, 0.0)).xyz;
    float projectedSpeed = length(velocityView.xy);
    float fallbackAngle = InstanceRot + hash1(InstanceSeed + 7.9) * 6.2831853;
    vec2 longAxis = projectedSpeed > 1e-4
            ? velocityView.xy / projectedSpeed
            : vec2(cos(fallbackAngle), sin(fallbackAngle));
    vec2 shortAxis = vec2(-longAxis.y, longAxis.x);

    bladeSeed = hash1(InstanceSeed * 0.47 + 3.1);
    bladeAge = InstanceAge;
    float lengthVariation = mix(0.72, 1.26, hash1(InstanceSeed * 0.31 + 9.6));
    float widthVariation = mix(0.66, 1.08, hash1(InstanceSeed * 0.73 + 4.4));
    float worldSpeed = length(InstanceVel);
    float stretch = (4.1 + clamp(worldSpeed * 0.09, 0.0, 1.7)) * lengthVariation;
    viewPos.xy += longAxis * offset.y * InstanceSize * stretch;
    viewPos.xy += shortAxis * offset.x * InstanceSize * 0.68 * widthVariation;

    gl_Position = Projection * viewPos;
    texCoord = Position.xy;
    vertexColor = InstanceColor;
}
