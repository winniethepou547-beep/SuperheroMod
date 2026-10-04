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
}
# A few sounds get pitch/time variants so repeats never sound identical.
VARIANTS = {'claw_slash': 3, 'blade_slash': 3, 'whoosh_light': 2, 'claw_hit': 2, 'hulk_punch': 2, 'shield_hit': 2, 'electric_zap': 2, 'metal_shing': 2}


def resample(x, factor):
    n = int(len(x) / factor)
    return signal.resample(x, n)


def write_ogg(path, x):
    x = fade(trim(normalise(soft(normalise(x, 0), 1.2))), 0.03)
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
                write_ogg(os.path.join(OUT, group, name + ('' if i == 0 else f'_{i}') + '.ogg'), x)
            print(group, name, f'{len(base) / SR:.2f}s', 'x' + str(n))
    with open(manifest_path, 'w') as f:
        json.dump(manifest, f, indent=2)


if __name__ == '__main__':
    main()
