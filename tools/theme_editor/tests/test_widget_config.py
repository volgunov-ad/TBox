"""Tests for widget_config / catalog."""

from __future__ import annotations

import unittest

from widget_catalog import WIDGET_TYPES, format_sample_value, get_widget_type
from widget_config import (
    DEFAULT_TEXT_LIGHT,
    empty_widget,
    hex_to_argb,
    normalize_widgets,
    serialize_widget,
    to_signed_argb,
)


class WidgetConfigTests(unittest.TestCase):
    def test_catalog_has_empty_and_common_keys(self) -> None:
        keys = [w.data_key for w in WIDGET_TYPES]
        self.assertEqual(keys[0], "")
        self.assertIn("carSpeed", keys)
        self.assertIn("musicWidget", keys)
        self.assertIn("appLauncherWidget", keys)
        self.assertGreaterEqual(len(keys), 90)

    def test_sample_accuracy(self) -> None:
        self.assertEqual(format_sample_value("carSpeedAccurate", 0), "86")
        self.assertEqual(format_sample_value("carSpeedAccurate", 1), "86.4")

    def test_normalize_pads_and_truncates(self) -> None:
        widgets = normalize_widgets(2, 2, [empty_widget("voltage")])
        self.assertEqual(len(widgets), 4)
        self.assertEqual(widgets[0]["dataKey"], "voltage")
        self.assertEqual(widgets[1]["dataKey"], "")

    def test_serialize_omits_default_optional(self) -> None:
        w = empty_widget("voltage")
        out = serialize_widget(w)
        self.assertEqual(out["dataKey"], "voltage")
        self.assertIn("showTitle", out)
        self.assertNotIn("customTitle", out)
        self.assertNotIn("textAlign", out)
        self.assertEqual(out["textColorLight"], DEFAULT_TEXT_LIGHT)

    def test_serialize_keeps_launcher_freeform(self) -> None:
        w = empty_widget("appLauncherWidget")
        w["launcherAppPackage"] = "com.example.app"
        w["launcherFreeformEnabled"] = True
        w["launcherFreeformSide"] = "left"
        w["launcherFreeformPercent"] = 40
        out = serialize_widget(w)
        self.assertEqual(out["launcherAppPackage"], "com.example.app")
        self.assertTrue(out["launcherFreeformEnabled"])
        self.assertEqual(out["launcherFreeformSide"], "left")
        self.assertEqual(out["launcherFreeformPercent"], 40)

    def test_argb_roundtrip(self) -> None:
        self.assertEqual(to_signed_argb(0xFF1A1C1E), DEFAULT_TEXT_LIGHT)
        self.assertEqual(hex_to_argb("#FF1A1C1E"), DEFAULT_TEXT_LIGHT)
        self.assertEqual(to_signed_argb(0xFFE2E2E6), -1907994)

    def test_capabilities(self) -> None:
        self.assertTrue(get_widget_type("voltage").supports_show_unit)
        self.assertFalse(get_widget_type("musicWidget").supports_show_unit)
        self.assertTrue(get_widget_type("musicWidget").is_music)
        self.assertTrue(get_widget_type("appLauncherWidget").is_launcher)


if __name__ == "__main__":
    unittest.main()
