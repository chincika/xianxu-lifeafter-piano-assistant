from __future__ import annotations

import importlib.util
import pathlib
import sys
import types
import unittest
from unittest.mock import patch


WORKER_PATH = (
    pathlib.Path(__file__).parents[2]
    / "main"
    / "resources"
    / "audio"
    / "transcribe.py"
)
SPEC = importlib.util.spec_from_file_location("game_piano_transcribe", WORKER_PATH)
assert SPEC is not None and SPEC.loader is not None
worker = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = worker
SPEC.loader.exec_module(worker)


class AudioWorkerTest(unittest.TestCase):
    def fake_basic_pitch(self, events, captured):
        inference = types.ModuleType("basic_pitch.inference")

        def predict(_path, **kwargs):
            captured.update(kwargs)
            return None, None, events

        inference.predict = predict
        package = types.ModuleType("basic_pitch")
        package.inference = inference
        return patch.dict(
            sys.modules,
            {"basic_pitch": package, "basic_pitch.inference": inference},
        )

    def test_high_fidelity_uses_full_model_range_and_keeps_a0_to_c8(self):
        captured = {}
        events = [
            (0.0, 0.2, 20, 0.8, None),
            (0.0, 0.2, 21, 0.8, None),
            (0.0, 0.2, 108, 0.8, None),
            (0.0, 0.2, 109, 0.8, None),
        ]

        with self.fake_basic_pitch(events, captured):
            notes = worker.predict_notes(
                pathlib.Path("example.wav"),
                "high-fidelity",
                high_fidelity=True,
            )

        self.assertIsNone(captured["minimum_frequency"])
        self.assertIsNone(captured["maximum_frequency"])
        self.assertEqual([21, 108], [note.pitch for note in notes])

    def test_high_fidelity_preserves_polyphony_and_merges_only_duplicates(self):
        notes = [
            worker.Note(0.0, 0.3, 60, 0.6, "accompaniment"),
            worker.Note(0.01, 0.5, 60, 0.9, "melody"),
            worker.Note(0.0, 0.4, 64, 0.7, "accompaniment"),
            worker.Note(0.0, 0.4, 67, 0.7, "accompaniment"),
            worker.Note(1.0, 1.2, 20, 0.9, "melody"),
            worker.Note(1.0, 1.2, 109, 0.9, "melody"),
        ]

        preserved = worker.preserve_high_fidelity_polyphony(notes)

        self.assertEqual([60, 64, 67], [note.pitch for note in preserved])
        merged = preserved[0]
        self.assertEqual(0.0, merged.start)
        self.assertEqual(0.5, merged.end)
        self.assertEqual(0.9, merged.amplitude)
        self.assertEqual("melody", merged.role)

    def test_accompaniment_cleaning_suppresses_weaker_simultaneous_harmonics(self):
        notes = [
            worker.Note(0.01, 0.5, 48, 0.8, "high-fidelity"),
            worker.Note(0.0, 0.5, 52, 0.7, "high-fidelity"),
            worker.Note(0.02, 0.4, 60, 0.55, "high-fidelity"),
            worker.Note(0.01, 0.4, 67, 0.45, "high-fidelity"),
            worker.Note(0.3, 0.7, 60, 0.9, "high-fidelity"),
            worker.Note(0.9, 1.2, 79, 0.5, "high-fidelity"),
            worker.Note(2.0, 2.4, 79, 0.8, "high-fidelity"),
        ]

        cleaned = worker.clean_high_fidelity_accompaniment(notes)

        self.assertEqual([48, 52, 60, 79], sorted(note.pitch for note in cleaned))

    def test_high_fidelity_modes_never_mix_vocals_and_accompaniment(self):
        vocal_notes = [
            worker.Note(0.0, 0.4, 60, 0.9, "melody"),
            worker.Note(0.01, 0.4, 72, 0.5, "melody"),
        ]
        accompaniment_notes = [
            worker.Note(0.0, 0.5, 48, 0.8, "high-fidelity"),
            worker.Note(0.0, 0.5, 52, 0.8, "high-fidelity"),
        ]
        written = []

        def fake_predict(path, _role, high_fidelity=False):
            self.assertTrue(high_fidelity)
            return vocal_notes if pathlib.Path(path).name == "vocals.wav" else accompaniment_notes

        def fake_write(_path, _bpm, notes):
            written.append([note.pitch for note in notes])

        patches = (
            patch.object(worker, "estimate_beats", return_value=(120.0, [0.0, 0.5], 1.0)),
            patch.object(
                worker,
                "separate",
                return_value=(pathlib.Path("vocals.wav"), pathlib.Path("accompaniment.wav")),
            ),
            patch.object(worker, "predict_notes", side_effect=fake_predict),
            patch.object(worker, "write_midi", side_effect=fake_write),
        )
        with patches[0], patches[1], patches[2], patches[3]:
            worker.transcribe(
                pathlib.Path("song.wav"),
                pathlib.Path("vocals.mid"),
                "high-fidelity-vocals",
            )
            worker.transcribe(
                pathlib.Path("song.wav"),
                pathlib.Path("accompaniment.mid"),
                "high-fidelity-accompaniment",
            )

        self.assertEqual([[60], [48, 52]], written)


if __name__ == "__main__":
    unittest.main()
