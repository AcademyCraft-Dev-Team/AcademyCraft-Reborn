#version 330
layout(std140) uniform GraphCamera { mat4 View; mat4 Projection; };
in vec3 Position;
in vec3 InstancePos;
in vec3 InstanceVel;
in float InstanceSize;
in vec4 InstanceColor;
in float InstanceRot;
in float InstanceSeed;
in float InstanceAge;
in vec3 InstanceNormal;
in vec4 InstanceClipPlane;
in float InstancePenetration;
out vec2 texCoord;
out vec4 vertexColor;
out vec3 surfacePosition;
out float materialProgress;
out float materialPhase;
out float materialMode;

void main() {
    float laminaLength = max(length(InstanceVel), 0.00001);
    vec3 axis = InstanceVel / laminaLength;
    vec3 side = normalize(cross(axis, InstanceNormal));
    vec3 normal = normalize(cross(side, axis));
    float p = clamp(InstanceAge, 0.0, 1.0);
    float v = Position.y;
    bool fragment = InstanceRot > 1.0;
    bool repair = InstanceRot < 0.0;
    float lift = fragment ? InstanceRot - 2.0 : abs(InstanceRot);
    float phase = InstanceSeed * 0.6180339;
    float growth = fragment ? 1.0 : smoothstep(0.0, repair ? 0.62 : 0.22, p);
    float retreat = repair ? smoothstep(0.0, 0.28, 1.0 - p) : 1.0;
    float tip = 1.0 - smoothstep(0.55, 1.0, v);
    if (!repair && !fragment) tip *= mix(0.10, 1.0, smoothstep(0.0, 0.15, v));
    if (repair) {
        float stem = mix(0.06, 0.92, smoothstep(0.0, 0.7, v));
        tip = stem * mix(1.0 - smoothstep(0.7, 1.0, v), 0.97, smoothstep(0.25, 0.65, p));
    }
    float taper = (0.94 + 0.06 * sin(v * 9.0 + phase)) * tip;
    float angle = Position.x * 6.2831853;
    float halfWidth = InstanceSize * laminaLength * 0.5 * taper * retreat;
    float rise = fragment ? lift * sin(v * 3.14159265)
            : repair ? lift * 0.12 * (1.0 - smoothstep(0.32, 0.70, p)) * sin(v * 3.14159265)
            : lift * smoothstep(0.18, 0.62, p) * v * v;
    // A closed oval cross-section gives the layer a back and thin sides at every viewing angle.
    float thickness = min(0.008, laminaLength * 0.025) * taper * retreat;
    float peel = !repair && !fragment ? smoothstep(0.18, 0.62, p) : 0.0;
    float along = v * laminaLength * growth * (1.0 - peel * v * v * 0.28);
    float drift = sin(v * 3.14159265) * sin(phase) * laminaLength * InstanceSize * 0.16;
    vec3 world = InstancePos + axis * along
            + side * (cos(angle) * halfWidth + drift) + normal * (rise - sin(angle) * thickness);
    gl_Position = Projection * View * vec4(world, 1.0);
    texCoord = vec2(cos(angle) * 0.5 + 0.5, v);
    surfacePosition = world;
    vertexColor = InstanceColor;
    materialProgress = p;
    materialPhase = phase;
    materialMode = fragment ? 2.0 : repair ? 1.0 : 0.0;
}
