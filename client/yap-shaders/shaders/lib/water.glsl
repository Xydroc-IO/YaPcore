#ifndef YAP_WATER_GLSL
#define YAP_WATER_GLSL

// Multi-direction Gerstner waves — crossed swell + chop (not a single linear set).
// No shore run-up: mesh stays on the water plane; banks stay dry.

vec2 yapGDir(float a, float b) {
    return normalize(vec2(a, b));
}

void yapGerstner(inout float h, inout vec2 xzOff, vec2 xz, float t,
                 vec2 dir, float amp, float wl, float speed, float steep) {
    float k = 6.2831853 / max(wl, 0.5);
    float phase = dot(dir, xz) * k - t * speed;
    float s = sin(phase);
    float c = cos(phase);
    h += amp * s;
    xzOff += dir * (steep * amp * c);
}

float yapWaveHeight(vec2 xz, float t) {
    float s = clamp(WAVE_STRENGTH, 0.25, 2.0);
    float h = 0.0;
    vec2 unused = vec2(0.0);

    // Long swell from several headings
    yapGerstner(h, unused, xz, t, yapGDir(0.92, 0.28), 0.42, 28.0, 0.55, 0.55);
    yapGerstner(h, unused, xz, t, yapGDir(-0.48, 0.88), 0.30, 19.0, 0.42, 0.50);
    yapGerstner(h, unused, xz, t, yapGDir(0.22, -0.97), 0.18, 37.0, 0.32, 0.40);

    // Mid chop
    yapGerstner(h, unused, xz, t, yapGDir(0.78, 0.52), 0.12, 9.5, 0.95, 0.45);
    yapGerstner(h, unused, xz, t, yapGDir(-0.62, 0.78), 0.08, 6.8, 1.15, 0.40);

    // Soft micro-ripple
    yapGerstner(h, unused, xz, t, yapGDir(0.95, -0.30), 0.028, 3.2, 1.55, 0.25);
    yapGerstner(h, unused, xz, t, yapGDir(-0.35, 0.94), 0.018, 2.1, 1.85, 0.20);

    h *= 1.0 + rainStrength * 0.38;
    if (rainStrength > 0.01) {
        h += rainStrength * 0.012 * sin(dot(xz, vec2(2.1, 1.7)) * 4.2 + t * 3.2);
    }

    // ~45–70 cm peak-to-trough at strength 1
    return h * 0.72 * s;
}

vec2 yapWaveOffsetXZ(vec2 xz, float t) {
    float s = clamp(WAVE_STRENGTH, 0.25, 2.0);
    float h = 0.0;
    vec2 off = vec2(0.0);

    yapGerstner(h, off, xz, t, yapGDir(0.92, 0.28), 0.42, 28.0, 0.55, 0.55);
    yapGerstner(h, off, xz, t, yapGDir(-0.48, 0.88), 0.30, 19.0, 0.42, 0.50);
    yapGerstner(h, off, xz, t, yapGDir(0.78, 0.52), 0.12, 9.5, 0.95, 0.45);

    // Small XZ only — no beach flooding
    return off * 0.08 * s;
}

vec3 yapWaterDisplace(vec3 wp, float t) {
    vec2 off = yapWaveOffsetXZ(wp.xz, t);
    float h = yapWaveHeight(wp.xz + off * 0.15, t);

    // Visible heave on open water; keep crests from climbing banks
    float hMesh = h;
    hMesh = min(hMesh, 0.14);
    hMesh = max(hMesh, -0.18);
    off *= 0.35;

    return vec3(wp.x + off.x, wp.y + hMesh, wp.z + off.y);
}

vec3 yapWaterNormal(vec2 xz, float t, vec3 baseN) {
    float e = 0.18;
    vec2 o0 = yapWaveOffsetXZ(xz, t);
    float h0 = yapWaveHeight(xz + o0 * 0.15, t);
    float hx = yapWaveHeight(xz + vec2(e, 0.0) + yapWaveOffsetXZ(xz + vec2(e, 0.0), t) * 0.15, t);
    float hz = yapWaveHeight(xz + vec2(0.0, e) + yapWaveOffsetXZ(xz + vec2(0.0, e), t) * 0.15, t);

    vec3 n = normalize(vec3((h0 - hx) / e, 1.0, (h0 - hz) / e));
    float up = clamp(dot(normalize(baseN), vec3(0.0, 1.0, 0.0)), 0.0, 1.0);
    float mixAmt = mix(0.45, 1.0, up);
    mixAmt *= clamp(0.75 + WAVE_STRENGTH * 0.22, 0.75, 1.0);
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
    float hot = pow(ndoth, 220.0) * ndotl * 0.85;
    float mid = pow(ndoth, 42.0) * ndotl * 0.22;
    float wide = pow(ndoth, 12.0) * ndotl * 0.08;
    return vec3(0.92, 0.96, 1.05) * (hot + mid + wide);
#else
    return vec3(0.0);
#endif
}

vec3 yapWaterAlbedo(float depthMix, float rain) {
    vec3 shallow = vec3(0.06, 0.24, 0.30);
    vec3 deep = vec3(0.008, 0.035, 0.11);
    vec3 storm = vec3(0.025, 0.045, 0.07);
    vec3 body = mix(shallow, deep, pow(clamp(depthMix, 0.0, 1.0), 0.78));
    return mix(body, storm, rain * 0.72);
}

vec3 yapWaterAbsorb(vec3 light, float thickness) {
    vec3 coeff = vec3(0.58, 0.15, 0.08);
    float t = clamp(thickness, 0.0, 4.0);
    return light * exp(-coeff * t);
}

// Vertical / steep water (falls & banks). Normals flow downward on the face —
// do NOT use the horizontal lake Gerstner field (that turns falls into mirrors).
vec3 yapWaterfallNormal(vec3 wp, float t, vec3 baseN) {
    vec3 n0 = normalize(baseN);
    // Tangent pointing roughly "down the face" in world space
    vec3 down = vec3(0.0, -1.0, 0.0);
    vec3 bitan = cross(n0, down);
    if (dot(bitan, bitan) < 1e-4) {
        bitan = cross(n0, vec3(1.0, 0.0, 0.0));
    }
    bitan = normalize(bitan);
    vec3 tanD = normalize(cross(bitan, n0)); // down-ish along face

    float u = dot(wp, bitan);
    float v = dot(wp, tanD) - t * 2.4;
    float ripple =
          0.55 * sin(v * 3.1 + u * 0.7)
        + 0.30 * sin(v * 7.2 - u * 1.4 + 1.7)
        + 0.15 * sin(v * 13.0 + u * 2.2);
    ripple *= 0.22 * clamp(WAVE_STRENGTH, 0.4, 1.6);

    vec3 n = normalize(n0 + bitan * ripple * 0.55 + tanD * ripple);
    return n;
}

vec3 yapWaterfallAlbedo(float rain) {
    vec3 clearCol = vec3(0.10, 0.32, 0.38);
    vec3 stormCol = vec3(0.06, 0.12, 0.16);
    return mix(clearCol, stormCol, rain * 0.65);
}

#endif
