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
out float dustTime;
out float dustSeed;

void main() {
    vec4 viewPos = View * vec4(InstancePos, 1.0);
    vec2 off = Position.xy * 2.0 - 1.0;
    float angle = InstanceRot + InstanceSeed * 0.41;
    float c = cos(angle);
    float s = sin(angle);
    vec2 rotated = vec2(off.x * c - off.y * s, off.x * s + off.y * c);

    vec2 velocity = (View * vec4(InstanceVel, 0.0)).xy;
    float speed = length(velocity);
    if (speed > 1e-4) {
        vec2 direction = velocity / speed;
        rotated += direction * off.y * clamp(speed * 0.0025, 0.0, 0.58);
    }
    viewPos.xy += rotated * InstanceSize;
    gl_Position = Projection * viewPos;
    texCoord = Position.xy;
    vertexColor = InstanceColor;
    dustTime = InstanceAge;
    dustSeed = fract(sin(InstanceSeed * 73.17) * 43758.5453);
}
