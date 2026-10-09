#!/usr/bin/env python3
"""Relays Wear data layer messages between unpaired phone and watch emulators (debug builds).

    python tools/wear-relay.py [phone-serial] [watch-serial]

Defaults to emulator-5558 and emulator-5554. Set ADB if adb isn't on the PATH.
"""
import os
import subprocess
import sys
import threading

ADB = os.environ.get("ADB", "adb")
ACTION = "online.bmarian.bus.DEBUG_WEAR"
PACKAGE = "online.bmarian.bus"


def pump(source: str, target: str, label: str) -> None:
    # Long messages arrive as path#i/n.
    parts: dict[str, list[str]] = {}
    subprocess.run([ADB, "-s", source, "logcat", "-c"], check=False)
    log = subprocess.Popen(
        [ADB, "-s", source, "logcat", "-v", "raw", "-s", "WearRelay:D"],
        stdout=subprocess.PIPE,
        text=True,
    )
    for line in log.stdout:
        line = line.strip()
        if not line.startswith("/") or " " not in line:
            continue
        path, data = line.split(" ", 1)
        if "#" in path:
            path, counter = path.split("#", 1)
            index, total = (int(x) for x in counter.split("/"))
            if index == 1:
                parts[path] = []
            parts.setdefault(path, []).append(data)
            if index < total:
                continue
            data = "".join(parts.pop(path, []))
        subprocess.run(
            [ADB, "-s", target, "shell", "am", "broadcast", "-a", ACTION, "-p", PACKAGE,
             "--es", "path", path, "--es", "data", data],
            stdout=subprocess.DEVNULL,
            check=False,
        )
        print(f"{label} {path} ({len(data)} chars)", flush=True)


def main() -> None:
    phone = sys.argv[1] if len(sys.argv) > 1 else "emulator-5558"
    watch = sys.argv[2] if len(sys.argv) > 2 else "emulator-5554"
    threads = [
        threading.Thread(target=pump, args=(phone, watch, "phone -> watch"), daemon=True),
        threading.Thread(target=pump, args=(watch, phone, "watch -> phone"), daemon=True),
    ]
    for t in threads:
        t.start()
    print(f"Relaying between {phone} and {watch}. Ctrl+C to stop.", flush=True)
    for t in threads:
        t.join()


if __name__ == "__main__":
    main()
