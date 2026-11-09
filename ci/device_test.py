#!/usr/bin/env python3
"""
End-to-end test on an Android emulator (run by GitHub Actions).

Checks, in order:
  1. The virtual controller helper creates "Xbox Wireless Controller" when run as the shell user.
  2. The main screen renders (screenshots).
  3. Pressing X (pairing) shows the code box at once (time is measured).
  4. Opening "Pair device with pairing code" makes the box expand by itself (time is measured).
  5. Typing the real pairing code pairs, connects and creates the virtual controller by itself.
  6. B stops the controller, A starts it again.
Writes ci-out/results.json and screenshots. Exit code 1 if a required check fails.
"""
import json
import os
import re
import subprocess
import sys
import time

PKG = "com.elad.codusb"
OUT = "ci-out"
APK = sys.argv[1] if len(sys.argv) > 1 else "CodUsbController.apk"
os.makedirs(OUT, exist_ok=True)
results = {}
log_lines = []


def log(*a):
    line = " ".join(str(x) for x in a)
    print(line, flush=True)
    log_lines.append(line)


def run(cmd, timeout=60):
    try:
        p = subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=timeout)
        return p.stdout
    except subprocess.TimeoutExpired:
        log("TIMEOUT:", cmd)
        return ""


def result(key, value):
    results[key] = value
    log(f"RESULT {key} = {value}")


shot_n = [0]


def shot(name):
    dismiss_system_dialogs()
    shot_n[0] += 1
    fn = f"{OUT}/{shot_n[0]:02d}_{name}.png"
    run(f"adb exec-out screencap -p > {fn}")
    log("screenshot", fn)


def raw_dump():
    run("adb shell uiautomator dump /sdcard/ui.xml", 30)
    return run("adb shell cat /sdcard/ui.xml", 30)


def dismiss_system_dialogs(xml=None):
    """Emulator hiccups: tap 'Wait' on any 'isn't responding' dialog. Returns True if one was closed."""
    xml = xml if xml is not None else raw_dump()
    if "isn't responding" in xml or "Wait" in xml and "Close app" in xml:
        for n in nodes(xml):
            if n["text"] == "Wait":
                log("dismissing ANR dialog")
                tap(n["cx"], n["cy"])
                time.sleep(1.5)
                return True
    return False


def dump():
    xml = raw_dump()
    for _ in range(3):
        if not dismiss_system_dialogs(xml):
            break
        xml = raw_dump()
    return xml


def nodes(xml):
    for m in re.finditer(r"<node [^>]*>", xml):
        n = m.group(0)
        t = re.search(r' text="([^"]*)"', n)
        d = re.search(r' content-desc="([^"]*)"', n)
        c = re.search(r' class="([^"]*)"', n)
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
        p = re.search(r' package="([^"]*)"', n)
        if not b:
            continue
        x1, y1, x2, y2 = map(int, b.groups())
        yield {
            "text": (t.group(1) if t else ""),
            "desc": (d.group(1) if d else ""),
            "cls": (c.group(1) if c else ""),
            "pkg": (p.group(1) if p else ""),
            "cx": (x1 + x2) // 2,
            "cy": (y1 + y2) // 2,
            "bounds": (x1, y1, x2, y2),
        }


def find(rx, xml=None, cls=None):
    xml = xml if xml is not None else dump()
    for n in nodes(xml):
        if cls and n["cls"] != cls:
            continue
        if re.search(rx, n["text"]) or re.search(rx, n["desc"]):
            return n
    return None


def tap(x, y):
    run(f"adb shell input tap {x} {y}")


def screen_size():
    out = run("adb shell wm size")
    m = re.search(r"(\d+)x(\d+)", out)
    return (int(m.group(1)), int(m.group(2))) if m else (1080, 2400)


W, H = screen_size()


def scroll_down():
    run(f"adb shell input swipe {W // 2} {int(H * 0.75)} {W // 2} {int(H * 0.3)} 350")
    time.sleep(1.0)


def scroll_up():
    run(f"adb shell input swipe {W // 2} {int(H * 0.3)} {W // 2} {int(H * 0.8)} 350")
    time.sleep(1.0)


def tap_text(rx, tries=8, cls=None):
    for _ in range(tries):
        n = find(rx, cls=cls)
        if n:
            tap(n["cx"], n["cy"])
            return n
        scroll_down()
    return None


last_block = [""]


def overlay_window():
    """Frame of our floating pairing window, or None."""
    out = run("adb shell dumpsys window windows", 30)
    blocks = re.split(r"\n\s*Window #\d+ ", out)
    for b in blocks:
        if "CodUsbPairing" in b.split("\n", 1)[0]:
            last_block[0] = b
            m = re.search(r"Frames:.*?\bframe=\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]", b) or \
                re.search(r"[^a-zA-Z]frame=\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]", b) or \
                re.search(r"mFrame=\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]", b)
            if m:
                return tuple(map(int, m.groups()))
            return (0, 0, 0, 0)
    return None


def save_block(name):
    with open(f"{OUT}/window_{name}.txt", "w") as f:
        f.write(last_block[0])


def virtual_present():
    return "Xbox Wireless Controller" in run("adb shell dumpsys input", 30)


def wait_for(fn, timeout, step=0.25):
    t0 = time.time()
    while time.time() - t0 < timeout:
        v = fn()
        if v:
            return v, time.time() - t0
        time.sleep(step)
    return None, time.time() - t0


def launch_app():
    run(f"adb shell am start -W -n {PKG}/.MainActivity", 60)
    time.sleep(2.5)
    dismiss_system_dialogs()


def allow_network_prompt():
    """'Allow wireless debugging on this network?' → tick 'Always allow' and press Allow."""
    xml = dump()
    if "Allow wireless debugging" not in xml:
        return False
    box = find(r"Always allow on this network", xml)
    if box:
        tap(box["cx"], box["cy"])
        time.sleep(0.5)
    btn = find(r"^Allow$", dump())
    if btn:
        tap(btn["cx"], btn["cy"])
        time.sleep(2)
    log("allowed wireless debugging on this network")
    return True


def read_pairing_code():
    """Read the 6-digit code and the port from the Settings pairing dialog."""
    xml = dump()
    code = port = None
    for n in nodes(xml):
        if re.fullmatch(r"\d{6}", n["text"].strip()):
            code = n["text"].strip()
        m = re.search(r":(\d{4,5})\b", n["text"])
        if m:
            port = m.group(1)
    return code, port, xml


def main():
    # ---------- setup ----------
    run("adb wait-for-device", 120)
    log(run(f"adb install -r -g {APK}", 180))
    run(f"adb shell appops set {PKG} SYSTEM_ALERT_WINDOW allow")
    run("adb shell settings put global development_settings_enabled 1")
    run("adb shell svc wifi enable")
    run("adb shell input keyevent 82")  # unlock
    # let the freshly booted emulator settle, and clear any "not responding" dialogs
    time.sleep(25)
    for _ in range(4):
        dismiss_system_dialogs()
        time.sleep(2)
    log("wifi:", run("adb shell cmd wifi status"))
    run("adb shell am start -a android.settings.DEVICE_INFO_SETTINGS", 30)
    time.sleep(3)
    n = tap_text(r"^Build number$", tries=8)
    if n:
        for _ in range(8):
            tap(n["cx"], n["cy"])
            time.sleep(0.25)
    time.sleep(1)
    run("adb shell input keyevent 4")
    run("adb shell input keyevent 3")
    time.sleep(1)

    # ---------- 1. helper ----------
    apk_path = run(f"adb shell pm path {PKG}").strip().splitlines()[0].replace("package:", "").strip()
    run(
        f"adb shell 'CLASSPATH={apk_path} nohup app_process /system/bin --nice-name=codusb_hid "
        f"com.elad.codusb.HidServer </dev/null >/dev/null 2>&1 &'"
    )
    ok, t = wait_for(virtual_present, 10)
    result("hid_helper_creates_controller", bool(ok))
    log("helper log:", run("adb shell cat /data/local/tmp/codusb_hid.log"))

    # ---------- 2. main screen ----------
    launch_app()
    shot("main_active")
    scroll_down()
    shot("main_scrolled_1")
    scroll_down()
    shot("main_scrolled_2")
    scroll_down()
    shot("main_scrolled_3")
    run("adb shell 'kill $(cat /data/local/tmp/codusb_hid.pid)'")
    gone, _ = wait_for(lambda: not virtual_present(), 5, 0.3)
    result("helper_stops_by_pid", bool(gone))
    run(f"adb shell am force-stop {PKG}")
    launch_app()
    shot("main_inactive")

    # ---------- 3. X → code box ----------
    x = find(r"^צימוד$")
    if not x:
        result("x_button_found", False)
        return
    result("x_button_found", True)
    t0 = time.time()
    tap(x["cx"], x["cy"])
    win, dt = wait_for(overlay_window, 10, 0.1)
    result("code_box_appears", bool(win))
    result("code_box_appear_seconds", round(dt, 2))
    time.sleep(2)
    shot("after_x_settings_with_chip")
    if win:
        save_block("chip")
        log("chip frame:", win)
        result("code_box_starts_small", (win[2] - win[0]) < W * 0.9)

    # ---------- 4. open the pairing dialog ----------
    if not find(r"^Wireless debugging$|^Developer options$|^Debugging$"):
        log("Settings did not open developer options, opening directly")
        run("adb shell am start -a android.settings.APPLICATION_DEVELOPMENT_SETTINGS", 30)
        time.sleep(3)
    shot("developer_options")
    run("adb shell settings put global adb_wifi_enabled 1")
    time.sleep(2)
    allow_network_prompt()
    if not tap_text(r"^Wireless debugging$"):
        result("wireless_debugging_opened", False)
        shot("no_wireless_debugging")
        return
    result("wireless_debugging_opened", True)
    time.sleep(3)
    shot("wireless_debugging_screen")
    allow_network_prompt()
    if not tap_text(r"Pair device with pairing code", tries=3):
        result("pair_dialog_opened", False)
        shot("no_pair_option")
        return
    t_dialog = time.time()
    time.sleep(0.4)
    code, port, _ = read_pairing_code()
    log("dialog code/port (first read):", code, port)
    result("pair_dialog_opened", True)

    def expanded():
        w = overlay_window()
        return w if (w and (w[2] - w[0]) >= W * 0.9) else None

    win, dt = wait_for(expanded, 25, 0.1)
    save_block("expanded")
    result("code_box_expands_by_itself", bool(win))
    result("code_box_expand_seconds_after_dialog", round(time.time() - t_dialog, 2) if win else None)
    time.sleep(1.5)
    shot("dialog_with_expanded_box")

    if not code:
        # our box has focus now; tap the dialog area below it to read the code from Settings
        xml = dump()
        log("dump while box focused contains our app:", PKG in xml)
        for n in nodes(xml):
            if re.fullmatch(r"\d{6}", n["text"].strip()):
                code = n["text"].strip()
        if not code:
            tap(W // 2, int(H * 0.52))
            time.sleep(1)
            code, port2, _ = read_pairing_code()
            port = port or port2
    log("pairing code:", code, "port:", port)
    result("pairing_code_read_by_test", bool(code))
    if not code:
        return

    # ---------- 5. type the code into our box ----------
    xml = dump()
    field = None
    for n in nodes(xml):
        if n["pkg"] == PKG and n["cls"] == "android.widget.EditText":
            field = n
            break
    if field:
        tap(field["cx"], field["cy"])
    else:
        w = overlay_window()
        log("EditText not in dump, tapping by frame", w)
        if w:
            tap(int(w[0] + (w[2] - w[0]) * 0.62), int(w[1] + (w[3] - w[1]) * 0.62))
    time.sleep(1)
    run(f"adb shell input text {code}")
    time.sleep(1)
    shot("code_typed")

    ok, dt = wait_for(virtual_present, 90, 1.0)
    result("pairing_creates_controller", bool(ok))
    result("seconds_from_code_to_controller", round(dt, 1))
    shot("after_pairing")
    time.sleep(4)
    result("code_box_closed_after_success", overlay_window() is None)

    # ---------- 6. B / A ----------
    run(f"adb shell am force-stop {PKG}")  # does not kill the shell helper
    result("controller_survives_app_kill", virtual_present())
    launch_app()
    shot("main_after_pairing")
    b = find(r"^כבה$")
    if b:
        tap(b["cx"], b["cy"])
        gone, dt = wait_for(lambda: not virtual_present(), 60, 1.0)
        result("B_stops_controller", bool(gone))
    a = find(r"^הפעל$")
    if a:
        tap(a["cx"], a["cy"])
        back, dt = wait_for(virtual_present, 90, 1.0)
        result("A_starts_controller", bool(back))
        result("A_start_seconds", round(dt, 1))
    time.sleep(2)
    shot("main_end")
    scroll_down()
    scroll_down()
    scroll_down()
    shot("log_end")


try:
    main()
except Exception as e:  # keep results even on a crash
    log("EXCEPTION", repr(e))
    result("test_crashed", repr(e))
finally:
    run(f"adb logcat -d -t 3000 > {OUT}/logcat.txt", 60)
    with open(f"{OUT}/results.json", "w") as f:
        json.dump(results, f, ensure_ascii=False, indent=2)
    with open(f"{OUT}/test_log.txt", "w") as f:
        f.write("\n".join(log_lines))

required = [
    "hid_helper_creates_controller",
    "helper_stops_by_pid",
    "code_box_appears",
    "code_box_expands_by_itself",
    "pairing_creates_controller",
    "B_stops_controller",
    "A_starts_controller",
]
failed = [k for k in required if results.get(k) is not True]
if results.get("code_box_appear_seconds") is not None and results["code_box_appear_seconds"] > 2:
    failed.append("code_box_appear_seconds>2")
print("FAILED:", failed, flush=True)
sys.exit(1 if failed else 0)
