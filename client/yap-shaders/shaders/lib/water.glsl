#ifndef YAP_WATER_GLSL
#define YAP_WATER_GLSL

#ifndef WAVE_STRENGTH
#define WAVE_STRENGTH 1.0
#endif

// Quiet multi-scale swell + chop (reads as real water, not arcade sine soup)
float yapWaveHeight(vec2 xz, float t) {
    float s = clamp(WAVE_STRENGTH, 0.2, 1.8);
    float h = 0.0;

    // Long ocean swell
    h += 0.55 * sin(dot(xz, vec2(0.97, 0.18)) * 0.28 + t * 0.48);
    h += 0.35 * sin(dot(xz, vec2(-0.62, 0.92)) * 0.42 + t * 0.37);
    // Mid chop
    h += 0.22 * sin(dot(xz, vec2(0.78, 0.55)) * 0.95 - t * 0.82);
    h += 0.14 * sin(dot(xz, vec2(-0.48, 0.84)) * 1.55 + t * 1.05);
    // Fine ripple
    h += 0.07 * sin(dot(xz, vec2(1.15, -0.38)) * 3.2 + t * 1.65);
    h += 0.035 * sin(dot(xz, vec2(-0.88, 1.05)) * 5.8 - t * 2.15);

    // Soft crest pinch (subtle — not cartoon peaks)
    h += 0.06 * sin(h * 1.8 + t * 0.4);

    // ~8–12 cm at strength 1.0
    return h * 0.072 * s;
}

vec3 yapWaterNormal(vec2 xz, float t, vec3 baseN) {
    // Smaller epsilon → smoother normals (less sparkly plastic)
    float e = 0.35;
    float h0 = yapWaveHeight(xz, t);
    float hx = yapWaveHeight(xz + vec2(e, 0.0), t);
    float hz = yapWaveHeight(xz + vec2(0.0, e), t);
    vec3 n = normalize(vec3((h0 - hx) / e, 1.0, (h0 - hz) / e));
    float mixAmt = clamp(0.35 + WAVE_STRENGTH * 0.35, 0.35, 0.85);
    return normalize(mix(normalize(baseN), n, mixAmt));
}

float yapFresnelSchlick(float cosTheta, float f0) {
    float f = 1.0 - clamp(cosTheta, 0.0, 1.0);
    return f0 + (1.0 - f0) * pow(f, 5.0);
}

vec3 yapWaterSpecular(vec3 N, vec3 V, vec3 L) {
#ifdef SPECULAR
    vec3 H = normalize(V + L);
    float ndoth = max(dot(N, H), 0.0);
    float ndotl = max(dot(N, L), 0.0);
    // Tiny hot sun glitter + soft mid lobe (Complementary-ish)
    float hot = pow(ndoth, 480.0) * ndotl;
    float mid = pow(ndoth, 64.0) * ndotl * 0.12;
    return vec3(1.0, 1.02, 1.05) * (hot * 1.15 + mid);
#else
    return vec3(0.0);
#endif
}

// Natural coastal / lake water — muted, not neon teal
vec3 yapWaterAlbedo(float depthMix, float rain) {
    vec3 shallow = vec3(0.12, 0.32, 0.34);
    vec3 deep = vec3(0.015, 0.055, 0.14);
    vec3 storm = vec3(0.04, 0.07, 0.10);
    vec3 body = mix(shallow, deep, pow(clamp(depthMix, 0.0, 1.0), 0.85));
    return mix(body, storm, rain * 0.65);
}

// Beer–Lambert style absorption through a water column
vec3 yapWaterAbsorb(vec3 light, float thickness) {
    vec3 coeff = vec3(0.55, 0.18, 0.10); // red dies first
    float t = clamp(thickness, 0.0, 4.0);
    return light * exp(-coeff * t);
}

#endif
