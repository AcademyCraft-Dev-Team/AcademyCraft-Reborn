#version 330

in vec3 worldPos;
in vec3 worldNormal;

layout (std140) uniform AuraUniforms {
    float Time;
    float Intensity;
    float NoiseScale;
    float NoiseSpeed;
    vec4 InnerColor;
    vec4 OuterColor;
    float FresnelPower;
    float PulseFrequency;
    float PulseAmplitude;
};

out vec4 fragColor;

vec3 hash3(vec3 p) {
    p = vec3(dot(p, vec3(127.1, 311.7, 74.7)),
             dot(p, vec3(269.5, 183.3, 246.1)),
             dot(p, vec3(113.5, 271.9, 348.3)));
    return -1.0 + 2.0 * fract(sin(p) * 43758.5453123);
}

float simplexNoise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);

    return mix(
        mix(mix(dot(hash3(i + vec3(0,0,0)), f - vec3(0,0,0)),
                dot(hash3(i + vec3(1,0,0)), f - vec3(1,0,0)), f.x),
            mix(dot(hash3(i + vec3(0,1,0)), f - vec3(0,1,0)),
                dot(hash3(i + vec3(1,1,0)), f - vec3(1,1,0)), f.x), f.y),
        mix(mix(dot(hash3(i + vec3(0,0,1)), f - vec3(0,0,1)),
                dot(hash3(i + vec3(1,0,1)), f - vec3(1,0,1)), f.x),
            mix(dot(hash3(i + vec3(0,1,1)), f - vec3(0,1,1)),
                dot(hash3(i + vec3(1,1,1)), f - vec3(1,1,1)), f.x), f.y), f.z
    );
}

void main() {
    vec3 viewDir = vec3(0.0, 0.0, -1.0);
    float fresnel = 1.0 - abs(dot(normalize(worldNormal), normalize(viewDir)));
    fresnel = pow(fresnel, FresnelPower);

    float noise = simplexNoise(worldPos * NoiseScale + Time * NoiseSpeed);
    float noise2 = simplexNoise(worldPos * NoiseScale * 2.0 - Time * NoiseSpeed * 0.7);
    float combinedNoise = (noise + noise2) * 0.5;

    float pulse = 1.0 + sin(Time * PulseFrequency) * PulseAmplitude;
    float alpha = fresnel * Intensity * pulse;
    alpha *= 0.5 + 0.5 * combinedNoise;

    vec3 color = mix(OuterColor.rgb, InnerColor.rgb, fresnel);
    fragColor = vec4(color, alpha * InnerColor.a);
}
