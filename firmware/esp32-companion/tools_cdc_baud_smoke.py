import json
import time
import serial

PORT = "COM30"
BAUD_CDC = 115200


def wait_for(ser, pred, timeout=5.0, also_send=None):
    if also_send:
        payload = also_send if also_send.endswith("\n") else also_send + "\n"
        ser.write(payload.encode())
        ser.flush()
    end = time.time() + timeout
    buf = b""
    while time.time() < end:
        n = ser.in_waiting
        if n:
            buf += ser.read(n)
            while b"\n" in buf:
                line, buf = buf.split(b"\n", 1)
                s = line.decode("utf-8", errors="replace").strip()
                if not s.startswith("{"):
                    continue
                try:
                    o = json.loads(s)
                except Exception:
                    continue
                if pred(o):
                    return o
        else:
            time.sleep(0.05)
    return None


def main():
    results = []
    ser = serial.Serial(PORT, BAUD_CDC, timeout=0.2)
    time.sleep(0.8)
    ser.reset_input_buffer()

    hello = wait_for(
        ser,
        lambda o: o.get("t") == "hello",
        timeout=4.0,
        also_send='{"v":1,"t":"hello"}',
    )
    assert hello, "no hello"
    results.append(("hello_fw", hello.get("fw")))
    results.append(("hello_baud", hello.get("baud")))
    assert hello.get("fw") == "0.3.0", hello
    assert hello.get("baud") in (
        9600,
        115200,
        57600,
        19200,
        38400,
        230400,
        460800,
    ), hello

    ack = wait_for(
        ser,
        lambda o: o.get("t") == "um980Baud",
        timeout=3.0,
        also_send='{"v":1,"t":"um980Baud","baud":9600}',
    )
    assert ack, "no um980Baud ack"
    results.append(("set_9600", ack))
    assert ack.get("ok") is True and ack.get("baud") == 9600, ack

    wait_for(
        ser,
        lambda o: o.get("t") == "rebootAck",
        timeout=2.0,
        also_send='{"v":1,"t":"reboot"}',
    )
    ser.close()
    time.sleep(3.5)

    ser = serial.Serial(PORT, BAUD_CDC, timeout=0.2)
    time.sleep(1.0)
    ser.reset_input_buffer()
    hello2 = wait_for(
        ser,
        lambda o: o.get("t") == "hello",
        timeout=5.0,
        also_send='{"v":1,"t":"hello"}',
    )
    assert hello2, "no hello after reboot"
    results.append(("hello_after_reboot", hello2))
    assert hello2.get("fw") == "0.3.0", hello2
    assert hello2.get("baud") == 9600, f"NVS persist failed: {hello2}"

    ack2 = wait_for(
        ser,
        lambda o: o.get("t") == "um980Baud",
        timeout=3.0,
        also_send='{"v":1,"t":"um980Baud","baud":115200}',
    )
    assert ack2 and ack2.get("ok") and ack2.get("baud") == 115200, ack2
    results.append(("restore_115200", ack2))

    ack3 = wait_for(
        ser,
        lambda o: o.get("t") == "um980Baud",
        timeout=3.0,
        also_send='{"v":1,"t":"um980Baud","baud":12345}',
    )
    assert ack3 and ack3.get("ok") is False and ack3.get("baud") == 115200, ack3
    results.append(("reject_bad", ack3))

    hb = wait_for(ser, lambda o: o.get("t") == "hb", timeout=3.0)
    results.append(("hb", bool(hb)))

    ser.close()
    print("PASS")
    for k, v in results:
        print(f"  {k}: {v}")


if __name__ == "__main__":
    main()
