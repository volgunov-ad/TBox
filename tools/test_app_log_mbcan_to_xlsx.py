#!/usr/bin/env python3
"""Unit tests for app_log_mbcan_to_xlsx pure parse helpers. Stdlib only."""

from __future__ import annotations

import importlib.util
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent
TOOL = ROOT / "app_log_mbcan_to_xlsx.py"
CATALOG = (
    ROOT.parent
    / "app"
    / "src"
    / "main"
    / "java"
    / "vad"
    / "dashing"
    / "tbox"
    / "mbcan"
    / "MbCanCatalog.kt"
)


def load_tool():
    spec = importlib.util.spec_from_file_location("app_log_mbcan_to_xlsx", TOOL)
    mod = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    sys.modules[spec.name] = mod
    spec.loader.exec_module(mod)
    return mod


class CatalogLoadTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.m = load_tool()
        cls.catalog = cls.m.load_catalog_from_kotlin(CATALOG)

    def test_known_drive_mode_and_eps(self) -> None:
        self.assertTrue(self.catalog.is_known(145))
        self.assertEqual(self.catalog.name_for(145), "VEHICLE_DRIVEMODE")
        self.assertTrue(self.catalog.is_known(25))
        self.assertIn("EPS", self.catalog.name_for(25) or "")

    def test_a9_undecoded_ids_are_unknown(self) -> None:
        for item_id in (95, 80, 119, 100, 231, 182):
            self.assertFalse(
                self.catalog.is_known(item_id),
                f"expected item {item_id} to be absent from catalog",
            )


class ParseHelpersTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.m = load_tool()
        cls.catalog = cls.m.load_catalog_from_kotlin(CATALOG)

    def test_cfg_vehicle_push_unknown(self) -> None:
        line = (
            "[20:18:50] DEBUG: MBCAN_TMP. cfgVehiclePush modular=2 rev=0 item=119 value=1"
        )
        events = self.m.parse_line(line, self.catalog, include_trip_fuel=True)
        self.assertEqual(len(events), 1)
        ev = events[0]
        self.assertEqual(ev.tag, "MBCAN_TMP")
        self.assertEqual(ev.kind, "cfgVehiclePush")
        self.assertEqual(ev.item_id, 119)
        self.assertEqual(ev.value, "1")
        self.assertIs(ev.known, False)

    def test_cfg_vehicle_push_known(self) -> None:
        line = (
            "[20:19:39] DEBUG: MBCAN_TMP. cfgVehiclePush modular=2 rev=0 item=145 value=0"
        )
        events = self.m.parse_line(line, self.catalog, include_trip_fuel=True)
        self.assertEqual(len(events), 1)
        self.assertEqual(events[0].item_id, 145)
        self.assertIs(events[0].known, True)
        self.assertEqual(events[0].catalog_name, "VEHICLE_DRIVEMODE")

    def test_candiag_mbcan(self) -> None:
        line = (
            "[20:19:38] DEBUG: CANDIAG_MBCAN. mbcan dt=eMBCAN_CFG_VEHICLE "
            "modular=2 rev=0 item=95 value=1"
        )
        events = self.m.parse_line(line, self.catalog, include_trip_fuel=True)
        self.assertEqual(len(events), 1)
        self.assertEqual(events[0].item_id, 95)
        self.assertEqual(events[0].data_type, "eMBCAN_CFG_VEHICLE")
        self.assertIs(events[0].known, False)

    def test_candiag_vhal(self) -> None:
        line = (
            "[12:00:00] DEBUG: CANDIAG_VHAL. vhal propertyId=557842432 areaId=0 "
            "value=42.5 type=float status=0 tsNanos=1 name=CarSpeed"
        )
        events = self.m.parse_line(line, self.catalog, include_trip_fuel=True)
        self.assertEqual(len(events), 1)
        self.assertEqual(events[0].property_id, 557842432)
        self.assertEqual(events[0].value, "42.5")
        self.assertEqual(events[0].catalog_name, "CarSpeed")

    def test_push_coalesced_expands_keys(self) -> None:
        line = (
            "[20:18:49] DEBUG: MBCAN_TMP. push_coalesced["
            "telemetry/car_speed count=36 last=raw=0.0; "
            "telemetry/vehicle_gear count=36 last=raw=4 mode=P; "
            "telemetry/engine_rpm count=20 last=raw=792.0]"
        )
        events = self.m.parse_line(line, self.catalog, include_trip_fuel=True)
        self.assertEqual(len(events), 3)
        keys = {e.telemetry_key for e in events}
        self.assertEqual(
            keys,
            {
                "telemetry/car_speed",
                "telemetry/vehicle_gear",
                "telemetry/engine_rpm",
            },
        )
        rpm = next(e for e in events if e.telemetry_key == "telemetry/engine_rpm")
        self.assertEqual(rpm.value, "raw=792.0")
        self.assertIs(rpm.known, True)

    def test_trip_fuel_values(self) -> None:
        line = (
            "[20:19:00] DEBUG: TripFuel. sources[Rpm=HU(hu=146ms tbox=55803ms)] "
            "values[rpm=1432.0 speed=7.875 fuelRaw%=49 engTempC=66.75]"
        )
        events = self.m.parse_line(line, self.catalog, include_trip_fuel=True)
        self.assertGreaterEqual(len(events), 4)
        by_key = {e.telemetry_key: e.value for e in events}
        self.assertEqual(by_key["rpm"], "1432.0")
        self.assertEqual(by_key["engTempC"], "66.75")

    def test_trip_fuel_skipped(self) -> None:
        line = "[20:19:00] DEBUG: TripFuel. values[rpm=1]"
        events = self.m.parse_line(line, self.catalog, include_trip_fuel=False)
        self.assertEqual(events, [])

    def test_summary_marks_unknown(self) -> None:
        lines = [
            "[20:00:00] DEBUG: MBCAN_TMP. cfgVehiclePush modular=2 rev=0 item=95 value=1",
            "[20:00:01] DEBUG: MBCAN_TMP. cfgVehiclePush modular=2 rev=0 item=80 value=2",
            "[20:00:02] DEBUG: CANDIAG_MBCAN. mbcan dt=eMBCAN_CFG_VEHICLE "
            "modular=2 rev=0 item=145 value=0",
        ]
        events = []
        for line in lines:
            events.extend(self.m.parse_line(line, self.catalog, True))
        summary = self.m.build_items_summary(events)
        by_id = {r["item_or_property_id"]: r for r in summary}
        self.assertIs(by_id[95]["known"], False)
        self.assertIs(by_id[80]["known"], False)
        self.assertIs(by_id[145]["known"], True)


class EndToEndSmokeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.m = load_tool()

    def test_write_tiny_xlsx(self) -> None:
        catalog = self.m.load_catalog_from_kotlin(CATALOG)
        sample = (
            "# tbox app journal log\n"
            "[20:18:50] DEBUG: MBCAN_TMP. cfgVehiclePush modular=2 rev=0 item=119 value=1\n"
            "[20:19:38] DEBUG: CANDIAG_MBCAN. mbcan dt=eMBCAN_CFG_VEHICLE "
            "modular=2 rev=0 item=95 value=1\n"
            "[20:19:39] DEBUG: CANDIAG_MBCAN. mbcan dt=eMBCAN_CFG_VEHICLE "
            "modular=2 rev=0 item=145 value=0\n"
            "[20:18:49] DEBUG: MBCAN_TMP. push_coalesced["
            "telemetry/engine_rpm count=1 last=raw=800.0]\n"
        )
        with tempfile.TemporaryDirectory() as td:
            inp = Path(td) / "tbox_app_log_sample.txt"
            out = Path(td) / "out.xlsx"
            inp.write_text(sample, encoding="utf-8")
            events = self.m.parse_file(inp, catalog, include_trip_fuel=True, show_progress=False)
            summary = self.m.build_items_summary(events)
            self.m.write_xlsx(events, summary, out, show_progress=False, catalog=catalog)
            self.assertTrue(out.is_file())
            self.assertGreater(out.stat().st_size, 1000)
            unknown_ids = {
                r["item_or_property_id"] for r in summary if r["known"] is False
            }
            self.assertIn(119, unknown_ids)
            self.assertIn(95, unknown_ids)
            self.assertNotIn(145, unknown_ids)


if __name__ == "__main__":
    unittest.main()
