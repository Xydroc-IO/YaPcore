#version 120

#include "/lib/common.glsl"
#include "/lib/sky.glsl"
#include "/lib/varyings.glsl"

varying vec3 viewDir;

void main() {
    vec4 view = gl_ModelViewMatrix * gl_Vertex;
    viewPos = view.xyz;
    worldPos = (gbufferModelViewInverse * view).xyz + cameraPosition;
    viewDir = normalize((gbufferModelViewInverse * view).xyz);
    normal = vec3(0.0, 1.0, 0.0);
    texcoord = gl_MultiTexCoord0;
    lmcoord = vec4(0.0);
    glcolor = gl_Color;
    gl_Position = gl_ProjectionMatrix * view;
}
