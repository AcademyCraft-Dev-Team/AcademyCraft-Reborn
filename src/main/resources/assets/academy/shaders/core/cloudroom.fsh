#version 330

#moj_import <minecraft:globals.glsl>

uniform sampler2D Sampler0;
in vec2 texCoord0;
in vec3 viewPosition;
out vec4 fragColor;

float hash(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}

float noise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash(i), hash(i + vec3(1, 0, 0)), f.x),
                   mix(hash(i + vec3(0, 1, 0)), hash(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(hash(i + vec3(0, 0, 1)), hash(i + vec3(1, 0, 1)), f.x),
                   mix(hash(i + vec3(0, 1, 1)), hash(i + vec3(1, 1, 1)), f.x), f.y), f.z);
}

void main() {
    // Texture cutouts retain the model silhouette (including ears, wings and plant-like mobs).
    if (texture(Sampler0, texCoord0).a < 0.1) discard;
    float time = GameTime * 1200.0;
    vec3 p = vec3(texCoord0 * 12.0, time * 0.14);
    p.y -= time * 0.32;
    float smoke = noise(p + noise(p * 0.7) * 1.7) * 0.65 + noise(p * 2.1) * 0.35;
    vec3 normal = normalize(cross(dFdx(viewPosition), dFdy(viewPosition)));
    float rim = pow(1.0 - abs(dot(normal, normalize(-viewPosition))), 2.0);
    float light = smoothstep(0.25, 0.8, smoke);
    vec3 color = mix(vec3(0.06, 0.72, 0.48), vec3(0.66, 1.0, 0.82), light);
    fragColor = vec4(color, 0.18 + light * 0.44 + rim * 0.22);
}
