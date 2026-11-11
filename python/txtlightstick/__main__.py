"""CLI: python -m txtlightstick {scan,set,play,off}."""

from __future__ import annotations

import argparse
import asyncio
import sys

from bleak.exc import BleakError

from .client import LightStick, scan
from .protocol import parse_effect
from .timeline import Timeline, parse_color


async def _run(args) -> None:
    if args.command == "scan":
        sticks = await scan(timeout=args.timeout, all_devices=args.all)
        if not sticks:
            print("no lightstick found")
            return
        for s in sticks:
            print(f"{s.address}  {s.rssi!s:>5} dBm  {s.name}")
        return

    async with LightStick(address=args.address) as stick:
        print(f"connected to {stick.address}")
        if args.command == "off":
            await stick.off()
        elif args.command == "set":
            r, g, b = parse_color(args.color)
            await stick.set_color(r, g, b, parse_effect(args.effect))
        elif args.command == "play":
            await stick.play(args.timeline, fps=args.fps, loop=args.loop)
    print("done")


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(prog="txtlightstick")
    sub = parser.add_subparsers(dest="command", required=True)

    def common(p):
        p.add_argument("-a", "--address", help="BLE address; default: scan and pick first")

    p = sub.add_parser("scan", help="scan for lightsticks")
    p.add_argument("-t", "--timeout", type=float, default=10.0)
    p.add_argument("--all", action="store_true", help="list every BLE device")

    p = sub.add_parser("set", help="set a colour")
    common(p)
    p.add_argument("color", help="#rrggbb")
    p.add_argument("-e", "--effect", default="basic",
                   choices=["basic", "blink", "breath", "strobe"])

    p = sub.add_parser("play", help="play a timeline JSON file")
    common(p)
    p.add_argument("timeline", help="timeline JSON (see examples/timeline.json)")
    p.add_argument("--fps", type=float, default=10.0)
    p.add_argument("--loop", action="store_true")

    p = sub.add_parser("off", help="turn the stick off (solid black)")
    common(p)

    args = parser.parse_args(argv)
    try:
        asyncio.run(_run(args))
    except (RuntimeError, ValueError, OSError, BleakError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
