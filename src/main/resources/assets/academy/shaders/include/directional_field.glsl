layout(std140) uniform DirectionalField {
    mat4 InverseViewProjection;
    vec4 SunLight;
    vec4 FieldParams;
    vec4 NoiseAnchor;
    vec4 FieldOrigins[16];
    vec4 FieldDirections[16];
    vec4 FieldCones[16];
    vec4 FieldStyles[16];
};

vec3 reconstruct(vec2 uv, float depth) {
    float z = FieldParams.z > 0.5 ? depth : depth * 2.0 - 1.0;
    vec4 p = InverseViewProjection * vec4(uv * 2.0 - 1.0, z, 1.0);
    return p.xyz / max(abs(p.w), 1e-8) * sign(p.w);
}

float sector(vec3 p, vec3 axis, float radius, float minimumDot, float edgeWidth) {
    float distance = length(p);
    if (radius <= 0.0 || distance > radius) return 0.0;
    float side = dot(p, axis) - distance * minimumDot;
    return smoothstep(0.0, edgeWidth, min(radius - distance, side));
}

float fieldAt(vec3 p, int i) {
    vec3 d = p - FieldOrigins[i].xyz;
    vec4 cones = FieldCones[i];
    // A monotonic edge curve lets the two sectors share one distance, projection and smoothstep.
    float distance = length(d);
    float along = dot(d, FieldDirections[i].xyz);
    vec2 edges = min(cones.xz - vec2(distance), vec2(along) - distance * cones.yw);
    return smoothstep(0.0, FieldStyles[i].z, max(edges.x, edges.y));
}

float unionAt(vec3 p) {
    float result = 0.0;
    for (int i = 0; i < 16; i++) {
        if (i >= int(FieldParams.y)) break;
        result = max(result, fieldAt(p, i) * FieldStyles[i].x);
    }
    return result;
}

float hashField(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float fieldNoise(vec2 p) {
    vec2 i = floor(p), f = fract(p); f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hashField(i), hashField(i + vec2(1, 0)), f.x),
               mix(hashField(i + vec2(0, 1)), hashField(i + vec2(1, 1)), f.x), f.y);
}
