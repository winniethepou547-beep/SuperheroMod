"""
The mod's own sound effects, synthesised from scratch (no recordings, nothing taken from games or videos), written as
mono Ogg Vorbis into src/main/resources/assets/superheromod/sounds/<group>/<name>.ogg, plus sounds.json.

    python3 tools/sounds/synth.py            # every sound
    python3 tools/sounds/synth.py magneto    # one group

Building blocks: noise through swept filters (air, whooshes, fire, sand), modal synthesis (metal: inharmonic partials
that ring and decay at their own rates), pitch-dropping sine thumps (body blows, booms), impulse crackle through
resonators (electricity), FM tones (magnetism, energy), a small synthetic room/space reverb, and soft limiting.
Every sound ends in silence and peaks at -1 dBFS. Needs numpy, scipy and ffmpeg (libvorbis).
"""
import json
import os
import subprocess
import sys
import tempfile
import wave

import numpy as np
from scipy import signal

SR = 44100
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
OUT = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'superheromod', 'sounds')
RNG = np.random.default_rng(7)


# ---------------------------------------------------------------- basics
def t_axis(dur):
    return np.arange(int(dur * SR)) / SR


def noise(dur, seed=None):
    r = np.random.default_rng(seed) if seed is not None else RNG
    return r.standard_normal(int(dur * SR))


def env_ad(dur, attack, decay_tau, hold=0.0):
    t = t_axis(dur)
    e = np.where(t < attack, t / max(attack, 1e-5), np.exp(-np.maximum(0, t - attack - hold) / decay_tau))
    e[(t >= attack) & (t < attack + hold)] = 1
    return e


def fade(x, out=0.02, inn=0.002):
    n_out, n_in = int(out * SR), int(inn * SR)
    if n_out > 0:
        x[-n_out:] *= np.linspace(1, 0, n_out) ** 2
    if n_in > 0:
        x[:n_in] *= np.linspace(0, 1, n_in)
    return x


def pad(x, dur):
    n = int(dur * SR)
    return x[:n] if len(x) >= n else np.concatenate([x, np.zeros(n - len(x))])


def mix(dur, *layers):
    out = np.zeros(int(dur * SR))
    for layer, offset, gain in layers:
        start = int(offset * SR)
        if start >= len(out):
            continue
        seg = layer[:len(out) - start]
        out[start:start + len(seg)] += seg * gain
    return out


def bandpass(x, lo, hi, order=2):
    sos = signal.butter(order, [max(20, lo), min(SR / 2 - 100, hi)], btype='band', fs=SR, output='sos')
    return signal.sosfilt(sos, x)


def lowpass(x, f, order=2):
    sos = signal.butter(order, min(SR / 2 - 100, f), btype='low', fs=SR, output='sos')
    return signal.sosfilt(sos, x)


def highpass(x, f, order=2):
    sos = signal.butter(order, max(20, f), btype='high', fs=SR, output='sos')
    return signal.sosfilt(sos, x)


def swept(x, f_start, f_end, q=2.5, steps=64, curve=1.0):
    """A band-pass whose centre moves from f_start to f_end through the sound (block-wise, crossfaded)."""
    n = len(x)
    out = np.zeros(n)
    block = max(256, n // steps)
    win = np.hanning(block * 2)
    for i, s in enumerate(range(0, n, block)):
        k = (s / max(1, n - 1)) ** curve
        f = f_start * (f_end / f_start) ** k
        lo, hi = f / (1 + 1 / q), f * (1 + 1 / q)
        a, b = max(0, s - block), min(n, s + block)
        seg = bandpass(x[a:b], lo, hi)
        w = win[:b - a] if a == 0 or b - a == block * 2 else np.hanning(b - a)
        out[a:b] += seg * w
    return out


def resonator(x, f, q):
    b, a = signal.iirpeak(min(f, SR / 2 - 200), q, fs=SR)
    return signal.lfilter(b, a, x)


def sine_sweep(dur, f0, f1, curve=1.0, phase=0.0):
    t = t_axis(dur)
    k = (t / dur) ** curve
    f = f0 * (f1 / f0) ** k
    return np.sin(2 * np.pi * np.cumsum(f) / SR + phase)


def modal(dur, base, ratios, decays, gains, strike=0.002, seed=0):
    """Metal: partials at base*ratio, each ringing with its own decay, a little detuned pair per mode (beating)."""
    t = t_axis(dur)
    r = np.random.default_rng(seed)
    out = np.zeros(len(t))
    for ratio, tau, g in zip(ratios, decays, gains):
        f = base * ratio
        if f > SR / 2 - 500:
            continue
        det = 1 + r.uniform(0.001, 0.004)
        ph = r.uniform(0, 2 * np.pi)
        out += g * np.exp(-t / tau) * (np.sin(2 * np.pi * f * t + ph) + 0.6 * np.sin(2 * np.pi * f * det * t + ph * 1.3))
    out *= np.minimum(1, t / strike)
    return out


def crackle(dur, density, lo=1500, hi=9000, seed=1, decay=None):
    """Electric crackle: random sharp impulses (clustered) through a bright band, with a little buzz."""
    r = np.random.default_rng(seed)
    n = int(dur * SR)
    x = np.zeros(n)
    t = 0
    while t < n:
        burst = r.integers(1, 6)
        for _ in range(burst):
            if t >= n:
                break
            x[t] = r.uniform(0.4, 1) * r.choice([-1, 1])
            t += r.integers(40, 300)
        t += int(SR / density * r.exponential(1))
    x = bandpass(x, lo, hi, 2) * 6
    if decay:
        x *= np.exp(-t_axis(dur) / decay)
    return x


def fm(dur, carrier, mod_ratio, index, f_drift=1.0):
    t = t_axis(dur)
    drift = np.linspace(1, f_drift, len(t))
    return np.sin(2 * np.pi * carrier * drift * t + index * np.sin(2 * np.pi * carrier * mod_ratio * drift * t))


def thump(dur, f0, f1, tau, curve=0.35):
    """A body blow / boom: a sine falling fast in pitch, its decay."""
    return sine_sweep(dur, f0, f1, curve) * env_ad(dur, 0.002, tau)


def reverb(x, size=0.6, decay=1.2, wet=0.25, bright=4000, seed=3):
    """A synthetic space: exponentially decaying filtered noise as the impulse response."""
    r = np.random.default_rng(seed)
    n = int(decay * SR)
    ir = r.standard_normal(n) * np.exp(-np.arange(n) / (SR * decay / 6.9))
    ir = lowpass(ir, bright)
    ir[:int(0.01 * SR * size)] = 0
    ir /= np.sqrt(np.sum(ir ** 2)) + 1e-9
    y = pad(signal.fftconvolve(x, ir), (len(x) + n) / SR)
    out = pad(np.concatenate([x, np.zeros(n)]), (len(x) + n) / SR) * (1 - wet) + y * wet
    return out


def soft(x, drive=1.5):
    return np.tanh(x * drive) / np.tanh(drive)


def normalise(x, peak_db=-1.0):
    x = x - np.mean(x)
    m = np.max(np.abs(x)) + 1e-9
    return x / m * (10 ** (peak_db / 20))


def trim(x, threshold=1e-3):
    idx = np.where(np.abs(x) > threshold)[0]
    if len(idx) == 0:
        return x
    end = min(len(x), idx[-1] + int(0.03 * SR))
    return x[:end]


# ---------------------------------------------------------------- building blocks for many sounds
def whoosh(dur, f0, f1, q=1.8, shape=0.45, seed=None, body=0.0):
    x = noise(dur, seed)
    x = swept(x, f0, f1, q)
    t = t_axis(dur)
    peak = shape * dur
    e = np.where(t < peak, (t / peak) ** 2, np.exp(-(t - peak) / (dur * 0.18)))
    out = x * e
    if body:
        out += lowpass(noise(dur, None if seed is None else seed + 1), 300) * e * body
    return out


def clink(base, seed, dur=0.35):
    return modal(dur, base, [1, 2.76, 5.4, 8.93], [0.09, 0.05, 0.03, 0.02], [1, .6, .4, .25], seed=seed)


def debris(dur, count, seed, lo=1200, hi=7000, start=0.05):
    """Little pieces landing: a scatter of clinks/ticks over time, getting sparser."""
    r = np.random.default_rng(seed)
    out = np.zeros(int(dur * SR))
    for i in range(count):
        at = start + (dur - start - 0.1) * (r.random() ** 1.8)
        c = clink(r.uniform(lo, hi) / 4, int(r.integers(0, 1e6)), 0.25) * r.uniform(0.15, 0.6)
        s = int(at * SR)
        seg = c[:len(out) - s]
        out[s:s + len(seg)] += seg
    return out


def rumble(dur, tau, cutoff=160, seed=None):
    return lowpass(noise(dur, seed), cutoff, 4) * env_ad(dur, 0.01, tau) * 3


def crack(dur=0.08, seed=None, hi=1800):
    return highpass(noise(dur, seed), hi) * env_ad(dur, 0.0005, 0.012)


# ---------------------------------------------------------------- the sounds
def s_whoosh_light():
    return whoosh(0.4, 500, 2600, 2.2, 0.55, seed=11)


def s_whoosh_heavy():
    return whoosh(0.7, 180, 1200, 1.6, 0.5, seed=12, body=0.6)


def s_impact_heavy():
    d = 1.4
    return mix(d, (thump(d, 95, 38, 0.32), 0, 1.2), (crack(0.1, 13, 1200), 0, 0.8), (rumble(d, 0.45, 140, 14), 0.01, 0.9),
               (debris(d, 10, 15, 600, 3000, 0.15), 0, 0.35))


def s_impact_metal():
    d = 1.2
    m = modal(d, 220, [1, 2.32, 4.25, 6.63, 9.38, 12.1], [.5, .35, .22, .14, .1, .07], [1, .7, .5, .35, .25, .15], seed=21)
    return mix(d, (m, 0, .9), (crack(0.06, 22, 2500), 0, 0.6), (thump(d, 140, 60, 0.12), 0, 0.5))


def s_electric_zap():
    d = 0.45
    buzz = signal.sawtooth(2 * np.pi * 120 * t_axis(d)) * (noise(d, 31) > 0.6)
    buzz = bandpass(buzz, 400, 4000) * env_ad(d, 0.002, 0.08)
    return mix(d, (crackle(d, 160, seed=32, decay=0.12), 0, 1), (buzz, 0, .35), (crack(0.04, 33, 3000), 0, 0.7))


def s_electric_crackle():
    d = 0.9
    c = crackle(d, 60, 1800, 10000, seed=34)
    e = 0.5 + 0.5 * np.sin(2 * np.pi * 3.1 * t_axis(d)) ** 2
    return c * e * env_ad(d, 0.05, 0.5)


def s_energy_swell():
    d = 1.1
    t = t_axis(d)
    chord = sum(signal.sawtooth(2 * np.pi * f * (1 + 0.6 * (t / d) ** 2) * t) for f in (110, 165, 220.5))
    chord = swept(chord, 300, 3000, 1.5) * (t / d) ** 1.5
    shimmer = fm(d, 1800, 1.41, 2.5, 1.6) * (t / d) ** 3 * 0.25
    return chord * 0.6 + shimmer


def s_energy_boom():
    d = 2.0
    t = t_axis(d)
    tone = sine_sweep(d, 420, 60, 0.4) * env_ad(d, 0.003, 0.5) * 0.6
    burst = swept(noise(d, 41), 6000, 300, 1.2, curve=0.5) * env_ad(d, 0.002, 0.35)
    return reverb(mix(d, (thump(d, 80, 30, 0.6), 0, 1.3), (tone, 0, 1), (burst, 0, .9), (crack(0.1, 42, 1500), 0, .7)), 0.8, 1.6, 0.3)


# Magneto
def s_magnetic_hum():
    d = 1.6
    t = t_axis(d)
    hum = np.sin(2 * np.pi * 55 * t) + 0.6 * np.sin(2 * np.pi * 110.7 * t) + 0.3 * np.sin(2 * np.pi * 166 * t)
    wob = 1 + 0.25 * np.sin(2 * np.pi * 4.3 * t)
    grit = bandpass(noise(d, 51), 200, 1200) * 0.15
    return (hum * wob * 0.5 + grit + fm(d, 880, 0.5, 1.5) * 0.06) * env_ad(d, 0.25, 0.9, 0.4)


def s_metal_rise():
    d = 1.0
    scrape = swept(noise(d, 52), 600, 4200, 6, curve=1.4) * env_ad(d, 0.15, 0.45, 0.2)
    ring = modal(d, 330, [1, 2.4, 3.9, 5.6], [.4, .3, .2, .15], [.5, .35, .25, .2], seed=53) * np.linspace(0.3, 1, int(d * SR))
    return mix(d, (scrape, 0, 1), (ring, 0.05, .5), (debris(d, 7, 54, 1500, 6000, 0.05), 0, .5))


def s_metal_clang_big():
    d = 1.8
    m = modal(d, 125, [1, 2.1, 3.37, 4.9, 6.8, 9.2, 11.7, 15.3], [1.1, .8, .6, .45, .3, .2, .15, .1], [1, .9, .8, .6, .5, .4, .3, .2], seed=55)
    ring = modal(d, 610, [1, 2.76, 5.4], [.6, .35, .2], [1, .5, .3], seed=60)
    return reverb(mix(d, (m, 0, 1), (ring, 0, .35), (thump(d, 110, 40, 0.3), 0, 1.0), (crack(0.1, 56, 1500), 0, .8), (rumble(d, 0.5, 120, 57), 0, .5)), 0.7, 1.4, 0.25)


def s_metal_shing():
    d = 0.5
    m = modal(d, 1480, [1, 1.53, 2.47, 3.6], [.25, .18, .1, .07], [1, .6, .4, .3], seed=58)
    air = whoosh(0.25, 2000, 7000, 2.5, 0.4, seed=59)
    return mix(d, (m, 0, .8), (air, 0, .6))


def s_shield_up():
    d = 1.4
    return mix(d, (s_energy_swell()[:int(1.1 * SR)], 0, .7), (rumble(d, 0.5, 200, 61), 0, .6), (debris(d, 9, 62, 900, 4000, 0.1), 0, .5),
               (modal(d, 140, [1, 2.2, 3.5], [.6, .4, .3], [1, .6, .4], seed=63), 0.05, .4))


def s_shield_hit():
    d = 0.7
    ping = sine_sweep(d, 620, 380, 0.5) * env_ad(d, 0.001, 0.09)
    force = lowpass(noise(d, 64), 900) * env_ad(d, 0.002, 0.06)
    return mix(d, (crackle(d, 220, 2000, 11000, seed=65, decay=0.18), 0, 1), (ping, 0, .7), (force, 0, .8), (thump(d, 160, 70, 0.08), 0, .6))


def s_shield_burst():
    d = 1.8
    pop = thump(d, 260, 40, 0.15) + highpass(noise(d, 66), 800) * env_ad(d, 0.001, 0.04)
    return reverb(mix(d, (pop, 0, 1), (s_energy_boom()[:int(1.2 * SR)], 0, .5), (debris(d, 26, 67, 1000, 6500, 0.08), 0, .7),
                      (crackle(d, 80, seed=68, decay=0.25), 0, .4)), 0.6, 1.2, 0.2)


def s_telekinesis_grab():
    d = 0.9
    whine = fm(d, 260, 0.5, 3, 2.2) * env_ad(d, 0.05, 0.35, 0.2) * 0.5
    return mix(d, (whine, 0, 1), (s_magnetic_hum()[:int(d * SR)], 0, .5), (debris(d, 8, 69, 1200, 5000, 0.15), 0, .5))


def s_fling():
    d = 0.6
    whirr = bandpass(noise(d, 70), 300, 2400) * (0.6 + 0.4 * np.sin(2 * np.pi * 28 * t_axis(d)))
    return whoosh(d, 220, 1800, 1.8, 0.35, seed=71, body=0.4) + whirr * env_ad(d, 0.02, 0.15) * 0.5


def s_fist_assemble():
    d = 1.6
    r = np.random.default_rng(72)
    clanks = np.zeros(int(d * SR))
    for i in range(9):
        at = 0.05 + 1.0 * (i / 8) ** 1.3
        c = modal(0.5, r.uniform(90, 200), [1, 2.4, 3.9, 6.1], [.25, .18, .1, .07], [1, .6, .4, .3], seed=int(r.integers(0, 1e6)))
        s = int(at * SR)
        seg = c[:len(clanks) - s] * (0.4 + 0.6 * i / 8)
        clanks[s:s + len(seg)] += seg
    return mix(d, (clanks, 0, 1), (s_magnetic_hum()[:int(d * SR)], 0, .4), (s_metal_clang_big()[:int(0.6 * SR)], 1.0, .7))


def s_fist_slam():
    d = 2.0
    return mix(d, (s_impact_heavy(), 0, 1), (s_metal_clang_big(), 0, .7), (rumble(d, 0.8, 90, 73), 0, .8))


def s_rod_whistle():
    d = 0.9
    t = t_axis(d)
    tone = sine_sweep(d, 1900, 700, 1.0) * (t / d) ** 1.2
    air = swept(noise(d, 74), 3000, 900, 4) * (t / d) ** 1.5
    return tone * 0.4 + air * 0.8


def s_rod_impale():
    d = 1.0
    ring = modal(d, 410, [1, 2.7, 5.2, 8.1], [.35, .22, .12, .08], [1, .6, .4, .3], seed=75)
    return mix(d, (thump(d, 120, 45, 0.15), 0, 1.1), (ring, 0.005, .6), (bandpass(noise(d, 76), 300, 2500) * env_ad(d, 0.002, 0.08), 0, .7),
               (debris(d, 8, 77, 500, 2500, 0.08), 0, .4))


# Black Panther
def s_claw_slash():
    d = 0.32
    air = whoosh(d, 1200, 5200, 2.5, 0.35, seed=81)
    shing = modal(d, 3100, [1, 1.37, 1.91], [.08, .06, .04], [1, .6, .4], seed=82) * 0.5
    return mix(d, (air, 0, 1), (shing, 0.06, .6))


def s_claw_hit():
    d = 0.4
    flesh = lowpass(noise(d, 83), 1400) * env_ad(d, 0.001, 0.03)
    return mix(d, (flesh, 0, 1), (thump(d, 150, 70, 0.05), 0, .8), (s_claw_slash()[:int(0.25 * SR)], 0, .5))


def s_vibranium_absorb():
    d = 1.0
    t = t_axis(d)
    vib = 1 + 0.004 * np.sin(2 * np.pi * 6 * t)
    tone = sum(g * np.sin(2 * np.pi * f * vib * t) * np.exp(-t / tau) for f, g, tau in ((523, 1, .5), (784, .6, .4), (1046, .45, .3), (1568, .25, .2)))
    return mix(d, (tone, 0, .7), (thump(d, 130, 60, 0.07), 0, .7), (fm(d, 2100, 1.5, 1.2) * env_ad(d, 0.005, 0.15), 0, .15))


def s_kinetic_release():
    d = 2.2
    sweep = sine_sweep(d, 60, 1200, 2.5) * env_ad(d, 0.4, 0.2, 0.1) * 0.3
    return reverb(mix(d, (sweep, 0, 1), (s_energy_boom(), 0.35, 1), (s_vibranium_absorb()[:int(0.6 * SR)], 0.35, .5)), 0.9, 1.8, 0.3)


def s_pounce():
    d = 0.55
    flap = bandpass(noise(d, 84), 200, 1500) * (np.sin(2 * np.pi * 14 * t_axis(d)) > 0.3) * env_ad(d, 0.01, 0.12)
    return whoosh(d, 300, 2200, 2, 0.4, seed=85) + flap * 0.3


def s_kick_impact():
    d = 0.7
    return mix(d, (thump(d, 120, 50, 0.12), 0, 1.3), (crack(0.06, 86, 1500), 0, .7), (lowpass(noise(d, 87), 900) * env_ad(d, 0.001, 0.05), 0, .7))


def s_camo_on():
    d = 0.9
    t = t_axis(d)
    gliss = sum(np.sin(2 * np.pi * f * (1.6 - 0.8 * t / d) * t) for f in (1200, 1650, 2210)) * np.exp(-t / 0.35) * 0.3
    return gliss + whoosh(d, 3000, 800, 2, 0.3, seed=88) * 0.4


def s_camo_off():
    return s_camo_on()[::-1].copy()


def s_dash():
    return whoosh(0.4, 700, 4500, 2.2, 0.3, seed=89, body=0.2)


# Thor
def s_hammer_whoosh():
    d = 0.6
    spin = 0.55 + 0.45 * np.sin(2 * np.pi * 16 * t_axis(d)) ** 2
    return whoosh(d, 140, 900, 1.5, 0.45, seed=91, body=0.8) * spin


def s_hammer_impact():
    d = 1.6
    return mix(d, (s_impact_heavy(), 0, 1), (s_impact_metal(), 0, .6), (crackle(d, 90, seed=92, decay=0.3), 0.02, .6))


def s_lightning_crackle():
    d = 1.0
    return mix(d, (crackle(d, 120, 1200, 10000, seed=93, decay=0.4), 0, 1), (rumble(d, 0.5, 120, 94), 0, .7), (crack(0.05, 95, 2000), 0, .8))


def s_hammer_catch():
    d = 0.5
    return mix(d, (thump(d, 160, 80, 0.06), 0, 1), (modal(d, 260, [1, 2.4, 4.1], [.15, .1, .06], [1, .5, .3], seed=96), 0, .5))


# Hulk
def s_hulk_punch():
    d = 0.9
    return mix(d, (thump(d, 85, 35, 0.2), 0, 1.4), (crack(0.07, 101, 1000), 0, .7), (lowpass(noise(d, 102), 600) * env_ad(d, 0.002, 0.12), 0, .9))


def s_ground_slam():
    d = 2.4
    return reverb(mix(d, (s_impact_heavy(), 0, 1), (rumble(d, 1.0, 80, 103), 0, 1.2), (debris(d, 24, 104, 300, 2200, 0.2), 0, .5)), 0.9, 1.8, 0.25)


def s_thunderclap():
    d = 2.2
    blast = swept(noise(d, 105), 5000, 120, 1.0, curve=0.35) * env_ad(d, 0.001, 0.5)
    return reverb(mix(d, (crack(0.12, 106, 600), 0, 1.4), (blast, 0, 1), (thump(d, 70, 28, 0.6), 0, 1.2)), 1.0, 2.0, 0.35)


def s_leap():
    d = 0.7
    return mix(d, (thump(d, 90, 45, 0.15), 0, 1), (whoosh(0.6, 200, 1500, 1.8, 0.35, seed=107, body=0.5), 0.03, .8))


# Zed
def s_blade_slash():
    d = 0.3
    air = whoosh(d, 2000, 7500, 3, 0.3, seed=111)
    ring = modal(d, 2600, [1, 1.62, 2.31], [.1, .07, .05], [1, .5, .3], seed=112) * 0.4
    return mix(d, (air, 0, 1), (ring, 0.04, .7))


def s_shadow_whoosh():
    d = 0.8
    breath = lowpass(noise(d, 113), 1200) * np.sin(np.pi * t_axis(d) / d) ** 2
    low = sine_sweep(d, 70, 45) * np.sin(np.pi * t_axis(d) / d) ** 3 * 0.4
    return breath + low + swept(noise(d, 114), 300, 1400, 1.5) * np.sin(np.pi * t_axis(d) / d) ** 2 * 0.5


def s_shuriken_whir():
    d = 0.7
    am = 0.5 + 0.5 * np.sin(2 * np.pi * 34 * t_axis(d))
    return bandpass(noise(d, 115), 1500, 6000) * am * np.sin(np.pi * t_axis(d) / d) + modal(d, 3200, [1, 1.5], [.3, .2], [.2, .1], seed=116) * am


def s_mark_burst():
    d = 1.2
    return reverb(mix(d, (thump(d, 140, 40, 0.25), 0, 1), (debris(d, 18, 117, 1500, 7000, 0.03), 0, .6), (s_shadow_whoosh()[:int(0.5 * SR)], 0, .6),
                      (crack(0.08, 118, 1500), 0, .8)), 0.6, 1.2, 0.25)


# Cyclops
def s_optic_beam():
    d = 1.2
    t = t_axis(d)
    buzz = signal.sawtooth(2 * np.pi * 110 * t) + 0.5 * signal.sawtooth(2 * np.pi * 220.8 * t)
    buzz = bandpass(buzz, 200, 5000) * (1 + 0.3 * np.sin(2 * np.pi * 19 * t))
    sizzle = highpass(noise(d, 121), 4000) * 0.3
    return (buzz * 0.4 + sizzle) * env_ad(d, 0.03, 0.8, 0.5)


def s_optic_blast():
    d = 1.4
    return reverb(mix(d, (crack(0.1, 122, 1000), 0, 1.2), (s_optic_beam(), 0, .7), (thump(d, 120, 45, 0.3), 0, 1)), 0.7, 1.3, 0.25)


# Ghost Rider
def s_chain_whip():
    d = 0.6
    r = np.random.default_rng(131)
    rattle = np.zeros(int(d * SR))
    for i in range(22):
        at = 0.02 + 0.35 * r.random()
        c = clink(r.uniform(600, 1400), int(r.integers(0, 1e6)), 0.12) * r.uniform(0.2, 0.6)
        s = int(at * SR)
        seg = c[:len(rattle) - s]
        rattle[s:s + len(seg)] += seg
    return mix(d, (rattle, 0, 1), (whoosh(0.35, 600, 3500, 2, 0.6, seed=132), 0, .7), (crack(0.05, 133, 2500), 0.33, 1))


def s_hellfire():
    d = 1.4
    roar = lowpass(noise(d, 134), 900) * (0.7 + 0.3 * np.abs(np.sin(2 * np.pi * 7 * t_axis(d))))
    pops = crackle(d, 40, 800, 5000, seed=135) * 0.5
    return (roar + pops + swept(noise(d, 136), 400, 2500, 1.2) * 0.4) * env_ad(d, 0.08, 0.6, 0.4)


# Sandman
def s_sand_whoosh():
    d = 0.9
    grains = highpass(noise(d, 141), 2500) * (np.random.default_rng(142).random(int(d * SR)) > 0.3)
    return (lowpass(grains, 9000) * 0.6 + swept(noise(d, 143), 500, 2500, 1.4) * 0.6) * np.sin(np.pi * t_axis(d) / d) ** 1.5


def s_sand_impact():
    d = 0.9
    return mix(d, (thump(d, 100, 45, 0.12), 0, 1), (highpass(noise(d, 144), 1800) * env_ad(d, 0.003, 0.25), 0.01, .6))


# Batman
def s_bat_punch():
    # A gloved, armoured fist into a body: a short low thud with a leathery slap on top.
    d = 0.45
    slap = bandpass(noise(d, 201), 900, 4200) * env_ad(d, 0.0008, 0.018)
    return mix(d, (thump(d, 120, 55, 0.07), 0, 1.3), (slap, 0, .8), (lowpass(noise(d, 202), 500) * env_ad(d, 0.001, 0.05), 0, .7))


def s_bat_batarang():
    # A spinning blade cutting the air: a whistling whoosh with a fast flutter.
    d = 0.55
    t = t_axis(d)
    air = whoosh(d, 1600, 5200, 3.2, 0.25, seed=203)
    flutter = (0.55 + 0.45 * np.sin(2 * np.pi * 38 * t)) * env_ad(d, 0.01, 0.25)
    tone = sine_sweep(d, 2400, 1700) * env_ad(d, 0.01, 0.2) * 0.25
    return mix(d, (air * flutter, 0, 1), (tone, 0, .5), (crack(0.03, 204, 4000), 0, .4))


def s_bat_grapnel():
    # The grapnel gun: a compressed-air pop, then the line whizzing off the spool.
    d = 0.8
    pop = mix(0.2, (thump(0.2, 220, 90, 0.03), 0, 1), (highpass(noise(0.2, 205), 1500) * env_ad(0.2, 0.0005, 0.02), 0, 1))
    t = t_axis(d)
    spool = bandpass(noise(d, 206), 2500, 7000) * (0.6 + 0.4 * np.sign(np.sin(2 * np.pi * (90 - 60 * t) * t))) * env_ad(d, 0.03, 0.3)
    return mix(d, (pop, 0, 1.1), (spool, 0.02, .55), (whoosh(0.5, 900, 3000, 2.5, 0.3, seed=207), 0.01, .5))


def s_bat_smoke():
    # A smoke pellet bursting: a dull pop and a long thick hiss that spreads.
    d = 2.6
    hiss = swept(noise(d, 208), 6000, 1800, 1.2, curve=0.6) * env_ad(d, 0.03, 0.8) * np.clip((d - t_axis(d)) / 0.9, 0, 1)
    return reverb(mix(d, (thump(d, 90, 50, 0.08), 0, 1), (hiss, 0, .9), (lowpass(noise(d, 209), 700) * env_ad(d, 0.05, 0.8), 0, .5)), 0.7, 1.4, 0.2)


def s_bat_flash():
    # A flashbang: a sharp crack, a bright bang and a high ringing tail.
    d = 2.2
    t = t_axis(d)
    ring = np.sin(2 * np.pi * 3600 * t) * env_ad(d, 0.05, 1.1) * 0.18
    return reverb(mix(d, (crack(0.08, 210, 900), 0, 1.4), (thump(d, 160, 45, 0.18), 0, 1.2), (highpass(noise(d, 211), 3000) * env_ad(d, 0.001, 0.1), 0, .9), (ring, 0.02, 1)), 0.8, 1.5, 0.3)


def s_bat_flash_bounce():
    # The flash grenade's little can hitting the ground: a small hollow metal tink and a short rattle (not loud).
    d = 0.32
    tink = modal(d, 2900, [1, 2.31, 3.94], [0.05, 0.03, 0.018], [1, .5, .3], seed=213)
    rattle = bandpass(noise(d, 214), 2500, 7000) * env_ad(d, 0.001, 0.018)
    return mix(d, (tink, 0, .8), (rattle, 0, .5), (lowpass(noise(d, 215), 900) * env_ad(d, 0.001, 0.01), 0, .3))


def s_bat_flash_ring():
    # The ringing in the ears after a flashbang (looped; the client sets its loudness as hearing comes back): a pure high
    # tone beating slowly against a second one, a faint octave and a breath of hiss. Every part repeats whole in 4 s.
    d = 4.0
    t = t_axis(d)
    tone = np.sin(2 * np.pi * 3700 * t) + .35 * np.sin(2 * np.pi * 3712.5 * t) + .08 * np.sin(2 * np.pi * 7400 * t)
    hiss = np.roll(highpass(noise(d, 216), 6000), int(SR * .5)) * .02
    return tone * .5 + hiss


def _beep(d, f, seed=0):
    t = t_axis(d)
    gate = np.clip(t / 0.004, 0, 1) * np.clip((d - t) / 0.01, 0, 1)
    return (np.sin(2 * np.pi * f * t) + 0.2 * np.sin(2 * np.pi * 2 * f * t)) * gate


def s_td_signal():
    # The earpiece call: BEEP, BEEP, then the confirm chirp (two quick rising tones) - small, clean, a little radio hiss.
    d = 0.95
    chirp = np.concatenate([_beep(0.05, 2400), _beep(0.07, 3300)])
    hiss = bandpass(noise(d, 451), 2500, 7000) * 0.04 * env_ad(d, 0.01, 0.5)
    return mix(d, (_beep(0.09, 2600), 0.0, .7), (_beep(0.09, 2600), 0.24, .7), (chirp, 0.62, .8), (hiss, 0, 1))


def s_td_abort():
    # Signal cancelled: one beep, then a falling, buzzy error tone.
    d = 0.75
    err = signal.square(2 * np.pi * np.cumsum(np.linspace(620, 380, int(0.38 * SR))) / SR) * env_ad(0.38, 0.005, 0.25)
    return mix(d, (_beep(0.08, 2600), 0, .7), (lowpass(err, 2500), 0.2, .45), (crack(0.02, 452, 2600), 0.18, .3))


def s_td_tracker():
    # The tracker on the back of the head: a hard CLICK and three tiny high blips as its light comes on.
    d = 0.6
    layers = [(tick_click(461, 2400), 0, 1.1), (thump(0.08, 600, 300, 0.01), 0, .5)]
    for i in range(3):
        layers.append((_beep(0.035, 4200), 0.16 + i * 0.12, .35))
    return mix(d, *layers)


def s_td_lock():
    # The grip locking: cloth and armour pressed hard, a heavy mechanical clunk of the gauntlets.
    d = 0.5
    cloth = bandpass(noise(d, 471), 600, 3500) * env_ad(d, 0.01, 0.08)
    return mix(d, (cloth, 0, .6), (lock_clunk(472, 360), 0.03, 1.1), (thump(d, 120, 55, 0.06), 0.02, .8))


def s_bm_engine():
    # The Batmobile coming in: a far growl rising fast to a roar (pitch up as it nears), the turbine whine on top.
    d = 1.5
    t = t_axis(d)
    f = 48 * (1 + 0.9 * (t / d) ** 1.6)
    ph = 2 * np.pi * np.cumsum(f) / SR
    growl = (signal.sawtooth(ph) + 0.6 * signal.sawtooth(2 * ph + 0.3)) * (0.25 + 0.75 * (t / d) ** 1.4)
    rumble = lowpass(noise(d, 481), 300) * (t / d) ** 1.2
    whine = np.sin(2 * np.pi * np.cumsum(1400 + 1600 * (t / d) ** 2) / SR) * 0.12 * (t / d) ** 2
    return lowpass(soft(growl * 0.6 + rumble * 1.4 + whine, 1.6), 3500) * np.clip((d - t) / 0.12, 0, 1)


def s_bm_drift():
    # Tyres sliding: a wavering squeal (two bands) over road friction and grit.
    d = 1.4
    t = t_axis(d)
    fq = 1250 + 160 * np.sin(2 * np.pi * 5.5 * t) + 90 * np.sin(2 * np.pi * 13 * t)
    squeal = np.sin(2 * np.pi * np.cumsum(fq) / SR) + 0.5 * np.sin(2 * np.pi * np.cumsum(fq * 1.97) / SR)
    env = np.clip(t / 0.06, 0, 1) * np.clip((d - t) / 0.35, 0, 1)
    friction = bandpass(noise(d, 491), 400, 3000) * 0.7
    grit = debris(d, 18, 492, 1500, 6000, 0.05)
    return mix(d, (squeal * env * 0.45, 0, 1), (friction * env, 0, .8), (grit, 0, .4))


def s_bm_boost():
    # The afterburner: a deep engine roar and a jet-like burst of rushing air, falling away into the distance.
    d = 1.9
    t = t_axis(d)
    roar_f = 60 * (1 + 0.5 * np.clip(t / 0.3, 0, 1))
    ph = 2 * np.pi * np.cumsum(roar_f) / SR
    roar = soft(signal.sawtooth(ph) + 0.5 * signal.sawtooth(1.5 * ph), 2.0) * env_ad(d, 0.02, 0.9)
    jet = swept(noise(d, 501), 900, 3500, 1.4, curve=0.5) * env_ad(d, 0.03, 0.7)
    boom = thump(d, 90, 35, 0.12)
    return mix(d, (lowpass(roar, 1800), 0, .9), (jet, 0, 1.0), (boom, 0, 1.0))


def s_bm_gun():
    # One round of the Batmobile's guns: a heavy, short mechanical bang with a metallic tail (rapid fire = many of these).
    d = 0.22
    bang = mix(d, (crack(0.03, 511, 1500), 0, 1.2), (thump(d, 140, 60, 0.03), 0, 1.1), (highpass(noise(d, 512), 3000) * env_ad(d, 0.0005, 0.01), 0, .6))
    return mix(d, (bang, 0, 1), (clink(1200, 513, 0.2) * env_ad(0.2, 0.001, 0.03), 0.004, .3))


def s_bat_mine():
    # Gadget electronics: two short beeps and a mechanical click.
    d = 0.45
    t = t_axis(d)
    beep = np.sin(2 * np.pi * 2300 * t) * ((t < 0.07) | ((t > 0.14) & (t < 0.21))) * 0.6
    return mix(d, (bandpass(beep, 1500, 4000), 0, 1), (crack(0.02, 212, 2500), 0.28, .6))


def s_bat_cape():
    # The heavy cape snapping open in the wind.
    d = 0.6
    snap = bandpass(noise(d, 213), 300, 2400) * env_ad(d, 0.004, 0.06)
    flap = whoosh(d, 250, 1400, 1.4, 0.4, seed=214, body=0.4)
    return mix(d, (snap, 0, 1), (flap, 0.01, .8))


# Batman: the WayneTech wrist cannon (energy, not a gun: electric cracks, servos, a hum)
def servo(d, f0, f1, seed):
    # A small motor: a buzzy sweep through a narrow band, with a little grit.
    t = t_axis(d)
    saw = signal.sawtooth(2 * np.pi * np.cumsum(f0 * (f1 / f0) ** (t / d)) / SR)
    whine = bandpass(saw, min(f0, f1) * 0.8, max(f0, f1) * 3.5) * np.sin(np.pi * t / d) ** 0.6
    return whine + bandpass(noise(d, seed), 2000, 6000) * 0.15 * np.sin(np.pi * t / d)


def tick_click(seed, base=1800):
    # A crisp mechanical click: a tiny crack and a short metal ping.
    return mix(0.12, (crack(0.03, seed, 3000), 0, 1), (clink(base, seed + 1, 0.12) * env_ad(0.12, 0.0005, 0.02), 0, .5))


def lock_clunk(seed, base=420):
    return mix(0.3, (thump(0.3, 300, 140, 0.04), 0, 1), (clink(base, seed, 0.3) * env_ad(0.3, 0.001, 0.06), 0, .55), (crack(0.03, seed + 2, 2200), 0, .6))


def s_cannon_deploy():
    # Both gauntlets transforming: plates sliding, servos, clicks, a lock each (the left a beat later), a power-up chirp.
    d = 1.0
    layers = []
    for off, p, sd in ((0.0, 1.0, 301), (0.15, 1.08, 311)):
        slide = swept(noise(0.14, sd), 1400 * p, 3600 * p, 2.0) * env_ad(0.14, 0.01, 0.05)
        layers += [(slide, off, .6), (servo(0.3, 650 * p, 1400 * p, sd + 1), off + 0.05, .45),
                   (tick_click(sd + 2, 2100 * p), off + 0.12, .7), (tick_click(sd + 4, 2500 * p), off + 0.24, .55),
                   (lock_clunk(sd + 6, 430 * p), off + 0.4, .9)]
    chirp = sine_sweep(0.18, 1200, 3200, 0.7) * env_ad(0.18, 0.02, 0.06)
    layers += [(tick_click(321, 2900), 0.72, .5), (chirp, 0.66, .3)]
    return mix(d, *layers)


def s_cannon_charge():
    # The emitters charging: a fast rising whine with a shimmer and a few sparks, ending on a point.
    d = 0.32
    t = t_axis(d)
    rise = sine_sweep(d, 380, 2700, 1.6) * np.clip(t / d, 0, 1) ** 1.5
    shimmer = fm(d, 1800, 1.5, 2.2, 1.6) * np.clip(t / d, 0, 1) ** 2 * 0.35
    return mix(d, (rise, 0, .8), (shimmer, 0, 1), (crackle(d, 60, 3000, 9000, seed=331) * np.clip(t / d, 0, 1), 0, .35))


def s_cannon_shot():
    # One bolt: a sharp electric crack, a compact mechanical snap, a high snap, a short low push of power.
    d = 0.24
    t = t_axis(d)
    crack_e = highpass(noise(d, 341), 2500) * env_ad(d, 0.0003, 0.006)
    zap = crackle(0.05, 900, 2500, 10000, seed=342, decay=0.015)
    snap = clink(950, 343, 0.1) * env_ad(0.1, 0.0005, 0.018)
    hi = sine_sweep(d, 6800, 3200, 0.5) * env_ad(d, 0.0005, 0.012)
    body = thump(d, 150, 62, 0.035)
    tone = fm(d, 820, 2.01, 3.0, 0.7) * env_ad(d, 0.0008, 0.03)
    return mix(d, (crack_e, 0, 1.1), (zap, 0, .8), (snap, 0.002, .55), (hi, 0, .45), (body, 0, 1.0), (tone, 0, .4))


def s_cannon_hum():
    # The energy hum under the fire: a buzzing low core, a bright AM shimmer, a band of hiss, a slow throb.
    # Exactly periodic over its length (whole cycles, noise filtered round the loop) so it loops without a seam.
    d = 1.0
    n = int(d * SR)
    t = np.arange(n) / SR
    core = sum(g * np.sin(2 * np.pi * f * t) for f, g in ((100, 1), (200, .55), (300, .3), (400, .18), (700, .08)))
    saw = np.fft.rfft(signal.sawtooth(2 * np.pi * 100 * t))
    fr = np.fft.rfftfreq(n, 1 / SR)
    saw[fr > 1800] = 0
    buzz = np.fft.irfft(saw, n)
    shimmer = np.sin(2 * np.pi * 2400 * t) * (0.6 + 0.4 * np.sin(2 * np.pi * 8 * t)) + 0.5 * np.sin(2 * np.pi * 3600 * t) * (0.5 + 0.5 * np.sin(2 * np.pi * 12 * t + 1))
    hn = np.fft.rfft(np.random.default_rng(351).standard_normal(n))
    hn[(fr < 2500) | (fr > 7000)] = 0
    hiss = np.fft.irfft(hn, n)
    hiss /= np.max(np.abs(hiss)) + 1e-9
    throb = 0.8 + 0.2 * np.sin(2 * np.pi * 2 * t)
    return (core * .5 + buzz * .35 + shimmer * .12 + hiss * .18) * throb


def s_cannon_final():
    # The last heavy discharge: a big crack, a deep push, electricity spitting out after it.
    d = 1.3
    zap = crackle(0.5, 220, 1500, 9000, seed=361, decay=0.15)
    drop = fm(0.6, 900, 1.5, 4.0, 0.3) * env_ad(0.6, 0.002, 0.15)
    return reverb(mix(d, (crack(0.1, 362, 900), 0, 1.4), (thump(d, 130, 34, 0.25), 0, 1.4), (zap, 0.01, .8), (drop, 0, .5),
                      (rumble(d, 0.3, 180, 363), 0.01, .6)), 0.6, 1.2, 0.22)


def s_cannon_stop():
    # The fire stops: the power winds down, heat hisses off the emitters, the metal ticks as it cools.
    d = 1.6
    t = t_axis(d)
    wind = sine_sweep(0.7, 1900, 280, 0.6) * env_ad(0.7, 0.005, 0.25)
    hiss = highpass(noise(d, 371), 3000) * env_ad(d, 0.02, 0.45) * 0.5
    return mix(d, (wind, 0, .7), (fm(0.6, 600, 0.5, 2, 0.4) * env_ad(0.6, 0.005, 0.2), 0, .3), (hiss, 0.03, .8),
               (debris(d, 7, 372, 3000, 8000, 0.3), 0, .35))


def s_cannon_retract():
    # The emitters fold away: falling servos, clicks, the plates closing and one lock each.
    d = 0.9
    layers = []
    for off, p, sd in ((0.0, 1.0, 381), (0.12, 1.07, 391)):
        layers += [(servo(0.28, 1300 * p, 600 * p, sd), off, .45), (tick_click(sd + 2, 2300 * p), off + 0.08, .55),
                   (swept(noise(0.12, sd + 3), 3200 * p, 1300 * p, 2.0) * env_ad(0.12, 0.01, 0.04), off + 0.22, .5),
                   (lock_clunk(sd + 5, 400 * p), off + 0.36, .8)]
    return mix(d, *layers)


# Batman: the sonic trap (Batman v Superman WayneTech emitters: heavy hydraulics, a deep hum, pressure-wave blasts)
def looped_noise(n, lo, hi, seed):
    # Noise band-limited round the loop (FFT), so it repeats without a seam.
    fr = np.fft.rfftfreq(n, 1 / SR)
    x = np.fft.rfft(np.random.default_rng(seed).standard_normal(n))
    x[(fr < lo) | (fr > hi)] = 0
    y = np.fft.irfft(x, n)
    return y / (np.max(np.abs(y)) + 1e-9)


def ratchet(d, count, seed, base=900, accel=1.0):
    # Mechanical clicks along the way, closing up (accel > 1) or spreading out (accel < 1).
    r = np.random.default_rng(seed)
    out = np.zeros(int(d * SR))
    for i in range(count):
        k = (i / max(1, count - 1)) ** accel
        at = 0.03 + (d - 0.12) * k
        c = tick_click(int(r.integers(0, 1e6)), base * r.uniform(0.85, 1.15)) * r.uniform(0.4, 0.8)
        s = int(at * SR)
        seg = c[:len(out) - s]
        out[s:s + len(seg)] += seg
    return out


def s_sonic_beep():
    # The red button: a firm mechanical click, then one clean military beep (two pure partials, soft edges), a small room.
    d = 0.5
    t = t_axis(0.19)
    gate = np.clip(t / 0.004, 0, 1) * np.clip((0.19 - t) / 0.012, 0, 1)
    beep = (np.sin(2 * np.pi * 2750 * t) + 0.22 * np.sin(2 * np.pi * 5500 * t) + 0.12 * np.sin(2 * np.pi * 1375 * t)) * gate
    click = mix(0.06, (crack(0.02, 401, 2500), 0, .8), (thump(0.06, 520, 260, 0.008), 0, .7))
    return reverb(mix(d, (click, 0, .9), (beep, 0.018, .55)), 0.3, 0.35, 0.12)


def s_sonic_rumble():
    # Under the ground: a deep rumble swelling, a grinding "KRRR" (gritty, chattering), soil and stones trickling, the
    # ground cracking open at 0.3 s.
    d = 1.5
    t = t_axis(d)
    swell = np.clip(t / 0.25, 0, 1) * np.clip((d - t) / 0.5, 0, 1)
    low = lowpass(noise(d, 411), 110, 4) * 4 * swell
    chatter = 0.55 + 0.45 * np.sign(np.sin(2 * np.pi * (17 + 5 * t) * t))
    grind = bandpass(noise(d, 412), 180, 750) * chatter * swell
    trickle = debris(d, 22, 413, 500, 2600, 0.2)
    crack_open = mix(0.5, (thump(0.5, 95, 38, 0.12), 0, 1.2), (bandpass(noise(0.5, 414), 300, 2500) * env_ad(0.5, 0.001, 0.05), 0, .9))
    return mix(d, (low, 0, 1), (grind, 0, .55), (trickle, 0, .45), (crack_open, 0.3, 1))


def s_sonic_rise():
    # The device coming up: a hydraulic whine climbing, the hiss of the rams, metal scraping on soil and on itself,
    # ratchet clicks closing up (slow, medium, fast).
    d = 1.15
    t = t_axis(d)
    whine = sine_sweep(d, 140, 430, 1.4)
    whine = lowpass(whine + 0.4 * np.sign(whine) * 0.3, 1600) * np.clip(t / 0.15, 0, 1) * np.clip((d - t) / 0.1, 0, 1)
    hiss = swept(noise(d, 421), 2200, 5200, 2.0) * np.clip(t / d, 0, 1) ** 0.8
    scrape = bandpass(noise(d, 422), 600, 2400) * (0.5 + 0.5 * np.sin(2 * np.pi * 9 * t) ** 2) * np.clip(1 - t / d, 0, 1)
    groan = modal(d, 95, [1, 2.3, 3.9], [0.6, 0.4, 0.25], [1, .5, .3], seed=423) * (0.5 + 0.5 * np.sin(2 * np.pi * 3 * t))
    return mix(d, (whine, 0, .6), (hiss, 0, .35), (scrape, 0, .35), (groan, 0, .3), (ratchet(d, 9, 424, 1100, 0.6), 0, .55))


def s_sonic_lock():
    # Locked: a hard CLACK of steel, then the heavy K-CHUNK of the legs and the frame settling, a low ring.
    d = 0.9
    clack = mix(0.3, (crack(0.03, 431, 1800), 0, 1.2), (modal(0.3, 260, [1, 2.7, 5.2, 8.1], [0.08, 0.05, 0.03, 0.02], [1, .7, .4, .2], seed=432), 0, .7))
    chunk = mix(0.6, (thump(0.6, 110, 42, 0.09), 0, 1.4), (modal(0.6, 120, [1, 2.4, 4.1], [0.25, 0.12, 0.06], [1, .5, .3], seed=433) * env_ad(0.6, 0.001, 0.2), 0, .6),
                (lowpass(noise(0.6, 434), 700) * env_ad(0.6, 0.001, 0.04), 0, .8))
    return reverb(mix(d, (clack, 0, 1), (chunk, 0.11, 1.1), (debris(d, 6, 435, 600, 2200, 0.2), 0, .25)), 0.5, 0.7, 0.18)


def s_sonic_hum():
    # The active emitter (looped): a deep 55 Hz hum with its harmonics, a resonant ring beating slowly, a faint cold
    # high whine, air hiss, a 6 Hz throb. Whole cycles over the length so it loops without a seam.
    d = 2.0
    n = int(d * SR)
    t = np.arange(n) / SR
    hum = sum(g * np.sin(2 * np.pi * f * t) for f, g in ((55, 1), (110, .6), (165, .35), (220, .2), (330, .1)))
    res = 0.5 * (np.sin(2 * np.pi * 440 * t) + np.sin(2 * np.pi * 440.5 * t)) * 0.18
    whine = np.sin(2 * np.pi * 3520 * t) * 0.035
    air = looped_noise(n, 300, 2400, 441) * 0.12
    throb = 0.82 + 0.18 * np.sin(2 * np.pi * 6 * t)
    return (hum * .5 + res + whine + air) * throb


def s_sonic_pulse():
    # One pulse leaving the chamber: a short hard thump of pressure, a burst of pushed air, a fast high zip.
    d = 0.35
    air = lowpass(noise(d, 451), 900) * env_ad(d, 0.001, 0.03)
    zip_ = swept(noise(0.08, 452), 7000, 1500, 2.5) * env_ad(0.08, 0.001, 0.025)
    return mix(d, (thump(d, 240, 70, 0.045), 0, 1.4), (air, 0, .9), (zip_, 0, .5), (fm(0.12, 180, 1.5, 3, 0.5) * env_ad(0.12, 0.001, 0.03), 0, .4))


def s_sonic_hit():
    # A pulse landing: a huge sub-bass WHOOOM, a mid boom under it, the air rushing past and away, a little crunch.
    d = 1.7
    sub = thump(d, 78, 30, 0.45)
    mid = thump(d, 170, 60, 0.12)
    rush = swept(noise(d, 461), 250, 1400, 1.4, curve=0.4) * env_ad(d, 0.03, 0.35)
    crunch = soft(bandpass(noise(0.2, 462), 600, 3500) * env_ad(0.2, 0.001, 0.03) * 3, 3)
    return reverb(mix(d, (sub, 0, 1.6), (mid, 0, 1.0), (rush, 0.01, .55), (crunch, 0, .4)), 0.8, 1.3, 0.25)


def s_sonic_ring():
    # Ringing ears (looped, quiet in game): a pure 4 kHz tone beating slowly against a near neighbour, a faint overtone.
    d = 2.0
    n = int(d * SR)
    t = np.arange(n) / SR
    return 0.5 * (np.sin(2 * np.pi * 4000 * t) + 0.7 * np.sin(2 * np.pi * 4000.5 * t)) + 0.12 * np.sin(2 * np.pi * 6000 * t)


def s_sonic_break():
    # Failing: sparks and a sputtering short circuit getting worse, a metal crack, then (at 0.7 s) a small blast and
    # pieces raining down.
    d = 1.6
    t = t_axis(0.7)
    sputter = crackle(0.7, 120, 1500, 9000, seed=471) * np.clip(t / 0.7, 0.2, 1) ** 1.5
    buzz = lowpass(signal.square(2 * np.pi * 60 * t), 900) * (np.random.default_rng(472).random(len(t)) > 0.35) * 0.25 * np.clip(t / 0.7, 0, 1)
    creak = clink(240, 473, 0.3) * env_ad(0.3, 0.001, 0.08)
    blast = mix(0.9, (crack(0.08, 474, 900), 0, 1.3), (thump(0.9, 140, 40, 0.16), 0, 1.4), (highpass(noise(0.9, 475), 1500) * env_ad(0.9, 0.001, 0.09), 0, .8),
                (crackle(0.4, 300, 1500, 9000, seed=476, decay=0.12), 0.01, .6))
    return reverb(mix(d, (sputter, 0, .8), (buzz, 0, 1), (creak, 0.35, .6), (blast, 0.7, 1.2), (debris(0.8, 12, 477, 900, 5000, 0.05), 0.75, .5)), 0.6, 1.0, 0.2)


def s_sonic_retract():
    # Shutting down: pressure let out of the rams, the hydraulic whine falling, ratchet clicks spreading out, soil
    # trickling back, the last thunk underground.
    d = 1.45
    t = t_axis(d)
    vent = highpass(noise(d, 481), 2500) * env_ad(d, 0.01, 0.18)
    whine = lowpass(sine_sweep(d, 420, 120, 0.8), 1500) * np.clip(t / 0.08, 0, 1) * np.clip((1.25 - t) / 0.2, 0, 1)
    return mix(d, (vent, 0, .6), (whine, 0.05, .55), (ratchet(1.2, 8, 482, 1000, 1.6), 0.05, .5), (debris(d, 14, 483, 500, 2200, 0.6), 0, .35),
               (mix(0.3, (thump(0.3, 90, 40, 0.06), 0, 1.1), (lowpass(noise(0.3, 484), 600) * env_ad(0.3, 0.001, 0.03), 0, .6)), 1.22, 1))


# Batman: the electric gauntlets (WayneTech power knuckles: locks and servos, stored charge, KRAK-KZZZT discharges)
def zap_buzz(d, f0, seed, gate=0.55):
    # A gated electric buzz: a sawtooth chopped by noise, through a bright band.
    t = t_axis(d)
    saw = signal.sawtooth(2 * np.pi * f0 * t) * (np.abs(lowpass(noise(d, seed), 400)) > gate * 0.1)
    return bandpass(saw, 300, 6000)


def s_shock_equip():
    # Locking on: two pairs of mechanical locks and a servo each (the left a beat later), the shutters clicking open,
    # the first sparks, then the charge spreading: a rising whine and crackle that ends just before the clap.
    d = 1.3
    t = t_axis(d)
    layers = []
    for off, p, sd in ((0.06, 1.0, 901), (0.2, 1.06, 911)):
        layers += [(servo(0.22, 520 * p, 1150 * p, sd), off, .4), (lock_clunk(sd + 2, 380 * p), off + 0.12, .9),
                   (tick_click(sd + 4, 2200 * p), off + 0.22, .6)]
    layers += [(tick_click(921, 3100), 0.48, .45), (tick_click(923, 3400), 0.53, .4)]
    k = np.clip((t - 0.55) / 0.7, 0, 1)
    rise = sine_sweep(d, 300, 2400, 1.4) * k ** 1.6
    shimmer = fm(d, 1500, 1.41, 2.0, 1.7) * k ** 2 * 0.3
    sparks = crackle(d, 140, 2500, 10000, seed=925) * (0.15 + 0.85 * k ** 1.3) * (t > 0.55)
    hum = (np.sin(2 * np.pi * 120 * t) + .5 * np.sin(2 * np.pi * 240 * t)) * k * 0.3
    layers += [(rise, 0, .55), (shimmer, 0, 1), (sparks, 0, .55), (hum, 0, .8)]
    out = mix(d, *layers)
    out *= np.clip((1.28 - t) / 0.03, 0, 1)
    return out


def s_shock_clap():
    # The fists slam together: a hard metal CLACK, an electric KRAK, a short bass hit, a spitting zap tail.
    d = 1.1
    clack = mix(0.5, (modal(0.5, 860, [1, 2.3, 3.9, 5.7], [.12, .08, .05, .03], [1, .7, .5, .3], seed=931), 0, .9), (crack(0.04, 932, 2500), 0, 1.2))
    krak = highpass(noise(0.06, 933), 1800) * env_ad(0.06, 0.0002, 0.008)
    zap = crackle(0.6, 700, 1500, 11000, seed=934, decay=0.12)
    buzz = zap_buzz(0.35, 140, 935) * env_ad(0.35, 0.001, 0.09)
    bass = thump(d, 105, 38, 0.18)
    return reverb(mix(d, (clack, 0, 1), (krak, 0.003, 1.3), (zap, 0.004, .9), (buzz, 0.01, .5), (bass, 0, 1.4),
                      (sine_sweep(0.12, 5200, 2600, 0.5) * env_ad(0.12, 0.0005, 0.02), 0.002, .4)), 0.5, 0.9, 0.18)


def s_shock_hum():
    # The stored charge humming in the gauntlets: a low electrical hum, a gritty buzz, a thin high whine that wavers,
    # sparse crackle. Exactly periodic over its length (whole cycles, crackle filtered round the loop) so it loops.
    d = 1.0
    n = int(d * SR)
    t = np.arange(n) / SR
    fr = np.fft.rfftfreq(n, 1 / SR)
    core = sum(g * np.sin(2 * np.pi * f * t) for f, g in ((60, .6), (120, 1), (180, .4), (240, .25), (360, .1)))
    saw = np.fft.rfft(signal.sawtooth(2 * np.pi * 120 * t))
    saw[fr > 2500] = 0
    buzz = np.fft.irfft(saw, n) * (0.7 + 0.3 * np.sin(2 * np.pi * 3 * t))
    whine = np.sin(2 * np.pi * 3300 * t + 0.8 * np.sin(2 * np.pi * 7 * t)) * (0.5 + 0.5 * np.sin(2 * np.pi * 5 * t) ** 2)
    r = np.random.default_rng(941)
    imp = np.zeros(n)
    for at in r.integers(0, n, 70):
        imp[at] = r.uniform(.4, 1) * r.choice([-1, 1])
    cr = np.fft.rfft(imp)
    cr[(fr < 2000) | (fr > 10000)] = 0
    crack_l = np.fft.irfft(cr, n)
    crack_l /= np.max(np.abs(crack_l)) + 1e-9
    hiss = looped_noise(n, 3000, 8000, 942)
    return core * .45 + buzz * .3 + whine * .06 + crack_l * .35 + hiss * .05


def s_shock_swing():
    # A heavy charged swing: the air moved by the fist, an electric buzz sweeping up with it, a crackle off the knuckles.
    d = 0.42
    t = t_axis(d)
    air = whoosh(d, 220, 1500, 1.6, 0.55, seed=951, body=0.5)
    sweep = bandpass(signal.sawtooth(2 * np.pi * np.cumsum(90 * (2.2 ** (t / d))) / SR), 200, 4000) * np.sin(np.pi * t / d) ** 1.5
    return mix(d, (air, 0, 1), (sweep, 0, .35), (crackle(d, 45, 2500, 9000, seed=952) * np.sin(np.pi * t / d) ** 2, 0, .14))


def s_shock_hit():
    # A charged blow landing: THUD (a heavy body blow) + KRAK (the discharge's crack) + KZZZT (a dense buzzing
    # crackle dying away), a bright zap sweep. Variants are pitched copies; the server also varies the pitch.
    d = 0.9
    thud = mix(d, (thump(d, 115, 44, 0.12), 0, 1.3), (lowpass(noise(d, 961), 700) * env_ad(d, 0.001, 0.035), 0, .9))
    krak = mix(0.1, (highpass(noise(0.1, 962), 2000) * env_ad(0.1, 0.0002, 0.01), 0, 1.3), (crack(0.06, 963, 1200), 0, .8))
    kz = crackle(0.5, 900, 1200, 10000, seed=964, decay=0.14) + zap_buzz(0.5, 150, 965, 0.4) * env_ad(0.5, 0.002, 0.13) * .6
    sweep = sine_sweep(0.15, 6500, 1800, 0.5) * env_ad(0.15, 0.0005, 0.03)
    return reverb(mix(d, (thud, 0, 1), (krak, 0.002, 1), (kz, 0.008, .85), (sweep, 0.004, .35)), 0.4, 0.7, 0.14)


def s_shock_miss():
    # A charged miss: a short electric crack in the air before the fist, a little zap, a puff of air.
    d = 0.4
    return mix(d, (crackle(0.18, 260, 1800, 10000, seed=971, decay=0.04), 0, .7), (crack(0.04, 972, 2800), 0, .7),
               (zap_buzz(0.15, 170, 973) * env_ad(0.15, 0.001, 0.04), 0.005, .4), (whoosh(0.3, 600, 2600, 2, 0.3, seed=974), 0, .35))


def s_shock_empty():
    # The charge runs out: the hum's pitch falls away, sputtering crackle with gaps, a dull click.
    d = 0.9
    t = t_axis(d)
    fall = sine_sweep(0.7, 1500, 110, 0.6) * env_ad(0.7, 0.003, 0.25)
    gate = (np.sin(2 * np.pi * 9 * t) > 0.2) * env_ad(d, 0.002, 0.3)
    sput = crackle(d, 400, 1500, 9000, seed=981) * gate
    return mix(d, (fall, 0, .6), (sput, 0, .7), ((np.sin(2 * np.pi * 120 * t) * env_ad(d, 0.002, 0.2)), 0, .35), (tick_click(982, 1400), 0.6, .5))


def s_shock_ready():
    # Charged again: a quick rising chirp, a crackle running over the plates, a small bright ping.
    d = 0.75
    t = t_axis(d)
    rise = sine_sweep(0.35, 400, 2600, 1.3) * np.clip(t[:int(0.35 * SR)] / 0.35, 0, 1) ** 1.5
    return mix(d, (rise, 0, .6), (crackle(0.4, 300, 2500, 10000, seed=991, decay=0.15), 0.25, .7),
               (clink(2400, 992, 0.4) * env_ad(0.4, 0.0005, 0.08), 0.33, .4), (thump(0.3, 160, 80, 0.06), 0.32, .5))


def s_shock_unequip():
    # Coming off: the charge winds down, the last arcs, the final KZZZT discharge (0.3 s: tick 6), the locks release
    # with clicks and servos, a last clunk.
    d = 1.0
    t = t_axis(d)
    wind = sine_sweep(0.32, 1700, 260, 0.6) * env_ad(0.32, 0.003, 0.12)
    arcs = crackle(0.3, 120, 2500, 9000, seed=1001) * np.linspace(1, .3, int(0.3 * SR))
    kz = crackle(0.22, 1000, 1200, 10000, seed=1002, decay=0.07) + zap_buzz(0.22, 130, 1003, 0.4) * env_ad(0.22, 0.001, 0.07) * .7
    layers = [(wind, 0, .5), (arcs, 0, .35), (kz, 0.3, 1.1), (crack(0.04, 1004, 2200), 0.3, .9)]
    for off, p, sd in ((0.44, 1.0, 1011), (0.54, 1.07, 1021)):
        layers += [(tick_click(sd, 2100 * p), off, .6), (servo(0.2, 1200 * p, 560 * p, sd + 1), off + 0.03, .35)]
    layers += [(lock_clunk(1031, 360), 0.72, .8)]
    return mix(d, *layers)


# ---------------------------------------------------------------- Iceman: ice
def glass_ring(base, seed, dur=0.6, bright=1.0):
    # Ice/glass: inharmonic partials of a thin plate, bright and quick, a little longer for the low ones.
    return modal(dur, base, [1, 2.32, 4.25, 6.63, 9.38], [0.16, 0.09, 0.05, 0.03, 0.02],
                 [1, .7 * bright, .5 * bright, .3 * bright, .2 * bright], strike=0.0006, seed=seed)


def ice_tinkle(dur, count, seed, lo=2500, hi=9000, start=0.03):
    # Small ice bits falling and touching: a scatter of high glassy pings, sparser over time.
    r = np.random.default_rng(seed)
    out = np.zeros(int(dur * SR))
    for i in range(count):
        at = start + (dur - start - 0.12) * (r.random() ** 1.6)
        c = glass_ring(r.uniform(lo, hi) / 2.3, int(r.integers(0, 1e6)), 0.22) * r.uniform(0.1, 0.5)
        s = int(at * SR)
        seg = c[:len(out) - s]
        out[s:s + len(seg)] += seg
    return out


def ice_creak(dur, f0, f1, seed, rough=0.5):
    # Ice under stress: a slow stick-slip groan (a sawtooth whose pitch wobbles) through body resonances.
    t = t_axis(dur)
    r = np.random.default_rng(seed)
    wob = 1 + 0.08 * np.sin(2 * np.pi * 3.3 * t + r.uniform(0, 6)) + 0.05 * np.sin(2 * np.pi * 7.1 * t)
    f = f0 * (f1 / f0) ** (t / dur) * wob
    saw = signal.sawtooth(2 * np.pi * np.cumsum(f) / SR)
    jitter = (r.random(len(t)) < 0.002 * rough) * r.uniform(-1, 1, len(t))
    x = resonator(saw + jitter * 8, f0 * 3, 6) + resonator(saw, f0 * 7.3, 9) * .6 + bandpass(saw, 300, 2500) * .3
    return x


def growth(dur, seed, lo=3000, hi=11000, rise=1.0, density=(200, 1600)):
    # Crystals growing: a dense rising sparkle of tiny clicks, thickening as it grows.
    r = np.random.default_rng(seed)
    n = int(dur * SR)
    x = np.zeros(n)
    t = 0
    while t < n:
        k = (t / n) ** rise
        rate = density[0] + (density[1] - density[0]) * k
        x[t] = r.uniform(.3, 1) * r.choice([-1, 1])
        t += max(1, int(SR / rate * r.exponential(1)))
    x = bandpass(x, lo, hi, 2) * 5
    return x


def s_ice_crack():
    # KRK: a sharp split, a glassy snap ringing, a short creak behind it.
    d = 0.7
    snap = mix(0.1, (highpass(noise(0.1, 2001), 2500) * env_ad(0.1, 0.0002, 0.006), 0, 1.3), (crack(0.05, 2002, 1500), 0.002, .8))
    ring = glass_ring(1900, 2003, 0.5) * .5
    tick = crackle(0.25, 260, 2500, 10000, seed=2004, decay=0.06)
    return reverb(mix(d, (snap, 0, 1), (ring, 0.001, .45), (tick, 0.004, .5), (ice_creak(0.3, 90, 70, 2005) * env_ad(0.3, 0.01, 0.08), 0.01, .25)), 0.4, 0.6, 0.12)


def s_ice_shatter():
    # Ice breaking apart: crack, the burst of pieces, the tinkling of the fragments falling, a short airy flash.
    d = 1.6
    snap = mix(0.12, (highpass(noise(0.12, 2011), 2000) * env_ad(0.12, 0.0002, 0.01), 0, 1.4), (crack(0.06, 2012, 1200), 0, 1))
    burst = bandpass(noise(0.4, 2013), 1500, 9000) * env_ad(0.4, 0.001, 0.06)
    body = lowpass(noise(0.3, 2014), 900) * env_ad(0.3, 0.001, 0.05)
    rings = sum(glass_ring(b, 2015 + i, 0.6) * g for i, (b, g) in enumerate(((1400, .6), (2300, .45), (3100, .35))))
    fall = ice_tinkle(1.5, 46, 2016)
    return reverb(mix(d, (snap, 0, 1), (burst, 0.001, .9), (body, 0, .6), (rings, 0.002, .5), (fall, 0.06, .9)), 0.5, 0.9, 0.18)


def s_ice_form():
    # A weapon of ice forming in the hand: moisture drawn in (a reversed hiss), crystals growing (rising sparkle and
    # a shimmer gliding up), closing with a clear chime.
    d = 0.75
    t = t_axis(0.6)
    draw = bandpass(noise(0.6, 2021), 2500, 9000) * (t / 0.6) ** 2.2 * .6
    grow = growth(0.6, 2022, rise=1.3) * (t / 0.6) ** 1.2
    shimmer = (sine_sweep(0.6, 2200, 5200, 1.4) + .5 * sine_sweep(0.6, 3300, 7100, 1.3)) * (t / 0.6) ** 2 * .12
    chime = glass_ring(2600, 2023, 0.5) * .6
    return reverb(mix(d, (draw, 0, 1), (grow, 0, .8), (shimmer, 0, 1), (chime, 0.56, 1), (crack(0.03, 2024, 3000), 0.56, .6)), 0.4, 0.7, 0.2)


def s_ice_form_big():
    # A big mass of ice forming: a low groan of ice under pressure, crunching growth, a deep settle.
    d = 1.2
    t = t_axis(1.0)
    groan = ice_creak(1.0, 70, 52, 2031) * np.sin(np.pi * t / 1.0) ** 1.5
    crunch = growth(1.0, 2032, 900, 6000, rise=0.8, density=(300, 1400)) * np.sin(np.pi * np.minimum(1, t / 0.9)) ** 0.8
    settle = thump(0.4, 90, 45, 0.12)
    return reverb(mix(d, (groan, 0, .5), (crunch, 0, .9), (settle, 0.8, .8), (crack(0.05, 2033, 1500), 0.82, .6), (glass_ring(900, 2034, 0.5), 0.82, .35)), 0.6, 1.0, 0.22)


def s_ice_sculpt():
    # Ice growing in the air along the brush: a short bright crystal growth chirp.
    d = 0.4
    t = t_axis(0.3)
    g = growth(0.3, 2041, 3500, 12000, rise=0.6, density=(600, 2200)) * np.sin(np.pi * t / 0.3)
    s = sine_sweep(0.3, 3000, 6000, 1.0) * np.sin(np.pi * t / 0.3) ** 2 * .08
    return reverb(mix(d, (g, 0, 1), (s, 0, 1), (glass_ring(3400, 2042, 0.3), 0.18, .25)), 0.3, 0.5, 0.2)


def s_frost():
    # A breath of frost: a cold hiss and a few crisp ticks of rime forming.
    d = 0.5
    t = t_axis(0.45)
    hiss = bandpass(noise(0.45, 2051), 3000, 11000) * np.sin(np.pi * t / 0.45) ** 1.5
    return mix(d, (hiss, 0, .6), (crackle(0.4, 90, 4000, 11000, seed=2052, decay=0.2), 0.03, .5))


def s_deep_freeze():
    # Frozen solid in an instant: a rushing crackle closing in (fast growth), a thick glassy knock, a deep ring.
    d = 1.3
    t = t_axis(0.35)
    rush = growth(0.35, 2061, 1200, 9000, rise=0.5, density=(800, 4000)) * (t / 0.35) ** 0.7
    hiss = swept(noise(0.35, 2062), 1500, 9000, 1.5) * (t / 0.35) ** 2
    knock = mix(0.4, (thump(0.4, 140, 60, 0.07), 0, 1.2), (lowpass(noise(0.4, 2063), 1500) * env_ad(0.4, 0.001, 0.03), 0, .6))
    ring = glass_ring(620, 2064, 1.0, 0.7) + pad(glass_ring(1250, 2065, 0.8), 1.0) * .5
    return reverb(mix(d, (rush, 0, .9), (hiss, 0, .5), (knock, 0.34, 1), (crack(0.05, 2066, 1500), 0.34, .9), (ring, 0.345, .45)), 0.7, 1.2, 0.25)


def s_ice_hit():
    # An ice weapon striking a body: a dull heavy knock, a crunch of ice, a short bright chip.
    d = 0.5
    knock = mix(0.3, (thump(0.3, 150, 60, 0.06), 0, 1.2), (lowpass(noise(0.3, 2071), 1200) * env_ad(0.3, 0.001, 0.025), 0, .8))
    crunch = crackle(0.15, 900, 1500, 8000, seed=2072, decay=0.04)
    return mix(d, (knock, 0, 1), (crunch, 0.002, .7), (glass_ring(2100, 2073, 0.3), 0.003, .3), (crack(0.03, 2074, 2500), 0, .6))


def s_slide_start():
    # Stepping onto the forming slide: a crisp rushing whoosh, the ice crunching into being under him.
    d = 0.8
    return reverb(mix(d, (whoosh(0.7, 500, 3800, 1.6, 0.3, seed=2081, body=0.3), 0, 1),
                      (growth(0.5, 2082, 1500, 9000, rise=0.6, density=(500, 1800)), 0, .7),
                      (glass_ring(1700, 2083, 0.5), 0.05, .3)), 0.5, 0.8, 0.18)


def s_slide():
    # Surfing on ice (a loop): a smooth hiss of the blade-like glide, a soft low rumble, sparse ice ticks.
    d = 2.0
    n = int(d * SR)
    t = t_axis(d)
    # Built to loop: the noises are circularly filtered (FFT band-limit) so the seam is clean.
    def looped(lo, hi, seed):
        x = np.random.default_rng(seed).standard_normal(n)
        X = np.fft.rfft(x)
        fr = np.fft.rfftfreq(n, 1 / SR)
        X[(fr < lo) | (fr > hi)] = 0
        y = np.fft.irfft(X, n)
        return y / (np.max(np.abs(y)) + 1e-9)
    glide = looped(2500, 7000, 2091) * (0.8 + 0.2 * np.sin(2 * np.pi * t / d * 4))
    rumble_l = looped(40, 220, 2092)
    ticks = np.zeros(n)
    r = np.random.default_rng(2093)
    for at in r.integers(0, n - 4000, 26):
        c = glass_ring(r.uniform(1500, 3500), int(r.integers(0, 1e6)), 0.08)[:3500] * r.uniform(.1, .3)
        ticks[at:at + len(c)] += c
    return glide * .55 + rumble_l * .5 + ticks


def s_dash():
    # The sub-zero slide: a fast low whoosh, ice scraping along the ground.
    d = 0.7
    return mix(d, (whoosh(0.6, 250, 2500, 1.7, 0.25, seed=2101, body=0.5), 0, 1),
               (bandpass(noise(0.55, 2102), 2000, 8000) * env_ad(0.55, 0.01, 0.2) * (1 + 0.5 * np.sin(2 * np.pi * 30 * t_axis(0.55))), 0.02, .45),
               (growth(0.4, 2103, 2000, 9000, rise=0.3, density=(1500, 500)), 0.02, .4))


def s_mace_swing():
    # A heavy ice mace swung: a big low whoosh with weight.
    d = 0.6
    return mix(d, (whoosh(0.55, 120, 900, 1.5, 0.5, seed=2111, body=0.8), 0, 1), (crackle(0.3, 40, 3000, 9000, seed=2112, decay=0.1), 0.2, .15))


def s_mace_slam():
    # The mace into the ground: a deep boom, the ground cracking, ice bursting, pieces raining.
    d = 2.0
    boom = mix(0.9, (thump(0.9, 95, 32, 0.25), 0, 1.4), (lowpass(noise(0.9, 2121), 500) * env_ad(0.9, 0.002, 0.15), 0, 1))
    split = mix(0.15, (highpass(noise(0.15, 2122), 1500) * env_ad(0.15, 0.0003, 0.015), 0, 1.2), (crack(0.06, 2123, 900), 0, 1))
    return reverb(mix(d, (boom, 0, 1), (split, 0.003, .9), (rumble(1.4, 0.4, 140, 2124), 0.02, .7),
                      (ice_tinkle(1.7, 40, 2125, 1500, 7000), 0.1, .7), (glass_ring(800, 2126, 0.8), 0.004, .4)), 0.8, 1.4, 0.24)


def s_spear_thrust():
    # A fast thrust: a short sharp swish, high and narrow.
    d = 0.3
    return mix(d, (whoosh(0.25, 1200, 5500, 2.5, 0.35, seed=2131), 0, 1), (glass_ring(3800, 2132, 0.2), 0.08, .12))


def s_spear_throw():
    # The spear thrown hard: a long tearing whoosh with a high whistle.
    d = 0.8
    t = t_axis(0.7)
    whistle = sine_sweep(0.7, 2600, 1700, 1.0) * np.sin(np.pi * t / 0.7) ** 2 * .15
    return mix(d, (whoosh(0.7, 300, 4200, 1.6, 0.3, seed=2141, body=0.4), 0, 1), (whistle, 0, 1))


def s_spikes():
    # Ice spikes bursting out of the ground: a rising crunch, a volley of cracks, pieces falling.
    d = 1.4
    t = t_axis(0.25)
    rise = growth(0.25, 2151, 700, 7000, rise=0.4, density=(1500, 4000)) * (t / 0.25)
    layers = [(rise, 0, .8), (thump(0.4, 120, 50, 0.1), 0.24, 1.1), (rumble(0.8, 0.25, 180, 2152), 0.2, .5)]
    r = np.random.default_rng(2153)
    for i in range(6):
        layers.append((crack(0.06, 2154 + i, 1200 + 300 * i), 0.24 + r.uniform(0, 0.09), r.uniform(.5, .9)))
    layers.append((ice_tinkle(1.1, 26, 2160, 2000, 8000), 0.3, .6))
    return reverb(mix(d, *layers), 0.6, 1.0, 0.2)


def s_sword_swing():
    # An ice sword cut: a clean swish with a cold glassy ring in it.
    d = 0.45
    return mix(d, (whoosh(0.4, 700, 4500, 2.0, 0.4, seed=2171), 0, 1), (glass_ring(2900, 2172, 0.35) * env_ad(0.35, 0.05, 0.1), 0.08, .18))


def s_sword_spin():
    # The spin: whirling air, swishes coming round, snow and ice ticks thrown off.
    d = 0.9
    t = t_axis(0.85)
    whirl = swept(noise(0.85, 2181), 500, 2600, 1.5) * (0.5 + 0.5 * np.sin(2 * np.pi * 9 * t) ** 2) * np.sin(np.pi * t / 0.85)
    return mix(d, (whirl, 0, 1), (ice_tinkle(0.85, 12, 2182, 3000, 9000), 0.1, .4))


def s_shell_form():
    # The shell closing over him from the feet up: crunching growth rising from low to high, a heavy settle and a ring.
    d = 1.4
    t = t_axis(0.95)
    crunch = growth(0.95, 2191, 600, 9000, rise=0.7, density=(400, 2600))
    crunch = swept(crunch, 900, 6000, 1.2) * 3 * (t / 0.95) ** 0.6
    groan = ice_creak(0.95, 60, 85, 2192) * np.sin(np.pi * t / 0.95) * .4
    return reverb(mix(d, (crunch, 0, 1), (groan, 0, 1), (thump(0.4, 110, 50, 0.1), 0.9, 1), (glass_ring(700, 2193, 0.8), 0.9, .4)), 0.6, 1.1, 0.24)


def s_shell_hit():
    # A blow on the thick shell: a dull, dense knock of thick ice, a little crunch.
    d = 0.6
    return reverb(mix(d, (thump(0.4, 170, 75, 0.08), 0, 1.2), (lowpass(noise(0.3, 2201), 1000) * env_ad(0.3, 0.001, 0.03), 0, .8),
                      (glass_ring(480, 2202, 0.5, 0.5), 0, .35), (crackle(0.15, 400, 1500, 6000, seed=2203, decay=0.05), 0.005, .4)), 0.4, 0.6, 0.15)


def s_shell_break():
    # The shell giving way: a long splitting crack, the whole mass bursting into big pieces, pieces raining down.
    d = 2.2
    split = mix(0.5, (ice_creak(0.35, 85, 50, 2211) * env_ad(0.35, 0.02, 0.15), 0, .6), (crackle(0.35, 600, 1200, 7000, seed=2212) * np.linspace(.3, 1, int(0.35 * SR)), 0, .8))
    burst = mix(0.6, (highpass(noise(0.15, 2213), 1500) * env_ad(0.15, 0.0002, 0.02), 0, 1.3), (thump(0.6, 110, 40, 0.15), 0, 1.2),
                (bandpass(noise(0.5, 2214), 800, 7000) * env_ad(0.5, 0.001, 0.09), 0, .8))
    return reverb(mix(d, (split, 0, 1), (burst, 0.33, 1), (ice_tinkle(1.8, 60, 2215, 1200, 7000), 0.38, .9), (rumble(1.0, 0.3, 150, 2216), 0.33, .6)), 0.7, 1.3, 0.22)


def s_shell_stress():
    # Inside the shell before the burst: ice groaning under rising pressure, crackles multiplying, a swelling rumble.
    d = 0.75
    t = t_axis(0.7)
    groan = ice_creak(0.7, 55, 120, 2221, rough=2.0) * (t / 0.7) ** 1.5
    cr = crackle(0.7, 900, 1500, 9000, seed=2222) * (t / 0.7) ** 2
    rum = lowpass(noise(0.7, 2223), 120, 4) * (t / 0.7) ** 1.8 * 3
    return mix(d, (groan, 0, .7), (cr, 0, .6), (rum, 0, .9))


def s_shell_burst():
    # KRAAAK: the shell explodes outward: a huge crack, a boom, a wall of ice pieces flying and raining.
    d = 2.6
    krak = mix(0.25, (highpass(noise(0.25, 2231), 1200) * env_ad(0.25, 0.0002, 0.04), 0, 1.5), (crack(0.08, 2232, 700), 0, 1.2))
    boom = mix(1.0, (thump(1.0, 100, 30, 0.3), 0, 1.5), (lowpass(noise(1.0, 2233), 600) * env_ad(1.0, 0.002, 0.18), 0, 1.1))
    wall = bandpass(noise(0.8, 2234), 1000, 9000) * env_ad(0.8, 0.002, 0.2)
    return reverb(mix(d, (krak, 0, 1), (boom, 0, 1), (wall, 0.005, .9), (ice_tinkle(2.4, 90, 2235, 1200, 8000), 0.12, 1),
                      (rumble(1.8, 0.5, 130, 2236), 0.02, .8)), 0.9, 1.6, 0.26)


def s_brush():
    # The cryogenic stream (a loop): a steady cold rush of mist, crystalline sparkle riding on it.
    d = 2.0
    n = int(d * SR)
    t = t_axis(d)
    def looped(lo, hi, seed):
        x = np.random.default_rng(seed).standard_normal(n)
        X = np.fft.rfft(x)
        fr = np.fft.rfftfreq(n, 1 / SR)
        X[(fr < lo) | (fr > hi)] = 0
        y = np.fft.irfft(X, n)
        return y / (np.max(np.abs(y)) + 1e-9)
    rush = looped(800, 6000, 2241) * (0.85 + 0.15 * np.sin(2 * np.pi * t / d * 6))
    air = looped(150, 700, 2242)
    sparkle = np.zeros(n)
    r = np.random.default_rng(2243)
    for at in r.integers(0, n - 3000, 120):
        c = glass_ring(r.uniform(3000, 6000), int(r.integers(0, 1e6)), 0.06)[:2600] * r.uniform(.05, .18)
        sparkle[at:at + len(c)] += c
    return rush * .6 + air * .35 + sparkle


def s_ground_crack():
    # Shattered ground: hands to the ground, a low tearing rumble running away under the earth, cracks along it.
    d = 1.6
    t = t_axis(1.4)
    rum = lowpass(noise(1.4, 2251), 160, 4) * np.sin(np.pi * np.minimum(1, t / 1.3)) * 3
    tear = swept(noise(1.4, 2252), 300, 1800, 1.2) * np.sin(np.pi * np.minimum(1, t / 1.3)) ** 1.5
    layers = [(rum, 0, .9), (tear, 0, .45), (thump(0.3, 120, 55, 0.08), 0, .8)]
    r = np.random.default_rng(2253)
    for i in range(9):
        layers.append((crack(0.05, 2254 + i, 1000 + 200 * i), 0.05 + i * 0.13 + r.uniform(0, .05), r.uniform(.25, .55)))
    return reverb(mix(d, *layers), 0.6, 1.0, 0.18)


SOUNDS = {
    'fx': {'whoosh_light': s_whoosh_light, 'whoosh_heavy': s_whoosh_heavy, 'impact_heavy': s_impact_heavy, 'impact_metal': s_impact_metal,
           'electric_zap': s_electric_zap, 'electric_crackle': s_electric_crackle, 'energy_swell': s_energy_swell, 'energy_boom': s_energy_boom},
    'magneto': {'magnetic_hum': s_magnetic_hum, 'metal_rise': s_metal_rise, 'metal_clang_big': s_metal_clang_big, 'metal_shing': s_metal_shing,
                'shield_up': s_shield_up, 'shield_hit': s_shield_hit, 'shield_burst': s_shield_burst, 'telekinesis_grab': s_telekinesis_grab,
                'fling': s_fling, 'fist_assemble': s_fist_assemble, 'fist_slam': s_fist_slam, 'rod_whistle': s_rod_whistle, 'rod_impale': s_rod_impale},
    'panther': {'claw_slash': s_claw_slash, 'claw_hit': s_claw_hit, 'vibranium_absorb': s_vibranium_absorb, 'kinetic_release': s_kinetic_release,
                'pounce': s_pounce, 'kick_impact': s_kick_impact, 'camo_on': s_camo_on, 'camo_off': s_camo_off, 'dash': s_dash},
    'thor': {'hammer_whoosh': s_hammer_whoosh, 'hammer_impact': s_hammer_impact, 'lightning_crackle': s_lightning_crackle, 'hammer_catch': s_hammer_catch},
    'hulk': {'hulk_punch': s_hulk_punch, 'ground_slam': s_ground_slam, 'thunderclap': s_thunderclap, 'leap': s_leap},
    'zed': {'blade_slash': s_blade_slash, 'shadow_whoosh': s_shadow_whoosh, 'shuriken_whir': s_shuriken_whir, 'mark_burst': s_mark_burst},
    'cyclops': {'optic_beam': s_optic_beam, 'optic_blast': s_optic_blast},
    'ghost': {'chain_whip': s_chain_whip, 'hellfire': s_hellfire},
    'sandman': {'sand_whoosh': s_sand_whoosh, 'sand_impact': s_sand_impact},
    'batman': {'punch': s_bat_punch, 'batarang': s_bat_batarang, 'grapnel': s_bat_grapnel, 'smoke': s_bat_smoke, 'flash': s_bat_flash, 'flash_bounce': s_bat_flash_bounce, 'flash_ring': s_bat_flash_ring,
               'td_signal': s_td_signal, 'td_abort': s_td_abort, 'td_tracker': s_td_tracker, 'td_lock': s_td_lock,
               'bm_engine': s_bm_engine, 'bm_drift': s_bm_drift, 'bm_boost': s_bm_boost, 'bm_gun': s_bm_gun,
               'mine': s_bat_mine, 'cape': s_bat_cape,
               'cannon_deploy': s_cannon_deploy, 'cannon_charge': s_cannon_charge, 'cannon_shot': s_cannon_shot, 'cannon_hum': s_cannon_hum,
               'cannon_final': s_cannon_final, 'cannon_stop': s_cannon_stop, 'cannon_retract': s_cannon_retract,
               'sonic_beep': s_sonic_beep, 'sonic_rumble': s_sonic_rumble, 'sonic_rise': s_sonic_rise, 'sonic_lock': s_sonic_lock,
               'sonic_hum': s_sonic_hum, 'sonic_pulse': s_sonic_pulse, 'sonic_hit': s_sonic_hit, 'sonic_ring': s_sonic_ring,
               'sonic_break': s_sonic_break, 'sonic_retract': s_sonic_retract,
               'shock_equip': s_shock_equip, 'shock_clap': s_shock_clap, 'shock_hum': s_shock_hum, 'shock_swing': s_shock_swing,
               'shock_hit': s_shock_hit, 'shock_miss': s_shock_miss, 'shock_empty': s_shock_empty, 'shock_ready': s_shock_ready,
               'shock_unequip': s_shock_unequip},
    'iceman': {'crack': s_ice_crack, 'shatter': s_ice_shatter, 'form': s_ice_form, 'form_big': s_ice_form_big, 'sculpt': s_ice_sculpt,
               'frost': s_frost, 'deep_freeze': s_deep_freeze, 'hit': s_ice_hit, 'slide_start': s_slide_start, 'slide': s_slide,
               'dash': s_dash, 'mace_swing': s_mace_swing, 'mace_slam': s_mace_slam, 'spear_thrust': s_spear_thrust,
               'spear_throw': s_spear_throw, 'spikes': s_spikes, 'sword_swing': s_sword_swing, 'sword_spin': s_sword_spin,
               'shell_form': s_shell_form, 'shell_hit': s_shell_hit, 'shell_break': s_shell_break, 'shell_stress': s_shell_stress,
               'shell_burst': s_shell_burst, 'brush': s_brush, 'ground_crack': s_ground_crack},
}
# A few sounds get pitch/time variants so repeats never sound identical.
VARIANTS = {'crack': 3, 'shatter': 2, 'hit': 3, 'spear_thrust': 2, 'sword_swing': 2, 'sculpt': 2, 'bm_gun': 3, 'cannon_shot': 3, 'shock_hit': 3, 'shock_miss': 2, 'shock_swing': 2, 'punch': 3, 'batarang': 2, 'claw_slash': 3, 'blade_slash': 3, 'whoosh_light': 2, 'claw_hit': 2, 'hulk_punch': 2, 'shield_hit': 2, 'electric_zap': 2, 'metal_shing': 2}


def resample(x, factor):
    n = int(len(x) / factor)
    return signal.resample(x, n)


# Looping sounds are written whole (no trimming or fades, which would leave a seam at the loop point).
LOOPS = {'slide', 'brush', 'cannon_hum', 'sonic_hum', 'sonic_ring', 'shock_hum', 'flash_ring'}


def write_ogg(path, x, loop=False):
    x = normalise(x, -3) if loop else fade(trim(normalise(soft(normalise(x, 0), 1.2))), 0.03)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as tmp:
        wav = tmp.name
    with wave.open(wav, 'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes((np.clip(x, -1, 1) * 32767).astype(np.int16).tobytes())
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', wav, '-c:a', 'libvorbis', '-q:a', '5', path], check=True)
    os.remove(wav)


def main():
    groups = sys.argv[1:] or list(SOUNDS)
    manifest_path = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'superheromod', 'sounds.json')
    manifest = {}
    for group, sounds in SOUNDS.items():
        for name, fn in sounds.items():
            n = VARIANTS.get(name, 1)
            files = [f'superheromod:{group}/{name}' + ('' if i == 0 else f'_{i}') for i in range(n)]
            manifest[f'{group}.{name}'] = {'subtitle': f'subtitles.superheromod.{group}.{name}', 'sounds': files}
            if group not in groups:
                continue
            base = fn()
            for i in range(n):
                x = base if i == 0 else resample(base, 1 + (0.06 if i == 1 else -0.05))
                write_ogg(os.path.join(OUT, group, name + ('' if i == 0 else f'_{i}') + '.ogg'), x, name in LOOPS)
            print(group, name, f'{len(base) / SR:.2f}s', 'x' + str(n))
    with open(manifest_path, 'w') as f:
        json.dump(manifest, f, indent=2)


if __name__ == '__main__':
    main()
