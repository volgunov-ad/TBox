#!/usr/bin/env python3
"""Генератор фиктивных CAN-кадров климата Jetour Dashing для стендовой проверки
esp32-climate-display (Waveshare ESP32-S3-Touch-LCD-7, второй CAN платы через
USB-C и трансивер SN65HVD230, режим listen-only).

Модель кадра повторяет декод приложения (CanFramesProcess.kt, CAN-выгрузка TBox):
  0x52F  заданная температура климата, байт 5 = °C x 4, 0 = невалидно;
         сетка 0.5 °C => сырые значения кратны 2 (16.0 °C = 64, 30.0 °C = 120).

Прошивка разбирает только 0x52F; прочие известные ID машина (и этот скрипт в
режиме --discovery) посылает для проверки лога "new CAN id" в can_map.

Транспорты (только stdlib):
  --mock              печатать кадры в консоль, без железа
  --slcan COM5        slcan-совместимый адаптер (CANable и клоны), COM / /dev/tty*
  --socketcan can0    Linux SocketCAN (CAN_RAW)

Примеры:
  python tools/climate_can_sim.py --mock --frames 5
  python tools/climate_can_sim.py --slcan COM5 --interval 0.5
  python tools/climate_can_sim.py --slcan COM5 --value 22.5
  python tools/climate_can_sim.py --slcan COM5 --discovery --frames 0
"""
import argparse
import struct
import sys
import time

CAN_ID_CLIMATE_SET = 0x52F
CAN_ID_CLIMATE_SET_BYTE = 5
CAN_DLC = 8
TEMP_MIN_C10 = 160
TEMP_MAX_C10 = 300
DEFAULT_STEP_C10 = 5

DISCOVERY_IDS = [0x100, 0x200, 0x2E9, 0x300, 0x310, 0x430, 0x4E0, 0x501, 0x51B, 0x535, 0x5FF]


def build_climate_set(temp_c10):
    raw4 = (int(round(temp_c10 * 10)) * 4) // 10
    if raw4 <= 0:
        return None
    data = bytearray(CAN_DLC)
    data[CAN_ID_CLIMATE_SET_BYTE] = raw4 & 0xFF
    return CAN_ID_CLIMATE_SET, bytes(data), raw4


class MockCan:
    def send(self, can_id, data):
        sys.stdout.write(
            time.strftime("%H:%M:%S")
            + "  0x%03X  [%s]\n" % (can_id, " ".join("%02X" % b for b in data))
        )
        sys.stdout.flush()

    def close(self):
        pass


class SlcanCan:
    def __init__(self, port, baud):
        self._handle = None
        self._fd = None
        if sys.platform == "win32":
            self._handle = self._open_win32(port, baud)
        else:
            self._fd = self._open_posix(port, baud)

    @staticmethod
    def _open_win32(port, baud):
        import ctypes
        from ctypes import wintypes

        class _DCB(ctypes.Structure):
            _fields_ = [
                ("DCBlength", wintypes.DWORD),
                ("BaudRate", wintypes.DWORD),
                ("fFlags", wintypes.DWORD),
                ("wReserved", wintypes.WORD),
                ("XonLim", wintypes.WORD),
                ("XoffLim", wintypes.WORD),
                ("ByteSize", wintypes.BYTE),
                ("Parity", wintypes.BYTE),
                ("StopBits", wintypes.BYTE),
                ("XonChar", ctypes.c_char),
                ("XoffChar", ctypes.c_char),
                ("ErrorChar", ctypes.c_char),
                ("EofChar", ctypes.c_char),
                ("EvtChar", ctypes.c_char),
                ("wReserved2", wintypes.WORD),
            ]

        class _COMMTIMEOUTS(ctypes.Structure):
            _fields_ = [
                ("ReadIntervalTimeout", wintypes.DWORD),
                ("ReadTotalTimeoutMultiplier", wintypes.DWORD),
                ("ReadTotalTimeoutConstant", wintypes.DWORD),
                ("WriteTotalTimeoutMultiplier", wintypes.DWORD),
                ("WriteTotalTimeoutConstant", wintypes.DWORD),
            ]

        k32 = ctypes.windll.kernel32
        name = "\\\\.\\%s" % port
        handle = k32.CreateFileW(name, 0xC0000000, 0, None, 3, 0, None)
        if handle in (-1, 0xFFFFFFFFFFFFFFFF):
            raise OSError("COM-порт %s не открылся" % port)
        dcb = _DCB()
        dcb.DCBlength = ctypes.sizeof(_DCB)
        if not k32.GetCommState(handle, ctypes.byref(dcb)):
            raise OSError("GetCommState failed")
        dcb.BaudRate = baud
        dcb.fFlags = 0x0001
        dcb.ByteSize = 8
        dcb.Parity = 0
        dcb.StopBits = 0
        if not k32.SetCommState(handle, ctypes.byref(dcb)):
            raise OSError("SetCommState failed")
        timeouts = _COMMTIMEOUTS(0xFFFFFFFF, 0, 0, 0, 0)
        if not k32.SetCommTimeouts(handle, ctypes.byref(timeouts)):
            raise OSError("SetCommTimeouts failed")
        return handle

    @staticmethod
    def _open_posix(port, baud):
        import os
        import termios

        fd = os.open(port, os.O_WRONLY | os.O_NOCTTY)
        attrs = termios.tcgetattr(fd)
        attrs[0] = 0
        attrs[1] = 0
        attrs[2] = termios.CS8 | termios.CLOCAL | termios.CREAD
        attrs[3] = 0
        speed = getattr(termios, "B%d" % baud, termios.B115200)
        attrs[4] = speed
        attrs[5] = speed
        termios.tcsetattr(fd, termios.TCSANOW, attrs)
        return fd

    def send(self, can_id, data):
        payload = ("t%03X%d%s\r" % (can_id, len(data), data.hex().upper())).encode("ascii")
        if self._handle is not None:
            import ctypes
            written = ctypes.c_ulong(0)
            ok = ctypes.windll.kernel32.WriteFile(
                self._handle, payload, len(payload), ctypes.byref(written), None
            )
            if not ok or written.value != len(payload):
                raise OSError("WriteFile failed")
        else:
            import os
            os.write(self._fd, payload)

    def close(self):
        if self._handle is not None:
            import ctypes
            ctypes.windll.kernel32.CloseHandle(self._handle)
            self._handle = None
        if self._fd is not None:
            import os
            os.close(self._fd)
            self._fd = None


class SocketcanCan:
    def __init__(self, iface):
        import socket
        self._sock = socket.socket(socket.AF_CAN, socket.SOCK_RAW, socket.CAN_RAW)
        self._sock.bind((iface,))

    def send(self, can_id, data):
        self._sock.send(
            struct.pack("=IB3x8s", can_id, len(data), bytes(data).ljust(CAN_DLC, b"\x00"))
        )

    def close(self):
        self._sock.close()


def parse_args():
    p = argparse.ArgumentParser(
        description="Фиктивный CAN-климат для стенда esp32-climate-display (0x52F, °C x 4)",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    g = p.add_mutually_exclusive_group()
    g.add_argument("--mock", action="store_true", help="консоль, без железа")
    g.add_argument("--slcan", metavar="PORT", help="slcan-адаптер: COM5 / /dev/ttyACM0")
    g.add_argument("--socketcan", metavar="IFACE", help="Linux SocketCAN: can0")
    p.add_argument("--baud", type=int, default=115200, help="скорость COM slcan (115200)")
    p.add_argument("--interval", type=float, default=1.0, help="период кадров 0x52F, с")
    p.add_argument("--start", type=float, default=16.0, help="низ качания, °C")
    p.add_argument("--stop", type=float, default=30.0, help="верх качания, °C")
    p.add_argument("--step", type=float, default=0.5, help="шаг качания, °C")
    p.add_argument("--value", type=float, help="фиксированное значение °C, без качания")
    p.add_argument("--frames", type=int, default=0, help="число кадров 0x52F (0 = бесконечно)")
    p.add_argument("--discovery", action="store_true", help="разово послать прочие известные ID")
    return p.parse_args()


def main():
    args = parse_args()
    if args.slcan:
        can = SlcanCan(args.slcan, args.baud)
    elif args.socketcan:
        can = SocketcanCan(args.socketcan)
    elif args.mock:
        can = MockCan()
    else:
        print("Транспорт не задан: --mock / --slcan PORT / --socketcan IFACE", file=sys.stderr)
        return 2

    step = max(DEFAULT_STEP_C10, int(round(args.step * 10)))
    lo = TEMP_MIN_C10 + max(0, (int(round(args.start * 10)) - TEMP_MIN_C10) // step) * step
    hi = TEMP_MIN_C10 + max(0, (int(round(args.stop * 10)) - TEMP_MIN_C10) // step) * step
    if args.value is not None:
        temp_c10 = TEMP_MIN_C10 + max(0, (int(round(args.value * 10)) - TEMP_MIN_C10) // step) * step
    else:
        temp_c10 = lo
    direction = 1

    try:
        if args.discovery:
            for did in DISCOVERY_IDS:
                can.send(did, bytes(CAN_DLC))
                sys.stdout.write("discovery 0x%03X\n" % did)
                time.sleep(0.05)

        sent = 0
        while args.frames <= 0 or sent < args.frames:
            frame = build_climate_set(temp_c10 / 10.0)
            if frame is None:
                print("setpoint %.1f C вне диапазона, кадр не послан" % (temp_c10 / 10.0), file=sys.stderr)
                return 2
            can_id, data, raw4 = frame
            can.send(can_id, data)
            print("0x%03X b5=%3d setpoint=%.1f C" % (can_id, data[CAN_ID_CLIMATE_SET_BYTE], raw4 / 4.0))
            sent += 1
            if args.value is None:
                nxt = temp_c10 + direction * step
                if nxt > hi:
                    direction = -1
                    nxt = temp_c10 + direction * step
                elif nxt < lo:
                    direction = 1
                    nxt = temp_c10 + direction * step
                temp_c10 = nxt
            time.sleep(args.interval)
    except KeyboardInterrupt:
        pass
    finally:
        can.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
