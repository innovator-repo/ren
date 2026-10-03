#!/usr/bin/env python3
"""Synthesizes Ren's three short UI sounds into app/src/main/assets/sfx (soft sine chimes, no samples)."""
import math
import os
import struct
import wave

RATE = 22050
OUT = os.path.join(os.path.dirname(__file__), "..", "..", "app", "src", "main", "assets", "sfx")


def tone(freq, dur, start, amp=0.35, decay=6.0):
    return [(start + i, amp * math.exp(-decay * i / RATE) * math.sin(2 * math.pi * freq * i / RATE)
             + amp * 0.25 * math.exp(-decay * 1.6 * i / RATE) * math.sin(4 * math.pi * freq * i / RATE))
            for i in range(int(dur * RATE))]


def render(name, notes, length):
    buf = [0.0] * int(length * RATE)
    for freq, dur, at, amp, decay in notes:
        for idx, v in tone(freq, dur, int(at * RATE), amp, decay):
            if idx < len(buf):
                buf[idx] += v
    fade = int(0.004 * RATE)  # avoid clicks at the edges
    for i in range(fade):
        buf[i] *= i / fade
        buf[-1 - i] *= i / fade
    with wave.open(os.path.join(OUT, name), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1, min(1, s)) * 32000)) for s in buf))


os.makedirs(OUT, exist_ok=True)
render("correct.wav", [(1046.5, 0.35, 0.0, 0.30, 9), (1318.5, 0.45, 0.07, 0.30, 7)], 0.55)
render("wrong.wav", [(220.0, 0.25, 0.0, 0.40, 14), (196.0, 0.25, 0.09, 0.35, 14)], 0.36)
render("complete.wav", [(784.0, 0.5, 0.0, 0.25, 6), (987.8, 0.5, 0.09, 0.25, 6), (1174.7, 0.6, 0.18, 0.25, 5),
                        (1568.0, 0.8, 0.30, 0.22, 4)], 1.1)
print("ok")
