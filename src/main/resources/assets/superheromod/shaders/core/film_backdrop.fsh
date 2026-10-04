#version 150
// Procedural film backdrop, evaluated per pixel every frame.
// Scene 0/3: rushing hell clouds (falling down / plunging back).
// Scene 1/2: space with a burning banded star, its halo and a spinning ring.
// Scene 9: a city at night (Black Panther's The Final Pursuit).
// Scene 10: a dead world under a crimson storm (Magneto's Magnetic Execution).
// Scene 11: Gotham at night, the city below (Batman's Kara Sovalye).
uniform float Time;      // seconds
uniform float Scene;
uniform vec3 CamPos;     // stage space
uniform vec3 CamF;
uniform vec3 CamR;
uniform vec3 CamU;
uniform vec2 Lens;       // x = tan(half vertical fov), y = aspect
uniform vec4 Planet;     // xyz centre, w radius
uniform vec4 Ring;       // inner, outer (in planet radii), spin speed, opacity
uniform vec4 Motion;     // x fall speed, y warp streaks, z cloud density, w x-ray grade
uniform vec4 Tint;       // rgb, amount

in vec2 ndc;
out vec4 fragColor;

float hash13(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}
float noise3(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float n000 = hash13(i);
    float n100 = hash13(i + vec3(1.0, 0.0, 0.0));
    float n010 = hash13(i + vec3(0.0, 1.0, 0.0));
    float n110 = hash13(i + vec3(1.0, 1.0, 0.0));
    float n001 = hash13(i + vec3(0.0, 0.0, 1.0));
    float n101 = hash13(i + vec3(1.0, 0.0, 1.0));
    float n011 = hash13(i + vec3(0.0, 1.0, 1.0));
    float n111 = hash13(i + vec3(1.0, 1.0, 1.0));
    return mix(mix(mix(n000, n100, f.x), mix(n010, n110, f.x), f.y),
               mix(mix(n001, n101, f.x), mix(n011, n111, f.x), f.y), f.z);
}
float fbm(vec3 p) {
    float sum = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 5; i++) {
        sum += amp * noise3(p);
        p = p * 2.03 + vec3(1.7, 9.2, 3.1);
        amp *= 0.5;
    }
    return sum;
}
float fbm3(vec3 p) {
    float sum = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 3; i++) {
        sum += amp * noise3(p);
        p = p * 2.11 + vec3(4.3, 1.9, 7.7);
        amp *= 0.5;
    }
    return sum;
}
vec3 fireRamp(float t) {
    vec3 c = mix(vec3(0.06, 0.0, 0.0), vec3(0.62, 0.05, 0.01), smoothstep(0.0, 0.35, t));
    c = mix(c, vec3(1.0, 0.40, 0.05), smoothstep(0.35, 0.7, t));
    return mix(c, vec3(1.0, 0.88, 0.58), smoothstep(0.75, 1.0, t));
}
vec3 space(vec3 d) {
    vec3 c = vec3(0.0);
    vec3 g = d * 170.0;
    vec3 cell = floor(g);
    float h = hash13(cell);
    if (h > 0.982) {
        vec3 jitter = vec3(hash13(cell + 1.3), hash13(cell + 2.7), hash13(cell + 5.1)) - 0.5;
        float dd = length(g - (cell + 0.5 + 0.35 * jitter));
        float twinkle = 0.75 + 0.25 * sin(Time * 3.0 + h * 50.0);
        c += vec3(1.0, 0.9, 0.82) * smoothstep(0.16, 0.0, dd) * (h - 0.982) * 55.0 * twinkle;
    }
    float n = fbm(d * 2.2 + vec3(0.0, Time * 0.01, 0.0));
    float m = fbm(d * 4.7 + n * 1.6);
    c += vec3(0.42, 0.05, 0.02) * pow(n, 3.0) * 1.7;
    c += vec3(0.16, 0.03, 0.10) * pow(m, 4.0) * 1.8;
    return c;
}
bool sphereHit(vec3 ro, vec3 rd, vec4 s, out float t0) {
    vec3 oc = ro - s.xyz;
    float b = dot(oc, rd);
    float c = dot(oc, oc) - s.w * s.w;
    float h = b * b - c;
    t0 = 0.0;
    if (h < 0.0) return false;
    t0 = -b - sqrt(h);
    return t0 > 0.0;
}
vec3 starSurface(vec3 n, vec3 rd) {
    float a = Time * 0.05;
    vec3 p = n;
    p.xz = mat2(cos(a), -sin(a), sin(a), cos(a)) * p.xz;
    // Gas-giant banding: latitude bands, domain-warped so they flow and curl at the edges,
    // fine streaks stretched along longitude, and one great storm eye.
    float warp = fbm(p * vec3(2.0, 7.0, 2.0) + vec3(0.0, 0.0, Time * 0.06));
    float lat = p.y + (warp - 0.5) * 0.12;
    float bands = 0.5 + 0.30 * sin(lat * 23.0) + 0.20 * sin(lat * 61.0 + warp * 4.0);
    float streaks = fbm(vec3(p.x * 2.5, lat * 48.0, p.z * 2.5) + vec3(Time * 0.04, 0.0, 0.0));
    vec3 spotCentre = normalize(vec3(0.55, -0.28, 0.79));
    float spot = smoothstep(0.32, 0.0, length((p - spotCentre) * vec3(1.0, 2.2, 1.0)));
    float swirl = fbm(p * 9.0 + spot * 3.0 + vec3(Time * 0.1));
    float heat = clamp(0.12 + 0.5 * bands + 0.35 * (streaks - 0.5) + spot * (0.45 * swirl - 0.15), 0.0, 1.0);
    vec3 c = fireRamp(heat);
    float facing = max(0.0, dot(n, -rd));
    float limb = pow(1.0 - facing, 2.0);
    c *= mix(1.0, 0.55, limb);
    c += vec3(1.0, 0.5, 0.12) * pow(limb, 3.0) * 1.3;
    return c * 1.3;
}
float cloudDensity(vec3 p) {
    vec3 q = p * 0.13 + vec3(0.0, Time * Motion.x, 0.0);
    float base = fbm3(q);
    float detail = fbm3(q * 3.1 + 5.0);
    return smoothstep(0.5, 0.78, base + 0.3 * detail) * Motion.z;
}
vec3 warpStreaks(vec3 col) {
    vec2 sp = ndc * vec2(Lens.y, 1.0);
    float rad = length(sp);
    float lanes = (atan(sp.y, sp.x) / 6.2831853 + 0.5) * 110.0;
    float lane = floor(lanes);
    float off = abs(fract(lanes) - 0.5);
    float h = hash13(vec3(lane, 3.0, 9.0));
    float s = fract(h * 13.0 + Time * (0.5 + h) * Motion.y * 0.6 - rad * 0.9);
    float streak = smoothstep(0.09, 0.0, off) * smoothstep(0.0, 0.03, s) * smoothstep(0.4, 0.03, s)
                 * smoothstep(0.12, 0.6, rad) * step(0.45, h);
    return col + vec3(1.0, 0.86, 0.7) * streak * Motion.y * 0.9;
}

// ---- Scene 10: the wasteland under the crimson storm ----
// Planet.xyz: where the lightning strikes (stage space, far off), Planet.w: the flash (0..1+).
// Ring.x: the bolt's seed, Ring.y: the bolt channel's brightness. Motion.x: wind, Motion.z: rain.
float boltDist(vec3 rd, vec3 target, float seed) {
    // The channel as seen from the camera: from the cloud base straight down to where it strikes, jagged on the way.
    vec3 to = target - CamPos;
    float azT = atan(to.x, to.z);
    float base = atan(to.y + 220.0, length(to.xz)) ;
    float foot = atan(to.y, length(to.xz));
    float up = asin(clamp(rd.y, -1.0, 1.0));
    float az = atan(rd.x, rd.z);
    if (up < foot - 0.002 || up > base) return 1.0;
    float u = (up - foot) / max(0.001, base - foot);
    float jag = (fbm3(vec3(u * 6.0, seed, 1.0)) - 0.5) * 0.08 + (noise3(vec3(u * 28.0, seed, 4.0)) - 0.5) * 0.018;
    float d = abs(az - azT - jag * (0.4 + u));
    // A fork leaving half way up.
    float fu = clamp((0.62 - u) / 0.4, 0.0, 1.0);
    float fork = abs(az - azT - jag * (0.4 + u) - fu * fu * 0.07 * sign(hash13(vec3(seed, 2.0, 2.0)) - 0.5) - (noise3(vec3(u * 22.0, seed, 9.0)) - 0.5) * 0.02);
    d = min(d, fork + step(0.62, u) * 1.0 + (1.0 - step(0.25, u)) * 0.6);
    return d;
}
vec3 wasteSky(vec3 rd, float flash) {
    float up = rd.y;
    vec3 zenith = vec3(0.05, 0.005, 0.01);
    vec3 horizon = vec3(0.62, 0.08, 0.045);
    vec3 col = mix(horizon, zenith, smoothstep(-0.02, 0.4, up));
    float az = atan(rd.x, rd.z);
    if (up > -0.02) {
        // Low, heavy storm clouds over the whole sky, rolling in the wind: black-red bellies, their undersides
        // and edges lit blood red by the burning horizon, breaks here and there showing the glow behind.
        float h = max(up, 0.0) + 0.05;
        vec2 cp = rd.xz / h * 0.55 + vec2(Time * Motion.x * 0.04, Time * Motion.x * 0.015);
        float warp = fbm3(vec3(cp * 0.3, Time * 0.025));
        float c = fbm(vec3(cp * 0.5 + warp * 1.6, Time * 0.015));
        float body = smoothstep(0.3, 0.55, c);
        float shade = fbm3(vec3(cp * 0.5 + vec2(0.11, -0.07) + warp * 1.6, 3.0));
        float lit = smoothstep(0.42, 0.75, shade) * (0.4 + 0.6 * exp(-up * 2.5));
        vec3 belly = vec3(0.09, 0.012, 0.016), edge = vec3(0.72, 0.11, 0.06);
        vec3 cloud = mix(belly, edge, lit);
        cloud *= 0.75 + 0.5 * smoothstep(0.5, 0.8, c);
        col = mix(col, cloud, body * smoothstep(-0.02, 0.05, up) * 0.95);
        // Glow breaking through between the masses, near the horizon.
        col += vec3(0.5, 0.07, 0.03) * (1.0 - body) * exp(-up * 6.0) * 0.4;
        // The flash lights the clouds from inside, strongest round the strike.
        vec3 to = normalize(Planet.xyz - CamPos);
        float near = pow(max(0.0, dot(normalize(vec3(rd.x, max(up, 0.05), rd.z)), normalize(vec3(to.x, 0.2, to.z)))), 6.0);
        col += vec3(1.0, 0.6, 0.66) * flash * (0.12 + 0.88 * near) * (0.3 + 0.7 * body) * smoothstep(-0.02, 0.1, up) * 0.8;
    }
    // A broken horizon: ruined towers with torn tops and bare girders, heaps of wreckage, black against the red,
    // fires glowing low among them.
    float heap = 0.006 + 0.03 * fbm3(vec3(az * 2.2, 0.0, 7.0)) + 0.012 * fbm3(vec3(az * 14.0, 5.0, 1.0));
    float cellAz = floor(az * 26.0);
    float fa = fract(az * 26.0);
    float pick = hash13(vec3(cellAz, 3.0, 1.0));
    float height = (0.025 + 0.08 * hash13(vec3(cellAz, 8.0, 2.0))) * step(0.45, pick);
    float wide = 0.3 + 0.45 * hash13(vec3(cellAz, 1.0, 9.0));
    float inside = step(0.5 - wide * 0.5, fa) * step(fa, 0.5 + wide * 0.5);
    // Torn tops: a slant and a bite out of them.
    float slant = (fa - 0.5) * (hash13(vec3(cellAz, 4.0, 4.0)) - 0.5) * 0.12;
    float bite = step(0.6, hash13(vec3(cellAz, 6.0, 2.0))) * smoothstep(0.08, 0.0, abs(fa - 0.3 - 0.4 * hash13(vec3(cellAz, 7.0, 7.0)))) * 0.03;
    float tower = (height + slant - bite) * inside;
    // A skeleton of girders above some of them.
    float rib = step(0.7, hash13(vec3(cellAz, 9.0, 3.0))) * inside * smoothstep(0.03, 0.0, abs(fract(fa * 5.0) - 0.5) - 0.42);
    float top = max(heap, max(tower, rib * (height + 0.03)));
    if (up < top && up > -0.004) {
        col = mix(col, vec3(0.025, 0.005, 0.007), 0.94);
        col += vec3(0.6, 0.25, 0.12) * flash * 0.12;
        // Windows burnt out, a few still lit by fire inside.
        vec2 g = vec2(az * 520.0, up * 420.0);
        float h = hash13(vec3(floor(g), 2.0));
        vec2 f = abs(fract(g) - 0.5);
        if (up > heap && h > 0.93) col += vec3(0.9, 0.25, 0.05) * step(f.x, 0.28) * step(f.y, 0.22) * (0.25 + 0.2 * sin(Time * 5.0 + h * 30.0));
        vec2 g2 = vec2(az * 240.0, up * 240.0);
        float h2 = hash13(vec3(floor(g2), 6.0));
        float spot = smoothstep(0.5, 0.0, length(fract(g2) - 0.5));
        if (h2 > 0.975 && up < heap * 0.7) col += vec3(1.0, 0.38, 0.08) * spot * (0.7 + 0.3 * sin(Time * 7.0 + h2 * 40.0));
    }
    col += vec3(0.38, 0.05, 0.025) * exp(-abs(up) * 22.0) * 0.6;
    return col;
}

// ---- Scene 11: Gotham at night (Batman's Kara Sovalye) ----
// Planet.xyz: toward the moon (hidden behind the cloud), Planet.w: how much of it shows through.
// Ring.x: a cold flash (0..1). Motion.x: cloud drift, Motion.z: rain.
vec3 gothamSky(vec3 rd) {
    float up = rd.y;
    // A black sky over a city that never sleeps: dull sodium-violet at the horizon, near black overhead.
    vec3 zenith = vec3(0.004, 0.005, 0.009);
    vec3 horizon = vec3(0.07, 0.05, 0.058);
    vec3 col = mix(horizon, zenith, smoothstep(-0.04, 0.42, up));
    vec3 moon = normalize(Planet.xyz);
    float m = max(0.0, dot(rd, moon));
    if (up > -0.04) {
        // Low heavy cloud drifting over everything, its bellies lit from below by the city, black overhead;
        // a silver edge on the thin parts near the hidden moon.
        float h = max(up, 0.0) + 0.06;
        vec2 cp = rd.xz / h * 0.5 + vec2(Time * Motion.x * 0.03, Time * Motion.x * 0.011);
        float warp = fbm3(vec3(cp * 0.35, Time * 0.02));
        float c = fbm(vec3(cp * 0.6 + warp * 1.4, 1.0 + Time * 0.01));
        float body = smoothstep(0.3, 0.62, c);
        float shade = fbm3(vec3(cp * 0.6 + vec2(0.13, -0.09) + warp * 1.4, 4.0));
        vec3 belly = mix(vec3(0.016, 0.015, 0.021), vec3(0.1, 0.068, 0.064), exp(-max(up, 0.0) * 3.5));
        vec3 cloud = belly * (0.55 + 0.7 * smoothstep(0.35, 0.8, shade));
        float halo = pow(m, 60.0) * 0.6 + pow(m, 9.0) * 0.08;
        cloud += vec3(0.5, 0.56, 0.7) * halo * (1.0 - smoothstep(0.42, 0.86, c)) * Planet.w;
        col = mix(col, cloud, body * smoothstep(-0.03, 0.06, up));
        // Through the gaps: the moon's light and a few faint stars.
        col += vec3(0.62, 0.68, 0.8) * smoothstep(0.9993, 0.9998, m) * (1.0 - body) * Planet.w;
        col += vec3(0.2, 0.23, 0.3) * pow(m, 14.0) * (1.0 - body) * Planet.w * 0.35;
        float star = step(0.9975, hash13(floor(rd * 420.0))) * (1.0 - body) * smoothstep(0.08, 0.4, up);
        col += vec3(0.5, 0.52, 0.6) * star * 0.45;
    }
    // The skyline all round: blocks and Gothic towers with spires, lit windows here and there, red lamps on the tallest.
    float az = atan(rd.x, rd.z);
    float cellAz = floor(az * 58.0);
    float fa = fract(az * 58.0);
    float tall = pow(hash13(vec3(cellAz, 2.0, 5.0)), 2.2);
    float hgt = 0.008 + 0.075 * tall * (0.45 + 0.8 * fbm3(vec3(az * 1.3, 1.0, 3.0)));
    float spire = step(0.8, hash13(vec3(cellAz, 4.0, 1.0))) * max(0.0, 1.0 - abs(fa - 0.5) * 5.0) * 0.035 * tall;
    float steps = step(0.5, hash13(vec3(cellAz, 6.0, 2.0))) * step(abs(fa - 0.5), 0.22) * 0.012;
    float top = max(0.006 + 0.012 * fbm3(vec3(az * 9.0, 2.0, 2.0)), hgt + spire + steps);
    if (up < top && up > -0.03) {
        col = vec3(0.012, 0.012, 0.018);
        vec2 g = vec2(az * 560.0, up * 460.0);
        vec2 cell = floor(g);
        float hw = hash13(vec3(cell, 1.0));
        vec2 f = abs(fract(g) - 0.5);
        float win = step(f.x, 0.28) * step(f.y, 0.24);
        if (hw > 0.9) col += mix(vec3(1.0, 0.74, 0.42), vec3(0.72, 0.8, 1.0), step(0.97, hw)) * win * (0.16 + 0.22 * hash13(vec3(cell, 7.0)));
        float beacon = smoothstep(0.004, 0.0, abs(up - top + 0.003)) * smoothstep(0.06, 0.0, abs(fa - 0.5)) * step(0.045, hgt);
        col += vec3(1.0, 0.08, 0.06) * beacon * (0.5 + 0.5 * sin(Time * 2.5 + cellAz));
    }
    col += vec3(0.09, 0.06, 0.06) * exp(-abs(up) * 18.0) * 0.5;
    return col;
}
// The city seen from above: the grid of streets lined with sodium lamps, black roofs, sparse lit windows and skylights.
vec3 gothamGround(vec3 ro, vec3 rd) {
    float t = -ro.y / rd.y;
    vec3 p = ro + rd * t;
    vec2 q = p.xz + vec2(11.0, 7.0);
    float block = 24.0, street = 4.5;
    vec2 cell = floor(q / block);
    vec2 f = q - cell * block;
    float sx = step(f.x, street), sz = step(f.y, street);
    float inStreet = max(sx, sz);
    float detail = exp(-t * 0.0035);
    // From down in the streets only the far lamps show through the haze; the roofs' lights belong to the view from above.
    float aloft = smoothstep(12.0, 40.0, ro.y);
    // Roofs: each block cut into buildings of different heights (shades), a few lit windows and skylights.
    vec2 sub = floor((f - street) / 5.5);
    float hs = hash13(vec3(cell * 7.0 + sub, 5.0));
    vec3 roof = vec3(0.02, 0.02, 0.026) * (0.5 + 0.9 * hs);
    vec2 g = floor(q * 1.4);
    float w = hash13(vec3(g, 9.0));
    roof += vec3(1.0, 0.72, 0.42) * step(0.982, w) * 0.5 * detail * aloft;
    roof += vec3(1.0, 0.1, 0.06) * step(0.9985, w) * 0.8 * detail * aloft;
    // Streets: wet black asphalt, a lamp every 8 along both kerbs, their pools of orange light.
    float along = sx > 0.5 ? q.y : q.x;
    float across = sx > 0.5 ? f.x : f.y;
    float dl = abs(fract(along / 8.0) - 0.5) * 8.0;
    float dk = min(abs(across - 0.6), abs(across - street + 0.6));
    float pool = exp(-(dl * dl + dk * dk) * 0.6);
    vec3 lamp = vec3(1.0, 0.56, 0.22);
    vec3 road = vec3(0.012, 0.011, 0.014) + lamp * mix(pool * 0.55, 0.07, 1.0 - detail);
    // Cars now and then: a pair of white or red points crawling along.
    float lane = floor(across * 0.5);
    float car = step(0.93, hash13(vec3(floor(along / 3.0 + Time * (lane > 0.5 ? 1.2 : -1.2)), cell.x + cell.y * 13.0, lane)));
    road += mix(vec3(1.0, 0.9, 0.75), vec3(1.0, 0.1, 0.05), lane) * car * 0.25 * detail * aloft * smoothstep(0.6, 0.2, abs(fract(across * 0.5) - 0.5));
    vec3 col = mix(roof, road, inStreet);
    // Haze over the distance, lit by the city.
    float haze = 1.0 - exp(-t * mix(0.02, 0.006, aloft));
    return mix(col, vec3(0.055, 0.042, 0.05), haze);
}

void main() {
    vec3 rd = normalize(CamF + ndc.x * Lens.x * Lens.y * CamR + ndc.y * Lens.x * CamU);
    vec3 ro = CamPos;
    int scene = int(Scene + 0.5);
    vec3 col = space(rd);

    if (scene == 0 || scene == 3) {
        // Hellfire light from below, clouds rushing past the camera.
        col = mix(vec3(0.04, 0.0, 0.0), vec3(0.42, 0.07, 0.0), smoothstep(-0.6, 0.9, -rd.y));
        float acc = 0.0;
        vec3 light = vec3(0.0);
        for (int i = 0; i < 22; i++) {
            float fi = float(i);
            float tt = 1.0 + fi * fi * 0.28;
            vec3 p = ro + rd * tt;
            float d = cloudDensity(p);
            if (d > 0.01) {
                float a = d * (1.0 - acc) * 0.45;
                // Lit from the fire below: less cloud underneath means a brighter underside.
                float below = cloudDensity(p + vec3(0.0, -2.5, 0.0));
                float lightAmt = exp(-below * 2.2);
                float edge = smoothstep(0.35, 0.0, d);
                float under = smoothstep(-0.3, 1.0, -rd.y);
                vec3 lit = fireRamp(clamp(0.12 + 0.7 * lightAmt + 0.25 * edge + 0.15 * under, 0.0, 1.0)) * (0.5 + 1.1 * lightAmt + 0.4 * under);
                light += lit * a;
                acc += a;
            }
            if (acc > 0.97) break;
        }
        col = col * (1.0 - acc) + light;
    }
    if (scene == 1 || scene == 2) {
        float tp;
        bool hit = sphereHit(ro, rd, Planet, tp);
        vec3 oc = Planet.xyz - ro;
        float along = dot(oc, rd);
        if (along > 0.0) {
            float dist = length(oc - rd * along);
            float rim = max(0.0, dist - Planet.w) / Planet.w;
            col += vec3(1.0, 0.36, 0.07) * exp(-rim * 6.0) * 0.95;
        }
        vec3 nrm = normalize(vec3(0.0, 1.0, 0.32));
        float denom = dot(rd, nrm);
        float ringA = 0.0;
        vec3 ringC = vec3(0.0);
        if (abs(denom) > 0.0001) {
            float tr = dot(Planet.xyz - ro, nrm) / denom;
            if (tr > 0.0) {
                vec3 hp = ro + rd * tr;
                float rr = length(hp - Planet.xyz) / Planet.w;
                // A thin bright band with a soft halo and a highlight sweeping round it.
                float mid = 0.5 * (Ring.x + Ring.y);
                float halfWidth = 0.5 * (Ring.y - Ring.x);
                float dr = abs(rr - mid);
                vec3 rel = hp - Planet.xyz;
                float ang = atan(rel.z, rel.x);
                float sweep = pow(0.5 + 0.5 * sin(ang - Time * Ring.z), 6.0);
                float core = smoothstep(halfWidth, halfWidth * 0.35, dr) * (0.85 + 0.15 * sin(rr * 260.0));
                float halo = exp(-max(0.0, dr - halfWidth * 0.5) * 22.0) * 0.55;
                ringA = Ring.w * clamp(core + halo, 0.0, 1.0);
                ringC = mix(vec3(1.0, 0.62, 0.18), vec3(1.0, 0.95, 0.75), core) * (1.2 + 1.8 * sweep);
                if (hit && tp < tr) ringA = 0.0;
            }
        }
        if (hit) col = starSurface(normalize(ro + rd * tp - Planet.xyz), rd);
        col = mix(col, ringC, clamp(ringA, 0.0, 1.0));
    }
    if (scene == 4) {
        // Hell: a low ceiling of fire-lit cloud, a red glow on the horizon and a dark plain
        // split by glowing lava cracks running out into the haze.
        float up = rd.y;
        col = mix(vec3(0.34, 0.05, 0.0), vec3(0.04, 0.0, 0.0), smoothstep(0.0, 0.45, up));
        col = mix(col, vec3(0.08, 0.01, 0.0), smoothstep(0.0, -0.3, up));
        if (up > 0.01) {
            float t = 34.0 / up;
            vec3 p = ro + rd * t;
            vec3 q = vec3(p.x * 0.018, Time * 0.03, p.z * 0.018 + Time * 0.05);
            float n = fbm(q);
            float n2 = fbm(q * 3.0 + n);
            float cover = smoothstep(0.32, 0.72, n + 0.3 * n2);
            vec3 cloud = mix(vec3(0.06, 0.01, 0.0), vec3(0.95, 0.3, 0.04), pow(1.0 - n2, 2.5) * 0.7 + 0.15);
            col = mix(col, cloud, cover * smoothstep(0.01, 0.12, up) * exp(-t * 0.002));
        }
        if (up < -0.001) {
            float t = -(ro.y + 0.02) / up;
            vec3 p = ro + rd * t;
            float lines = abs(fbm(vec3(p.x * 0.22, p.z * 0.22, 1.7)) - 0.5);
            float fine = abs(fbm(vec3(p.x * 0.9, p.z * 0.9, 4.1)) - 0.5);
            float crack = smoothstep(0.035, 0.0, lines) + 0.5 * smoothstep(0.02, 0.0, fine);
            float glowLine = smoothstep(0.07, 0.0, lines) * 0.6;
            float pulse = 0.75 + 0.25 * sin(Time * 2.0 + p.x * 0.2 + p.z * 0.15);
            vec3 ground = vec3(0.035, 0.012, 0.008) + vec3(1.0, 0.33, 0.04) * (crack * 1.6 + glowLine * 0.35) * pulse;
            col = mix(col, ground, exp(-t * 0.012));
        }
    }
    if (scene == 5) {
        // The abyss: near-black red murk, slow smoke, and shafts of fire light falling from above.
        col = vec3(0.012, 0.0, 0.0);
        float fog = fbm(rd * 3.0 + vec3(0.0, Time * 0.06, 0.0));
        float wisps = fbm(rd * 7.0 + fog * 2.0 + vec3(Time * 0.04));
        col += vec3(0.28, 0.035, 0.0) * pow(fog, 2.2) * 1.3 + vec3(0.12, 0.02, 0.0) * pow(wisps, 3.0);
        float shaft = pow(max(0.0, rd.y), 2.5) * (0.5 + 0.5 * fbm(vec3(rd.x * 9.0, rd.z * 9.0, Time * 0.25)));
        col += vec3(0.95, 0.32, 0.05) * shaft * 0.45;
        col += vec3(0.35, 0.05, 0.0) * pow(max(0.0, -rd.y), 2.0) * 0.4;
    }
    if (scene == 6) {
        // A dark arena in fog: wet black stone underfoot, fog banks lit red by the optic glow
        // (Planet: light position, w intensity) and by a beam running from it down +z
        // (Ring: strength, radius, length, white flash). Motion.z is the fog density.
        // Tint.a is the darkness here: it takes away the ambient light, never the red light.
        float dark = Tint.a;
        col = mix(vec3(0.010, 0.011, 0.016), vec3(0.030, 0.031, 0.040), smoothstep(-0.1, 0.5, rd.y)) * (1.0 - dark);
        float tFloor = rd.y < -0.0005 ? -ro.y / rd.y : 100000.0;
        if (tFloor < 10000.0) {
            vec3 p = ro + rd * tFloor;
            vec2 cell = floor(p.xz * 0.5);
            vec2 f = fract(p.xz * 0.5);
            float seam = smoothstep(0.035, 0.0, min(min(f.x, 1.0 - f.x), min(f.y, 1.0 - f.y)));
            float stone = (0.020 + 0.014 * hash13(vec3(cell, 2.0)) + 0.012 * fbm3(vec3(p.xz * 1.3, 0.0))) * (1.0 - 0.85 * dark);
            vec3 ground = vec3(stone) * vec3(0.9, 0.93, 1.0) * (1.0 - 0.55 * seam);
            // Wet sheen: the red light mirrored in the stone, a pool of light under the beam.
            vec3 refl = reflect(rd, vec3(0.0, 1.0, 0.0));
            vec3 toL = Planet.xyz - p;
            float spec = pow(max(0.0, dot(refl, normalize(toL))), 40.0) * Planet.w / (1.0 + dot(toL, toL) * 0.08);
            float pool = Planet.w / (1.0 + dot(toL.xz, toL.xz) * 0.35);
            vec3 nearest = Planet.xyz + vec3(0.0, 0.0, clamp(p.z - Planet.z, 0.0, Ring.z));
            float db = length(p - nearest);
            float beamPool = Ring.x * 2.0 / (1.0 + db * db * 1.5);
            float wet = 0.6 + 0.4 * fbm3(vec3(p.xz * 3.0, 1.0));
            ground += vec3(1.0, 0.08, 0.05) * (spec * 2.0 + pool * 0.08 + beamPool * 0.2) * wet;
            col = mix(col, ground, exp(-tFloor * 0.035));
        }
        // Fog in-scattering, marched to the floor or 40 blocks away.
        float tEnd = min(tFloor, 40.0);
        float acc = 0.0;
        vec3 light = vec3(0.0);
        float last = 0.0;
        for (int i = 0; i < 18; i++) {
            float next = tEnd * pow((float(i) + 1.0) / 18.0, 1.6);
            float tt = 0.5 * (last + next), dt = next - last;
            last = next;
            vec3 p = ro + rd * tt;
            float dens = (0.12 + 1.6 * smoothstep(0.3, 0.8, fbm3(p * 0.22 + vec3(Time * 0.08, 0.0, Time * 0.03)))) * exp(-max(p.y, 0.0) * 0.28) * Motion.z;
            vec3 toL = Planet.xyz - p;
            float pl = Planet.w / (1.0 + dot(toL, toL) * 0.25);
            vec3 q = p - (Planet.xyz + vec3(0.0, 0.0, clamp(p.z - Planet.z, 0.0, Ring.z)));
            float bl = Ring.x * 0.7 / (1.0 + dot(q, q) / (Ring.y * Ring.y + 0.01));
            vec3 lit = vec3(1.0, 0.07, 0.04) * (pl + bl) + vec3(0.075, 0.08, 0.10) * (1.0 - dark);
            float a = 1.0 - exp(-dens * 0.08 * dt);
            light += lit * a * (1.0 - acc);
            acc += a * (1.0 - acc);
        }
        col = col * (1.0 - acc) + light;
        col = mix(col, vec3(1.0, 0.86, 0.82), clamp(Ring.w, 0.0, 1.0));
    }
    if (scene == 7) {
        // Desert at the golden hour with a sandstorm rolling over it
        // (Planet: sun direction, w sun size; Motion.x wind speed, Motion.z storm).
        vec3 sunDir = normalize(Planet.xyz);
        float storm = Motion.z;
        float up = rd.y;
        vec3 zenith = mix(vec3(0.20, 0.32, 0.52), vec3(0.46, 0.33, 0.20), storm);
        vec3 horizon = mix(vec3(0.97, 0.68, 0.40), vec3(0.80, 0.57, 0.32), storm);
        col = mix(horizon, zenith, smoothstep(0.0, 0.55, up));
        float sd = max(0.0, dot(rd, sunDir));
        col += vec3(1.0, 0.72, 0.42) * pow(sd, 10.0) * (0.65 - 0.35 * storm);
        col += vec3(1.0, 0.94, 0.80) * smoothstep(1.0 - 0.0007 * Planet.w, 1.0 - 0.0003 * Planet.w, sd) * (1.0 - 0.75 * storm);
        // Far dunes: a soft ridge line round the horizon, the sun side lit.
        float az = atan(rd.x, rd.z);
        float ridge = 0.012 + 0.05 * fbm3(vec3(az * 2.5, 0.0, 3.0)) + 0.018 * fbm3(vec3(az * 9.0, 1.0, 0.0));
        if (up < ridge) {
            float side = 0.5 + 0.5 * sin(az * 6.0 + fbm3(vec3(az * 4.0, 2.0, 1.0)) * 5.0);
            vec3 far = mix(vec3(0.55, 0.36, 0.22), vec3(0.92, 0.66, 0.40), side * (0.4 + 0.6 * max(0.0, dot(normalize(vec3(rd.x, 0.0, rd.z)), sunDir))));
            col = mix(far, horizon, 0.45 + 0.4 * storm);
        }
        if (up < -0.0005) {
            float t = -ro.y / up;
            vec3 p = ro + rd * t;
            vec2 w = p.xz * 0.045;
            float h = fbm3(vec3(w, 0.0));
            float hx = fbm3(vec3(w + vec2(0.02, 0.0), 0.0)) - h;
            float hz = fbm3(vec3(w + vec2(0.0, 0.02), 0.0)) - h;
            vec3 n = normalize(vec3(-hx * 160.0, 1.0, -hz * 160.0));
            float ripple = sin(p.x * 2.2 + p.z * 0.9 + fbm3(vec3(p.xz * 0.4, 2.0)) * 6.0) * 0.5 + 0.5;
            float lambert = clamp(dot(n, sunDir), 0.0, 1.0);
            vec3 sand = vec3(0.80, 0.60, 0.36) * (0.38 + 0.85 * lambert) * (0.9 + 0.1 * ripple);
            sand = mix(sand, sand * vec3(0.62, 0.56, 0.68), smoothstep(0.35, 0.0, lambert) * 0.5);
            col = mix(col, sand, exp(-t * 0.02));
        }
        if (storm > 0.0) {
            // Veils of blown sand, thickest near the ground, rolling with the wind.
            vec3 q = rd * 4.0 + vec3(-Time * Motion.x, 0.0, Time * Motion.x * 0.3);
            float veil = fbm(q + vec3(0.0, fbm3(q * 2.0), 0.0));
            float low = exp(-max(up, 0.0) * 3.0);
            col = mix(col, vec3(0.76, 0.54, 0.31) * (0.75 + 0.45 * veil), clamp(storm * low * (0.2 + 0.8 * veil), 0.0, 0.9));
            // Grains streaking sideways across the frame.
            float lanes = ndc.y * 140.0;
            float hh = hash13(vec3(floor(lanes), 7.0, 3.0));
            float sx = fract(ndc.x * Lens.y * 0.35 + Time * Motion.x * (0.6 + hh) + hh * 7.0);
            float grain = smoothstep(0.0, 0.015, sx) * smoothstep(0.07, 0.015, sx) * smoothstep(0.32, 0.0, abs(fract(lanes) - 0.5)) * step(0.72, hh);
            col += vec3(1.0, 0.85, 0.6) * grain * storm * 0.35;
        }
    }
    if (scene == 8) {
        // Open plain under a high blue sky with big white clouds (Planet: sun direction;
        // Motion.x cloud drift, Motion.z dust hanging in the air).
        vec3 sunDir = normalize(Planet.xyz);
        float dust = Motion.z;
        float up = rd.y;
        vec3 zenith = vec3(0.10, 0.27, 0.70);
        vec3 horizon = vec3(0.64, 0.77, 0.92);
        col = mix(horizon, zenith, smoothstep(-0.02, 0.5, up));
        float sd = max(0.0, dot(rd, sunDir));
        col += vec3(1.0, 0.95, 0.85) * pow(sd, 24.0) * 0.5;
        if (up > 0.0) {
            // Clouds on a high layer: firm white masses with grey-blue undersides.
            vec2 cp = rd.xz / (up + 0.08) * 1.6 + vec2(Time * Motion.x * 0.02, 0.0);
            float c = fbm3(vec3(cp * 0.55, 1.0));
            float body = smoothstep(0.52, 0.6, c);
            float lit = smoothstep(0.5, 0.72, fbm3(vec3(cp * 0.55 + vec2(0.06, 0.09), 1.0)));
            vec3 cloud = mix(vec3(0.70, 0.77, 0.88), vec3(1.0), lit);
            col = mix(col, cloud, body * smoothstep(0.0, 0.08, up));
        }
        // Far hills, hazy blue, low round the horizon.
        float az = atan(rd.x, rd.z);
        float ridge = 0.008 + 0.02 * fbm3(vec3(az * 3.0, 0.0, 5.0));
        if (up < ridge && up >= -0.0005) col = mix(col, vec3(0.55, 0.62, 0.70), 0.6);
        if (up < -0.0005) {
            // Pale sandy ground with darker rocky patches, fading into the haze.
            float t = -ro.y / up;
            vec3 p = ro + rd * t;
            float big = fbm3(vec3(p.xz * 0.03, 4.0));
            float small = fbm3(vec3(p.xz * 0.35, 7.0));
            vec3 sand = mix(vec3(0.80, 0.74, 0.60), vec3(0.62, 0.56, 0.46), smoothstep(0.4, 0.7, big));
            sand = mix(sand, vec3(0.45, 0.42, 0.37), smoothstep(0.62, 0.75, small) * 0.6);
            sand *= 0.85 + 0.25 * max(0.0, sunDir.y);
            col = mix(horizon * 0.95, sand, exp(-t * 0.006));
        }
        col = mix(col, vec3(0.86, 0.80, 0.70), clamp(dust, 0.0, 1.0) * (0.25 + 0.6 * exp(-max(up, 0.0) * 4.0)));
    }
    if (scene == 9) {
        // A city at night (The Final Pursuit): a black sky warmed at the horizon by the city's light, low cloud
        // lit from beneath, towers all round with lit windows and red lamps on their tops, hills behind dotted with
        // flats, a long bridge strung with lights far off; the dust of the crash hanging in it (Motion.z).
        float up = rd.y;
        float dust = Motion.z;
        vec3 zenith = vec3(0.010, 0.012, 0.028);
        vec3 glow = vec3(0.13, 0.075, 0.13);
        col = mix(glow, zenith, smoothstep(-0.02, 0.42, up));
        col += vec3(0.06, 0.025, 0.012) * exp(-max(up, 0.0) * 10.0);
        if (up > 0.0) {
            vec2 cp = rd.xz / (up + 0.1) * 1.2 + vec2(Time * 0.004, 0.0);
            float c = fbm3(vec3(cp * 0.7, 2.0));
            float body = smoothstep(0.45, 0.72, c);
            vec3 cloud = vec3(0.12, 0.065, 0.11) * (0.6 + 0.6 * c) * (0.4 + 0.6 * exp(-up * 3.0));
            col = mix(col, cloud, body * smoothstep(0.0, 0.1, up) * 0.85);
        }
        float az = atan(rd.x, rd.z);
        // The hills behind, their slopes full of the small lights of flats.
        float hill = 0.035 + 0.07 * fbm3(vec3(az * 1.4, 0.0, 9.0));
        if (up < hill && up > -0.01) {
            col = mix(col, vec3(0.02, 0.018, 0.03), 0.92);
            vec2 g = vec2(az * 900.0, up * 700.0);
            vec2 cell = floor(g);
            float h = hash13(vec3(cell, 4.0));
            float spot = smoothstep(0.42, 0.0, length(fract(g) - 0.5));
            if (h > 0.82) col += mix(vec3(1.0, 0.75, 0.42), vec3(0.75, 0.85, 1.0), step(0.95, h)) * spot * 0.55 * smoothstep(-0.01, hill, hill - up + 0.01);
        }
        // The towers: a ring of silhouettes, windows lit here and there, red lamps blinking on top.
        float cellAz = floor(az * 46.0);
        float th = 0.012 + 0.085 * hash13(vec3(cellAz, 2.0, 7.0)) * (0.45 + 0.55 * fbm3(vec3(az * 2.0, 3.0, 1.0)));
        if (up < th && up > -0.01) {
            col = vec3(0.016, 0.016, 0.026);
            vec2 g = vec2(az * 520.0, up * 420.0);
            vec2 cell = floor(g);
            float h = hash13(vec3(cell, 1.0));
            vec2 f = abs(fract(g) - 0.5);
            float win = step(f.x, 0.3) * step(f.y, 0.26);
            if (h > 0.62) col += mix(vec3(1.0, 0.78, 0.45), vec3(0.7, 0.82, 1.0), step(0.86, h)) * win * (0.25 + 0.35 * hash13(vec3(cell, 9.0)));
            float top = smoothstep(0.003, 0.0, abs(up - th + 0.002)) * smoothstep(0.004, 0.0, abs(fract(az * 46.0) - 0.5) - 0.01);
            col += vec3(1.0, 0.1, 0.08) * top * step(0.5, hash13(vec3(cellAz, 5.0, 1.0))) * (0.5 + 0.5 * sin(Time * 3.0 + cellAz));
        }
        // The bridge, far off on one side: a long sagging line of lights on the water, its towers marked.
        if (az > 0.9 && az < 2.1) {
            float u = (az - 0.9) / 1.2;
            float sag = 0.022 - 0.012 * sin(3.14159 * fract(u * 3.0));
            float line = smoothstep(0.0015, 0.0, abs(up - sag));
            float beads = smoothstep(0.45, 0.0, abs(fract(az * 260.0) - 0.5));
            vec3 tint = mix(vec3(0.4, 0.8, 1.0), vec3(0.85, 0.5, 1.0), 0.5 + 0.5 * sin(az * 7.0 + Time * 0.4));
            col += tint * line * (0.35 + 0.65 * beads) * 0.9;
            col += tint * smoothstep(0.004, 0.0, abs(up - 0.006)) * 0.2;
        }
        if (up < -0.01) col = mix(glow * 0.7, vec3(0.02, 0.02, 0.03), smoothstep(-0.01, -0.12, up));
        col = mix(col, vec3(0.30, 0.22, 0.17), clamp(dust, 0.0, 1.0) * (0.45 + 0.4 * exp(-abs(up) * 3.0)));
    }
    if (scene == 10) {
        float flash = Planet.w;
        float up = rd.y;
        col = wasteSky(rd, flash);
        if (up < -0.0005) {
            // The dead ground: black, broken, soaked; cracked into slabs, strewn with rubble; puddles everywhere
            // holding the red sky and the flashes, rippled by the rain; it fades into the red haze far off.
            float t = -ro.y / up;
            vec3 p = ro + rd * t;
            float big = fbm3(vec3(p.xz * 0.05, 3.0));
            float small = fbm3(vec3(p.xz * 0.8, 5.0));
            vec3 ground = mix(vec3(0.05, 0.028, 0.024), vec3(0.11, 0.06, 0.045), smoothstep(0.35, 0.75, small));
            ground *= 0.65 + 0.6 * big;
            // Cracks between slabs (a cell pattern) and pale grit.
            vec2 cg = p.xz * 0.45 + vec2(fbm3(vec3(p.xz * 0.2, 1.0)), fbm3(vec3(p.xz * 0.2, 2.0))) * 1.5;
            vec2 ci = floor(cg), cf = fract(cg);
            float edge = min(min(cf.x, 1.0 - cf.x), min(cf.y, 1.0 - cf.y));
            ground *= mix(0.35, 1.0, smoothstep(0.0, 0.05, edge));
            ground *= 0.85 + 0.3 * hash13(vec3(ci, 8.0));
            float grit = step(0.93, hash13(vec3(floor(p.xz * 9.0), 4.0)));
            ground += vec3(0.08, 0.05, 0.045) * grit * exp(-t * 0.08);
            float wet = smoothstep(0.47, 0.53, big + 0.22 * (small - 0.5));
            // Rain rings on the water.
            vec2 cell = floor(p.xz * 1.6);
            float phase = Time * 1.7 + hash13(vec3(cell, 1.0)) * 5.0;
            float rh = hash13(vec3(cell, floor(phase)));
            vec2 rc = (cell + 0.25 + 0.5 * vec2(hash13(vec3(cell, 3.0)), hash13(vec3(cell, 4.0)))) / 1.6;
            float age = fract(phase);
            float rr = length(p.xz - rc) - age * 0.3;
            float ring = smoothstep(0.035, 0.0, abs(rr)) * (1.0 - age) * step(0.4, rh) * Motion.z * exp(-t * 0.05);
            vec3 nrm = normalize(vec3(ring * 0.25 * sign(p.x - rc.x) + (small - 0.5) * 0.05, 1.0, ring * 0.25 * sign(p.z - rc.y) + (big - 0.5) * 0.05));
            vec3 mirror = wasteSky(reflect(rd, nrm), flash * 0.35);
            float fres = 0.25 + 0.75 * pow(1.0 - clamp(-up, 0.0, 1.0), 5.0);
            // Everything is soaked: a sheen on the slabs, a mirror in the puddles.
            vec3 soaked = ground * 0.8 + mirror * fres * 0.22;
            ground = mix(soaked, ground * 0.3 + mirror * (0.3 + 0.7 * fres), wet);
            ground += vec3(0.85, 0.6, 0.65) * flash * (0.04 + 0.2 * wet) * exp(-t * 0.01);
            float haze = exp(-t * 0.011);
            col = mix(wasteSky(vec3(rd.x, 0.0, rd.z), flash) * 0.8, ground, haze);
        }
        // The lightning channel itself, with its halo.
        if (Ring.y > 0.0) {
            float d = boltDist(rd, Planet.xyz, Ring.x);
            col += vec3(1.0, 0.92, 1.0) * smoothstep(0.0025, 0.0, d) * Ring.y * 2.0;
            col += vec3(1.0, 0.45, 0.55) * exp(-d * 60.0) * Ring.y * 0.45;
        }
        // Sheets of rain blown slant across everything.
        if (Motion.z > 0.0) {
            for (int k = 0; k < 2; k++) {
                float fk = float(k);
                vec2 q = vec2(ndc.x * Lens.y + ndc.y * (0.18 + 0.05 * fk), ndc.y);
                float lanes = q.x * (95.0 + 70.0 * fk);
                float hh = hash13(vec3(floor(lanes), 3.0 + fk, 1.0));
                float y = fract(q.y * (0.45 + 0.2 * fk) + Time * (1.6 + 0.9 * fk) * (0.8 + 0.4 * hh) + hh * 9.0);
                float drop = smoothstep(0.0, 0.02, y) * smoothstep(0.22, 0.05, y) * smoothstep(0.35, 0.0, abs(fract(lanes) - 0.5)) * step(0.55, hh);
                col += mix(vec3(0.5, 0.18, 0.16), vec3(0.9, 0.85, 0.95), flash) * drop * Motion.z * (0.16 - 0.05 * fk);
            }
            col = mix(col, vec3(0.22, 0.04, 0.035) + flash * 0.2, Motion.z * 0.12);
        }
        col += vec3(0.55, 0.4, 0.5) * flash * 0.12;
    }
    if (scene == 11) {
        // Gotham at night: the sky, and below the horizon the city under the camera (seen from the yard it is
        // hidden by the stage's own buildings; from high up it spreads out underneath).
        float up = rd.y;
        col = gothamSky(rd);
        if (up < -0.0005 && ro.y > 0.2) {
            vec3 ground = gothamGround(ro, rd);
            float fade = smoothstep(-0.0005, -0.02, up);
            col = mix(col, ground, fade);
        }
        // Rain blown slant across everything, lit faintly by the city.
        if (Motion.z > 0.0) {
            for (int k = 0; k < 2; k++) {
                float fk = float(k);
                vec2 q = vec2(ndc.x * Lens.y + ndc.y * (0.12 + 0.05 * fk), ndc.y);
                float lanes = q.x * (110.0 + 70.0 * fk);
                float hh = hash13(vec3(floor(lanes), 5.0 + fk, 2.0));
                float y = fract(q.y * (0.45 + 0.2 * fk) + Time * (1.7 + 0.9 * fk) * (0.8 + 0.4 * hh) + hh * 9.0);
                float drop = smoothstep(0.0, 0.02, y) * smoothstep(0.22, 0.05, y) * smoothstep(0.35, 0.0, abs(fract(lanes) - 0.5)) * step(0.6, hh);
                col += vec3(0.3, 0.32, 0.38) * drop * Motion.z * (0.12 - 0.04 * fk);
            }
        }
        col += vec3(0.55, 0.65, 0.9) * Ring.x * 0.35;
    }
    if (Motion.y > 0.0) col = warpStreaks(col);
    if (scene != 6) col = mix(col, Tint.rgb, Tint.a);
    if (Motion.w > 0.0) {
        float l = dot(col, vec3(0.3, 0.59, 0.11));
        col = mix(col, vec3(0.1, 0.5, 0.82) * l * 2.6 + vec3(0.0, 0.02, 0.05), Motion.w);
    }
    col = col / (1.0 + col * 0.25);
    fragColor = vec4(col, 1.0);
}
