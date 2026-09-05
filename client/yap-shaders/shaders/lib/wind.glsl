#ifndef YAP_WIND_GLSL
#define YAP_WIND_GLSL

// Phase 3 — wind-driven tree sway:
//  - Global wind direction (sway goes with the wind)
//  - Speed from weather (clear ≈ still; rain/thunder = more)
//  - Species profiles (birch flexible, spruce stiff, jungle tall, …)
//  - Height bend (base rooted, top leads)
//
// block.properties species pairs: 10011/21 flex · 10012/22 mid · 10013/23 stiff ·
//   10014/24 jungle · 10015/25 azalea · 10002 grass · 10003 vines · 10004 nether wood

vec3 yapBlockCenterFallback(vec3 wp) {
    return vec3(floor(wp.x) + 0.5, floor(wp.y) + 0.5, floor(wp.z) + 0.5);
}

vec3 yapBlockCenter(vec3 wp, vec3 midOff) {
    if (dot(midOff, midOff) < 1e-8) {
        return yapBlockCenterFallback(wp);
    }
    return wp + midOff / 64.0;
}

vec3 yapTreeColumn(vec3 wp) {
    return vec3(floor(wp.x) + 0.5, 0.0, floor(wp.z) + 0.5);
}

// Weather wind speed. Clear skies ≈ 0 (no fake breeze). Rain/thunder drive motion.
// WIND_STRENGTH is the user multiplier (0 = force off).
float yapWindSpeed() {
    float rain = clamp(rainStrength, 0.0, 1.0);
    float thunder = clamp(thunderStrength, 0.0, 1.0);
    // Rain does most of the work; thunder adds gusts on top
    float weather = rain * 0.75 + thunder * 0.55;
    weather = clamp(weather, 0.0, 1.4);
    return weather * clamp(WIND_STRENGTH, 0.0, 2.5);
}

// Slow global heading — whole forest leans the same way.
vec2 yapWindDir(float t) {
    float ang = t * 0.012 + sin(t * 0.0063) * 0.55;
    return vec2(cos(ang), sin(ang));
}

// Displacement vector along wind. Gust varies gently along the wind axis only.
vec2 yapWindVec(vec3 col, float t) {
    float speed = yapWindSpeed();
    if (speed < 0.01) {
        return vec2(0.0);
    }
    vec2 dir = yapWindDir(t);
    float along = dot(col.xz, dir);
    float gust = 0.70 + 0.30 * sin(along * 0.055 + t * 0.38);
    // ~16 cm canopy travel at full storm + strength 1
    return dir * (0.16 * speed * gust);
}

float yapTreePivotY(float y) {
    return floor(y / 16.0) * 16.0;
}

// heightRef: shorter/flexible trees fill the lean curve sooner.
float yapTreeBendAmt(float y, float heightRef) {
    float h = max(y - yapTreePivotY(y), 0.0);
    float n = clamp(h / max(heightRef, 4.0), 0.0, 1.75);
    return n * n;
}

// Species profile → flex (amp), heightRef (bend curve), woodStiff (0 leaves .. 1 trunk default)
void yapSpeciesProfile(float entityId, out float flex, out float heightRef, out float woodness) {
    // Defaults = medium oak
    flex = 1.0;
    heightRef = 9.0;
    woodness = 0.5;

    // Flexible: birch / cherry / pale oak
    if (abs(entityId - 10011.0) < 0.5) { flex = 1.30; heightRef = 7.0; woodness = 0.0; return; }
    if (abs(entityId - 10021.0) < 0.5) { flex = 1.30; heightRef = 7.0; woodness = 1.0; return; }

    // Medium: oak / acacia / mangrove
    if (abs(entityId - 10012.0) < 0.5) { flex = 1.00; heightRef = 9.0; woodness = 0.0; return; }
    if (abs(entityId - 10022.0) < 0.5) { flex = 1.00; heightRef = 9.0; woodness = 1.0; return; }

    // Stiff: spruce / dark oak
    if (abs(entityId - 10013.0) < 0.5) { flex = 0.72; heightRef = 11.0; woodness = 0.0; return; }
    if (abs(entityId - 10023.0) < 0.5) { flex = 0.72; heightRef = 11.0; woodness = 1.0; return; }

    // Tall jungle
    if (abs(entityId - 10014.0) < 0.5) { flex = 1.15; heightRef = 14.0; woodness = 0.0; return; }
    if (abs(entityId - 10024.0) < 0.5) { flex = 1.15; heightRef = 14.0; woodness = 1.0; return; }

    // Soft azalea shrub
    if (abs(entityId - 10015.0) < 0.5) { flex = 1.35; heightRef = 5.0; woodness = 0.0; return; }
    if (abs(entityId - 10025.0) < 0.5) { flex = 1.35; heightRef = 5.0; woodness = 0.85; return; }

    // Nether / leftover wood
    if (abs(entityId - 10004.0) < 0.5) { flex = 0.65; heightRef = 8.0; woodness = 1.0; return; }
}

vec3 yapTreeSway(vec3 wp, vec3 midOff, float t, float entityId) {
    float flex, heightRef, woodness;
    yapSpeciesProfile(entityId, flex, heightRef, woodness);

    vec2 wind = yapWindVec(yapTreeColumn(wp), t);
    if (dot(wind, wind) < 1e-8) {
        return vec3(0.0);
    }

    float bend = yapTreeBendAmt(wp.y, heightRef);
    // Trunk stiffer than leaves of the same species
    float stiff = mix(1.0, 0.78, clamp(woodness, 0.0, 1.0));
    bend *= stiff * flex;

    float local = clamp(fract(wp.y), 0.0, 1.0);
    bend *= mix(0.92, 1.0, local * local);

    float settle = length(wind) * bend * 0.10;
    return vec3(wind.x * bend, -settle, wind.y * bend);
}

vec3 yapPlantSway(vec3 wp, vec3 midOff, float t, float tip) {
    vec2 wind = yapWindVec(yapBlockCenter(wp, midOff), t);
    if (dot(wind, wind) < 1e-8) {
        return vec3(0.0);
    }
    float tipW = clamp(tip, 0.0, 1.0);
    tipW = tipW * tipW;
    float mag = length(wind);
    vec2 dir = wind / max(mag, 1e-5);
    // Grass uses wind magnitude directly (already weather-scaled)
    float amp = mag * 1.15 * tipW;
    float pulse = 0.70 + 0.30 * sin(t * 0.45 + yapTreeColumn(wp).x * 0.1);
    return vec3(dir.x * amp * pulse, 0.0, dir.y * amp * pulse);
}

vec3 yapVineSway(vec3 wp, vec3 midOff, float t, float tip) {
    vec2 wind = yapWindVec(yapBlockCenter(wp, midOff), t);
    if (dot(wind, wind) < 1e-8) {
        return vec3(0.0);
    }
    float tipW = clamp(tip, 0.0, 1.0);
    float mag = length(wind);
    vec2 dir = wind / max(mag, 1e-5);
    float amp = mag * 0.85 * tipW;
    return vec3(dir.x * amp, 0.0, dir.y * amp);
}

bool yapIsTreeId(float id) {
    return abs(id - 10004.0) < 0.5
        || abs(id - 10011.0) < 0.5 || abs(id - 10012.0) < 0.5
        || abs(id - 10013.0) < 0.5 || abs(id - 10014.0) < 0.5
        || abs(id - 10015.0) < 0.5
        || abs(id - 10021.0) < 0.5 || abs(id - 10022.0) < 0.5
        || abs(id - 10023.0) < 0.5 || abs(id - 10024.0) < 0.5
        || abs(id - 10025.0) < 0.5;
}

vec3 yapApplyWind(vec3 wp, float entityId, float tip, vec3 midOff) {
#ifdef WIND
    if (yapWindSpeed() < 0.01) {
        return wp;
    }
    if (yapIsTreeId(entityId)) {
        return wp + yapTreeSway(wp, midOff, frameTimeCounter, entityId);
    }
    if (abs(entityId - 10002.0) < 0.5) {
        return wp + yapPlantSway(wp, midOff, frameTimeCounter, tip);
    }
    if (abs(entityId - 10003.0) < 0.5) {
        return wp + yapVineSway(wp, midOff, frameTimeCounter, tip);
    }
#endif
    return wp;
}

#endif
