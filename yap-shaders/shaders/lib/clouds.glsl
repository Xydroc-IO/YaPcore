#ifndef YAP_CLOUDS_GLSL
#define YAP_CLOUDS_GLSL

#ifndef CLOUD_STEPS
#define CLOUD_STEPS 16
#endif

const float YAP_CLOUD_BOTTOM = 168.0;
const float YAP_CLOUD_THICK  = 36.0;
const float YAP_CLOUD_SCALE  = 0.00135;

float yapCloudHash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float yapCloudNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = yapCloudHash(i);
    float b = yapCloudHash(i + vec2(1.0, 0.0));
    float c = yapCloudHash(i + vec2(0.0, 1.0));
    float d = yapCloudHash(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float yapCloudFbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 5; i++) {
        v += a * yapCloudNoise(p);
        p = p * 2.05 + vec2(17.1, 9.3);
        a *= 0.5;
    }
    return v;
}

float yapCloudDensity(vec3 worldPos, float rain) {
    vec2 drift = vec2(frameTimeCounter * 0.012, frameTimeCounter * 0.0045);
    vec2 uv = worldPos.xz * YAP_CLOUD_SCALE + drift;
    float banks = yapCloudFbm(uv * vec2(1.0, 0.55));
    float detail = yapCloudFbm(uv * 3.4 + vec2(4.2, 1.7));
    float field = banks * 0.72 + detail * 0.28;
    float cover = mix(0.52, 0.34, rain);
    float dens = smoothstep(cover, cover + 0.22, field);
    dens *= dens;
    float h = (worldPos.y - YAP_CLOUD_BOTTOM) / YAP_CLOUD_THICK;
    float shape = smoothstep(0.0, 0.18, h) * (1.0 - smoothstep(0.55, 1.0, h));
    // Slight puff at mid-height so slabs read as volumes.
    shape *= 0.75 + 0.25 * sin(h * 3.14159);
    dens *= shape;
    dens *= mix(1.0, 1.35, rain);
    return clamp(dens, 0.0, 1.0);
}

float yapCloudShadow(vec3 pos, vec3 sunDir, float rain) {
    float shadow = 1.0;
    vec3 p = pos;
    vec3 stepDir = normalize(sunDir) * (YAP_CLOUD_THICK * 0.22);
    for (int i = 0; i < 4; i++) {
        p += stepDir;
        float d = yapCloudDensity(p, rain);
        shadow *= exp(-d * 1.8);
    }
    return mix(0.35, 1.0, shadow);
}

// Returns rgb = cloud lighting, a = opacity to composite over sky.
vec4 yapVolumetricClouds(vec3 worldDir, vec3 sunView, float rain) {
    vec3 rd = normalize(worldDir);
    vec3 ro = cameraPosition;

    float y0 = YAP_CLOUD_BOTTOM;
    float y1 = YAP_CLOUD_BOTTOM + YAP_CLOUD_THICK;

    // Looking away from the slab
    if (abs(rd.y) < 1e-4) {
        return vec4(0.0);
    }

    float tEnter = (y0 - ro.y) / rd.y;
    float tExit  = (y1 - ro.y) / rd.y;
    if (tEnter > tExit) {
        float tmp = tEnter;
        tEnter = tExit;
        tExit = tmp;
    }

    // Below clouds looking down, or above looking up with no hit
    if (tExit < 0.0) {
        return vec4(0.0);
    }
    tEnter = max(tEnter, 0.0);
    float maxDist = 2200.0;
    if (tEnter > maxDist) {
        return vec4(0.0);
    }
    tExit = min(tExit, maxDist);

    float travel = tExit - tEnter;
    if (travel < 1.0) {
        return vec4(0.0);
    }

    float dt = travel / float(CLOUD_STEPS);
    vec3 pos = ro + rd * (tEnter + dt * 0.35);
    vec3 stepV = rd * dt;

    vec3 sunDirWorld = normalize(mat3(gbufferModelViewInverse) * sunView);
    float sunUp = clamp(sunDirWorld.y * 0.5 + 0.5, 0.0, 1.0);
    float dayness = clamp(luma(skyColor) * 2.5, 0.0, 1.0);

    vec3 cloudLit = mix(vec3(0.55, 0.58, 0.70), vec3(0.96, 0.97, 0.99), dayness);
    vec3 cloudShade = mix(vec3(0.12, 0.14, 0.20), vec3(0.55, 0.60, 0.72), dayness);
    // Warm rim near the sun disk
    float towardSun = max(dot(rd, sunDirWorld), 0.0);
    vec3 sunTint = mix(vec3(1.0), vec3(1.0, 0.78, 0.55), pow(towardSun, 4.0) * sunUp);

    vec3 accum = vec3(0.0);
    float transm = 1.0;
    float optical = mix(1.35, 2.1, rain);

    for (int i = 0; i < CLOUD_STEPS; i++) {
        float dens = yapCloudDensity(pos, rain);
        if (dens > 0.002) {
            float sh = yapCloudShadow(pos, sunDirWorld, rain);
            float h = clamp((pos.y - YAP_CLOUD_BOTTOM) / YAP_CLOUD_THICK, 0.0, 1.0);
            vec3 albedo = mix(cloudShade, cloudLit, h * 0.65 + 0.2);
            albedo *= sunTint;
            // Powder sugar: soft forward scatter near the sun
            float powder = 1.0 - exp(-dens * 2.0);
            vec3 light = albedo * (0.28 + 0.72 * sh) * (0.85 + 0.35 * powder);
            light += vec3(1.0, 0.92, 0.8) * pow(towardSun, 8.0) * sh * dens * 0.35 * dayness;

            float absorb = exp(-dens * optical * dt * 0.045);
            accum += transm * (1.0 - absorb) * light;
            transm *= absorb;
            if (transm < 0.02) {
                break;
            }
        }
        pos += stepV;
    }

    float alpha = clamp(1.0 - transm, 0.0, 1.0);
    // Soft horizon fade so clouds do not form a hard plane edge
    float horizonFade = smoothstep(0.02, 0.14, abs(rd.y));
    alpha *= horizonFade;
    accum *= horizonFade;

    return vec4(accum, alpha);
}

vec3 yapSkyWithClouds(vec3 worldDir, vec3 sunView, vec3 skyCol, float rain) {
#ifdef VOLUMETRIC_CLOUDS
    vec4 c = yapVolumetricClouds(worldDir, sunView, rain);
    return mix(skyCol, c.rgb, clamp(c.a, 0.0, 1.0));
#else
    return skyCol;
#endif
}

#endif
