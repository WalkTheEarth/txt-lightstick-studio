import asyncio
import unittest

from txtlightstick.client import LightStick
from txtlightstick.protocol import (
    LED_MODE_UUID,
    SERVICE_UUID,
    Mode,
    chunk_packet,
    crc16_modbus,
    led_packet,
    parse_effect,
)
from txtlightstick.timeline import Timeline, parse_color


class ProtocolTest(unittest.TestCase):
    def test_uuids(self):
        self.assertEqual(SERVICE_UUID, "0001fe01-0000-1000-8000-00805f9800c4")
        self.assertEqual(LED_MODE_UUID, "0001ff02-0000-1000-8000-00805f9800c4")

    def test_led_packet(self):
        # exactly the bytes the official app writes: [mode, r, g, b]
        self.assertEqual(led_packet(255, 0, 0), b"\x01\xff\x00\x00")
        self.assertEqual(led_packet(0, 0, 255, "blink"), b"\x02\x00\x00\xff")
        self.assertEqual(led_packet(1, 2, 3, Mode.STROBE), b"\x04\x01\x02\x03")

    def test_parse_effect(self):
        self.assertEqual(parse_effect("solid"), Mode.BASIC)
        self.assertEqual(parse_effect(3), Mode.BREATH)
        with self.assertRaises(ValueError):
            parse_effect("disco")

    def test_channels_are_validated(self):
        with self.assertRaises(ValueError):
            led_packet(256, 0, 0)
        with self.assertRaises(ValueError):
            led_packet(-1, 0, 0)

    def test_crc16_modbus_check_value(self):
        self.assertEqual(crc16_modbus(b"123456789"), 0x4B37)

    def test_chunk_packet(self):
        packet = chunk_packet(0x1234, b"\xaa\xbb", packet_size=4)
        self.assertEqual(packet[:2], b"\x34\x12")
        self.assertEqual(packet[2:6], b"\xaa\xbb\xff\xff")
        self.assertEqual(int.from_bytes(packet[6:], "little"), crc16_modbus(packet[:6]))


class TimelineTest(unittest.TestCase):
    def test_parse_color(self):
        self.assertEqual(parse_color("#ff8000"), (255, 128, 0))
        self.assertEqual(parse_color([1, 2, 255]), (1, 2, 255))
        with self.assertRaises(ValueError):
            parse_color("#fff")

    def test_interpolation(self):
        tl = Timeline(
            duration=10,
            keyframes=[
                {"t": 0, "color": "#000000"},
                {"t": 10, "color": "#ffffff"},
            ],
        )
        self.assertEqual(tl.sample(0), (0, 0, 0))
        self.assertEqual(tl.sample(5), (128, 128, 128))
        self.assertEqual(tl.sample(10), (255, 255, 255))
        self.assertEqual(tl.sample(-4), (0, 0, 0))
        self.assertEqual(tl.sample(99), (255, 255, 255))

    def test_frames_cover_duration(self):
        tl = Timeline(duration=1.0, keyframes=[{"t": 0, "color": "#000000"}])
        times = [t for t, _ in tl.frames(fps=10)]
        self.assertEqual(len(times), 10)
        self.assertEqual(times[0], 0.0)
        self.assertLess(times[-1], 1.0)

    def test_round_trip(self):
        tl = Timeline(5, [{"t": 2.5, "color": "#12ab34"}])
        self.assertEqual(Timeline.from_dict(tl.to_dict()).to_dict(), tl.to_dict())


class _FakeStick(LightStick):
    def __init__(self):
        super().__init__()
        self.packets = []

    async def _write(self, data):
        self.packets.append(data)


class PlaybackTest(unittest.TestCase):
    def test_play_streams_changes_then_off(self):
        stick = _FakeStick()
        timeline = Timeline(
            duration=0.4,
            keyframes=[{"t": 0, "color": "#000000"}, {"t": 0.4, "color": "#ff0000"}],
        )
        asyncio.run(stick.play(timeline, fps=5))
        # 5 fps over 0.4 s -> 2 frames, both different, then black.
        self.assertEqual(stick.packets[0], led_packet(0, 0, 0))
        self.assertEqual(stick.packets[1], led_packet(128, 0, 0))
        self.assertEqual(stick.packets[-1], led_packet(0, 0, 0))
        self.assertEqual(len(stick.packets), 3)

    def test_play_skips_duplicate_frames(self):
        stick = _FakeStick()
        timeline = Timeline(duration=0.4, keyframes=[{"t": 0, "color": "#112233"}])
        asyncio.run(stick.play(timeline, fps=5))
        self.assertEqual(stick.packets, [led_packet(0x11, 0x22, 0x33), led_packet(0, 0, 0)])


if __name__ == "__main__":
    unittest.main()
