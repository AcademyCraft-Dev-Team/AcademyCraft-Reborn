#version 330

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

layout (std140) uniform SpatialCutFlow {
    // x: game time in ticks; z: pulse half-width.
    vec4 Params;
};

in vec4 vertexColor;
in float flowCoord;
in vec2 materialUv;
in vec4 portalProjection;

out vec4 fragColor;

// Vanilla End Portal palette, kept in the same order as rendertype_end_portal.fsh.
const vec3 PORTAL_COLORS[16] = vec3[](
    vec3(0.022087, 0.098399, 0.110818),
    vec3(0.011892, 0.095924, 0.089485),
    vec3(0.027636, 0.101689, 0.100326),
    vec3(0.046564, 0.109883, 0.114838),
    vec3(0.064901, 0.117696, 0.097189),
    vec3(0.063761, 0.086895, 0.123646),
    vec3(0.084817, 0.111994, 0.166380),
    vec3(0.097489, 0.154120, 0.091064),
    vec3(0.106152, 0.131144, 0.195191),
    vec3(0.097721, 0.110188, 0.187229),
    vec3(0.133516, 0.138278, 0.148582),
    vec3(0.070006, 0.243332, 0.235792),
    vec3(0.196766, 0.142899, 0.214696),
    vec3(0.047281, 0.315338, 0.321970),
    vec3(0.204675, 0.390010, 0.302066),
    vec3(0.080955, 0.314821, 0.661491)
);

const mat4 SCALE_TRANSLATE = mat4(
    0.5, 0.0, 0.0, 0.25,
    0.0, 0.5, 0.0, 0.25,
    0.0, 0.0, 1.0, 0.0,
    0.0, 0.0, 0.0, 1.0
);

mat4 endPortalLayer(float layer, float gameTime) {
    mat4 translate = mat4(
        1.0, 0.0, 0.0, 17.0 / layer,
        0.0, 1.0, 0.0, (2.0 + layer / 1.5) * (gameTime * 1.5),
        0.0, 0.0, 1.0, 0.0,
        0.0, 0.0, 0.0, 1.0
    );
    float angle = radians((layer * layer * 4321.0 + layer * 9.0) * 2.0);
    float sine = sin(angle);
    float cosine = cos(angle);
    mat2 rotation = mat2(cosine, -sine, sine, cosine);
    mat2 scale = mat2((4.5 - layer / 4.0) * 2.0);
    return mat4(scale * rotation) * translate * SCALE_TRANSLATE;
}

vec3 samplePortal(vec4 portalProjection, float gameTime) {
    vec3 portal = textureProj(Sampler0, portalProjection).rgb * PORTAL_COLORS[0];
    for (int i = 0; i < 15; i++) {
        float layer = float(i + 1);
        portal += textureProj(Sampler1,
                portalProjection * endPortalLayer(layer, gameTime)).rgb
                * PORTAL_COLORS[i];
    }
    return clamp(portal, 0.0, 1.0);
}

float hash11(float value) {
    return fract(sin(value * 127.1 + 311.7) * 43758.5453123);
}

float pulseBand(float coordinate, float speed, float width) {
    float wrapped = fract(coordinate * 2.0 - Params.x * speed);
    float distanceToPeak = min(wrapped, 1.0 - wrapped);
    return 1.0 - smoothstep(0.0, width, distanceToPeak);
}

void main() {
    if (vertexColor.a < 0.001) discard;

    float speed = materialUv.x;
    if (!(abs(speed) >= 0.035 && abs(speed) <= 0.085)) speed = 0.05;
    float width = Params.z;
    if (!(width >= 0.02 && width <= 0.5)) width = 0.16;

    // Keep one seeded stream from the CPU, then add two independent streams
    // derived from that stream. Their signed speeds and phase offsets are
    // stable for a crack but vary between cracks, avoiding a synchronized,
    // conveyor-belt-like highlight.
    float streamSeed = abs(speed) * 997.0 + 17.0;
    float secondarySpeed = mix(0.035, 0.085, hash11(streamSeed + 13.0));
    float secondaryDirection = hash11(streamSeed + 29.0) < 0.5 ? -1.0 : 1.0;
    float secondaryPhase = hash11(streamSeed + 47.0);
    float tertiarySpeed = mix(0.035, 0.085, hash11(streamSeed + 61.0));
    float tertiaryDirection = hash11(streamSeed + 73.0) < 0.5 ? -1.0 : 1.0;
    float tertiaryPhase = hash11(streamSeed + 89.0);
    float primaryPulse = pulseBand(flowCoord, speed, width);
    float secondaryPulse = pulseBand(
            flowCoord * (1.35 + hash11(streamSeed + 101.0) * 0.85)
                    + secondaryPhase,
            secondarySpeed * secondaryDirection,
            width * 0.75);
    float tertiaryPulse = pulseBand(
            flowCoord * (2.05 + hash11(streamSeed + 113.0) * 1.15)
                    + tertiaryPhase,
            tertiarySpeed * tertiaryDirection,
            width * 0.55);
    float localNoise = 0.82 + 0.18 * sin(
            flowCoord * (18.0 + hash11(streamSeed + 127.0) * 11.0)
                    - Params.x * (0.02 + hash11(streamSeed + 139.0) * 0.04));
    float pulse = clamp(max(primaryPulse,
            max(secondaryPulse * 0.72, tertiaryPulse * 0.48)) * localNoise,
            0.0, 1.0);

    float edgeDistance = abs(materialUv.y - 0.5) * 2.0;
    float aa = 0.22;
    float band = 1.0 - smoothstep(1.0 - aa, 1.0, edgeDistance);
    if (band <= 0.001) discard;

    float gameTime = mod(Params.x, 24000.0) / 24000.0;
    vec3 portal = samplePortal(portalProjection, gameTime);

    // The center ridge is still End Portal texture; there is no flat-color pass
    // drawn over it. It only raises the sampled material's contrast slightly.
    float centerRidge = 1.0 - smoothstep(0.0, 0.22,
            abs(materialUv.y - 0.5));
    float intensity = clamp(0.82 + pulse * 1.25 + centerRidge * 0.15, 0.0, 2.2);
    float alpha = vertexColor.a * band * clamp(0.88 + pulse * 0.20, 0.0, 1.0);
    fragColor = vec4(portal * vertexColor.rgb * intensity, alpha);
}
