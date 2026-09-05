#ifndef YAP_COMMON_GLSL
#define YAP_COMMON_GLSL

#include "/lib/options.glsl"

const float PI = 3.14159265359;
const float EPS = 1e-4;

uniform float frameTimeCounter;
uniform vec3 sunPosition;
uniform vec3 moonPosition;
uniform float rainStrength;
uniform float thunderStrength;
uniform int worldTime;
uniform float viewWidth;
uniform float viewHeight;
uniform float aspectRatio;
uniform mat4 gbufferModelView;
uniform mat4 gbufferModelViewInverse;
uniform mat4 gbufferProjection;
uniform mat4 gbufferProjectionInverse;
uniform vec3 cameraPosition;
uniform int isEyeInWater;
uniform float near;
uniform float far;
uniform vec3 skyColor;
uniform vec3 fogColor;
uniform float eyeAltitude;
// Iris: WorldRenderingPhase ordinal (see MC_RENDER_STAGE_* macros)
uniform int renderStage;

uniform sampler2D texture;
uniform sampler2D lightmap;
uniform sampler2D depthtex0;
uniform sampler2D depthtex1;
uniform sampler2D colortex0;
uniform sampler2D colortex1;
uniform sampler2D colortex2;
uniform sampler2D colortex3;
uniform sampler2D noisetex;

float luma(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

float linearizeDepth(float z) {
    return (2.0 * near) / (far + near - z * (far - near));
}

vec3 screenToView(vec3 screenPos) {
    vec4 ndc = vec4(screenPos.xy * 2.0 - 1.0, screenPos.z * 2.0 - 1.0, 1.0);
    vec4 view = gbufferProjectionInverse * ndc;
    return view.xyz / view.w;
}

vec3 viewToScreen(vec3 view) {
    vec4 clip = gbufferProjection * vec4(view, 1.0);
    vec3 ndc = clip.xyz / clip.w;
    return ndc * 0.5 + 0.5;
}

vec3 applyFog(vec3 color, float dist, vec3 fogCol) {
    // Gentle distance haze — old rate 0.008 washed mid-range into a white wall.
    float rate = (isEyeInWater == 1) ? 0.022 : 0.0026;
    float f = 1.0 - exp(-dist * rate);
    float rainMist = rainStrength * (1.0 - exp(-dist * 0.0018)) * 0.10;
    float cap = (isEyeInWater == 1) ? 0.78 : 0.48;
    f = clamp(f + rainMist, 0.0, cap);
    if (isEyeInWater == 1) {
        fogCol = mix(fogCol, vec3(0.015, 0.08, 0.16), 0.80);
    } else {
        // Prefer sky tint over vanilla chalk fogColor
        fogCol = mix(fogCol, skyColor, 0.55);
        fogCol = mix(fogCol, vec3(0.42, 0.50, 0.60), 0.22);
    }
    return mix(color, fogCol, f);
}

vec3 encodeNormal(vec3 n) {
    // Full XYZ — the old xy-only encode forced +Z and wrecked undersides/backfaces.
    return normalize(n) * 0.5 + 0.5;
}

vec3 decodeNormal(vec3 e) {
    return normalize(e * 2.0 - 1.0);
}

#endif
