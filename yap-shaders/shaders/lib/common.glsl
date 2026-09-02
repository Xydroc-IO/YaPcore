#ifndef YAP_COMMON_GLSL
#define YAP_COMMON_GLSL

const float PI = 3.14159265359;
const float EPS = 1e-4;

uniform float frameTimeCounter;
uniform vec3 sunPosition;
uniform vec3 moonPosition;
uniform float rainStrength;
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

uniform sampler2D texture;
uniform sampler2D lightmap;
uniform sampler2D depthtex0;
uniform sampler2D depthtex1;
uniform sampler2D colortex0;
uniform sampler2D colortex1;
uniform sampler2D colortex2;
uniform sampler2D colortex3;
uniform sampler2D noisetex;

varying vec4 texcoord;
varying vec4 lmcoord;
varying vec4 glcolor;
varying vec3 viewPos;
varying vec3 worldPos;
varying vec3 normal;

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
    float f = 1.0 - exp(-dist * 0.008);
    f = clamp(f + rainStrength * 0.25, 0.0, 1.0);
    return mix(color, fogCol, f);
}

vec2 encodeNormal(vec3 n) {
    n = normalize(n);
    return n.xy * 0.5 + 0.5;
}

vec3 decodeNormal(vec2 e) {
    vec2 f = e * 2.0 - 1.0;
    float z = sqrt(max(1.0 - dot(f, f), 0.0));
    return normalize(vec3(f, z));
}

#endif
