package com.elad.codusb;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Runs as the ADB "shell" user (started with app_process by the app over local ADB).
 * Creates a virtual HID gamepad called "Xbox Wireless Controller" through /dev/uhid.
 * Call of Duty Mobile checks for that controller name; once it exists the game turns on
 * controller mode and also accepts input from the real USB controller.
 *
 * Same name, IDs and HID descriptor as the classes.dex from the original guide.
 * Pure Java on purpose: no AndroidX or Kotlin classes, so it runs under app_process.
 */
public final class HidServer {

    private static final int UHID_START = 2;
    private static final int UHID_OPEN = 4;
    private static final int UHID_GET_REPORT = 9;
    private static final int UHID_GET_REPORT_REPLY = 10;
    private static final int UHID_CREATE2 = 11;
    private static final int UHID_INPUT2 = 12;
    private static final int UHID_SET_REPORT = 13;
    private static final int UHID_SET_REPORT_REPLY = 14;

    /** sizeof(struct uhid_event): 4 (type) + sizeof(struct uhid_create2_req) = 4 + 4372. */
    private static final int EVENT_SIZE = 4376;

    private static final String NAME = "Xbox Wireless Controller";
    private static final int BUS_USB = 0x03;
    private static final int VENDOR_ID = 0x0929;
    private static final int PRODUCT_ID = 0x1a85;

    /** Gamepad: 16 buttons + X, Y, Z, Rz axes (6-byte report). */
    private static final byte[] DESCRIPTOR = hex(
            "05010905a1010509190129101500250175019510810205011581257f0930093109320935750895048102c0");

    private static PrintStream log = System.out;

    public static void main(String[] args) {
        try {
            log = new PrintStream(new FileOutputStream("/data/local/tmp/codusb_hid.log", false), true);
        } catch (Throwable ignored) {
        }
        try {
            run();
        } catch (Throwable t) {
            log.println("fatal: " + t);
            t.printStackTrace(log);
            System.exit(1);
        }
    }

    private static void run() throws IOException {
        RandomAccessFile file = new RandomAccessFile("/dev/uhid", "rw");
        FileOutputStream out = new FileOutputStream(file.getFD());
        FileInputStream in = new FileInputStream(file.getFD());

        ByteBuffer b = newEvent(UHID_CREATE2);
        putString(b, NAME, 128);   // name
        putString(b, "codusb", 64); // phys
        putString(b, "1", 64);      // uniq
        b.putShort((short) DESCRIPTOR.length);
        b.putShort((short) BUS_USB);
        b.putInt(VENDOR_ID);
        b.putInt(PRODUCT_ID);
        b.putInt(0); // version
        b.putInt(0); // country
        b.put(DESCRIPTOR);
        out.write(b.array());
        log.println("Virtual Xbox Wireless Controller created successfully.");

        sendNeutralReport(out);

        byte[] buf = new byte[EVENT_SIZE + 64];
        while (true) {
            int n = in.read(buf);
            if (n < 0) {
                log.println("uhid closed");
                return;
            }
            if (n < 4) continue;
            ByteBuffer e = ByteBuffer.wrap(buf, 0, n).order(ByteOrder.LITTLE_ENDIAN);
            int type = e.getInt();
            switch (type) {
                case UHID_START:
                case UHID_OPEN:
                    sendNeutralReport(out);
                    break;
                case UHID_GET_REPORT: {
                    int id = e.getInt();
                    ByteBuffer r = newEvent(UHID_GET_REPORT_REPLY);
                    r.putInt(id);
                    r.putShort((short) 5); // EIO: no feature reports
                    r.putShort((short) 0);
                    out.write(r.array());
                    break;
                }
                case UHID_SET_REPORT: {
                    int id = e.getInt();
                    ByteBuffer r = newEvent(UHID_SET_REPORT_REPLY);
                    r.putInt(id);
                    r.putShort((short) 0);
                    out.write(r.array());
                    break;
                }
                default:
                    break;
            }
            log.println("uhid event " + type);
        }
    }

    private static void sendNeutralReport(FileOutputStream out) throws IOException {
        ByteBuffer b = newEvent(UHID_INPUT2);
        b.putShort((short) 6);
        b.put(new byte[6]); // no buttons, sticks centred
        out.write(b.array());
    }

    private static ByteBuffer newEvent(int type) {
        ByteBuffer b = ByteBuffer.allocate(EVENT_SIZE).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(type);
        return b;
    }

    private static void putString(ByteBuffer b, String s, int len) {
        int start = b.position();
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        b.put(bytes, 0, Math.min(bytes.length, len - 1));
        b.position(start + len);
    }

    private static byte[] hex(String s) {
        byte[] r = new byte[s.length() / 2];
        for (int i = 0; i < r.length; i++) {
            r[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        }
        return r;
    }
}
