from __future__ import annotations

import argparse
import importlib.util
import json
import math
import os
import pathlib
import shutil
import statistics
import subprocess
import sys
import tempfile
from dataclasses import dataclass
from typing import Iterable


@dataclass(frozen=True)
class Note:
    start: float
    end: float
    pitch: int
    amplitude: float
    role: str


PIANO_LOWEST_MIDI = 21  # A0
PIANO_HIGHEST_MIDI = 108  # C8


def progress(message: str) -> None:
    print(f"PROGRESS {message}", flush=True)


def predict_notes(audio_path: pathlib.Path, role: str, high_fidelity: bool = False) -> list[Note]:
    from basic_pitch.inference import predict

    if high_fidelity:
        progress("正在进行 88 键高保真人声识别" if role == "melody" else "正在进行 88 键高保真伴奏识别")
        # Favor pitch precision over aggressive recall: low thresholds turn
        # upper harmonics in dense mixes into false piano notes.
        onset = 0.50 if role == "melody" else 0.55
        frame = 0.32 if role == "melody" else 0.35
        minimum_length = 80.0
        minimum_frequency = None
        maximum_frequency = None
    else:
        progress("正在识别主旋律" if role == "melody" else "正在识别伴奏音符")
        onset = 0.48 if role == "melody" else 0.62
        frame = 0.30 if role == "melody" else 0.42
        minimum_length = 90.0 if role == "melody" else 130.0
        minimum_frequency = 82.0
        maximum_frequency = 2094.0
    _, _, events = predict(
        str(audio_path),
        onset_threshold=onset,
        frame_threshold=frame,
        minimum_note_length=minimum_length,
        minimum_frequency=minimum_frequency,
        maximum_frequency=maximum_frequency,
        multiple_pitch_bends=False,
    )
    return [
        Note(float(start), float(end), int(pitch), float(amplitude), role)
        for start, end, pitch, amplitude, _ in events
        if end > start
        and (
            PIANO_LOWEST_MIDI <= pitch <= PIANO_HIGHEST_MIDI
            if high_fidelity
            else 0 <= pitch <= 127
        )
    ]


def estimate_beats(audio_path: pathlib.Path) -> tuple[float, list[float], float]:
    import librosa
    import numpy as np

    progress("正在分析节拍")
    audio, sample_rate = librosa.load(str(audio_path), sr=22050, mono=True)
    duration = float(librosa.get_duration(y=audio, sr=sample_rate))
    tempo, beat_frames = librosa.beat.beat_track(y=audio, sr=sample_rate)
    tempo_values = np.asarray(tempo).reshape(-1)
    bpm = float(tempo_values[0]) if tempo_values.size else 120.0
    if not math.isfinite(bpm) or bpm < 40.0 or bpm > 240.0:
        bpm = 120.0
    beats = [float(value) for value in librosa.frames_to_time(beat_frames, sr=sample_rate)]
    if len(beats) < 2:
        beat_seconds = 60.0 / bpm
        beats = [index * beat_seconds for index in range(max(1, math.ceil(duration / beat_seconds)))]
    return bpm, beats, duration


def separate(audio_path: pathlib.Path, work_root: pathlib.Path) -> tuple[pathlib.Path, pathlib.Path] | None:
    if importlib.util.find_spec("demucs") is None:
        progress("未安装人声分离模型，将使用整曲智能简化")
        return None

    progress("正在分离人声与伴奏，首次运行会加载模型")
    ffmpeg_root = pathlib.Path(sys.executable).resolve().parent / "ffmpeg"
    ffmpeg_bins = [item.parent for item in ffmpeg_root.glob("**/avcodec-62.dll")] if ffmpeg_root.is_dir() else []
    if ffmpeg_bins:
        os.environ["PATH"] = str(ffmpeg_bins[0]) + os.pathsep + os.environ.get("PATH", "")
    command = [
        sys.executable,
        "-m",
        "demucs.separate",
        "--two-stems=vocals",
        "--name",
        "htdemucs",
        "--out",
        str(work_root),
        str(audio_path),
    ]
    process = subprocess.run(command, text=True, encoding="utf-8", errors="replace", capture_output=True)
    stem_root = work_root / "htdemucs" / audio_path.stem
    vocals = stem_root / "vocals.wav"
    accompaniment = stem_root / "no_vocals.wav"
    if process.returncode != 0 or not vocals.is_file() or not accompaniment.is_file():
        details = (process.stdout + "\n" + process.stderr).strip().splitlines()[-3:]
        progress("人声分离失败，回退为整曲智能简化：" + " / ".join(details))
        return None
    return vocals, accompaniment


def nearest_grid(value: float, grid: list[float], maximum_shift: float = 0.11) -> float:
    if not grid:
        return value
    nearest = min(grid, key=lambda candidate: abs(candidate - value))
    return nearest if abs(nearest - value) <= maximum_shift else value


def subdivision_grid(beats: list[float]) -> list[float]:
    if len(beats) < 2:
        return beats
    grid: list[float] = []
    for left, right in zip(beats, beats[1:]):
        grid.extend((left, (left + right) / 2.0))
    grid.append(beats[-1])
    return grid


def melody_line(events: Iterable[Note], grid: list[float]) -> list[Note]:
    ordered = sorted(events, key=lambda note: (note.start, -note.amplitude, -note.pitch))
    groups: list[list[Note]] = []
    for event in ordered:
        if not groups or event.start - groups[-1][0].start > 0.065:
            groups.append([event])
        else:
            groups[-1].append(event)

    selected: list[Note] = []
    previous_pitch: int | None = None
    for group in groups:
        def score(note: Note) -> float:
            jump_penalty = 0.0 if previous_pitch is None else min(abs(note.pitch - previous_pitch), 24) * 0.009
            range_penalty = 0.12 if note.pitch < 48 else 0.0
            return note.amplitude - jump_penalty - range_penalty

        choice = max(group, key=score)
        snapped = nearest_grid(choice.start, grid)
        selected.append(
            Note(snapped, max(snapped + 0.09, choice.end), choice.pitch, choice.amplitude, "melody")
        )
        previous_pitch = choice.pitch

    merged: list[Note] = []
    for note in selected:
        if merged and note.pitch == merged[-1].pitch and note.start - merged[-1].end <= 0.11:
            previous = merged[-1]
            merged[-1] = Note(previous.start, max(previous.end, note.end), note.pitch, max(previous.amplitude, note.amplitude), "melody")
        else:
            merged.append(note)
    return merged


def accompaniment_pulses(events: Iterable[Note], beats: list[float]) -> list[Note]:
    notes = list(events)
    pulses: list[Note] = []
    for index, beat in enumerate(beats):
        active = [note for note in notes if note.start - 0.08 <= beat < note.end + 0.04]
        if not active:
            continue
        active.sort(key=lambda note: (-note.amplitude, note.pitch))
        chosen: list[Note] = []
        for candidate in active:
            if any(candidate.pitch % 12 == existing.pitch % 12 for existing in chosen):
                continue
            chosen.append(candidate)
            if len(chosen) == 2:
                break
        next_beat = beats[index + 1] if index + 1 < len(beats) else beat + 0.45
        end = beat + max(0.09, min(next_beat - beat, 0.55) * 0.72)
        pulses.extend(Note(beat, end, note.pitch, note.amplitude * 0.72, "accompaniment") for note in chosen)
    return pulses


def deduplicate(notes: Iterable[Note]) -> list[Note]:
    output: list[Note] = []
    for note in sorted(notes, key=lambda value: (value.start, value.role != "melody", -value.amplitude)):
        duplicate = next(
            (
                existing
                for existing in output[-8:]
                if abs(existing.start - note.start) <= 0.045 and existing.pitch == note.pitch
            ),
            None,
        )
        if duplicate is None:
            output.append(note)
    return output


def preserve_high_fidelity_polyphony(notes: Iterable[Note]) -> list[Note]:
    """Keep full timing/polyphony while merging cross-stem duplicates."""
    output: list[Note] = []
    for note in sorted(notes, key=lambda value: (value.start, value.pitch, -value.amplitude)):
        if not PIANO_LOWEST_MIDI <= note.pitch <= PIANO_HIGHEST_MIDI:
            continue
        duplicate_index = next(
            (
                index
                for index in range(len(output) - 1, max(-1, len(output) - 33), -1)
                if output[index].pitch == note.pitch
                and abs(output[index].start - note.start) <= 0.035
            ),
            None,
        )
        if duplicate_index is None:
            output.append(note)
            continue

        existing = output[duplicate_index]
        role = "melody" if "melody" in (existing.role, note.role) else existing.role
        output[duplicate_index] = Note(
            start=min(existing.start, note.start),
            end=max(existing.end, note.end),
            pitch=note.pitch,
            amplitude=max(existing.amplitude, note.amplitude),
            role=role,
        )
    return sorted(output, key=lambda value: (value.start, value.pitch))


def clean_high_fidelity_accompaniment(notes: Iterable[Note]) -> list[Note]:
    polyphony = preserve_high_fidelity_polyphony(notes)
    harmonic_intervals = {12, 19, 24}
    without_parallel_harmonics = [
        candidate
        for candidate in polyphony
        if not any(
            candidate.pitch - fundamental.pitch in harmonic_intervals
            and abs(candidate.start - fundamental.start) <= 0.055
            and min(candidate.end, fundamental.end) > max(candidate.start, fundamental.start)
            and fundamental.amplitude >= candidate.amplitude * 0.75
            for fundamental in polyphony
            if fundamental.pitch < candidate.pitch
        )
    ]
    cleaned: list[Note] = []
    for candidate in without_parallel_harmonics:
        nearby_lower_pitches = [
            neighbour.pitch
            for neighbour in without_parallel_harmonics
            if neighbour is not candidate
            and neighbour.pitch < candidate.pitch
            and abs(neighbour.start - candidate.start) <= 0.8
        ]
        weak_isolated_high_note = (
            bool(nearby_lower_pitches)
            and candidate.pitch - statistics.median(nearby_lower_pitches) >= 12
            and candidate.amplitude < 0.62
        )
        if not weak_isolated_high_note:
            cleaned.append(candidate)
    return cleaned


def write_midi(path: pathlib.Path, bpm: float, notes: list[Note]) -> None:
    import pretty_midi

    midi = pretty_midi.PrettyMIDI(initial_tempo=bpm)
    melody = pretty_midi.Instrument(program=0, name="Main melody")
    accompaniment = pretty_midi.Instrument(program=0, name="Simplified accompaniment")
    detailed = pretty_midi.Instrument(program=0, name="88-key high-fidelity transcription")
    for note in notes:
        velocity = int(max(35, min(112, round(note.amplitude * 115))))
        target = (
            melody
            if note.role == "melody"
            else accompaniment
            if note.role == "accompaniment"
            else detailed
        )
        target.notes.append(
            pretty_midi.Note(
                velocity=velocity,
                pitch=note.pitch,
                start=max(0.0, note.start),
                end=max(note.start + 0.08, note.end),
            )
        )
    if melody.notes:
        midi.instruments.append(melody)
    if accompaniment.notes:
        midi.instruments.append(accompaniment)
    if detailed.notes:
        midi.instruments.append(detailed)
    path.parent.mkdir(parents=True, exist_ok=True)
    midi.write(str(path))


def transcribe(input_path: pathlib.Path, output_path: pathlib.Path, mode: str) -> dict[str, object]:
    bpm, beats, _ = estimate_beats(input_path)
    half_beats = subdivision_grid(beats)
    with tempfile.TemporaryDirectory(prefix="game-piano-audio-") as temporary:
        if mode in ("high-fidelity-vocals", "high-fidelity-accompaniment"):
            separated = separate(input_path, pathlib.Path(temporary))
            if separated is None:
                raise RuntimeError(
                    "88 键人声/伴奏模式需要人声分离组件，请使用 Full 版或安装带人声分离的音频组件"
                )
            used_separation = True
            vocal_path, accompaniment_path = separated
            if mode == "high-fidelity-vocals":
                # Vocals are normally monophonic. Keep their original timing,
                # but select only one fundamental per onset group to suppress
                # harmonics detected as extra piano notes.
                selected = melody_line(
                    predict_notes(vocal_path, "melody", high_fidelity=True),
                    grid=[],
                )
            else:
                selected = clean_high_fidelity_accompaniment(
                    predict_notes(
                        accompaniment_path,
                        "high-fidelity",
                        high_fidelity=True,
                    )
                )
        else:
            separated = separate(input_path, pathlib.Path(temporary))
            used_separation = separated is not None
            if separated is None:
                full_events = predict_notes(input_path, "melody")
                melody = melody_line(full_events, half_beats)
                accompaniment = accompaniment_pulses(full_events, beats)
            else:
                vocal_path, accompaniment_path = separated
                melody = melody_line(predict_notes(vocal_path, "melody"), half_beats)
                accompaniment = accompaniment_pulses(
                    predict_notes(accompaniment_path, "accompaniment"),
                    beats,
                )

            if mode == "melody":
                selected = melody
            elif mode == "accompaniment":
                selected = accompaniment
            else:
                selected = deduplicate([*melody, *accompaniment])

        if not selected:
            raise RuntimeError("没有识别到可用音符，请尝试更清晰的音频或其他模式")
        progress("正在生成游戏 MIDI")
        write_midi(output_path, bpm, selected)
        return {
            "output": str(output_path),
            "notes": len(selected),
            "bpm": round(bpm, 2),
            "separated": used_separation,
            "mode": mode,
            "range": (
                "A0-C8"
                if mode in ("high-fidelity-vocals", "high-fidelity-accompaniment")
                else "game-simplified"
            ),
        }


def main() -> int:
    parser = argparse.ArgumentParser(description="Transcribe song audio into a game-oriented MIDI arrangement")
    parser.add_argument("--input", required=True, type=pathlib.Path)
    parser.add_argument("--output", required=True, type=pathlib.Path)
    parser.add_argument(
        "--mode",
        choices=(
            "high-fidelity-vocals",
            "high-fidelity-accompaniment",
            "melody",
            "balanced",
            "accompaniment",
        ),
        default="balanced",
    )
    arguments = parser.parse_args()
    if not arguments.input.is_file():
        raise FileNotFoundError(arguments.input)
    result = transcribe(arguments.input.resolve(), arguments.output.resolve(), arguments.mode)
    print("RESULT " + json.dumps(result, ensure_ascii=False), flush=True)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:
        print(f"ERROR {type(error).__name__}: {error}", file=sys.stderr, flush=True)
        raise
