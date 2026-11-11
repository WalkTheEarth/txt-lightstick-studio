"""Async BLE client for the TXT Official Light Stick Ver.2."""

from __future__ import annotations

import asyncio
import time
from dataclasses import dataclass

from bleak import BleakClient, BleakScanner

from .protocol import (
    DEVICE_MAC_PREFIXES,
    DEVICE_NAME_PREFIXES,
    LED_MODE_UUID,
    SERVICE_UUID,
    Mode,
    led_packet,
    parse_effect,
)
from .timeline import Timeline


@dataclass
class DiscoveredStick:
    address: str
    name: str
    rssi: int | None


def _matches(name: str | None, address: str = "") -> bool:
    name = name or ""
    return name.startswith(DEVICE_NAME_PREFIXES) or address.upper().startswith(
        DEVICE_MAC_PREFIXES
    )


async def scan(timeout: float = 10.0, all_devices: bool = False) -> list[DiscoveredStick]:
    """Scan for lightsticks (or every BLE device with all_devices=True)."""
    found = await BleakScanner.discover(timeout=timeout, return_adv=True)
    sticks = []
    for address, (device, adv) in found.items():
        name = adv.local_name or device.name or ""
        if all_devices or _matches(name, address):
            sticks.append(DiscoveredStick(address=address, name=name, rssi=adv.rssi))
    return sorted(sticks, key=lambda s: (s.rssi is None, -(s.rssi or 0)))


class LightStick:
    """Connect, set colours/effects and play colour timelines."""

    def __init__(self, address: str | None = None, timeout: float = 20.0):
        self.address = address
        self.timeout = timeout
        self._client: BleakClient | None = None

    @property
    def connected(self) -> bool:
        return bool(self._client and self._client.is_connected)

    async def connect(self, address: str | None = None) -> str:
        address = address or self.address
        if not address:
            sticks = await scan()
            if not sticks:
                raise RuntimeError(
                    "no lightstick found; turn it on in Bluetooth mode "
                    "(buttons 1+2 for 2s) and try again"
                )
            address = sticks[0].address
        client = BleakClient(address, timeout=self.timeout)
        await client.connect()
        self._client = client
        self.address = address
        return address

    async def disconnect(self) -> None:
        if self._client:
            await self._client.disconnect()
            self._client = None

    async def _write(self, data: bytes) -> None:
        if not self.connected:
            raise RuntimeError("not connected")
        await self._client.write_gatt_char(LED_MODE_UUID, data, response=True)

    async def set_color(
        self, r: int, g: int, b: int, effect: str | int | Mode = Mode.BASIC
    ) -> None:
        """Set a solid/effect colour immediately."""
        await self._write(led_packet(r, g, b, effect))

    async def set_effect(
        self, effect: str | int | Mode, r: int, g: int, b: int
    ) -> None:
        """Same as set_color with the effect first, mirroring the app UI."""
        await self._write(led_packet(r, g, b, parse_effect(effect)))

    async def off(self) -> None:
        """Solid black; the stick stays connected."""
        await self._write(led_packet(0, 0, 0, Mode.BASIC))

    async def play(
        self,
        timeline: Timeline | str,
        fps: float = 10.0,
        loop: bool = False,
        effect: str | int | Mode = Mode.BASIC,
    ) -> None:
        """Stream a timeline to the stick in real time.

        Frames are only written when the interpolated colour changes; the
        firmware holds the last colour in between.
        """
        if isinstance(timeline, str):
            timeline = Timeline.load(timeline)
        mode = int(parse_effect(effect))
        start = time.monotonic()
        last: tuple[int, int, int] | None = None
        for t, (r, g, b) in timeline.frames(fps=fps, loop=loop):
            delay = start + t - time.monotonic()
            if delay > 0:
                await asyncio.sleep(delay)
            if (r, g, b) != last:
                await self._write(bytes((mode, r, g, b)))
                last = (r, g, b)
        await self.off()

    async def __aenter__(self) -> "LightStick":
        await self.connect()
        return self

    async def __aexit__(self, *exc) -> None:
        await self.disconnect()
