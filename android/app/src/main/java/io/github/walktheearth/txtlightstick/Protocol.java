package io.github.walktheearth.txtlightstick;

import java.util.UUID;

/** Wire constants recovered from the TXT Official Light Stick Ver.2 app (v1.10). */
final class Protocol {
    static final UUID SERVICE_UUID = UUID.fromString("0001fe01-0000-1000-8000-00805f9800c4");
    static final UUID LED_MODE_UUID = UUID.fromString("0001ff02-0000-1000-8000-00805f9800c4");

    static final String[] NAME_PREFIXES = {"txt_v2 ls", "0120ip"};
    static final String[] MAC_PREFIXES = {"A8:3F", "80:DE:CC"};

    static final int SOLID = 1;
    static final int BLINK = 2;
    static final int BREATH = 3;
    static final int STROBE = 4;

    private Protocol() {}

    static byte[] ledPacket(int mode, int r, int g, int b) {
        return new byte[] {(byte) mode, (byte) r, (byte) g, (byte) b};
    }

    static int rgb(int r, int g, int b) {
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    static boolean matches(String name) {
        if (name == null) return false;
        for (String prefix : NAME_PREFIXES) {
            if (name.startsWith(prefix)) return true;
        }
        String upper = name.toUpperCase();
        for (String prefix : MAC_PREFIXES) {
            if (upper.startsWith(prefix)) return true;
        }
        return false;
    }
}
