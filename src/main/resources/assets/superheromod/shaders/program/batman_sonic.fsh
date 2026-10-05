#version 150

// The sonic trap on the one it hits (BatmanSonicFx): the picture judders with the pressure. A ripple runs out from the
// middle of the view with every pulse (Wave 0..1 = how far it has gone), the image wobbles side to side at a low
// frequency, the colours split apart (red one way, blue-cyan the other, more toward the edges and toward the side the
// blast came from, Side -1..1), the edges darken with a cold tint, and each hit flashes a little white-cyan (Flash).
// Amount 0..1 scales everything; at 0 the picture is untouched. Never blinding.

uniform sampler2D DiffuseSampler;

in vec2 texCoord;
in vec2 oneTexel;

uniform vec2 InSize;
uniform float Amount;
uniform float Wave;
uniform float Flash;
uniform float Time;
uniform float Side;

out vec4 fragColor;

void main() {
    vec2 uv = texCoord;
    vec2 aspect = vec2(InSize.x / max(InSize.y, 1.0), 1.0);
    vec2 d = (uv - 0.5) * aspect;
    float r = length(d);
    vec2 dir = r > 1e-4 ? d / r : vec2(0.0);

    // The pressure ring running out from the middle.
    float front = Wave * 1.25;
    float band = exp(-abs(r - front) * 9.0) * (1.0 - Wave);
    float ripple = sin((r - front) * 46.0) * band;
    uv += dir / aspect * ripple * 0.016 * Amount;

    // The low judder: the whole image swaying side to side, a little up and down.
    uv.x += sin(Time * 2.3 + uv.y * 5.0) * 0.0035 * Amount;
    uv.y += sin(Time * 1.7 + uv.x * 4.0) * 0.0012 * Amount;

    // Colours split, more toward the edges and the side of the blast.
    vec2 split = (dir / aspect * (0.35 + r) + vec2(Side * 0.5, 0.0)) * 0.006 * Amount + vec2(band * 0.004 * Amount, 0.0);
    float red = texture(DiffuseSampler, clamp(uv + split, 0.0, 1.0)).r;
    vec2 gb = texture(DiffuseSampler, clamp(uv, 0.0, 1.0)).gb;
    float blue = texture(DiffuseSampler, clamp(uv - split, 0.0, 1.0)).b;
    vec3 color = vec3(red, gb.x, mix(gb.y, blue, 0.85));

    // Darkened edges with a cold tint; a short white-cyan flash.
    float vig = smoothstep(0.35, 0.95, r) * 0.42 * Amount;
    color = mix(color, color * vec3(0.55, 0.62, 0.72), vig);
    color += vec3(0.85, 0.95, 1.0) * Flash * 0.18 * (0.6 + 0.4 * band);

    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
