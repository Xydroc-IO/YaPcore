#version 120

#include "/lib/common.glsl"
#include "/lib/varyings.glsl"
#include "/lib/water.glsl"

// Iris: mc_Entity.x = block.properties id (-1 if unmapped). Injected on water too.
attribute vec2 mc_Entity;
varying float yapEntityId;

void main() {
    yapEntityId = mc_Entity.x;
    bool isWater = abs(mc_Entity.x - 10001.0) < 0.5;

    vec4 view = gl_ModelViewMatrix * gl_Vertex;
    vec3 wp = (gbufferModelViewInverse * view).xyz + cameraPosition;

    // Almost-flat mesh — Gerstner heave on 1m quads looked like ocean stair-steps.
    if (isWater) {
        vec3 nWorld = normalize(mat3(gbufferModelViewInverse) * gl_NormalMatrix * gl_Normal);
        float up = clamp(nWorld.y, 0.0, 1.0);
        if (up > 0.45) {
            vec3 disp = yapWaterDisplace(wp, frameTimeCounter);
            wp = mix(wp, disp, smoothstep(0.45, 0.85, up) * 0.65);
        }
    }

    view = gbufferModelView * vec4(wp - cameraPosition, 1.0);

    viewPos = view.xyz;
    worldPos = wp;
    normal = normalize(gl_NormalMatrix * gl_Normal);
    texcoord = gl_MultiTexCoord0;
    lmcoord = gl_TextureMatrix[1] * gl_MultiTexCoord1;
    glcolor = gl_Color;
    gl_Position = gl_ProjectionMatrix * view;
}
