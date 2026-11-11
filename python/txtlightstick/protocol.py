"""Wire protocol constants and packet builder for the TXT lightstick (Ver.2)."""

from __future__ import annotations

from enum import IntEnum

# Recovered from com.hybe.txtapp2 1.10 (libapp.so, sdkapp/utils/ble/BleUtils.dart).
SERVICE_UUID = "0001fe01-0000-1000-8000-00805f9800c4"
LED_MODE_UUID = "0001ff02-0000-1000-8000-00805f9800c4"

# The stick advertises with one of these name prefixes.
DEVICE_NAME_PREFIXES = ("txt_v2 ls", "0120ip")

# The official app also accepts these MAC prefixes; they are only a hint.
DEVICE_MAC_PREFIXES = ("A8:3F", "80:DE:CC")

PACKET_SIZE = 4


class Mode(IntEnum):
    """LED mode byte. Values mirror the app's changeLedMode() calls."""

    BASIC = 1    # solid colour
    BLINK = 2
    BREATH = 3
    STROBE = 4


_EFFECT_ALIASES = {
    "basic": Mode.BASIC,
    "solid": Mode.BASIC,
    "blink": Mode.BLINK,
    "breath": Mode.BREATH,
    "breathe": Mode.BREATH,
    "strobe": Mode.STROBE,
}


def parse_effect(effect: str | int | Mode) -> Mode:
    if isinstance(effect, Mode):
        return effect
    if isinstance(effect, int):
        return Mode(effect)
    try:
        return _EFFECT_ALIASES[effect.lower()]
    except KeyError:
        raise ValueError(
            f"unknown effect {effect!r}; use {sorted(_EFFECT_ALIASES)}"
        ) from None


def _channel(value: int, name: str) -> int:
    value = int(value)
    if not 0 <= value <= 255:
        raise ValueError(f"{name} must be 0..255, got {value}")
    return value


def led_packet(r: int, g: int, b: int, effect: str | int | Mode = Mode.BASIC) -> bytes:
    """Build the 4-byte LED command: [mode, red, green, blue]."""
    return bytes(
        (int(parse_effect(effect)), _channel(r, "red"), _channel(g, "green"), _channel(b, "blue"))
    )


def crc16_modbus(data: bytes) -> int:
    """CRC-16/MODBUS (poly 0xA001, init 0xFFFF), used by library/OTA packets."""
    crc = 0xFFFF
    for byte in data:
        crc ^= byte
        for _ in range(8):
            crc = (crc >> 1) ^ 0xA001 if crc & 1 else crc >> 1
    return crc & 0xFFFF


def chunk_packet(index: int, payload: bytes, packet_size: int = 16) -> bytes:
    """One library/OTA chunk as built by BleUtils.getPacket().

    Layout: uint16 packet index (little endian), payload padded with 0xFF to
    packet_size, uint16 CRC-16/MODBUS (little endian) over the preceding bytes.
    """
    if not 0 <= index <= 0xFFFF:
        raise ValueError("packet index must be 0..65535")
    if len(payload) > packet_size:
        raise ValueError(f"payload is {len(payload)} bytes, max {packet_size}")
    body = index.to_bytes(2, "little") + payload.ljust(packet_size, b"\xff")
    return body + crc16_modbus(body).to_bytes(2, "little")
