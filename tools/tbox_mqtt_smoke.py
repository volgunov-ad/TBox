#!/usr/bin/env python3
"""Subscribe to a TBox MQTT broker and optionally send one command.

Example:
  python3 tools/tbox_mqtt_smoke.py --host 192.168.1.10 --device dashing
  python3 tools/tbox_mqtt_smoke.py --host 192.168.1.10 --set drive_mode SPT
"""

from __future__ import annotations

import argparse
import sys

try:
    import paho.mqtt.client as mqtt
except ImportError:
    print("paho-mqtt is required: pip install -r requirements.txt", file=sys.stderr)
    raise SystemExit(1)


def main() -> None:
    parser = argparse.ArgumentParser(description="Watch TBox MQTT topics and send one command")
    parser.add_argument("--host", required=True)
    parser.add_argument("--port", type=int, default=1883)
    parser.add_argument("--username", default="")
    parser.add_argument("--password", default="")
    parser.add_argument("--device", default="dashing")
    parser.add_argument("--prefix", default="tbox")
    parser.add_argument("--discovery-prefix", default="homeassistant")
    parser.add_argument("--set", nargs=2, metavar=("OBJECT", "PAYLOAD"))
    args = parser.parse_args()

    base = f"{args.prefix}/{args.device}"
    client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2)
    if args.username:
        client.username_pw_set(args.username, args.password)

    def on_message(_client, _userdata, message) -> None:
        payload = message.payload.decode("utf-8", errors="replace")
        print(f"{message.topic} {payload}")

    client.on_message = on_message
    client.connect(args.host, args.port, keepalive=30)
    client.subscribe(f"{base}/#", qos=1)
    client.subscribe(f"{args.discovery_prefix}/#", qos=1)
    if args.set:
        object_id, payload = args.set
        topic = f"{base}/{object_id}/set"
        print(f"publish {topic} {payload}")
        client.publish(topic, payload, qos=1, retain=False)
    client.loop_forever()


if __name__ == "__main__":
    main()
