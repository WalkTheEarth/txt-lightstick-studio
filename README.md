# TXT Lightstick Studio

Unofficial controller for the **TOMORROW X TOGETHER Official Light Stick Ver.2**
(HYBE, app `com.hybe.txtapp2` 1.10).

Two implementations of the same recovered BLE protocol:

| Feature | Python library | Android app |
| --- | --- | --- |
| Scan / connect | `scan()`, `LightStick.connect()` | Connect button |
| Set colour | `set_color(r, g, b)` | RGB sliders + *Send colour now* |
| Set effect | `set_effect("blink", r, g, b)` | Effect spinner + *Send colour now* |
| Timeline playback | `play(timeline, fps=10)` | Tap/drag stops, *Play* |
| Off | `off()` | *Stop* |

The colour protocol is a 4-byte write: `[mode, red, green, blue]` to the
lightstick's LED characteristic. See [PROTOCOL.md](PROTOCOL.md) for the full
reverse-engineering notes, UUIDs and packet formats.

## Python library

```bash
cd python
python3 -m venv .venv && . .venv/bin/activate
pip install -e .
```

```bash
txtlightstick scan
txtlightstick set '#ff0000'              # solid red
txtlightstick set '#0000ff' -e blink     # blinking blue
txtlightstick play examples/timeline.json
txtlightstick off
```

From a script:

```python
import asyncio
from txtlightstick import LightStick, Timeline

async def main():
    async with LightStick() as stick:          # scans and connects
        await stick.set_color(255, 0, 0)        # solid red
        await asyncio.sleep(2)
        await stick.set_effect("breath", 0, 0, 255)
        await stick.play(Timeline.load("examples/timeline.json"), fps=10)

asyncio.run(main())
```

A timeline is a JSON file. Colours between stops are linearly interpolated:

```json
{
  "duration": 8.0,
  "keyframes": [
    {"t": 0.0, "color": "#000000"},
    {"t": 1.0, "color": "#ff0000"},
    {"t": 3.0, "color": "#0000ff"},
    {"t": 8.0, "color": "#000000"}
  ]
}
```

## Android app

Requires an Android 8.0+ phone and the lightstick in Bluetooth mode
(power off, then hold buttons 1 and 2 together for two seconds — it blinks blue).

```bash
cd android
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Open **TXT Lightstick Studio**:

1. Tap **Connect lightstick** and allow Bluetooth permissions.
2. Pick a colour with the R/G/B sliders and an effect, then **Send colour now**.
3. In the timeline, tap the strip to drop a colour stop, drag stops to move them.
   Selecting a stop loads its colour into the sliders; edits update the stop.
4. **Play** streams the interpolated colours at 10 fps; **Stop** turns the stick off.

The timeline and last colour are saved locally.

## Effects

| Effect | Mode byte |
| --- | --- |
| Solid | 1 |
| Blink | 2 |
| Breath | 3 |
| Strobe | 4 |

Effect speed is fixed by the firmware; the app only chooses colour.

## Notes and limitations

- Reverse engineered statically from the shipped APK (v1.10) — see
  [PROTOCOL.md](PROTOCOL.md). **Not tested against physical hardware.**
- The official app's own effect picker orders its icons Solid, Strobe, Blink,
  Breath but sends modes 1, 4, 2, 3 respectively. This project maps effect
  names directly to the mode bytes.
- Playback is streaming: BLE throughput and write-with-response latency limit
  the practical frame rate (10 fps default). The stick holds the last colour
  between writes.
- This project is not affiliated with HYBE or TOMORROW X TOGETHER. The
  proprietary APK is not redistributed.

## Layout

```
PROTOCOL.md                   recovered protocol, evidence and unknown parts
python/                       async bleak library + CLI
android/                      standalone Java app (no AndroidX, minSdk 26)
```
