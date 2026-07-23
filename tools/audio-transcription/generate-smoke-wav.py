import math
import pathlib
import struct
import wave


OUTPUT = pathlib.Path(__file__).resolve().parents[2] / "build" / "audio-smoke" / "twinkle-vocal-like.wav"
SAMPLE_RATE = 22_050
MELODY = [60, 60, 67, 67, 69, 69, 67, 65, 65, 64, 64, 62, 62, 60]
DURATIONS = [0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 1.0, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 1.0]


def midi_frequency(note: int) -> float:
    return 440.0 * 2.0 ** ((note - 69) / 12.0)


def main() -> None:
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    samples: list[int] = []
    for note, duration in zip(MELODY, DURATIONS):
        frequency = midi_frequency(note)
        count = int(duration * SAMPLE_RATE)
        for index in range(count):
            time = index / SAMPLE_RATE
            attack = min(1.0, time / 0.025)
            release = min(1.0, (duration - time) / 0.06)
            envelope = max(0.0, min(attack, release))
            vibrato = 1.0 + 0.004 * math.sin(2.0 * math.pi * 5.2 * time)
            fundamental = math.sin(2.0 * math.pi * frequency * vibrato * time)
            second_harmonic = 0.22 * math.sin(2.0 * math.pi * frequency * 2.0 * time)
            value = int(12_000 * envelope * (fundamental + second_harmonic))
            samples.append(max(-32_768, min(32_767, value)))

    with wave.open(str(OUTPUT), "wb") as output:
        output.setnchannels(1)
        output.setsampwidth(2)
        output.setframerate(SAMPLE_RATE)
        output.writeframes(b"".join(struct.pack("<h", sample) for sample in samples))
    print(OUTPUT)


if __name__ == "__main__":
    main()
