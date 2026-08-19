#version 150

// SINEMATIK RENK KATMANI
//
// Sadece sinematik oynarken devrede; oynanista hic calismaz. Bu yuzden
// biraz pahali olmasi sorun degil — kisa ve nadir bir an icin harcaniyor.
//
// Yaptigi is:
//   1) Onceden bulaniklastirilmis kopyayi (GlowSampler) parlak yerlerden
//      geri ekler -> BLOOM. Referans gorsellerdeki gunes/sis parlamasi bu.
//   2) Doygunlugu dusurur -> arka plan geri ceker, film tonu verir
//   3) Kontrasti hafif artirir -> gorseli "cekilmis" gosterir
//   4) Sicak/soguk ton uygular -> sahnenin duygusu

uniform sampler2D DiffuseSampler;
uniform sampler2D GlowSampler;

uniform float GlowStrength;
uniform float Saturation;
uniform float Contrast;
uniform vec3 Tint;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec3 base = texture(DiffuseSampler, texCoord).rgb;
    vec3 blurred = texture(GlowSampler, texCoord).rgb;

    // Bloom: bulanik kopyanin sadece PARLAK kismini geri ekle.
    // Tamamini eklemek goruntuyu sisli degil pis gosterir.
    float brightness = dot(blurred, vec3(0.2126, 0.7152, 0.0722));
    float threshold = smoothstep(0.55, 1.0, brightness);
    vec3 glow = blurred * threshold * GlowStrength;

    vec3 color = base + glow;

    // Doygunluk
    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    color = mix(vec3(luma), color, Saturation);

    // Kontrast (orta gri etrafinda)
    color = (color - 0.5) * Contrast + 0.5;

    // Ton
    color *= Tint;

    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
