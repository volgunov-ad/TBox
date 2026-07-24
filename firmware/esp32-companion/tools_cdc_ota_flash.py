#!/usr/bin/env python3
"""Flash companion app image over USB CDC OTA (no UART needed)."""

from __future__ import annotations

import json
import struct
import sys
import time
import zlib
from pathlib import Path

import serial
from serial import SerialException

PORT = sys.argv[1] if len(sys.argv) > 1 else "COM30"
BIN_PATH = Path(
    sys.argv[2]
    if len(sys.argv) > 2
    else Path(__file__).resolve().parent / "build" / "esp32_companion.bin"
)
CHUNK = 1024


def open_ser() -> serial.Serial:
    s = serial.Serial(PORT, 115200, timeout=0.05)
    s.dtr = True
    s.rts = False
    time.sleep(0.6)
    try:
        s.reset_input_buffer()
    except Exception:
        pass
    return s


def read_json_lines(s: serial.Serial, timeout: float) -> list[dict]:
    end = time.time() + timeout
    buf = b""
    out: list[dict] = []
    while time.time() < end:
        try:
            n = s.in_waiting
        except SerialException:
            break
        if n:
            buf += s.read(n)
            while b"\n" in buf:
                raw, buf = buf.split(b"\n", 1)
                t = raw.decode("utf-8", errors="replace").strip()
                if t.startswith("{"):
                    try:
                        out.append(json.loads(t))
                    except Exception:
                        pass
        else:
            time.sleep(0.02)
    return out


def wait_t(s: serial.Serial, t: str, timeout: float, send: dict | None = None) -> dict | None:
    if send is not None:
        s.write((json.dumps(send, separators=(",", ":")) + "\n").encode())
        s.flush()
    end = time.time() + timeout
    while time.time() < end:
        for o in read_json_lines(s, 0.1):
            if o.get("t") == t:
                return o
    return None


def chunk_frame(payload: bytes) -> bytes:
    crc = zlib.crc32(payload) & 0xFFFFFFFF
    return b"\xA5\x5A" + struct.pack(">H", len(payload)) + payload + struct.pack(">I", crc)


def main() -> int:
    data = BIN_PATH.read_bytes()
    if not data or data[0] != 0xE9:
        print(f"bad image: {BIN_PATH} size={len(data)}")
        return 2
    crc = zlib.crc32(data) & 0xFFFFFFFF
    print(f"OTA {BIN_PATH.name}: {len(data)} bytes crc=0x{crc:08x} -> {PORT}")

    s = open_ser()
    try:
        hello = wait_t(s, "hello", 4.0, {"v": 1, "t": "hello"})
        print("hello", hello)
        if not hello:
            print("FAIL no hello")
            return 1

        begin = wait_t(
            s,
            "otaAck",
            5.0,
            {"v": 1, "t": "otaBegin", "size": len(data), "crc32": crc},
        )
        print("otaBegin ack", begin)
        if not begin or begin.get("ok") is not True or begin.get("phase") != "begin":
            print("FAIL otaBegin")
            return 1

        off = 0
        while off < len(data):
            piece = data[off : off + CHUNK]
            s.write(chunk_frame(piece))
            s.flush()
            off += len(piece)
            # Drain acks/hb so buffer doesn't fill
            read_json_lines(s, 0.01)
            if off % (CHUNK * 32) == 0 or off >= len(data):
                print(f"  wrote {off}/{len(data)}")

        done = None
        s.write((json.dumps({"v": 1, "t": "otaEnd"}, separators=(",", ":")) + "\n").encode())
        s.flush()
        end = time.time() + 30
        while time.time() < end:
            for o in read_json_lines(s, 0.2):
                print("RX", o.get("t"), o)
                if o.get("t") == "otaDone":
                    done = o
                    break
            if done:
                break
        print("otaDone", done)
        if not done or done.get("ok") is not True:
            print("FAIL otaDone")
            return 1
        print("PASS OTA — companion rebooting")
    finally:
        try:
            s.close()
        except Exception:
            pass

    time.sleep(4.0)
    # Verify new version
    for attempt in range(1, 10):
        try:
            s2 = open_ser()
            hello2 = wait_t(s2, "hello", 5.0, {"v": 1, "t": "hello"})
            s2.close()
            print("hello after OTA", hello2)
            if hello2 and str(hello2.get("fw", "")).startswith("0.4.3"):
                print("PASS fw=0.4.3")
                return 0
            if hello2:
                print(f"WARN unexpected fw={hello2.get('fw')}")
                return 0
        except Exception as e:
            print(f"reopen attempt {attempt}: {e}")
            time.sleep(1.0)
    print("FAIL no hello after OTA")
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
