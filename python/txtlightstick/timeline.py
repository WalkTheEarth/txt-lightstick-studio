"""Colour timeline: keyframes over time with linear RGB interpolation.

The JSON format is shared with the Android app:

    {"duration": 10.0,
     "keyframes": [{"t": 0.0, "color": "#ff0000"},
                   {"t": 4.5, "color": [0, 0, 255]}]}
"""

from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path

RGB = tuple[int, int, int]


def parse_color(value) -> RGB:
    """Accept "#rrggbb", "rrggbb", [r, g, b] or (r, g, b)."""
    if isinstance(value, (list, tuple)):
        if len(value) != 3:
            raise ValueError(f"colour needs 3 channels, got {value!r}")
        rgb = tuple(int(c) for c in value)
    elif isinstance(value, str):
        text = value.strip().lstrip("#")
        if len(text) != 6:
            raise ValueError(f"colour must be #rrggbb, got {value!r}")
        rgb = tuple(int(text[i : i + 2], 16) for i in (0, 2, 4))
    else:
        raise ValueError(f"unsupported colour {value!r}")
    if not all(0 <= c <= 255 for c in rgb):
        raise ValueError(f"colour channel out of range: {value!r}")
    return rgb  # type: ignore[return-value]


def format_color(rgb: RGB) -> str:
    return "#%02x%02x%02x" % rgb


@dataclass
class Keyframe:
    t: float
    color: RGB


def _as_keyframe(value) -> Keyframe:
    if isinstance(value, Keyframe):
        return value
    if isinstance(value, dict):
        return Keyframe(float(value["t"]), parse_color(value["color"]))
    return Keyframe(float(value.t), parse_color(value.color))


class Timeline:
    def __init__(self, duration: float = 10.0, keyframes=()):
        if duration <= 0:
            raise ValueError("duration must be positive")
        self.duration = float(duration)
        self.keyframes = sorted((_as_keyframe(k) for k in keyframes), key=lambda k: k.t)

    @classmethod
    def from_dict(cls, data: dict) -> "Timeline":
        return cls(
            duration=float(data.get("duration", 10.0)),
            keyframes=[
                Keyframe(float(k["t"]), parse_color(k["color"]))
                for k in data.get("keyframes", [])
            ],
        )

    @classmethod
    def load(cls, path: str | Path) -> "Timeline":
        return cls.from_dict(json.loads(Path(path).read_text()))

    def to_dict(self) -> dict:
        return {
            "duration": self.duration,
            "keyframes": [
                {"t": round(k.t, 3), "color": format_color(k.color)}
                for k in self.keyframes
            ],
        }

    def save(self, path: str | Path) -> None:
        Path(path).write_text(json.dumps(self.to_dict(), indent=2) + "\n")

    def sample(self, t: float) -> RGB:
        """Colour at time t: linear RGB interpolation, clamped at both ends."""
        if not self.keyframes:
            return (0, 0, 0)
        if t <= self.keyframes[0].t:
            return self.keyframes[0].color
        if t >= self.keyframes[-1].t:
            return self.keyframes[-1].color
        for left, right in zip(self.keyframes, self.keyframes[1:]):
            if left.t <= t <= right.t:
                span = right.t - left.t
                if span <= 0:
                    return right.color
                f = (t - left.t) / span
                return tuple(
                    round(a + (b - a) * f) for a, b in zip(left.color, right.color)  # type: ignore[return-value]
                )
        return self.keyframes[-1].color

    def frames(self, fps: float = 10.0, loop: bool = False):
        """Yield (time, colour) frames covering the timeline."""
        if fps <= 0:
            raise ValueError("fps must be positive")
        while True:
            frame = 0
            while frame / fps < self.duration:
                yield frame / fps, self.sample(frame / fps)
                frame += 1
            if not loop:
                return
