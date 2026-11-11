"""Control the TXT Official Light Stick Ver.2 over BLE."""

from .client import DiscoveredStick, LightStick, scan
from .protocol import (
    DEVICE_MAC_PREFIXES,
    DEVICE_NAME_PREFIXES,
    LED_MODE_UUID,
    SERVICE_UUID,
    Mode,
    chunk_packet,
    crc16_modbus,
    led_packet,
    parse_effect,
)
from .timeline import Keyframe, Timeline, format_color, parse_color

__all__ = [
    "DiscoveredStick",
    "LightStick",
    "scan",
    "Timeline",
    "Keyframe",
    "Mode",
    "led_packet",
    "parse_effect",
    "parse_color",
    "format_color",
    "crc16_modbus",
    "chunk_packet",
    "SERVICE_UUID",
    "LED_MODE_UUID",
    "DEVICE_NAME_PREFIXES",
    "DEVICE_MAC_PREFIXES",
]
