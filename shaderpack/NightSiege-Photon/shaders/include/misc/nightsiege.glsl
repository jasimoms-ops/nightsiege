#if !defined INCLUDE_MISC_NIGHTSIEGE
#define INCLUDE_MISC_NIGHTSIEGE

/*
--------------------------------------------------------------------------------
  Night Siege events

  The Night Siege mod hands the event that is shown right now to every
  program. Without the mod both values stay 0 and the pack looks exactly
  like Photon.

  ns_event:    0 none, 1 blood moon, 2 eclipse, 3 fog, 4 thunder,
               5 nether rift, 6 frost, 7 aurora
  ns_strength: 0..1, fades in and out with the event
--------------------------------------------------------------------------------
*/

uniform int ns_event;
uniform float ns_strength;

#define NS_BLOOD_MOON  1
#define NS_ECLIPSE     2
#define NS_FOG         3
#define NS_THUNDER     4
#define NS_NETHER_RIFT 5
#define NS_FROST       6
#define NS_AURORA      7

float ns_strength01() {
    return clamp(ns_strength, 0.0, 1.0);
}

float ns_amount(int id) {
    return ns_event == id ? ns_strength01() : 0.0;
}

// Tint of sunlight (moon = false) or moonlight (moon = true). Through the
// light colours it reaches the sky, the ambient light, the clouds and the fog.
vec3 ns_light_tint(bool moon) {
    vec3 tint = vec3(1.0);

    if (ns_event == NS_ECLIPSE) {
        tint = moon ? vec3(1.0) : vec3(0.030, 0.028, 0.040);
    } else if (ns_event == NS_BLOOD_MOON) {
        tint = vec3(2.30, 0.30, 0.20);
    } else if (ns_event == NS_NETHER_RIFT) {
        tint = vec3(2.00, 0.60, 0.28);
    } else if (ns_event == NS_FROST) {
        tint = vec3(0.68, 0.88, 1.32);
    } else if (ns_event == NS_FOG) {
        tint = vec3(0.66, 0.68, 0.70);
    } else if (ns_event == NS_THUNDER) {
        tint = vec3(0.80, 0.86, 1.08);
    }

    return mix(vec3(1.0), tint, ns_strength01());
}

// Event fog: x is the extinction per block, yzw the share of it that scatters
// light back (so also the colour of the fog).
vec4 ns_fog_look() {
    if (ns_event == NS_FOG)         return vec4(0.110, 0.80, 0.82, 0.84);
    if (ns_event == NS_FROST)       return vec4(0.034, 0.80, 0.90, 1.00);
    if (ns_event == NS_NETHER_RIFT) return vec4(0.040, 0.95, 0.36, 0.20);
    if (ns_event == NS_BLOOD_MOON)  return vec4(0.016, 0.95, 0.28, 0.20);
    if (ns_event == NS_ECLIPSE)     return vec4(0.010, 0.70, 0.70, 0.78);
    return vec4(0.0);
}

float ns_fog_amount() {
    return ns_fog_look().x > 0.0 ? ns_strength01() : 0.0;
}

// Height of the event fog: full up to 24 blocks above sea level, above that it
// halves every 22 blocks, so it also fills valleys and covers hills.
const float ns_fog_start = SEA_LEVEL + 24.0;
const float ns_fog_half_life = 22.0;

float ns_fog_density(float y) {
    return exp2(min((ns_fog_start - y) / ns_fog_half_life, 0.0));
}

// Integral of ns_fog_density along a ray from height y0 with vertical
// direction dy over the length len.
float ns_fog_airmass(float y0, float dy, float len) {
    const float m = -1.0 / ns_fog_half_life;

    if (abs(dy) < 1e-4) {
        return len * ns_fog_density(y0);
    }

    float t_cross = (ns_fog_start - y0) / dy;
    float below, t0, t1;

    if (y0 < ns_fog_start) {
        if (dy <= 0.0) return len;
        below = min(t_cross, len);
        t0 = below;
        t1 = len;
    } else if (dy >= 0.0) {
        below = 0.0;
        t0 = 0.0;
        t1 = len;
    } else {
        t0 = 0.0;
        t1 = min(t_cross, len);
        below = max(len - t_cross, 0.0);
    }

    float above = 0.0;
    if (t1 > t0) {
        float k = m * dy;
        float a = (y0 - ns_fog_start) * m;
        above = (exp2(a + k * t1) - exp2(a + k * t0)) / (log(2.0) * k);
    }

    return below + max(above, 0.0);
}

// Which picture of the moon atlas (blood, frost, rift, fog); -1 = Photon's moon
int ns_moon_index() {
    if (ns_event == NS_BLOOD_MOON)  return 0;
    if (ns_event == NS_FROST)       return 1;
    if (ns_event == NS_NETHER_RIFT) return 2;
    if (ns_event == NS_FOG)         return 3;
    return -1;
}

#endif // INCLUDE_MISC_NIGHTSIEGE
