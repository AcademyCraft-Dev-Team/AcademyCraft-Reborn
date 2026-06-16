#version 330

in vec2 texCoord0;

layout (std140) uniform SpatialUniforms {
    float Time;
    float Intensity;
    float Scale;
    vec4 CoreColor;
    vec4 EdgeColor;
    float VoronoiDensity;
    float TearProgress;
};

out vec4 fragColor;

vec2 hash2(vec2 p) {
    p = vec2(dot(p, vec2(127.1, 311.7)), dot(p, vec2(269.5, 183.3)));
    return -1.0 + 2.0 * fract(sin(p) * 43758.5453123);
}

float voronoi(vec2 uv, float density) {
    vec2 i = floor(uv * density);
    vec2 f = fract(uv * density);
    float minDist = 1.0;
    float secondMinDist = 1.0;

    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 neighbor = vec2(float(x), float(y));
            vec2 point = hash2(i + neighbor);
            vec2 diff = neighbor + point - f;
            float d = dot(diff, diff);
            if (d < minDist) {
                secondMinDist = minDist;
                minDist = d;
            } else if (d < secondMinDist) {
                secondMinDist = d;
            }
        }
    }

    return secondMinDist - minDist;
}

void main() {
    vec2 centeredUV = texCoord0 * 2.0 - 1.0;
    float dist = length(centeredUV);

    float tearRadius = TearProgress * 1.5;
    float ringOuter = tearRadius * (1.0 + Scale * 0.3);
    float ringInner = tearRadius * (1.0 - Scale * 0.3);

    if (dist > ringOuter + 0.3 || dist < ringInner - 0.3) {
        discard;
    }

    float ringFade = 1.0;
    if (dist < ringInner) {
        ringFade = smoothstep(ringInner - 0.3, ringInner, dist);
    } else if (dist > ringOuter) {
        ringFade = 1.0 - smoothstep(ringOuter, ringOuter + 0.3, dist);
    }

    float v = voronoi(centeredUV + Time * 0.1, VoronoiDensity);
    v = v * 0.5 + 0.5;

    float alpha = Intensity * ringFade * (0.4 + 0.6 * v);
    vec3 color = mix(EdgeColor.rgb, CoreColor.rgb, 1.0 - dist / ringOuter);

    fragColor = vec4(color, alpha * CoreColor.a);
}
