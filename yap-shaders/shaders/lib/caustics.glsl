#ifndef YAP_CAUSTICS_GLSL
#define YAP_CAUSTICS_GLSL

// Animated caustic pattern (YaP original — not sampled from third-party packs)
float yapCausticPattern(vec2 uv, float t) {
    vec2 p = uv * 3.5;
    float c = 0.0;
    c += abs(sin(p.x * 2.1 + t * 1.3) * cos(p.y * 1.7 - t * 1.1));
    c += abs(sin((p.x + p.y) * 3.3 - t * 1.7)) * 0.65;
    c += abs(sin(p.x * 5.2 - p.y * 4.1 + t * 2.2)) * 0.35;
    c = pow(clamp(c * 0.55, 0.0, 1.0), 2.2);
    return c;
}

vec3 yapApplyCaustics(vec3 color, vec3 worldPos, vec3 N, vec3 L, float amount) {
#ifdef CAUSTICS
    float ndotl = max(dot(normalize(N), normalize(L)), 0.0);
    float pat = yapCausticPattern(worldPos.xz * 0.35, frameTimeCounter);
    float boost = pat * ndotl * amount;
    return color + vec3(0.45, 0.75, 0.85) * boost * 0.32;
#else
    return color;
#endif
}

vec3 yapUnderwaterCaustics(vec3 color, vec2 uv, float t) {
#ifdef CAUSTICS
    float pat = yapCausticPattern(uv * vec2(aspectRatio, 1.0) * 2.2 + t * 0.05, t);
    return color + vec3(0.12, 0.35, 0.42) * pat * 0.14;
#else
    return color;
#endif
}

#endif
