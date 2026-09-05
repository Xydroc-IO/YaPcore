#version 120

#include "/lib/common.glsl"
#include "/lib/varyings.glsl"
#include "/lib/water.glsl"

void main() {
    vec4 view = gl_ModelViewMatrix * gl_Vertex;
    vec3 wp = (gbufferModelViewInverse * view).xyz + cameraPosition;

    // Only warp upward-facing water (surface). Side faces stay put so banks don't tear.
    vec3 nWorld = normalize(mat3(gbufferModelViewInverse) * gl_NormalMatrix * gl_Normal);
    float up = clamp(nWorld.y, 0.0, 1.0);
    if (up > 0.30) {
        vec3 disp = yapWaterDisplace(wp, frameTimeCounter);
        wp = mix(wp, disp, smoothstep(0.30, 0.75, up));
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
