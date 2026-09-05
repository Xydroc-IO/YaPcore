// YaP shader pack options — Iris discovers these via #define + // [values]
// Keep at top of common.glsl include chain. Do not wrap in #ifndef guards.

#define WAVE_STRENGTH 1.50 // [0.20 0.25 0.30 0.35 0.40 0.45 0.50 0.55 0.60 0.65 0.70 0.75 0.80 0.85 0.90 0.95 1.00 1.05 1.10 1.15 1.20 1.25 1.30 1.35 1.40 1.45 1.50 1.55 1.60 1.65 1.70 1.75 1.80 1.85 1.90 1.95 2.00]
#define WIND
#define WIND_STRENGTH 1.00 // [0.00 0.05 0.10 0.15 0.20 0.25 0.30 0.35 0.40 0.45 0.50 0.55 0.60 0.65 0.70 0.75 0.80 0.85 0.90 0.95 1.00 1.05 1.10 1.15 1.20 1.25 1.30 1.35 1.40 1.45 1.50 1.55 1.60 1.65 1.70 1.75 1.80 1.85 1.90 1.95 2.00]
#define SSR
#define SSR_STEPS 48 // [8 12 16 20 24 28 32 36 40 44 48 52 56 60 64]
#define REFRACTION
#define CAUSTICS
#define SPECULAR
#define SUN_GLOW
//#define HORIZON_HAZE
#define VOLUMETRIC_CLOUDS
#define CLOUD_STEPS 12 // [8 10 12 14 16 18 20 22 24 26 28 30 32]
