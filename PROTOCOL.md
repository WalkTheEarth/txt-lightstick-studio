# TXT Light Stick Ver.2 BLE protocol

Recovered from the shipped Android app **TXT Official Light Stick Ver.2 1.10**
(`com.hybe.txtapp2`, versionCode 15):

- `TXT-Official-Light-Stick-Ver.2_1.10_apkcube.apks`
- `base.apk` SHA-256 `cc27179abbbb8bce8552a15477b3df2d9dd7ea8f9b91b86bc7c483c9ec254cdf`
- `split_config.arm64_v8a.apk` SHA-256 `8c5cefc7d5885fe509f4a7fe97871c481e83d1e4590a7d488448ec9906d17851`
- `libapp.so` SHA-256 `34dbde3a1db4021ba45f3bbb19112b458bf772a2a86c5aeeaefe56d875c8bec6`,
  Dart 3.8.1 AOT snapshot `830f4f59e7969c70b595182826435c19`

The app is Flutter. BLE logic lives in
`package:sdkapp/utils/ble/BleUtils.dart` (`BleUtils`) and the CRC helper in
`package:sdkapp/utils/ble/CRCUtils.dart`. The Dart AOT snapshot was decompiled /
symbolised with [Blutter](https://github.com/worawit/blutter) and the native
image inspected with REA. Function addresses below are from `libapp.so` before
rebasing (Blutter reports `libapp is loaded at …`; addresses are file-relative).

## Scan

`BleUtils.scan()` (0x5f14c8) scans with `FlutterReactiveBle.scanForDevices`
using an empty service filter and keeps devices whose advertised name starts
with one of `Config.ssids()`:

```dart
static List<String> ssids() => ["0120ip", "txt_v2 ls"];
```

It also knows these MAC prefixes (used for model/firmware checks):

```dart
static List<String> deviceMac() => ["A8:3F", "80:DE:CC"];
```

The lightstick must be powered off, then buttons 1 and 2 held together for two
seconds, to enter Bluetooth mode (blinks blue).

## GATT map

All vendor UUIDs use the odd base `0001xxxx-0000-1000-8000-00805f9800c4`
(note `9800c4`, not Bluetooth SIG `9b34fb`).

| Name | UUID | Direction |
| --- | --- | --- |
| `LED_SERVICE` | `0001fe01-0000-1000-8000-00805f9800c4` | service |
| `LED_MODE_CHARACTERISTIC` | `0001ff02-0000-1000-8000-00805f9800c4` | write |
| `LIBRARY_DATA_SERVICE` | `0001fe02-0000-1000-8000-00805f9800c4` | service |
| `LIBRARY_DATA_SERVICE_CHARACTERISTIC` | `0001ff04-0000-1000-8000-00805f9800c4` | write |
| `MAC_SERVICE` | `0001fe03-0000-1000-8000-00805f9800c4` | service |
| `MAC_CHARACTERISTIC` | `0001ff05-0000-1000-8000-00805f9800c4` | read |
| `CONCERT_ID_SERVICE` | `0001fe04-0000-1000-8000-00805f9800c4` | service |
| `CONCERT_ID_CHARACTERISTIC` | `0001ff06-0000-1000-8000-00805f9800c4` | read |
| `OTA_SERVICE` | `0001fe06-0000-1000-8000-00805f9800c4` | service |
| `OTA_CHARACTERISTIC` | `0001ff13-0000-1000-8000-00805f9800c4` | write |
| Battery service | `0000180f-0000-1000-8000-00805f9b34fb` | service |
| Battery level | `00002a19-0000-1000-8000-00805f9b34fb` | read |
| Device information | `0000180a-0000-1000-8000-00805f9b34fb` | service |
| Model number | `00002a24-0000-1000-8000-00805f9b34fb` | read |
| Firmware revision | `00002a26-0000-1000-8000-00805f9b34fb` | read |

Evidence: each `BleUtils.<NAME>()` getter (`0x399768` ff02, `0x3997a0` fe01,
`0x573be8` ff13, `0x573c20` fe06, `0x573f08` ff04, `0x573f40` fe02,
`0x5f6c98` 2a26, `0x5f6cd0` 180a, `0x5f71f8` 2a24, `0x5f77a8` ff06,
`0x5f77e0` ff05, `0x5f7818` 2a19, `0x5f7850` fe04, `0x5f7888` fe03,
`0x5f78c0` 180f) returns `Uuid.parse("<string>")`.

## Colour command (self mode)

`BleUtils.changeLedMode` (0x388270) builds a 4-byte `Uint8List` and calls
`FlutterReactiveBle.writeCharacteristicWithResponse` on
`LED_SERVICE / LED_MODE_CHARACTERISTIC`:

```
byte 0: mode (1 = solid, 2 = blink, 3 = breath, 4 = strobe)
byte 1: red   0..255
byte 2: green 0..255
byte 3: blue  0..255
```

The byte order is confirmed by the connection-check screen, which cycles
`changeLedMode(1, 255, 0, 0)` (red), `(1, 0, 0, 255)` (blue) and
`(1, 255, 255, 0)` (yellow) at `DeviceConnectResultBody.dart` 0x388134,
0x3881ac, 0x388208. `SelfModeBody.syncLedMode` (0x5858fc) reads the selected
mode and `Color.red/green/blue`, then calls the same function.

No CRC, length, or magic is used for this command — just four bytes. Writes use
write-with-response. To turn the LEDs off, send solid black `[1, 0, 0, 0]`;
the app itself simply stops writing and disconnects.

Effect mapping: `SelfModeBody.onEffectChanged` (0x5856d8) maps the slider
position to modes: `<2 → 1`, `2..<3 → 4`, `3..<4 → 2`, `>=4 → 3`. The four
effect icons built in `SelfModeBody.build` are, in order, `icon_ef_basic`,
`icon_ef_strobe`, `icon_ef_blink`, `icon_ef_breath`, so at those positions the
modes are 1, 4, 2, 3 — i.e. **mode 4 is strobe, 2 blink, 3 breath**. The
official app's odd icon order is just a UI quirk; this project maps effect names
to mode bytes directly.

## Connection behaviour

`BleUtils.connectFirstDevice` (0x5f3cf4) waits briefly, picks the strongest
nearby candidate, and calls `connectToDevice` with these four services:
`LIBRARY_DATA_SERVICE`, `BATTERY_SERVICE`, `MAC_SERVICE`, `CONCERT_ID_SERVICE`.
`afterConnect` (0x5f5e9c) calls `discoverServices`, disconnects if none were
found, then `requestMtu`, and finally reads info characteristics. No bonding or
authentication is performed before colour writes; the connection-check screen
writes colours immediately after discovering services.

## Library data / OTA packets

`BleUtils.getPacket` (0x573f84) frames fixed-size chunks for the library-data
and OTA characteristics:

```
uint16  packet index, little endian
bytes   payload (a slice of a static pack), padded with 0xFF to PACKET_SIZE
uint16  CRC-16/MODBUS over all preceding bytes, little endian
```

`CRCUtils.crc16` (0x5700c8) is CRC-16/MODBUS: init `0xFFFF`, reflected
polynomial `0xA001`, no final XOR (check value for `"123456789"` is `0x4B37`).
The Python library exposes `chunk_packet()`; the Android app does not use OTA.

## Research limits

- Static evidence only: no physical lightstick was available, so effect timing,
  maximum write rate, and whether every Ver.2 firmware accepts writes without a
  preceding handshake are unverified.
- The `PACKET_SIZE` and full firmware/OTA state machine were not reconstructed;
  they are not needed for colour control.
- `isAndroidTxt1` (0x5f14c8 region) special-cases the older Ver.1 stick with a
  different binary path; this project targets Ver.2.
