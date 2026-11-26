"""Synthesises Vibecraft's three notification sounds (soft bells with a short, warm reverb).
Run: python3 tools/make_sounds.py   ->  app/src/main/res/raw/vc_done.wav, vc_ask.wav, vc_problem.wav"""
import wave
import numpy as np

SR = 32000
rng = np.random.default_rng(7)
OUT = "app/src/main/res/raw/"


def bell(freq, dur, amp=1.0, decay=3.2, bright=1.0, attack=0.006):
    n = int(dur * SR)
    t = np.arange(n) / SR
    # ratio, amplitude, decay multiplier: a struck-bell spectrum, higher partials die faster
    parts = [(1.0, 1.0, 1.0), (2.0, 0.32 * bright, 1.6), (2.76, 0.16 * bright, 2.2), (4.07, 0.07 * bright, 3.0), (5.4, 0.03 * bright, 4.0)]
    y = np.zeros(n)
    for ratio, a, dm in parts:
        for detune, gain in ((1.0, 1.0), (1.0025, 0.45)):  # a slightly detuned twin gives a gentle shimmer
            ph = rng.uniform(0, 2 * np.pi)
            y += a * gain * np.sin(2 * np.pi * freq * ratio * detune * t + ph) * np.exp(-t * decay * dm)
    att = 0.5 - 0.5 * np.cos(np.pi * np.minimum(1.0, t / attack))
    return amp * y * att


def mix(notes, total):
    out = np.zeros(int(total * SR))
    for onset, y in notes:
        i = int(onset * SR)
        out[i:i + len(y)] += y[: len(out) - i]
    return out


def lowpass(x, k):
    y = np.zeros_like(x)
    for i in range(1, len(x)):
        y[i] = y[i - 1] + k * (x[i] - y[i - 1])
    return y


def reverb(x, wet=0.2, rt=0.45):
    n = int(rt * SR)
    ir = rng.standard_normal(n) * np.exp(-np.arange(n) / SR * 7.0)
    ir = lowpass(ir, 0.4)
    ir /= np.sqrt((ir ** 2).sum())
    N = 1 << (len(x) + n - 1).bit_length()
    y = np.fft.irfft(np.fft.rfft(x, N) * np.fft.rfft(ir, N), N)[: len(x)]
    y *= np.sqrt((x ** 2).sum() / max((y ** 2).sum(), 1e-9))
    return (1 - wet) * x + wet * y


def finish(x, peak=0.8, fade=0.09):
    x = x - x.mean()
    nf = int(fade * SR)
    x[-nf:] *= np.linspace(1, 0, nf) ** 2
    # the three sounds should feel equally loud: aim at -16 dBFS RMS, but never above the peak limit
    rms = np.sqrt((x ** 2).mean())
    scale = min(0.92 / np.max(np.abs(x)), 10 ** (-16 / 20) / rms)
    return (x * scale * 32767).astype(np.int16)


def write(name, samples):
    with wave.open(OUT + name, "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes(samples.tobytes())


# Done: a bright rising C major arpeggio, like a small success chime.
done = mix([(0.00, bell(523.25, 1.0, 0.8, 3.0)), (0.11, bell(659.25, 1.0, 0.85, 3.0)),
            (0.22, bell(783.99, 1.1, 0.9, 2.8)), (0.36, bell(1046.5, 1.3, 1.0, 2.3))], 1.7)
# Ask: two notes rising by a fourth, an open, question-like "ding-dong".
ask = mix([(0.00, bell(659.25, 0.9, 0.85, 3.4)), (0.20, bell(880.0, 1.1, 1.0, 2.9))], 1.4)
# Problem: two soft falling notes, mellow and calm, never harsh.
problem = mix([(0.00, bell(440.0, 0.9, 0.9, 3.8, bright=0.45, attack=0.012)),
               (0.22, bell(329.63, 1.1, 1.0, 3.3, bright=0.45, attack=0.012))], 1.4)
problem = lowpass(problem, 0.35)

for name, x, wet in (("vc_done.wav", done, 0.22), ("vc_ask.wav", ask, 0.2), ("vc_problem.wav", problem, 0.18)):
    write(name, finish(reverb(x, wet)))
print("sounds written")
