# txtlightstick

Async Python library for the TOMORROW X TOGETHER Official Light Stick Ver.2.

```bash
pip install -e .
txtlightstick scan
txtlightstick set "#ff0000"
txtlightstick play examples/timeline.json
```

```python
import asyncio
from txtlightstick import LightStick, Timeline

async def main():
    async with LightStick() as stick:
        await stick.set_color(255, 0, 0)
        await stick.set_effect("breath", 0, 0, 255)
        await stick.play(Timeline.load("examples/timeline.json"), fps=10)

asyncio.run(main())
```

See the repository root README and `PROTOCOL.md` for the recovered BLE protocol.
