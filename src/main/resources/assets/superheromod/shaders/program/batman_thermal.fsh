#version 150

// Batman's thermal / detective vision (Arkham reference). The world is turned into a cold, dark, low-contrast image
// (a lens, not a red filter); living bodies come from the heat layer (drawn by BatmanThermal with a body-heat gradient,
// brighter where in sight, dimmer through walls) and glow on an iron thermal palette with bloom; hot spots of the world
// (fire, lava, torches) are read off the image itself. Edge = the lens vignette coming in, Distort = the forensic
// switch-over glitch, Pulse = the brightness pulse of the switch, Motion = how fast the view turns (a little smear).

uniform sampler2D DiffuseSampler;
uniform sampler2D HeatSampler;
uniform sampler2D BloomSampler;

in vec2 texCoord;
in vec2 oneTexel;

uniform vec2 InSize;
uniform float Amount;
uniform float Edge;
uniform float Distort;
uniform float Pulse;
uniform float Time;
uniform float Motion;

out vec4 fragColor;

float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }

// Iron palette: cold violet through red and orange to a yellow-white core.
vec3 iron(float t) {
    t = clamp(t, 0.0, 1.0);
    vec3 c0 = vec3(0.06, 0.02, 0.16), c1 = vec3(0.36, 0.03, 0.52), c2 = vec3(0.86, 0.10, 0.24),
         c3 = vec3(1.00, 0.44, 0.02), c4 = vec3(1.00, 0.82, 0.22), c5 = vec3(1.00, 0.98, 0.88);
    if (t < 0.2) return mix(c0, c1, t / 0.2);
    if (t < 0.45) return mix(c1, c2, (t - 0.2) / 0.25);
    if (t < 0.65) return mix(c2, c3, (t - 0.45) / 0.2);
    if (t < 0.85) return mix(c3, c4, (t - 0.65) / 0.2);
    return mix(c4, c5, (t - 0.85) / 0.15);
}

void main() {
    vec2 uv = texCoord;
    // The switch-over: bands of the image slipping sideways, a fine wobble.
    float row = floor(uv.y * 48.0), tick = floor(Time * 18.0);
    float band = step(0.9, hash(vec2(row, tick)));
    uv.x += (band * (hash(vec2(tick, row)) - 0.5) * 0.05 + sin(uv.y * 90.0 + Time * 31.0) * 0.0016) * Distort;
    uv.x += sin(uv.y * 14.0 + Time * 3.0) * 0.0012 * Motion * Amount;
    float ca = 0.005 * Distort + 0.0009 * Amount + 0.002 * Motion * Amount;
    vec3 base = vec3(texture(DiffuseSampler, uv + vec2(ca, 0.0)).r, texture(DiffuseSampler, uv).g, texture(DiffuseSampler, uv - vec2(ca, 0.0)).b);
    float lum = dot(base, vec3(0.299, 0.587, 0.114));

    // Hot spots of the world itself: bright, warm, saturated (fire, lava, torches, lit furnaces).
    float worldHeat = smoothstep(0.55, 0.92, base.r) * smoothstep(0.22, 0.55, base.r - base.b) * smoothstep(0.3, 0.58, lum);

    // The cold world: dark slate and navy, low contrast; plants and stone sink into it.
    float l = pow(clamp(lum, 0.0, 1.0), 0.85);
    vec3 cold = mix(vec3(0.02, 0.035, 0.07), vec3(0.25, 0.35, 0.45), l * 0.9);
    cold += vec3(0.0, 0.015, 0.035) * (0.5 + 0.5 * sin(uv.y * 3.0 + Time * 0.4));

    float heat = texture(HeatSampler, uv).r;
    float bloom = texture(BloomSampler, uv).r;
    float h = max(heat, worldHeat * 0.9);

    vec3 col = mix(base, cold, Amount * 0.92);
    col = mix(col, iron(h * 0.95 + 0.05), smoothstep(0.03, 0.2, h) * Amount);
    col += iron(min(1.0, bloom * 1.3 + 0.1)) * bloom * 0.75 * Amount;

    // The lens: faint scanlines and grain (more while switching), a soft pulse of light at the switch.
    col *= 1.0 - Amount * (0.035 + 0.08 * Distort) * (0.5 + 0.5 * sin(uv.y * InSize.y * 1.4 + Time * 2.0));
    col += (hash(uv * InSize + Time * 61.0) - 0.5) * (0.03 * Amount + 0.09 * Distort);
    col += vec3(0.22, 0.28, 0.36) * Pulse;

    // Edges darker (black / dark violet / dark grey), the middle stays readable.
    vec2 d = texCoord - 0.5;
    float v = smoothstep(0.3, 0.86, length(d * vec2(1.0, 0.82)));
    col = mix(col, vec3(0.03, 0.005, 0.055), v * 0.55 * Edge);
    // While coming in, the normal colours fade first.
    col = mix(col, vec3(dot(col, vec3(0.333))), 0.35 * Edge * (1.0 - Amount));

    fragColor = vec4(col, 1.0);
}
