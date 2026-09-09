#ifndef YAP_WATER_GLSL
#define YAP_WATER_GLSL

// Open-water look: flat mesh + multi-scale normals (no block-size Gerstner heave —
// vertex displacement on 1m quads reads as stair-step zigzags across the ocean).

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

// Analytic height used for normals only (not for mesh).
float yapWaveHeight(vec2 xz, float t) {
    float s = clamp(WAVE_STRENGTH, 0.25, 2.0);
    float h = 0.0;
    vec2 unused = vec2(0.0);

    // Broad swell — long wavelengths only (readable at distance)
    yapGerstner(h, unused, xz, t, yapGDir(0.92, 0.28), 0.55, 42.0, 0.38, 0.35);
    yapGerstner(h, unused, xz, t, yapGDir(-0.48, 0.88), 0.38, 31.0, 0.30, 0.32);
    yapGerstner(h, unused, xz, t, yapGDir(0.22, -0.97), 0.22, 55.0, 0.22, 0.28);

    // Mid chop (kept soft)
    yapGerstner(h, unused, xz, t, yapGDir(0.78, 0.52), 0.10, 14.0, 0.72, 0.28);
    yapGerstner(h, unused, xz, t, yapGDir(-0.62, 0.78), 0.07, 9.5, 0.88, 0.24);

    // Micro ripples — rain boosts these; calm weather keeps them tiny
    float micro = mix(0.55, 1.35, rainStrength);
    yapGerstner(h, unused, xz, t, yapGDir(0.95, -0.30), 0.018 * micro, 4.5, 1.25, 0.18);
    yapGerstner(h, unused, xz, t, yapGDir(-0.35, 0.94), 0.012 * micro, 2.8, 1.55, 0.15);

    if (rainStrength > 0.01) {
        // Soft rain pitters — not sharp enough to moiré
        h += rainStrength * 0.010 * sin(dot(xz, vec2(1.7, 2.3)) * 2.8 + t * 2.6);
        h += rainStrength * 0.006 * sin(dot(xz, vec2(-2.4, 1.1)) * 4.1 + t * 3.4);
    }

    return h * 0.55 * s;
}

vec2 yapWaveOffsetXZ(vec2 xz, float t) {
    float s = clamp(WAVE_STRENGTH, 0.25, 2.0);
    float h = 0.0;
    vec2 off = vec2(0.0);
    yapGerstner(h, off, xz, t, yapGDir(0.92, 0.28), 0.55, 42.0, 0.38, 0.35);
    yapGerstner(h, off, xz, t, yapGDir(-0.48, 0.88), 0.38, 31.0, 0.30, 0.32);
    yapGerstner(h, off, xz, t, yapGDir(0.78, 0.52), 0.10, 14.0, 0.72, 0.28);
    // Tiny lateral drift for normal coupling only
    return off * 0.04 * s;
}

// Mesh stays nearly flat — any real heave facets into zigzags on block water.
vec3 yapWaterDisplace(vec3 wp, float t) {
    float h = yapWaveHeight(wp.xz, t);
    // Centimeters at most — motion hint without geometry banding
    float hMesh = clamp(h * 0.06, -0.035, 0.028);
    return vec3(wp.x, wp.y + hMesh, wp.z);
}

vec3 yapWaterNormal(vec2 xz, float t, vec3 baseN) {
    // Distance LOD: far ocean drops micro detail (kills moiré / stair banding)
    float dist = length(xz - cameraPosition.xz);
    float nearW = 1.0 - smoothstep(8.0, 48.0, dist);
    float midW = 1.0 - smoothstep(24.0, 96.0, dist);
    float farW = 1.0 - smoothstep(60.0, 180.0, dist);

    float e = mix(0.55, 0.22, nearW); // coarser finite-diff farther out
    vec2 o0 = yapWaveOffsetXZ(xz, t) * midW;
    float h0 = yapWaveHeight(xz + o0 * 0.12, t);
    float hx = yapWaveHeight(xz + vec2(e, 0.0) + yapWaveOffsetXZ(xz + vec2(e, 0.0), t) * 0.12 * midW, t);
    float hz = yapWaveHeight(xz + vec2(0.0, e) + yapWaveOffsetXZ(xz + vec2(0.0, e), t) * 0.12 * midW, t);

    // Flatten slope with distance so horizon water reads as a sheet
    float slopeScale = mix(0.22, 1.0, nearW) * mix(0.55, 1.0, farW);
    slopeScale *= clamp(0.70 + WAVE_STRENGTH * 0.18, 0.70, 1.15);
    // Overcast / rain: calmer slopes (storm swell is duller, not sharper)
    slopeScale *= mix(1.0, 0.72, rainStrength * 0.85);

    vec3 n = normalize(vec3((h0 - hx) / e * slopeScale, 1.0, (h0 - hz) / e * slopeScale));
    float up = clamp(dot(normalize(baseN), vec3(0.0, 1.0, 0.0)), 0.0, 1.0);
    float mixAmt = mix(0.35, 0.92, up) * mix(0.55, 1.0, midW);
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
    // Soft sun path; rain kills hard glints (overcast)
    float rainDim = mix(1.0, 0.22, rainStrength);
    float hot = pow(ndoth, 180.0) * ndotl * 0.32 * rainDim;
    float mid = pow(ndoth, 36.0) * ndotl * 0.10 * mix(1.0, 0.45, rainStrength);
    float wide = pow(ndoth, 10.0) * ndotl * 0.045;
    return vec3(0.88, 0.93, 1.00) * (hot + mid + wide);
#else
    return vec3(0.0);
#endif
}

vec3 yapWaterAlbedo(float depthMix, float rain) {
    // Clear shallow teal → deep navy; storm shifts toward slate without mud
    vec3 shallow = vec3(0.05, 0.22, 0.28);
    vec3 deep = vec3(0.012, 0.045, 0.10);
    vec3 storm = vec3(0.04, 0.07, 0.10);
    vec3 body = mix(shallow, deep, pow(clamp(depthMix, 0.0, 1.0), 0.72));
    return mix(body, storm, rain * 0.78);
}

vec3 yapWaterAbsorb(vec3 light, float thickness) {
    vec3 coeff = vec3(0.52, 0.14, 0.075);
    float t = clamp(thickness, 0.0, 4.0);
    return light * exp(-coeff * t);
}

// Vertical / steep water (falls & banks). Normals flow downward on the face —
// do NOT use the horizontal lake Gerstner field (that turns falls into mirrors).
vec3 yapWaterfallNormal(vec3 wp, float t, vec3 baseN) {
    vec3 n0 = normalize(baseN);
    vec3 down = vec3(0.0, -1.0, 0.0);
    vec3 bitan = cross(n0, down);
    if (dot(bitan, bitan) < 1e-4) {
        bitan = cross(n0, vec3(1.0, 0.0, 0.0));
    }
    bitan = normalize(bitan);
    vec3 tanD = normalize(cross(bitan, n0));

    float u = dot(wp, bitan);
    float v = dot(wp, tanD) - t * 2.4;
    float ripple =
          0.55 * sin(v * 3.1 + u * 0.7)
        + 0.30 * sin(v * 7.2 - u * 1.4 + 1.7)
        + 0.15 * sin(v * 13.0 + u * 2.2);
    ripple *= 0.18 * clamp(WAVE_STRENGTH, 0.4, 1.6);

    vec3 n = normalize(n0 + bitan * ripple * 0.45 + tanD * ripple);
    return n;
}

vec3 yapWaterfallAlbedo(float rain) {
    vec3 clearCol = vec3(0.10, 0.30, 0.36);
    vec3 stormCol = vec3(0.06, 0.11, 0.15);
    return mix(clearCol, stormCol, rain * 0.65);
}

#endif
